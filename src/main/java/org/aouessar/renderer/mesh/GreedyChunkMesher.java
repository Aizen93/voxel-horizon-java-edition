package org.aouessar.renderer.mesh;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.RenderLayer;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.world.BlockRenderMap;
import org.aouessar.shared.EngineConfig;

import java.util.Arrays;

/**
 * Greedy mesher with per-vertex lighting:
 * <ul>
 *   <li><b>Ambient occlusion</b> — classic 3-neighbor corner darkening
 *       (Minecraft "smooth lighting"). AO is part of the greedy merge key, so
 *       merged quads always carry uniform corner AO.</li>
 *   <li><b>Skylight</b> — a per-column "light ceiling" scan darkens faces
 *       under tree canopies and overhangs (soft canopy shadows).</li>
 * </ul>
 * Vertex layout: x, y, z, tileMinU, tileMinV, uLocal, vLocal, shade (8 floats).
 */
public final class GreedyChunkMesher {

    private static final int STRIDE = 8; // x y z tileMinU tileMinV uLocal vLocal shade

    /** AO levels 0..3 (0 = fully occluded corner). */
    private static final float[] AO_LEVELS = {0.55f, 0.72f, 0.86f, 1.0f};
    /** Skylight levels 0..3 (0 = deep under canopy/overhang). */
    private static final float[] SKY_LEVELS = {0.50f, 0.66f, 0.82f, 1.0f};

    /**
     * Corner convention: index = (uMax ? 2 : 0) | (wMax ? 1 : 0), where u/w are
     * the sweep's in-plane axes. This table maps each face's emitted vertex
     * order (see emitRectFace) to that corner index.
     */
    private static final int[][] FACE_VERT_CORNER = {
            {0, 1, 3, 2}, // PX
            {1, 0, 2, 3}, // NX
            {0, 1, 3, 2}, // PY
            {2, 3, 1, 0}, // NY
            {2, 0, 1, 3}, // PZ
            {0, 2, 3, 1}, // NZ
    };

    private record FaceEmit(int newVertexCount, boolean flip) {}
    private record GrowResult(float[] v, int[] i) {}

    /**
     * 3 mesh outputs so the renderer can draw in 3 passes:
     * - opaque first
     * - cutout second (alpha discard, depth write ON)
     * - translucent last (blending, depth write OFF)
     */
    public record ChunkMeshes(MeshData opaque, MeshData cutout, MeshData translucent) {}

    private static final class Buf {
        float[] v;
        int[] i;
        int vCount;
        int iCount;

        Buf(int initialQuads) {
            this.v = new float[STRIDE * 4 * initialQuads];
            this.i = new int[6 * initialQuads];
        }

        void ensure(int addQuads) {
            var grow = ensureCapacity(v, i, vCount, iCount, addQuads);
            v = grow.v;
            i = grow.i;
        }

        MeshData toMesh() {
            float[] outV = Arrays.copyOf(v, vCount * STRIDE);
            int[] outI   = Arrays.copyOf(i, iCount);
            return new MeshData(outV, outI, STRIDE);
        }
    }

