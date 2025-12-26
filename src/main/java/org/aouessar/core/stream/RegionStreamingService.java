package org.aouessar.core.stream;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.api.WorldSampler;
import org.aouessar.core.gen.RegionPipeline;
import org.aouessar.core.world.*;
import org.aouessar.core.world.chunk.Chunk;
import org.aouessar.core.world.chunk.ChunkBuilder;
import org.aouessar.core.world.layers.LayerRect;
import org.aouessar.shared.EngineConfig;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public final class RegionStreamingService implements ChunkProvider, WorldSampler, AutoCloseable {

    private final long seed;
    private final RegionPipeline pipeline;

    private final ExecutorService regionExecutor;

    // Ready regions (cache). Production-grade: you can later swap to Caffeine.
    private final ConcurrentHashMap<RegionPos, Region> ready = new ConcurrentHashMap<>();

    // In-flight region builds (dedup).
    private final ConcurrentHashMap<RegionPos, CompletableFuture<Region>> inFlight = new ConcurrentHashMap<>();

    // Optional small chunk cache (derived). Keep bounded later.
    private final ConcurrentHashMap<ChunkPos, Chunk> chunkCache = new ConcurrentHashMap<>();

    private final ChunkBuilder chunkBuilder = new ChunkBuilder();

    public RegionStreamingService(long seed, RegionPipeline pipeline) {
        this(seed, pipeline, Executors.newFixedThreadPool(EngineConfig.CPU_WORKERS, new NamedThreadFactory("region-gen")));
    }

    public RegionStreamingService(long seed, RegionPipeline pipeline, ExecutorService regionExecutor) {
        this.seed = seed;
        this.pipeline = Objects.requireNonNull(pipeline);
        this.regionExecutor = Objects.requireNonNull(regionExecutor);
    }

    // -----------------------
    // Public API: WorldSampler
    // -----------------------

    @Override
    public int heightAt(int wx, int wz) {
        Region region = tryGetReadyRegionForWorld(wx, wz);
        if (region == null) {
            scheduleRegionForWorld(wx, wz);
            return EngineConfig.SEA_LEVEL; // deterministic placeholder
        }
        return region.layers().heightmap().heightAt(wx, wz);
    }

    @Override
    public int biomeIdAt(int wx, int wz) {
        Region region = tryGetReadyRegionForWorld(wx, wz);
        if (region == null) {
            scheduleRegionForWorld(wx, wz);
            return 0; // default biome placeholder
        }
        return region.layers().biomeMap().biomeIdAt(wx, wz);
    }

    @Override
    public short surfaceBlockAt(int wx, int wz) {
        Region region = tryGetReadyRegionForWorld(wx, wz);
        if (region == null) {
            scheduleRegionForWorld(wx, wz);
            return 0; // default block placeholder (AIR)
        }
        return region.layers().surfaceRules().topBlockAt(wx, wz);
    }

    // -----------------------
    // Public API: ChunkProvider
    // -----------------------

    @Override
    public Chunk getChunk(int cx, int cz) {
        ChunkPos cp = new ChunkPos(cx, cz);

        // Fast path: return cached chunk if present
        Chunk cached = chunkCache.get(cp);
        if (cached != null) return cached;

        RegionPos rp = WorldGrid.regionOfChunk(cx, cz);
        Region region = ready.get(rp);
        if (region == null) {
            scheduleRegion(rp);
            return Chunk.emptyAir(cx, cz); // deterministic placeholder
        }

        // Build deterministically from region layers (no async here; async is for region generation)
        Chunk real = chunkBuilder.buildChunk(region, cx, cz);
        chunkCache.put(cp, real);
        return real;
    }

    // -----------------------
    // Region scheduling / building
    // -----------------------

    private Region tryGetReadyRegionForWorld(int wx, int wz) {
        int cx = WorldGrid.worldBlockToChunkX(wx);
        int cz = WorldGrid.worldBlockToChunkZ(wz);
        RegionPos rp = WorldGrid.regionOfChunk(cx, cz);
        return ready.get(rp);
    }

    private void scheduleRegionForWorld(int wx, int wz) {
        int cx = WorldGrid.worldBlockToChunkX(wx);
        int cz = WorldGrid.worldBlockToChunkZ(wz);
        scheduleRegion(WorldGrid.regionOfChunk(cx, cz));
    }

    private void scheduleRegion(RegionPos rp) {
        // Deduplicate in-flight builds.
        inFlight.computeIfAbsent(rp, pos ->
                CompletableFuture.supplyAsync(() -> buildRegion(pos), regionExecutor)
                        .whenComplete((region, err) -> {
                            inFlight.remove(pos);
                            if (err == null && region != null) {
                                ready.put(pos, region);
                                // Optional: invalidate derived chunk cache for chunks in this region
                                // For now, keep simple: clear all chunk cache entries for that region.
                                invalidateChunksInRegion(pos);
                            } else {
                                // In production you would log this.
                                // Keep system alive: next request will reschedule.
                            }
                        })
        );
    }

    private Region buildRegion(RegionPos pos) {
        LayerRect rect = RegionRect.rectOf(pos);
        var layers = pipeline.generateRegionLayers(seed, rect);
        return new Region(pos, rect, layers);
    }

    private void invalidateChunksInRegion(RegionPos regionPos) {
        // Simple correctness-first invalidation.
        // Later: keep per-region chunk key sets or use a bounded cache library.
        for (Map.Entry<ChunkPos, Chunk> e : chunkCache.entrySet()) {
            ChunkPos cp = e.getKey();
            RegionPos rp = WorldGrid.regionOfChunk(cp.cx(), cp.cz());
            if (rp.equals(regionPos)) {
                chunkCache.remove(cp);
            }
        }
    }

    @Override
    public void close() {
        regionExecutor.shutdownNow();
    }

    // -----------------------
    // Thread factory (nice names)
    // -----------------------
    private static final class NamedThreadFactory implements ThreadFactory {
        private final String base;
        private final ThreadFactory delegate = Executors.defaultThreadFactory();
        private final AtomicInteger idx = new AtomicInteger();

        private NamedThreadFactory(String base) { this.base = base; }

        @Override public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            t.setName(base + "-" + idx.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    //We can replace it with java.util.concurrent.atomic.AtomicInteger
    private static final class AtomicInteger {
        private int v;
        int incrementAndGet() { return ++v; }
    }
}