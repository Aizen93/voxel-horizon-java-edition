# Minecraft-Like Voxel Engine — Clean Architecture Bootstrap Prompt (Updated)

This document is the **authoritative project instructions prompt**.
It **keeps the exact structure and intent of the original instructions**, but updates the content to reflect the **current stabilized state** of the project and the **next development phases**.

Use this prompt as the baseline for all future work.

---

## Project Goal
Build a **Java 21** voxel engine using **LWJGL (OpenGL)** with a **Minecraft-like world** and a future **Distant Horizons–style far field**, designed with a **correct, production-grade architecture from day one**.

The world must be:
- **Infinite in coordinates**
- **Streamed**
- **Deterministic**
- **Region-based**

The engine is a **data-pipeline + streaming system**, not a fixed world window.

---

## Core Principles (Non-Negotiable)
- Java 21
- LWJGL renderer (OpenGL)
- Minecraft-style texture atlas
- Infinite world coordinates
- Region-based streaming (paged world)
- Deterministic generation (**seed + coordinates only**)
- Far-field LOD friendly
- UE5-friendly (world data must be serializable and engine-agnostic)
- **NO OpenGL / LWJGL code outside the renderer module**
- **Renderer must never know about regions, rects, caches, or generation windows**
- **Core must never throw out-of-bounds during normal gameplay**
- Missing data must be handled via placeholders or futures — never crashes

---

## High-Level Module Layout
app/
└─ Main entry point
└─ Wires core ↔ renderer

core/
└─ World generation
└─ Streaming & caching
└─ Chunk composition
└─ Zero rendering code

renderer/
└─ LWJGL implementation
└─ Near-field rendering (stable)
└─ Far-field rendering (future)


Renderer treats core as a **black box**.

---

## World Model (Critical Concept)
The world is **infinite**, but **data is finite per Region**.

- World is divided into **Regions**
- Each Region contains **N×N chunks**
- Regions are the **only streaming + caching unit**
- Regions own all derived layers
- Renderer never sees region boundaries

Renderer API remains:

```java
Chunk chunk = chunkProvider.getChunk(cx, cz);
```

Core responsibilities:
- Resolve region ownership
- Generate/load region layers
- Compose chunks deterministically
- Always answer safely

---

## Required streaming architecture
Region coordinate
```java
record RegionPos(int rx, int rz) {}
```

Region size :
- Constant: REGION_SIZE_CHUNKS
- Region size in blocks = REGION_SIZE_CHUNKS * CHUNK_SIZE

A region is the streaming and caching unit.

---

## Internal spatial bounds (important clarification)
Derived layers (heightmap, biome map, carve mask, surface rules…) are backed by finite arrays.

Each such layer must internally know:

- Its world-space origin
- Its size
- How to safely map (wx, wz) → array index

For this purpose, the core uses an internal helper:

```java
final class LayerRect {
    int minX, minZ;
    int sizeX, sizeZ;
}
```
Rules for LayerRect:
- It is NOT a world concept
- It is NOT visible to renderer or app
- It is used only internally by array-backed layers
- It exists to enforce correctness and fail fast during development
- Regions own layers; layers internally own a LayerRect.

---

## Generation pipeline (per region)
Each generation phase produces derived data, never blocks.

```java
Heightmap heightmap = terrainGenerator.generateHeightmap(seed, rect);
BiomeMap biomeMap = biomeGenerator.generateBiomes(seed, heightmap);
CarveMask carveMask = carver.generateCarveMask(seed, heightmap);
SurfaceRules surfaceRules = surfaceDecorator.generateSurfaceRules(heightmap, biomeMap);
WaterLayer waterLayer = waterGenerator.generateWaterLayer(seed, heightmap, carveMask);
StructureMap structures = structureBuilder.placeStructures(seed, heightmap, biomeMap);
```

These are grouped into:
```java
record RegionLayers(
    Heightmap heightmap,
    BiomeMap biomeMap,
    CarveMask carveMask,
    SurfaceRules surfaceRules,
    WaterLayer waterLayer,
    StructureMap structureMap
) {}
```

---

## Required interfaces (core)
1. ### Terrain
    ```java
    interface WorldGenerator {
        Heightmap generateHeightmap(long seed, LayerRect rect);
    }
    ```

   Implementation:
    - SimpleWorldGenerator
        - Uses FastNoiseLite
        - Responsible only for : base terrain shape, continents, oceans, Large-scale elevation, Mountains / hills ...etc
        - No biomes, no surface blocks, no structures

2. ### Biomes
     ```java
    interface BiomeGenerator {
    BiomeMap generateBiomes(long seed, Heightmap heightmap);
    }
    ```

   Responsibilities:
    - Decide biome per column
    - Derived from height + noise
    - No block logic

