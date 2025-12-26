package org.aouessar.renderer.gl;

public interface IGlMesh extends AutoCloseable {
    void draw();
    @Override void close();
}