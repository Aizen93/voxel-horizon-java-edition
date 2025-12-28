package org.aouessar.core.world;

public enum RenderLayer {
    OPAQUE,        // stone, dirt...
    CUTOUT,        // leaves/bush textures with alpha holes
    TRANSLUCENT    // water, glass
}