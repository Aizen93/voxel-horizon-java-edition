package org.aouessar.core.api;

public interface WorldSampler {

    /**
     * world Y
     * @param wx
     * @param wz
     * @return
     */
    int heightAt(int wx, int wz);

    /**
     * biome id
     * @param wx
     * @param wz
     * @return
     */
    int biomeIdAt(int wx, int wz);

    /**
     * top material id
     * @param wx
     * @param wz
     * @return
     */
    short surfaceBlockAt(int wx, int wz);

}