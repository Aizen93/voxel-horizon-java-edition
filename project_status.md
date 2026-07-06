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

## Current State (July 2026 cleanup & LOD pass)

**Shipped in this pass:**
- **Far-field LOD (Distant Horizons)**: `LodProvider`/`LodTile` core API + ring-based
  LOD renderer. View distance is now **4096 blocks** (= 256 chunks) by default
  (`RendererConfig.LOD_VIEW_TILES`), rendered at 1000+ FPS. Far heights are
  byte-identical to near chunks (shared column samplers, pinned by `LodConsistencyTest`).
- **Rivers v2**: connected meandering channels (domain-warped zero-contour field) with
  intensity-shaped profiles (shallow banks, deep center), water filling to 1 below the
  banks along the whole course, elevation fade above sea+60. Visible in the far field too.
- **Tree distribution**: JSON `distribution`/`clustering`/`clearings` are now honored —
  CLUSTERED groves, PATCHY thickets, tree-free meadow clearings (vegetation unaffected).
- **Bug fixes**: unified `EngineConfig.WORLD_SEED` (2D viewer showed a *different world*
  before), seeded ecotone noise per world, frustum-culling Y-range (mountain tops above
  world Y ~255 no longer vanish), billboard Y iteration (missing high-altitude vegetation),
  MeshData stride, swamp ocean-distance region seam, NPE guard in BlockAccessor,
  duplicate sky render, dead code removal (ChunkMesher, GlMesh).

**Renderer defaults**: near field radius 16 chunks (full voxel detail) + LOD rings to 16
tiles (innermost ring at 2-block sampling, flat-shaded for a blocky far look). Fog tuned
so the world edge is always fully fogged before it ends.

**Rivers v3**: river valleys are carved directly inside `TerrainColumnSampler` — water is
ALWAYS at sea level (vanilla style). No more water climbing hills, no stepped terraces,
no snow/ice beside elevated channels; valleys fade into dry gullies above sea+60.

**Image quality**: atlas mipmaps (max level 4) + `textureGrad` sampling (no seam lines on
greedy quads) + anisotropic filtering (8x) + 4x MSAA.

**Near→far transition**: dithered dissolve band (`NEAR_FADE_BAND_BLOCKS`) — the voxel
world melts pixel-by-pixel into the LOD over the last ~44 blocks of the near radius,
with the LOD already drawn underneath. Euclidean cut, so the horizon ring is circular.

**Lighting** (chunk vertex stride is now 8 floats: ... + shade):
- Per-vertex ambient occlusion (Minecraft "smooth lighting"; AO is part of the greedy
  merge key so merged quads keep correct corners)
- Skylight via per-column light-ceiling scan — soft shadows under tree canopies and
  overhangs (water passes light; leaves block it)
- Directional sun shadow map over the near field: 2048px depth texture, hardware-PCF
  3x3, texel-snapped ortho, leaves cast alpha-tested shadows, fades out at dusk/night.
  Toggle + knobs: `RendererConfig.SHADOWS_ENABLED / SHADOW_MAP_SIZE / SHADOW_EXTENT_BLOCKS
  / SHADOW_STRENGTH`. Measured cost: ~15% at 1100+ FPS.
- Blocklight (torches etc.) intentionally deferred until there are light-emitting blocks.

**Water v2 / sky v2** (reference: Minecraft shader-pack look):
- Water surfaces carry their column depth in the vertex shade channel: shallow water is
  clear teal (bed visible through it), deep water darkens toward navy and turns opaque;
  animated shore foam on the shallowest band; brighter sun-glitter path. LOD ocean gets
  animated swell + the same glitter so the horizon sparkles.
- Sky is now view-directional (per-pixel ray from inverse proj/view): gradient, visible
  sun disc + glow that tracks the FogCycle sun (matches shadows), twilight warm band
  toward the sun, and an animated procedural FBM cloud deck with cheap self-shadowing
  (lit tops / dark bottoms), wind drift, distance fade. Knobs: `RendererConfig.CLOUD_COVER`,
  `CLOUD_HEIGHT`.
**Water v3 + HDR post pipeline** (reference: Bliss shader pack):
- The frame now renders into an offscreen 4x MSAA RGBA16F HDR target (`PostProcessor`).
  After the opaque+cutout passes, the scene is resolved to color + depth textures so the
  water shader can sample the world *behind* the surface:
  - **Refraction**: screen-space refraction with a depth guard — the riverbed/seabed is
    visible through the water and wobbles with the waves.
  - **Absorption**: Beer's-law tint by real water travel distance (clear teal shallows →
    deep navy) plus in-scatter; water is no longer an opaque painted color.
  - **SSR**: screen-space reflections ray-marched against the depth buffer — hills, trees
    and sky mirror on the surface (falls back to sky color at screen edges).
