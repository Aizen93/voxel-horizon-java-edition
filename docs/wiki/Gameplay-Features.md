# Gameplay Features

This document describes the current gameplay features and mechanics in Voxel Horizon.

## Table of Contents

1. [World Features](#world-features)
2. [Biomes](#biomes)
3. [Blocks](#blocks)
4. [Structures](#structures)
5. [Controls](#controls)
6. [Visual Features](#visual-features)

---

## World Features

### Infinite World

- ✅ **Infinite coordinates**: Theoretically unlimited world size
- ✅ **Region-based streaming**: Seamless chunk loading as you explore
- ✅ **Deterministic generation**: Same seed always generates identical world
- ✅ **No world borders**: Explore in any direction without limits

### Terrain Generation

**Island System**:
- Large continental landmasses
- Island chains and archipelagos
- Smooth coastline transitions
- Realistic beach slopes

**Ocean System**:
- **Ocean** biome: Shallow water (depth 0-20 blocks)
- **Deep Ocean** biome: Deep water (depth > 20 blocks)
- Dynamic water levels

**Mountain System**:
- Multi-octave noise for realistic peaks
- Erosion patterns (smooth vs. jagged)
- Mountain ranges up to Y=200+
- Valleys and lowlands

**Height Range**:
- Minimum: Y = -64 (bedrock layer)
- Sea level: Y = 62
- Maximum: Y = 319
- Total vertical range: 384 blocks

---

## Biomes

The world features 9 distinct biomes, each with unique characteristics:

### 1. PLAINS
**Climate**: Temperate  
**Elevation**: 62-120 blocks  
**Surface**: Grass, dirt, occasional gravel  

**Features**:
- Oak trees (15% density, scattered)
- Tall grass and flowers (60% density)
- Tree-free clearings (8-block radius)
- Rolling hills

### 2. DESERT
**Climate**: Hot & dry  
**Elevation**: 90+ blocks (high plateaus)  
**Surface**: Desert sand, desert sandstone  

**Features**:
- Cacti (5% density, clustered in groups of 2-5)
- No vegetation
- Flat terrain with occasional dunes

### 3. SNOW
**Climate**: Cold  
**Elevation**: 62-160 blocks  
**Surface**: Snow grass, snow layer, ice  

**Features**:
- Snow trees (12% density, clustered 3-7)
- Tall grass, cornflowers (12% density)
- Frozen water surfaces
- Mountainous terrain

### 4. FOREST
**Climate**: Temperate  
**Elevation**: 62-140 blocks  
**Surface**: Dirt, occasional leaves  

**Features**:
- Spruce trees (45% density, heavily clustered 5-12)
- Berry bushes, oxeye daisies (80% density, patchy)
- Tree clearings (10-block radius)
- Dense canopy

### 5. SAVANNA
**Climate**: Hot & dry  
**Elevation**: 62-110 blocks  
**Surface**: Dry grass  

**Features**:
- Acacia trees (18% density, scattered)
- Dry wheat, vines (40% density, patchy clusters 4-9)
- Large clearings (12-block radius)
- Flat with occasional hills

### 6. SWAMP
**Climate**: Temperate & wet  
**Elevation**: 60-70 blocks (low-lying)  
**Surface**: Grass, dirt, clay, gravel, water patches  

**Features**:
- Oak trees (25% density, clustered 3-6)
- Bushes, tall grass (90% density, uniform)
- Shallow water pools
- Flat terrain

### 7. JUNGLE
**Climate**: Hot & wet  
**Elevation**: 62-100 blocks  
**Surface**: Podzol dirt  

**Features**:
- Jungle trees (65% density, massive clusters 8-16)
- Mega jungle trees (rare, 12-20 blocks tall)
- Bushes, flowers (100% density, dense patches 6-20)
- Hilly terrain
- Impenetrable vegetation

### 8. OCEAN
**Climate**: Any  
**Elevation**: -20 to 62 (below sea level)  
**Surface**: Water, occasional gravel/sand on floor  

**Features**:
- Water-filled
- Depth: 0-20 blocks
- Underwater terrain variation

### 9. DEEP_OCEAN
**Climate**: Any  
**Elevation**: < -20 (deep below sea level)  
**Surface**: Water, deepslate on floor  

**Features**:
- Water-filled
- Depth: 20+ blocks
- Darker, deeper trenches

---

## Blocks

### Block Types (27 total)

**Natural Terrain**:
- **STONE** (3): Base underground material
- **DEEPSLATE** (27): Deep underground (Y < -20)
- **DIRT** (2): Filler material
- **GRASS** (1): Standard grass block
- **SNOW_GRASS** (22): Snow-covered grass
- **DRY_GRASS** (23): Savanna grass
- **PODZOL_DIRT** (24): Jungle floor

**Surface Materials**:
- **DESERT_SAND** (5): Desert surface
- **DESERT_SAND_STONE** (26): Desert filler
- **SAND** (25): Beach sand
- **SNOW** (6): Snow layer
- **ICE** (21): Frozen water
- **GRAVEL** (18): Rivers, beaches
- **CLAY** (19): Swamp bottoms

**Liquids**:
- **WATER** (4): Oceans, rivers, lakes

**Transparent**:
- **GLASS** (7): Transparent block (future)
- **AIR** (0): Empty space

**Vegetation**:
- **LEAVES** (8): Tree foliage
- **BUSH** (9): Small vegetation
- **TALL_GRASS** (10-17): Various grass types
- **Flowers**: Multiple varieties (red, yellow, cornflower, etc.)
- **BERRY_BUSH** (20): Future food source

**Special**:
- **BEDROCK** (28): Indestructible bottom layer (Y = -64)
- **WOOD** (future): Logs/planks

### Render Layers

**OPAQUE** (solid, no transparency):
- Stone, dirt, grass variants, sand, wood, leaves (when treated as solid)

**CUTOUT** (alpha discard, no blending):
- Leaves, flowers, grass, bushes
- Uses alpha testing (binary visible/invisible)

**TRANSLUCENT** (blending):
- Water (requires depth sorting)
- Glass (future)

---

## Structures

### Trees

**OAK** (Plains, Swamp):
- Height: 3-5 blocks
- Trunk: 1×1 wood
- Canopy: 3×3×2 leaves

**SPRUCE** (Forest, Snow):
- Height: 4-7 blocks
- Trunk: 1×1 wood
- Canopy: Conical shape

**JUNGLE** (Jungle):
- Height: 6-10 blocks
- Trunk: 1×1 wood
- Canopy: Wide, irregular

**MEGA_JUNGLE** (Jungle rare):
- Height: 12-20 blocks
- Trunk: 2×2 wood
- Canopy: Massive 7×7+ leaves

**ACACIA** (Savanna):
- Height: 5-7 blocks
- Trunk: Angled
- Canopy: Flat-topped

**CACTUS** (Desert):
- Height: 1-3 blocks
- No leaves
- Standalone columns

**SNOW_TREE** (Snow):
- Height: 4-6 blocks
- Trunk: 1×1 wood
- Canopy: Snow-covered leaves

### Vegetation

**Ground Cover**:
- Tall grass (various types)
- Flowers (red, yellow, cornflower, oxeye daisy, etc.)
- Bushes
- Berry bushes

**Distribution Patterns**:
- **UNIFORM**: Evenly spread
- **SCATTERED**: Random sparse
- **CLUSTERED**: Dense groups
- **PATCHY**: Large irregular patches

---

## Controls

### Movement & Physics

| Key | Action |
|-----|--------|
| **W / A / S / D** | Move (walk or fly) |
| **Space** | Jump (walk) / swim up (water) / ascend (fly) |
| **Left Ctrl** | Dive (water) / descend (fly) |
| **Left Shift** | Sprint (walk) / speed boost (fly) |
| **G** | Toggle physics: walking+gravity+swimming vs free fly (no collision) |
| **Mouse** | Look around |

Swimming engages automatically when your feet are in water (buoyancy,
belly-flop damping, bank-hop exit assist). Physics freezes safely while the
chunk under you is still streaming in.

### Block Interaction

| Input | Action |
|-------|--------|
| **Left Mouse** | Break the aimed block (hold to dig) |
| **Right Mouse** | Place the selected block against the aimed face |
| **1–9** | Select hotbar slot |
| **Scroll wheel** | Cycle hotbar selection |

A wireframe highlight marks the aimed block (6-block reach). Edits survive
chunk eviction within the session; placement never traps you inside a block
while physics is on.

### View, Character & Torch

| Key | Action |
|-----|--------|
| **F5** | Cycle view: first person → third person (back) → third person (front) |
| **C** | Switch character: Aldric the Wanderer (human) ↔ Sylwen the Elf Ranger |
| **T** | Toggle the handheld torch light (warm point light, flickers) |
| **H** | Show/hide the held torch model |

### Menu & Tools

| Key | Action |
|-----|--------|
| **ESC** | Pause menu: mouse-driven sliders/toggles for live settings, Resume/Quit |
| **F2** | Screenshot to `./screenshots` |
| **F9** | "Teleport to Coordinates" dialog |
| **F10** | "Search & Teleport to Biome" dialog |

**F9 - Teleport to Coordinates**:
- Enter X, Z coordinates
- Instantly teleport to location
- Automatically sets Y to surface height

**F10 - Biome Search**:
- Select biome from dropdown
- Searches in expanding spiral pattern
- Shows progress bar
- Pre-warms regions for smooth arrival
- Teleports when biome found

### Visualization Tools

**BiomeMapViewer** (separate application):
- 2D overhead view of world
- Color-coded biomes
- Height visualization
- Grid overlay (toggle)
- Pan and zoom
- Real-time generation

---

## Visual Features

### Rendering (OpenGL 4.6)

**Greedy Meshing** with per-vertex AO + skylight (canopy shade and true cave
darkness down to 0.12 under solid rock).

**Multi-Draw-Indirect Arenas**: all chunk and LOD meshes live in a few large
shared GPU buffers; each pass issues ONE `glMultiDrawElementsIndirect` per
arena — single-digit draw calls per frame regardless of view radius.

**HDR Post Pipeline**: 4x MSAA RGBA16F scene → TAA (Halton-jittered
projection, depth reprojection, neighborhood clamp) → SSAO (depth-derived
contact shadows) → bloom → god rays → volumetric sun shafts (a real ray
march through the shadow cascades: crepuscular rays behind trees, ridges and
cave mouths) → ACES filmic tonemap.

**Shadows**: 3 cascaded 4096px shadow maps out to 1300 blocks (LOD terrain
casts into the far cascade); the near cascade uses adaptive soft PCF
(PCSS-style penumbras — sharp at contact, softer with distance).

**Water v3**: screen-space refraction, Beer's-law absorption, SSR
reflections, shore foam, sun glitter, underwater fog/caustics/Snell's
window — and cave-aware lighting (no sky reflections underground).

### Lighting & Atmosphere

- **Full day/night cycle**: white noon → orange sunset → blue moonlit night
  (readable, tunable floor) → sunrise; moon disc + twinkling stars.
- **Weather**: seed-scheduled rain fronts (snow in cold biomes) with ramping
  intensity, drifting wind, storm overcast that dims fog/light/sky/clouds,
  and lightning (whole-sky flash + jagged HDR bolt).
- **Ambient life**: 3D fish (5 species, varied sizes) wandering real water
  columns, bird flocks (crow/gull/sparrow, articulated flapping wings +
  glides) on fair days, leaves shedding from real tree canopies in tumbling
  pendulum arcs, wind-slanted rain that never falls indoors.
- **Handheld torch**: warm flickering point light + HDR flame viewmodel.

### Player & Camera

- **Two playable avatars** (procedural voxel models, walk/swim animation,
  camera-tracking head): Aldric the Wanderer and Sylwen the Elf Ranger.
- **Minecraft-style F5 camera** with terrain-aware orbit distance.
- **Character physics**: 0.6×1.8 AABB, gravity, jumping, swimming.

### Textures

**Atlas System**: Minecraft-style texture atlas, 16×16 tiles, per-face
texturing, shared by the world, the far-field LOD vertex colors and the
hotbar icons.

---

## Gameplay Mechanics (Current)

### Exploration

✅ **Walk or fly**: full character physics (gravity, jumping, swimming) or free flight, toggled with G  
✅ **Infinite world**: region streaming, view radius up to 48+ chunks at 250-350 FPS  
✅ **Caves**: classic worm-carver systems with surface/underwater entrances and aquifer-sealed flooded sections — explore them by torchlight  
✅ **Fast travel**: teleport to coordinates or biomes  

### World Interaction

✅ **Block breaking**: LMB with raycast targeting + wireframe highlight, hold to dig  
✅ **Block placing**: RMB against the aimed face, 9-block hotbar palette  
✅ **Edits persist across chunk reloads** (within the session; disk saves planned)  
✅ **Live settings**: ESC menu tunes 16 graphics/gameplay values instantly  
⏳ **Crafting / inventory**: not yet implemented (hotbar palette is creative-style)  

### Survival Elements

⏳ **Health / hunger**: not yet implemented  
✅ **Day/night + weather**: full visual cycle with storms, snow, lightning  
⏳ **Mobs**: ambient life only (fish, birds) — no hostile/interactive mobs yet  

---

## Performance

**Recommended Specs**:
- **CPU**: Quad-core 2.5+ GHz
- **GPU**: OpenGL 3.3+ compatible (2GB+ VRAM)
- **RAM**: 4GB+ (8GB recommended)
- **Java**: Java 21 or higher

**Typical Performance** (measured, RTX-class GPU at 720p):
- **FPS**: 250-350 at radius 48 (9,400+ chunk meshes resident), 400-1200 at radius 16
- **Draw calls**: single digits per frame (multi-draw-indirect arenas)
- **Chunk generation**: real-time, async (worker threads)
- **Memory usage**: ~1-5GB heap at radius 16, up to ~10GB at radius 48

Requires an **OpenGL 4.6** capable GPU (any discrete GPU from the last decade).

---

## Known Limitations

- Block edits are in-memory only (no save/load yet — lost on exit)
- Water is static (no flow simulation; generation guarantees hydrostatic
  plausibility via aquifer dams instead)
- No sound effects or music (no audio system yet)
- No crafting/inventory/survival systems (creative-style hotbar only)
- Avatar casts no shadow; ambient creatures are scenery, not interactive
- World-generation constants can't change at runtime (would seam against
  already-generated regions)

---

**Next**: [Work in Progress →](Work-In-Progress.md)
