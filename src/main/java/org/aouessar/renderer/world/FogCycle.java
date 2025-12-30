package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.joml.Math;

public final class FogCycle {

    // Output (public read-only via getters)
    private float r, g, b;
    private float startMul = 1.0f;
    private float rangeMul = 1.0f;

    // Debug / hooks (set from renderer input)
    private float rain01; // 0..1
    private float mist01; // 0..1 (optional extra intensity)

    public void setRain01(float v) { this.rain01 = Math.clamp(v, 0.0f, 1.0f); }
    public void setMist01(float v) { this.mist01 = Math.clamp(v, 0.0f, 1.0f); }

    public float r() { return r; }
    public float g() { return g; }
    public float b() { return b; }
    public float startMul() { return startMul; }
    public float rangeMul() { return rangeMul; }

    private float twilight01; // 0..1

    public float twilight01() {
        return twilight01;
    }

    /**
     * Update fog outputs for this frame.
     *
     * @param nowSeconds current time in seconds (e.g. glfwGetTime())
     * @param cameraY    camera altitude in world units
     */
    public void update(float nowSeconds, float cameraY) {
        // ----- Time-of-day -----
        float dayLen = Math.max(0.001f, RendererConfig.DAY_LENGTH_SECONDS);
        float t = (nowSeconds / dayLen) % 1.0f;               // 0..1
        float angle = (float) (t * Math.PI * 2.0);  // 0..2π

        // Sun height: -1..+1 (negative = night)
        float sunH = (float) Math.sin(angle);

        // Day factor: 0..1 (0 night, 1 day) with smoother transition
        float day01 = sunH * 0.5f + 0.5f;
        day01 = Math.clamp(day01, 0.0f, 1.0f);
        day01 = day01 * day01 * (3.0f - 2.0f * day01); // smoothstep

        // Base fog color (night <-> day)
        float fr = Math.lerp(RendererConfig.FOG_NIGHT_R, RendererConfig.FOG_DAY_R, day01);
        float fg = Math.lerp(RendererConfig.FOG_NIGHT_G, RendererConfig.FOG_DAY_G, day01);
        float fb = Math.lerp(RendererConfig.FOG_NIGHT_B, RendererConfig.FOG_DAY_B, day01);

        // ----- Twilight glow (orange only near horizon) -----
        float width = Math.max(0.0001f, RendererConfig.SUNRISE_SUNSET_WIDTH);

        // 1 at horizon, 0 away from horizon (works symmetrically for sunrise/sunset)
        float twilight = 1.0f - (Math.abs(sunH) / width);
        twilight = Math.clamp(twilight, 0.0f, 1.0f);
        twilight = twilight * twilight * (3.0f - 2.0f * twilight); // smoothstep

        // Apply strength
        twilight *= RendererConfig.FOG_WARM_STRENGTH;
        twilight = Math.clamp(twilight, 0.0f, 1.0f);
        this.twilight01 = Math.clamp(twilight / Math.max(0.0001f, RendererConfig.FOG_WARM_STRENGTH), 0.0f, 1.0f);

        // Warm target (strong orange haze) — you can keep config, but these values WORK visually
        float warmR = RendererConfig.FOG_WARM_R;
        float warmG = RendererConfig.FOG_WARM_G;
        float warmB = RendererConfig.FOG_WARM_B;

        // 1) Saturated lerp toward warm (gives hue shift)
        fr = Math.lerp(fr, warmR, twilight);
        fg = Math.lerp(fg, warmG, twilight);
        fb = Math.lerp(fb, warmB, twilight);

        // 2) Add a “glow” that favors R/G (prevents beige/grey wash)
        float glow = twilight * RendererConfig.FOG_TWILIGHT_GLOW; // 0..~0.4
        fr += glow;
        fg += glow * 0.75f;
        fb += glow * 0.25f;

        // Clamp final
        fr = Math.clamp(fr, 0.0f, 1.0f);
        fg = Math.clamp(fg, 0.0f, 1.0f);
        fb = Math.clamp(fb, 0.0f, 1.0f);

        // ----- Weather hooks -----
        float rain = Math.clamp(rain01, 0.0f, 1.0f);
        float valley = valleyMistFromAltitude(cameraY);
        float mist = Math.clamp(valley * Math.clamp(mist01, 0.0f, 1.0f), 0.0f, 1.0f);

        float sMul = 1.0f;
        float rMul = 1.0f;

        sMul *= Math.lerp(1.0f, RendererConfig.RAIN_FOG_START_MUL, rain);
        rMul *= Math.lerp(1.0f, RendererConfig.RAIN_FOG_RANGE_MUL, rain);

        sMul *= Math.lerp(1.0f, RendererConfig.MIST_FOG_START_MUL, mist);
        rMul *= Math.lerp(1.0f, RendererConfig.MIST_FOG_RANGE_MUL, mist);

        // outputs
        this.r = Math.clamp(fr, 0f, 1f);
        this.g = Math.clamp(fg, 0f, 1f);
        this.b = Math.clamp(fb, 0f, 1f);
        this.startMul = sMul;
        this.rangeMul = rMul;
    }

    /**
     * 0..1 where 1 = thick mist (low), 0 = no mist (high).
     * Tune these two numbers to change where mist lives.
     */
    public static float valleyMistFromAltitude(float y) {
        float t = (y - 50.0f) / 80.0f; // y=50 -> 0, y=130 -> 1
        t = Math.clamp(t, 0.0f, 1.0f);
        return 1.0f - t;
    }
}