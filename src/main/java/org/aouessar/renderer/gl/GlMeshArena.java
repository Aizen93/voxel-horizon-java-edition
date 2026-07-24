package org.aouessar.renderer.gl;

import org.aouessar.renderer.mesh.MeshData;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL40.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL43.glMultiDrawElementsIndirect;

/**
 * Shared-buffer mesh storage + multi-draw-indirect batching for the 8-float
 * tiled chunk format (GL 4.6 path).
 * <p>
 * Instead of one VAO/VBO/EBO and one glDrawElements per chunk (thousands of
 * driver calls per frame at large view radii), chunk meshes live as regions
 * inside a few large shared buffers. Each draw pass queues the visible
 * regions and flushes them as ONE glMultiDrawElementsIndirect per arena —
 * the GPU walks the command list itself.
 * <p>
 * Regions are recycled through offset-sorted free lists with merge-on-free.
 * When an arena fills up, a new one is created (draws stay batched per
 * arena, so the call count stays at "number of arenas", not chunks).
 */
public final class GlMeshArena implements AutoCloseable {

    private static final long VERT_BYTES_PER_ARENA = 384L * 1024 * 1024;
    private static final long IDX_BYTES_PER_ARENA = 96L * 1024 * 1024;
    private static final int CMD_INTS = 5; // count, inst, firstIndex, baseVertex, baseInstance

    private final int strideFloats;
    private final int strideBytes;
    private final int[] attribSizes;
    private final List<Arena> arenas = new ArrayList<>();

    /** Arena for the near-field tiled chunk format (8 floats/vertex). */
    public GlMeshArena() {
        this(8, new int[]{3, 2, 2, 1});
    }

    /** Arena for an arbitrary float vertex layout (e.g. LOD: 9 = 3+3+3). */
    public GlMeshArena(int strideFloats, int[] attribSizes) {
        this.strideFloats = strideFloats;
        this.strideBytes = strideFloats * Float.BYTES;
        this.attribSizes = attribSizes.clone();
    }

    /** One shared VBO/EBO/VAO + free lists + per-frame command queue. */
    private final class Arena {
        final int vao;
        final int vbo;
        final int ebo;
        final int indirectBo;
        final TreeMap<Long, Long> vFree = new TreeMap<>(); // offset -> size
        final TreeMap<Long, Long> iFree = new TreeMap<>();
        IntBuffer cmds = MemoryUtil.memAllocInt(4096 * CMD_INTS);
        int queued = 0;

        Arena() {
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
            ebo = glGenBuffers();
            indirectBo = glGenBuffers();

            glBindVertexArray(vao);
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferData(GL_ARRAY_BUFFER, VERT_BYTES_PER_ARENA, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, IDX_BYTES_PER_ARENA, GL_DYNAMIC_DRAW);

            long off = 0;
            for (int a = 0; a < attribSizes.length; a++) {
                glEnableVertexAttribArray(a);
                glVertexAttribPointer(a, attribSizes[a], GL_FLOAT, false, strideBytes, off);
                off += attribSizes[a] * (long) Float.BYTES;
            }
            glBindVertexArray(0);

            vFree.put(0L, VERT_BYTES_PER_ARENA);
            iFree.put(0L, IDX_BYTES_PER_ARENA);
        }
    }

    /** A chunk mesh living inside an arena. draw() queues it for the flush. */
    public final class Region implements IGlMesh {
        private final Arena arena;
        private final long vOff, vBytes, iOff, iBytes;
        private final int indexCount, baseVertex, firstIndex;

        private Region(Arena a, long vOff, long vBytes, long iOff, long iBytes, int indexCount) {
            this.arena = a;
            this.vOff = vOff;
            this.vBytes = vBytes;
            this.iOff = iOff;
            this.iBytes = iBytes;
            this.indexCount = indexCount;
            this.baseVertex = (int) (vOff / strideBytes);
            this.firstIndex = (int) (iOff / Float.BYTES);
        }

