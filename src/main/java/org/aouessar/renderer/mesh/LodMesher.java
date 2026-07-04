package org.aouessar.renderer.mesh;

import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.world.LodTile;
import org.aouessar.renderer.gl.GlMeshLod;
import org.aouessar.renderer.world.AtlasColorMap;
import org.aouessar.shared.EngineConfig;

import java.util.Arrays;

/**
 * Builds far-field meshes from a {@link LodTile}:
 * <ul>
 *   <li><b>terrain</b>: a vertex-colored heightfield grid with smooth normals
 *       and downward "skirts" on all four edges (hides cracks between
 *       neighboring tiles of different LOD steps)</li>
 *   <li><b>water</b>: row-merged flat quads at the water surface</li>
 * </ul>
 * Vertex layout is {@link GlMeshLod}: pos(3) + color(3) + normal(3).
 * <p>
 * Positions are in render space (array Y = world Y - MIN_Y), matching chunk
 * meshes. Terrain is biased slightly downward so that where near-field chunks
 * overlap the LOD ring, the real geometry always wins the depth test.
 */
public final class LodMesher {

    private static final int STRIDE = GlMeshLod.STRIDE_FLOATS;

    /** Push LOD terrain slightly below the identical near-field surface. */
    private static final float TERRAIN_Y_BIAS = 0.35f;

    public record LodMeshes(MeshData terrain, MeshData water) {}

    private final AtlasColorMap colors;

    public LodMesher(AtlasColorMap colors) {
        this.colors = colors;
    }

    public LodMeshes buildTileMeshes(LodTile tile) {
        return new LodMeshes(buildTerrain(tile), buildWater(tile));
    }

    // ---------------------------------------------------------------
    // Terrain
    // ---------------------------------------------------------------

    private MeshData buildTerrain(LodTile tile) {
        final int cells = tile.cells();
        final int step = tile.step();
        final int originX = tile.originX();
        final int originZ = tile.originZ();
        final float yOff = -EngineConfig.MIN_Y - TERRAIN_Y_BIAS;

        final int side = cells + 1;
        final int gridVerts = side * side;
        final int skirtVerts = 4 * side * 2;
        final int maxVerts = gridVerts + skirtVerts;
        final int maxIndices = cells * cells * 6 + 4 * cells * 6 * 2;

        float[] v = new float[maxVerts * STRIDE];
        int[] idx = new int[maxIndices];
        int vc = 0;
        int ic = 0;

        // ---- Grid vertices ----
        for (int j = 0; j <= cells; j++) {
            int wz = originZ + j * step;
            for (int i = 0; i <= cells; i++) {
                int wx = originX + i * step;
                int h = tile.heightAt(i, j);

                // Smooth normal from central differences (border ring makes
                // the -1 / +1 neighbors always available)
                float nx = tile.heightAt(i - 1, j) - tile.heightAt(i + 1, j);
                float ny = 2f * step;
                float nz = tile.heightAt(i, j - 1) - tile.heightAt(i, j + 1);
                float invLen = 1f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);

                short block = tile.topBlockAt(i, j);
                // Small deterministic brightness jitter breaks up flat color plains
                float jitter = 0.95f + 0.10f * (GlobalTerrainUtils.hash8(wx, wz) / 255f);
                float r = Math.min(1f, colors.r(block) * jitter);
                float g = Math.min(1f, colors.g(block) * jitter);
                float b = Math.min(1f, colors.b(block) * jitter);

                int o = vc * STRIDE;
                v[o]     = wx;
                v[o + 1] = h + yOff;
                v[o + 2] = wz;
                v[o + 3] = r;
                v[o + 4] = g;
                v[o + 5] = b;
                v[o + 6] = nx * invLen;
                v[o + 7] = ny * invLen;
                v[o + 8] = nz * invLen;
                vc++;
            }
        }

        // ---- Grid indices (CCW seen from above: +Y is front) ----
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int v00 = j * side + i;
                int v10 = v00 + 1;
                int v01 = v00 + side;
                int v11 = v01 + 1;

