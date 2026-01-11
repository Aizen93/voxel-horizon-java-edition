package org.aouessar.renderer.world;

import org.aouessar.renderer.gl.GlMesh;
import org.aouessar.renderer.gl.IGlMesh;
import org.aouessar.renderer.mesh.MeshData;
import org.joml.FrustumIntersection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    public int entryCount() {
        return entries.size();
    }

    public int meshCount() {
        int n = 0;
        for (Entry e : entries.values()) if (e.mesh != null) n++;
        return n;
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
     * Request meshes in a square radius around (centerCx, centerCz),
     * but only submit jobs for chunks whose AABB intersects the current frustum.
     * Render thread only (same intended usage as requestRadius).
     */
    public void requestRadiusCulled(
            int centerCx, int centerCz,
            int radiusChunks,
            int submitBudget,
            MeshBuilder builder,
            FrustumIntersection frustum,
            int chunkSize,
            float minY, float maxY
    ) {
        if (submitBudget <= 0) return;
        if (inFlight.get() >= maxInFlight) return;

        for (int r = 0; r <= radiusChunks && submitBudget > 0; r++) {

            for (int dx = -r; dx <= r && submitBudget > 0; dx++) {
                int cx1 = centerCx + dx;
                int cz1 = centerCz - r;
                if (chunkIntersectsFrustum(frustum, cx1, cz1, chunkSize, minY, maxY)) {
                    submitBudget = trySubmit(cx1, cz1, submitBudget, builder);
                }

                if (r != 0) {
                    int cx2 = centerCx + dx;
                    int cz2 = centerCz + r;
                    if (chunkIntersectsFrustum(frustum, cx2, cz2, chunkSize, minY, maxY)) {
                        submitBudget = trySubmit(cx2, cz2, submitBudget, builder);
                    }
                }
            }

            for (int dz = -r + 1; dz <= r - 1 && submitBudget > 0; dz++) {
                int cx1 = centerCx - r;
                int cz1 = centerCz + dz;
                if (chunkIntersectsFrustum(frustum, cx1, cz1, chunkSize, minY, maxY)) {
                    submitBudget = trySubmit(cx1, cz1, submitBudget, builder);
                }

                if (r != 0) {
                    int cx2 = centerCx + r;
                    int cz2 = centerCz + dz;
                    if (chunkIntersectsFrustum(frustum, cx2, cz2, chunkSize, minY, maxY)) {
                        submitBudget = trySubmit(cx2, cz2, submitBudget, builder);
                    }
                }
            }
        }
    }

    private static boolean chunkIntersectsFrustum(
            FrustumIntersection frustum,
            int cx, int cz,
            int chunkSize,
            float minY, float maxY
    ) {
        float minX = (float) cx * chunkSize;
        float minZ = (float) cz * chunkSize;
        float maxX = minX + chunkSize;
        float maxZ = minZ + chunkSize;
        return frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int collectVisibleKeys(
            LongKeyList out,
            FrustumIntersection frustum,
            int chunkSize,
            float minY,
            float maxY
    ) {
        out.clear();

        for (var me : entries.entrySet()) {
            Entry e = me.getValue();
            if (e.mesh == null) continue;

            long key = me.getKey();
            int cx = ChunkKey.unpackX(key);
            int cz = ChunkKey.unpackZ(key);

            float minX = (float) cx * chunkSize;
            float minZ = (float) cz * chunkSize;
            float maxX = minX + chunkSize;
            float maxZ = minZ + chunkSize;

            if (!frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ)) continue;

            out.add(key);
        }
        return out.size();
    }

    /** Render thread only. Draw exactly the keys collected earlier. */
    public int drawKeys(LongKeyList keys) {
        int drawn = 0;
        for (int i = 0; i < keys.size(); i++) {
            Entry e = entries.get(keys.get(i));
            if (e == null) continue;
            IGlMesh m = e.mesh;
            if (m == null) continue;
            m.draw();
            drawn++;
        }
        return drawn;
    }

    /** Render thread only. Sorted translucent draw using only visible keys (far -> near). */
    public int drawKeysSorted(LongKeyList keys, float camChunkX, float camChunkZ) {
        drawList.clear();

        for (int i = 0; i < keys.size(); i++) {
            long key = keys.get(i);
            Entry e = entries.get(key);
            if (e == null) continue;
            IGlMesh m = e.mesh;
            if (m == null) continue;

            int cx = ChunkKey.unpackX(key);
            int cz = ChunkKey.unpackZ(key);

            float dx = (cx + 0.5f) - camChunkX;
            float dz = (cz + 0.5f) - camChunkZ;
            float d2 = dx * dx + dz * dz;

            drawList.add(new DrawItem(m, d2));
        }

        if (drawList.size() <= 1) {
            if (!drawList.isEmpty()) drawList.get(0).mesh.draw();
            return drawList.size();
        }

        drawList.sort(Comparator.comparingDouble(DrawItem::d2).reversed());

        for (DrawItem it : drawList) it.mesh.draw();
        return drawList.size();
    }
}