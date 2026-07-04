package org.aouessar.core.gen.impl;

import org.aouessar.core.math.GlobalTerrainUtils;
import org.aouessar.core.noise.FastNoiseLite;
import org.aouessar.shared.EngineConfig;

/**
 * Per-column river valley field.
 * <p>
 * Rivers follow the zero-contours of a domain-warped low-frequency noise
 * field, which produces long connected meandering lines. This class only
 * answers "how close is this column to a river line" — the actual valley
 * shaping happens inside {@link TerrainColumnSampler}, which blends terrain
 * height down toward sea level across the valley. That is the key to natural
 * rivers: the water is ALWAYS at sea level (like vanilla Minecraft), and the
 * terrain forms a valley around it, instead of water trying to climb hills.
 * <p>
 * Thread-safe: noise instances are configured once and only read.
 */
public final class RiverColumnSampler {

    private final FastNoiseLite warp;
    private final FastNoiseLite riverN;

    public RiverColumnSampler(long seed) {
        warp = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x51BE55ED));
        warp.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warp.SetFrequency(EngineConfig.RIVER_WARP_FREQ);
        warp.SetFractalType(FastNoiseLite.FractalType.DomainWarpProgressive);
        warp.SetFractalOctaves(2);
        warp.SetFractalGain(0.5f);
        warp.SetFractalLacunarity(2.0f);
        warp.SetDomainWarpAmp(EngineConfig.RIVER_WARP_AMP_BLOCKS);

        riverN = new FastNoiseLite(GlobalTerrainUtils.mixSeed(seed, 0x41E5B0AD));
        riverN.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        riverN.SetFrequency(EngineConfig.RIVER_FREQ);
        riverN.SetFractalType(FastNoiseLite.FractalType.FBm);
        riverN.SetFractalOctaves(2);
        riverN.SetFractalGain(0.5f);
        riverN.SetFractalLacunarity(2.0f);
    }

    /**
     * Valley proximity at (wx, wz): 0 = outside any river valley,
     * 1 = on the river center line. Purely noise-based (no height input).
     */
    public float valley01(int wx, int wz) {
        FastNoiseLite.Vector2 p = new FastNoiseLite.Vector2(wx, wz);
        warp.DomainWarp(p);
        float v = Math.abs(riverN.GetNoise(p.x, p.y));

        if (v >= EngineConfig.RIVER_VALLEY_WIDTH) return 0f;
        float t = 1f - v / EngineConfig.RIVER_VALLEY_WIDTH;
        return t * t * (3f - 2f * t);
    }

    /** Water-channel core of the valley (the part that actually holds water). */
    public static float channel01(float valley01) {
        return GlobalTerrainUtils.smoothstep(EngineConfig.RIVER_CHANNEL_START, 0.9f, valley01);
    }
}
