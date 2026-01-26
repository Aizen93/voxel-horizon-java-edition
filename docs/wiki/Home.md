# Voxel Horizon Java Edition - Wiki

Welcome to the **Voxel Horizon Java Edition** project wiki! This is a comprehensive documentation for a production-grade Minecraft-like voxel engine built with Java 21 and LWJGL (OpenGL).

## Project Overview

**Voxel Horizon** is a voxel-based game engine designed with clean architecture principles from day one. The engine features:

- ✅ **Infinite world coordinates** with region-based streaming
- ✅ **Deterministic generation** (seed + coordinates only)
- ✅ **Production-grade architecture** with strict separation of concerns
- ✅ **Configurable biome system** with JSON-based content definition
- ✅ **Advanced terrain generation** with islands, oceans, mountains, and coastlines
- ✅ **Multi-pass rendering** with greedy meshing optimization
- ✅ **Far-field LOD ready** architecture (Distant Horizons style)
- ✅ **Future UE5 compatible** (engine-agnostic world data)

## Quick Links

### For Developers
- **[Architecture](Architecture.md)** - System design, modules, and clean architecture principles
- **[Interfaces](Interfaces.md)** - Core interfaces and their contracts
- **[Implementation](Implementation.md)** - Detailed implementation guide
- **[Technical Reference](Technical-Reference.md)** - Data structures, constants, and coordinate systems

### For Users
- **[Getting Started](Getting-Started.md)** - Build, run, and basic controls
- **[Gameplay Features](Gameplay-Features.md)** - Current features and gameplay mechanics
- **[Configuration Guide](Configuration-Guide.md)** - Customizing world generation and biomes

### For Contributors
- **[Work in Progress](Work-In-Progress.md)** - Current development status and roadmap
- **[Contributing](Contributing.md)** - How to contribute to the project

## Project Philosophy

This engine is built as a **data-pipeline + streaming system**, not a fixed world window. Key principles:

1. **No Global State** - All generation is pure and deterministic
2. **Region-Based Paging** - Data is generated per region and cached
3. **Renderer Independence** - Renderer can request any chunk at any time
4. **Safe Fallbacks** - Core always answers without throwing bounds exceptions
5. **Separation of Concerns** - NO OpenGL code outside renderer module

## Technology Stack

- **Language**: Java 21
- **Graphics**: LWJGL 3 (OpenGL 3.3+)
- **Build System**: Gradle with Kotlin DSL
- **Noise Library**: FastNoiseLite (OpenSimplex2)
- **Math Library**: JOML (Java OpenGL Math Library)

## Module Structure

```
voxel-horizon-java-edition/
├── app/          # Entry point, wires core ↔ renderer
├── core/         # World generation, streaming, data model (NO rendering)
├── renderer/     # LWJGL implementation (replaceable)
└── shared/       # Cross-module constants
```

## Current Status

The engine is in **active development** with a stable foundation:

- ✅ **Core architecture** - Complete and production-ready
- ✅ **Terrain generation** - Configurable, multi-biome system
- ✅ **Rendering pipeline** - Greedy meshing, 3-pass rendering, frustum culling
- ✅ **Streaming system** - Thread-safe region caching with deduplication
- 🚧 **Advanced features** - Caves, improved rivers, lighting (in progress)
- 📋 **Future work** - Far-field LOD, save/load, physics

## Screenshots

*Note: Add screenshots showing the terrain, biomes, and rendering quality*

## Community

- **GitHub**: [Aizen93/voxel-horizon-java-edition](https://github.com/Aizen93/voxel-horizon-java-edition)
- **Issues**: [Report bugs or request features](https://github.com/Aizen93/voxel-horizon-java-edition/issues)

## License

*Add license information here*

---

**Last Updated**: 2026-01-26  
**Version**: Terrain-Generation-Configurable Branch
