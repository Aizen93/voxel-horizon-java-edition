# Work in Progress & Roadmap

This document outlines the current development status, ongoing work, and future plans for Voxel Horizon.

## Table of Contents

1. [Current Development Phase](#current-development-phase)
2. [Completed Features](#completed-features)
3. [In Progress](#in-progress)
4. [Roadmap](#roadmap)
5. [Technical Debt](#technical-debt)

---

## Current Development Phase

**Phase**: Playable Sandbox  
**Branch**: `feature/unreal-engine`  
**Focus**: Gameplay systems (block editing, UI), rendering quality/performance on modern GPUs

### Recent Achievements

✅ **Block interaction + game UI** (Jul 2026)
- Break/place with DDA raycast, wireframe highlight, hold-repeat
- Session-persistent edit overlay + border-aware remeshing
- Atlas-icon hotbar (1-9/scroll), crosshair
- Mouse-driven ESC pause menu: sliders/toggles live-tuning 16 settings

✅ **GPU performance + visuals pass** (Jul 2026)
- GL 3.3 → 4.6; multi-draw-indirect mesh arenas (near + LOD): single-digit
  draw calls/frame, 250-350 FPS at radius 48 (was ~90-140)
- 4096px shadow cascades with adaptive soft PCF (PCSS-style penumbras)
- SSAO contact shadows; volumetric sun shafts marched through the cascades

✅ **Weather + ambient life** (Jul 2026)
- Seed-scheduled rain/snow fronts, wind, storm overcast, lightning
- 3D fish (5 species), bird flocks (3 species, flap/glide), tumbling leaves

✅ **Playable character** (Jul 2026)
- Physics: gravity, jumping, swimming (G toggles free fly)
- Third-person avatars (F5 back/front): human wanderer + elf ranger
- Handheld torch (T/H): flickering point light + HDR flame viewmodel

✅ **Caves** (Jul 2026)
- Classic worm carvers: tunnels, rooms, branches; surface + underwater
  entrances; aquifer dams seal flooded sections (no standing water walls)
- True cave darkness via dual skylight ceilings in the mesher

✅ **HDR renderer foundation** (Jul 2026)
- TAA, 3-cascade shadow mapping, bloom, god rays, ACES tonemap
- Water v3: refraction, SSR, caustics, underwater fog/Snell's window
- Full day/night cycle with moon/stars + storm-aware sky

✅ **Deep ocean & island system** (Jan 2026)
- Realistic ocean depth variants
- Improved coastline fragmentation
- Better island generation

✅ **Biome geography variation** (Jan 2026)
- Ocean and deep ocean distinction
- Biome-specific terrain characteristics

✅ **2D Visualization Tool** (Jan 2026)
- BiomeMapViewer for debugging generation
- Grid toggle, real-time updates
- Color-coded biome display

✅ **Configurable world generation** (Dec 2025)
- JSON-based biome configuration
- Structure placement rules
- Vegetation density and distribution

✅ **Water as separate layer** (Dec 2025)
- WaterLayer in generation pipeline
- Decoupled from terrain height
- Future-ready for rivers, lakes

---

## Completed Features

### Core Architecture ✅

- [x] Clean architecture (app / core / renderer separation)
- [x] Region-based streaming with caching
- [x] Deterministic generation pipeline
- [x] Thread-safe concurrent region building
- [x] Infinite coordinate support
- [x] Safe placeholder system
- [x] Radius-based cache eviction (regions, chunks and meshes evict outside the view radius)

### Terrain Generation ✅

- [x] Multi-octave noise terrain
- [x] Continentalness system (land/ocean)
- [x] Erosion and peaks/valleys
- [x] Island and archipelago generation
- [x] Coastline fragmentation
- [x] Mountain ranges (realistic erosion)
- [x] Ocean depth variants (ocean / deep ocean)

### Biome System ✅

- [x] 9 distinct biomes
- [x] Temperature and moisture-based assignment
- [x] JSON-based biome configuration
- [x] Height-dependent biome rules
- [x] Smooth biome transitions (future improvement)

### Structure System ✅

- [x] Configurable tree placement
- [x] 7 tree types (oak, spruce, jungle, mega jungle, acacia, cactus, snow)
- [x] Vegetation system (grass, flowers, bushes)
- [x] Clustering support
- [x] Clearing generation
- [x] Distribution patterns (scattered, clustered, uniform, patchy)
- [x] Density control per biome

### Rendering ✅

- [x] Greedy chunk meshing
- [x] 3-pass rendering (opaque/cutout/translucent)
- [x] Frustum culling
- [x] Texture atlas system
- [x] Per-face block textures
- [x] Water rendering (lowered surface)
- [x] Billboard vegetation (crossed quads)
- [x] Sky dome rendering
- [x] Day/night cycle (visual)
- [x] Distance fog

### Tools & Features ✅

- [x] Teleport to coordinates (F9)
- [x] Biome search & teleport (F10)
- [x] 2D biome map viewer
- [x] Free-flight camera
- [x] WASD + mouse controls

---

## In Progress

### 📋 World Persistence (NEXT, HIGH PRIORITY)

Block edits, player position, time of day and settings are in-memory
only. Region-file style saves make builds permanent. Prerequisite for
anything survival-shaped.

### 📋 Audio (HIGH PRIORITY)

No sound at all yet: wind, rain, thunder, footsteps, water, ambience.
LWJGL ships OpenAL; weather/biome/cave state can drive the mix.

### 📋 Backlog (smaller items)

- Render-scale supersampling; GPU frustum culling; persistent command rings
- Avatar shadow casting; torch in the third-person avatar hand
- Ravines; lava pools at depth; ore veins
- Rain splash rings; underwater overlay inside flooded caves
- Click-to-type value entry in menu sliders; F3-style debug HUD toggle

---

## Roadmap

### Phase 1: Foundation (DONE ✅)
- [x] Clean architecture
- [x] Streaming system
- [x] Basic terrain generation
- [x] Simple biome system
- [x] Basic rendering

### Phase 2: Content & Quality (DONE ✅)
- [x] Configurable biomes
- [x] Structure variety
- [x] Improved terrain (islands, oceans)
- [x] Performance optimization (GL 4.6 multi-draw-indirect, 250-350 FPS @ r48)
- [x] Caves (worm carvers, entrances, aquifer dams)
- [x] Improved rivers (sea-level valleys, riverbeds)

### Phase 3: Lighting & Atmosphere (DONE ✅ except block light)

**Lighting System**:
- [ ] Minecraft-style per-block light propagation (torch light is a dynamic
      camera-centered point light instead; placed-torch blocks would need this)
- [x] Skylight (per-column ceilings: canopy shade + true cave darkness)
- [x] Shadow maps (3 cascades, 4096px, soft PCF) + SSAO + volumetric shafts

**Atmospheric Effects**:
- [x] Time-of-day lighting (full day/night sunlight cycle)
- [x] Star field at night + moon
- [x] Weather effects (rain, snow, wind, lightning)
- [x] Clouds (3D slab-marched volumetric + terrain cloud shadows)

**Current Status**: Shipped July 2026

---

### Phase 4: Far-Field LOD (DONE ✅)

**Goal**: Distant Horizons–style far rendering

**Design**:
```
LOD Tiles (lower resolution)
  ↓
Heightmap-based meshes
  ↓
Simplified texturing (color-only)
  ↓
Seamless blending with near field
```

**Tasks**:
- [x] LOD tile system (4×4, 16×16 block quads)
- [x] Heightmap mesh generation
- [x] Biome color sampling (no per-block textures)
- [x] Distance-based LOD switching
- [x] Atmospheric perspective (distant fog)
- [x] LOD chunk caching

**Benefits**:
- Render distance: 16 tiles = 4km in every direction
- Performance: Minimal overhead (arena-batched draws)
- Immersion: See mountains from far away

**Current Status**: Shipped — LOD rings to 4km, atlas-derived vertex colors,
LOD terrain casts into the far shadow cascade, dithered near-field dissolve

---

### Phase 5: Gameplay (CURRENT 🚧)

**Block Interaction**:
- [x] Block breaking (left click, raycast + highlight + remesh)
- [x] Block placing (right click, hotbar palette)
- [ ] Inventory system
- [ ] Crafting system

**Survival Mechanics**:
- [ ] Health system
- [ ] Hunger/food
- [ ] Fall damage
- [ ] Respawn system

**Entities**:
- [x] Ambient life (fish, birds, leaves — scenery, not interactive)
- [ ] Entity framework (interactive mobs)
- [ ] Passive/hostile mobs + AI

**Physics**:
- [x] Collision detection (AABB, substepped)
- [x] Gravity + jumping
- [x] Swimming (buoyancy, dive, bank-hop exit)

**Current Status**: Block editing + physics shipped July 2026; inventory,
survival and mobs remain

---

### Phase 6: Persistence (NEXT 📋 — highest priority)

**World Save/Load**:
- [ ] Region serialization (NBT or custom format)
- [ ] Player data (position, inventory)
- [ ] Modified chunks (player-placed blocks)
- [ ] Structure data (chests, signs)

**Multiplayer** (OPTIONAL):
- [ ] Server architecture
- [ ] Client-server protocol
- [ ] Entity synchronization
- [ ] Chunk streaming over network

**Current Status**: Not started

---

## Technical Debt

### Code Quality

**Cleanup Needed**:
- [ ] Remove dead code and unused helpers
- [ ] Consolidate noise parameter classes
- [ ] Better naming consistency
- [ ] More unit tests (especially WorldGrid, coordinate math)
- [ ] Integration tests for generation pipeline

**Documentation**:
- [ ] Javadoc for all public APIs
- [ ] Architecture decision records (ADRs)
- [ ] Code comments for complex algorithms

### Architecture

**Improvements**:
- [ ] Abstract Renderer interface (prepare for UE5 backend)
- [ ] Plugin system for custom biomes/structures
- [ ] Event system for gameplay hooks
- [ ] Better error handling (avoid silent failures)

**Refactoring**:
- [ ] Simplify ChunkBuilder (too many responsibilities)
- [ ] Extract noise configuration to JSON
- [ ] Unified config system (merge EngineConfig, RendererConfig)

---

## Development Priorities

### Immediate (Next 1-2 Months)

1. **Performance optimization** ⭐⭐⭐
   - Profile and fix bottlenecks
   - Async mesh generation
   - Better caching

2. **Caves** ⭐⭐⭐
   - Core feature for exploration
   - High player demand

3. **Cleanup** ⭐⭐
   - Remove dead code
   - Better tests
   - Refactor ChunkBuilder

### Short-Term (3-6 Months)

4. **Lighting** ⭐⭐⭐
   - Essential for atmosphere
   - Enables day/night gameplay

5. **Rivers** ⭐⭐
   - Improve world realism
   - Better biome connectivity

6. **Far-field LOD** ⭐⭐
   - Impressive visual feature
   - Architecture already supports

### Long-Term (6+ Months)

7. **Gameplay mechanics** ⭐⭐⭐
   - Block interaction
   - Survival elements
   - Entities

8. **Save/Load** ⭐⭐
   - Required for persistent worlds
   - Foundation for multiplayer

---

## Community Contributions

### How to Contribute

See [Contributing.md](Contributing.md) for guidelines.

**Wanted**:
- Performance profiling and optimization
- Additional biome types
- Structure variety (buildings, dungeons)
- Shader improvements (water, lighting)
- Bug reports and testing

---

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| **v0.4** | Jan 2026 | Deep ocean, islands, 2D viewer |
| **v0.3** | Dec 2025 | Configurable biomes, water layer |
| **v0.2** | Nov 2025 | Greedy meshing, structure placement |
| **v0.1** | Oct 2025 | Initial architecture, basic terrain |

---

**Last Updated**: 2026-01-26  
**Next Milestone**: Performance optimization + Caves (v0.5)

---

**Next**: [Getting Started →](Getting-Started.md)
