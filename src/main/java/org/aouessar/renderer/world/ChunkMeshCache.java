package org.aouessar.renderer.world;

import org.aouessar.renderer.gl.GlMesh;
import org.aouessar.renderer.gl.IGlMesh;
import org.aouessar.renderer.mesh.MeshData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.joml.FrustumIntersection;

public final class ChunkMeshCache implements AutoCloseable {

    @FunctionalInterface
    public interface MeshBuilder {
        MeshData build(int cx, int cz);
    }

    @FunctionalInterface
    public interface MeshUploader {
        IGlMesh upload(MeshData md);
    }

    private static final class Entry {
        volatile IGlMesh mesh;                         // render thread only (creation/close)
        volatile CompletableFuture<MeshData> inFlight; // worker threads
    }

    private record Ready(long key, MeshData data) {}
    private record DrawItem(IGlMesh mesh, float d2) {}

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Ready> readyQueue = new ConcurrentLinkedQueue<>();

    // Render thread only: reused list to avoid per-frame allocations in sorted draws
    private final ArrayList<DrawItem> drawList = new ArrayList<>(1024);

    private final ExecutorService executor;
    private final int maxInFlight;
    private final AtomicInteger inFlight = new AtomicInteger(0);

    private final MeshUploader uploader;

    public ChunkMeshCache(int workerThreads, int maxInFlight, MeshUploader uploader) {
        this.maxInFlight = Math.max(1, maxInFlight);
        this.uploader = uploader;

        this.executor = Executors.newFixedThreadPool(
                Math.max(1, workerThreads),
                r -> {
                    Thread t = new Thread(r, "mesh-cpu");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /** Good defaults (naive pipeline). */
    public ChunkMeshCache() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 128, GlMesh::new);
    }

    /** O(1) */
    public int inFlightCount() {
        return inFlight.get();
    }

    public int size() {
        return entries.size();
    }

    public int entryCount() { return entries.size(); }

    public int meshCount() {
        int n = 0;
        for (Entry e : entries.values()) if (e.mesh != null) n++;
        return n;
    }

    /**
     * Render thread only.
     * Build and upload a small area immediately (prevents “spawn hole”).
     */
    public int ensureImmediateArea(int centerCx, int centerCz, int radiusChunks, MeshBuilder builder) {
        int built = 0;

        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;
                long key = ChunkKey.pack(cx, cz);

                Entry e = entries.computeIfAbsent(key, k -> new Entry());

                CompletableFuture<MeshData> f = e.inFlight;
                if (f != null && !f.isDone()) {
                    f.cancel(true);
                    e.inFlight = null; // DO NOT touch inFlight counter here
                }

                if (e.mesh != null) continue;

                MeshData md = builder.build(cx, cz);
                if (md == null || md.isEmpty()) continue;

                e.mesh = uploader.upload(md);
                built++;
            }
        }

