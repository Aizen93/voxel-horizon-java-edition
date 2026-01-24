package org.aouessar.core.gen.config;

import org.aouessar.core.world.Blocks;

/**
 * Tree types that map to tree generation methods.
 * Unlike simple blocks, trees are multi-block structures that require
 * specific placement logic defined in ChunkBuilder.
 */
public enum TreeType {
    /** Standard oak tree */
    OAK(Blocks.STRUCT_OAK_TREE),
    /** Desert cactus (treated as a "tree" for placement purposes) */
    CACTUS(Blocks.CACTUS),
    /** Snow biome tree with snowy leaves */
    SNOW_TREE(Blocks.STRUCT_SNOW_TREE),
    /** Coniferous spruce tree */
    SPRUCE(Blocks.STRUCT_SPRUCE_TREE),
    /** African-style acacia tree */
    ACACIA(Blocks.STRUCT_ACACIA_TREE),
    /** Jungle tree */
    JUNGLE(Blocks.STRUCT_JUNGLE_TREE),
    /** Large 2x2 jungle tree */
    MEGA_JUNGLE(Blocks.STRUCT_MEGA_JUNGLE);

    private final short structureMarkerId;

    TreeType(short structureMarkerId) {
        this.structureMarkerId = structureMarkerId;
    }

    /**
     * Returns the structure marker ID used by StructureBuilder for placement.
     */
    public short getStructureMarkerId() {
        return structureMarkerId;
    }

    /**
     * Parse tree type from string (case-insensitive).
     * @param name the tree type name from JSON
     * @return the TreeType or null if not found
     */
    public static TreeType fromString(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return TreeType.valueOf(name.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
