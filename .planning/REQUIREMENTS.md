# Requirements

**Project:** FilmFX
**Version:** v1
**Created:** 2026-02-25

## v1 Requirements

### Rendering Pipeline

- [x] **PIPE-01**: App converts sRGB input to linear color space before GPU processing and applies gamma correction on output
- [x] **PIPE-02**: Effects process in correct order: linearize → tone map → split tone → halation → bloom → grain → vignette → gamma
- [x] **PIPE-03**: Real-time preview renders at 30fps minimum (60fps target) on mid-range devices using downsampled resolution (1080p max)
- [x] **PIPE-04**: All custom shaders use `precision highp float` and work correctly on Adreno, Mali, and PowerVR GPUs

### Film Effects

- [ ] **FX-01**: Signal-dependent film grain where grain density varies with pixel luminance (brighter areas = more grain, Poisson noise model), applied per-channel RGB
- [ ] **FX-02**: Halation effect producing red-orange glow at high-contrast edges using multi-scale Gaussian blur (3 blur passes at different radii with exponential falloff)
- [ ] **FX-03**: Bloom effect producing colorless white light scattering from bright areas, visually distinct from halation
- [ ] **FX-04**: Split toning with separate color wheels for highlight and shadow color casting, supporting hue and saturation control
- [ ] **FX-05**: ACES filmic tone mapping providing cinematic highlight rolloff with smooth shoulder (no hard clipping)
- [ ] **FX-06**: Film compression modeling gradual highlight shoulder behavior that emulates analog film's latitude
- [ ] **FX-07**: Vignette effect with radial darkening, adjustable intensity and smoothness/feathering

### Image I/O

- [x] **IO-01**: User can import photos from device gallery via system photo picker
- [ ] **IO-02**: User can export edited photo at full original resolution as JPEG to device gallery
- [ ] **IO-03**: Exported images preserve EXIF metadata (orientation, date, camera info)

### UI/UX

- [x] **UI-01**: Dark, minimal UI design that is photo-centric with controls accessible from bottom of screen
- [ ] **UI-02**: Slider controls for all effect parameters (grain intensity, halation radius/intensity, bloom threshold/intensity, vignette intensity/smoothness, tone mapping strength, film compression amount)
- [ ] **UI-03**: Color wheel controls for split toning (separate wheels for highlight and shadow colors)
- [ ] **UI-04**: Before/after comparison via tap-hold toggle or split view showing original vs edited
- [ ] **UI-05**: Non-destructive parameter editing — user can adjust any parameter without restarting or losing other edits

### Launch

- [ ] **LAUNCH-01**: App icon designed and included
- [ ] **LAUNCH-02**: Play Store screenshots captured showing key features
- [ ] **LAUNCH-03**: Play Store listing with description, feature highlights, and category

## v2 Requirements (Deferred)

- Film stock presets (Portra 400, CineStill 800T, Kodak Gold, etc.) — core effects engine comes first
- Basic color adjustments (brightness, contrast, saturation) — film-specific focus for v1
- HEIC export format
- RAW file import
- Camera capture with CameraX integration
- Live camera preview with effects
- Educational content (film photography concepts)

## Out of Scope

- Temporal effects (film breath, gate weave) — v2+ feature
- iOS version — Android first
- Video processing — photos only for v1
- Social features / sharing integrations — export to gallery is sufficient
- AI auto-enhancement — contradicts physics-based approach
- Light leaks, dust, scratches overlays — superficial, not physics-based
- Subscription with hidden paywalls — anti-user pattern
- Unlimited undo/edit history — non-destructive editing via parameter reset is sufficient

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PIPE-01 | Phase 1 | Complete |
| PIPE-02 | Phase 1 | Complete |
| PIPE-03 | Phase 1 | Complete |
| PIPE-04 | Phase 1 | Complete |
| FX-01 | Phase 5 | pending |
| FX-02 | Phase 4 | pending |
| FX-03 | Phase 4 | pending |
| FX-04 | Phase 3 | pending |
| FX-05 | Phase 2 | pending |
| FX-06 | Phase 2 | pending |
| FX-07 | Phase 4 | pending |
| IO-01 | Phase 1 | Complete |
| IO-02 | Phase 7 | pending |
| IO-03 | Phase 7 | pending |
| UI-01 | Phase 1 | Complete |
| UI-02 | Phase 6 | pending |
| UI-03 | Phase 3 | pending |
| UI-04 | Phase 6 | pending |
| UI-05 | Phase 6 | pending |
| LAUNCH-01 | Phase 8 | pending |
| LAUNCH-02 | Phase 8 | pending |
| LAUNCH-03 | Phase 8 | pending |

---
*22 requirements | 5 categories | Created: 2026-02-25*