                idx[ic++] = v00; idx[ic++] = v11; idx[ic++] = v10;
                idx[ic++] = v00; idx[ic++] = v01; idx[ic++] = v11;
            }
        }

        // ---- Skirts (double-sided, so winding never matters) ----
        float skirtDepth = Math.max(6f, step * 2f);
        // north edge (j=0), south (j=cells), west (i=0), east (i=cells)
        ic = emitSkirt(tile, v, idx, vc, ic, side, 0, true, skirtDepth, yOff);
        vc += side * 2;
        ic = emitSkirt(tile, v, idx, vc, ic, side, cells, true, skirtDepth, yOff);
        vc += side * 2;
        ic = emitSkirt(tile, v, idx, vc, ic, side, 0, false, skirtDepth, yOff);
        vc += side * 2;
        ic = emitSkirt(tile, v, idx, vc, ic, side, cells, false, skirtDepth, yOff);
        vc += side * 2;

        return new MeshData(Arrays.copyOf(v, vc * STRIDE), Arrays.copyOf(idx, ic), STRIDE);
    }

    /**
     * Emits one edge skirt: the edge vertices duplicated at the surface and at
     * (surface - skirtDepth), stitched into double-sided quads.
     *
     * @param fixed    the fixed grid coordinate of this edge (0 or cells)
     * @param edgeIsZ  true when the edge runs along X at fixed j; false when it
     *                 runs along Z at fixed i
     */
    private int emitSkirt(
            LodTile tile, float[] v, int[] idx,
            int vBase, int ic, int side, int fixed, boolean edgeIsZ,
            float skirtDepth, float yOff
    ) {
        final int cells = side - 1;
        final int step = tile.step();
        final int originX = tile.originX();
        final int originZ = tile.originZ();

        for (int t = 0; t <= cells; t++) {
            int i = edgeIsZ ? t : fixed;
            int j = edgeIsZ ? fixed : t;
            int wx = originX + i * step;
            int wz = originZ + j * step;
            int h = tile.heightAt(i, j);

            short block = tile.topBlockAt(i, j);
            float r = colors.r(block) * 0.9f;
            float g = colors.g(block) * 0.9f;
            float b = colors.b(block) * 0.9f;

            int oTop = (vBase + t) * STRIDE;
            int oBot = (vBase + side + t) * STRIDE;

            v[oTop]     = wx;
            v[oTop + 1] = h + yOff;
            v[oTop + 2] = wz;
            v[oTop + 3] = r; v[oTop + 4] = g; v[oTop + 5] = b;
            v[oTop + 6] = 0f; v[oTop + 7] = 1f; v[oTop + 8] = 0f;

            v[oBot]     = wx;
            v[oBot + 1] = h + yOff - skirtDepth;
            v[oBot + 2] = wz;
            v[oBot + 3] = r; v[oBot + 4] = g; v[oBot + 5] = b;
            v[oBot + 6] = 0f; v[oBot + 7] = 1f; v[oBot + 8] = 0f;
        }

        for (int t = 0; t < cells; t++) {
            int a = vBase + t;
            int b = vBase + t + 1;
            int aD = vBase + side + t;
            int bD = vBase + side + t + 1;

            // both windings: visible regardless of which side faces the camera
            idx[ic++] = a; idx[ic++] = b; idx[ic++] = bD;
            idx[ic++] = a; idx[ic++] = bD; idx[ic++] = aD;

            idx[ic++] = a; idx[ic++] = bD; idx[ic++] = b;
            idx[ic++] = a; idx[ic++] = aD; idx[ic++] = bD;
        }

        return ic;
    }

    // ---------------------------------------------------------------
    // Water
    // ---------------------------------------------------------------

    private MeshData buildWater(LodTile tile) {
        final int cells = tile.cells();
        final int step = tile.step();
        final int originX = tile.originX();
        final int originZ = tile.originZ();

        // Same surface height formula as near-field water: top face of the
        // water block, lowered by WATER_TOP_DELTA.
        final float yOff = -EngineConfig.MIN_Y + 1f - EngineConfig.WATER_TOP_DELTA;

        // Match the near-field water shader's tint (tex.rgb * (0.3, 0.5, 0.7))
        // so the LOD ocean continues the near ocean's color seamlessly.
        float wr = colors.r(org.aouessar.core.world.Blocks.WATER) * 0.30f;
        float wg = colors.g(org.aouessar.core.world.Blocks.WATER) * 0.50f;
        float wb = colors.b(org.aouessar.core.world.Blocks.WATER) * 0.70f;

        // worst case: checkerboard -> cells^2/... keep simple upper bound
        int maxQuads = cells * cells;
        float[] v = new float[maxQuads * 4 * STRIDE];
        int[] idx = new int[maxQuads * 6];
        int vc = 0;
        int ic = 0;

        for (int j = 0; j < cells; j++) {
            int i = 0;
            while (i < cells) {
                if (!cellHasWater(tile, i, j)) { i++; continue; }

                int waterLevel = cellWaterLevel(tile, i, j);
                int runStart = i;
                while (i < cells && cellHasWater(tile, i, j) && cellWaterLevel(tile, i, j) == waterLevel) i++;

                float x0 = originX + runStart * step;
                float x1 = originX + i * step;
                float z0 = originZ + j * step;
                float z1 = z0 + step;
                float y = waterLevel + yOff;

                int base = vc;
                vc = putWaterVertex(v, vc, x0, y, z0, wr, wg, wb);
                vc = putWaterVertex(v, vc, x1, y, z0, wr, wg, wb);
                vc = putWaterVertex(v, vc, x1, y, z1, wr, wg, wb);
                vc = putWaterVertex(v, vc, x0, y, z1, wr, wg, wb);

                // CCW from above: (v0, v2, v1), (v0, v3, v2)
                idx[ic++] = base;     idx[ic++] = base + 2; idx[ic++] = base + 1;
                idx[ic++] = base;     idx[ic++] = base + 3; idx[ic++] = base + 2;
            }
        }

        return new MeshData(Arrays.copyOf(v, vc * STRIDE), Arrays.copyOf(idx, ic), STRIDE);
    }

    private static boolean cellHasWater(LodTile tile, int i, int j) {
        return tile.waterLevelAt(i, j) != LodTile.NO_WATER
                || tile.waterLevelAt(i + 1, j) != LodTile.NO_WATER
                || tile.waterLevelAt(i, j + 1) != LodTile.NO_WATER
                || tile.waterLevelAt(i + 1, j + 1) != LodTile.NO_WATER;
    }

    private static int cellWaterLevel(LodTile tile, int i, int j) {
        int w = tile.waterLevelAt(i, j);
        if (w != LodTile.NO_WATER) return w;
        w = tile.waterLevelAt(i + 1, j);
        if (w != LodTile.NO_WATER) return w;
        w = tile.waterLevelAt(i, j + 1);
        if (w != LodTile.NO_WATER) return w;
        return tile.waterLevelAt(i + 1, j + 1);
    }

    private static int putWaterVertex(float[] v, int vc, float x, float y, float z,
                                      float r, float g, float b) {
        int o = vc * STRIDE;
        v[o]     = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = r; v[o + 4] = g; v[o + 5] = b;
        v[o + 6] = 0f; v[o + 7] = 1f; v[o + 8] = 0f;
        return vc + 1;
    }
}