3. ### Carvers
    ```java
    interface WorldCarver {
        CarveMask generateCarveMask(long seed, Heightmap heightmap);
    }
    ```

   Implementation:
    - DefaultWorldCarver
        - Responsibilities: Caves, Ravines, Underground tunnels, Large voids
        - No block placement

4. ### Surface
    ```java
    interface SurfaceDecorator {
        SurfaceRules generateSurfaceRules(Heightmap heightmap, BiomeMap biomeMap);
    }
    ```

   Implementation:
    - BiomeDecorator
        - Responsibilities : Top block, Filler block, Filler depth
        - Biome-dependent logic
        - No structure placement

5. ### Structures
    ```java
    interface StructureBuilder {
        StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap);
    }
    ```

   Implementation:
    - DefaultStructureBuilder
        - Responsibilities : Oak trees, jungle trees, savanna trees, swamp trees, cactus, bushes, flowers ..etc
        - Produces structure placement data only
        - No block placement

6. ### Water
    ```java
    interface WaterGenerator {
        WaterLayer generateWaterLayer(long seed, Heightmap heightmap, CarveMask carveMask);
    }
    ```

   Implementation:
    - DefaultWaterGenerator
        - Responsibilities: Ocean/sea water, river water levels
        - Produces water level per column (or NO_WATER)
        - Runs AFTER carving
        - Enables future: lakes, swamps, variable river levels

7. ### Chunk composition
   the only place blocks exist
    ```java
    final class ChunkBuilder {
        Chunk buildChunk(int cx, int cz);
        short blockAt(int wx, int wy, int wz);
    }
    ```
    - Responsibilities :
        - Combine: Heightmap, CarveMask, SurfaceRules, WaterLayer, StructureMap
        - No generation logic
        - No caching
        - Deterministic

8. ## Core streaming service (mandatory)
    ```java
    interface ChunkProvider {
        Chunk getChunk(int cx, int cz);
    }
   ```
   Implementation responsibilities:
    - Manage region cache
    - Generate region layers on demand
    - Guarantee:
        - Renderer can request any chunk
        - Infinite world behavior
        - No out-of-rect exceptions
        - Region boundaries never leak

9. ### Rendering
    ```java
    interface Renderer {
        void render(Camera camera);
    }
    ```

   Implementation:
    - LwjglRenderer
        - Responsibilities :
            - Near-field chunk meshes
            - Far-field LOD (heightmap-based meshes)
            - Texture atlas sampling
        - Treat core as a black box

---

## Renderer Status (Near-Field)
- Greedy meshing with texture atlas
- Frustum culling
- Chunk streaming from core
- Water rendering (stable)
- Day/night cycle
- Fog
- Renderer remains fully decoupled from core logic.

---

## Teleportation & Tooling (DONE)
- F9: Teleport to coordinates
- F10: Search and Teleport to nearest selected biome
- Swing dialogs (EDT-safe)
- Background biome search with region warm-up
- Uses WorldSampler safely (no placeholder misuse)

---

## Provided Assets (Mandatory Use)
- ```FastNoiseLite.java```
- ```atlas.png```
- ```atlas.json```
- ```project_status.md```

Assets must not dictate architecture.

---

## Engineering Rules (Strict)
- No OpenGL outside renderer
- No global world window
- No silent bounds errors
- No abandoned helpers or half-finished abstractions
- If a method/class is changed, provide the entire updated implementation
- Correctness > stability > performance > scale

---

## What Remains To Do
1) Performance & Cleanup (HIGH PRIORITY)
   - Cache eviction (likely Caffeine) (done)
   - Better invalidation strategies
   - Threading cleanup
   - Placeholder semantics improvements
   - Remove dead code and helper sprawl

2) Caves (MAJOR)
   - True 3D cave systems
   - Region-paged carve volumes
   - Deterministic

3) Rivers (Upgrade)
   - Network coherence
   - Ocean termination
   - Biome-aware behavior

4) Structure Variety
   - More vegetation variants
   - Density rules and clustering
   - Rare large features

5) Lighting & Shadows
   - Minecraft-style skylight + sunlight
   - Renderer-only implementation
   - Later options (optional):
       - SSAO / soft shadows / cascaded shadow maps

6) Far Field (Distant Horizons)
   - Heightmap-based LOD tiles
   - Seamless blending with near field
   - Clean renderer ↔ core contract

7) Additional (Verify / Optional)
   - Occlusion culling
   - Post-processing
   - Physics/collision
   - Save/load (region serialization)

---

## Current Development Focus Order
1) Continuous cleanup & robustness
2) Caves + improved rivers
3) Structure variety
4) Lighting & shadows
5) Far-field tiles

This prompt replaces all previous instructions and must be followed verbatim for future work.