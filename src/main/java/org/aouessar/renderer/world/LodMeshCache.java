package org.aouessar.renderer.world;

import org.aouessar.renderer.gl.GlMeshLod;
import org.aouessar.renderer.mesh.LodMesher;
import org.aouessar.renderer.mesh.MeshData;
import org.aouessar.shared.EngineConfig;
import org.joml.FrustumIntersection;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Far-field LOD tile mesh cache (Distant Horizons rings).
 * <p>
 * Tiles are keyed by tile coordinates (one tile = one region footprint,
 * 256x256 blocks). Each tile is meshed at a step chosen from its Chebyshev
 * distance to the camera tile — near rings get dense grids, far rings coarse
 * ones — and is rebuilt in the background whenever its desired step changes.
 * <p>
 * Same threading contract as {@link ChunkMeshCache}: request/upload/draw/evict
 * on the render thread, meshing on worker threads.
 */
public final class LodMeshCache implements AutoCloseable {

    @FunctionalInterface
    public interface TileMeshBuilder {
        LodMesher.LodMeshes build(int tileX, int tileZ, int step);
    }

    private static final class Entry {
        volatile GlMeshLod terrain;
        volatile GlMeshLod water;
        volatile int step = -1;         // step of the uploaded meshes
        volatile int buildingStep = -1; // step currently being built (-1 = none)
        volatile CompletableFuture<LodMesher.LodMeshes> inFlight;
    }

    private record Ready(long key, int step, LodMesher.LodMeshes meshes) {}

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Ready> readyQueue = new ConcurrentLinkedQueue<>();
    private final LongKeyList visible = new LongKeyList(4096);

