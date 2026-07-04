package org.aouessar.core.gen.impl;

import org.aouessar.core.api.LodProvider;
import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.LodTile;
import org.aouessar.shared.EngineConfig;

/**
 * Direct-sampling far-field LOD provider.
 * <p>
 * Evaluates the SAME per-column functions as the region pipeline
 * ({@link TerrainColumnSampler} for heights, {@link ClimateColumnSampler} for
 * biomes) at LOD resolution, then reproduces {@link org.aouessar.core.world.chunk.ChunkBuilder}'s
 * visible-surface decision tree (beaches, mountain rock/snow, desert, ocean
 * floor mix) to pick a representative top block per sample.
 * <p>
 * Deliberately skipped at LOD scale: ecotone blending, structures, river
 * carving (a 3-block-deep channel is invisible past a few hundred meters).
 * <p>
 * Thread-safe and allocation-light; called from renderer worker threads.
 */
public final class LodWorldSampler implements LodProvider {

    // Seen from far away, forested biomes are mostly canopy. LOD tiles have no
    // tree structures, so a per-biome fraction of grass samples reports the
    // biome's leaf block instead — distant forests read as forests.
    // Indexed by biome id (0..8), fraction in 0..255.
    private static final int[] CANOPY_CHANCE_255 = new int[16];
    private static final short[] CANOPY_LEAVES = new short[16];

    static {
        CANOPY_CHANCE_255[EngineConfig.BIOME_PLAINS]  = 8;
        CANOPY_LEAVES[EngineConfig.BIOME_PLAINS]      = Blocks.OAK_LEAVES;
        CANOPY_CHANCE_255[EngineConfig.BIOME_FOREST]  = 140;
        CANOPY_LEAVES[EngineConfig.BIOME_FOREST]      = Blocks.OAK_LEAVES;
        CANOPY_CHANCE_255[EngineConfig.BIOME_JUNGLE]  = 215;
        CANOPY_LEAVES[EngineConfig.BIOME_JUNGLE]      = Blocks.JUNGLE_LEAVES;
        CANOPY_CHANCE_255[EngineConfig.BIOME_SAVANNA] = 18;
        CANOPY_LEAVES[EngineConfig.BIOME_SAVANNA]     = Blocks.ACACIA_LEAVES;
        CANOPY_CHANCE_255[EngineConfig.BIOME_SNOW]    = 45;
        CANOPY_LEAVES[EngineConfig.BIOME_SNOW]        = Blocks.SPRUCE_LEAVES;
        CANOPY_CHANCE_255[EngineConfig.BIOME_SWAMP]   = 60;
        CANOPY_LEAVES[EngineConfig.BIOME_SWAMP]       = Blocks.OAK_LEAVES;
    }

    private final TerrainColumnSampler terrain;
    private final ClimateColumnSampler climate;

    public LodWorldSampler(long seed) {
        this.terrain = new TerrainColumnSampler(seed);
        this.climate = new ClimateColumnSampler(seed);
    }

