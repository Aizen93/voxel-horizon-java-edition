# Copilot Instructions — Voxel Horizon Java Edition

## Architecture Overview

This is a Java 21 voxel engine (Minecraft-like, infinite world) built on LWJGL/OpenGL with strict module separation:

| Module | Package | Responsibility |
|--------|---------|----------------|
| **app** | `org.aouessar.app` | Bootstrap only — wires core ↔ renderer via `WorldAccess` façade |
| **core** | `org.aouessar.core` | World generation, streaming, chunk composition. Zero rendering code |
| **renderer** | `org.aouessar.renderer` | LWJGL/OpenGL. Treats core as a black box via `core/api/` interfaces only |
| **shared** | `org.aouessar.shared` | `EngineConfig` — static constants shared across modules |

**Hard rules**: No OpenGL outside `renderer/`. Renderer must never import from `core/gen/`, `core/stream/`, `core/world/` — only from `core/api/`. The 4 public interfaces are bundled in `WorldAccess(ChunkProvider, WorldSampler, BiomeLocator, StreamingControl)`.

## Data Flow: Generation → Rendering

```
seed + RegionPos → RegionPipeline (6 pure stages) → RegionLayers record
  → cached in Region → ChunkBuilder.buildChunk() → Chunk (short[16×16×384])
  → GreedyChunkMesher → MeshData (opaque/cutout/translucent) → GPU upload
```

Generation pipeline stages (each a separate interface in `core/gen/`):
1. `WorldGenerator` → `Heightmap` (base terrain via FastNoiseLite)
2. `BiomeGenerator` → `BiomeMap` (9 biomes: Plains/Desert/Snow/Forest/Savanna/Swamp/Jungle/Ocean/DeepOcean)
3. `WorldCarver` → `CarveMask` (caves/ravines)
4. `SurfaceDecorator` → `SurfaceRules` (biome-dependent top/filler blocks)
5. `WaterGenerator` → `WaterLayer` (ocean/river levels, runs after carving)
6. `StructureBuilder` → `StructureMap` (trees, vegetation placements)

All generators are **pure functions**: deterministic from `(seed, coordinates)` only. Implementations live in `core/gen/impl/`.

## Key Conventions

- **Records for data**: `RegionPos`, `ChunkPos`, `RegionRect`, `MeshData`, `RegionLayers`, `WorldAccess`
- **Final utility classes** with private constructors: `Blocks`, `CoordMath`, `Height`, `BlockRenderMap`
- **Block IDs are shorts** (0–41 + 100–105 for structure markers), defined in `Blocks.java`
- **Biome IDs are shorts** (0–8)
- **Safe accessor pattern**: layer classes provide both bounds-checked (`heightAt`) and unchecked (`heightAtUnchecked`) variants. Out-of-bounds returns defaults (e.g., `SEA_LEVEL`), never throws
- **Constructor validation**: records/layers fail-fast with `IllegalArgumentException` on array length mismatches

## World Geometry Constants (in `EngineConfig`)

- `CHUNK_SIZE = 16`, `REGION_SIZE_CHUNKS = 16` (256×256 blocks per region)
- Y range: `WORLD_MIN_Y = −64` to `WORLD_MAX_Y = 319` (`WORLD_HEIGHT = 384`)
- `SEA_LEVEL = 62`
- `RegionRect` is an internal spatial helper — never exposed to renderer

## Threading Model

- **Region generation**: async thread pool (`availableProcessors() − 1`, min 2), named `region-gen-N`. Deduplicated via `ConcurrentHashMap<RegionPos, CompletableFuture<Region>>`
- **Chunk building**: synchronous on calling thread (derives from cached region layers)
- **Mesh building**: renderer-side thread pool, budget-limited per frame (64 submits, 128 GPU uploads)
- **Eviction**: throttled, runs every N frames for regions, chunks, and mesh caches

## Configuration

- **Static tuning**: `EngineConfig` — terrain noise frequencies, biome thresholds, threading counts
- **Content config**: `resources/constraints/world_content_v1.json` — per-biome surface blocks, tree types/density, vegetation. Versioned (`v1`, `v2`…), schema-validated by `world_content.schema.json`, loaded by `WorldContentConfig`
- **Renderer config**: `RendererConfig` — window size, fog/sky colors, shader paths, atlas path

## Build & Run

```sh
./gradlew run          # Launch the engine (main class: org.aouessar.app.Main)
./gradlew test         # JUnit 5 tests
./gradlew build        # Full build + test
```

Dependencies: LWJGL 3.3.6 (GLFW, OpenGL, STB), Gson 2.13.2, JOML 1.10.8, JUnit 5.10.0.

## Error Handling

- **Never crash during gameplay**: missing regions return placeholder/empty chunks, biome defaults to 0
- **Graceful config loading**: JSON parse failures log warnings and keep defaults
- Core uses a custom `WorldGenException` (unchecked) for unrecoverable generation errors

## Testing

Tests are under `src/test/java/org/aouessar/core/` using JUnit 5. Current focus is coordinate math correctness (`RegionRectTest`, `WorldGridTest`). Tests verify negative coordinate handling and chunk↔region transforms.

## When Adding Features

1. New **generation layer**: add interface in `core/gen/`, implementation in `core/gen/impl/`, wire into `RegionPipeline`, add field to `RegionLayers` record, consume in `ChunkBuilder`
2. New **block type**: add constant in `Blocks.java`, map to atlas tile in `BlockRenderMap`, set render layer in `RenderLayer`
3. New **biome**: add ID in `Blocks`/constants, configure in `world_content_v1.json`, handle in `BiomeGenerator` and `SurfaceDecorator`
4. New **renderer feature**: keep it in `renderer/` — only access core through `WorldAccess` interfaces
