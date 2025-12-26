package org.aouessar.renderer.world;

import org.aouessar.renderer.gl.GlMesh;
import org.aouessar.renderer.mesh.MeshData;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkMeshCache implements AutoCloseable {

    @FunctionalInterface
    public interface MeshBuilder {
        MeshData build(int cx, int cz);
    }

    private static final class Entry {
        volatile GlMesh mesh;                          // render thread only (creation/close)
        volatile CompletableFuture<MeshData> inFlight; // worker threads
    }

    private record Ready(long key, MeshData data) {}

    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Ready> readyQueue = new ConcurrentLinkedQueue<>();

    private final ExecutorService executor;
    private final int maxInFlight;
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public ChunkMeshCache(int workerThreads, int maxInFlight) {
        this.maxInFlight = Math.max(1, maxInFlight);
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, workerThreads),
                r -> {
                    Thread t = new Thread(r, "mesh-cpu");
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /** Good defaults. */
    public ChunkMeshCache() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() - 1), 128);
    }

    /** O(1) */
    public int inFlightCount() {
        return inFlight.get();
    }

    public int size() {
        return entries.size();
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

                // If there is an async job, cancel it — we want the mesh NOW.
                CompletableFuture<MeshData> f = e.inFlight;
                if (f != null && !f.isDone()) {
                    f.cancel(true);
                    e.inFlight = null; // DO NOT touch inFlight counter here
                }

                if (e.mesh != null) continue;

                MeshData md = builder.build(cx, cz);
                if (md == null || md.isEmpty()) {
                    continue;
                }

                e.mesh = new GlMesh(md);
                built++;
            }
        }

        return built;
    }

    public int entryCount() { return entries.size(); }

    public int meshCount() {
        int n = 0;
        for (Entry e : entries.values()) if (e.mesh != null) n++;
        return n;
    }

    /**
     * Request meshes in a square radius around (centerCx, centerCz).
     * Submits up to submitBudget jobs per call. Prioritizes close chunks (rings).
     */
    public void requestRadius(int centerCx, int centerCz, int radiusChunks, int submitBudget, MeshBuilder builder) {
        if (submitBudget <= 0) return;
        if (inFlight.get() >= maxInFlight) return;

        for (int r = 0; r <= radiusChunks && submitBudget > 0; r++) {
            // top & bottom edges
            for (int dx = -r; dx <= r && submitBudget > 0; dx++) {
                submitBudget = trySubmit(centerCx + dx, centerCz - r, submitBudget, builder);
                if (r != 0) submitBudget = trySubmit(centerCx + dx, centerCz + r, submitBudget, builder);
            }
            // left & right edges (excluding corners)
            for (int dz = -r + 1; dz <= r - 1 && submitBudget > 0; dz++) {
                submitBudget = trySubmit(centerCx - r, centerCz + dz, submitBudget, builder);
                if (r != 0) submitBudget = trySubmit(centerCx + r, centerCz + dz, submitBudget, builder);
            }
        }
    }

    /**
     * Render thread only.
     * Upload completed MeshData into GlMesh (budgeted).
     */
    public int uploadReady(int uploadBudget) {
        int uploaded = 0;
        while (uploaded < uploadBudget) {
            Ready r = readyQueue.poll();
            if (r == null) break;

            Entry e = entries.get(r.key);
            if (e == null) continue; // evicted before upload

            // clear inFlight marker (job done)
            e.inFlight = null;

            MeshData md = r.data;
            if (md == null || md.isEmpty()) {
                continue;
            }

            GlMesh old = e.mesh;
            if (old != null) old.close();

            e.mesh = new GlMesh(md);
            uploaded++;
        }
        return uploaded;
    }

    public void drawAll() {
        for (Entry e : entries.values()) {
            GlMesh m = e.mesh;
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

            GlMesh m = e.mesh;
            if (m != null) m.close();

            CompletableFuture<MeshData> f = e.inFlight;
            if (f != null && !f.isDone()) {
                f.cancel(true);
            }

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

        // reserve a slot *before* submitting
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
            GlMesh m = e.mesh;
            if (m != null) m.close();

            CompletableFuture<MeshData> f = e.inFlight;
            if (f != null && !f.isDone()) f.cancel(true);
        }
        entries.clear();
        readyQueue.clear();

        executor.shutdownNow();
    }
}