    @Override
    public LodTile sampleTile(int tileX, int tileZ, int step) {
        final int cells = EngineConfig.REGION_SIZE_BLOCKS / step;
        final int grid = cells + 3;
        final int originX = tileX * EngineConfig.REGION_SIZE_BLOCKS;
        final int originZ = tileZ * EngineConfig.REGION_SIZE_BLOCKS;
        final int sea = EngineConfig.SEA_LEVEL;

        int[] height = new int[grid * grid];
        int[] water = new int[grid * grid];
        short[] top = new short[grid * grid];

        // Pass 1: heights (full grid including the -1 / cells+1 border ring).
        // River valleys are already part of the terrain function, so LOD
        // heights match near-field chunks with no extra work.
        int k = 0;
        for (int j = -1; j <= cells + 1; j++) {
            int wz = originZ + j * step;
            for (int i = -1; i <= cells + 1; i++) {
                height[k++] = terrain.heightAt(originX + i * step, wz);
            }
        }

        // Pass 2: water + top block (uses grid neighbors for steepness).
        // All water in the world sits at sea level (oceans, lakes AND river
        // channels — their beds are below sea level by construction).
        k = 0;
        for (int j = -1; j <= cells + 1; j++) {
            int wz = originZ + j * step;
            for (int i = -1; i <= cells + 1; i++, k++) {
                int wx = originX + i * step;
                int h = height[k];

                int waterLevel = (h < sea) ? sea : LodTile.NO_WATER;
                water[k] = waterLevel;

                short biome = climate.biomeAt(wx, wz, h, ClimateColumnSampler.NEVER_INLAND);

                int steepness = steepnessAt(height, grid, cells, i, j, step);
                short t = topBlockFor(wx, wz, h, biome, steepness, waterLevel);
                top[k] = applyCanopy(wx, wz, h, biome, t);
            }
        }

        return new LodTile(tileX, tileZ, step, height, water, top);
    }

    /**
     * Approximates ChunkBuilder's steepness metric (max height delta to the 4
     * cardinal neighbors at 2 blocks) using grid neighbors {@code step} blocks
     * apart, rescaled to the same per-2-block units.
     */
    private static int steepnessAt(int[] height, int grid, int cells, int i, int j, int step) {
        int iw = clamp(i - 1, cells) + 1;
        int ie = clamp(i + 1, cells) + 1;
        int jn = clamp(j - 1, cells) + 1;
        int js = clamp(j + 1, cells) + 1;
        int gi = i + 1;
        int gj = j + 1;

        int c = height[gj * grid + gi];
        int maxDiff = Math.abs(c - height[gj * grid + iw]);
        maxDiff = Math.max(maxDiff, Math.abs(c - height[gj * grid + ie]));
        maxDiff = Math.max(maxDiff, Math.abs(c - height[jn * grid + gi]));
        maxDiff = Math.max(maxDiff, Math.abs(c - height[js * grid + gi]));

        return maxDiff * 2 / step;
    }

    private static int clamp(int i, int cells) {
        return Math.max(-1, Math.min(cells + 1, i));
    }

    /** Swap some grass/soil samples for canopy leaves in forested biomes. */
    private static short applyCanopy(int wx, int wz, int surfaceY, short biome, short top) {
        if (surfaceY <= EngineConfig.SEA_LEVEL + 1) return top;
        // No canopy on snow cover or in the mountain zone: trees only grow on
        // soil, and green speckles on distant snowcaps look wrong.
        if (top == Blocks.SNOW) return top;
        if (surfaceY - EngineConfig.SEA_LEVEL >= EngineConfig.MOUNTAIN_MIN_ELEVATION) return top;
        if (!Blocks.isGrassLike(top) && !Blocks.isSoil(top)) return top;

        int chance = CANOPY_CHANCE_255[biome & 15];
        if (chance == 0) return top;

        // Decorrelated from the terrain hashes so canopy doesn't align with
        // gravel/clay patches.
        int h = GlobalTerrainUtils.hash8(wx * 5 + 11, wz * 5 - 17);
        return (h < chance) ? CANOPY_LEAVES[biome & 15] : top;
    }

