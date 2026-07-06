package org.aouessar.renderer.world;

import org.aouessar.renderer.RendererConfig;
import org.joml.Math; // NB: JOML's clamp is (min, max, value) — NOT (value, min, max)

public final class FogCycle {

    // Output (public read-only via getters)
    private float r, g, b;
    private float startMul = 1.0f;
    private float rangeMul = 1.0f;

    // Sky colors for reflections
    private float skyTopR, skyTopG, skyTopB;
    private float skyHorizonR, skyHorizonG, skyHorizonB;

    // Sun direction (normalized)
    private float sunDirX, sunDirY, sunDirZ;

    // Sunlight color x intensity: multiplies all world shading, so the terrain
    // follows the day cycle (bright day, orange twilight, blue moonlight).
    private float lightR, lightG, lightB;

    public float lightR() { return lightR; }
    public float lightG() { return lightG; }
    public float lightB() { return lightB; }

    // Debug / hooks (set from renderer input)
    private float rain01; // 0..1
    private float mist01; // 0..1 (optional extra intensity)

    public void setRain01(float v) { this.rain01 = Math.clamp(0.0f, 1.0f, v); }
    public void setMist01(float v) { this.mist01 = Math.clamp(0.0f, 1.0f, v); }

    public float r() { return r; }
    public float g() { return g; }
    public float b() { return b; }
    public float startMul() { return startMul; }
    public float rangeMul() { return rangeMul; }

    // Sky color getters for water reflections
    public float skyTopR() { return skyTopR; }
    public float skyTopG() { return skyTopG; }
    public float skyTopB() { return skyTopB; }
    public float skyHorizonR() { return skyHorizonR; }
    public float skyHorizonG() { return skyHorizonG; }
    public float skyHorizonB() { return skyHorizonB; }

    // Sun direction getters
    public float sunDirX() { return sunDirX; }
    public float sunDirY() { return sunDirY; }
    public float sunDirZ() { return sunDirZ; }

    private float twilight01; // 0..1
    private float day01;      // 0 = night, 1 = full day

    public float twilight01() {
        return twilight01;
    }

