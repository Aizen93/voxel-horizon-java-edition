package org.aouessar.core.api;

public interface WorldSampler {
    int heightAt(int wx, int wz);       // world Y
    int biomeIdAt(int wx, int wz);      // biome id
    short surfaceBlockAt(int wx, int wz); // top material id
}