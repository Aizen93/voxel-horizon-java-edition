package org.aouessar.core.world.layers;

import org.aouessar.core.world.WorldGrid;
import org.aouessar.shared.EngineConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StructureMap {
    public record Placement(int wx, int wy, int wz, short structureId) {}

    private final LayerRect rect;
    private final List<Placement> placements;

    // lazily-built index: chunkKey -> placements in that chunk
    private volatile Map<Long, List<Placement>> byChunk;

    public StructureMap(LayerRect rect, List<Placement> placements) {
        this.rect = rect;
        this.placements = List.copyOf(placements);
    }

    public LayerRect rect() { return rect; }

    /** Existing API stays (unchanged). */
    public List<Placement> placements() { return placements; }

    /** New: placements already filtered for this chunk. */
    public List<Placement> placementsInChunk(int cx, int cz) {
        Map<Long, List<Placement>> idx = byChunk;
        if (idx == null) {
            synchronized (this) {
                idx = byChunk;
                if (idx == null) {
                    idx = buildIndex();
                    byChunk = idx;
                }
            }
        }
        return idx.getOrDefault(packChunk(cx, cz), List.of());
    }

    private Map<Long, List<Placement>> buildIndex() {
        HashMap<Long, List<Placement>> m = new HashMap<>();
        for (Placement p : placements) {
            int pcx = WorldGrid.worldBlockToChunkX(p.wx());
            int pcz = WorldGrid.worldBlockToChunkZ(p.wz());

            long key = packChunk(pcx, pcz);
            m.computeIfAbsent(key, __ -> new ArrayList<>()).add(p);
        }
        // freeze lists
        for (Map.Entry<Long, List<Placement>> e : m.entrySet()) {
            e.setValue(List.copyOf(e.getValue()));
        }
        return Map.copyOf(m);
    }

    private static long packChunk(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }
}