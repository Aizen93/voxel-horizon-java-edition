package org.aouessar.core.gen.impl;

import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.chunk.Chunk;
import org.aouessar.core.world.layers.RegionLayers;
import org.aouessar.core.world.layers.WaterLayer;
import org.aouessar.shared.EngineConfig;

import java.util.Random;

/**
 * Classic Minecraft cave generation: Perlin-worm tunnel carvers.
 * <p>
 * Every chunk in a {@link EngineConfig#CAVE_RANGE_CHUNKS} radius around the
 * chunk being built gets a deterministic RNG seeded from (world seed, chunk
 * coords). If that origin chunk hosts a cave system, its tunnels are simulated
 * in full and only the blocks that land inside the target chunk are carved —
 * so tunnels cross chunk borders seamlessly and every chunk builds the same
 * caves no matter the build order.
 * <p>
 * Shape recipe (faithful to the classic carver):
 * <ul>
 *   <li>Tunnels are a random walk with momentum: yaw/pitch drift accumulates,
 *       radius swells sinusoidally along the length, occasional "pinch" steps
 *       skip carving to vary the profile.</li>
 *   <li>1 in {@link EngineConfig#CAVE_ROOM_CHANCE} systems opens with a large
 *       squashed-sphere room that spawns several tunnels outward.</li>
 *   <li>Wide tunnels split into two branches at mid-length (yaw ± 90°).</li>
 *   <li>The bottom 30% of each carve ellipsoid is skipped, giving tunnels the
 *       classic flat-ish walkable floor.</li>
 * </ul>
 * Entrances need no special casing: a worm that wanders above the heightmap
 * pierces a hillside, meadow or riverbed. Water handling makes those breaches
 * safe — a carved cell in a column that has water (ocean/river/lake), at or
 * below its water level, fills with WATER instead of AIR, so seabed breaches
 * become flooded, diveable cave mouths instead of dry holes under the sea.
 */
public final class CaveCarver {

    private CaveCarver() {}

    private static final int CS = EngineConfig.CHUNK_SIZE;
    /** Bottom of the carvable range: keep a floor above the bedrock slab. */
    private static final int FLOOR_Y = EngineConfig.MIN_Y + EngineConfig.BEDROCK_THICKNESS;

