package org.aouessar.core.stream;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.api.StreamingControl;
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

public final class RegionStreamingService implements ChunkProvider, WorldSampler, StreamingControl, AutoCloseable {

    private final long seed;
    private final RegionPipeline pipeline;
    private final ExecutorService regionExecutor;
    private final ChunkBuilder chunkBuilder;

    // Ready regions (cache). Production-grade: you can later swap to Caffeine.
    private final ConcurrentHashMap<RegionPos, Region> ready = new ConcurrentHashMap<>();
    // In-flight region builds (dedup).
    private final ConcurrentHashMap<RegionPos, CompletableFuture<Region>> inFlight = new ConcurrentHashMap<>();
    // Optional small chunk cache (derived). Keep bounded later.
    private final ConcurrentHashMap<ChunkPos, Chunk> chunkCache = new ConcurrentHashMap<>();

    // Eviction configuration (chunks)
    private static final int DEFAULT_EVICT_RADIUS_CHUNKS = 48; // ~3 regions from center


    public RegionStreamingService(long seed, RegionPipeline pipeline) {
        this(seed, pipeline, Executors.newFixedThreadPool(EngineConfig.CPU_WORKERS, new NamedThreadFactory("region-gen")));
    }

    public RegionStreamingService(long seed, RegionPipeline pipeline, ExecutorService regionExecutor) {
        this.seed = seed;
        this.pipeline = Objects.requireNonNull(pipeline);
        this.regionExecutor = Objects.requireNonNull(regionExecutor);
        this.chunkBuilder = new ChunkBuilder();
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
            return Chunk.emptyChunk(cx, cz); // deterministic placeholder
        }

        // Build deterministically from region layers (no async here; async is for region generation)
        Chunk real = chunkBuilder.buildChunk(seed, region, cx, cz);
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


    private Region buildRegion(RegionPos pos) {
        LayerRect baseRect = RegionRect.rectOf(pos);

        int pad = EngineConfig.REGION_LAYER_PAD_BLOCKS;

        LayerRect paddedRect = new LayerRect(
                baseRect.minX - pad,
                baseRect.minZ - pad,
                baseRect.sizeX + pad * 2,
                baseRect.sizeZ + pad * 2
        );

        var layers = pipeline.generateRegionLayers(seed, paddedRect);

        // Keep baseRect as the "region identity rect" if you want,
        // but layers MUST be paddedRect for seam-safe sampling/structures.
        return new Region(pos, baseRect, layers);
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
    // Eviction (memory management)
    // -----------------------

    /**
     * Evict regions and chunks outside the given radius (in chunks) from the center.
     * Call this periodically from the renderer to prevent memory leaks.
     */
    @Override
    public void evictOutside(int centerCx, int centerCz, int radiusChunks) {
        // Evict regions: a region is kept if ANY chunk within it is inside the radius
        int regionRadiusChunks = radiusChunks + EngineConfig.REGION_SIZE_CHUNKS;
        int centerRx = WorldGrid.regionOfChunk(centerCx, centerCz).rx();
        int centerRz = WorldGrid.regionOfChunk(centerCx, centerCz).rz();

        for (var it = ready.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<RegionPos, Region> entry = it.next();
            RegionPos rp = entry.getKey();

            // Distance in chunks from region center to player chunk
            int regionCenterCx = rp.rx() * EngineConfig.REGION_SIZE_CHUNKS + EngineConfig.REGION_SIZE_CHUNKS / 2;
            int regionCenterCz = rp.rz() * EngineConfig.REGION_SIZE_CHUNKS + EngineConfig.REGION_SIZE_CHUNKS / 2;

            int dx = Math.abs(regionCenterCx - centerCx);
            int dz = Math.abs(regionCenterCz - centerCz);

            if (dx > regionRadiusChunks || dz > regionRadiusChunks) {
                it.remove();
            }
        }

        // Cancel in-flight builds that are too far away
        for (var it = inFlight.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<RegionPos, CompletableFuture<Region>> entry = it.next();
            RegionPos rp = entry.getKey();

            int regionCenterCx = rp.rx() * EngineConfig.REGION_SIZE_CHUNKS + EngineConfig.REGION_SIZE_CHUNKS / 2;
            int regionCenterCz = rp.rz() * EngineConfig.REGION_SIZE_CHUNKS + EngineConfig.REGION_SIZE_CHUNKS / 2;

            int dx = Math.abs(regionCenterCx - centerCx);
            int dz = Math.abs(regionCenterCz - centerCz);

            if (dx > regionRadiusChunks || dz > regionRadiusChunks) {
                CompletableFuture<Region> future = entry.getValue();
                if (!future.isDone()) {
                    future.cancel(true);
                }
                it.remove();
            }
        }

        // Evict chunks outside radius
        for (var it = chunkCache.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ChunkPos, Chunk> entry = it.next();
            ChunkPos cp = entry.getKey();

            int dx = Math.abs(cp.cx() - centerCx);
            int dz = Math.abs(cp.cz() - centerCz);

            if (dx > radiusChunks || dz > radiusChunks) {
                it.remove();
            }
        }
    }

    /**
     * Evict with default radius
     */
    public void evictOutside(int centerCx, int centerCz) {
        evictOutside(centerCx, centerCz, DEFAULT_EVICT_RADIUS_CHUNKS);
    }

    /**
     * Get the number of cached regions (for debug overlay)
     */
    @Override
    public int regionCount() {
        return ready.size();
    }

    /**
     * Get the number of cached chunks (for debug overlay)
     */
    @Override
    public int chunkCount() {
        return chunkCache.size();
    }

    // -----------------------
    // For Biome Search Algorithm
    // -----------------------

    public CompletableFuture<Void> ensureRegionForWorld(int wx, int wz) {
        int cx = WorldGrid.worldBlockToChunkX(wx);
        int cz = WorldGrid.worldBlockToChunkZ(wz);
        RegionPos rp = WorldGrid.regionOfChunk(cx, cz);
        return ensureRegion(rp).thenApply(r -> null);
    }

    public CompletableFuture<Region> ensureRegion(RegionPos rp) {
        Region r = ready.get(rp);
        if (r != null) return CompletableFuture.completedFuture(r);
        return startRegionBuild(rp);
    }

    // refactor scheduleRegion to use this
    private CompletableFuture<Region> startRegionBuild(RegionPos rp) {
        return inFlight.computeIfAbsent(rp, pos ->
            CompletableFuture.supplyAsync(() -> buildRegion(pos), regionExecutor)
                .whenComplete((region, err) -> {
                    inFlight.remove(pos);
                    if (err == null && region != null) {
                        ready.put(pos, region);
                        invalidateChunksInRegion(pos);
                    }
                })
        );
    }

    private void scheduleRegion(RegionPos rp) {
        ensureRegion(rp);
    }
}