    /**
     * Representative visible block for a column — mirrors ChunkBuilder's
     * terrain-fill overrides (same order, same hash) so LOD colors match what
     * the near field builds at the sample coordinate.
     */
    private static short topBlockFor(int wx, int wz, int surfaceY, short biome, int steepness, int waterLevel) {
        final int sea = EngineConfig.SEA_LEVEL;
        boolean isOceanBiome = (biome == EngineConfig.BIOME_OCEAN || biome == EngineConfig.BIOME_DEEP_OCEAN);

        // ---- Ocean biomes: seabed materials ----
        if (isOceanBiome) {
            int h = GlobalTerrainUtils.hash8(wx, wz);
            if (biome == EngineConfig.BIOME_DEEP_OCEAN) {
                if (h < 80) return Blocks.CLAY;
                if (h < 180) return Blocks.GRAVEL;
                return Blocks.SAND;
            }
            if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256) return Blocks.CLAY;
            if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256 + EngineConfig.OCEAN_GRAVEL_CHANCE_PER_256) return Blocks.GRAVEL;
            return Blocks.SAND;
        }

        // ---- Beach band around sea level ----
        boolean isBeachTerrain = Math.abs(surfaceY - sea) <= EngineConfig.BEACH_BAND
                && surfaceY >= sea - 2 && surfaceY <= sea + 6;
        if (isBeachTerrain) {
            return Blocks.SAND;
        }

        // ---- Mountain terrain decoration ----
        int elevationAboveSea = surfaceY - sea;
        if (elevationAboveSea >= EngineConfig.MOUNTAIN_MIN_ELEVATION) {
            int mountainHash = GlobalTerrainUtils.hash8(wx, wz);

            boolean isCliff = steepness >= 6;
            boolean isSteepSlope = steepness >= 3;
            boolean isFlat = steepness <= 2;

            boolean aboveSnowLine = elevationAboveSea >= EngineConfig.MOUNTAIN_SNOW_LINE;
            boolean highAlpine = elevationAboveSea >= EngineConfig.MOUNTAIN_HIGH_ALPINE;

            if (isCliff) {
                return Blocks.STONE;
            }
            if (isSteepSlope) {
                return (mountainHash < 180) ? Blocks.STONE : Blocks.GRAVEL;
            }
            if (isFlat) {
                if (highAlpine) {
                    return (mountainHash < 40) ? Blocks.STONE : Blocks.SNOW;
                }
                if (aboveSnowLine) {
                    if (mountainHash < 25) return Blocks.STONE;
                    if (mountainHash < 50) return Blocks.GRAVEL;
                    return Blocks.SNOW;
                }
                if (mountainHash < 80) return Blocks.GRAVEL;
                if (mountainHash < 140) return Blocks.STONE;
                return BiomeDecorator.defaultTopBlock(biome, surfaceY);
            }
            // Gentle slopes
            if (aboveSnowLine) {
                if (mountainHash < 60) return Blocks.STONE;
                if (mountainHash < 100) return Blocks.GRAVEL;
                return Blocks.SNOW;
            }
            if (mountainHash < 100) return Blocks.STONE;
            if (mountainHash < 180) return Blocks.GRAVEL;
            return BiomeDecorator.defaultTopBlock(biome, surfaceY);
        }

        // ---- Climate biome adjustments ----
        if (biome == EngineConfig.BIOME_DESERT) {
            return Blocks.DESERT_SAND;
        }

        if (biome == EngineConfig.BIOME_SWAMP) {
            int swampHash = GlobalTerrainUtils.hash8(wx + 12345, wz + 67890);
            if (swampHash < 80) return Blocks.CLAY;
            if (swampHash < 120) return Blocks.GRAVEL;
        }

        // Underwater in non-ocean biomes (lakes / shallow water)
        if (waterLevel != LodTile.NO_WATER && surfaceY < waterLevel) {
            int h = GlobalTerrainUtils.hash8(wx, wz);
            if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256) return Blocks.CLAY;
            if (h < EngineConfig.OCEAN_CLAY_CHANCE_PER_256 + EngineConfig.OCEAN_GRAVEL_CHANCE_PER_256) return Blocks.GRAVEL;
            return Blocks.SAND;
        }

        // Snow biome: snow cover well above sea level (matches ChunkBuilder)
        if (biome == EngineConfig.BIOME_SNOW && surfaceY > sea + 8) {
            return Blocks.SNOW;
        }

        return BiomeDecorator.defaultTopBlock(biome, surfaceY);
    }
}
