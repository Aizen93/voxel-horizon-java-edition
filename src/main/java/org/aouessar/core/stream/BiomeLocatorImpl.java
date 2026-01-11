package org.aouessar.core.stream;

import org.aouessar.core.api.BiomeLocator;
import org.aouessar.core.api.WorldSampler;
import org.aouessar.core.world.RegionPos;
import org.aouessar.core.world.WorldGrid;
import org.aouessar.shared.EngineConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BiomeLocatorImpl implements BiomeLocator {

    private final RegionStreamingService stream; // ensures regions
    private final WorldSampler sampler;          // reads biome/height

    public BiomeLocatorImpl(RegionStreamingService stream) {
        this.stream = Objects.requireNonNull(stream);
        this.sampler = stream; // stream implements WorldSampler
    }

    @Override
    public Optional<BiomeHit> findNearestBiome(int startWx, int startWz, int targetBiomeId, int maxRadiusBlocks) {
        // If already in biome, return immediately
        stream.ensureRegionForWorld(startWx, startWz).join();
        if (sampler.biomeIdAt(startWx, startWz) == targetBiomeId) {
            return Optional.of(new BiomeHit(startWx, startWz, 0));
        }

        // Coarse-to-fine search tuned for "minecraft-like big biomes"
        final int coarseStep = 256;   // blocks (fast)
        final int fineStep   = 64;    // blocks (better)
        final int finalStep  = 16;    // blocks (near hit)
        final int maxR = Math.max(EngineConfig.REGION_SIZE_BLOCKS, maxRadiusBlocks);

        Hit best = null;

        // 1) Coarse ring scan
        best = ringScan(startWx, startWz, targetBiomeId, maxR, coarseStep, best);
        if (best == null) return Optional.empty();

        // 2) Fine scan around coarse hit
        best = boxScan(startWx, startWz, targetBiomeId, best.wx, best.wz, 1024, fineStep, best);

        // 3) Final refine around best
        best = boxScan(startWx, startWz, targetBiomeId, best.wx, best.wz, 256, finalStep, best);

        return Optional.of(new BiomeHit(best.wx, best.wz, best.dist));
    }

    private Hit ringScan(int sx, int sz, int biome, int maxR, int step, Hit best) {
        for (int r = step; r <= maxR; r += step) {
            // schedule all regions touched by this ring first (parallel generation)
            warmupRing(sx, sz, r, step);

            int minX = sx - r, maxX = sx + r;
            int minZ = sz - r, maxZ = sz + r;

            // top/bottom
            for (int x = minX; x <= maxX; x += step) {
                best = consider(sx, sz, biome, x, minZ, best);
                best = consider(sx, sz, biome, x, maxZ, best);
            }
            // left/right
            for (int z = minZ; z <= maxZ; z += step) {
                best = consider(sx, sz, biome, minX, z, best);
                best = consider(sx, sz, biome, maxX, z, best);
            }

            if (best != null && best.dist <= r) {
                // good enough to start refining; biome areas are large, so no need to keep expanding far
                return best;
            }
        }
        return best;
    }

    private Hit boxScan(int sx, int sz, int biome, int cx, int cz, int radius, int step, Hit best) {
        int minX = cx - radius, maxX = cx + radius;
        int minZ = cz - radius, maxZ = cz + radius;

        // warmup all regions in this box (still parallel)
        warmupBox(minX, minZ, maxX, maxZ, step);

        for (int z = minZ; z <= maxZ; z += step) {
            for (int x = minX; x <= maxX; x += step) {
                best = consider(sx, sz, biome, x, z, best);
            }
        }
        return best;
    }

    private Hit consider(int sx, int sz, int biome, int wx, int wz, Hit best) {
        // region should be ready due to warmup; but safe anyway
        if (sampler.biomeIdAt(wx, wz) != biome) return best;

        int dx = wx - sx;
        int dz = wz - sz;
        int d2 = dx * dx + dz * dz;

        if (best == null || d2 < best.dist2) {
            int dist = (int) Math.floor(Math.sqrt(d2));
            return new Hit(wx, wz, dist, d2);
        }
        return best;
    }

    private void warmupRing(int sx, int sz, int r, int step) {
        int minX = sx - r, maxX = sx + r;
        int minZ = sz - r, maxZ = sz + r;

        Set<RegionPos> regions = new HashSet<>();

        for (int x = minX; x <= maxX; x += step) {
            regions.add(regionOfWorld(x, minZ));
            regions.add(regionOfWorld(x, maxZ));
        }
        for (int z = minZ; z <= maxZ; z += step) {
            regions.add(regionOfWorld(minX, z));
            regions.add(regionOfWorld(maxX, z));
        }

        waitAll(regions);
    }

    private void warmupBox(int minX, int minZ, int maxX, int maxZ, int step) {
        Set<RegionPos> regions = new HashSet<>();
        for (int z = minZ; z <= maxZ; z += step) {
            for (int x = minX; x <= maxX; x += step) {
                regions.add(regionOfWorld(x, z));
            }
        }
        waitAll(regions);
    }

    private void waitAll(Set<RegionPos> regions) {
        if (regions.isEmpty()) return;
        List<CompletableFuture<?>> futures = new ArrayList<>(regions.size());
        for (RegionPos rp : regions) futures.add(stream.ensureRegion(rp));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private static RegionPos regionOfWorld(int wx, int wz) {
        int cx = WorldGrid.worldBlockToChunkX(wx);
        int cz = WorldGrid.worldBlockToChunkZ(wz);
        return WorldGrid.regionOfChunk(cx, cz);
    }

    private static final class Hit {
        final int wx, wz, dist, dist2;
        Hit(int wx, int wz, int dist, int dist2) {
            this.wx = wx; this.wz = wz; this.dist = dist; this.dist2 = dist2;
        }
    }
}