        return built;
    }

    /**
     * Request meshes in a square radius around (centerCx, centerCz).
     * Submits up to submitBudget jobs per call. Prioritizes close chunks (rings).
     */
    public void requestRadius(int centerCx, int centerCz, int radiusChunks, int submitBudget, MeshBuilder builder) {
        if (submitBudget <= 0) return;
        if (inFlight.get() >= maxInFlight) return;

        for (int r = 0; r <= radiusChunks && submitBudget > 0; r++) {
            for (int dx = -r; dx <= r && submitBudget > 0; dx++) {
                submitBudget = trySubmit(centerCx + dx, centerCz - r, submitBudget, builder);
                if (r != 0) submitBudget = trySubmit(centerCx + dx, centerCz + r, submitBudget, builder);
            }
            for (int dz = -r + 1; dz <= r - 1 && submitBudget > 0; dz++) {
                submitBudget = trySubmit(centerCx - r, centerCz + dz, submitBudget, builder);
                if (r != 0) submitBudget = trySubmit(centerCx + r, centerCz + dz, submitBudget, builder);
            }
        }
    }

    /**
     * Render thread only.
     * Upload completed MeshData into GL mesh (budgeted).
     */
    public int uploadReady(int uploadBudget) {
        int uploaded = 0;
        while (uploaded < uploadBudget) {
            Ready r = readyQueue.poll();
            if (r == null) break;

            Entry e = entries.get(r.key);
            if (e == null) continue;

            e.inFlight = null;

            MeshData md = r.data;
            if (md == null || md.isEmpty()) continue;

            IGlMesh old = e.mesh;
            if (old != null) old.close();

            e.mesh = uploader.upload(md);
            uploaded++;
        }
        return uploaded;
    }

    public void drawAll() {
        for (Entry e : entries.values()) {
            IGlMesh m = e.mesh;
            if (m != null) m.draw();
        }
    }

    /**
     * Render thread only.
     * Evict outside keepRadius; closes GL meshes and cancels in-flight tasks.
     */
    public void evictOutside(int centerCx, int centerCz, int keepRadiusChunks) {
        for (var it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Entry> me = it.next();
            long key = me.getKey();
            int cx = ChunkKey.unpackX(key);
            int cz = ChunkKey.unpackZ(key);

            if (Math.abs(cx - centerCx) <= keepRadiusChunks && Math.abs(cz - centerCz) <= keepRadiusChunks) {
                continue;
            }

            Entry e = me.getValue();

            IGlMesh m = e.mesh;
            if (m != null) m.close();

            CompletableFuture<MeshData> f = e.inFlight;
            if (f != null && !f.isDone()) f.cancel(true);

            it.remove();
        }
    }

    private int trySubmit(int cx, int cz, int submitBudget, MeshBuilder builder) {
        if (submitBudget <= 0) return 0;
        if (inFlight.get() >= maxInFlight) return submitBudget;

        long key = ChunkKey.pack(cx, cz);
        Entry e = entries.computeIfAbsent(key, k -> new Entry());

        if (e.mesh != null) return submitBudget;
        CompletableFuture<MeshData> existing = e.inFlight;
        if (existing != null && !existing.isDone()) return submitBudget;

        if (inFlight.incrementAndGet() > maxInFlight) {
            inFlight.decrementAndGet();
            return submitBudget;
        }

        CompletableFuture<MeshData> fut = CompletableFuture.supplyAsync(() -> builder.build(cx, cz), executor);
        e.inFlight = fut;

        fut.whenComplete((md, ex) -> {
            inFlight.decrementAndGet();
            if (fut.isCancelled() || ex != null) return;
            readyQueue.add(new Ready(key, md));
        });

        return submitBudget - 1;
    }

    @Override
    public void close() {
        for (Entry e : entries.values()) {
            IGlMesh m = e.mesh;
            if (m != null) m.close();

            CompletableFuture<MeshData> f = e.inFlight;
            if (f != null && !f.isDone()) f.cancel(true);
        }
        entries.clear();
        readyQueue.clear();
        executor.shutdownNow();
    }

    /**
     * Render thread only (intended usage).
     * Inject a pre-built MeshData into this cache so it will be uploaded by uploadReady().
     * This DOES NOT schedule worker jobs and DOES NOT touch the inFlight counter.
     */
    public void requestOne(int cx, int cz, MeshData mesh) {
        if (mesh == null || mesh.isEmpty()) return;

        long key = ChunkKey.pack(cx, cz);

        // Ensure entry exists so draw/evict bookkeeping works
        Entry e = entries.computeIfAbsent(key, k -> new Entry());

        // If there is some stale inFlight future, cancel it (should not happen for side caches,
        // but this makes the method safe to call in all cases).
        CompletableFuture<MeshData> f = e.inFlight;
        if (f != null && !f.isDone()) {
            f.cancel(true);
        }
        e.inFlight = null;

        // Enqueue for GL upload (actual upload happens on render thread in uploadReady()).
        readyQueue.add(new Ready(key, mesh));
    }

    /**
     * Render thread only.
     * Ensure entries exist for all chunks in radius so:
     * - evictOutside() keeps them alive
     * - requestOne() can safely enqueue uploads for them
     * NOTE: This does not schedule builds; it only "touches" the cache geometry.
     */
    public void touchRadius(int centerCx, int centerCz, int radiusChunks) {
        for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
            for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
                int cx = centerCx + dx;
                int cz = centerCz + dz;

                long key = ChunkKey.pack(cx, cz);
                entries.computeIfAbsent(key, k -> new Entry());
            }
        }
    }

    /**
     * Render thread only.
     * Draw meshes sorted back-to-front (far -> near) relative to camera X/Z.
     * Intended for TRANSLUCENT pass.
     */
    public void drawSorted(float camX, float camZ) {
        // Collect visible meshes (avoid allocating too much: capacity ~ entries.size())
        ArrayList<DrawItem> list = new ArrayList<>(entries.size());

        for (var me : entries.entrySet()) {
            Entry e = me.getValue();
            IGlMesh m = e.mesh;
            if (m == null) continue;

            long key = me.getKey();
            int cx = ChunkKey.unpackX(key);
            int cz = ChunkKey.unpackZ(key);

            // chunk center in world space (x/z). Use 0.5 to center the chunk.
            // We don't know CHUNK_SIZE here; but you can derive it if you want.
            // Since cx/cz are in chunk coords, distance in chunk units is fine for sorting.
            float dx = (cx + 0.5f) - camX;
            float dz = (cz + 0.5f) - camZ;
            float d2 = dx * dx + dz * dz;

            list.add(new DrawItem(m, d2));
        }

        // Far -> near
        list.sort(Comparator.comparingDouble(DrawItem::d2).reversed());

        for (DrawItem it : list) {
            it.mesh.draw();
        }
    }

    public int drawAllCulled(FrustumIntersection frustum, int chunkSize, float minY, float maxY) {
        int drawn = 0;

        for (var me : entries.entrySet()) {
            Entry e = me.getValue();
            IGlMesh m = e.mesh;
            if (m == null) continue;

            long key = me.getKey();
            int cx = ChunkKey.unpackX(key);
            int cz = ChunkKey.unpackZ(key);

            float minX = (float) cx * chunkSize;
            float minZ = (float) cz * chunkSize;
            float maxX = minX + chunkSize;
            float maxZ = minZ + chunkSize;

            if (!frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ)) continue;

            m.draw();
            drawn++;
        }

        return drawn;
    }

    public int drawSortedCulled(float camX, float camZ, FrustumIntersection frustum, int chunkSize, float minY, float maxY) {
        drawList.clear();

        for (var me : entries.entrySet()) {
            Entry e = me.getValue();
            IGlMesh m = e.mesh;
            if (m == null) continue;

            long key = me.getKey();
            int cx = ChunkKey.unpackX(key);
            int cz = ChunkKey.unpackZ(key);

            float minX = (float) cx * chunkSize;
            float minZ = (float) cz * chunkSize;
            float maxX = minX + chunkSize;
            float maxZ = minZ + chunkSize;

            if (!frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ)) continue;

            // Sorting in chunk units is fine (camX/camZ are chunk-space in your renderer)
            float dx = (cx + 0.5f) - camX;
            float dz = (cz + 0.5f) - camZ;
            float d2 = dx * dx + dz * dz;

            drawList.add(new DrawItem(m, d2));
        }

        // Far -> near
        drawList.sort(Comparator.comparingDouble(DrawItem::d2).reversed());

        for (DrawItem it : drawList) {
            it.mesh.draw();
        }

        return drawList.size();
    }
}