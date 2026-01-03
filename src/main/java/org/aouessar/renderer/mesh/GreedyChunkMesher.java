package org.aouessar.renderer.mesh;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.RenderLayer;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.world.BlockRenderMap;
import org.aouessar.shared.EngineConfig;

import java.util.Arrays;

public final class GreedyChunkMesher {

    private static final int STRIDE = 7; // x y z tileMinU tileMinV uLocal vLocal
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
            return new MeshData(outV, outI);
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

        // dims in local chunk space
        final int[] dims = new int[]{ cs, h, cs }; // x,y,z

        // Mask stores packed cell: (blockId << 3) | (faceId+1). 0 means empty.
        int[] mask = new int[Math.max(cs * cs, Math.max(cs * h, cs * h))];

        for (int d = 0; d < 3; d++) {
            int u = (d + 1) % 3;
            int w = (d + 2) % 3;

            int duDim = dims[u];
            int dwDim = dims[w];

            int maskSize = duDim * dwDim;
            if (mask.length < maskSize) mask = new int[maskSize];

            int[] q = new int[]{0,0,0};
            q[d] = 1;

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
                            // a -> b : face points in +d direction
                            int face = faceFor(d, true);
                            mask[n++] = pack(a, face);
                        } else if (b != Blocks.AIR && isFaceVisible(b, a)) {
                            // b -> a : face points in -d direction
                            int face = faceFor(d, false);
                            mask[n++] = pack(b, face);
                        } else {
                            mask[n++] = 0;
                        }
                    }
                }

                // --- greedy merge mask into quads ---
                n = 0;
                for (int jw = 0; jw < dwDim; jw++) {
                    for (int iu = 0; iu < duDim; ) {

                        int c = mask[n];
                        if (c == 0) { iu++; n++; continue; }

                        // decode early (so we can special-case before merging)
                        short blockId = unpackBlockId(c);
                        int face = unpackFace(c);

                        boolean isWater = (blockId == Blocks.WATER);
                        boolean isSideFace = (face == Face.PX || face == Face.NX || face == Face.PZ || face == Face.NZ);

                        // ---------------------------------------------------------
                        // SPECIAL CASE: Water SIDE faces (PX/NX/PZ/NZ)
                        // Emit per-cell quads so the top edge matches the lowered surface.
                        // This fixes the "water side reaches full cube height" artifact.
                        // ---------------------------------------------------------
                        if (isWater && isSideFace) {
                            // rectangle is 1x1 in (u,w)
                            int[] p = new int[]{ x[0], x[1], x[2] };
                            p[u] = iu;
                            p[w] = jw;

                            // Visible face plane coordinate:
                            p[d] = x[d] + 1;

                            int[] du = new int[]{0,0,0};
                            int[] dw = new int[]{0,0,0};
                            du[u] = 1;
                            dw[w] = 1;

                            // World-space block bounds for this single cell
                            int x0 = baseWx + p[0];
                            int y0 = p[1];
                            int z0 = baseWz + p[2];

                            int x1 = x0 + du[0] + dw[0];
                            int y1 = y0 + du[1] + dw[1];
                            int z1 = z0 + du[2] + dw[2];

                            // Determine if this water block is a surface water block (above != water)
                            boolean surface = isSurfaceWater(accessor, x0, y0, z0);
                            float topY = surface ? waterSurfaceTopY(y0) : (y0 + 1f);

                            // water is translucent
                            translucent.ensure(1);

                            int base = translucent.vCount;
                            Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(blockId, face));

                            var fe = emitRectFaceWaterSide(
                                    translucent.v, translucent.vCount,
                                    x0, y0, z0,
                                    x1, y1, z1,
                                    face,
                                    uv,
                                    topY
                            );
                            translucent.vCount = fe.newVertexCount();
                            translucent.iCount = emitIndices(translucent.i, translucent.iCount, base, fe.flip());

                            // clear only this cell (no greedy merging)
                            mask[n] = 0;

                            iu += 1;
                            n  += 1;
                            continue;
                        }

                        // -----------------------------
                        // NORMAL greedy merging
                        // -----------------------------
                        // width
                        int width = 1;
                        while (iu + width < duDim && mask[n + width] == c) width++;

                        // height
                        int height = 1;
                        outer:
                        while (jw + height < dwDim) {
                            int row = n + height * duDim;
                            for (int k = 0; k < width; k++) {
                                if (mask[row + k] != c) break outer;
                            }
                            height++;
                        }

                        // Choose which mesh buffer receives this quad
                        Buf target = switch (Blocks.getRenderLayer(blockId)) {
                            case OPAQUE -> opaque;
                            case CUTOUT -> cutout;
                            case TRANSLUCENT -> translucent;
                        };

                        // We have a rectangle in (u,w) of size (width,height) on slice plane x[d]
                        // Convert to local chunk coords:
                        int[] p = new int[]{ x[0], x[1], x[2] };
                        p[u] = iu;
                        p[w] = jw;

                        // Visible face plane coordinate:
                        // For +d faces, visible face is at x[d] + 1
                        p[d] = x[d] + 1;

                        // du and dw vectors in local coords
                        int[] du = new int[]{0,0,0};
                        int[] dw = new int[]{0,0,0};
                        du[u] = width;
                        dw[w] = height;

                        // Convert local corner ranges to world-space block bounds:
                        int x0 = baseWx + p[0];
                        int y0 = p[1];
                        int z0 = baseWz + p[2];

                        int x1 = x0 + du[0] + dw[0];
                        int y1 = y0 + du[1] + dw[1];
                        int z1 = z0 + du[2] + dw[2];

                        target.ensure(1);

                        int base = target.vCount;

                        Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(blockId, face));
                        var fe = emitRectFace(target.v, target.vCount,
                                x0, y0, z0,
                                x1, y1, z1,
                                face,
                                blockId,
                                uv
                        );
                        target.vCount = fe.newVertexCount();
                        target.iCount = emitIndices(target.i, target.iCount, base, fe.flip());

                        // clear mask area
                        for (int hh = 0; hh < height; hh++) {
                            int row = n + hh * duDim;
                            Arrays.fill(mask, row, row + width, 0);
                        }

                        iu += width;
                        n  += width;
                    }
                }
            }
        }

        emitBillboards(accessor, atlas, blockRenderMap, baseWx, baseWz, cs, h, cutout);

        return new ChunkMeshes(
                opaque.toMesh(),
                cutout.toMesh(),
                translucent.toMesh()
        );
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

        // Internal faces inside the same translucent material (water-water, glass-glass)
        if (selfLayer == RenderLayer.TRANSLUCENT &&
                otherLayer == RenderLayer.TRANSLUCENT &&
                self == neighbor) {
            return false;
        }

        // Opaque blocks occlude faces behind them
        if (otherLayer == RenderLayer.OPAQUE) return false;

        // Otherwise visible (against cutout or translucent or different translucent type)
        return true;
    }

    private static short sample(BlockAccessor accessor, int baseWx, int baseWz, int lx, int ly, int lz) {
        int wx = baseWx + lx;
        int wz = baseWz + lz;
        return accessor.blockAtWorld(wx, ly, wz);
    }

    private static int faceFor(int axis, boolean positive) {
        return switch (axis) {
            case 0 -> positive ? Face.PX : Face.NX;
            case 1 -> positive ? Face.PY : Face.NY;
            case 2 -> positive ? Face.PZ : Face.NZ;
            default -> throw new IllegalArgumentException("axis=" + axis);
        };
    }

    private static boolean isPositiveFace(int face) {
        return face == Face.PX || face == Face.PY || face == Face.PZ;
    }

    private static int pack(short blockId, int face) {
        // store face+1 so 0 means empty
        return ((blockId & 0xFFFF) << 3) | ((face + 1) & 7);
    }

    private static short unpackBlockId(int packed) {
        return (short) (packed >>> 3);
    }

    private static int unpackFace(int packed) {
        return (packed & 7) - 1;
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
            Atlas.UvRect uv
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
                put(v, vertexCount++, x1, y0, z0, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, y0, z1, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, y1, z1, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x1, y1, z0, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.NX -> {
                put(v, vertexCount++, x0, y0, z1, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x0, y0, z0, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x0, y1, z0, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, y1, z1, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.PY -> {
                float y = y1;
                if (blockId == Blocks.WATER) {
                    y = y1 - EngineConfig.WATER_TOP_DELTA; // tweak: 0.85–0.92 feels good
                }
                put(v, vertexCount++, x0, y, z0, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, y, z0, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, y, z1, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, y, z1, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.NY -> {
                put(v, vertexCount++, x0, y0, z1, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, y0, z1, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, y0, z0, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, y0, z0, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.PZ -> {
                put(v, vertexCount++, x1, y0, z1, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x0, y0, z1, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x0, y1, z1, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x1, y1, z1, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.NZ -> {
                put(v, vertexCount++, x0, y0, z0, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, y0, z0, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, y1, z0, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, y1, z0, tileMinU, tileMinV, lu0, lv1);
            }
            default -> throw new IllegalArgumentException("Unknown face: " + face);
        }

        // Compute face normal from positions of first triangle (base, base+1, base+2)
        boolean flip = isFlip(v, face, base);
        return new FaceEmit(vertexCount, flip);
    }

    private static boolean isFlip(float[] v, int face, int base) {
        float ax = v[base * 7];
        float ay = v[base * 7 + 1];
        float az = v[base * 7 + 2];
        float bx = v[(base + 1) * 7];
        float by = v[(base + 1) * 7 + 1];
        float bz = v[(base + 1) * 7 + 2];
        float cx = v[(base + 2) * 7];
        float cy = v[(base + 2) * 7 + 1];
        float cz = v[(base + 2) * 7 + 2];

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

        boolean flip = (nx * ex + ny * ey + nz * ez) < 0f;
        return flip;
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
            float uLocal, float vLocal
    ) {
        int o = vertexIndex * 7;
        v[o]     = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = tileMinU;
        v[o + 4] = tileMinV;
        v[o + 5] = uLocal;
        v[o + 6] = vLocal;
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

        int neededV = (vCount + addVerts) * 7;
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

    private static boolean isBillboard(short blockId) {
        return switch (blockId) {
            case Blocks.BUSH, Blocks.TALL_GRASS, Blocks.FLOWER_YELLOW, Blocks.FLOWER_RED -> true;
            default -> false;
        };
    }

    private static short sampleForGreedy(BlockAccessor accessor, int wx, int wy, int wz) {
        short id = accessor.blockAtWorld(wx, wy, wz);
        // Billboards are not voxel geometry: treat as AIR for greedy face generation
        return isBillboard(id) ? Blocks.AIR : id;
    }

    private static void emitBillboards(
            BlockAccessor accessor,
            Atlas atlas,
            BlockRenderMap blockRenderMap,
            int baseWx,
            int baseWz,
            int cs,
            int h,
            Buf cutout
    ) {
        // Use the “top” face mapping for plants (usually same tile for all anyway)
        final int tileFace = Face.PY;

        for (int lz = 0; lz < cs; lz++) {
            int wz = baseWz + lz;
            for (int lx = 0; lx < cs; lx++) {
                int wx = baseWx + lx;

                for (int wy = EngineConfig.MIN_Y; wy < EngineConfig.MIN_Y + h; wy++) {
                    short id = accessor.blockAtWorld(wx, wy, wz);
                    if (!isBillboard(id)) continue;

                    // Atlas tile (same mechanism as cubes)
                    Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(id, tileFace));
                    float tileMinU = uv.u0();
                    float tileMinV = uv.v0();

                    // 2 crossed quads, double-sided => 4 quads total
                    cutout.ensure(4);

                    // World-space block corner
                    float y1 = wy + 1f;

                    // Center of the block footprint
                    float cx = wx + 0.5f;
                    float cz = wz + 0.5f;

                    // Half width from center to edge (0.5 = exactly block width)
                    float r = 0.5f;

                    // Quad A (diagonal)
                    emitBillboardQuadDoubleSided(cutout,
                            cx - r, wy, cz - r,
                            cx + r, y1, cz + r,
                            tileMinU, tileMinV);

                    // Quad B (other diagonal)
                    emitBillboardQuadDoubleSided(cutout,
                            cx - r, wy, cz + r,
                            cx + r, y1, cz - r,
                            tileMinU, tileMinV);
                }
            }
        }
    }

    private static void emitBillboardQuadDoubleSided(
            Buf target,
            float xA, float y0, float zA,
            float xB, float y1, float zB,
            float tileMinU, float tileMinV
    ) {
        // First side (winding 0-1-2, 2-3-0)
        int base0 = target.vCount;

        put(target.v, target.vCount++, xA, y0, zA, tileMinU, tileMinV, 0f, 0f);
        put(target.v, target.vCount++, xB, y0, zB, tileMinU, tileMinV, 1f, 0f);
        put(target.v, target.vCount++, xB, y1, zB, tileMinU, tileMinV, 1f, 1f);
        put(target.v, target.vCount++, xA, y1, zA, tileMinU, tileMinV, 0f, 1f);

        target.iCount = emitIndices(target.i, target.iCount, base0, false);

        // Second side (reverse winding) – simplest, robust, no renderer-state assumptions
        int base1 = target.vCount;

        put(target.v, target.vCount++, xA, y0, zA, tileMinU, tileMinV, 0f, 0f);
        put(target.v, target.vCount++, xA, y1, zA, tileMinU, tileMinV, 0f, 1f);
        put(target.v, target.vCount++, xB, y1, zB, tileMinU, tileMinV, 1f, 1f);
        put(target.v, target.vCount++, xB, y0, zB, tileMinU, tileMinV, 1f, 0f);

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
            float topY
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
        float fy1 = topY; // custom top

        switch (face) {
            case Face.PX -> {
                put(v, vertexCount++, x1, fy0, z0, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, fy0, z1, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, fy1, z1, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x1, fy1, z0, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.NX -> {
                put(v, vertexCount++, x0, fy0, z1, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x0, fy0, z0, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x0, fy1, z0, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, fy1, z1, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.PZ -> {
                put(v, vertexCount++, x1, fy0, z1, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x0, fy0, z1, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x0, fy1, z1, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x1, fy1, z1, tileMinU, tileMinV, lu0, lv1);
            }
            case Face.NZ -> {
                put(v, vertexCount++, x0, fy0, z0, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, fy0, z0, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, fy1, z0, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, fy1, z0, tileMinU, tileMinV, lu0, lv1);
            }
            default -> throw new IllegalArgumentException("emitRectFaceWaterSide only for side faces");
        }

        // Normal/flip logic same as emitRectFace
        float ax = v[base * 7];
        float ay = v[base * 7 + 1];
        float az = v[base * 7 + 2];
        float bx = v[(base + 1) * 7];
        float by = v[(base + 1) * 7 + 1];
        float bz = v[(base + 1) * 7 + 2];
        float cx = v[(base + 2) * 7];
        float cy = v[(base + 2) * 7 + 1];
        float cz = v[(base + 2) * 7 + 2];

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