    public float day01() {
        return day01;
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

        // Sun direction vector (normalized) - sun moves east to west
        float sunCosAngle = (float) Math.cos(angle);
        this.sunDirX = sunCosAngle;  // sun moves along X axis
        this.sunDirY = sunH;         // sun height
        this.sunDirZ = 0.3f;         // slight tilt for visual interest
        // Normalize the sun direction
        float sunLen = (float) java.lang.Math.sqrt(sunDirX * sunDirX + sunDirY * sunDirY + sunDirZ * sunDirZ);
        if (sunLen > 0.0001f) {
            this.sunDirX /= sunLen;
            this.sunDirY /= sunLen;
            this.sunDirZ /= sunLen;
        }

        // Day factor: 0..1 (0 night, 1 day) with smoother transition
        float day01 = sunH * 0.5f + 0.5f;
        day01 = Math.clamp(0.0f, 1.0f, day01);
        day01 = day01 * day01 * (3.0f - 2.0f * day01); // smoothstep
        this.day01 = day01;

        // Base fog color (night <-> day)
        float fr = Math.lerp(RendererConfig.FOG_NIGHT_R, RendererConfig.FOG_DAY_R, day01);
        float fg = Math.lerp(RendererConfig.FOG_NIGHT_G, RendererConfig.FOG_DAY_G, day01);
        float fb = Math.lerp(RendererConfig.FOG_NIGHT_B, RendererConfig.FOG_DAY_B, day01);

        // ----- Twilight glow (orange only near horizon) -----
        float width = Math.max(0.0001f, RendererConfig.SUNRISE_SUNSET_WIDTH);

        // 1 at horizon, 0 away from horizon (works symmetrically for sunrise/sunset)
        float twilight = 1.0f - (Math.abs(sunH) / width);
        twilight = Math.clamp(0.0f, 1.0f, twilight);
        twilight = twilight * twilight * (3.0f - 2.0f * twilight); // smoothstep

        // Apply strength
        twilight *= RendererConfig.FOG_WARM_STRENGTH;
        twilight = Math.clamp(0.0f, 1.0f, twilight);
        this.twilight01 = Math.clamp(0.0f, 1.0f, twilight / Math.max(0.0001f, RendererConfig.FOG_WARM_STRENGTH));

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
        fr = Math.clamp(0.0f, 1.0f, fr);
        fg = Math.clamp(0.0f, 1.0f, fg);
        fb = Math.clamp(0.0f, 1.0f, fb);

        // ----- Weather hooks -----
        float rain = Math.clamp(0.0f, 1.0f, rain01);
        float valley = valleyMistFromAltitude(cameraY);
        float mist = Math.clamp(0.0f, 1.0f, valley * Math.clamp(0.0f, 1.0f, mist01));

        // Storms grey the fog toward a wet overcast tone
        fr = Math.lerp(fr, 0.58f, rain * 0.45f);
        fg = Math.lerp(fg, 0.61f, rain * 0.45f);
        fb = Math.lerp(fb, 0.66f, rain * 0.45f);

        float sMul = 1.0f;
        float rMul = 1.0f;

        sMul *= Math.lerp(1.0f, RendererConfig.RAIN_FOG_START_MUL, rain);
        rMul *= Math.lerp(1.0f, RendererConfig.RAIN_FOG_RANGE_MUL, rain);

        sMul *= Math.lerp(1.0f, RendererConfig.MIST_FOG_START_MUL, mist);
        rMul *= Math.lerp(1.0f, RendererConfig.MIST_FOG_RANGE_MUL, mist);

        // outputs
        this.r = Math.clamp(0f, 1f, fr);
        this.g = Math.clamp(0f, 1f, fg);
        this.b = Math.clamp(0f, 1f, fb);
        this.startMul = sMul;
        this.rangeMul = rMul;

        // ----- Sky colors for water reflections -----
        // Sky top color: brighter blue during day, darker at night
        this.skyTopR = Math.lerp(0.02f, 0.45f, day01);
        this.skyTopG = Math.lerp(0.04f, 0.65f, day01);
        this.skyTopB = Math.lerp(0.08f, 0.95f, day01);

        // Apply twilight boost to sky (sunrise/sunset makes sky warmer)
        float skyTwilightBoost = RendererConfig.SKY_TWILIGHT_BOOST;
        this.skyTopR = Math.lerp(this.skyTopR, warmR * 0.8f, twilight * skyTwilightBoost);
        this.skyTopG = Math.lerp(this.skyTopG, warmG * 0.9f, twilight * skyTwilightBoost);
        this.skyTopB = Math.lerp(this.skyTopB, warmB * 0.7f, twilight * skyTwilightBoost);

        // Horizon color: warmer, brighter near horizon
        this.skyHorizonR = Math.lerp(0.05f, 0.75f, day01);
        this.skyHorizonG = Math.lerp(0.06f, 0.85f, day01);
        this.skyHorizonB = Math.lerp(0.10f, 0.95f, day01);

        // During twilight, horizon becomes orange/red
        this.skyHorizonR = Math.lerp(this.skyHorizonR, warmR, twilight);
        this.skyHorizonG = Math.lerp(this.skyHorizonG, warmG, twilight);
        this.skyHorizonB = Math.lerp(this.skyHorizonB, warmB, twilight);

        // Clamp sky colors
        this.skyTopR = Math.clamp(0f, 1f, this.skyTopR);
        this.skyTopG = Math.clamp(0f, 1f, this.skyTopG);
        this.skyTopB = Math.clamp(0f, 1f, this.skyTopB);
        this.skyHorizonR = Math.clamp(0f, 1f, this.skyHorizonR);
        this.skyHorizonG = Math.clamp(0f, 1f, this.skyHorizonG);
        this.skyHorizonB = Math.clamp(0f, 1f, this.skyHorizonB);

        // ----- Terrain sunlight (intensity + color through the cycle) -----
        // Intensity: full sun once it clears the horizon haze, easing down to
        // a moonlight floor at night (dark, but the world stays readable).
        float sunT = Math.clamp(0f, 1f, (sunH + 0.08f) / 0.33f);
        sunT = sunT * sunT * (3f - 2f * sunT);
        float level = Math.lerp(RendererConfig.NIGHT_LIGHT_FLOOR, 1.0f, sunT);

        // Color: white by day, warm at sunrise/sunset, cool blue under the moon
        float lr = 1f, lg = 1f, lb = 1f;
        float warmCast = twilight * RendererConfig.TWILIGHT_SUNLIGHT_TINT;
        lr = Math.lerp(lr, 1.00f, warmCast);
        lg = Math.lerp(lg, 0.60f, warmCast);
        lb = Math.lerp(lb, 0.34f, warmCast);

        float night = 1f - sunT;
        lr = Math.lerp(lr, 0.62f, night);
        lg = Math.lerp(lg, 0.72f, night);
        lb = Math.lerp(lb, 1.00f, night);

        // Storm clouds mute the sunlight (slightly cold cast)
        float stormDim = 1f - rain * 0.32f;
        this.lightR = lr * level * stormDim;
        this.lightG = lg * level * stormDim;
        this.lightB = lb * level * (1f - rain * 0.24f);

        // Overcast flattens the sky colors too
        this.skyTopR *= (1f - rain * 0.30f);
        this.skyTopG *= (1f - rain * 0.28f);
        this.skyTopB *= (1f - rain * 0.26f);
        this.skyHorizonR *= (1f - rain * 0.26f);
        this.skyHorizonG *= (1f - rain * 0.24f);
        this.skyHorizonB *= (1f - rain * 0.22f);
    }

    /**
     * 0..1 where 1 = thick mist (low), 0 = no mist (high).
     * Tune these two numbers to change where mist lives.
     */
    public static float valleyMistFromAltitude(float y) {
        float t = (y - 50.0f) / 80.0f; // y=50 -> 0, y=130 -> 1
        t = Math.clamp(0.0f, 1.0f, t);
        return 1.0f - t;
    }
}