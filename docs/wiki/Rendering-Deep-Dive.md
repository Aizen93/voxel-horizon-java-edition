# Rendering Deep-Dive

How Voxel Horizon draws a frame: a modern OpenGL 4.6 forward renderer with an
HDR post stack, built incrementally and verified in-game at every step. This
page is the detailed tour; [Architecture](Architecture.md) has the summary.

## Frame Anatomy

```
SHADOW PASS      3 cascades (130/420/1300 blocks, 4096px each, texel-snapped)
                 near chunks + cutout; LOD terrain casts into the far cascade
      ▼
WORLD PASS       into 4x MSAA RGBA16F: sky (volumetric clouds) → opaque chunks
(jittered proj)  + avatar + fish [multi-draw-indirect] → LOD terrain → cutout
      │          ── mid-frame resolve: sceneColor + sceneDepth ──
      ▼          LOD water → translucent water (refraction/SSR reads resolve)
                 → rain/snow/leaves/birds/lightning → torch viewmodel
      ▼
POST             resolve → TAA → volumetric sun shafts → SSAO → bright/bloom
                 → god rays → ACES composite (+lightning flash, underwater FX)
      ▼
UI               debug HUD, crosshair, hotbar, pause menu (screen-space quads)
```

## Draw Submission: Multi-Draw-Indirect Arenas

`GlMeshArena` replaces per-chunk VAO/VBO/EBOs with a few large shared
buffers. Regions are allocated from offset-sorted free lists (merge-on-free);
each draw pass queues visible regions as 20-byte indirect commands and issues
**one `glMultiDrawElementsIndirect` per arena**. Command order is preserved,
so the distance-sorted translucent pass and the shadow pass radius filters
work unchanged. Two layouts: near-field tiled (8 floats: pos, tileMin,
uvLocal, shade) and LOD (9 floats: pos, color, normal).

Why it matters: at radius 48 the old path issued ~6,400 driver calls per
frame and ran ~90-140 FPS; the arena path issues single digits and runs
250-350 FPS. Frame cost no longer scales with chunk count.

## Lighting Model

Per-fragment terrain light is a product of independent terms:

```
color = albedo × mcShade(normal)          face shading (MC style)
               × vShade                   baked per-vertex AO × skylight
               × shadowMul(sunShadow)     cascaded shadow maps + cloud shadows
               × uSunLight                day/night sunlight color (FogCycle)
       + albedo × torch(dist, N·L)        handheld torch point light
```

- **Baked skylight** (mesher): two per-column ceilings — *any blocker*
  (canopy shade, floored at 0.50) and *topmost opaque* (cave depth, down to
  0.12). Caves go dark without any light propagation system.
- **Day/night sunlight** (`FogCycle`): white noon → warm twilight → blue
  moonlight at `NIGHT_LIGHT_FLOOR`; storms mute it. Multiplies every world
  shader via `uSunLight`.
- **SSAO** adds screen-space contact shadows at composite time.

## Shadows

Three 4096px cascades selected per-fragment by XZ distance, per-cascade
texel snapping and depth bias, far-edge fade. The near cascade uses
**adaptive soft PCF**: a wide 5-tap probe classifies the pixel (fully lit /
fully shadowed / penumbra — the first two early-out cheaper than plain PCF),
then a hash-rotated 12-tap Poisson disk widens mid-penumbra (1.2→5.5 texels).
TAA integrates the rotation noise into smooth gradients.

**Driver pitfall (load-bearing)**: passing a `sampler2DShadow` as a GLSL
function parameter silently disables hardware depth compare on some drivers.
All cascade sampling is inlined per map — keep it that way.

## Water v3

Translucent pass reads the mid-frame opaque resolve:
- **Refraction**: wavy screen-space offset with depth rejection
- **Absorption**: Beer's law by real water travel distance; in-scatter
- **Reflection**: screen-space ray march with sky-gradient fallback
- **Surface**: 6-band layered ripple normals, sun glitter, shore foam
- **Underwater**: dense exp fog, animated caustics on submerged terrain,
  Snell's window looking up, composite wobble + vignette
- **Cave awareness**: water faces carry baked skylight (side faces in vShade,
  top faces as `1 + light`); sky reflection/sun glitter/scatter/foam scale by
  it, so underground water is dark instead of mirroring a sky it can't see

## Post Stack

| Pass | Notes |
|------|-------|
| **TAA** | Halton(2,3) subpixel jitter on the projection; history reprojected via depth + previous view-proj; 3×3 neighborhood clamp; blend 0.12 |
| **Volumetric shafts** | Half-res march from camera toward each pixel's depth, sampling the shadow cascades inline (24 dithered steps, forward-scattering phase). Real crepuscular rays; storm/night/underwater aware |
| **SSAO** | Half-res; view position from depth, normals from derivatives (no G-buffer), 12 spiral taps, Gaussian blur, sky masked |
| **Bloom** | Bright pass (threshold 1.0) + separable Gaussian at half-res |
| **God rays** | Radial march toward sun UV with border fade + aspect-correct falloff (screen-space; complements the volumetrics) |
| **Composite** | ACES filmic tonemap, exposure, lightning flash, underwater tint/wobble/vignette |

## Weather & Ambient Rendering

`WeatherSystem` (seed-deterministic storm slots, wind, lightning events)
drives: fog greying, sunlight muting, cloud-cover boost (sky + terrain cloud
shadows), and `AmbientEffects` — one dynamic-VBO pass drawing wind-slanted
rain streaks, swaying snow, tumbling leaves (spotted on real leaf blocks),
3D birds (articulated flapping wings, glide phases) and the lightning bolt
(HDR-bright, bloom does the glow). Fish are 3D box models drawn **with the
opaque pass** so the water surface refracts them correctly. Precipitation
dies at each column's surface — it never rains in caves.

## Characters & Viewmodels

`PlayerModel` (third person) and `TorchHand` (first person) are CPU-built
colored-box meshes re-uploaded per frame for animation, drawn into the HDR
scene (the torch flame is HDR → bloom glow). The avatar is lit by
`sunlight × cave-skylight probe + torch warmth`. The orbit camera raycasts
terrain and snaps in so it never clips.

## UI

`UiOverlay` batches screen-space pixel quads (crosshair, atlas-icon hotbar,
menu panels) in one draw; `PauseMenu` is an immediate-mode GUI (hover/drag
hit-testing per frame) with labels centered via `stb_easy_font` metrics.
**Note**: the pixel→NDC y-flip reverses winding — cull must be disabled.

## Performance Snapshot (RTX-class GPU, 720p)

| Scene | FPS |
|-------|-----|
| Radius 16, full stack | 400-1200 |
| Radius 48, 9,400 meshes resident, vista | 250-350 |
| Cave + torch + SSAO, radius 48 | ~350 |

## Tuning

Live (ESC menu): SSAO, sun shafts, bloom, exposure, shadow strength, cloud
cover, torch, night brightness, day length, TAA. Startup: `-Pvoxel.radius`,
`-Pvoxel.taa=false`, and the rest of the `voxel.*` properties — see the
[Configuration Guide](Configuration-Guide.md).

## Known Engineering Notes

- `org.joml.Math.clamp` is **(min, max, value)** — the wrong order silently
  becomes `max(value, 0)` and once exploded the day/night lighting.
- `GlShaderProgram.setUniform1i/2f` **throw** when a uniform is optimized
  away — never set a uniform a shader edit just dead-coded.
- TAA + screen-fixed geometry (viewmodels) relies on the neighborhood clamp;
  world-space geometry under the jittered projection is handled exactly.