    public ChunkMeshes buildChunkMeshes(
            ChunkProvider chunkProvider,
            Atlas atlas,
            BlockRenderMap blockRenderMap,
            int cx,
            int cz
    ) {
        final int cs = EngineConfig.CHUNK_SIZE;
        final int h  = EngineConfig.WORLD_HEIGHT;

        final int baseWx = cx * cs;
        final int baseWz = cz * cs;

        // 3 dynamic buffers
        Buf opaque = new Buf(2048);
        Buf cutout = new Buf(1024);
        Buf translucent = new Buf(1024);

        BlockAccessor accessor = new BlockAccessor(chunkProvider);

        // ---- Skylight ceilings: topmost light-blocking block per column ----
        // Covers the chunk plus a 1-column border (front cells of border faces).
        final int ceilSide = cs + 2;
        int[] ceil = new int[ceilSide * ceilSide];
        for (int lz = -1; lz <= cs; lz++) {
            for (int lx = -1; lx <= cs; lx++) {
                int top = -1000; // no blocker: full sky everywhere
                for (int y = h - 1; y >= 0; y--) {
                    short id = accessor.blockAtWorld(baseWx + lx, y, baseWz + lz);
                    if (id == Blocks.AIR || Blocks.isBillboard(id)) continue;
                    if (Blocks.getRenderLayer(id) == RenderLayer.TRANSLUCENT) continue; // water passes light
                    top = y;
                    break;
                }
                ceil[(lz + 1) * ceilSide + (lx + 1)] = top;
            }
        }

        // dims in local chunk space
        final int[] dims = new int[]{ cs, h, cs }; // x,y,z

        // Mask cell packing (long):
        //   bits 0..2   face+1 (0 = empty)
        //   bits 3..18  blockId
        //   bits 19..26 AO (4 corners x 2 bits, by corner index)
        //   bits 27..28 sky level
        //   bits 32+    cell tag when corner AO is non-uniform (blocks merging,
        //               so interpolated AO never smears across cells)
        long[] mask = new long[Math.max(cs * cs, cs * h)];

        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3;
            int w = (d + 2) % 3;

            int duDim = dims[u];
            int dwDim = dims[w];

            int maskSize = duDim * dwDim;
            if (mask.length < maskSize) mask = new long[maskSize];

            int[] q = new int[]{0,0,0};
            q[d] = 1;

            int[] uAxis = new int[]{0,0,0};
            int[] wAxis = new int[]{0,0,0};
            uAxis[u] = 1;
            wAxis[w] = 1;

            int[] x = new int[]{0,0,0};

            // sweep slices along d
            for (x[d] = -1; x[d] < dims[d]; x[d]++) {

                // --- build mask for this slice ---
                int n = 0;
                for (x[w] = 0; x[w] < dwDim; x[w]++) {
                    for (x[u] = 0; x[u] < duDim; x[u]++) {

                        int awx = baseWx + x[0];
                        int awy = x[1];
                        int awz = baseWz + x[2];

                        int bwx = baseWx + x[0] + q[0];
                        int bwy = x[1] + q[1];
                        int bwz = baseWz + x[2] + q[2];

                        short a = sampleForGreedy(accessor, awx, awy, awz);
                        short b = sampleForGreedy(accessor, bwx, bwy, bwz);

                        if (a != Blocks.AIR && isFaceVisible(a, b)) {
                            // a -> b : face points in +d direction; front cell is b
                            int face = faceFor(d, true);
                            mask[n] = packCell(accessor, ceil, ceilSide, baseWx, baseWz,
                                    a, face, bwx, bwy, bwz, uAxis, wAxis, n);
                        } else if (b != Blocks.AIR && isFaceVisible(b, a)) {
                            // b -> a : face points in -d direction; front cell is a
                            int face = faceFor(d, false);
                            mask[n] = packCell(accessor, ceil, ceilSide, baseWx, baseWz,
                                    b, face, awx, awy, awz, uAxis, wAxis, n);
                        } else {
                            mask[n] = 0;
                        }
                        n++;
                    }
                }

                // --- greedy merge mask into quads ---
                n = 0;
                for (int jw = 0; jw < dwDim; jw++) {
                    for (int iu = 0; iu < duDim; ) {

                        long c = mask[n];
                        if (c == 0) { iu++; n++; continue; }

                        short blockId = unpackBlockId(c);
                        int face = unpackFace(c);
                        int aoPack = unpackAo(c);
                        int skyLevel = unpackSky(c);

                        boolean isWater = (blockId == Blocks.WATER);
                        boolean isSideFace = (face == Face.PX || face == Face.NX || face == Face.PZ || face == Face.NZ);

                        float skyShade = SKY_LEVELS[skyLevel];

                        // ---------------------------------------------------------
                        // SPECIAL CASE: Water SIDE faces (PX/NX/PZ/NZ)
                        // Emit per-cell quads so the top edge matches the lowered surface.
                        // ---------------------------------------------------------
                        if (isWater && isSideFace) {
                            int[] p = new int[]{ x[0], x[1], x[2] };
                            p[u] = iu;
                            p[w] = jw;
                            p[d] = x[d] + 1;

                            int[] du = new int[]{0,0,0};
                            int[] dw = new int[]{0,0,0};
                            du[u] = 1;
                            dw[w] = 1;

                            int x0 = baseWx + p[0];
                            int y0 = p[1];
                            int z0 = baseWz + p[2];

                            int x1 = x0 + du[0] + dw[0];
                            int y1 = y0 + du[1] + dw[1];
                            int z1 = z0 + du[2] + dw[2];

                            boolean surface = isSurfaceWater(accessor, x0, y0, z0);
                            float topY = surface ? waterSurfaceTopY(y0) : (y0 + 1f);

                            translucent.ensure(1);

                            int base = translucent.vCount;
                            Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(blockId, face));

                            var fe = emitRectFaceWaterSide(
                                    translucent.v, translucent.vCount,
                                    x0, y0, z0,
                                    x1, y1, z1,
                                    face,
                                    uv,
                                    topY,
                                    skyShade
                            );
                            translucent.vCount = fe.newVertexCount();
                            translucent.iCount = emitIndices(translucent.i, translucent.iCount, base, fe.flip());

                            mask[n] = 0;
                            iu += 1;
                            n  += 1;
                            continue;
                        }

                        // -----------------------------
                        // NORMAL greedy merging
                        // -----------------------------
                        int width = 1;
                        while (iu + width < duDim && mask[n + width] == c) width++;

                        int height = 1;
                        outer:
                        while (jw + height < dwDim) {
                            int row = n + height * duDim;
                            for (int k = 0; k < width; k++) {
                                if (mask[row + k] != c) break outer;
                            }
                            height++;
                        }

                        Buf target = switch (Blocks.getRenderLayer(blockId)) {
                            case OPAQUE -> opaque;
                            case CUTOUT -> cutout;
                            case TRANSLUCENT -> translucent;
                        };

                        int[] p = new int[]{ x[0], x[1], x[2] };
                        p[u] = iu;
                        p[w] = jw;
                        p[d] = x[d] + 1;

                        int[] du = new int[]{0,0,0};
                        int[] dw = new int[]{0,0,0};
                        du[u] = width;
                        dw[w] = height;

                        int x0 = baseWx + p[0];
                        int y0 = p[1];
                        int z0 = baseWz + p[2];

                        int x1 = x0 + du[0] + dw[0];
                        int y1 = y0 + du[1] + dw[1];
                        int z1 = z0 + du[2] + dw[2];

                        target.ensure(1);

                        int base = target.vCount;

                        // Per-vertex shade = corner AO x cell skylight.
                        // Water surfaces instead encode depth as (1 + level/7),
                        // which the translucent shader decodes for depth tint/foam.
                        float[] vertShade = new float[4];
                        if (blockId == Blocks.WATER && face == Face.PY) {
                            float depthShade = 1.0f + (aoPack & 7) / 7.0f;
                            Arrays.fill(vertShade, depthShade);
                        } else {
                            int[] map = FACE_VERT_CORNER[face];
                            for (int vi = 0; vi < 4; vi++) {
                                int ao = (aoPack >> (map[vi] * 2)) & 3;
                                vertShade[vi] = AO_LEVELS[ao] * skyShade;
                            }
                        }

                        Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(blockId, face));
                        var fe = emitRectFace(target.v, target.vCount,
                                x0, y0, z0,
                                x1, y1, z1,
                                face,
                                blockId,
                                uv,
                                vertShade
                        );
                        target.vCount = fe.newVertexCount();
                        target.iCount = emitIndices(target.i, target.iCount, base, fe.flip());

                        for (int hh = 0; hh < height; hh++) {
                            int row = n + hh * duDim;
                            Arrays.fill(mask, row, row + width, 0L);
                        }

                        iu += width;
                        n  += width;
                    }
                }
            }
        }

        emitBillboards(accessor, atlas, blockRenderMap, baseWx, baseWz, cs, h, cutout, ceil, ceilSide);

        return new ChunkMeshes(
                opaque.toMesh(),
                cutout.toMesh(),
                translucent.toMesh()
        );
    }

    // -----------------------------
    // Lighting
    // -----------------------------

    /**
     * Packs one visible face into a mask cell: block, face, corner AO and sky
     * level. (fx, fy, fz) is the world position of the face's FRONT (air) cell.
     */
    private static long packCell(
            BlockAccessor accessor, int[] ceil, int ceilSide, int baseWx, int baseWz,
            short blockId, int face,
            int fx, int fy, int fz,
            int[] uAxis, int[] wAxis,
            int cellIndex
    ) {
        // Skylight from the front cell's column ceiling
        int lx = fx - baseWx + 1;
        int lz = fz - baseWz + 1;
        int sky = 3;
        if (lx >= 0 && lx < ceilSide && lz >= 0 && lz < ceilSide) {
            int top = ceil[lz * ceilSide + lx];
            if (fy < top) {
                int depth = top - fy;
                sky = (depth <= 2) ? 2 : (depth <= 5) ? 1 : 0;
            }
        }

        // Corner AO (skip for translucent blocks: water surfaces stay smooth)
        int aoPack;
        boolean uniform;
        if (blockId == Blocks.WATER && face == Face.PY) {
            // Water surface: reuse the AO bits to carry the water column depth
            // (drives depth-tinted transparency + shore foam in the shader).
            // Different depth bands don't merge, keeping the gradient honest.
            int depth = 0;
            int y = fy - 1;
            while (depth < 12 && y >= 0 && accessor.blockAtWorld(fx, y, fz) == Blocks.WATER) {
                depth++;
                y--;
            }
            aoPack = Math.min(7, depth * 7 / 12);
            uniform = true;
        } else if (Blocks.getRenderLayer(blockId) == RenderLayer.TRANSLUCENT) {
            aoPack = 0xFF; // all corners = 3
            uniform = true;
        } else {
            int a00 = cornerAo(accessor, fx, fy, fz, uAxis, wAxis, -1, -1);
            int a01 = cornerAo(accessor, fx, fy, fz, uAxis, wAxis, -1, +1);
            int a10 = cornerAo(accessor, fx, fy, fz, uAxis, wAxis, +1, -1);
            int a11 = cornerAo(accessor, fx, fy, fz, uAxis, wAxis, +1, +1);
            // corner index = (uMax?2:0) | (wMax?1:0)
            aoPack = a00 | (a01 << 2) | (a10 << 4) | (a11 << 6);
            uniform = (a00 == a01 && a00 == a10 && a00 == a11);
        }

        long key = ((blockId & 0xFFFFL) << 3) | ((face + 1) & 7L);
        key |= ((long) aoPack) << 19;
        key |= ((long) sky) << 27;
        if (!uniform) {
            // Unique tag prevents merging cells with gradient AO
            key |= ((long) (cellIndex + 1)) << 32;
        }
        return key;
    }

    /** Classic 3-neighbor AO for one face corner (0 = darkest, 3 = open). */
    private static int cornerAo(
            BlockAccessor accessor,
            int fx, int fy, int fz,
            int[] uAxis, int[] wAxis,
            int su, int sw
    ) {
        boolean side1 = isAoBlocker(accessor,
                fx + su * uAxis[0], fy + su * uAxis[1], fz + su * uAxis[2]);
        boolean side2 = isAoBlocker(accessor,
                fx + sw * wAxis[0], fy + sw * wAxis[1], fz + sw * wAxis[2]);
        if (side1 && side2) return 0;
        boolean corner = isAoBlocker(accessor,
                fx + su * uAxis[0] + sw * wAxis[0],
                fy + su * uAxis[1] + sw * wAxis[1],
                fz + su * uAxis[2] + sw * wAxis[2]);
        return 3 - ((side1 ? 1 : 0) + (side2 ? 1 : 0) + (corner ? 1 : 0));
    }

    private static boolean isAoBlocker(BlockAccessor accessor, int wx, int wy, int wz) {
        short id = accessor.blockAtWorld(wx, wy, wz);
        return id != Blocks.AIR && Blocks.getRenderLayer(id) == RenderLayer.OPAQUE;
    }

    /**
     * Face visibility rules for 3-pass rendering:
     * - neighbor OPAQUE hides faces behind it
     * - CUTOUT does NOT hide (so you can see behind leaf/bush holes)
     * - TRANSLUCENT hides only if both are translucent AND same blockId (water-water, glass-glass internal faces)
     */
    private static boolean isFaceVisible(short self, short neighbor) {
        if (neighbor == Blocks.AIR) return true;

        var selfLayer = Blocks.getRenderLayer(self);
        var otherLayer = Blocks.getRenderLayer(neighbor);

        if (selfLayer == RenderLayer.TRANSLUCENT &&
                otherLayer == RenderLayer.TRANSLUCENT &&
                self == neighbor) {
            return false;
        }

        if (otherLayer == RenderLayer.OPAQUE) return false;

        return true;
    }

    private static int faceFor(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? Face.PX : Face.NX;
            case 1 -> positive ? Face.PY : Face.NY;
            case 2 -> positive ? Face.PZ : Face.NZ;
            default -> throw new IllegalArgumentException("axis=" + axis);
        };
    }

    private static short unpackBlockId(long packed) {
        return (short) ((packed >>> 3) & 0xFFFF);
    }

    private static int unpackFace(long packed) {
        return (int) (packed & 7) - 1;
    }

    private static int unpackAo(long packed) {
        return (int) ((packed >>> 19) & 0xFF);
    }

    private static int unpackSky(long packed) {
        return (int) ((packed >>> 27) & 3);
    }

    // -----------------------------
    // Emission
    // -----------------------------

    private static FaceEmit emitRectFace(
            float[] v,
            int vertexCount,
            int x0, int y0, int z0,
            int x1, int y1, int z1,
            int face,
            short blockId,
            Atlas.UvRect uv,
            float[] vertShade
    ) {
        float tileMinU = uv.u0();
        float tileMinV = uv.v0();

        int uBlocks = faceUBlocks(face, x0, y0, z0, x1, y1, z1);
        int vBlocks = faceVBlocks(face, x0, y0, z0, x1, y1, z1);

        float lu0 = 0f;
        float lv0 = 0f;
        float lu1 = uBlocks;
        float lv1 = vBlocks;

        if (face == Face.PX || face == Face.NX || face == Face.PZ || face == Face.NZ) {
            float tmp = lv0;
            lv0 = lv1;
            lv1 = tmp;
        }

        int base = vertexCount;

        switch (face) {
            case Face.PX -> {
                put(v, vertexCount++, x1, y0, z0, tileMinU, tileMinV, lu0, lv0, vertShade[0]);
                put(v, vertexCount++, x1, y0, z1, tileMinU, tileMinV, lu1, lv0, vertShade[1]);
                put(v, vertexCount++, x1, y1, z1, tileMinU, tileMinV, lu1, lv1, vertShade[2]);
                put(v, vertexCount++, x1, y1, z0, tileMinU, tileMinV, lu0, lv1, vertShade[3]);
            }
            case Face.NX -> {
                put(v, vertexCount++, x0, y0, z1, tileMinU, tileMinV, lu0, lv0, vertShade[0]);
                put(v, vertexCount++, x0, y0, z0, tileMinU, tileMinV, lu1, lv0, vertShade[1]);
                put(v, vertexCount++, x0, y1, z0, tileMinU, tileMinV, lu1, lv1, vertShade[2]);
                put(v, vertexCount++, x0, y1, z1, tileMinU, tileMinV, lu0, lv1, vertShade[3]);
            }
            case Face.PY -> {
                float y = y1;
                if (blockId == Blocks.WATER) {
                    y = y1 - EngineConfig.WATER_TOP_DELTA;
                }
                put(v, vertexCount++, x0, y, z0, tileMinU, tileMinV, lu0, lv0, vertShade[0]);
                put(v, vertexCount++, x1, y, z0, tileMinU, tileMinV, lu1, lv0, vertShade[1]);
                put(v, vertexCount++, x1, y, z1, tileMinU, tileMinV, lu1, lv1, vertShade[2]);
                put(v, vertexCount++, x0, y, z1, tileMinU, tileMinV, lu0, lv1, vertShade[3]);
            }
            case Face.NY -> {
                put(v, vertexCount++, x0, y0, z1, tileMinU, tileMinV, lu0, lv0, vertShade[0]);
                put(v, vertexCount++, x1, y0, z1, tileMinU, tileMinV, lu1, lv0, vertShade[1]);
                put(v, vertexCount++, x1, y0, z0, tileMinU, tileMinV, lu1, lv1, vertShade[2]);
                put(v, vertexCount++, x0, y0, z0, tileMinU, tileMinV, lu0, lv1, vertShade[3]);
            }
            case Face.PZ -> {
                put(v, vertexCount++, x1, y0, z1, tileMinU, tileMinV, lu0, lv0, vertShade[0]);
                put(v, vertexCount++, x0, y0, z1, tileMinU, tileMinV, lu1, lv0, vertShade[1]);
                put(v, vertexCount++, x0, y1, z1, tileMinU, tileMinV, lu1, lv1, vertShade[2]);
                put(v, vertexCount++, x1, y1, z1, tileMinU, tileMinV, lu0, lv1, vertShade[3]);
            }
            case Face.NZ -> {
                put(v, vertexCount++, x0, y0, z0, tileMinU, tileMinV, lu0, lv0, vertShade[0]);
                put(v, vertexCount++, x1, y0, z0, tileMinU, tileMinV, lu1, lv0, vertShade[1]);
                put(v, vertexCount++, x1, y1, z0, tileMinU, tileMinV, lu1, lv1, vertShade[2]);
                put(v, vertexCount++, x0, y1, z0, tileMinU, tileMinV, lu0, lv1, vertShade[3]);
            }
            default -> throw new IllegalArgumentException("Unknown face: " + face);
        }

        boolean flip = isFlip(v, face, base);
        return new FaceEmit(vertexCount, flip);
    }

    private static boolean isFlip(float[] v, int face, int base) {
        float ax = v[base * STRIDE];
        float ay = v[base * STRIDE + 1];
        float az = v[base * STRIDE + 2];
        float bx = v[(base + 1) * STRIDE];
        float by = v[(base + 1) * STRIDE + 1];
        float bz = v[(base + 1) * STRIDE + 2];
        float cx = v[(base + 2) * STRIDE];
        float cy = v[(base + 2) * STRIDE + 1];
        float cz = v[(base + 2) * STRIDE + 2];

        float abx = bx - ax;
        float aby = by - ay;
        float abz = bz - az;
        float acx = cx - ax;
        float acy = cy - ay;
        float acz = cz - az;

        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;

        float ex = 0;
        float ey = 0;
        float ez = 0;
        switch (face) {
            case Face.PX -> ex = 1;
            case Face.NX -> ex = -1;
            case Face.PY -> ey = 1;
            case Face.NY -> ey = -1;
            case Face.PZ -> ez = 1;
            case Face.NZ -> ez = -1;
            default -> throw new IllegalStateException("Unexpected value: " + face);
        }

        return (nx * ex + ny * ey + nz * ez) < 0f;
    }

    private static int faceUBlocks(int face, int x0, int y0, int z0, int x1, int y1, int z1) {
        return switch (face) {
            case Face.PX, Face.NX -> (z1 - z0);
            case Face.PZ, Face.NZ -> (x1 - x0);
            case Face.PY, Face.NY -> (x1 - x0);
            default -> 1;
        };
    }

    private static int faceVBlocks(int face, int x0, int y0, int z0, int x1, int y1, int z1) {
        return switch (face) {
            case Face.PX, Face.NX -> (y1 - y0);
            case Face.PZ, Face.NZ -> (y1 - y0);
            case Face.PY, Face.NY -> (z1 - z0);
            default -> 1;
        };
    }

    private static void put(
            float[] v,
            int vertexIndex,
            float x, float y, float z,
            float tileMinU, float tileMinV,
            float uLocal, float vLocal,
            float shade
    ) {
        int o = vertexIndex * STRIDE;
        v[o]     = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = tileMinU;
        v[o + 4] = tileMinV;
        v[o + 5] = uLocal;
        v[o + 6] = vLocal;
        v[o + 7] = shade;
    }

    private static int emitIndices(int[] idx, int iCount, int baseVertex, boolean flip) {
        if (!flip) {
            idx[iCount++] = baseVertex;
            idx[iCount++] = baseVertex + 1;
            idx[iCount++] = baseVertex + 2;

            idx[iCount++] = baseVertex + 2;
            idx[iCount++] = baseVertex + 3;
            idx[iCount++] = baseVertex;
        } else {
            idx[iCount++] = baseVertex;
            idx[iCount++] = baseVertex + 2;
            idx[iCount++] = baseVertex + 1;

            idx[iCount++] = baseVertex;
            idx[iCount++] = baseVertex + 3;
            idx[iCount++] = baseVertex + 2;
        }
        return iCount;
    }

    private static GrowResult ensureCapacity(float[] v, int[] i, int vCount, int iCount, int addQuads) {
        int addVerts = addQuads * 4;
        int addIdx   = addQuads * 6;

        int neededV = (vCount + addVerts) * STRIDE;
        int neededI = (iCount + addIdx);

        if (neededV > v.length) {
            int newLen = Math.max(neededV, v.length * 2);
            v = Arrays.copyOf(v, newLen);
        }
        if (neededI > i.length) {
            int newLen = Math.max(neededI, i.length * 2);
            i = Arrays.copyOf(i, newLen);
        }
        return new GrowResult(v, i);
    }

    private static short sampleForGreedy(BlockAccessor accessor, int wx, int wy, int wz) {
        short id = accessor.blockAtWorld(wx, wy, wz);
        // Billboards are not voxel geometry: treat as AIR for greedy face generation
        return Blocks.isBillboard(id) ? Blocks.AIR : id;
    }

    private static void emitBillboards(
            BlockAccessor accessor,
            Atlas atlas,
            BlockRenderMap blockRenderMap,
            int baseWx,
            int baseWz,
            int cs,
            int h,
            Buf cutout,
            int[] ceil,
            int ceilSide
    ) {
        final int tileFace = Face.PY;

        for (int lz = 0; lz < cs; lz++) {
            int wz = baseWz + lz;
            for (int lx = 0; lx < cs; lx++) {
                int wx = baseWx + lx;

                // BlockAccessor expects array-space Y (0..WORLD_HEIGHT)
                for (int wy = 0; wy < h; wy++) {
                    short id = accessor.blockAtWorld(wx, wy, wz);
                    if (!Blocks.isBillboard(id)) continue;

                    Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(id, tileFace));
                    float tileMinU = uv.u0();
                    float tileMinV = uv.v0();

                    // Skylight of the plant's own cell (canopy shade)
                    int top = ceil[(lz + 1) * ceilSide + (lx + 1)];
                    float shade = 1f;
                    if (wy < top) {
                        int depth = top - wy;
                        shade = SKY_LEVELS[(depth <= 2) ? 2 : (depth <= 5) ? 1 : 0];
                    }

                    cutout.ensure(4);

                    float y1 = wy + 1f;
                    float cx = wx + 0.5f;
                    float cz = wz + 0.5f;
                    float r = 0.5f;

                    emitBillboardQuadDoubleSided(cutout,
                            cx - r, wy, cz - r,
                            cx + r, y1, cz + r,
                            tileMinU, tileMinV, shade);

                    emitBillboardQuadDoubleSided(cutout,
                            cx - r, wy, cz + r,
                            cx + r, y1, cz - r,
                            tileMinU, tileMinV, shade);
                }
            }
        }
    }

    private static void emitBillboardQuadDoubleSided(
            Buf target,
            float xA, float y0, float zA,
            float xB, float y1, float zB,
            float tileMinU, float tileMinV,
            float shade
    ) {
        int base0 = target.vCount;

        put(target.v, target.vCount++, xA, y0, zA, tileMinU, tileMinV, 0f, 0f, shade);
        put(target.v, target.vCount++, xB, y0, zB, tileMinU, tileMinV, 1f, 0f, shade);
        put(target.v, target.vCount++, xB, y1, zB, tileMinU, tileMinV, 1f, 1f, shade);
        put(target.v, target.vCount++, xA, y1, zA, tileMinU, tileMinV, 0f, 1f, shade);

        target.iCount = emitIndices(target.i, target.iCount, base0, false);

        int base1 = target.vCount;

        put(target.v, target.vCount++, xA, y0, zA, tileMinU, tileMinV, 0f, 0f, shade);
        put(target.v, target.vCount++, xA, y1, zA, tileMinU, tileMinV, 0f, 1f, shade);
        put(target.v, target.vCount++, xB, y1, zB, tileMinU, tileMinV, 1f, 1f, shade);
        put(target.v, target.vCount++, xB, y0, zB, tileMinU, tileMinV, 1f, 0f, shade);

        target.iCount = emitIndices(target.i, target.iCount, base1, false);
    }

    private static boolean isSurfaceWater(BlockAccessor accessor, int wx, int wy, int wz) {
        if (accessor.blockAtWorld(wx, wy, wz) != Blocks.WATER) return false;
        return accessor.blockAtWorld(wx, wy + 1, wz) != Blocks.WATER;
    }

    private static float waterSurfaceTopY(int wy) {
        return (wy + 1f) - EngineConfig.WATER_TOP_DELTA;
    }

    private static FaceEmit emitRectFaceWaterSide(
            float[] v,
            int vertexCount,
            int x0, int y0, int z0,
            int x1, int y1, int z1,
            int face,
            Atlas.UvRect uv,
            float topY,
            float shade
    ) {
        float tileMinU = uv.u0();
        float tileMinV = uv.v0();

        int uBlocks = faceUBlocks(face, x0, y0, z0, x1, y1, z1);
        int vBlocks = faceVBlocks(face, x0, y0, z0, x1, y1, z1);

        float lu0 = 0f, lv0 = 0f;
        float lu1 = uBlocks, lv1 = vBlocks;

        if (face == Face.PX || face == Face.NX || face == Face.PZ || face == Face.NZ) {
            float tmp = lv0; lv0 = lv1; lv1 = tmp;
        }

        int base = vertexCount;

        float fy0 = y0;
        float fy1 = topY;

        switch (face) {
            case Face.PX -> {
                put(v, vertexCount++, x1, fy0, z0, tileMinU, tileMinV, lu0, lv0, shade);
                put(v, vertexCount++, x1, fy0, z1, tileMinU, tileMinV, lu1, lv0, shade);
                put(v, vertexCount++, x1, fy1, z1, tileMinU, tileMinV, lu1, lv1, shade);
                put(v, vertexCount++, x1, fy1, z0, tileMinU, tileMinV, lu0, lv1, shade);
            }
            case Face.NX -> {
                put(v, vertexCount++, x0, fy0, z1, tileMinU, tileMinV, lu0, lv0, shade);
                put(v, vertexCount++, x0, fy0, z0, tileMinU, tileMinV, lu1, lv0, shade);
                put(v, vertexCount++, x0, fy1, z0, tileMinU, tileMinV, lu1, lv1, shade);
                put(v, vertexCount++, x0, fy1, z1, tileMinU, tileMinV, lu0, lv1, shade);
            }
            case Face.PZ -> {
                put(v, vertexCount++, x1, fy0, z1, tileMinU, tileMinV, lu0, lv0, shade);
                put(v, vertexCount++, x0, fy0, z1, tileMinU, tileMinV, lu1, lv0, shade);
                put(v, vertexCount++, x0, fy1, z1, tileMinU, tileMinV, lu1, lv1, shade);
                put(v, vertexCount++, x1, fy1, z1, tileMinU, tileMinV, lu0, lv1, shade);
            }
            case Face.NZ -> {
                put(v, vertexCount++, x0, fy0, z0, tileMinU, tileMinV, lu0, lv0, shade);
                put(v, vertexCount++, x1, fy0, z0, tileMinU, tileMinV, lu1, lv0, shade);
                put(v, vertexCount++, x1, fy1, z0, tileMinU, tileMinV, lu1, lv1, shade);
                put(v, vertexCount++, x0, fy1, z0, tileMinU, tileMinV, lu0, lv1, shade);
            }
            default -> throw new IllegalArgumentException("emitRectFaceWaterSide only for side faces");
        }

        // Normal/flip logic same as emitRectFace
        float ax = v[base * STRIDE];
        float ay = v[base * STRIDE + 1];
        float az = v[base * STRIDE + 2];
        float bx = v[(base + 1) * STRIDE];
        float by = v[(base + 1) * STRIDE + 1];
        float bz = v[(base + 1) * STRIDE + 2];
        float cx = v[(base + 2) * STRIDE];
        float cy = v[(base + 2) * STRIDE + 1];
        float cz = v[(base + 2) * STRIDE + 2];

        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;

        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;

        float ex = 0, ey = 0, ez = 0;
        switch (face) {
            case Face.PX -> ex = 1;
            case Face.NX -> ex = -1;
            case Face.PZ -> ez = 1;
            case Face.NZ -> ez = -1;
        }

        boolean flip = (nx * ex + ny * ey + nz * ez) < 0f;
        return new FaceEmit(vertexCount, flip);
    }
}
