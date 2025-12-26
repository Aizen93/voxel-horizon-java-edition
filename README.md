# Minecraft-Like Voxel Engine — Clean Architecture Bootstrap Prompt (Streaming-Ready)

I want to start a **new Java 21 project using LWJGL** to build a **Minecraft-like voxel engine with a Distant Horizons look**, but this time with a **correct, production-grade architecture from day one**.

The world must be **infinite in coordinates**, **streamed**, and **deterministic**.
No part of the system should rely on a single global “world window”.

The engine must be designed as a **data-pipeline + streaming system** where:
- All generation is **pure and deterministic**
- All data is generated **per region (paged)** and cached
- The renderer can request **any chunk at any time**
- The core must always be able to answer without throwing bounds exceptions

---

## Core principles (non-negotiable)

- Java 21
- LWJGL renderer (OpenGL)
- Minecraft-style texture atlas
- Infinite world coordinates
- Region-based streaming (like Minecraft / Distant Horizons)
- Deterministic generation (seed + coordinates only)
- Far-field LOD friendly
- UE5-friendly (world data must be serializable, streamable, and engine-agnostic)
- **NO OpenGL / LWJGL code outside the renderer module**
- **Renderer must never know about generation windows, rects, or caching**
- **Core must never throw “out of bounds” during normal gameplay**

---

## High-level module layout

app/
- Main entry point
- Wires core ↔ renderer

core/
- All world generation
- Streaming & caching
- Chunk composition
- Zero rendering code

renderer/
- LWJGL implementation
- Later replaceable by UE5


---

## World model (critical concept)

The world is **infinite**, but **data is finite per region**.

- World is divided into **regions**
- Each region contains **N×N chunks** (e.g. 16×16)
- Each region owns all **derived generation layers**
- Regions are generated **on demand** and cached
- Renderer never sees region boundaries

The renderer only ever does:

```java
Chunk chunk = chunkProvider.getChunk(cx, cz);
```
The core is responsible for:
- Determining which region contains the chunk
- Generating or loading the region layers
- Composing the chunk from those layers

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
StructureMap structures = structureBuilder.placeStructures(seed, heightmap, biomeMap);
```

These are grouped into:
```java
record RegionLayers(
    Heightmap heightmap,
    BiomeMap biomeMap,
    CarveMask carveMask,
    SurfaceRules surfaceRules,
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
        - Responsibilities: Rivers, caves, ravines ...etc
        - Rivers must always reach oceans
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

6. ### Chunk composition
   the only place blocks exist
    ```java
    final class ChunkBuilder {
        Chunk buildChunk(int cx, int cz);
        short blockAt(int wx, int wy, int wz);
    }
    ```
    - Responsibilities :
        - Combine: Heightmap, CarveMask, SurfaceRules, StructureMap
        - No generation logic 
        - No caching 
        - Deterministic

7. ## Core streaming service (mandatory)
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

8. ### Rendering
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

## Important constraints:
- No global “world heightmap”
- No fixed generation window exposed
- No mutation of world data during generation
- All layers must be:
  - Inspectable
  - Cacheable
  - Serializable
- Region boundaries must never leak outside core
- Architecture must support:
  - Distant Horizons
  - Streaming
  - UE5 replacement later

## Provided assets
- **FastNoiseLite.java**
- **atlas.png**
- **atlas.json**
These must be used but must not dictate architecture.

## What I want generated
- Correct package structure
- Core interfaces
- Streaming-ready region-based design
- Region cache strategy
- Focus on correctness first, then performance, then long-term scalability.

I want to start this project the right way so it can scale to a real Minecraft-like engine with distant horizons.


--- 
## packages :

renderer/src/main/java/org/aouessar/renderer/
LwjglRendererV1.java
RendererConfig.java

renderer/src/main/java/org/aouessar/renderer/gl/
GlShaderProgram.java
GlTexture2D.java
GlMesh.java

renderer/src/main/java/org/aouessar/renderer/atlas/
Atlas.java
AtlasLoader.java

renderer/src/main/java/org/aouessar/renderer/camera/
Camera.java
CameraController.java

renderer/src/main/java/org/aouessar/renderer/mesh/
ChunkMesher.java
MeshData.java
VertexWriter.java

renderer/src/main/java/org/aouessar/renderer/world/
ChunkMeshCache.java
ChunkKey.java
BlockRenderMap.java