    private final ExecutorService executor;
    private final int maxInFlight;
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public LodMeshCache(int workerThreads, int maxInFlight) {
        this.maxInFlight = Math.max(1, maxInFlight);

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "lod-cpu");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    System.err.println("[" + thread.getName() + "] Uncaught exception: " + ex));
            return t;
        };
        this.executor = Executors.newFixedThreadPool(Math.max(1, workerThreads), tf);
    }

    /** Chebyshev tile distance -> sample step in blocks. */
    public static int stepForDistance(int d) {
        if (d <= 2) return 2;   // crisp band right behind the near field
        if (d <= 5) return 4;
        if (d <= 9) return 8;
        if (d <= 14) return 16;
        return 32;
    }

    // -----------------------------------------------------------------
    // Request (render thread)
    // -----------------------------------------------------------------

    /**
     * Walk tiles ring-by-ring around the camera tile and submit (re)builds for
     * tiles that are missing or whose desired step changed. Tiles outside the
     * frustum are skipped except for the innermost rings, so turning the
     * camera doesn't leave gaping holes right at the transition edge.
     */
    public void requestAround(
            int centerTx, int centerTz,
            int radiusTiles,
            int submitBudget,
            TileMeshBuilder builder,
            FrustumIntersection frustum,
            float minY, float maxY
    ) {
        for (int r = 0; r <= radiusTiles && submitBudget > 0; r++) {
            int desiredStep = stepForDistance(r);
            boolean alwaysBuild = r <= 2;

            if (r == 0) {
                submitBudget = maybeSubmit(centerTx, centerTz, desiredStep, submitBudget,
                        builder, frustum, minY, maxY, alwaysBuild);
                continue;
            }

            for (int dx = -r; dx <= r && submitBudget > 0; dx++) {
                submitBudget = maybeSubmit(centerTx + dx, centerTz - r, desiredStep, submitBudget,
                        builder, frustum, minY, maxY, alwaysBuild);
                submitBudget = maybeSubmit(centerTx + dx, centerTz + r, desiredStep, submitBudget,
                        builder, frustum, minY, maxY, alwaysBuild);
            }
            for (int dz = -r + 1; dz <= r - 1 && submitBudget > 0; dz++) {
                submitBudget = maybeSubmit(centerTx - r, centerTz + dz, desiredStep, submitBudget,
                        builder, frustum, minY, maxY, alwaysBuild);
                submitBudget = maybeSubmit(centerTx + r, centerTz + dz, desiredStep, submitBudget,
                        builder, frustum, minY, maxY, alwaysBuild);
            }
        }
    }

    private int maybeSubmit(
            int tx, int tz, int desiredStep, int submitBudget,
            TileMeshBuilder builder, FrustumIntersection frustum,
            float minY, float maxY, boolean alwaysBuild
    ) {
        if (submitBudget <= 0) return 0;
        if (inFlight.get() >= maxInFlight) return submitBudget;

        long key = ChunkKey.pack(tx, tz);
        Entry e = entries.computeIfAbsent(key, k -> new Entry());

        if (e.step == desiredStep) return submitBudget;
        if (e.buildingStep == desiredStep) return submitBudget;

        if (!alwaysBuild && !tileIntersectsFrustum(frustum, tx, tz, minY, maxY)) {
            return submitBudget;
        }

        CompletableFuture<LodMesher.LodMeshes> existing = e.inFlight;
        if (existing != null && !existing.isDone()) {
            // A build for a stale step is in flight; let it finish (it will be
            // replaced next frame if still wrong) rather than churn.
            return submitBudget;
        }

        if (inFlight.incrementAndGet() > maxInFlight) {
            inFlight.decrementAndGet();
            return submitBudget;
        }

        e.buildingStep = desiredStep;
        CompletableFuture<LodMesher.LodMeshes> fut =
                CompletableFuture.supplyAsync(() -> builder.build(tx, tz, desiredStep), executor);
        e.inFlight = fut;

        fut.whenComplete((meshes, ex) -> {
            inFlight.decrementAndGet();
            if (fut.isCancelled() || ex != null || meshes == null) {
                e.buildingStep = -1;
                return;
            }
            readyQueue.add(new Ready(key, desiredStep, meshes));
        });

        return submitBudget - 1;
    }

    // -----------------------------------------------------------------
    // Upload (render thread)
    // -----------------------------------------------------------------

    public int uploadReady(int uploadBudget) {
        int uploaded = 0;
        while (uploaded < uploadBudget) {
            Ready r = readyQueue.poll();
            if (r == null) break;

            Entry e = entries.get(r.key);
            if (e == null) continue; // evicted while building

            GlMeshLod oldT = e.terrain;
            GlMeshLod oldW = e.water;

            MeshData terrain = r.meshes.terrain();
            MeshData water = r.meshes.water();

            e.terrain = (terrain != null && !terrain.isEmpty()) ? new GlMeshLod(terrain) : null;
            e.water = (water != null && !water.isEmpty()) ? new GlMeshLod(water) : null;
            e.step = r.step;
            if (e.buildingStep == r.step) e.buildingStep = -1;
            e.inFlight = null;

            if (oldT != null) oldT.close();
            if (oldW != null) oldW.close();
            uploaded++;
        }
        return uploaded;
    }

    // -----------------------------------------------------------------
    // Visibility + draw (render thread)
    // -----------------------------------------------------------------

    /** Collect frustum-visible tiles with uploaded terrain. Call once per frame. */
    public int computeVisible(FrustumIntersection frustum, float minY, float maxY) {
        visible.clear();
        for (var me : entries.entrySet()) {
            Entry e = me.getValue();
            if (e.terrain == null) continue;

            long key = me.getKey();
            int tx = ChunkKey.unpackX(key);
            int tz = ChunkKey.unpackZ(key);
            if (!tileIntersectsFrustum(frustum, tx, tz, minY, maxY)) continue;

            visible.add(key);
        }
        return visible.size();
    }

    public int drawTerrain() {
        int drawn = 0;
        for (int i = 0; i < visible.size(); i++) {
            Entry e = entries.get(visible.get(i));
            if (e == null) continue;
            GlMeshLod m = e.terrain;
            if (m == null) continue;
            m.draw();
            drawn++;
        }
        return drawn;
    }

    /**
     * Draw all uploaded terrain meshes within a Chebyshev tile radius,
     * ignoring the camera frustum — shadow casters behind the camera still
     * throw shadows into the view.
     */
    public int drawTerrainWithin(int centerTx, int centerTz, int radiusTiles) {
        int drawn = 0;
        for (var me : entries.entrySet()) {
            long key = me.getKey();
            int tx = ChunkKey.unpackX(key);
            int tz = ChunkKey.unpackZ(key);
            if (Math.abs(tx - centerTx) > radiusTiles || Math.abs(tz - centerTz) > radiusTiles) continue;

            GlMeshLod m = me.getValue().terrain;
            if (m == null) continue;
            m.draw();
            drawn++;
        }
        return drawn;
    }

    public int drawWater() {
        int drawn = 0;
        for (int i = 0; i < visible.size(); i++) {
            Entry e = entries.get(visible.get(i));
            if (e == null) continue;
            GlMeshLod m = e.water;
            if (m == null) continue;
            m.draw();
            drawn++;
        }
        return drawn;
    }

    private static boolean tileIntersectsFrustum(
            FrustumIntersection frustum, int tx, int tz, float minY, float maxY
    ) {
        float minX = (float) tx * EngineConfig.REGION_SIZE_BLOCKS;
        float minZ = (float) tz * EngineConfig.REGION_SIZE_BLOCKS;
        float maxX = minX + EngineConfig.REGION_SIZE_BLOCKS;
        float maxZ = minZ + EngineConfig.REGION_SIZE_BLOCKS;
        return frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // -----------------------------------------------------------------
    // Eviction / stats / shutdown
    // -----------------------------------------------------------------

    public void evictOutside(int centerTx, int centerTz, int keepRadiusTiles) {
        for (var it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Entry> me = it.next();
            long key = me.getKey();
            int tx = ChunkKey.unpackX(key);
            int tz = ChunkKey.unpackZ(key);

            if (Math.abs(tx - centerTx) <= keepRadiusTiles && Math.abs(tz - centerTz) <= keepRadiusTiles) {
                continue;
            }

            Entry e = me.getValue();
            if (e.terrain != null) e.terrain.close();
            if (e.water != null) e.water.close();

            CompletableFuture<LodMesher.LodMeshes> f = e.inFlight;
            if (f != null && !f.isDone()) f.cancel(true);

            it.remove();
        }

        readyQueue.removeIf(r -> {
            int tx = ChunkKey.unpackX(r.key());
            int tz = ChunkKey.unpackZ(r.key());
            return Math.abs(tx - centerTx) > keepRadiusTiles || Math.abs(tz - centerTz) > keepRadiusTiles;
        });
    }

    public int tileCount() {
        return entries.size();
    }

    public int meshedTileCount() {
        int n = 0;
        for (Entry e : entries.values()) if (e.terrain != null) n++;
        return n;
    }

    public int inFlightCount() {
        return inFlight.get();
    }

    @Override
    public void close() {
        for (Entry e : entries.values()) {
            if (e.terrain != null) e.terrain.close();
            if (e.water != null) e.water.close();
            CompletableFuture<LodMesher.LodMeshes> f = e.inFlight;
            if (f != null && !f.isDone()) f.cancel(true);
        }
        entries.clear();
        readyQueue.clear();
        executor.shutdownNow();
    }
}
