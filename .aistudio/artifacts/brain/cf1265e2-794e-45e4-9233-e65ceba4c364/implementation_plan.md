# DARK ESCAPE — Real-Time 4K-Ready 2.5D Graphics & Gameplay Engine

A production-grade real-time 2.5D isometric rendering and gameplay upgrade integrated directly into the existing **DARK ESCAPE** codebase—adding GPU-accelerated procedural material textures with mipmap caching, real-time multi-source dynamic lighting, contact ambient occlusion, directional drop shadows, weather/biome particle systems, destructible world objects, dynamic camera zoom (boss encounters + combat + manual zoom), and genuine **ULTRA / HIGH / PERFORMANCE** quality presets with adaptive frame-time monitoring.

## User Review & Critical Decisions

> [!IMPORTANT]
> All requirements from the specification are preserved in full and mapped directly to the existing playable game architecture:

- **Real-Time Graphics Quality Tiers (`ULTRA`, `HIGH`, `PERFORMANCE` + Auto-Adaptive FPS Monitor)**: Real-time switching in the HUD and Settings that adjusts render resolution scale, procedural texture detail/mipmaps, dynamic point-light count, ambient occlusion passes, shadow softness, and environmental weather particle budgets—automatically scaling down if frame times exceed target thresholds.
- **Dynamic Camera System**: Smooth velocity-interpolated camera follow with dynamic combat/dash zoom, **Cinematic Boss Camera** framing when an Elder Biome Boss is within proximity, screen shake, and interactive zoom controls.
- **All 8 Biomes & Environmental Effects**: Real-time animated water waves, flowing magma rivers, swaying jungle foliage, desert heat/sandstorm particles, snowfall & frost crystals, haunted spectral mist, golden solar beams, and bioluminescent energy conduits—plus destructible crates/urns and animated opening treasure chests.

---

## 1. Overview & Core Concept

- **What It Does**: Upgrades the live `IsometricGameCanvas` and `GameViewModel` into a multi-pass real-time 2.5D engine featuring:
  1. **Material & Texture Cache (`ProceduralMaterialAtlas`)**: Synthesizes high-resolution tileable normal/albedo surface patterns (`ImageBitmap` shaders with mipmap downsampling for `ULTRA`, `HIGH`, and `PERFORMANCE`) for each of the 8 biomes so blocks render crisp stone, moss, sandstone, glacial ice, basalt, gold filigree, and crystal circuitry without external asset bloat.
  2. **Real-Time Lighting, Shadows & Ambient Occlusion**: Multi-light accumulation pass (torches, lava tiles, arcane crystals, portals, and hero light) with distance attenuation, directional ground shadows for all tiles/props/characters, and corner ambient occlusion at wall-floor junctions.
  3. **Full Character & Enemy Animation State Machine**: Idle breathing, 8-directional walk/run stride cycles, Z-axis jump launch & landing squash/stretch, directional sword/staff slash trails, dash afterimage ghosts, hit-reaction recoil/flash, and enemy death shatter animations.
  4. **Interactive & Destructible 2.5D World Objects**: Destructible biome props (ancient urns, crystal clusters, wooden barricades) that shatter on attack and drop bonus coins/elixirs, plus animated chest lids that swing open when collected.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Live HUD Graphics & Telemetry Bar**:
     - Displays real-time **FPS & Frame-Time (ms)** badge alongside a one-tap **Graphics Quality Cycling Pill (`ULTRA` / `HIGH` / `PERFORMANCE` / `AUTO`)** and **Camera Zoom (`+` / `-`)** controls.
  2. **Biome-Specific Real-Time Atmosphere & Weather**:
     - *Ancient Jungle Ruins*: Swaying vine canopies, falling leaf spores, emerald water ripples, poison dart totems.
     - *Underground Dungeon*: Flickering iron braziers, drifting dungeon embers, retracting steel spikes.
     - *Desert Temple*: Swirling golden sand motes, sandstone obelisks, rhythmic fire jets.
     - *Frozen Kingdom*: Falling snow particles, specular ice reflections, frost geysers.
     - *Haunted Castle*: Drifting violet spectral fog, gothic gargoyle pillars, cursed lanterns.
     - *Lava Caverns*: Flowing magma veins, rising volcanic ash & sparks, basalt columns.
     - *Lost Golden City*: Shimmering solar rays, gilded ziggurat reliefs, clockwork automatons.
     - *Ancient Underground Civilization*: Pulsing cyan power grid lines, floating crystal shards, energy pylons.
  3. **Cinematic Boss Camera & Combat Feedback**:
     - Approaching a level Boss smoothly pulls the camera zoom back (`zoom = 0.86f`) and biases the focal point between the player and the Boss while displaying a dedicated Boss Health Bar banner.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Hardware-Accelerated Compose Canvas + Procedural `ImageBitmap` Material Shaders**
  - *Chosen Approach*: Combine Android's hardware-accelerated `Canvas` (`DrawScope`) with pre-generated high-DPI `ImageBitmap` material textures, radial/linear GPU `Brush` shaders, pre-allocated object pools (`ParticleFx`, `WeatherMote`, `DestructibleProp`, `DeathEffect`), and strict viewport frustum culling.
  - *Why*: Integrates seamlessly with the existing Jetpack Compose UI, touch controls, and Room persistence while delivering 60 FPS hardware-accelerated rendering and genuine resolution/effect scaling across `ULTRA`, `HIGH`, and `PERFORMANCE` modes.

