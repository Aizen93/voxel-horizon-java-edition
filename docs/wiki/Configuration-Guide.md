# Configuration Guide

This guide explains how to customize world generation, biomes, structures, and other aspects of Voxel Horizon.

## Table of Contents

1. [Overview](#overview)
2. [World Content Configuration](#world-content-configuration)
3. [Biome Configuration](#biome-configuration)
4. [Structure Configuration](#structure-configuration)
5. [Advanced Customization](#advanced-customization)
6. [Examples](#examples)

---

## Overview

Voxel Horizon uses **JSON-based configuration** for world content, allowing you to customize:
- Biome surface blocks
- Tree types and density
- Vegetation types and distribution
- Clustering and clearing behavior
- Global block rules

Configuration is loaded from:
```
src/main/resources/constraints/world_content_v1.json
```

**Schema**: `src/main/resources/constraints/world_content.schema.json`

---

## World Content Configuration

### File Structure

```json
{
  "version": "1.0.0",
  "biomes": {
    "BIOME_NAME": { /* biome config */ }
  },
  "global_blocks": { /* global rules */ }
}
```

### Version

**Required**: Must be `"1.0.0"` (current schema version)

```json
{
  "version": "1.0.0"
}
```

---

## Biome Configuration

Each biome has:
- **Surface blocks**: List of allowed top-layer blocks
- **Structures**: Trees and vegetation rules

### Basic Biome Structure

```json
{
  "biomes": {
    "PLAINS": {
      "surface_blocks": [
        "GRASS",
        "DIRT",
        "GRAVEL"
      ],
      "structures": {
        "trees": { /* tree config */ },
        "vegetation": { /* vegetation config */ }
      }
    }
  }
}
```

### Surface Blocks

**List of block names** that can appear as the top layer in this biome.

**Available blocks**:
- GRASS, DIRT, STONE
- DESERT_SAND, DESERT_SAND_STONE, SAND
- SNOW, SNOW_GRASS, ICE
- DRY_GRASS (savanna)
- PODZOL_DIRT (jungle)
- GRAVEL, CLAY (rivers, swamps)
- WATER (swamps, oceans)

**Example**:
```json
"surface_blocks": ["GRASS", "DIRT", "GRAVEL"]
```

**Behavior**:
- Engine randomly selects from list
- Weighted by noise (more variation)
- Top block placed at terrain surface

---

## Structure Configuration

### Trees

Configure tree generation for a biome.

```json
"trees": {
  "types": ["OAK", "SPRUCE"],
  "density": 0.15,
  "distribution": "SCATTERED",
  "clustering": {
    "enabled": false
  },
  "clearings": {
    "enabled": true,
    "average_radius": 8
  }
}
```

#### Tree Properties

**types** (array of strings):
- List of tree types to generate
- **Available types**:
  - `OAK` - Standard oak tree (3-5 blocks tall)
  - `SPRUCE` - Conical spruce (4-7 blocks tall)
  - `JUNGLE` - Tropical tree (6-10 blocks tall)
  - `MEGA_JUNGLE` - Massive jungle tree (12-20 blocks tall)
  - `ACACIA` - Flat-topped acacia (5-7 blocks tall)
  - `CACTUS` - Desert cactus (1-3 blocks tall, no leaves)
  - `SNOW_TREE` - Snow-covered tree (4-6 blocks tall)

**density** (number, 0.0 - 1.0):
- Probability of tree placement per attempt
- `0.0` = No trees
- `1.0` = Maximum trees (very dense)
- Typical values: `0.05` (sparse) to `0.65` (jungle)

**distribution** (string):
- Controls how trees are spread
- **Options**:
  - `"SCATTERED"` - Random, even distribution
  - `"CLUSTERED"` - Groups of trees
  - `"UNIFORM"` - Grid-like spacing (rare)
  - `"PATCHY"` - Large irregular patches

#### Clustering

**enabled** (boolean):
- `true` = Trees placed in groups
- `false` = Trees placed individually

**cluster_size** (array of 2 integers):
- `[min, max]` trees per cluster
- Example: `[3, 7]` = 3-7 trees per cluster

**Example**:
```json
"clustering": {
  "enabled": true,
  "cluster_size": [5, 12]
}
```

#### Clearings

**enabled** (boolean):
- `true` = Create tree-free zones
- `false` = No clearings

**average_radius** (number):
- Radius of clearing in blocks
- Typical values: `8` (small) to `12` (large)

**Example**:
```json
"clearings": {
  "enabled": true,
  "average_radius": 10
}
```

---

### Vegetation

Configure ground vegetation (grass, flowers, bushes).

```json
"vegetation": {
  "types": ["TALL_GRASS", "FLOWER_RED", "FLOWER_YELLOW"],
  "density": 0.6,
  "distribution": "UNIFORM",
  "clustering": {
    "enabled": false
  }
}
```

#### Vegetation Properties

**types** (array of strings):
- List of vegetation types to generate
- **Available types**:
  - `TALL_GRASS` - Standard grass
  - `FLOWER_RED` - Red flower
  - `FLOWER_YELLOW` - Yellow flower
  - `FLOWER_CORNFLOWER` - Blue cornflower
  - `FLOWER_OXEYE_DAISY` - White daisy
  - `FLOWER_HOUSTONIA` - Small white flower
  - `FLOWER_RED_DOUBLE` - Tall red flower
  - `BUSH` - Small bush
  - `BERRY_BUSH` - Berry bush (future food)
  - `DRY_WHEAT` - Dry grass (savanna)
  - `VINE` - Hanging vine

**density** (number, 0.0 - 1.0):
- Coverage of vegetation
- `0.0` = No vegetation
- `1.0` = Maximum coverage
- Typical values: `0.4` (sparse) to `1.0` (jungle undergrowth)

**distribution** (string):
- Same options as trees: `SCATTERED`, `CLUSTERED`, `UNIFORM`, `PATCHY`

**clustering**:
- Same as trees (enabled, cluster_size)

---

## Global Blocks

**Not yet implemented** - Reserved for future features.

Planned usage:
```json
"global_blocks": {
  "BEDROCK": "bottom_of_world_only",
  "DEEPSLATE": "deep_underground_only",
  "STONE": "base_terrain_everywhere",
  "WATER": "oceans_rivers_lakes"
}
```

---

## Advanced Customization

### Adding a New Biome

**Step 1**: Define biome ID in code

Edit `src/main/java/org/aouessar/core/world/Biomes.java`:
```java
public static final int MY_CUSTOM_BIOME = 9;
```

**Step 2**: Add to biome generator logic

Edit `src/main/java/org/aouessar/core/gen/impl/DefaultBiomeGenerator.java`:
```java
// Add conditions for your biome
if (temperature < -0.5 && moisture > 0.3) {
    biomeMap.set(wx, wz, (short) Biomes.MY_CUSTOM_BIOME);
}
```

**Step 3**: Add configuration to JSON

Edit `world_content_v1.json`:
```json
{
  "biomes": {
    "MY_CUSTOM_BIOME": {
      "surface_blocks": ["CUSTOM_BLOCK"],
      "structures": {
        "trees": { /* ... */ },
        "vegetation": { /* ... */ }
      }
    }
  }
}
```

**Step 4**: Rebuild and test

```bash
./gradlew build
./gradlew run
```

---

## Examples

### Example 1: Dense Forest

```json
{
  "biomes": {
    "FOREST": {
      "surface_blocks": ["DIRT"],
      "structures": {
        "trees": {
          "types": ["SPRUCE"],
          "density": 0.60,
          "distribution": "CLUSTERED",
          "clustering": {
            "enabled": true,
            "cluster_size": [10, 20]
          },
          "clearings": {
            "enabled": true,
            "average_radius": 15
          }
        },
        "vegetation": {
          "types": ["BERRY_BUSH", "FLOWER_OXEYE_DAISY", "TALL_GRASS"],
          "density": 0.9,
          "distribution": "PATCHY",
          "clustering": {
            "enabled": true,
            "cluster_size": [15, 30]
          }
        }
      }
    }
  }
}
```

**Effect**:
- Very dense spruce trees
- Large clusters (10-20 trees)
- Big clearings (15-block radius)
- Dense undergrowth in patches

---

### Example 2: Sparse Desert

```json
{
  "biomes": {
    "DESERT": {
      "surface_blocks": ["DESERT_SAND", "DESERT_SAND_STONE"],
      "structures": {
        "trees": {
          "types": ["CACTUS"],
          "density": 0.02,
          "distribution": "SCATTERED",
          "clustering": {
            "enabled": true,
            "cluster_size": [1, 3]
          },
          "clearings": {
            "enabled": false
          }
        },
        "vegetation": {
          "types": [],
          "density": 0.0,
          "distribution": "NONE"
        }
      }
    }
  }
}
```

**Effect**:
- Very sparse cacti (2% density)
- Small clusters (1-3 cacti)
- No other vegetation
- Barren landscape

---

### Example 3: Flower Meadow

```json
{
  "biomes": {
    "PLAINS": {
      "surface_blocks": ["GRASS"],
      "structures": {
        "trees": {
          "types": ["OAK"],
          "density": 0.05,
          "distribution": "SCATTERED",
          "clustering": {
            "enabled": false
          },
          "clearings": {
            "enabled": false
          }
        },
        "vegetation": {
          "types": [
            "FLOWER_RED",
            "FLOWER_YELLOW",
            "FLOWER_CORNFLOWER",
            "FLOWER_OXEYE_DAISY",
            "TALL_GRASS"
          ],
          "density": 0.95,
          "distribution": "UNIFORM",
          "clustering": {
            "enabled": false
          }
        }
      }
    }
  }
}
```

**Effect**:
- Very few trees (5% density)
- Abundant flowers (95% density)
- Uniform spread (meadow look)
- Multiple flower colors

---

### Example 4: Jungle

```json
{
  "biomes": {
    "JUNGLE": {
      "surface_blocks": ["PODZOL_DIRT"],
      "structures": {
        "trees": {
          "types": ["JUNGLE", "MEGA_JUNGLE"],
          "density": 0.70,
          "distribution": "CLUSTERED",
          "clustering": {
            "enabled": true,
            "cluster_size": [12, 25]
          },
          "clearings": {
            "enabled": false
          }
        },
        "vegetation": {
          "types": [
            "BUSH",
            "FLOWER_HOUSTONIA",
            "FLOWER_RED_DOUBLE",
            "TALL_GRASS",
            "VINE"
          ],
          "density": 1.0,
          "distribution": "PATCHY",
          "clustering": {
            "enabled": true,
            "cluster_size": [10, 30]
          }
        }
      }
    }
  }
}
```

**Effect**:
- Extremely dense trees (70% density)
- Massive clusters (12-25 trees)
- Mix of regular and mega jungle trees
- 100% vegetation coverage
- Impenetrable undergrowth

---

## Testing Your Configuration

### Step 1: Edit JSON

Modify `src/main/resources/constraints/world_content_v1.json`

### Step 2: Rebuild

```bash
./gradlew build
```

### Step 3: Run and Teleport

```bash
./gradlew run
```

Press `F10` → Select your biome → Teleport

### Step 4: Use 2D Viewer (Optional)

```bash
./gradlew runViewer
```

Visualize biome distribution before exploring in 3D.

---

## Troubleshooting

### Config Not Loading

**Symptom**: Changes don't appear in game

**Solutions**:
1. Verify JSON syntax (use JSON validator)
2. Rebuild project (`./gradlew clean build`)
3. Check console for loading errors
4. Ensure file is in `src/main/resources/constraints/`

### Invalid Block Names

**Symptom**: Error during loading or missing blocks

**Solution**:
- Check block names against `Blocks.java`
- Use exact names (case-sensitive)
- Refer to [Technical Reference](Technical-Reference.md) for valid IDs

### Trees Not Appearing

**Possible causes**:
- Density too low (try `0.3` for testing)
- Wrong biome (teleport to correct biome)
- Clearings too large (disable clearings for testing)

### Performance Issues

**If too many structures**:
- Reduce `density` (especially for vegetation)
- Disable `clustering` (reduces spawn attempts)
- Reduce `cluster_size`

---

## Schema Reference

Full JSON schema: `src/main/resources/constraints/world_content.schema.json`

**Top-level fields**:
- `version` (string, required): `"1.0.0"`
- `biomes` (object, required): Map of biome name → BiomeConfig
- `global_blocks` (object, optional): Future use

**BiomeConfig fields**:
- `surface_blocks` (array of strings, required)
- `structures` (object, required): StructuresConfig

**StructuresConfig fields**:
- `trees` (object, optional): TreesConfig
- `vegetation` (object, optional): VegetationConfig

**TreesConfig/VegetationConfig fields**:
- `types` (array of strings, required)
- `density` (number, required, 0.0-1.0)
- `distribution` (string, required): SCATTERED | CLUSTERED | UNIFORM | PATCHY
- `clustering` (object, optional): ClusteringConfig
- `clearings` (object, optional, trees only): ClearingsConfig

**ClusteringConfig fields**:
- `enabled` (boolean, required)
- `cluster_size` (array of 2 integers, optional): [min, max]

**ClearingsConfig fields**:
- `enabled` (boolean, required)
- `average_radius` (number, optional)

---

**Next**: [Contributing →](Contributing.md)
