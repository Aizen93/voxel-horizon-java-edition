# Architecture

This document describes the production-grade architecture of Voxel Horizon Java Edition.

## Table of Contents

1. [Core Principles](#core-principles)
2. [Module Layout](#module-layout)
3. [World Model](#world-model)
4. [Streaming Architecture](#streaming-architecture)
5. [Generation Pipeline](#generation-pipeline)
6. [Clean Architecture Adherence](#clean-architecture-adherence)

---

## Core Principles

The engine is built on **non-negotiable** architectural principles:

### 1. Infinite World Model
- **Infinite coordinates**: Works with `Integer.MIN_VALUE` to `Integer.MAX_VALUE`
- **Region-based paging**: World divided into finite regions (256×256 blocks each)
- **Deterministic generation**: Same seed + coordinates = same output always
- **No global world window**: No fixed "loaded area" concept

### 2. Separation of Concerns
- **NO OpenGL/LWJGL code** outside the renderer module
- **Renderer never knows** about generation windows, rects, or caching
- **Core never throws** "out of bounds" during normal gameplay
- **Missing data handled** via placeholders or futures — never crashes

### 3. Data Pipeline Design
- All generation is **pure functions** (no side effects)
- Data is generated **per region** and cached
- Renderer can request **any chunk at any time**
- Core **always answers** safely

### 4. Future-Proof Architecture
- **Far-field LOD friendly**: Heightmap-based rendering supported
- **UE5 compatible**: World data is serializable and engine-agnostic
- **Streaming ready**: All layers are cacheable and serializable

---

## Module Layout

```
src/main/java/org/aouessar/
├── app/                        # APPLICATION LAYER
│   ├── Main.java              # Entry point, bootstrap
│   └── BiomeMapViewer.java    # 2D visualization tool
│
├── core/                       # CORE DOMAIN LAYER (NO RENDERING)
│   ├── api/                   # Public streaming interfaces
│   │   ├── ChunkProvider.java
│   │   ├── WorldSampler.java
│   │   ├── LodProvider.java   # Far-field LOD tiles (Distant Horizons)
│   │   └── WorldAccess.java
│   │
│   ├── gen/                   # Generation pipeline
│   │   ├── WorldGenerator.java        # Interface
│   │   ├── BiomeGenerator.java        # Interface
│   │   ├── WorldCarver.java           # Interface
│   │   ├── SurfaceDecorator.java      # Interface
│   │   ├── WaterGenerator.java        # Interface
│   │   ├── StructureBuilder.java      # Interface
│   │   ├── RegionPipeline.java        # Orchestrator
│   │   │
│   │   ├── impl/              # Implementations
│   │   │   ├── SimpleWorldGenerator.java
│   │   │   ├── DefaultBiomeGenerator.java
│   │   │   ├── DefaultWorldCarver.java
│   │   │   ├── BiomeDecorator.java
│   │   │   ├── DefaultWaterGenerator.java
│   │   │   ├── DefaultStructureBuilder.java
│   │   │   └── DefaultRegionPipeline.java
│   │   │
│   │   └── config/            # JSON-based configuration
│   │       ├── WorldContentConfig.java
│   │       ├── BiomeConfig.java
│   │       ├── StructuresConfig.java
│   │       └── VegetationConfig.java
│   │
│   ├── stream/                # Streaming orchestration
│   │   └── RegionStreamingService.java
│   │
│   ├── world/                 # Data model
│   │   ├── Chunk.java
│   │   ├── Region.java
│   │   ├── WorldGrid.java     # Coordinate transforms
│   │   ├── Blocks.java        # Block ID registry
│   │   ├── Biomes.java        # Biome ID registry
│   │   ├── RegionPos.java
│   │   ├── ChunkPos.java
│   │   ├── RegionRect.java
│   │   │
│   │   ├── chunk/
│   │   │   ├── Chunk.java
│   │   │   └── ChunkBuilder.java
│   │   │
│   │   └── layers/            # Derived generation data
│   │       ├── Heightmap.java
│   │       ├── BiomeMap.java
│   │       ├── CarveMask.java
│   │       ├── SurfaceRules.java
│   │       ├── WaterLayer.java
│   │       ├── StructureMap.java
│   │       ├── RegionLayers.java
│   │       ├── ArrayLayer2D.java
│   │       └── LayerRect.java
│   │
│   ├── math/                  # Utilities
│   │   ├── Height.java
│   │   └── CoordMath.java
│   │
│   └── noise/
│       └── FastNoiseLite.java # Procedural noise
│
├── renderer/                   # RENDERING LAYER (LWJGL)
│   ├── LwjglRendererV1.java   # Main renderer
│   ├── RendererConfig.java
│   │
│   ├── mesh/                  # Geometry generation
│   │   ├── ChunkMesher.java
│   │   ├── GreedyChunkMesher.java
│   │   ├── BlockAccessor.java
│   │   ├── MeshData.java
│   │   └── Face.java
│   │
│   ├── atlas/                 # Texture management
│   │   ├── Atlas.java
│   │   └── AtlasLoader.java
│   │
│   ├── gl/                    # OpenGL abstraction
│   │   ├── GlShaderProgram.java
│   │   ├── GlTexture2D.java
│   │   ├── GlMesh.java
│   │   ├── GlMeshTiled.java
│   │   └── IGlMesh.java
│   │
│   ├── camera/
│   │   ├── Camera.java
│   │   └── CameraController.java
│   │
│   └── world/                 # Render-side caching
│       ├── ChunkMeshCache.java
│       ├── ChunkKey.java
│       ├── BlockRenderMap.java
│       ├── SkyRenderer.java
│       └── FogCycle.java
│
└── shared/                     # SHARED CONSTANTS
    └── EngineConfig.java
```

### Module Responsibilities

| Module | Responsibility | Dependencies | Contains |
|--------|----------------|--------------|----------|
| **app** | Bootstrap, wire components | core, renderer | Main entry point |
| **core** | World generation, streaming, data | noise, math | All game logic (NO rendering) |
| **renderer** | OpenGL, meshing, camera | core (via ChunkProvider), LWJGL | All rendering code |
| **shared** | Constants | (none) | Configuration constants |

---

## World Model

### Critical Concept: Infinite World with Finite Regions

The world is **infinite in coordinates**, but **data is finite per region**.

```
WORLD (infinite coordinates)
    ↓ divided into
REGIONS (256×256 blocks each)
    ↓ divided into
CHUNKS (16×16×384 blocks each)
    ↓ composed of
BLOCKS (individual voxels)
```

### Region Structure

Each **Region** is the **streaming and caching unit**:

```java
record Region(
    RegionPos pos,          // rx, rz coordinates
    LayerRect rect,         // Internal bounds (256×256)
    RegionLayers layers     // All derived data
)
```

A region contains:
- **16×16 chunks** (4096 blocks × 4096 blocks if fully generated)
- **All derived layers**: heightmap, biomes, carving, surface, water, structures
- **Immutable once generated** (cached indefinitely)

### Chunk Structure

Each **Chunk** is a **16×16×384 voxel array**:

```java
final class Chunk {
    int cx, cz;             // Chunk coordinates
    short[] blocks;         // Flattened [x,z,y] array
    
    short getBlock(int localX, int worldY, int localZ);
    void setBlock(int localX, int worldY, int localZ, short blockId);
}
```

- Size: 16 × 16 × 384 = **98,304 blocks** = ~196 KB per chunk
- Layout: `index = ((z*16)+x)*384 + localY`
- Immutable after initial composition

### Renderer API

The renderer **only ever does**:

```java
Chunk chunk = chunkProvider.getChunk(cx, cz);
```

The renderer:
- ✅ **Can request any chunk** at any coordinates
- ✅ **Never sees region boundaries**
- ✅ **Doesn't know about caching** or generation windows
- ✅ **Receives deterministic placeholders** if data not ready

---

## Streaming Architecture

### RegionStreamingService

The core streaming service implements both `ChunkProvider` and `WorldSampler`:

```java
public class RegionStreamingService implements ChunkProvider, WorldSampler {
    
    // Thread-safe caches
    private final Cache<RegionPos, Region> regionCache;
    private final ConcurrentMap<RegionPos, CompletableFuture<Region>> inFlight;
    private final Cache<ChunkPos, Chunk> chunkCache;
    
    // Generation pipeline
    private final RegionPipeline pipeline;
    private final ExecutorService regionExecutor;
    
    // Public API
    @Override
    public Chunk getChunk(int cx, int cz) { ... }
    
    @Override
    public int heightAt(int wx, int wz) { ... }
    
    @Override
    public int biomeIdAt(int wx, int wz) { ... }
    
    @Override
    public short surfaceBlockAt(int wx, int wz) { ... }
}
```

### Streaming Guarantees

✅ **Thread-safe**: Uses `ConcurrentHashMap` and `Caffeine` cache  
✅ **Deduplication**: In-flight map prevents redundant region builds  
✅ **No blocking**: Returns placeholders if region not ready  
✅ **No exceptions**: Safe fallbacks for missing data  
✅ **Async generation**: Regions built on thread pool  
✅ **Cache eviction**: LRU eviction with Caffeine (configurable)

### Cache Strategy

```
Request: getChunk(cx, cz)
    ↓
1. Check chunk cache → HIT? Return cached chunk
    ↓ MISS
2. Resolve region: regionPos = (cx/16, cz/16)
    ↓
3. Check region cache → HIT? Compose chunk from region layers
    ↓ MISS
4. Check in-flight map → BUILDING? Wait for future
    ↓ NOT BUILDING
5. Submit async build → Add to in-flight
    ↓
6. Return placeholder chunk (deterministic)
    ↓
7. Future completes → Region added to cache
    ↓
8. Next request gets real data
```

---

## Generation Pipeline

### Pipeline Flow (Per Region)

Each region goes through a **deterministic generation pipeline**:

```
1. WorldGenerator.generateHeightmap(seed, rect)
       ↓ produces Heightmap (int[256×256])
       
2. BiomeGenerator.generateBiomes(seed, heightmap)
       ↓ produces BiomeMap (short[256×256])
       
3. WorldCarver.generateCarveMask(seed, heightmap)
       ↓ produces CarveMask (byte[256×256])
       
4. SurfaceDecorator.generateSurfaceRules(heightmap, biomeMap)
       ↓ produces SurfaceRules (top/filler/depth arrays)
       
5. WaterGenerator.generateWaterLayer(seed, heightmap, carveMask)
       ↓ produces WaterLayer (int[256×256])
       
6. StructureBuilder.placeStructures(seed, heightmap, biomeMap)
       ↓ produces StructureMap (List<Placement>)
       
7. Bundle into RegionLayers record
       ↓
8. Cache in Region(pos, rect, layers)
```

### Pipeline Orchestration

The **RegionPipeline** interface orchestrates all generation:

```java
public interface RegionPipeline {
    RegionLayers generateRegionLayers(long seed, LayerRect rect);
}
```

Implementation:

```java
public class DefaultRegionPipeline implements RegionPipeline {
    private final WorldGenerator worldGen;
    private final BiomeGenerator biomeGen;
    private final WorldCarver carver;
    private final SurfaceDecorator surface;
    private final WaterGenerator water;
    private final StructureBuilder structures;
    
    @Override
    public RegionLayers generateRegionLayers(long seed, LayerRect rect) {
        Heightmap heightmap = worldGen.generateHeightmap(seed, rect);
        BiomeMap biomeMap = biomeGen.generateBiomes(seed, heightmap);
        CarveMask carveMask = carver.generateCarveMask(seed, heightmap);
        SurfaceRules surfaceRules = surface.generateSurfaceRules(heightmap, biomeMap);
        WaterLayer waterLayer = water.generateWaterLayer(seed, heightmap, carveMask);
        StructureMap structureMap = structures.placeStructures(seed, heightmap, biomeMap);
        
        return new RegionLayers(
            heightmap, biomeMap, carveMask,
            surfaceRules, waterLayer, structureMap
        );
    }
}
```

### Key Properties

- **Pure functions**: No side effects, no mutation
- **Deterministic**: Same inputs → same outputs
- **Composable**: Each layer builds on previous
- **Cacheable**: All outputs are immutable records/arrays
- **Serializable**: All data can be saved to disk (future)

---

## Clean Architecture Adherence

### Dependency Inversion

```
app/
  ↓ depends on interfaces in
core/api/
  ↑ implemented by
core/stream/
  ↓ uses
core/gen/ (interfaces)
  ↑ implemented by
core/gen/impl/
```

The renderer **never depends on implementations**, only interfaces:

```java
// In app/Main.java
ChunkProvider chunkProvider = new RegionStreamingService(...);
Renderer renderer = new LwjglRendererV1(chunkProvider, ...);
```

### Separation of Concerns

| Concern | Location | Forbidden |
|---------|----------|-----------|
| **World logic** | core/ | OpenGL, LWJGL, rendering |
| **Rendering** | renderer/ | Generation, caching, world data |
| **Bootstrap** | app/ | Direct dependencies on impl classes |

### Testability

- **Pure generation functions** are easily unit testable
- **Mocked ChunkProvider** allows renderer testing without full core
- **Region layers** can be validated independently

---

## Internal Bounds Concept: LayerRect

### Purpose

Derived layers (heightmap, biome map, etc.) are backed by **finite arrays**. Each layer needs to know:
- Its world-space origin
- Its size
- How to map `(wx, wz)` → array index

### Implementation

```java
public final class LayerRect {
    private final int minX, minZ;
    private final int sizeX, sizeZ;
    
    public boolean contains(int wx, int wz);
    public int index(int wx, int wz);
}
```

### Rules

- ✅ **Internal helper only** (used by layer classes)
- ✅ **NOT visible** to renderer or app
- ✅ **Enables safe bounds checking** during development
- ✅ **Enforces correctness** (fail fast on coordinate errors)

---

## Coordinate Systems

### World Grid Transforms

The `WorldGrid` class handles all coordinate conversions:

```java
// Blocks ↔ Chunks
int chunkX = WorldGrid.blockToChunk(worldX);
int chunkZ = WorldGrid.blockToChunk(worldZ);
int localX = WorldGrid.blockToLocal(worldX);

// Chunks ↔ Regions
int regionX = WorldGrid.chunkToRegion(chunkX);
int regionZ = WorldGrid.chunkToRegion(chunkZ);
int chunkInRegionX = WorldGrid.chunkToLocalInRegion(chunkX);

// Regions ↔ World Blocks
int regionOriginX = WorldGrid.regionToBlock(regionX);
int regionOriginZ = WorldGrid.regionToBlock(regionZ);
```

Uses **`floorDiv` and `floorMod`** for correct negative coordinate handling.

---

## Far-Field LOD (Distant Horizons)

The engine renders a **Distant Horizons–style far field** on top of the near-field
voxel chunks, pushing the visible range to **4096+ blocks** (configurable via
`RendererConfig.LOD_VIEW_TILES`).

### Core side: `LodProvider` / `LodTile`

```java
LodTile tile = lodProvider.sampleTile(tileX, tileZ, step); // step ∈ {4, 8, 16, 32}
```

- One tile covers one region footprint (256×256 blocks) sampled every `step` blocks
- A tile holds per-column **height**, **water level** and **top block**, plus a
  1-sample border ring for smooth normals and crack-free stitching
- Tiles are computed **directly from the deterministic column functions** — no
  region build, no cache dependency. A step-16 tile costs a few hundred noise
  evaluations instead of a full region build, so thousands of tiles stream in
  within seconds while the region cache stays tiny

### Shared column functions (the key invariant)

`TerrainColumnSampler`, `ClimateColumnSampler` and `RiverColumnSampler` are the
single source of truth for terrain height, biome classification and river
channels. Both the full-resolution region pipeline and the LOD sampler evaluate
these same functions, so **far-field heights are byte-identical to near-field
chunks** (pinned by `LodConsistencyTest`).

### Renderer side

- `LodMeshCache` keeps one mesh per tile, keyed by tile coords, meshed at a step
  chosen from the Chebyshev ring distance to the camera (4 → 8 → 16 → 32),
  rebuilt in the background when the desired step changes
- `LodMesher` builds vertex-colored heightfield grids with smooth normals, edge
  skirts (hide cracks between LOD levels) and row-merged water planes
- `AtlasColorMap` derives per-block vertex colors from the **actual texture
  atlas**, so distant terrain matches near-field textures exactly; forested
  biomes blend in canopy leaf colors
- LOD terrain draws after near opaque geometry (slightly depth-biased, and
  fragments inside the near-field radius are discarded in-shader), LOD water
  draws in the translucent pass with sky reflection + sun glint

---

## HDR Post-Processing Pipeline

The renderer draws the whole frame into an offscreen **4x MSAA RGBA16F** HDR
target managed by `PostProcessor` (renderer module only — core is untouched).

```
3x shadow cascades (near chunks; LOD casts into the far cascade)
        ▼
sky → opaque → LOD terrain → cutout          [jittered projection: TAA]
        │ resolve color+depth ─────────┐
        ▼                              ▼
   translucent water  ◄── samples sceneColor / sceneDepth
        │
        ▼ resolve HDR scene
 TAA (reproject history via depth + prev view-proj, neighborhood clamp)
 bright pass → Gaussian bloom (half-res)
 radial god rays (from sun screen position, border-faded + radial falloff)
        ▼
 ACES filmic tonemap + exposure (+ underwater wobble/vignette) → HUD
```

- **Water v3**: the mid-frame resolve lets the water shader do screen-space
  **refraction** (visible riverbed), **Beer's-law absorption** by real travel
  distance, and ray-marched **SSR** reflections against the depth buffer
- **Volumetric clouds** live in the sky shader (slab march with per-step sun
  tap); the **same FBM field projected along the sun** darkens terrain in the
  near, cutout and LOD shaders, so cloud shadows drift in sync with the sky
- **Cascaded shadows**: 130 / 420 / 1300-block ortho cascades, selected per
  fragment by camera distance; the LOD heightfield casts into the far cascade
  so distant mountains shade distant valleys
- **Underwater**: camera-below-surface flag switches every shader to dense
  blue-green fog, adds animated caustics on submerged terrain, and renders the
  water surface double-sided (Snell's window from below)
- **Day/night lighting**: `FogCycle` derives a sunlight color × intensity from
  the sun height (white day → orange twilight → blue moonlight at
  `NIGHT_LIGHT_FLOOR`); it multiplies all world shading via `uSunLight`. The
  sky adds a moon + twinkling stars at night, and clouds dim to moonlight
- **Handheld torch**: warm point light around the camera (quadratic falloff,
  flicker) added in the near-field shaders, plus a view-space viewmodel with
  an HDR flame that feeds bloom. T = light on/off (smooth fade), H = show/hide
  the model
- **Player physics**: G toggles free-fly vs Minecraft-style walking — AABB
  collision (0.6 x 1.8, substepped axis clamping), gravity + jump, and
  swimming with buoyancy when the feet are in water (Space up / Ctrl dive,
  bank-hop assist). Physics freezes until the player's chunk has streamed in
- **Third-person avatar**: F5 cycles first person / third (back) / third
  (front); C switches between the human wanderer and the elf ranger. The
  avatar is a procedural voxel model animated on the CPU (walk/swim cycles,
  camera-tracking head), drawn with the opaque pass and lit by sun x cave
  skylight + torch. The orbit camera pulls in at terrain so it never clips
- **Weather + ambient life**: seed-deterministic storm schedule (rain, or
  snow in cold biomes) with ramping intensity, drifting wind, storm overcast
  (fog/light/sky/clouds all follow) and lightning (composite flash + HDR
  bolt). Ambient particles: wind-slanted rain / swaying snow that stop at
  each column's surface (dry caves), leaves falling from real leaf blocks,
  bird flocks on fair days, and fish inside water columns drawn opaque so
  the water refracts them
- **Caves**: classic worm carvers run per chunk in `ChunkBuilder`
  (deterministic per-origin-chunk RNG within `CAVE_RANGE_CHUNKS`, so tunnels
  cross chunk borders seamlessly). Tunnels/rooms/branches carve terrain to AIR
  — or to WATER at/below the water level of wet columns, which turns seabed
  breaches into flooded diveable entrances. The mesher darkens faces by depth
  under the topmost opaque block (down to 0.12), so cave interiors read as
  caves without any shader changes
- Knobs: `RendererConfig.POST_EXPOSURE`, `BLOOM_*`, `GODRAY_STRENGTH`,
  `TAA_ENABLED`, `SHADOW_CASCADE_EXTENTS`, `CLOUD_COVER`, `CLOUD_SHADOW_STRENGTH`,
  `NIGHT_LIGHT_FLOOR`, `TWILIGHT_SUNLIGHT_TINT`

---

## Summary

The architecture is designed for:
- ✅ **Scalability**: Infinite world with finite memory
- ✅ **Correctness**: No crashes, safe fallbacks, deterministic
- ✅ **Maintainability**: Clean separation, testable, documented
- ✅ **Future-proof**: LOD-ready, serializable, engine-agnostic

**Next**: [Interfaces →](Interfaces.md)
