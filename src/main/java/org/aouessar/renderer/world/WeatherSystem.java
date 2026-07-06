package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;

/**
 * Weather scheduling: rain (or snow, in cold biomes) rolls in "sometimes",
 * driven by a deterministic per-slot hash of the world seed — every
 * {@link RendererConfig#WEATHER_SLOT_SECONDS} the sky rolls for a storm, and
 * consecutive rainy slots merge into longer weather fronts. Intensity ramps
 * smoothly, wind drifts slowly in direction and strength (and stiffens during
 * storms), and heavy rain throws lightning: a whole-sky flash plus a strike
 * event the ambient effects turn into a visible bolt.
 * <p>
 * Override with -Dvoxel.weather=clear|rain|snow (default auto).
 */
public final class WeatherSystem {

    private enum Forced { AUTO, CLEAR, RAIN, SNOW }

    private final long seed;
    private final Forced forced;

    private float precip01 = 0f;
    private boolean snowing = false;

    private float windX = 0f, windZ = 0f, windStrength = 0f;

    private float flash01 = 0f;
    private boolean strikePending = false;
    private double lastStrikeRoll = 0;

    public WeatherSystem(long seed) {
        this.seed = seed;
        this.forced = switch (System.getProperty("voxel.weather", "auto").toLowerCase()) {
            case "clear" -> Forced.CLEAR;
            case "rain" -> Forced.RAIN;
            case "snow" -> Forced.SNOW;
            default -> Forced.AUTO;
        };
    }

    public void update(double now, float dt, boolean coldBiome) {
        // ---- Target intensity from the storm schedule ----
        float target;
        switch (forced) {
            case CLEAR -> target = 0f;
            case RAIN, SNOW -> target = 1f;
            default -> {
                long slot = (long) Math.floor(now / RendererConfig.WEATHER_SLOT_SECONDS);
                float roll = hash01(slot);
                target = (roll < RendererConfig.WEATHER_RAIN_CHANCE)
                        ? 0.55f + 0.45f * hash01(slot * 7919L + 13L)
                        : 0f;
            }
        }

        float rate = dt / Math.max(0.5f, RendererConfig.WEATHER_RAMP_SECONDS);
        if (precip01 < target) precip01 = Math.min(target, precip01 + rate);
        else precip01 = Math.max(target, precip01 - rate);

        snowing = (forced == Forced.SNOW) || (forced == Forced.AUTO && coldBiome);

        // ---- Wind: slow directional drift, stiffer in storms ----
        float t = (float) now;
        float angle = t * 0.02f + (float) Math.sin(t * 0.043 + seed % 7) * 1.7f;
        windStrength = 0.25f + 0.45f * (0.5f + 0.5f * (float) Math.sin(t * 0.031 + seed % 13))
                + precip01 * 0.5f;
        windX = (float) Math.cos(angle) * windStrength;
        windZ = (float) Math.sin(angle) * windStrength;

        // ---- Lightning (rain only, not snow) ----
        flash01 *= (float) Math.exp(-dt * 5.0);
        if (!snowing && precip01 > 0.5f && now - lastStrikeRoll > 1.0) {
            lastStrikeRoll = now;
            float chance = RendererConfig.LIGHTNING_CHANCE_PER_SEC * precip01;
            if (hash01((long) (now * 997) ^ seed) < chance) {
                flash01 = 1f;
                strikePending = true;
            }
        }
    }

    /** 0..1 precipitation intensity (rain or snow). */
    public float precip01() {
        return precip01;
    }

    /** Rain intensity for fog/light dimming (snow dims less). */
    public float rain01() {
        return snowing ? precip01 * 0.55f : precip01;
    }

    public boolean isSnow() {
        return snowing;
    }

    public float windX() {
        return windX;
    }

    public float windZ() {
        return windZ;
    }

    public float windStrength() {
        return windStrength;
    }

    /** Whole-sky lightning flash, decaying after each strike. */
    public float flash01() {
        return flash01;
    }

    /** True once per strike; reading clears it (spawns the visible bolt). */
    public boolean consumeStrike() {
        boolean s = strikePending;
        strikePending = false;
        return s;
    }

    public String label() {
        if (precip01 < 0.05f) return "clear";
        String kind = snowing ? "snow" : "rain";
        return kind + " " + Math.round(precip01 * 100f) + "%";
    }

    /** Deterministic 0..1 hash of (seed, x). */
    private float hash01(long x) {
        long h = x * 0x9E3779B97F4A7C15L ^ seed;
        h ^= (h >>> 32);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 29);
        return ((h >>> 11) & 0xFFFFFF) / (float) 0x1000000;
    }
}
