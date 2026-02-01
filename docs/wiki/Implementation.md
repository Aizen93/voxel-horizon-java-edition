# Implementation

This document provides detailed implementation guidance for the Voxel Horizon engine components.

## Table of Contents

1. [Terrain Generation](#terrain-generation)
2. [Biome System](#biome-system)
3. [Structure Placement](#structure-placement)
4. [Chunk Composition](#chunk-composition)
5. [Rendering Pipeline](#rendering-pipeline)
6. [Configuration System](#configuration-system)

---

## Terrain Generation

### SimpleWorldGenerator

**Location**: `org.aouessar.core.gen.impl.SimpleWorldGenerator`

Generates base terrain using multi-layered noise.

#### Algorithm

```
1. Continentalness Noise (1:4000 scale)
   ↓ determines: ocean / land
   
2. Erosion Noise (1:500 scale)
   ↓ modulates: smooth / eroded terrain
   
3. Peaks & Valleys Noise (1:300 scale)
   ↓ adds: mountain peaks, deep valleys
   
4. Height Calculation
   ↓ combines all layers
   
5. Special Features
   ↓ islands, deep oceans, coastlines
```

#### Key Parameters

```java
// Continentalness (large-scale land/ocean)
continentalnessNoise.SetFrequency(1.0 / 4000.0);
continentalnessNoise.SetNoiseType(CellularJitter);

// Erosion (terrain character)
erosionNoise.SetFrequency(1.0 / 500.0);
erosionNoise.SetFractalOctaves(4);

// Peaks/Valleys (fine detail)
peaksNoise.SetFrequency(1.0 / 300.0);
peaksNoise.SetFractalOctaves(6);
```

#### Height Formula

```java
double height = seaLevel;

if (continentalness > oceanThreshold) {
    // Land generation
    double baseHeight = seaLevel + (continentalness * maxHeightAboveSea);
    double erosionEffect = erosion * erosionStrength;
    double peakEffect = peaks * peakStrength;
    
    height = baseHeight 
           + erosionEffect 
           + peakEffect 
           - (1.0 - continentalness) * coastalDropoff;
} else if (continentalness > deepOceanThreshold) {
    // Ocean
    height = seaLevel - (oceanThreshold - continentalness) * oceanDepthScale;
} else {
    // Deep ocean
    height = seaLevel - deepOceanDepth;
}

return clamp(height, MIN_Y, MAX_Y);
```

#### Features

✅ **Island Generation**: Noise-based continents surrounded by ocean  
✅ **Coastal Fragmentation**: Smooth land-to-ocean transitions  
✅ **Mountain Ranges**: Multi-octave peaks with realistic erosion  
✅ **Ocean Depth Variants**: Ocean vs. deep ocean  
✅ **Deterministic**: Same seed + coordinates = same terrain

---

## Biome System

### DefaultBiomeGenerator

**Location**: `org.aouessar.core.gen.impl.DefaultBiomeGenerator`

Assigns biomes based on height and climate noise.

#### Algorithm

```
1. Sample temperature noise (1:800 scale)
   ↓ determines: cold / temperate / hot
   
2. Sample moisture noise (1:600 scale)
   ↓ determines: dry / normal / wet
   
3. Sample height from heightmap
   ↓
   
4. Determine biome:
   - Height < deepOceanLevel → DEEP_OCEAN
   - Height < seaLevel → OCEAN
   - temp < -0.3 → SNOW
   - temp > 0.3 & height > mountainLevel → DESERT
   - temp > 0.3 & moisture < -0.2 → SAVANNA
   - moisture > 0.4 → SWAMP
   - temp > 0.5 & moisture > 0.2 → JUNGLE
   - moisture < -0.3 → PLAINS
   - Default → FOREST
```

#### Biome Types

| Biome | ID | Temperature | Moisture | Height | Surface |
|-------|----|----|----------|--------|---------|
| **DEEP_OCEAN** | 0 | Any | Any | < -20 | Water |
| **OCEAN** | 1 | Any | Any | < 62 | Water |
| **PLAINS** | 2 | Mid | Dry | 62-120 | Grass |
| **DESERT** | 3 | Hot | Dry | > 90 | Sand |
| **SNOW** | 4 | Cold | Any | 62-160 | Snow |
| **FOREST** | 5 | Mid | Normal | 62-140 | Dirt |
| **SAVANNA** | 6 | Hot | Dry | 62-110 | Dry grass |
| **SWAMP** | 7 | Mid | Wet | 60-70 | Grass/water |
| **JUNGLE** | 8 | Hot | Wet | 62-100 | Podzol |

#### Configuration

Biomes are configured via `world_content_v1.json`:

```json
{
  "biomes": {
    "PLAINS": {
      "surface_blocks": ["GRASS", "DIRT", "GRAVEL"],
      "structures": {
        "trees": { "types": ["OAK"], "density": 0.15 },
        "vegetation": { "types": ["TALL_GRASS", "FLOWER_RED"], "density": 0.6 }
      }
    }
  }
}
```

---

## Structure Placement

### DefaultStructureBuilder

**Location**: `org.aouessar.core.gen.impl.DefaultStructureBuilder`

Places trees and vegetation based on biome configuration.

#### Algorithm

```
For each chunk in region:
  1. Get biome at chunk center
  2. Load biome config (trees, vegetation)
  3. Determine number of placements (density × chunkArea)
  4. For each placement attempt:
     a. Choose random position in chunk
     b. Get height at position
     c. Check if valid (not water, not too steep)
     d. Select structure type (weighted random)
     e. Add placement to StructureMap
  5. Apply clustering (if enabled)
  6. Apply clearings (if enabled)
```

#### Placement Types

**Trees**:
- OAK (3-5 blocks tall, leaves)
- SPRUCE (4-7 blocks tall, conical)
- JUNGLE (6-10 blocks tall, wide canopy)
- MEGA_JUNGLE (12-20 blocks tall, massive)
- ACACIA (5-7 blocks tall, flat top)
- CACTUS (1-3 blocks tall, no leaves)
- SNOW_TREE (4-6 blocks tall, snow-covered)

**Vegetation**:
- TALL_GRASS (1 block, cutout rendering)
- FLOWER_* (various colors, 1 block)
- BUSH (1 block, crossed billboard)
- BERRY_BUSH (1 block, food source, future)

#### Clustering

When enabled, structures are placed in groups:

```json
"clustering": {
  "enabled": true,
  "cluster_size": [5, 12]  // Min, max structures per cluster
}
```

Algorithm:
1. Choose cluster center (random position)
2. Generate N structures (random in range)
3. Place each within radius of center (Gaussian distribution)

#### Clearings

When enabled, creates tree-free zones:

```json
"clearings": {
  "enabled": true,
  "average_radius": 10
}
```

Algorithm:
1. Generate clearing centers (Poisson disk sampling)
2. For each tree placement, check distance to nearest clearing
3. Suppress trees within clearing radius

---

## Chunk Composition

### ChunkBuilder

**Location**: `org.aouessar.core.world.chunk.ChunkBuilder`

Composes chunks deterministically from region layers.

#### Algorithm

```
For each column (x, z) in chunk:
  1. Get local position in region (regionLocalX, regionLocalZ)
  2. Sample heightmap → terrainHeight
  3. Sample biomeMap → biomeId
  4. Sample carveMask → isCarved
  5. Sample surfaceRules → topBlock, fillerBlock, fillerDepth
  6. Sample waterLayer → waterLevel
  
  For each Y level:
    if Y == MIN_Y:
      block = BEDROCK
    else if Y < terrainHeight - fillerDepth:
      block = STONE (or DEEPSLATE if very deep)
    else if Y < terrainHeight && !isCarved:
      if Y == terrainHeight - 1:
        block = topBlock (e.g., GRASS, SAND)
      else:
        block = fillerBlock (e.g., DIRT, SANDSTONE)
    else if Y <= waterLevel:
      block = WATER
    else:
      block = AIR
    
    chunk.setBlock(localX, Y, localZ, block)
  
  // Apply structures (trees, vegetation)
  for placement in structureMap:
    if placement.isInChunk(cx, cz):
      applyStructure(chunk, placement)
```

#### Special Cases

**Bedrock**: Always at Y = MIN_Y (-64)  
**Deepslate**: Y < -20 (replaces stone)  
**Water**: Fills air below waterLevel  
**Carving**: Removes terrain blocks where carved

#### Structure Application

```java
void applyStructure(Chunk chunk, Placement placement) {
    StructureTemplate template = getTemplate(placement.type);
    
    for (Block blockDef : template.blocks) {
        int wx = placement.x + blockDef.offsetX;
        int wy = placement.y + blockDef.offsetY;
        int wz = placement.z + blockDef.offsetZ;
        
        if (chunk.containsWorldPos(wx, wy, wz)) {
            chunk.setBlock(toLocalX(wx), wy, toLocalZ(wz), blockDef.id);
        }
    }
}
```

---

## Rendering Pipeline

### GreedyChunkMesher

**Location**: `org.aouessar.renderer.mesh.GreedyChunkMesher`

Optimized meshing using greedy algorithm.

#### Algorithm Overview

```
For each axis (X, Y, Z):
  For each slice perpendicular to axis:
    1. Build visibility mask (which faces are exposed)
    2. Group adjacent same-block faces into quads
    3. Merge quads greedily (expand width, then height)
    4. Output optimized quad
```

#### Detailed Steps

**Step 1: Visibility Mask**

```java
for (int y = 0; y < WORLD_HEIGHT; y++) {
    for (int z = 0; z < CHUNK_SIZE; z++) {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            short block = chunk.getBlock(x, y, z);
            short neighbor = getNeighbor(x, y, z, faceDir);
            
            boolean visible = isBlockFaceVisible(block, neighbor);
            mask[y][z][x] = visible ? blockId : 0;
        }
    }
}
```

**Step 2: Greedy Merging**

```java
for (int y = 0; y < height; y++) {
    for (int z = 0; z < depth; ) {
        int blockId = mask[y][z];
        if (blockId == 0) { z++; continue; }
        
        // Expand width (along z)
        int width = 1;
        while (z + width < depth && mask[y][z + width] == blockId) {
            width++;
        }
        
        // Expand height (along y)
        int height = 1;
        boolean canExpand = true;
        while (y + height < maxHeight && canExpand) {
            for (int w = 0; w < width; w++) {
                if (mask[y + height][z + w] != blockId) {
                    canExpand = false;
                    break;
                }
            }
            if (canExpand) height++;
        }
        
        // Output quad
        emitQuad(x, y, z, width, height, blockId, faceDir);
        
        // Clear mask
        for (int h = 0; h < height; h++) {
            for (int w = 0; w < width; w++) {
                mask[y + h][z + w] = 0;
            }
        }
        
        z += width;
    }
}
```

**Step 3: Vertex Generation**

```java
void emitQuad(int x, int y, int z, int w, int h, short block, Face face) {
    // Get texture UV from atlas
    String tileName = BlockRenderMap.getTile(block, face);
    Atlas.Rect uv = atlas.getRect(tileName);
    
    // Generate 4 vertices
    Vertex v0 = new Vertex(x0, y0, z0, uv.minU, uv.minV, 0, 0);
    Vertex v1 = new Vertex(x1, y1, z1, uv.maxU, uv.minV, w, 0);
    Vertex v2 = new Vertex(x2, y2, z2, uv.maxU, uv.maxV, w, h);
    Vertex v3 = new Vertex(x3, y3, z3, uv.minU, uv.maxV, 0, h);
    
    // Add to mesh
    vertices.add(v0, v1, v2, v3);
    indices.add(baseIdx, baseIdx+1, baseIdx+2, baseIdx, baseIdx+2, baseIdx+3);
}
```

#### 3-Pass Rendering

Chunks are rendered in 3 passes for correct transparency:

**Pass 1: Opaque** (depth write ON, no blending)
- Stone, dirt, grass, sand, wood, etc.

**Pass 2: Cutout** (depth write ON, alpha discard)
- Leaves, flowers, grass, bushes (alpha test)

**Pass 3: Translucent** (depth write OFF, blending ON)
- Water, glass (requires back-to-front sorting)

#### Special Block Handling

**Water**: 
- Rendered with lowered top face (0.85 height)
- Per-cell quads (no greedy merging for top face)

**Billboard Vegetation**:
- Bush, flowers, grass: crossed quads (X shape)
- Double-sided rendering
- No face culling

---

## Configuration System

### WorldContentConfig

**Location**: `org.aouessar.core.gen.config.WorldContentConfig`

JSON-based configuration for biomes and structures.

#### File Structure

```json
{
  "version": "1.0.0",
  "biomes": {
    "BIOME_NAME": {
      "surface_blocks": ["BLOCK1", "BLOCK2"],
      "structures": {
        "trees": { ... },
        "vegetation": { ... }
      }
    }
  },
  "global_blocks": { ... }
}
```

#### Loading

```java
// Load from resources
InputStream stream = getClass().getResourceAsStream("/constraints/world_content_v1.json");
ObjectMapper mapper = new ObjectMapper();
WorldContentConfig config = mapper.readValue(stream, WorldContentConfig.class);

// Access biome config
BiomeConfig plainsConfig = config.getBiome("PLAINS");
TreesConfig treesConfig = plainsConfig.getStructures().getTrees();
```

#### Configuration Objects

**BiomeConfig**:
```java
public class BiomeConfig {
    private List<String> surfaceBlocks;
    private StructuresConfig structures;
}
```

**StructuresConfig**:
```java
public class StructuresConfig {
    private TreesConfig trees;
    private VegetationConfig vegetation;
}
```

**TreesConfig**:
```java
public class TreesConfig {
    private List<String> types;        // ["OAK", "SPRUCE"]
    private double density;             // 0.0 - 1.0
    private String distribution;        // SCATTERED, CLUSTERED, etc.
    private ClusteringConfig clustering;
    private ClearingsConfig clearings;
}
```

#### Runtime Usage

```java
// In DefaultStructureBuilder
BiomeConfig biomeConfig = config.getBiome(biomeName);
TreesConfig treesConfig = biomeConfig.getStructures().getTrees();

int numTrees = (int)(treesConfig.getDensity() * CHUNK_AREA);
List<String> treeTypes = treesConfig.getTypes();

for (int i = 0; i < numTrees; i++) {
    String treeType = randomChoice(treeTypes);
    int x = random.nextInt(CHUNK_SIZE);
    int z = random.nextInt(CHUNK_SIZE);
    placeTree(x, z, treeType);
}
```

---

## Performance Considerations

### Caching Strategy

**Region Cache** (Caffeine):
- Max size: 512 regions (~128 MB)
- Eviction: LRU
- Thread-safe

**Chunk Cache** (Caffeine):
- Max size: 8192 chunks (~1.5 GB)
- Eviction: LRU
- Thread-safe

**Mesh Cache** (ChunkMeshCache):
- Lazy generation (deferred until visible)
- GPU memory managed separately
- Invalidation on chunk update

### Threading Model

**Region Generation**:
- Async on thread pool (`ExecutorService`)
- Deduplication via `CompletableFuture` map
- No blocking on main thread

**Rendering**:
- Single-threaded (OpenGL constraint)
- Mesh building can be async (future improvement)

### Memory Management

**Per Region**: ~500 KB (256×256 layers)  
**Per Chunk**: ~200 KB (voxel array)  
**Per Mesh**: ~10-100 KB (vertex/index buffers)

**Total for 512 regions**: ~250 MB + GPU memory

---

**Next**: [Gameplay Features →](Gameplay-Features.md)
