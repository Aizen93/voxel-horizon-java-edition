package org.aouessar.renderer.mesh;

import org.aouessar.core.api.ChunkProvider;
import org.aouessar.core.world.Blocks;
import org.aouessar.core.world.chunk.Chunk;
import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.world.BlockRenderMap;
import org.aouessar.shared.EngineConfig;

import java.util.Arrays;

public final class ChunkMesher {

    private record FaceEmit(int newVertexCount, boolean flip) {}

    public MeshData buildChunkMesh(
            ChunkProvider chunkProvider,
            Atlas atlas,
            BlockRenderMap blockRenderMap,
            int cx,
            int cz
    ) {
        final int cs = EngineConfig.CHUNK_SIZE;
        final int h  = EngineConfig.WORLD_HEIGHT;

        final Chunk center = chunkProvider.getChunk(cx, cz);
        final short[] centerRaw = center.raw();

        // Dynamic buffers (grow as needed)
        float[] v = new float[5 * 4 * 2048];
        int[]   i = new int[6 * 2048];

        int vCount = 0; // vertex count (not floats)
        int iCount = 0;

        BlockAccessor accessor = new BlockAccessor(chunkProvider);

        final int baseWx = cx * cs;
        final int baseWz = cz * cs;

        for (int z = 0; z < cs; z++) {
            for (int x = 0; x < cs; x++) {
                for (int y = 0; y < h; y++) {
                    short id = centerRaw[BlockAccessor.index(cs, h, x, y, z)];
                    if (id == Blocks.AIR) continue;

                    int wx = baseWx + x;
                    int wz = baseWz + z;

                    // +X
                    if (accessor.blockAtWorld(wx + 1, y, wz) == Blocks.AIR) {
                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        var fe = emitFace(
                                v, vCount, wx, y, wz, Face.PX,
                                atlas.uv(blockRenderMap.tileName(id, Face.PX))
                        );
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());
                    }

                    // -X
                    if (accessor.blockAtWorld(wx - 1, y, wz) == Blocks.AIR) {
                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        var fe = emitFace(
                                v, vCount, wx, y, wz, Face.NX,
                                atlas.uv(blockRenderMap.tileName(id, Face.NX))
                        );
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());
                    }

                    // +Y
                    if (accessor.blockAtWorld(wx, y + 1, wz) == Blocks.AIR) {
                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        var fe = emitFace(
                                v, vCount, wx, y, wz, Face.PY,
                                atlas.uv(blockRenderMap.tileName(id, Face.PY))
                        );
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());
                    }

                    // -Y
                    if (accessor.blockAtWorld(wx, y - 1, wz) == Blocks.AIR) {
                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        var fe = emitFace(
                                v, vCount, wx, y, wz, Face.NY,
                                atlas.uv(blockRenderMap.tileName(id, Face.NY))
                        );
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());
                    }

