package org.aouessar.core.gen.config;

import org.aouessar.core.world.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry that maps block names (from JSON) to block IDs.
 * Used during config loading to resolve string block names.
 */
public final class BlockRegistry {
    private static final Map<String, Short> NAME_TO_ID = new HashMap<>();

    static {
        // Surface blocks
        NAME_TO_ID.put("GRASS", Blocks.GRASS);
        NAME_TO_ID.put("DIRT", Blocks.DIRT);
        NAME_TO_ID.put("STONE", Blocks.STONE);
        NAME_TO_ID.put("WATER", Blocks.WATER);
        NAME_TO_ID.put("SAND", Blocks.SAND);
        NAME_TO_ID.put("SNOW", Blocks.SNOW);
        NAME_TO_ID.put("GRAVEL", Blocks.GRAVEL);
        NAME_TO_ID.put("CLAY", Blocks.CLAY);
        NAME_TO_ID.put("ICE", Blocks.ICE);
        NAME_TO_ID.put("BEDROCK", Blocks.BEDROCK);
        NAME_TO_ID.put("DEEPSLATE", Blocks.DEEPSLATE);
        NAME_TO_ID.put("SANDSTONE", Blocks.SANDSTONE);
        NAME_TO_ID.put("DESERT_SAND", Blocks.DESERT_SAND);
        NAME_TO_ID.put("DESERT_SAND_STONE", Blocks.DESERT_SANDSTONE);
        NAME_TO_ID.put("DESERT_SANDSTONE", Blocks.DESERT_SANDSTONE);
        NAME_TO_ID.put("PODZOl_DIRT", Blocks.PODZOl_DIRT);
        NAME_TO_ID.put("PODZOL_DIRT", Blocks.PODZOl_DIRT);
        NAME_TO_ID.put("SNOW_GRASS", Blocks.SNOW_GRASS);
        NAME_TO_ID.put("DRY_GRASS", Blocks.DRY_GRASS);

        // Vegetation
        NAME_TO_ID.put("BUSH", Blocks.BUSH);
        NAME_TO_ID.put("TALL_GRASS", Blocks.TALL_GRASS);
        NAME_TO_ID.put("FLOWER_RED", Blocks.FLOWER_RED);
        NAME_TO_ID.put("FLOWER_YELLOW", Blocks.FLOWER_YELLOW);
        NAME_TO_ID.put("DRY_WHEAT", Blocks.DRY_WHEAT);
        NAME_TO_ID.put("BERRY_BUSH", Blocks.BERRY_BUSH);
        NAME_TO_ID.put("FLOWER_CORNFLOWER", Blocks.FLOWER_CORNFLOWER);
        NAME_TO_ID.put("FLOWER_HOUSTONIA", Blocks.FLOWER_HOUSTONIA);
        NAME_TO_ID.put("FLOWER_OXEYE_DAISY", Blocks.FLOWER_OXEYE_DAISY);
        NAME_TO_ID.put("FLOWER_RED_DOUBLE", Blocks.FLOWER_RED_DOUBLE);
        NAME_TO_ID.put("VINE", Blocks.VINE);

        // Tree components (logs and leaves)
        NAME_TO_ID.put("OAK_LOG", Blocks.OAK_LOG);
        NAME_TO_ID.put("OAK_LEAVES", Blocks.OAK_LEAVES);
        NAME_TO_ID.put("ACACIA_LOG", Blocks.ACACIA_LOG);
        NAME_TO_ID.put("ACACIA_LEAVES", Blocks.ACACIA_LEAVES);
        NAME_TO_ID.put("JUNGLE_LOG", Blocks.JUNGLE_LOG);
        NAME_TO_ID.put("JUNGLE_LEAVES", Blocks.JUNGLE_LEAVES);
        NAME_TO_ID.put("SPRUCE_LOG", Blocks.SPRUCE_LOG);
        NAME_TO_ID.put("SPRUCE_LEAVES", Blocks.SPRUCE_LEAVES);
        NAME_TO_ID.put("SNOW_LOG", Blocks.SNOW_LOG);
        NAME_TO_ID.put("SNOW_LEAVES", Blocks.SNOW_LEAVES);
        NAME_TO_ID.put("CACTUS", Blocks.CACTUS);

        // Special blocks
        NAME_TO_ID.put("GLASS", Blocks.GLASS);
        NAME_TO_ID.put("AIR", Blocks.AIR);
    }

    private BlockRegistry() {}

    /**
     * Get block ID by name.
     * @param name block name (case-insensitive)
     * @return block ID or -1 if not found
     */
    public static short getBlockId(String name) {
        if (name == null || name.isBlank()) return -1;
        Short id = NAME_TO_ID.get(name.toUpperCase().trim());
        return id != null ? id : -1;
    }

    /**
     * Check if a block name is registered.
     */
    public static boolean isRegistered(String name) {
        return getBlockId(name) >= 0;
    }
}
