# Architecture and how to extend it

All code lives in `app/src/main/java/com/vroombuddies/game/`.

| Package | What lives there | Android/Filament? |
|---|---|---|
| `sim/` | Track geometry (`TrackPath`), car physics (`Vehicle`), knock-over props (`Props`), race rules (`Race`) | Pure Kotlin, unit-tested |
| `render/` | Meshes and primitives, materials, environment/sky/IBL, track mesh, cars, scenery, particles, the race scene and camera (`GameScene`), the menu turntable (`Showroom`), graphics settings | Filament |
| `content/` | Theme data for all 20 tracks (`Themes.kt`) and knock-prop kinds (`Knock`) | Data only |
| `ui/` | Shared look (`Ui`), menus (`Menus`), HUD and result screen (`Hud`) | Android Views |
| `audio/` | Synthesised sound effects and the engine hum | AudioTrack |
| `GameActivity.kt` | Glue: Filament setup, screen flow, frame loop, input | |

Units: metres and seconds. Lighting is unitless (exposure 1). Sun ~3, IBL ~1, and emissive values above 1 bloom.
Track space: `s` is the distance along the loop and `x` is the lateral offset (+ is right). `TrackPath.frame(s)` turns that into world space.

## Mesh conventions
- Build geometry with `MeshData` + `Prims` (box, roundBox, cylinder, cone, sphere, torus, rock, plane). Use `Mat.trs(...)` for transforms.
- Vertex colours are linear RGBA (`Rgba.hex(0xRRGGBB)` converts from sRGB). Alpha is the foliage wind sway weight
  (0 = rigid; use `MeshData.windByHeight` for plants).
- Triangles are CCW from outside. `PrimsTest` checks this, so add new primitives to it.
- Keep models cheap: the near LOD should be at most ~300 triangles for repeated scenery, and the far LOD about a fifth of that.

## Parallel work streams (file ownership)
Each stream owns its own files and only touches a shared file to add one line.

### 1. Track batches (scenery + theme tuning)
- Create `render/scenery/<Group>Scenery.kt` with `object <Group>Scenery { val pack: SceneryPack = mapOf("cactus" to { lod -> ... }) }`.
- Register it with one line in `render/scenery/ScenerySets.kt`.
- Tune your themes' entries in `content/Themes.kt` (colours, prop counts, `TrackShape`, gravity, grip, grading). Only edit your own themes' blocks.
- Ids are global. If two batches need the same id, the owner listed below builds it.

| Batch | Themes | Ids to build |
|---|---|---|
| A Hot & dry | Desert Canyon, Sunset Highway, Volcano Run, Dino Valley | cactus, tumbleweed, mesa, billboard, lavaRock, volcano, fern, bigEgg |
| B Space | Space Station, Moon Base, Galaxy Grand Prix, Crystal Canyon | asteroid, satellite, planet, dome, antenna, crystal, glowMushroom |
| C Water & tropics | Sunny Beach, Jungle Jam, Pirate Cove, Bubble Reef | palm, umbrella, beachBall, sandcastle, chest, barrelProp, coral, kelp, fish |
| D Cold & cozy | Snowy Peaks, Frozen Lake, Autumn Woods | snowPine, snowman, iceberg, penguin, igloo, autumnTree, pumpkin, hayBale |
| E Fun town | Neon City, Sweet Treats, Toy Town, Rainbow Sky | building, lamp, neonSign, lollipop, candyCane, gumdrop, toyBlocks, toyBall, pencil, toyHouse, cloudPuff, rainbow |

Water surfaces (`Theme.water`) and per-theme special effects (falling snow, lava glow, bubbles) belong to the batch,
written as small classes in `render/fx/` and hooked into `GameScene` with one line.

### 2. Cars and paint
- Add each design as `render/cars/<Name>.kt` implementing `CarDesign` (see `Zoomy` in `CarModels.kt` for parts, wheel
  placement and the friendly face). Register it in `CarDesigns.all`.
- Paints live in `Paints.all`. Aim for 6 or more original cars with distinct silhouettes (bubble, tall truck, low racer, etc.).
- Physics differences go in `VehicleParams` per design, kept within about ±10% so no choice is bad for a child.

### 3. UI and menus
- Owns `ui/` and the screen flow in `GameActivity`. Ideas: an animated title, car names and stats, a track map preview,
  a bigger win celebration, and a parent-only settings gate.

### 4. Effects and graphics
- Owns `render/Particles.kt`, `render/GraphicsSettings.kt`, `render/Environment.kt` and new `render/fx/` files that aren't theme-specific.
- Missing from the feature list: real volumetric god rays (currently bloom + flare only), true motion blur
  (currently speed streaks), and water reflections. Confetti and fireworks already exist.

### 5. Audio
- Owns `audio/`: richer engine sound, per-theme music loops (synthesised or original), and a jingle on the menu.

## Testing
No emulator is available in the cloud, so verify sim changes with `SimTest`. For rendering, install the APK on a real phone.
