package org.aouessar.renderer.mesh;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.world.Blocks;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.world.BlockRenderMap;
import org.aouessar.shared.EngineConfig;

import java.util.Arrays;

public final class GreedyChunkMesher {

    private static final int STRIDE = 7; // x y z tileMinU tileMinV uLocal vLocal
    private record FaceEmit(int newVertexCount, boolean flip) {}
    private record GrowResult(float[] v, int[] i) {}

    public MeshData buildChunkMesh(
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

        // Dynamic buffers
        float[] v = new float[STRIDE * 4 * 2048];
        int[]   i = new int[6 * 2048];
        int vCount = 0;
        int iCount = 0;

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

                        short a = sample(accessor, baseWx, baseWz, x[0], x[1], x[2]);
                        short b = sample(accessor, baseWx, baseWz, x[0] + q[0], x[1] + q[1], x[2] + q[2]);

                        // EXACT naive rule: only AIR is empty
                        boolean aAir = (a == Blocks.AIR);
                        boolean bAir = (b == Blocks.AIR);

                        if (!aAir && bAir) {
                            // solid -> air : face points in +d direction
                            int face = faceFor(d, true);
                            mask[n++] = pack(a, face);
                        } else if (aAir && !bAir) {
                            // air -> solid : face points in -d direction
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

                        short blockId = unpackBlockId(c);
                        int face = unpackFace(c);

                        // We have a rectangle in (u,w) of size (width,height) on slice plane x[d]
                        // Convert to local chunk coords:
                        int[] p = new int[]{ x[0], x[1], x[2] };
                        p[u] = iu;
                        p[w] = jw;

                        // plane coordinate along d:
                        // If face points +d, the visible face is at coord (x[d] + 1)
                        // If face points -d, the visible face is at coord (x[d] + 0)

                        p[d] = x[d] + 1;

                        // du and dw vectors in local coords
                        int[] du = new int[]{0,0,0};
                        int[] dw = new int[]{0,0,0};
                        du[u] = width;
                        dw[w] = height;

                        // Convert local corner ranges to world-space block bounds:
                        // x-range is [pX, pX+duX+dwX] etc.
                        int x0 = baseWx + p[0];
                        int y0 = p[1];
                        int z0 = baseWz + p[2];

                        int x1 = x0 + du[0] + dw[0];
                        int y1 = y0 + du[1] + dw[1];
                        int z1 = z0 + du[2] + dw[2];

                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        Atlas.UvRect uv = atlas.uv(blockRenderMap.tileName(blockId, face));
                        var fe = emitRectFace(v, vCount, x0, y0, z0, x1, y1, z1, face, uv);
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());

                        // clear mask area
                        for (int hh = 0; hh < height; hh++) {
                            int row = n + hh * duDim;
                            Arrays.fill(mask, row, row + width, 0);
                        }

                        iu += width;
                        n += width;
                    }
                }
            }
        }

        float[] outV = Arrays.copyOf(v, vCount * STRIDE);
        int[] outI   = Arrays.copyOf(i, iCount);
        return new MeshData(outV, outI);
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

    /**
     * Emit a rectangular face using the SAME vertex layouts as ChunkMesher.emitFace,
     * but with variable extents.
     *
     * Coordinates are integer world coords (block grid). x1/y1/z1 are "max" corners.
     * This emits 4 vertices and returns (newVertexCount, flip) exactly like ChunkMesher.
     */
    private static FaceEmit emitRectFace(
            float[] v,
            int vertexCount,
            int x0, int y0, int z0,
            int x1, int y1, int z1,
            int face,
            Atlas.UvRect uv
    ) {
        // tileMin in atlas UV space
        // IMPORTANT: keep your validated V convention:
        // tileMinV uses uv.v1() (because you previously flipped V)
        float tileMinU = uv.u0();
        float tileMinV = uv.v0();

        // How many 1x1 blocks the greedy rect spans in "U" and "V" directions
        int uBlocks = faceUBlocks(face, x0, y0, z0, x1, y1, z1);
        int vBlocks = faceVBlocks(face, x0, y0, z0, x1, y1, z1);

        // local repeat UV (0..uBlocks, 0..vBlocks)
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

        // Same vertex layouts as your working ChunkMesher.emitFace, just stretched to x0/x1 etc.
        // UVs:
        // - tileMinU/tileMinV per vertex (same for all 4)
        // - local u/v differs per corner to create repeat
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
                put(v, vertexCount++, x0, y1, z0, tileMinU, tileMinV, lu0, lv0);
                put(v, vertexCount++, x1, y1, z0, tileMinU, tileMinV, lu1, lv0);
                put(v, vertexCount++, x1, y1, z1, tileMinU, tileMinV, lu1, lv1);
                put(v, vertexCount++, x0, y1, z1, tileMinU, tileMinV, lu0, lv1);
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
        // NOTE: stride is now 7 floats per vertex
        float ax = v[base * 7],         ay = v[base * 7 + 1],         az = v[base * 7 + 2];
        float bx = v[(base + 1) * 7],   by = v[(base + 1) * 7 + 1],   bz = v[(base + 1) * 7 + 2];
        float cx = v[(base + 2) * 7],   cy = v[(base + 2) * 7 + 1],   cz = v[(base + 2) * 7 + 2];

        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;

        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;

        float ex = 0, ey = 0, ez = 0;
        switch (face) {
            case Face.PX -> ex = 1;
            case Face.NX -> ex = -1;
            case Face.PY -> ey = 1;
            case Face.NY -> ey = -1;
            case Face.PZ -> ez = 1;
            case Face.NZ -> ez = -1;
        }

        boolean flip = (nx * ex + ny * ey + nz * ez) < 0f;
        return new FaceEmit(vertexCount, flip);
    }


    private static int faceUBlocks(int face, int x0, int y0, int z0, int x1, int y1, int z1) {
        return switch (face) {
            case Face.PX, Face.NX -> (z1 - z0); // U along Z
            case Face.PZ, Face.NZ -> (x1 - x0); // U along X
            case Face.PY, Face.NY -> (x1 - x0); // U along X
            default -> 1;
        };
    }

    private static int faceVBlocks(int face, int x0, int y0, int z0, int x1, int y1, int z1) {
        return switch (face) {
            case Face.PX, Face.NX -> (y1 - y0); // V along Y
            case Face.PZ, Face.NZ -> (y1 - y0); // V along Y
            case Face.PY, Face.NY -> (z1 - z0); // V along Z
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
            idx[iCount++] = baseVertex + 0;
            idx[iCount++] = baseVertex + 1;
            idx[iCount++] = baseVertex + 2;

            idx[iCount++] = baseVertex + 2;
            idx[iCount++] = baseVertex + 3;
            idx[iCount++] = baseVertex + 0;
        } else {
            idx[iCount++] = baseVertex + 0;
            idx[iCount++] = baseVertex + 2;
            idx[iCount++] = baseVertex + 1;

            idx[iCount++] = baseVertex + 0;
            idx[iCount++] = baseVertex + 3;
            idx[iCount++] = baseVertex + 2;
        }
        return iCount;
    }

    private static GrowResult ensureCapacity(float[] v, int[] i, int vCount, int iCount, int addQuads) {
        int addVerts = addQuads * 4;
        int addIdx   = addQuads * 6;

        // stride is now 7 floats per vertex
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
}