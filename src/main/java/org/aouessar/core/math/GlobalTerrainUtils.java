package org.aouessar.core.math;

public class GlobalTerrainUtils {
    private GlobalTerrainUtils() {}

    /**
     * Stable 64->32 seed mixing. Keeps worlds deterministic and avoids correlated noise instances.
     */
    public static int mixSeed(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * salt;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 31));
        return (int) z;
    }

    public static float clamp(float v, float lo, float hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }

    public static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = (x - edge0) / (edge1 - edge0);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t * t * (3f - 2f * t);
    }

    public static long hash(long seed, int cx, int cz, int salt) {
        long v = seed;
        v ^= cx * 0x632BE59BD9B4E019L;
        v ^= cz * 0x9E3779B97F4A7C15L;
        v ^= salt * 0x85157AF5L;
        v ^= (v >>> 27);
        v *= 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }

    public static float hash01(long seed, int x, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return ((h >>> 40) & 0xFFFFFFL) / (float) (1 << 24);
    }

    // Small deterministic hash -> [0..255]
    public static int hash8(int x, int z) {
        int h = x * 0x1f1f1f1f ^ z * 0x7f4a7c15;
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        return h & 0xFF;
    }

    public static int count(short v, short s0, short s1, short s2, short s3, short s4) {
        int c = 0;
        if (s0 == v) c++;
        if (s1 == v) c++;
        if (s2 == v) c++;
        if (s3 == v) c++;
        if (s4 == v) c++;
        return c;
    }

    // Cheap exp(-x) approximation (x >= 0)
    public static float fastExpNeg(float x) {
        if (x > 20f) return 0f;
        float x2 = x * x;
        return 1.0f / (1.0f + x + 0.48f * x2);
    }

    public static float to01(float n) {
        return (n + 1.0f) * 0.5f;
    }
}