- Post chain: bright pass → separable Gaussian **bloom** (half-res) → radial **god rays**
  marched toward the sun's screen position → **ACES filmic tonemap** + exposure.
  Knobs: `RendererConfig.POST_EXPOSURE / BLOOM_THRESHOLD / BLOOM_STRENGTH / GODRAY_STRENGTH`.
- **Volumetric clouds**: the sky's flat cloud deck was replaced by a slab-marched
  volumetric layer (8 steps with a sun tap per step — lit tops, shadowed bases).
- **Cloud shadows on terrain**: the same FBM cloud field is projected along the sun onto
  near-field, cutout and LOD terrain (`CLOUD_SHADOW_STRENGTH`), so cloud shade drifts
  across the whole visible world in sync with the sky.
- Measured: 1050–1125 FPS with the full stack enabled.

**TAA + cascaded shadows + underwater (July 2026):**
- **TAA (temporal antialiasing)**: the projection is jittered by a subpixel Halton
  offset each frame; `post_taa.frag` reprojects the previous output via the depth
  buffer + previous view-projection, clamps it to the current 3x3 neighborhood
  (no ghosting on water/cloud animation) and blends. Runs between the scene resolve
  and bloom. Knobs: `RendererConfig.TAA_ENABLED / TAA_BLEND`.
- **Cascaded shadow maps**: 3 cascades (`SHADOW_CASCADE_EXTENTS` = 130 / 420 /
  1300 blocks) replace the single 260-block map — crisp close-ups, whole near
  field, and a far cascade that the **LOD terrain renders into** (depth-only
  `lod_shadow.vert`), so mountains shade valleys past a kilometer. Cascade picked
  per fragment by camera distance, per-cascade depth bias, far edge fades to lit.
  *Engineering note: passing `sampler2DShadow` as a GLSL function parameter
  silently disabled the hardware depth compare on the dev machine's driver —
  cascade sampling must stay inlined per map.*
- **Underwater rendering**: camera-below-surface detection drives dense blue-green
  exp fog in every world shader, an underwater sky, screen wobble + vignette in the
  composite, and dimmed god rays (light shafts). **Caustics** (two drifting ridged
  noise layers) dance on all submerged terrain — visible through the refracting
  surface from above and while diving. Water top faces render double-sided while
  diving, so the surface + Snell's window are visible from below.
- **God rays artifact fix**: the radial march now fades samples smoothly at the
  screen border (a hard cutoff stamped nested copies of the screen rectangle) and
  applies an aspect-correct radial falloff around the sun (whole-sky washout gone).
- **Tooling**: F2 saves a screenshot to `./screenshots`; `-Pvoxel.camera=x,y,z[,yaw,pitch]`
  overrides the spawn; `-Pvoxel.autoshot.dir=<dir> -Pvoxel.autoshot.period=<sec>`
  captures the framebuffer periodically (debug/CI).
- Measured: 830–1230 FPS with TAA + 3 shadow cascades + full post stack.

**Day/night terrain lighting (July 2026):**
- `FogCycle` now outputs a **sunlight color × intensity** (`lightR/G/B`) each frame:
  full white by day, warm orange cast at sunrise/sunset (`TWILIGHT_SUNLIGHT_TINT`),
  cool blue moonlight scaled down to `NIGHT_LIGHT_FLOOR` (0.18) at night — dark but
  readable. Uploaded as `uSunLight` and multiplied into all five world shaders
  (near opaque, cutout, translucent water additive terms, LOD terrain, LOD water),
  so the whole world tracks the cycle: day → orange sunset → moonlit night →
  sunrise → day.
- **Night sky**: moon disc opposite the sun + hash-grid twinkling stars (horizon
  fade), clouds dim to moonlight so they don't glow against the dark sky.
- *Engineering note (root cause of a nasty corruption bug): `org.joml.Math.clamp`
  takes `(min, max, value)` — NOT `(value, min, max)` like Java 21's
  `java.lang.Math.clamp`. Called in the wrong order it degenerates to
  `max(value, 0)`: harmless while inputs stay ≤ 1, but any ramp like
  `clamp(x/0.33, 0, 1)` passes >1 values straight through — a following
  smoothstep polynomial then extrapolates to huge negatives and lerps explode
  (uSunLight hit (426, 306, −31) at noon → white-out; negative R/G at morning →
  inverted blue terrain). The same latent misuse also silently disabled shadows
  above ~21° sun elevation and let god-ray visibility blow up off-screen — all
  call sites fixed to the (min, max, value) order.*
