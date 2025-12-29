package org.aouessar.shared;

public final class EngineConfig {
    private EngineConfig() {}

    // -----------------------------
    // World geometry (Minecraft-style)
    // -----------------------------
    public static final int CHUNK_SIZE = 16;

    public static final int REGION_SIZE_CHUNKS = 16;
    public static final int REGION_SIZE_BLOCKS = REGION_SIZE_CHUNKS * CHUNK_SIZE;

    // Minecraft 1.18+ overworld vertical range: -64..319 (inclusive) :contentReference[oaicite:1]{index=1}
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    public static final int WORLD_HEIGHT = (MAX_Y - MIN_Y + 1); // 384
    public static final float WATER_TOP_DELTA = 0.2f;

    // Vanilla-ish default sea level used by many tools/resources is 62 :contentReference[oaicite:2]{index=2}
    public static final int SEA_LEVEL = 62;

    // -----------------------------
    // Threading (core + renderer)
    // -----------------------------
    public static final int CPU_WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
}
