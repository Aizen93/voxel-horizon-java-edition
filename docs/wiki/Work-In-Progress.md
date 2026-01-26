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

**Phase**: Foundation + Content Expansion  
**Branch**: `terrain-generation-configurable`  
**Focus**: Configurable biome system, terrain quality, performance optimization

### Recent Achievements

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
- [x] LRU cache eviction (Caffeine)

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

### 🚧 Performance Optimization (HIGH PRIORITY)

**Goal**: Improve frame rates and reduce memory usage

**Tasks**:
- [ ] Profiling with JProfiler/VisualVM
- [ ] Mesh batching improvements
- [ ] Async chunk meshing (off main thread)
- [ ] Better cache eviction strategies
- [ ] Reduce placeholder generation overhead
- [ ] GPU memory management

**Current Status**: Initial profiling done, optimization planned

---

### 🚧 Caves (MAJOR FEATURE)

**Goal**: Add true 3D underground cave systems

**Design**:
```
CaveVolume (per region)
  ↓
3D noise field (cellular/worm caves)
  ↓
Per-voxel carving decision
  ↓
Integrated with ChunkBuilder
```

**Tasks**:
- [ ] Design CaveVolume layer (3D array or sparse structure)
- [ ] Implement 3D cave noise (cellular + worm caves)
- [ ] Region-paged cave volumes
- [ ] ChunkBuilder integration
- [ ] Cave biome variants (lush caves, dripstone, etc.)
- [ ] Cave structure placement (ore veins, mushrooms)

**Challenges**:
- Memory: 3D arrays are expensive (256×384×256 = 25M values/region)
- Solution: Sparse voxel octrees or run-length encoding
- Determinism: Must be reproducible from seed

**Current Status**: Design phase, prototyping 3D noise

---

### 🚧 Rivers (UPGRADE)

**Goal**: Coherent river networks that flow to oceans

**Design**:
```
Drainage Basin Analysis
  ↓
River Network Graph
  ↓
Per-column water level
  ↓
Carving + WaterLayer update
```

**Tasks**:
- [ ] Hydraulic erosion simulation (simplified)
- [ ] River network coherence (tributaries)
- [ ] Ocean termination guarantee
- [ ] Biome-aware rivers (swamp rivers are wider)
- [ ] River depth variation
- [ ] Waterfall detection and rendering

**Challenges**:
- Cross-region coherence (rivers span multiple regions)
- Performance (pathfinding to ocean is expensive)
- Determinism (same seed = same rivers)

**Current Status**: Research phase, algorithm selection

---

## Roadmap

### Phase 1: Foundation (DONE ✅)
- [x] Clean architecture
- [x] Streaming system
- [x] Basic terrain generation
- [x] Simple biome system
- [x] Basic rendering

### Phase 2: Content & Quality (CURRENT 🚧)
- [x] Configurable biomes
- [x] Structure variety
- [x] Improved terrain (islands, oceans)
- [ ] Performance optimization
- [ ] Caves
- [ ] Improved rivers

### Phase 3: Lighting & Atmosphere (NEXT 📋)

**Lighting System**:
- [ ] Minecraft-style light propagation
- [ ] Skylight (sunlight from above)
- [ ] Block light (torches, lava, etc.)
- [ ] Shadow maps (optional, advanced)

**Atmospheric Effects**:
- [ ] Time-of-day lighting (sun position)
- [ ] Star field at night
- [ ] Moon phases
- [ ] Weather effects (rain, snow)
- [ ] Clouds (2D billboard or 3D volumetric)

**Current Status**: Not started

---

### Phase 4: Far-Field LOD (PLANNED 📋)

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
- [ ] LOD tile system (4×4, 16×16 block quads)
- [ ] Heightmap mesh generation
- [ ] Biome color sampling (no per-block textures)
- [ ] Distance-based LOD switching
- [ ] Atmospheric perspective (distant fog)
- [ ] LOD chunk caching

**Benefits**:
- Render distance: 64+ chunks
- Performance: Minimal overhead
- Immersion: See mountains from far away

**Current Status**: Architecture is ready, not yet implemented

---

### Phase 5: Gameplay (FUTURE 📋)

**Block Interaction**:
- [ ] Block breaking (left click)
- [ ] Block placing (right click)
- [ ] Inventory system
- [ ] Crafting system

**Survival Mechanics**:
- [ ] Health system
- [ ] Hunger/food
- [ ] Fall damage
- [ ] Respawn system

**Entities**:
- [ ] Entity framework
- [ ] Passive mobs (animals)
- [ ] Hostile mobs
- [ ] Entity AI

**Physics**:
- [ ] Collision detection
- [ ] Gravity
- [ ] Jumping
- [ ] Swimming

**Current Status**: Not started (foundation needed first)

---

### Phase 6: Persistence (FUTURE 📋)

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
