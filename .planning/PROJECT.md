# FilmFX

## What This Is

FilmFX is an Android photo editing app that applies authentic, physics-based film emulation effects to digital photos. Unlike existing mobile apps that apply superficial color filters, FilmFX simulates the actual physical processes that give analog film its distinctive look — signal-dependent grain density, halation from light reflecting through emulsion layers, cinematic highlight rolloff, and color-accurate split toning. Users import photos from their gallery, shape the look with individual effect controls (sliders and color wheels), compare before/after, and export at full resolution.

## Core Value

Signal-dependent film grain and physically accurate halation — the two effects that make film look like film, and that no other mobile app implements correctly.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Signal-dependent film grain (density varies with brightness, per-channel RGB)
- [ ] Halation effect (red-orange glow at high-contrast edges, multi-scale blur)
- [ ] Bloom effect (white/colorless light scattering from bright areas)
- [ ] Split toning with color wheels (separate highlight and shadow colors)
- [ ] ACES filmic tone mapping (cinematic highlight rolloff)
- [ ] Film compression (gradual highlight shoulder, no hard clipping)
- [ ] Vignette (radial darkening with adjustable intensity/smoothness)
- [ ] Gallery photo import (pick from device gallery)
- [ ] Real-time preview of all effects while editing
- [ ] Before/after comparison (tap-hold or split view)
- [ ] Full-resolution export to device gallery
- [ ] Dark, minimal UI design (photo-centric, controls from bottom)
- [ ] Slider controls for effect parameters
- [ ] Color wheel controls for split toning
- [ ] Play Store ready (icon, screenshots, store listing)

### Out of Scope

- Film stock presets (Portra 400, CineStill 800T, etc.) — v2 feature, core effects engine comes first
- Camera capture / CameraX integration — gallery-only for v1
- Live camera preview with effects — not needed without camera
- Temporal effects (film breath, gate weave) — v2+ feature
- iOS version — Android first
- Video processing — photos only for v1
- Social features / sharing integrations — export to gallery is sufficient

## Context

### Research Foundation

Extensive research conducted across industry-leading sources:
- **Dehancer** — Hollywood-standard film emulation plugin; informed halation physics, grain modeling, bloom parameters
- **Frame.io / Cullen Kelly** — Professional colorist workflows; informed processing order and grain placement
- **VSCO** — Mobile film emulation pioneer; informed halation vs bloom distinction
- **Academic research** — IPOL paper on stochastic film grain rendering; informed signal-dependent grain algorithm
- **CineStill 800T** — Real-world halation reference (Kodak Vision3 without remjet backing)

### Key Technical Insights

1. **Signal-dependent grain** is THE differentiator — grain density proportional to brightness (Poisson model: `density = -log(1 - luminance)`)
2. **Halation color** comes from film physics — red layer is closest to camera back, receives most reflected light
3. **Processing order matters** — linearize → tone map → color grade → effects → grain → vignette → gamma
4. **Multi-scale blur** for halation — exponential falloff using 3 blur passes at different radii

### Open Source Decisions

- **android-gpuimage** (Apache 2.0) — Foundation for GPU filter pipeline, provides 100+ base filters
- **Coil 3.x** — Image loading, caching, downsampling for preview
- **Custom OpenGL ES 3.0 shaders** — Signal-dependent grain, halation, split toning, ACES tone mapping
- **ACES tone curve** — Public domain implementation from Krzysztof Narkowicz

### Existing Code

The repository contains research documents and early explorations. No production codebase yet — this is a greenfield build.

## Constraints

- **Platform**: Android only (v1), minimum SDK TBD based on OpenGL ES 3.0 support
- **Tech stack**: Kotlin + Jetpack Compose + android-gpuimage + OpenGL ES 3.0 + Coil
- **Performance**: Real-time preview must hit 30fps minimum (60fps target) on mid-range devices
- **GPU**: Must work across Adreno, Mali, and PowerVR GPUs (driver quirks are a known risk)
- **Export**: Full original resolution — GPU must handle large images without OOM

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| android-gpuimage as GPU foundation | 9.2k stars, Apache 2.0, 100+ filters, saves 3-4 months | — Pending |
| Gallery-only for v1 (no camera) | Simplifies scope significantly, camera adds CameraX complexity | — Pending |
| Effects-first (no presets in v1) | Core physics engine is the differentiator, presets are configuration on top | — Pending |
| Custom shaders for core effects | android-gpuimage lacks signal-dependent grain, halation, split toning | — Pending |
| ACES over Hable/Reinhard for tone mapping | Industry standard, used by Pixar/Disney/DaVinci Resolve | — Pending |
| Coil over Glide | Kotlin-first, smaller (94KB vs 222KB), Compose-native | — Pending |
| Dark minimal UI | Photo-centric editing, industry standard for photo editors | — Pending |

---
*Last updated: 2026-02-25 after initialization*
