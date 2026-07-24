# Getting Started

This guide will help you build, run, and start exploring Voxel Horizon Java Edition.

## Table of Contents

1. [Requirements](#requirements)
2. [Building from Source](#building-from-source)
3. [Running the Engine](#running-the-engine)
4. [Controls](#controls)
5. [Customization](#customization)
6. [Troubleshooting](#troubleshooting)

---

## Requirements

### System Requirements

**Minimum**:
- **OS**: Windows 10, macOS 11+, or Linux (Ubuntu 20.04+)
- **CPU**: Dual-core 2.0+ GHz
- **GPU**: OpenGL 4.6 compatible (any discrete GPU from ~2015 on)
- **RAM**: 2GB available
- **Disk**: 500MB free space

**Recommended**:
- **OS**: Windows 11, macOS 12+, or Linux (latest)
- **CPU**: Quad-core 2.5+ GHz
- **GPU**: Dedicated graphics card with 2GB+ VRAM
- **RAM**: 8GB available
- **Disk**: 2GB free space (for larger worlds)

### Software Requirements

**Required**:
- **Java 21 or higher** ([Download](https://adoptium.net/))
- **Git** (for cloning repository)

**Optional** (for development):
- **IntelliJ IDEA** or **Eclipse** (IDEs with Gradle support)
- **Gradle** 8.5+ (usually bundled with project)

---

## Building from Source

### Step 1: Clone the Repository

```bash
git clone https://github.com/Aizen93/voxel-horizon-java-edition.git
cd voxel-horizon-java-edition
```

### Step 2: Checkout the Branch

```bash
# For the latest configurable terrain features
git checkout feature/unreal-engine
```

### Step 3: Build the Project

**Linux/macOS**:
```bash
./gradlew build
```

**Windows**:
```cmd
gradlew.bat build
```

This will:
1. Download dependencies (LWJGL, JOML, etc.)
2. Compile all Java source files
3. Run tests (if any)
4. Create JAR files in `build/libs/`

**Expected output**:
```
BUILD SUCCESSFUL in 30s
```

### Step 4: Verify Build

Check that the build artifacts exist:

```bash
ls build/libs/
# Should show: voxel-horizon-java-edition-1.0-SNAPSHOT.jar
```

---

## Running the Engine

### Method 1: Using Gradle (Recommended)

**Run the main application**:

```bash
./gradlew run
```

**Run the 2D biome viewer**:

```bash
./gradlew runBiomeViewer
```

### Method 2: Using JAR (After Building)

```bash
java -jar build/libs/voxel-horizon-java-edition-1.0-SNAPSHOT.jar
```

### Method 3: From IDE

1. Open project in IntelliJ IDEA or Eclipse
2. Import as Gradle project
3. Navigate to `src/main/java/org/aouessar/app/Main.java`
4. Right-click → Run 'Main.main()'

---

## First Launch

### What to Expect

1. **Window opens**: 1280×720 default resolution
2. **World generation begins**: Chunks load around spawn point
3. **You spawn at**: Coordinates near (0, surface_height, 0)
4. **Camera**: Free-flight mode (press G for walking physics)

### Initial Spawn

- **Spawn point**: Near world origin (0, 0) in blocks
- **Spawn height**: Automatically set to terrain surface + 10 blocks
- **Spawn biome**: Random (depends on seed)

---

## Controls

### Movement

| Input | Action |
|-------|--------|
| **W / A / S / D** | Move |
| **Space** | Jump (walk mode) / swim up / ascend (fly) |
| **Left Ctrl** | Dive (water) / descend (fly) |
| **Left Shift** | Sprint / fly speed boost |
| **G** | Toggle physics (walk+gravity+swim) vs free fly (no collision) |
| **Mouse** | Look around |

### Blocks & UI

| Input | Action |
|-------|--------|
| **LMB / RMB** | Break / place blocks (6-block reach, wireframe highlight) |
| **1–9, scroll** | Hotbar selection |
| **ESC** | Pause menu — mouse-driven sliders for live settings, Quit |
| **F5** | First person / third person (back) / third person (front) |
| **C** | Switch avatar (human / elf) |
| **T / H** | Torch light on-off / show-hide torch model |
| **F2** | Screenshot to `./screenshots` |

**Mouse sensitivity** and fly speed are adjustable live in the ESC menu.

### Teleportation

**F9 - Teleport to Coordinates**:
1. Press `F9`
2. Dialog appears with input fields
3. Enter X coordinate (e.g., `1000`)
4. Enter Z coordinate (e.g., `-500`)
5. Click "Teleport" or press Enter
6. You instantly move to (X, surface, Z)

**F10 - Search & Teleport to Biome**:
1. Press `F10`
2. Dialog appears with biome dropdown
3. Select desired biome (e.g., "JUNGLE")
4. Click "Search"
5. Progress bar shows search status
6. Automatically teleports when biome found
7. Pre-warms surrounding regions for smooth experience

**Search Algorithm**:
- Starts from current position
- Searches in expanding spiral (16-chunk radius increments)
- Checks ~100 positions per radius level
- May take 5-30 seconds depending on biome rarity

### Camera

- **FOV**: 75° (default)
- **Near plane**: 0.1 blocks
- **Far plane**: 8000 blocks (LOD horizon + fog)

---

## Customization

### Changing World Seed

**In code** (before building):

Edit `src/main/java/org/aouessar/shared/EngineConfig.java`:

```java
// Single source of truth for the world seed (Main and the 2D
// biome viewer both derive from it):
public static final long WORLD_SEED = 905282311L;
```

Rebuild and run.

### Adjusting Performance

**View Distance** (near-field chunks, no rebuild needed):

```bash
# Default is 48; lower = faster generation/less memory
./gradlew run -Pvoxel.radius=16
```

The far-field LOD always extends ~4km regardless of the near radius.

**Cache / memory**: regions, chunks and meshes evict automatically outside
the view radius — memory scales with `voxel.radius` (roughly 1-5GB heap at
radius 16, up to ~10GB at radius 48). Give the JVM headroom with
`org.gradle.jvmargs` or run the jar with `-Xmx12g` for radius 48.

**Live graphics tuning**: most quality knobs (SSAO, sun shafts, bloom,
shadows, clouds...) are adjustable in-game in the ESC menu — see the
[Configuration Guide](Configuration-Guide.md#runtime-properties--live-settings-july-2026).

**Thread Count** (CPU usage):

Edit `src/main/java/org/aouessar/shared/EngineConfig.java`:

```java
// Default: all cores except one
public static final int CPU_WORKERS = Runtime.getRuntime().availableProcessors() - 1;

// Reduce for lower CPU usage:
public static final int CPU_WORKERS = 2;
```

### Customizing Biomes

See [Configuration Guide](Configuration-Guide.md) for detailed biome customization.

**Quick example** - Edit `src/main/resources/constraints/world_content_v1.json`:

```json
{
  "biomes": {
    "PLAINS": {
      "structures": {
        "trees": {
          "density": 0.30    // Change from 0.15 (doubles trees)
        }
      }
    }
  }
}
```

Rebuild and run to see changes.

---

## Exploration Tips

### Finding Interesting Terrain

**Oceans**:
- Use F10 → Search for "OCEAN" or "DEEP_OCEAN"
- Look for underwater trenches and continental shelves

**Mountains**:
- Search for "SNOW" biome (often mountainous)
- Or manually explore high Y levels (150+)

**Islands**:
- Fly over oceans
- Look for small landmasses surrounded by water

**Dense Forests**:
- Search for "JUNGLE" (densest vegetation)
- Or "FOREST" (spruce trees)

**Deserts**:
- Search for "DESERT"
- Look for cactus clusters

### World Seed Notes

The default seed `905282311` spawns near a lake basin with hills,
beaches, a snow-capped range to the north-east and cave entrances
within a short flight (probe-verified: meadow holes around (40, -440),
mountainside mouths near (-504, -307), flooded ocean entrances east).
Any seed works — generation is fully deterministic per seed.

---

## Troubleshooting

### Build Fails

**Error**: `Could not find Java 21`

**Solution**:
- Install Java 21 from [Adoptium](https://adoptium.net/)
- Set `JAVA_HOME` environment variable
- Verify: `java -version` (should show 21.x.x)

**Error**: `Could not download dependencies`

**Solution**:
- Check internet connection
- Try again: `./gradlew build --refresh-dependencies`
- Clear Gradle cache: `rm -rf ~/.gradle/caches/`

### Window Won't Open

**Error**: `GLFW error: Could not create window`

**Solution**:
- Update graphics drivers
- Check GPU supports OpenGL 4.6 (driver up to date)
- Try on different GPU if you have multiple (dedicated vs. integrated)

**Error**: `No compatible OpenGL context`

**Solution**:
- Your GPU is too old (pre-2010)
- Use software rendering (slow): `export LIBGL_ALWAYS_SOFTWARE=1`

### Poor Performance

**Symptom**: Low FPS (< 30)

**Solutions**:
1. Reduce view distance (edit `RendererConfig.java`)
2. Close other applications (free up RAM/CPU)
3. Lower window resolution (edit `RendererConfig.java`)
4. Disable VSync (future option)

**Symptom**: Stuttering/freezing

**Solutions**:
1. Increase cache size (more RAM = fewer reloads)
2. Reduce thread count if CPU is overheating
3. Check for Java GC pauses: Run with `-XX:+PrintGCDetails`

### Visual Glitches

**Symptom**: Black/missing textures

**Solution**:
- Verify `atlas.png` exists in `src/main/resources/`
- Check console for texture loading errors
- Rebuild project

**Symptom**: Z-fighting (flickering surfaces)

**Solution**:
- This is a known issue with coplanar faces
- Will be fixed in future greedy mesher improvements

**Symptom**: Water rendering incorrectly

**Solution**:
- Ensure translucent pass is enabled
- Check GPU supports blending
- Update graphics drivers

---

## Advanced Usage

### Using the 2D Biome Viewer

**Purpose**: Visualize biome distribution and heightmap

**Launch**:
```bash
./gradlew runBiomeViewer
```

**Controls**:
- **Mouse drag**: Pan view
- **Mouse wheel**: Zoom in/out
- **G key**: Toggle grid overlay
- **Click**: Show coordinates and biome info

**Uses**:
- Debug biome generation
- Find biome boundaries
- Verify terrain shapes
- Plan builds (future)

### Modifying Shaders

**Location**: `src/main/resources/shaders/`

**Files**:
- `chunk.vert` - Vertex shader (position, UV)
- `chunk.frag` - Fragment shader (texturing, fog)
- `sky.vert` / `sky.frag` - Sky dome shaders

**Hot Reload**: Not supported yet (restart required after changes)

---

## Next Steps

1. **Explore the world**: Use F9/F10 to visit different biomes
2. **Read the docs**: Check out [Gameplay Features](Gameplay-Features.md)
3. **Customize**: Edit biome configs, try different seeds
4. **Contribute**: See [Contributing](Contributing.md) to help develop the engine

---

## Getting Help

**Found a bug?**
- Open an issue: [GitHub Issues](https://github.com/Aizen93/voxel-horizon-java-edition/issues)
- Include: OS, Java version, error logs, steps to reproduce

**Have questions?**
- Check existing documentation in `/docs/wiki/`
- Review project status: [Work in Progress](Work-In-Progress.md)

**Want to contribute?**
- Read [Contributing](Contributing.md)
- Fork the repository
- Submit pull requests

---

**Next**: [Configuration Guide →](Configuration-Guide.md)