- **Decision 2: Adaptive Frame-Time Governor**
  - *Chosen Approach*: Continuously measure rolling frame time (`frameTimeMs`) in the `withFrameNanos` game loop; when `autoAdaptQuality` is enabled and rolling FPS drops below 45 FPS for >1.5 seconds, automatically step down particle and shadow passes to keep gameplay silky smooth.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                     Real-Time 2.5D Rendering Pipeline                        │
│  ┌──────────────────────────────┐  ┌──────────────────────────────────────┐  │
│  │   ProceduralMaterialAtlas    │  │       IsometricGameCanvas (GPU)      │  │
│  │ • 8 Biome Albedo/Detail Maps │  │ • Pass 1: Skybox & Dynamic Camera    │  │
│  │ • ULTRA/HIGH/PERF Mipmaps    │  │ • Pass 2: Extruded 2.5D Terrain + AO │  │
│  │ • Cached Shader Brushes      │  │ • Pass 3: Props, Chests & Characters │  │
│  └──────────────┬───────────────┘  │ • Pass 4: Dynamic Lights, Fog & FX   │  │
│                 └─────────────────►└──────────────────┬───────────────────┘  │
└───────────────────────────────────────────────────────┼──────────────────────┘
                                                        │ 60 FPS Frame Clock
┌───────────────────────────────────────────────────────▼──────────────────────┐
│                       GameViewModel & Real-Time Engine                       │
│  ┌──────────────────────────────────┐  ┌──────────────────────────────────┐  │
│  │   Physics, AI & Animation FSM    │  │   GraphicsQuality & FPS Monitor  │  │
│  │ • Walk/Run/Jump/Land/Attack/Die  │  │ • ULTRA / HIGH / PERFORMANCE     │  │
│  │ • Destructible Props & Chests    │  │ • Rolling FPS & Frame-Time (ms)  │  │
│  │ • Boss Camera & Dynamic Zoom     │  │ • Auto-Adaptive Workload Scaler  │  │
│  └──────────────────────────────────┘  └──────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Interactive Component & State Mapping

- **Graphics Quality Switcher (`cycleGraphicsQuality`)**: Cycles between `ULTRA` (full ambient occlusion, multi-layer soft shadows, 96 weather motes, 128 combat particles, high-res procedural surface detail, dynamic lighting halos), `HIGH` (balanced shadows, 48 weather motes, 72 particles, medium surface detail), and `PERFORMANCE` (crisp fast shading, single-pass shadows, 16 weather motes, 36 particles).
- **Dynamic & Manual Camera Zoom (`adjustCameraZoom`, automatic combat/boss zoom)**: Smoothly interpolates `cameraZoom` between `0.75f` and `1.35f` based on player speed, dash state, nearby Boss presence, or HUD zoom buttons.
- **Destructible Props & Animated Chests**: Attacking near destructible props (`DESTRUCTIBLE_URN`, `CRYSTAL_CLUSTER`, `ANCIENT_CRATE`) shatters them in real time with debris particles and spawns bonus coins/health; collecting a `TREASURE_CHEST` transitions it to an open-lid state (`isOpened = true`, `openProgress = 0f..1f`) rather than vanishing abruptly.
