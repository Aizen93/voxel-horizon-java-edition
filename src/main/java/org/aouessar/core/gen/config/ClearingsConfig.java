package org.aouessar.core.gen.config;

/**
 * Clearings configuration for tree placement.
 */
public record ClearingsConfig(
    boolean enabled,
    int averageRadius
) {
    public static final ClearingsConfig DISABLED = new ClearingsConfig(false, 0);

    public static ClearingsConfig of(boolean enabled, int averageRadius) {
        if (!enabled) return DISABLED;
        return new ClearingsConfig(true, averageRadius);
    }
}
