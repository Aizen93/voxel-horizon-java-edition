package org.aouessar.core.api;

import java.util.Optional;

public interface BiomeLocator {
    Optional<BiomeHit> findNearestBiome(int startWx, int startWz, int targetBiomeId, int maxRadiusBlocks);

    record BiomeHit(int wx, int wz, int distBlocks) {}
}