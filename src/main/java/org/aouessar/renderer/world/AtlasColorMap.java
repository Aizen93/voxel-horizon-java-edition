package org.aouessar.renderer.world;

import org.aouessar.renderer.atlas.Atlas;
import org.aouessar.renderer.mesh.Face;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Average color per block id, derived from the actual texture atlas.
 * <p>
 * Far-field LOD tiles are vertex-colored (no textures at that distance), and
 * sampling the atlas keeps the LOD palette automatically in sync with the
 * near-field textures — grass, sand, snow, leaves all match what the player
 * sees up close.
 * <p>
 * CPU-side only (plain STB decode); safe to build before or after GL init.
 */
public final class AtlasColorMap {

    private static final int MAX_BLOCK_ID = 64;

    // r,g,b per block id
    private final float[] colors = new float[MAX_BLOCK_ID * 3];

    public AtlasColorMap(String atlasPngResource, Atlas atlas, BlockRenderMap blockRenderMap) {
        ByteBuffer pixels = null;
        int w;
        int h;
        try {
            byte[] bytes;
            try (InputStream in = AtlasColorMap.class.getResourceAsStream(atlasPngResource)) {
                if (in == null) throw new IllegalArgumentException("Resource not found: " + atlasPngResource);
                bytes = in.readAllBytes();
            }

            ByteBuffer data = ByteBuffer.allocateDirect(bytes.length);
            data.put(bytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer wb = stack.mallocInt(1);
                IntBuffer hb = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                STBImage.stbi_set_flip_vertically_on_load(false);
                pixels = STBImage.stbi_load_from_memory(data, wb, hb, comp, 4);
                if (pixels == null) {
                    throw new IllegalStateException("STB failed: " + STBImage.stbi_failure_reason());
                }
                w = wb.get(0);
                h = hb.get(0);
            }

            for (short id = 0; id < MAX_BLOCK_ID; id++) {
                float[] rgb = averageTileColor(pixels, w, h, atlas, blockRenderMap, id);
                colors[id * 3] = rgb[0];
                colors[id * 3 + 1] = rgb[1];
                colors[id * 3 + 2] = rgb[2];
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to build AtlasColorMap from " + atlasPngResource, e);
        } finally {
            if (pixels != null) STBImage.stbi_image_free(pixels);
        }
    }

    public float r(short blockId) { return colors[idx(blockId)]; }
    public float g(short blockId) { return colors[idx(blockId) + 1]; }
    public float b(short blockId) { return colors[idx(blockId) + 2]; }

    private static int idx(short blockId) {
        int id = (blockId >= 0 && blockId < MAX_BLOCK_ID) ? blockId : 0;
        return id * 3;
    }

    private static float[] averageTileColor(
            ByteBuffer pixels, int atlasW, int atlasH,
            Atlas atlas, BlockRenderMap brm, short blockId
    ) {
        try {
            // Top-face tile: that's what far terrain shows from above/afar
            Atlas.UvRect uv = atlas.uv(brm.tileName(blockId, Face.PY));

            int x0 = Math.max(0, Math.round(uv.u0() * atlasW));
            int y0 = Math.max(0, Math.round(uv.v0() * atlasH));
            int x1 = Math.min(atlasW, Math.round(uv.u1() * atlasW));
            int y1 = Math.min(atlasH, Math.round(uv.v1() * atlasH));

            double r = 0;
            double g = 0;
            double b = 0;
            double wSum = 0;

            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int o = (y * atlasW + x) * 4;
                    int a = pixels.get(o + 3) & 0xFF;
                    if (a < 16) continue; // ignore transparent texels (leaves, plants)
                    double wgt = a / 255.0;
                    r += (pixels.get(o) & 0xFF) * wgt;
                    g += (pixels.get(o + 1) & 0xFF) * wgt;
                    b += (pixels.get(o + 2) & 0xFF) * wgt;
                    wSum += wgt;
                }
            }

            if (wSum <= 0) return new float[]{0.5f, 0.5f, 0.5f};
            return new float[]{
                    (float) (r / wSum / 255.0),
                    (float) (g / wSum / 255.0),
                    (float) (b / wSum / 255.0)
            };
        } catch (Exception e) {
            // Unknown tile / block id — neutral gray keeps rendering robust
            return new float[]{0.5f, 0.5f, 0.5f};
        }
    }
}