    /** Carve all cave systems that can reach this chunk. Deterministic. */
    public static void carve(long seed, Chunk chunk, RegionLayers layers) {
        Random rootRng = new Random(seed);
        long xMul = rootRng.nextLong() | 1L;
        long zMul = rootRng.nextLong() | 1L;

        final int range = EngineConfig.CAVE_RANGE_CHUNKS;
        final int cx = chunk.cx();
        final int cz = chunk.cz();

        for (int ocx = cx - range; ocx <= cx + range; ocx++) {
            for (int ocz = cz - range; ocz <= cz + range; ocz++) {
                long chunkSeed = (long) ocx * xMul ^ (long) ocz * zMul ^ seed;
                carveSystemsFromOrigin(new Random(chunkSeed), ocx, ocz, chunk, layers);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cave system layout (per origin chunk)
    // -------------------------------------------------------------------------

    private static void carveSystemsFromOrigin(Random rng, int ocx, int ocz, Chunk out, RegionLayers layers) {
        int systems = rng.nextInt(rng.nextInt(rng.nextInt(EngineConfig.CAVE_SYSTEMS_MAX) + 1) + 1);
        if (rng.nextInt(EngineConfig.CAVE_SYSTEM_CHANCE) != 0) systems = 0;

        for (int s = 0; s < systems; s++) {
            double x = ocx * CS + rng.nextInt(CS);
            double y = EngineConfig.CAVE_MIN_Y + rng.nextInt(rng.nextInt(EngineConfig.CAVE_Y_SPAN) + 8);
            double z = ocz * CS + rng.nextInt(CS);

            int tunnels = 1;
            if (rng.nextInt(EngineConfig.CAVE_ROOM_CHANCE) == 0) {
                float roomWidth = 1f + rng.nextFloat() * EngineConfig.CAVE_ROOM_MAX_EXTRA_RADIUS;
                carveEllipsoid(out, layers, x, y, z, 1.5 + roomWidth, (1.5 + roomWidth) * 0.5);
                tunnels += rng.nextInt(4);
            }

            for (int t = 0; t < tunnels; t++) {
                float yaw = rng.nextFloat() * (float) (Math.PI * 2.0);
                float pitch = (rng.nextFloat() - 0.5f) / 4f;
                float width = rng.nextFloat() * 2f + rng.nextFloat();
                // Rare huge tunnels
                if (rng.nextInt(10) == 0) width *= rng.nextFloat() * rng.nextFloat() * 3f + 1f;

                carveTunnel(rng.nextLong(), out, layers, x, y, z, width, yaw, pitch, 0, 0, true);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Tunnel walk
    // -------------------------------------------------------------------------

    private static void carveTunnel(
            long tunnelSeed, Chunk out, RegionLayers layers,
            double x, double y, double z,
            float width, float yaw, float pitch,
            int fromStep, int maxSteps, boolean canBranch
    ) {
        Random rng = new Random(tunnelSeed);

        if (maxSteps <= 0) {
            int full = (EngineConfig.CAVE_RANGE_CHUNKS - 1) * CS;
            maxSteps = full - rng.nextInt(full / 4);
        }
        int branchStep = rng.nextInt(maxSteps / 2) + maxSteps / 4;
        boolean steep = rng.nextInt(6) == 0; // occasional diving shafts

        float yawDelta = 0f;
        float pitchDelta = 0f;

        final double targetCx = out.cx() * CS + CS / 2.0;
        final double targetCz = out.cz() * CS + CS / 2.0;

        for (int step = fromStep; step < maxSteps; step++) {
            double r = 1.5 + Math.sin(step * Math.PI / maxSteps) * width;
            double rv = r * 0.85;

            float cosPitch = (float) Math.cos(pitch);
            x += Math.cos(yaw) * cosPitch;
            y += Math.sin(pitch);
            z += Math.sin(yaw) * cosPitch;

            pitch *= steep ? 0.92f : 0.7f;
            pitch += pitchDelta * 0.1f;
            yaw += yawDelta * 0.1f;
            pitchDelta = pitchDelta * 0.9f + (rng.nextFloat() - rng.nextFloat()) * rng.nextFloat() * 2f;
            yawDelta = yawDelta * 0.75f + (rng.nextFloat() - rng.nextFloat()) * rng.nextFloat() * 4f;

            // Wide tunnels split into two arms and stop
            if (canBranch && step == branchStep && width > 1f) {
                carveTunnel(rng.nextLong(), out, layers, x, y, z,
                        rng.nextFloat() * 0.5f + 0.5f, yaw - (float) (Math.PI / 2.0), pitch / 3f,
                        step, maxSteps, false);
                carveTunnel(rng.nextLong(), out, layers, x, y, z,
                        rng.nextFloat() * 0.5f + 0.5f, yaw + (float) (Math.PI / 2.0), pitch / 3f,
                        step, maxSteps, false);
                return;
            }

            // Occasional pinch: keep walking, skip carving this step
            if (rng.nextInt(4) == 0) continue;

            // Early out once the walk can no longer reach the target chunk
            double ddx = x - targetCx;
            double ddz = z - targetCz;
            double stepsLeft = maxSteps - step;
            double reach = width + 2.0 + CS;
            if (ddx * ddx + ddz * ddz - stepsLeft * stepsLeft > reach * reach) return;

            carveEllipsoid(out, layers, x, y, z, r, rv);
        }
    }

    // -------------------------------------------------------------------------
    // Block carving
    // -------------------------------------------------------------------------

    private static void carveEllipsoid(
            Chunk out, RegionLayers layers,
            double x, double y, double z,
            double r, double rv
    ) {
        final int baseX = out.cx() * CS;
        final int baseZ = out.cz() * CS;

        int minX = Math.max((int) Math.floor(x - r), baseX);
        int maxX = Math.min((int) Math.floor(x + r), baseX + CS - 1);
        if (minX > maxX) return;

        int minZ = Math.max((int) Math.floor(z - r), baseZ);
        int maxZ = Math.min((int) Math.floor(z + r), baseZ + CS - 1);
        if (minZ > maxZ) return;

        int minY = Math.max((int) Math.floor(y - rv), FLOOR_Y);
        int maxY = Math.min((int) Math.floor(y + rv), EngineConfig.MAX_Y);
        if (minY > maxY) return;

        WaterLayer water = layers.waterLayer();

        for (int wz = minZ; wz <= maxZ; wz++) {
            double dz = (wz + 0.5 - z) / r;
            for (int wx = minX; wx <= maxX; wx++) {
                double dx = (wx + 0.5 - x) / r;
                double hh = dx * dx + dz * dz;
                if (hh >= 1.0) continue;

                int lx = wx - baseX;
                int lz = wz - baseZ;

                int waterLevel = water.waterLevelAt(wx, wz);
                boolean wetColumn = waterLevel != WaterLayer.NO_WATER;

                // Wet columns on the outline of their water body act as a
                // natural rock dam: below the water level they are never
                // carved, so flooded cave sections are sealed behind stone
                // instead of standing as open water walls against dry
                // tunnels (same idea as Minecraft's aquifer barriers).
                // The neighbor columns are always inside the padded region
                // rect, so this stays deterministic and chunk-local.
                boolean dam = wetColumn
                        && (water.waterLevelAt(wx + 1, wz) == WaterLayer.NO_WATER
                        || water.waterLevelAt(wx - 1, wz) == WaterLayer.NO_WATER
                        || water.waterLevelAt(wx, wz + 1) == WaterLayer.NO_WATER
                        || water.waterLevelAt(wx, wz - 1) == WaterLayer.NO_WATER);

                for (int wy = minY; wy <= maxY; wy++) {
                    double dy = (wy + 0.5 - y) / rv;
                    // Skip the bottom 30% of the ellipsoid: flat walkable floors
                    if (dy <= -0.7 || hh + dy * dy >= 1.0) continue;

                    boolean flood = wetColumn && wy <= waterLevel;
                    if (flood && dam) continue; // hold the groundwater back

                    short cur = out.getBlock(lx, wy, lz);
                    if (!isCarvable(cur)) continue;

                    out.setBlock(lx, wy, lz, flood ? Blocks.WATER : Blocks.AIR);

                    // Carving away a grass surface exposes dirt below: regrow
                    // the grass so entrance rims look natural.
                    if (!flood && isGrassSurface(cur) && wy - 1 >= FLOOR_Y) {
                        if (out.getBlock(lx, wy - 1, lz) == Blocks.DIRT) {
                            out.setBlock(lx, wy - 1, lz, cur);
                        }
                    }
                }
            }
        }
    }

    private static boolean isGrassSurface(short id) {
        return id == Blocks.GRASS || id == Blocks.SNOW_GRASS || id == Blocks.DRY_GRASS;
    }

    /**
     * Terrain materials a cave may eat through. Water, bedrock, ice and all
     * structure blocks (logs, leaves, plants) are left alone.
     */
    private static boolean isCarvable(short id) {
        return id == Blocks.STONE
                || id == Blocks.DEEPSLATE
                || id == Blocks.DIRT
                || id == Blocks.GRASS
                || id == Blocks.DRY_GRASS
                || id == Blocks.SNOW_GRASS
                || id == Blocks.PODZOl_DIRT
                || id == Blocks.SAND
                || id == Blocks.DESERT_SAND
                || id == Blocks.SANDSTONE
                || id == Blocks.DESERT_SANDSTONE
                || id == Blocks.GRAVEL
                || id == Blocks.CLAY
                || id == Blocks.SNOW;
    }
}
