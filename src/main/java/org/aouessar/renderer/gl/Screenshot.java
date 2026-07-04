package org.aouessar.renderer.gl;

import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImageWrite.stbi_flip_vertically_on_write;
import static org.lwjgl.stb.STBImageWrite.stbi_write_png;

/** Reads back the currently bound framebuffer and writes it as a PNG. */
public final class Screenshot {
    private Screenshot() {}

    public static boolean save(File file, int width, int height) {
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 3);
        try {
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, pixels);
            stbi_flip_vertically_on_write(true);
            return stbi_write_png(file.getAbsolutePath(), width, height, 3, pixels, width * 3);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }
}
