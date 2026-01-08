package org.aouessar.core.world.layers;

import java.util.List;

public final class StructureMap {
    public record Placement(int wx, int wy, int wz, short structureId) {}

    private final LayerRect rect;
    private final List<Placement> placements;

    public StructureMap(LayerRect rect, List<Placement> placements) {
        this.rect = rect;
        this.placements = List.copyOf(placements);
    }

    public LayerRect rect() {
        return rect;
    }

    public List<Placement> placements() {
        return placements;
    }
}