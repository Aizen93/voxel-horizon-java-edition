package org.aouessar.renderer.gl;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

public final class GlShaderProgram implements AutoCloseable {

    private final int programId;

    public GlShaderProgram(String vertResourcePath, String fragResourcePath) {
        int vs = compileShader(GL_VERTEX_SHADER, readResource(vertResourcePath));
        int fs = compileShader(GL_FRAGMENT_SHADER, readResource(fragResourcePath));

        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);

        int linked = glGetProgrami(programId, GL_LINK_STATUS);
        if (linked == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteProgram(programId);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new IllegalStateException("Shader link failed:\n" + log);
        }

        glDetachShader(programId, vs);
        glDetachShader(programId, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    public void use() {
        glUseProgram(programId);
    }

    public int id() {
        return programId;
    }

    public void setUniform1i(String name, int v) {
        int loc = glGetUniformLocation(programId, name);
        if (loc < 0) throw new IllegalStateException("Uniform not found: " + name);
        glUniform1i(loc, v);
    }

    public void setUniformMat4(String name, Matrix4f mat) {
        int loc = glGetUniformLocation(programId, name);
        if (loc < 0) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            mat.get(fb);
            glUniformMatrix4fv(loc, false, fb);
        }
    }

    @Override
    public void close() {
        glUseProgram(0);
        glDeleteProgram(programId);
    }

    private static int compileShader(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);

        int ok = glGetShaderi(id, GL_COMPILE_STATUS);
        if (ok == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            glDeleteShader(id);
            throw new IllegalStateException("Shader compile failed:\n" + log);
        }
        return id;
    }

    private static String readResource(String path) {
        InputStream in = GlShaderProgram.class.getResourceAsStream(path);
        if (in == null) throw new IllegalArgumentException("Resource not found: " + path);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder(4096);
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed reading resource: " + path, e);
        }
    }

    public void setUniform2f(String name, float x, float y) {
        int loc = glGetUniformLocation(programId, name);
        if (loc < 0) {
            throw new IllegalStateException("Uniform not found: " + name);
        }
        glUniform2f(loc, x, y);
    }

    public void setUniform1f(String name, float v) {
        int loc = glGetUniformLocation(programId, name);
        if (loc < 0) return; // uniform not found/optimized out
        glUniform1f(loc, v);
    }

    public void setUniform3f(String name, float x, float y, float z) {
        int loc = glGetUniformLocation(programId, name);
        if (loc < 0) return; // uniform not found/optimized out
        glUniform3f(loc, x, y, z);
    }
}