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

### Movement

| Key | Action |
|-----|--------|
| **W** | Move forward |
| **S** | Move backward |
| **A** | Strafe left |
| **D** | Strafe right |
| **Space** | Move up (fly mode) |
| **Shift** | Move down (fly mode) |
| **Mouse** | Look around (free camera) |

### Camera

| Key | Action |
|-----|--------|
| **Mouse Move** | Rotate camera |
| **Scroll** | Adjust FOV (future) |

### World Navigation

| Key | Action |
|-----|--------|
| **F9** | Open "Teleport to Coordinates" dialog |
| **F10** | Open "Search & Teleport to Biome" dialog |

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

### Rendering

**Greedy Meshing**:
- Optimized geometry (fewer polygons)
- Merges adjacent same-block faces into larger quads
- Reduces GPU load by ~70%

**Frustum Culling**:
- Only renders chunks in view
- Distance-based LOD (future)

**3-Pass Rendering**:
1. Opaque pass (depth write ON)
2. Cutout pass (alpha discard)
3. Translucent pass (blending, back-to-front)

### Lighting & Atmosphere

**Sky Rendering**:
- Gradient sky dome
- Day/night cycle (visual only, no gameplay effect yet)
- Horizon color blending

**Fog**:
- Distance fog for depth perception
- Color changes with time of day
- Adjustable density

**Water Rendering**:
- Lowered surface (0.85 height) for wave effect
- Per-cell rendering (not greedy merged on top)
- Translucent with blending
- Future: Reflections, ripples

### Textures

**Atlas System**:
- Minecraft-style texture atlas (256×256)
- 16×16 pixel tiles
- Per-face texturing (e.g., grass has different top/side)
- Efficient GPU batching

---

## Gameplay Mechanics (Current)

### Exploration

✅ **Free flight**: WASD + Space/Shift navigation  
✅ **Infinite world**: Explore without boundaries  
✅ **Biome diversity**: 9 distinct biomes to discover  
✅ **Fast travel**: Teleport to coordinates or biomes  

### World Interaction

⏳ **Block breaking**: Not yet implemented  
⏳ **Block placing**: Not yet implemented  
⏳ **Crafting**: Not yet implemented  
⏳ **Inventory**: Not yet implemented  

### Survival Elements

⏳ **Health**: Not yet implemented  
⏳ **Hunger**: Not yet implemented  
⏳ **Day/night cycle gameplay**: Visual only, no mobs yet  
⏳ **Weather**: Not yet implemented  

---

## Performance

**Recommended Specs**:
- **CPU**: Quad-core 2.5+ GHz
- **GPU**: OpenGL 3.3+ compatible (2GB+ VRAM)
- **RAM**: 4GB+ (8GB recommended)
- **Java**: Java 21 or higher

**Typical Performance** (Mid-range hardware):
- **FPS**: 60-144 (uncapped)
- **View distance**: 16-32 chunks
- **Chunk generation**: Real-time, async
- **Memory usage**: 500MB - 2GB (depends on exploration)

---

## Known Limitations

- No block modification (creative/survival not implemented)
- No mobs or entities
- No lighting system (beyond ambient)
- No physics (beyond collision detection, future)
- Water is static (no flow simulation)
- No sound effects or music

---

**Next**: [Work in Progress →](Work-In-Progress.md)