**Caves (July 2026):**
- **Classic Minecraft worm carvers** (`CaveCarver`, hooked into `ChunkBuilder`
  between terrain fill and structures): every chunk within `CAVE_RANGE_CHUNKS`
  gets a deterministic per-chunk RNG; ~1 in 7 origin chunks hosts a cave system
  whose tunnels are fully simulated and carved only where they intersect the
  chunk being built — caves cross chunk borders seamlessly in any build order.
- Tunnel walk with momentum (yaw/pitch drift), sinusoidal radius swell,
  occasional pinches, rare huge tunnels, diving shafts (1 in 6), branch splits
  at mid-length for wide tunnels, and 1-in-4 systems opening with a large
  squashed room that radiates extra tunnels. Ellipsoid carving skips the bottom
  30% for flat walkable floors; carved-away grass surfaces regrow on the dirt
  below so entrance rims look natural.
- **Surface entrances for free**: worms that wander above the heightmap breach
  hillsides, meadows and mountain flanks (probe found mouths from sea level to
  +64 elevation). **Underwater entrances**: carved cells at/below the water
  level of a wet column (ocean/river/lake) fill with WATER instead of AIR, so
  seabed breaches become flooded, diveable cave mouths — no floating water,
  no dry pockets under the sea.
- **Aquifer dams**: wet columns on the outline of their water body (any dry
  4-neighbor column) are never carved below the water level. Flooded cave
  sections are therefore always sealed behind rock — a dry tunnel dead-ends
  into stone at the groundwater boundary instead of facing a free-standing
  wall of water (provably no lateral water→air interfaces from carving; same
  trick as Minecraft's aquifer barrier blocks). Flooded sections remain
  reachable by diving in from the water body above.
- **Cave darkness** (mesher, not shaders): the per-column skylight now tracks
  two ceilings — any blocker (canopy shade, floored at 0.50 as before) and the
  topmost OPAQUE block (cave depth). Faces deep under rock fade through 0.32 /
  0.20 down to 0.12, so interiors go properly dark toward the depths while
  jungle floors keep their old look. Sky level is 3 bits in the greedy mask.
- **Cave water lighting**: the water shader assumed open sky, so water walls
  bordering caves glowed bright sky-blue (looked like holes to the outside).
  Water faces now carry the mesher's baked skylight — side faces directly in
  vShade, top faces as (1 + light) — and the shader scales sky reflection,
  sun glitter, in-scatter, foam and the Snell's-window terms by it. Cave water
  is dark with a faint teal tint (the refracted scene keeps its own light);
  open lakes/oceans are untouched since their light is 1.0.
- Vegetation/cactus placements now verify the ground block survived carving
  (no flowers hovering over fresh entrance holes). Trees keep Minecraft parity
  (a rare tree over a new entrance can overhang, like vanilla).
- Knobs: `EngineConfig.CAVE_*` (range, frequency, y-span, room size).
- **Handheld torch** (cave exploration): a warm point light around the camera
  (quadratic falloff over `TORCH_RANGE_BLOCKS` = 28, soft diffuse toward the
  flame, subtle flicker) added independently of sun/sky light in the near
  opaque/cutout/water shaders — it cuts through cave darkness and is invisible
  in daylight. A view-space torch viewmodel (wooden stick + HDR flame that
  feeds bloom) sits bottom-right like a held item. **T** toggles the light
  (smooth fade, the flame visibly dies/relights), **H** hides/shows the model
  — fully independent. HUD shows the torch state. Knobs: `TORCH_*` in
  `RendererConfig`.
**Player physics (July 2026):**
- **Walk mode** (`CameraController`, G toggles fly <-> walk, or start with
  `-Pvoxel.physics=true`): Minecraft-style character physics — a 0.6 x 1.8
  AABB with eyes at 1.62, gravity 32 blocks/s² with terminal velocity, Space
  jumps ~1.25 blocks from the ground, Shift sprints. Collision is
  axis-separated block clamping run in substeps (<=0.45 blocks each) so even
  terminal-velocity falls can't tunnel through floors. All physics runs in
  render-space Y, querying blocks straight from the chunk provider.
- **Swimming** (feet in water): buoyant damped motion — idle sinks slowly,
  Space swims up, Ctrl dives, horizontal speed drops to swim pace; a fall
  into water gets belly-flop damping, and surfacing against a bank while
  holding Space hops you out (Minecraft's water-exit assist).
- While the chunk under the player is still streaming in (placeholder chunks
  have no bedrock), physics freezes in place instead of dropping the player
  through the unloaded world.
- G is a TWO-STATE toggle: physics ON (gravity + collision; swimming engages
  automatically with feet in water) vs physics OFF (free fly — the original
  camera, no collision at all, flies through rock and water). HUD:
  `Physics [G]: on (walking) | on (swimming) | off (free fly)`. Knobs:
  `PLAYER_*` / `SWIM_*` in `RendererConfig`.
**Third-person character (July 2026):**
- **Two hand-designed voxel avatars** (`PlayerModel`, C switches): *Aldric the
  Wanderer* (tanned skin, dark hair, leather tunic, gold belt buckle, slate
  trousers, travel backpack) and *Sylwen the Elf Ranger* (pale skin, long
  silver hair, pointed ears, emerald eyes, forest-green tunic with gold trim,
  knee-length cape). Classic Minecraft proportions (1.8 blocks), built from
  colored boxes and rebuilt on the CPU each frame for animation.
- **Animation**: legs/arms swing counter-phased with movement speed (short
  fast strokes while swimming, relaxed float in fly mode), the body turns
  smoothly toward the movement direction and settles toward the camera when
  idle, the head tracks the camera within human limits, the elf's cape sways.
- **F5 view cycle** (Minecraft-style): first person -> third person (behind)
  -> third person (front). The orbit camera raycasts back through terrain and
  snaps in so it never clips into rock; it eases back out. The avatar draws
  with the opaque world (water reflects/refracts it) and is lit by day/night
  sunlight x a cave-skylight probe above the player + torch warmth.
- The whole renderer now distinguishes the PLAYER (physics, torch, streaming
  center) from the RENDER EYE (view/fog/underwater/reflections) — `Camera`
  exposes `eyePosition()`; in first person they coincide.
- Startup props: `-Pvoxel.view=first|back|front`, `-Pvoxel.character=elf`.
- Known v1 limits: the avatar casts no shadow and is a color-box model (no
  texture atlas entry yet).
**Weather + ambient life (July 2026):**
- **WeatherSystem**: storms roll in "sometimes" — every `WEATHER_SLOT_SECONDS`
  a seed-deterministic hash decides if the slot rains (~30%); consecutive
  rainy slots form longer fronts; intensity ramps over ~6s. Cold biomes get
  snow instead. Wind drifts slowly in direction/strength and stiffens in
  storms. Storms grey the fog, mute sunlight, flatten the sky colors and push
  cloud cover toward overcast (sky + terrain cloud shadows follow).
  Override: `-Pvoxel.weather=clear|rain|snow` (default auto).
- **Lightning**: heavy rain rolls strikes (~1 per few seconds at full storm):
  a whole-frame cold flash in the composite (`uLightning`, decaying) plus a
  jagged HDR-bright bolt from the cloud deck to the ground that bloom turns
  into a proper flash-glow.
- **AmbientEffects** (CPU-built geometry, one dynamic VBO): wind-slanted rain
  streaks and swaying snowflakes that die at their column's surface (so it
  never rains in caves or under overhangs, and rain lands on water);
  **falling leaves** (up to 120) shed in clusters from real leaf blocks,
  tumbling in pendulum arcs with flip foreshortening, tinted per tree with
  per-leaf variation; **bird flocks** on fair days — real little 3D birds
  (body, head, beak, tail, articulated wings) that alternate flapping bursts
  with glides, in three species (crow, gull, sparrow) and varied sizes;
  **fish** — 3D swimmers (body, belly, snout, beating tail, dorsal +
  pectoral fins) in five species (cod, salmon, perch common; gold and blue
  tropicals rarer and smaller) with sizes 0.6-1.6x, small ones flicking
  faster — drawn with the opaque pass so the water surface refracts and
  reflects them like world geometry.
- HUD shows `Weather: rain 85%  wind 1.2`. Knobs: `WEATHER_*`, `RAIN/SNOW_
  PARTICLES`, `AMBIENT_*`, `LIGHTNING_CHANCE_PER_SEC` in `RendererConfig`.
- Next candidates (not yet built): rain splash rings on surfaces, thunder
  (needs audio), ravines, lava pools at depth, avatar shadow casting,
  underwater god-ray tuning, cascade seam blending, TAA sharpening pass.

---

## High-Level Module Layout
app/
└─ Main entry point
└─ Wires core ↔ renderer

core/
└─ World generation
└─ Streaming & caching
└─ Chunk composition
└─ Far-field LOD sampling (LodProvider — pure, cache-free)
└─ Zero rendering code

renderer/
└─ LWJGL implementation
└─ Near-field rendering (stable)
└─ Far-field LOD rendering (stable — Distant Horizons rings)


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