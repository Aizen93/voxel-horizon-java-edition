# Technical Reference

This document provides technical specifications, constants, data structures, and coordinate systems used in Voxel Horizon.

## Table of Contents

1. [Constants & Configuration](#constants--configuration)
2. [Data Structures](#data-structures)
3. [Coordinate Systems](#coordinate-systems)
4. [Block & Biome IDs](#block--biome-ids)
5. [File Formats](#file-formats)
6. [Performance Metrics](#performance-metrics)

---

## Constants & Configuration

### EngineConfig

**Location**: `org.aouessar.shared.EngineConfig`

```java
public class EngineConfig {
    // World dimensions
    public static final int CHUNK_SIZE = 16;              // 16×16 horizontal
    public static final int REGION_SIZE_CHUNKS = 16;      // 16×16 chunks per region
    public static final int REGION_SIZE_BLOCKS = 256;     // 256×256 blocks per region
    
    // Vertical bounds (Minecraft 1.18+ style)
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    public static final int WORLD_HEIGHT = 384;           // Total Y range
    
    // Sea level
    public static final int SEA_LEVEL = 62;
    
    // Water rendering
    public static final float WATER_TOP_DELTA = 0.2f;     // Surface offset (0.85-0.92)
    
    // Threading
    public static final int CPU_WORKERS = 
        Runtime.getRuntime().availableProcessors() - 1;   // All cores - 1
}
```

### RendererConfig

**Location**: `org.aouessar.renderer.RendererConfig`

```java
public class RendererConfig {
    // Window
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final String WINDOW_TITLE = "Voxel Horizon";
    
    // Rendering
    public static final int VIEW_DISTANCE_CHUNKS = 16;    // Render radius
    public static final float FOV = 70.0f;                // Field of view (degrees)
    public static final float NEAR_PLANE = 0.1f;          // Near clipping
    public static final float FAR_PLANE = 1000.0f;        // Far clipping (with fog)
    
    // Camera
    public static final float CAMERA_SPEED = 20.0f;       // Blocks per second
    public static final float MOUSE_SENSITIVITY = 0.1f;   // Look sensitivity
    
    // Fog
    public static final float FOG_START = 500.0f;         // Fog start distance
    public static final float FOG_END = 800.0f;           // Fog full density
    
    // Paths
    public static final String SHADER_PATH = "/shaders/";
    public static final String ATLAS_PATH = "/atlas.png";
    public static final String ATLAS_JSON_PATH = "/atlas.json";
}
```

---

## Data Structures

### Chunk

**Size**: 16 × 16 × 384 blocks = 98,304 voxels  
**Memory**: ~196 KB (2 bytes per block)

```java
public final class Chunk {
    private final int cx, cz;          // Chunk coordinates
    private final short[] blocks;      // Flattened array
    
    // Array layout: ((z * CHUNK_SIZE) + x) * WORLD_HEIGHT + localY
    private int index(int localX, int worldY, int localZ) {
        return ((localZ * CHUNK_SIZE) + localX) * WORLD_HEIGHT + (worldY - MIN_Y);
    }
    
    public short getBlock(int localX, int worldY, int localZ);
    public void setBlock(int localX, int worldY, int localZ, short blockId);
}
```

### Region

**Size**: 256 × 256 blocks (16 × 16 chunks)  
**Memory**: ~500 KB (all layers combined)

```java
public record Region(
    RegionPos pos,           // (rx, rz)
    LayerRect rect,          // Spatial bounds (min, size)
    RegionLayers layers      // All derived data
) {}
```

### RegionLayers

Bundle of all generation outputs for a region.

```java
public record RegionLayers(
    Heightmap heightmap,         // int[256×256] = 256 KB
    BiomeMap biomeMap,           // short[256×256] = 128 KB
    CarveMask carveMask,         // byte[256×256] = 64 KB
    SurfaceRules surfaceRules,   // 3 arrays = ~320 KB
    WaterLayer waterLayer,       // int[256×256] = 256 KB
    StructureMap structureMap    // Variable (list of placements)
) {}
```

### LayerRect

Internal spatial bounds for array-backed layers.

```java
public final class LayerRect {
    private final int minX, minZ;    // World origin (in blocks)
    private final int sizeX, sizeZ;  // Dimensions (in blocks)
    
    // Check if world coords are within bounds
    public boolean contains(int wx, int wz) {
        return wx >= minX && wx < (minX + sizeX)
            && wz >= minZ && wz < (minZ + sizeZ);
    }
    
    // Convert world coords to array index
    public int index(int wx, int wz) {
        if (!contains(wx, wz)) throw new IndexOutOfBoundsException();
        return (wz - minZ) * sizeX + (wx - minX);
    }
}
```

### MeshData

Vertex and index arrays for rendering.

```java
public record MeshData(
    float[] vertices,      // [x, y, z, tileMinU, tileMinV, uLocal, vLocal] × N
    int[] indices,         // Triangle indices
    int vertexCount,
    int indexCount
) {}
```

**Vertex Stride**: 7 floats = 28 bytes per vertex

---

## Coordinate Systems

### World Coordinates (Blocks)

**Range**: `Integer.MIN_VALUE` to `Integer.MAX_VALUE` (±2.1 billion)

```
X: East (+) / West (-)
Y: Up (+) / Down (-)     (MIN_Y = -64, MAX_Y = 319)
Z: South (+) / North (-)
```

### Chunk Coordinates

**Chunk**: 16×16 horizontal area, full height (384 blocks)

```
chunkX = floorDiv(worldX, 16)
chunkZ = floorDiv(worldZ, 16)

localX = floorMod(worldX, 16)   // 0-15
localZ = floorMod(worldZ, 16)   // 0-15
```

**Example**:
- World (34, 100, -17)
- Chunk (2, -2) at local (2, 15) at Y=100

### Region Coordinates

**Region**: 16×16 chunks = 256×256 blocks

```
regionX = floorDiv(chunkX, 16)
regionZ = floorDiv(chunkZ, 16)

chunkInRegionX = floorMod(chunkX, 16)   // 0-15
chunkInRegionZ = floorMod(chunkZ, 16)   // 0-15
```

**Region Origin** (in world blocks):
```
regionOriginX = regionX * 256
regionOriginZ = regionZ * 256
```

### Transforms (WorldGrid)

```java
// Blocks → Chunks
public static int blockToChunk(int worldCoord) {
    return Math.floorDiv(worldCoord, CHUNK_SIZE);
}

public static int blockToLocal(int worldCoord) {
    return Math.floorMod(worldCoord, CHUNK_SIZE);
}

// Chunks → Regions
public static int chunkToRegion(int chunkCoord) {
    return Math.floorDiv(chunkCoord, REGION_SIZE_CHUNKS);
}

public static int chunkToLocalInRegion(int chunkCoord) {
    return Math.floorMod(chunkCoord, REGION_SIZE_CHUNKS);
}

// Regions → World Blocks
public static int regionToBlock(int regionCoord) {
    return regionCoord * REGION_SIZE_BLOCKS;
}
```

**Why `floorDiv`/`floorMod`?**
- Correct handling of negative coordinates
- Standard division fails for negatives (e.g., -1 / 16 = 0, should be -1)

---

## Block & Biome IDs

### Block IDs

**Location**: `org.aouessar.core.world.Blocks`

```java
public static final short AIR = 0;
public static final short GRASS = 1;
public static final short DIRT = 2;
public static final short STONE = 3;
public static final short WATER = 4;
public static final short DESERT_SAND = 5;
public static final short SNOW = 6;
public static final short GLASS = 7;
public static final short LEAVES = 8;
public static final short BUSH = 9;
public static final short TALL_GRASS = 10;
public static final short FLOWER_RED = 11;
public static final short FLOWER_YELLOW = 12;
public static final short FLOWER_BLUE = 13;
public static final short FLOWER_WHITE = 14;
public static final short FLOWER_CORNFLOWER = 15;
public static final short FLOWER_OXEYE_DAISY = 16;
public static final short FLOWER_HOUSTONIA = 17;
public static final short GRAVEL = 18;
public static final short CLAY = 19;
public static final short BERRY_BUSH = 20;
public static final short ICE = 21;
public static final short SNOW_GRASS = 22;
public static final short DRY_GRASS = 23;
public static final short PODZOL_DIRT = 24;
public static final short SAND = 25;
public static final short DESERT_SAND_STONE = 26;
public static final short DEEPSLATE = 27;
public static final short BEDROCK = 28;
```

### Biome IDs

**Location**: `org.aouessar.core.world.Biomes`

```java
public static final int DEEP_OCEAN = 0;
public static final int OCEAN = 1;
public static final int PLAINS = 2;
public static final int DESERT = 3;
public static final int SNOW = 4;
public static final int FOREST = 5;
public static final int SAVANNA = 6;
public static final int SWAMP = 7;
public static final int JUNGLE = 8;
```

### Render Layers

**Location**: `org.aouessar.core.world.RenderLayer`

```java
public enum RenderLayer {
    OPAQUE,       // Solid blocks (stone, dirt, grass, etc.)
    CUTOUT,       // Alpha-tested (leaves, flowers, grass)
    TRANSLUCENT   // Blended (water, glass)
}
```

**Assignment** (BlockRenderMap):
- AIR → null (not rendered)
- STONE, DIRT, GRASS, SAND, SNOW, etc. → OPAQUE
- LEAVES, FLOWERS, BUSH, TALL_GRASS → CUTOUT
- WATER, GLASS → TRANSLUCENT

---

## File Formats

### atlas.json

Texture atlas metadata.

```json
{
  "tiles": {
    "grass_top": { "x": 0, "y": 0 },
    "grass_side": { "x": 1, "y": 0 },
    "dirt": { "x": 2, "y": 0 },
    "stone": { "x": 3, "y": 0 }
  }
}
```

**Tile Size**: 16×16 pixels  
**Atlas Size**: 256×256 pixels (16×16 tiles)

**UV Calculation**:
```java
float tileSize = 1.0f / 16.0f;  // 0.0625
float minU = tileX * tileSize;
float minV = tileY * tileSize;
float maxU = minU + tileSize;
float maxV = minV + tileSize;
```

### world_content_v1.json

Biome configuration (see [Configuration Guide](Configuration-Guide.md)).

**Schema**: `world_content.schema.json`

---

## Performance Metrics

### Memory Usage

**Per Chunk** (full):
- Voxel array: 196 KB
- Mesh data: ~10-100 KB (varies by complexity)
- **Total**: ~200-300 KB

**Per Region** (layers only):
- Heightmap: 256 KB
- BiomeMap: 128 KB
- CarveMask: 64 KB
- SurfaceRules: 320 KB
- WaterLayer: 256 KB
- StructureMap: ~10-50 KB
- **Total**: ~500-600 KB

**Caches** (default config):
- Region cache: 512 regions × 500 KB = **~250 MB**
- Chunk cache: 8192 chunks × 200 KB = **~1.6 GB**
- **Total**: ~2 GB RAM usage at max capacity

### CPU Usage

**Generation** (per region, single-threaded):
- Heightmap: ~10-20 ms
- BiomeMap: ~5 ms
- CarveMask: ~5 ms
- SurfaceRules: ~10 ms
- WaterLayer: ~5 ms
- StructureMap: ~20-50 ms (varies by density)
- **Total**: ~60-100 ms per region

**Multi-threaded**: With 7 workers, can generate ~70-100 regions/sec

**Meshing** (per chunk):
- Greedy meshing: ~5-20 ms (varies by complexity)
- Simple meshing: ~2-5 ms

### GPU Usage

**Vertex Count** (typical chunk):
- Simple meshing: ~20,000-50,000 vertices
- Greedy meshing: ~5,000-15,000 vertices
- **Reduction**: ~70-80% fewer vertices

**Draw Calls** (per frame):
- 3-pass rendering: ~300-1000 chunks × 3 passes = 900-3000 draw calls
- Future batching: Target < 100 draw calls

**VRAM** (typical):
- Mesh buffers: ~500 MB - 2 GB (depends on loaded chunks)
- Textures: ~1 MB (atlas)
- **Total**: ~500 MB - 2 GB

---

## Noise Parameters

### SimpleWorldGenerator

**Continentalness** (land/ocean):
```
Frequency: 1.0 / 4000.0
Noise Type: Cellular (Jitter)
Octaves: 1
```

**Erosion** (terrain character):
```
Frequency: 1.0 / 500.0
Noise Type: OpenSimplex2
Octaves: 4
Fractal Type: FBM
```

**Peaks & Valleys** (fine detail):
```
Frequency: 1.0 / 300.0
Noise Type: OpenSimplex2
Octaves: 6
Fractal Type: FBM
```

### DefaultBiomeGenerator

**Temperature**:
```
Frequency: 1.0 / 800.0
Noise Type: OpenSimplex2
Range: [-1.0, 1.0]
```

**Moisture**:
```
Frequency: 1.0 / 600.0
Noise Type: OpenSimplex2
Range: [-1.0, 1.0]
```

---

## Shader Uniforms

### chunk.vert / chunk.frag

**Vertex Shader Uniforms**:
```glsl
uniform mat4 uModelViewProjection;  // MVP matrix
```

**Fragment Shader Uniforms**:
```glsl
uniform sampler2D uAtlas;           // Texture atlas
uniform vec3 uFogColor;             // Fog color
uniform float uFogStart;            // Fog start distance
uniform float uFogEnd;              // Fog end distance
```

**Vertex Attributes**:
```glsl
layout(location = 0) in vec3 aPosition;    // World position
layout(location = 1) in vec2 aTexCoord;    // Texture UV
```

---

## Debugging Constants

**Placeholder Values** (when data not ready):

```java
// RegionStreamingService placeholders
public static final int PLACEHOLDER_HEIGHT = SEA_LEVEL;
public static final int PLACEHOLDER_BIOME = Biomes.PLAINS;
public static final short PLACEHOLDER_SURFACE = Blocks.AIR;
```

**Testing Seeds**:
- `12345` - Balanced, default
- `42` - Large continents
- `123456789` - Island archipelago
- `0` - Extreme mountains

---

**Next**: [Configuration Guide →](Configuration-Guide.md)