        @Override
        public void draw() {
            Arena a = arena;
            if ((a.queued + 1) * CMD_INTS > a.cmds.capacity()) {
                IntBuffer bigger = MemoryUtil.memAllocInt(a.cmds.capacity() * 2);
                a.cmds.flip();
                bigger.put(a.cmds);
                MemoryUtil.memFree(a.cmds);
                a.cmds = bigger;
            }
            a.cmds.put(indexCount).put(1).put(firstIndex).put(baseVertex).put(0);
            a.queued++;
        }

        @Override
        public void close() {
            free(arena.vFree, vOff, vBytes);
            free(arena.iFree, iOff, iBytes);
        }
    }

    /** Copy a finished mesh into arena storage (render thread). */
    public Region upload(MeshData md) {
        if (md.floatsPerVertex != strideFloats) {
            throw new IllegalArgumentException("Arena expects " + strideFloats
                    + " floats/vertex, got " + md.floatsPerVertex);
        }
        long vBytes = align(md.vertices.length * (long) Float.BYTES, strideBytes);
        long iBytes = align(md.indices.length * (long) Float.BYTES, Float.BYTES);

        for (Arena a : arenas) {
            Region r = tryUpload(a, md, vBytes, iBytes);
            if (r != null) return r;
        }
        Arena a = new Arena();
        arenas.add(a);
        Region r = tryUpload(a, md, vBytes, iBytes);
        if (r == null) throw new IllegalStateException("Mesh larger than an arena: " + vBytes + "B");
        return r;
    }

    private Region tryUpload(Arena a, MeshData md, long vBytes, long iBytes) {
        long vOff = alloc(a.vFree, vBytes);
        if (vOff < 0) return null;
        long iOff = alloc(a.iFree, iBytes);
        if (iOff < 0) {
            free(a.vFree, vOff, vBytes);
            return null;
        }

        glBindBuffer(GL_ARRAY_BUFFER, a.vbo);
        glBufferSubData(GL_ARRAY_BUFFER, vOff, md.vertices);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, a.ebo);
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, iOff, md.indices);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        return new Region(a, vOff, vBytes, iOff, iBytes, md.indices.length);
    }

    /** Issue every queued region as one indirect multi-draw per arena. */
    public void flush() {
        for (Arena a : arenas) {
            if (a.queued == 0) continue;
            a.cmds.flip();
            glBindVertexArray(a.vao);
            glBindBuffer(GL_DRAW_INDIRECT_BUFFER, a.indirectBo);
            glBufferData(GL_DRAW_INDIRECT_BUFFER, a.cmds, GL_STREAM_DRAW);
            glMultiDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_INT, 0L, a.queued, 0);
            a.cmds.clear();
            a.queued = 0;
        }
        glBindVertexArray(0);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
    }

    public int arenaCount() {
        return arenas.size();
    }

    @Override
    public void close() {
        for (Arena a : arenas) {
            glDeleteBuffers(a.vbo);
            glDeleteBuffers(a.ebo);
            glDeleteBuffers(a.indirectBo);
            glDeleteVertexArrays(a.vao);
            MemoryUtil.memFree(a.cmds);
        }
        arenas.clear();
    }

    // ---- offset-sorted free-list with merge-on-free ----

    private static long align(long v, long a) {
        return (v + a - 1) / a * a;
    }

    private static long alloc(TreeMap<Long, Long> freeList, long bytes) {
        for (var e : freeList.entrySet()) {
            long off = e.getKey(), size = e.getValue();
            if (size < bytes) continue;
            freeList.remove(off);
            if (size > bytes) freeList.put(off + bytes, size - bytes);
            return off;
        }
        return -1;
    }

    private static void free(TreeMap<Long, Long> freeList, long off, long bytes) {
        var lower = freeList.floorEntry(off);
        if (lower != null && lower.getKey() + lower.getValue() == off) {
            off = lower.getKey();
            bytes += lower.getValue();
            freeList.remove(lower.getKey());
        }
        var higher = freeList.ceilingEntry(off + bytes);
        if (higher != null && higher.getKey() == off + bytes) {
            bytes += higher.getValue();
            freeList.remove(higher.getKey());
        }
        freeList.put(off, bytes);
    }
}
