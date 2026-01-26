# Interfaces

This document details all core interfaces in the Voxel Horizon engine and their contracts.

## Table of Contents

1. [Streaming Interfaces](#streaming-interfaces)
2. [Generation Pipeline Interfaces](#generation-pipeline-interfaces)
3. [Rendering Interfaces](#rendering-interfaces)
4. [Data Layer Interfaces](#data-layer-interfaces)

---

## Streaming Interfaces

### ChunkProvider

**Location**: `org.aouessar.core.api.ChunkProvider`

The primary interface for chunk streaming.

```java
public interface ChunkProvider {
    /**
     * Get a chunk at the given chunk coordinates.
     * 
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return The chunk (may be a placeholder if not ready)
     */
    Chunk getChunk(int cx, int cz);
}
```

**Contract**:
- ✅ **Always returns a non-null chunk**
- ✅ **May return placeholder** if region not yet generated
- ✅ **Thread-safe**: Can be called from multiple threads
- ✅ **No exceptions**: Never throws for valid coordinates
- ✅ **Infinite coords**: Works with any integer coordinates

**Placeholder Behavior**:
- Returns chunk filled with appropriate blocks:
  - Below sea level: WATER
  - Above sea level: AIR
- Deterministic based on coordinates

### WorldSampler

**Location**: `org.aouessar.core.api.WorldSampler`

Interface for sampling world data without requesting full chunks.

```java
public interface WorldSampler {
    /**
     * Get height at world coordinates.
     * 
     * @param wx World X coordinate (in blocks)
     * @param wz World Z coordinate (in blocks)
     * @return Height in blocks (Y coordinate of terrain surface)
     */
    int heightAt(int wx, int wz);
    
    /**
     * Get biome ID at world coordinates.
     * 
     * @param wx World X coordinate (in blocks)
     * @param wz World Z coordinate (in blocks)
     * @return Biome ID (see Biomes class)
     */
    int biomeIdAt(int wx, int wz);
    
    /**
     * Get surface block at world coordinates.
     * 
     * @param wx World X coordinate (in blocks)
     * @param wz World Z coordinate (in blocks)
     * @return Block ID of top surface block
     */
    short surfaceBlockAt(int wx, int wz);
}
```

**Contract**:
- ✅ **Fast lookups**: No chunk composition required
- ✅ **Safe placeholders**: Returns SEA_LEVEL, default biome, AIR if not ready
- ✅ **Thread-safe**: Concurrent access allowed
- ✅ **Used by**: LOD systems, biome search, visualization tools

### WorldAccess

**Location**: `org.aouessar.core.api.WorldAccess`

Composite record bundling both streaming interfaces.

```java
public record WorldAccess(
    ChunkProvider chunkProvider,
    WorldSampler worldSampler
) {}
```

**Usage**:
```java
WorldAccess world = new WorldAccess(streamingService, streamingService);
Chunk chunk = world.chunkProvider().getChunk(cx, cz);
int height = world.worldSampler().heightAt(wx, wz);
```

---

## Generation Pipeline Interfaces

### WorldGenerator

**Location**: `org.aouessar.core.gen.WorldGenerator`

Generates base terrain heightmap.

```java
public interface WorldGenerator {
    /**
     * Generate heightmap for a region.
     * 
     * @param seed World seed
     * @param rect Spatial bounds (region size)
     * @return Heightmap layer
     */
    Heightmap generateHeightmap(long seed, LayerRect rect);
}
```

**Responsibilities**:
- Base terrain shape (continents, oceans)
- Large-scale elevation (mountains, plains)
- Island generation
- Coastline fragmentation
- **NO biomes, NO surface blocks, NO structures**

**Current Implementation**: `SimpleWorldGenerator`
- Uses OpenSimplex2 noise (FastNoiseLite)
- Multi-octave continentalness (scale, erosion, peaks/valleys)
- Deep ocean / ocean / coastline / island system
- Smooth mountain ranges with realistic erosion

### BiomeGenerator

**Location**: `org.aouessar.core.gen.BiomeGenerator`

Assigns biome IDs to each column.

```java
public interface BiomeGenerator {
    /**
     * Generate biome map from heightmap.
     * 
     * @param seed World seed
     * @param heightmap Previously generated heightmap
     * @return BiomeMap layer
     */
    BiomeMap generateBiomes(long seed, Heightmap heightmap);
}
```

**Responsibilities**:
- Assign biome per column (2D)
- Based on: height, temperature noise, moisture noise
- **NO block placement, NO structures**

**Current Implementation**: `DefaultBiomeGenerator`
- Supports biomes: PLAINS, DESERT, SNOW, FOREST, SAVANNA, SWAMP, JUNGLE, OCEAN, DEEP_OCEAN
- Temperature-based (noise-driven)
- Moisture-based for sub-biomes
- Ocean depth variants

### WorldCarver

**Location**: `org.aouessar.core.gen.WorldCarver`

Generates carving mask (caves, ravines, future rivers).

```java
public interface WorldCarver {
    /**
     * Generate carving mask.
     * 
     * @param seed World seed
     * @param heightmap Previously generated heightmap
     * @return CarveMask layer (byte array: 0 = solid, 1 = carved)
     */
    CarveMask generateCarveMask(long seed, Heightmap heightmap);
}
```

**Responsibilities**:
- Mark columns to be carved (caves, ravines)
- Future: 3D carve volumes
- **NO block placement**

**Current Implementation**: `DefaultWorldCarver`
- Simple 2D river-like carving (noise threshold)
- Future: True 3D cave systems

### SurfaceDecorator

**Location**: `org.aouessar.core.gen.SurfaceDecorator`

Generates surface block rules (top block, filler, depth).

```java
public interface SurfaceDecorator {
    /**
     * Generate surface rules.
     * 
     * @param heightmap Previously generated heightmap
     * @param biomeMap Previously generated biome map
     * @return SurfaceRules layer (top/filler/depth)
     */
    SurfaceRules generateSurfaceRules(Heightmap heightmap, BiomeMap biomeMap);
}
```

**Responsibilities**:
- Top block per column (grass, sand, snow, etc.)
- Filler block per column (dirt, sandstone, etc.)
- Filler depth (how many blocks below surface)
- Biome-dependent logic
- **NO structure placement**

**Current Implementation**: `BiomeDecorator`
- Biome-specific surface blocks
- Configurable via JSON (world_content_v1.json)
- Supports: GRASS, SAND, SNOW, DIRT, PODZOL, etc.

### WaterGenerator

**Location**: `org.aouessar.core.gen.WaterGenerator`

Generates water layer (oceans, rivers, lakes).

```java
public interface WaterGenerator {
    /**
     * Generate water layer.
     * 
     * @param seed World seed
     * @param heightmap Previously generated heightmap
     * @param carveMask Previously generated carve mask
     * @return WaterLayer (water level per column, or NO_WATER)
     */
    WaterLayer generateWaterLayer(long seed, Heightmap heightmap, CarveMask carveMask);
}
```

**Responsibilities**:
- Ocean water placement
- River water levels (future)
- Lake generation (future)
- Runs **AFTER** carving

**Current Implementation**: `DefaultWaterGenerator`
- Fills below sea level (62) with water
- Future: Variable river levels, swamp water

### StructureBuilder

**Location**: `org.aouessar.core.gen.StructureBuilder`

Places structures (trees, vegetation, etc.).

```java
public interface StructureBuilder {
    /**
     * Place structures in region.
     * 
     * @param seed World seed
     * @param heightmap Previously generated heightmap
     * @param biomeMap Previously generated biome map
     * @return StructureMap (list of placements)
     */
    StructureMap placeStructures(long seed, Heightmap heightmap, BiomeMap biomeMap);
}
```

**Responsibilities**:
- Tree placement (oak, spruce, jungle, acacia, cactus, etc.)
- Vegetation (grass, flowers, bushes, berry bushes)
- **Produces placement data only, NO block placement**

**Current Implementation**: `DefaultStructureBuilder`
- Configurable via JSON (world_content_v1.json)
- Supports:
  - **Trees**: OAK, SPRUCE, JUNGLE, MEGA_JUNGLE, ACACIA, CACTUS, SNOW_TREE
  - **Vegetation**: TALL_GRASS, FLOWER_*, BUSH, BERRY_BUSH, etc.
- Features:
  - Density control
  - Distribution patterns (SCATTERED, CLUSTERED, UNIFORM, PATCHY)
  - Clustering (group sizes)
  - Clearings (tree-free zones)

### RegionPipeline

**Location**: `org.aouessar.core.gen.RegionPipeline`

Orchestrates all generation phases.

```java
public interface RegionPipeline {
    /**
     * Generate all layers for a region.
     * 
     * @param seed World seed
     * @param rect Spatial bounds
     * @return RegionLayers bundle
     */
    RegionLayers generateRegionLayers(long seed, LayerRect rect);
}
```

**Contract**:
- ✅ **Pure function**: No side effects
- ✅ **Deterministic**: Same seed + rect = same output
- ✅ **Linear pipeline**: Each phase builds on previous
- ✅ **Immutable output**: RegionLayers record is immutable

**Current Implementation**: `DefaultRegionPipeline`
- Runs 6 phases in order:
  1. Heightmap
  2. BiomeMap
  3. CarveMask
  4. SurfaceRules
  5. WaterLayer
  6. StructureMap

---

## Rendering Interfaces

### Renderer

**Location**: (Conceptual interface, implemented by `LwjglRendererV1`)

```java
public interface Renderer {
    void render(Camera camera);
}
```

**Contract**:
- Renders the visible world from camera perspective
- Treats `ChunkProvider` as black box
- Never modifies world data

### ChunkMesher

**Location**: (Conceptual interface, implemented by `GreedyChunkMesher`)

```java
public interface ChunkMesher {
    MeshData buildMesh(Chunk chunk, ChunkProvider provider);
}
```

**Contract**:
- Converts chunk voxels → renderable geometry
- May access neighbor chunks for edge visibility
- Returns vertex/index arrays (MeshData)

---

## Data Layer Interfaces

### ArrayLayer2D

**Location**: `org.aouessar.core.world.layers.ArrayLayer2D`

Abstract base for all 2D array-backed layers.

```java
public abstract class ArrayLayer2D {
    protected final LayerRect rect;
    
    protected ArrayLayer2D(LayerRect rect);
    
    public boolean contains(int wx, int wz);
    protected int index(int wx, int wz);
}
```

**Subclasses**:
- `Heightmap`
- `BiomeMap`
- `CarveMask`
- `SurfaceRules`
- `WaterLayer`

**Contract**:
- ✅ Internally owns `LayerRect` for bounds checking
- ✅ `contains()` checks if world coords are in bounds
- ✅ `index()` maps world coords → array index
- ✅ Fails fast on out-of-bounds access during development

---

## Interface Summary Table

| Interface | Purpose | Returns | Pure Function | Thread-Safe |
|-----------|---------|---------|---------------|-------------|
| **ChunkProvider** | Chunk streaming | Chunk | ❌ (caching) | ✅ |
| **WorldSampler** | Quick world queries | height/biome/block | ❌ (caching) | ✅ |
| **WorldGenerator** | Base terrain | Heightmap | ✅ | ✅ |
| **BiomeGenerator** | Biome assignment | BiomeMap | ✅ | ✅ |
| **WorldCarver** | Carving mask | CarveMask | ✅ | ✅ |
| **SurfaceDecorator** | Surface blocks | SurfaceRules | ✅ | ✅ |
| **WaterGenerator** | Water placement | WaterLayer | ✅ | ✅ |
| **StructureBuilder** | Structure placement | StructureMap | ✅ | ✅ |
| **RegionPipeline** | Orchestration | RegionLayers | ✅ | ✅ |
| **Renderer** | Display world | void | ❌ (side effects) | ❌ |

**Legend**:
- **Pure Function**: Same inputs → same outputs, no side effects
- **Thread-Safe**: Can be called concurrently from multiple threads

---

## Usage Examples

### Streaming Usage

```java
// Get streaming service
RegionStreamingService streaming = new RegionStreamingService(...);

// Request chunks
Chunk chunk = streaming.getChunk(0, 0);
Chunk neighbor = streaming.getChunk(1, 0);

// Sample world data
int height = streaming.heightAt(100, 200);
int biome = streaming.biomeIdAt(100, 200);
short surfaceBlock = streaming.surfaceBlockAt(100, 200);
```

### Generation Usage

```java
// Create pipeline
RegionPipeline pipeline = new DefaultRegionPipeline(
    worldGen, biomeGen, carver, surface, water, structures
);

// Generate region layers
LayerRect rect = new LayerRect(0, 0, 256, 256);
RegionLayers layers = pipeline.generateRegionLayers(12345L, rect);

// Access individual layers
Heightmap heightmap = layers.heightmap();
BiomeMap biomeMap = layers.biomeMap();
StructureMap structures = layers.structureMap();
```

### Rendering Usage

```java
// Create renderer with streaming provider
ChunkProvider provider = new RegionStreamingService(...);
Renderer renderer = new LwjglRendererV1(provider, config);

// Render loop
while (!windowShouldClose()) {
    renderer.render(camera);
}
```

---

**Next**: [Implementation →](Implementation.md)
