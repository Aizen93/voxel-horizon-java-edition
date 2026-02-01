# Contributing

Thank you for your interest in contributing to Voxel Horizon Java Edition! This guide will help you get started.

## Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Workflow](#development-workflow)
4. [Coding Standards](#coding-standards)
5. [Architecture Guidelines](#architecture-guidelines)
6. [Testing](#testing)
7. [Pull Request Process](#pull-request-process)
8. [Areas Needing Help](#areas-needing-help)

---

## Code of Conduct

### Our Pledge

We are committed to providing a welcoming and inclusive environment for all contributors, regardless of:
- Experience level
- Background
- Identity

### Expected Behavior

✅ **Do**:
- Be respectful and constructive
- Welcome newcomers
- Focus on technical merit
- Give credit where due
- Ask questions when unsure

❌ **Don't**:
- Use offensive language
- Harass or belittle others
- Submit malicious code
- Ignore maintainer feedback

---

## Getting Started

### Prerequisites

**Required**:
- Java 21 or higher
- Git
- Basic understanding of:
  - Java programming
  - Gradle build system
  - OpenGL (for rendering contributions)
  - Procedural generation (for world gen contributions)

**Recommended**:
- IntelliJ IDEA or Eclipse
- Familiarity with Minecraft-like games
- Experience with LWJGL or similar graphics libraries

### Fork and Clone

1. **Fork** the repository on GitHub
2. **Clone** your fork locally:

```bash
git clone https://github.com/YOUR_USERNAME/voxel-horizon-java-edition.git
cd voxel-horizon-java-edition
```

3. **Add upstream** remote:

```bash
git remote add upstream https://github.com/Aizen93/voxel-horizon-java-edition.git
```

4. **Checkout the development branch**:

```bash
git checkout terrain-generation-configurable
```

### Build and Test

```bash
./gradlew build
./gradlew run
```

Verify the engine runs without errors.

---

## Development Workflow

### 1. Create a Feature Branch

```bash
git checkout -b feature/my-awesome-feature
```

**Branch naming**:
- `feature/` - New features
- `bugfix/` - Bug fixes
- `refactor/` - Code improvements
- `docs/` - Documentation updates

### 2. Make Changes

- Follow [Coding Standards](#coding-standards)
- Write clean, readable code
- Add comments for complex logic
- Update documentation if needed

### 3. Test Locally

```bash
# Build
./gradlew build

# Run
./gradlew run

# Run tests (if applicable)
./gradlew test
```

### 4. Commit Changes

**Good commit messages**:
```
Add cave generation using 3D noise

- Implement CaveVolume layer
- Add cellular noise for cave shapes
- Integrate with ChunkBuilder
- Add configuration options

Closes #42
```

**Bad commit messages**:
```
fix stuff
update code
wip
```

### 5. Push to Your Fork

```bash
git push origin feature/my-awesome-feature
```

### 6. Create Pull Request

- Go to GitHub
- Click "New Pull Request"
- Select your branch
- Fill out the PR template (if provided)
- Submit for review

---

## Coding Standards

### Java Style

**Follow standard Java conventions**:

✅ **Good**:
```java
public class ChunkBuilder {
    private final RegionLayers layers;
    
    public ChunkBuilder(RegionLayers layers) {
        this.layers = layers;
    }
    
    /**
     * Builds a chunk at the given coordinates.
     *
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return Completed chunk
     */
    public Chunk buildChunk(int cx, int cz) {
        // Implementation
    }
}
```

❌ **Bad**:
```java
public class chunkbuilder {
    RegionLayers l;
    
    public chunkbuilder(RegionLayers L) { this.l = L; }
    
    public Chunk build(int x, int z) {
        // No documentation
    }
}
```

### Naming Conventions

**Classes**: `PascalCase`
```java
public class RegionStreamingService { }
```

**Methods**: `camelCase`
```java
public Chunk getChunk(int cx, int cz) { }
```

**Constants**: `UPPER_SNAKE_CASE`
```java
public static final int CHUNK_SIZE = 16;
```

**Private fields**: `camelCase`
```java
private final ChunkProvider provider;
```

### Documentation

**Javadoc for public APIs**:

```java
/**
 * Generates a heightmap for the given region.
 *
 * @param seed World generation seed
 * @param rect Spatial bounds of the region
 * @return Heightmap layer containing elevation data
 */
Heightmap generateHeightmap(long seed, LayerRect rect);
```

**Inline comments for complex logic**:

```java
// Use floorDiv to handle negative coordinates correctly
int chunkX = Math.floorDiv(worldX, CHUNK_SIZE);

// Expand quad width (greedy meshing along Z axis)
while (z + width < depth && mask[y][z + width] == blockId) {
    width++;
}
```

### Code Organization

**Package structure**:
- Keep related classes together
- Follow existing package layout (app, core, renderer, shared)
- Don't create new top-level packages without discussion

**Class size**:
- Aim for < 500 lines per class
- Split large classes into logical components
- Use composition over inheritance

---

## Architecture Guidelines

### Core Principles (Non-Negotiable)

1. **NO OpenGL in core module**
   - All rendering code stays in `renderer/`
   - Core should never import `org.lwjgl.*`

2. **NO global state**
   - Avoid static mutable fields
   - Use dependency injection
   - Pass dependencies through constructors

3. **Deterministic generation**
   - Generation functions must be pure
   - Same seed + coordinates = same output
   - No randomness without seeding

4. **Safe fallbacks**
   - Never throw on missing data
   - Return placeholders if not ready
   - Log warnings, don't crash

### Module Boundaries

**app/** → Can depend on: core, renderer  
**core/** → Can depend on: shared, external libraries (noise, math)  
**renderer/** → Can depend on: core (interfaces only), LWJGL, shared  
**shared/** → No dependencies (only constants)

**Forbidden**:
- `core` depending on `renderer` ❌
- `renderer` depending on `core.impl` classes ❌
- Circular dependencies ❌

### Interface Design

**Public interfaces should be minimal**:

✅ **Good**:
```java
public interface ChunkProvider {
    Chunk getChunk(int cx, int cz);
}
```

❌ **Bad**:
```java
public interface ChunkProvider {
    Chunk getChunk(int cx, int cz);
    void preloadChunk(int cx, int cz);
    void invalidateCache();
    int getCacheSize();
    void setMaxCacheSize(int size);
    // Too many methods!
}
```

**Reason**: Interfaces should be focused and hard to misuse.

---

## Testing

### Unit Tests

**Location**: `src/test/java/`

**Example**:
```java
@Test
void testWorldGridBlockToChunk() {
    // Positive coordinates
    assertEquals(0, WorldGrid.blockToChunk(0));
    assertEquals(0, WorldGrid.blockToChunk(15));
    assertEquals(1, WorldGrid.blockToChunk(16));
    
    // Negative coordinates
    assertEquals(-1, WorldGrid.blockToChunk(-1));
    assertEquals(-1, WorldGrid.blockToChunk(-16));
    assertEquals(-2, WorldGrid.blockToChunk(-17));
}
```

**What to test**:
- Coordinate transforms (critical!)
- Generation determinism (same seed = same output)
- Edge cases (negative coords, boundaries)
- Public API contracts

### Manual Testing

**Before submitting PR**:
1. Build and run the engine
2. Verify your feature works as expected
3. Test edge cases (e.g., negative coordinates)
4. Check for visual glitches (rendering changes)
5. Test performance (no major slowdowns)

**Use teleport to test specific scenarios**:
```
F9 → Go to (0, 0)       # Test origin
F9 → Go to (-1000, -1000)  # Test negative coords
F10 → Search JUNGLE     # Test biome changes
```

---

## Pull Request Process

### Before Submitting

**Checklist**:
- [ ] Code follows style guidelines
- [ ] All tests pass (`./gradlew test`)
- [ ] Engine builds without errors
- [ ] Engine runs without crashes
- [ ] Documentation updated (if needed)
- [ ] Commit messages are clear
- [ ] Branch is up-to-date with upstream

**Update your branch**:
```bash
git fetch upstream
git rebase upstream/terrain-generation-configurable
```

### PR Title Format

**Good**:
- `Add 3D cave generation system`
- `Fix negative coordinate handling in WorldGrid`
- `Improve greedy mesher performance by 30%`
- `Update biome documentation with new types`

**Bad**:
- `Update`
- `Fix bug`
- `New feature`

### PR Description

**Template**:
```markdown
## Summary
Brief description of the change.

## Motivation
Why is this change needed?

## Changes
- Change 1
- Change 2
- Change 3

## Testing
How did you test this?

## Screenshots (if visual)
[Attach images]

## Closes
Fixes #issue_number
```

### Review Process

1. **Maintainer reviews** your PR
2. **Feedback provided** (if needed)
3. **You address** feedback
4. **Approval** once ready
5. **Merge** into main branch

**Be patient**: Reviews may take a few days.

---

## Areas Needing Help

### High Priority 🔴

**Performance Optimization**:
- Profile the engine (JProfiler, VisualVM)
- Optimize hot paths
- Reduce memory allocations
- Async mesh generation

**3D Caves**:
- Design CaveVolume data structure
- Implement 3D noise (cellular + worm caves)
- Integrate with ChunkBuilder
- Add cave biomes

**Lighting System**:
- Minecraft-style light propagation
- Skylight + block light
- Shadow maps (optional)

### Medium Priority 🟡

**River Improvements**:
- Coherent river networks
- Hydraulic erosion simulation
- Ocean termination guarantee

**Structure Variety**:
- More tree types
- Buildings (houses, towers)
- Dungeons
- Rare structures (temples, etc.)

**Far-Field LOD**:
- Heightmap-based LOD tiles
- Distance-based switching
- Seamless blending with near field

### Low Priority 🟢

**Quality of Life**:
- Better configuration UI
- In-game settings menu
- Screenshot hotkey
- Seed input dialog

**Documentation**:
- More code comments
- Tutorial videos
- Wiki improvements
- Example configurations

**Testing**:
- More unit tests
- Integration tests
- Performance benchmarks

---

## Communication

### Where to Discuss

**GitHub Issues**:
- Bug reports
- Feature requests
- Design discussions

**Pull Requests**:
- Code review
- Implementation details

**Discussions** (if enabled):
- General questions
- Ideas and brainstorming

### Getting Help

**Stuck? Ask!**
- Comment on the issue/PR
- Provide context:
  - What you're trying to do
  - What you've tried
  - Error messages (if any)

**Good question**:
> "I'm implementing 3D caves and need to store a sparse voxel volume per region. Should I use a 3D array (memory-heavy) or an octree (complex)? What's the project's preference?"

**Bad question**:
> "How do I code?"

---

## Recognition

**Contributors are credited**:
- In commit messages (Co-authored-by)
- In release notes
- In project README (future)

**Your contributions matter** - thank you for helping make Voxel Horizon better! 🎉

---

## License

By contributing, you agree that your contributions will be licensed under the same license as the project.

---

**Questions?** Open an issue or discussion on GitHub!

---

**Happy Coding!** 🚀