                    // +Z
                    if (accessor.blockAtWorld(wx, y, wz + 1) == Blocks.AIR) {
                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        var fe = emitFace(
                                v, vCount, wx, y, wz, Face.PZ,
                                atlas.uv(blockRenderMap.tileName(id, Face.PZ))
                        );
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());
                    }

                    // -Z
                    if (accessor.blockAtWorld(wx, y, wz - 1) == Blocks.AIR) {
                        var grow = ensureCapacity(v, i, vCount, iCount, 1);
                        v = grow.v; i = grow.i;

                        int base = vCount;
                        var fe = emitFace(
                                v, vCount, wx, y, wz, Face.NZ,
                                atlas.uv(blockRenderMap.tileName(id, Face.NZ))
                        );
                        vCount = fe.newVertexCount();
                        iCount = emitIndices(i, iCount, base, fe.flip());
                    }
                }
            }
        }

        // Trim to exact
        float[] outV = Arrays.copyOf(v, vCount * 5);
        int[] outI = Arrays.copyOf(i, iCount);
        return new MeshData(outV, outI);
    }

    private static FaceEmit emitFace(float[] v, int vertexCount, int wx, int y, int wz, int face, Atlas.UvRect uv) {
        float u0 = uv.u0();
        float u1 = uv.u1();

        float v0 = uv.v1();
        float v1 = uv.v0();

        int base = vertexCount;

        // ---- KEEP your current vertex placements here (any order) ----
        switch (face) {
            case Face.PX -> {
                put(v, vertexCount++, wx + 1, y,     wz,     u0, v0);
                put(v, vertexCount++, wx + 1, y,     wz + 1, u1, v0);
                put(v, vertexCount++, wx + 1, y + 1, wz + 1, u1, v1);
                put(v, vertexCount++, wx + 1, y + 1, wz,     u0, v1);
            }
            case Face.NX -> {
                put(v, vertexCount++, wx, y,     wz + 1, u0, v0);
                put(v, vertexCount++, wx, y,     wz,     u1, v0);
                put(v, vertexCount++, wx, y + 1, wz,     u1, v1);
                put(v, vertexCount++, wx, y + 1, wz + 1, u0, v1);
            }
            case Face.PY -> {
                put(v, vertexCount++, wx,     y + 1, wz,     u0, v0);
                put(v, vertexCount++, wx + 1, y + 1, wz,     u1, v0);
                put(v, vertexCount++, wx + 1, y + 1, wz + 1, u1, v1);
                put(v, vertexCount++, wx,     y + 1, wz + 1, u0, v1);
            }
            case Face.NY -> {
                put(v, vertexCount++, wx,     y, wz + 1, u0, v0);
                put(v, vertexCount++, wx + 1, y, wz + 1, u1, v0);
                put(v, vertexCount++, wx + 1, y, wz,     u1, v1);
                put(v, vertexCount++, wx,     y, wz,     u0, v1);
            }
            case Face.PZ -> {
                put(v, vertexCount++, wx + 1, y,     wz + 1, u0, v0);
                put(v, vertexCount++, wx,     y,     wz + 1, u1, v0);
                put(v, vertexCount++, wx,     y + 1, wz + 1, u1, v1);
                put(v, vertexCount++, wx + 1, y + 1, wz + 1, u0, v1);
            }
            case Face.NZ -> {
                put(v, vertexCount++, wx,     y,     wz, u0, v0);
                put(v, vertexCount++, wx + 1, y,     wz, u1, v0);
                put(v, vertexCount++, wx + 1, y + 1, wz, u1, v1);
                put(v, vertexCount++, wx,     y + 1, wz, u0, v1);
            }
            default -> throw new IllegalArgumentException("Unknown face: " + face);
        }

        // Compute face normal from first triangle (base, base+1, base+2)
        float ax = v[base * 5],     ay = v[base * 5 + 1],     az = v[base * 5 + 2];
        float bx = v[(base + 1) * 5], by = v[(base + 1) * 5 + 1], bz = v[(base + 1) * 5 + 2];
        float cx = v[(base + 2) * 5], cy = v[(base + 2) * 5 + 1], cz = v[(base + 2) * 5 + 2];

        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float acx = cx - ax, acy = cy - ay, acz = cz - az;

        // normal = AB x AC
        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;

        // Expected outward normal for the face
        float ex = 0, ey = 0, ez = 0;
        switch (face) {
            case Face.PX -> ex = 1;
            case Face.NX -> ex = -1;
            case Face.PY -> ey = 1;
            case Face.NY -> ey = -1;
            case Face.PZ -> ez = 1;
            case Face.NZ -> ez = -1;
        }

        // If dot(normal, expected) < 0, winding is backwards => flip indices
        boolean flip = (nx * ex + ny * ey + nz * ez) < 0f;

        return new FaceEmit(vertexCount, flip);
    }

    private static void put(float[] v, int vertexIndex, float x, float y, float z, float u, float vv) {
        int o = vertexIndex * 5;
        v[o]     = x;
        v[o + 1] = y;
        v[o + 2] = z;
        v[o + 3] = u;
        v[o + 4] = vv;
    }

    private static int emitIndices(int[] idx, int iCount, int baseVertex, boolean flip) {
        if (!flip) {
            // CCW
            idx[iCount++] = baseVertex + 0;
            idx[iCount++] = baseVertex + 1;
            idx[iCount++] = baseVertex + 2;

            idx[iCount++] = baseVertex + 2;
            idx[iCount++] = baseVertex + 3;
            idx[iCount++] = baseVertex + 0;
        } else {
            // CW -> flip to CCW by swapping winding
            idx[iCount++] = baseVertex + 0;
            idx[iCount++] = baseVertex + 2;
            idx[iCount++] = baseVertex + 1;

            idx[iCount++] = baseVertex + 0;
            idx[iCount++] = baseVertex + 3;
            idx[iCount++] = baseVertex + 2;
        }
        return iCount;
    }


    private record GrowResult(float[] v, int[] i) {}

    private static GrowResult ensureCapacity(float[] v, int[] i, int vCount, int iCount, int addQuads) {
        int addVerts = addQuads * 4;
        int addIdx   = addQuads * 6;

        int neededV = (vCount + addVerts) * 5;
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