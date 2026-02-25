# Roadmap: FilmFX

## Overview

FilmFX goes from an empty Android project to a Play Store-ready physics-based film emulation photo editor in 8 phases. The journey starts by establishing a correct GPU rendering pipeline with linear color space from day one (the foundation everything depends on), then layers in film effects in pipeline order -- tone mapping first, then color grading, then spatial effects, then grain -- before wiring up the editor UI, building the full-resolution export pipeline, and preparing for launch. Each phase delivers a verifiable capability that builds on the previous one.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: GPU Pipeline Foundation** - App scaffold, gallery import, linear color space pipeline, real-time preview rendering
- [ ] **Phase 2: Tone Mapping and Film Compression** - ACES filmic tone curve and highlight shoulder modeling
- [ ] **Phase 3: Split Toning** - Color grading with separate highlight/shadow color wheels
- [ ] **Phase 4: Spatial Effects** - Halation, bloom, and vignette via multi-pass GPU shaders
- [ ] **Phase 5: Signal-Dependent Film Grain** - Physics-based grain with luminance-dependent density (core differentiator)
- [ ] **Phase 6: Editor Controls and Interaction** - Slider controls, before/after comparison, non-destructive parameter editing
- [ ] **Phase 7: Full-Resolution Export** - Dual pipeline for full-res JPEG export with tile-based rendering and EXIF preservation
- [ ] **Phase 8: Launch Readiness** - App icon, Play Store screenshots, and store listing

## Phase Details

### Phase 1: GPU Pipeline Foundation
**Goal**: User can import a photo from their gallery and see it rendered in real-time through a correctly configured linear color space GPU pipeline
**Depends on**: Nothing (first phase)
**Requirements**: PIPE-01, PIPE-02, PIPE-03, PIPE-04, IO-01, UI-01
**Success Criteria** (what must be TRUE):
  1. User can open the app and see a dark, photo-centric interface with controls accessible from the bottom
  2. User can pick a photo from their device gallery and see it displayed in the editor
  3. The imported photo renders through the GPU pipeline at 30fps+ on a mid-range device (preview at 1080p max)
  4. The pipeline processes in the correct effect order (linearize through gamma) even though most effect slots are pass-through initially
  5. Custom shaders compile and run without artifacts on Adreno, Mali, and PowerVR GPUs
**Plans**: TBD

Plans:
- [ ] 01-01: TBD
- [ ] 01-02: TBD
- [ ] 01-03: TBD

### Phase 2: Tone Mapping and Film Compression
**Goal**: User can see their photo transformed with cinematic highlight rolloff and analog film latitude, giving digital photos a film-like tonal character
**Depends on**: Phase 1
**Requirements**: FX-05, FX-06
**Success Criteria** (what must be TRUE):
  1. Bright highlights in the photo roll off smoothly into white rather than clipping hard (ACES tone mapping visible)
  2. The highlight shoulder behaves like analog film -- gradual compression rather than digital clipping
  3. The tone mapping and film compression effects are visually distinct from each other and can be observed independently
**Plans**: TBD

Plans:
- [ ] 02-01: TBD

### Phase 3: Split Toning
**Goal**: User can apply separate color casts to highlights and shadows using intuitive color wheel controls, enabling classic film color grading looks
**Depends on**: Phase 2
**Requirements**: FX-04, UI-03
**Success Criteria** (what must be TRUE):
  1. User can select a color for shadow toning via a color wheel and see shadows shift toward that hue
  2. User can select a different color for highlight toning via a separate color wheel and see highlights shift independently
  3. Both color wheels support hue and saturation control
  4. Shadow and highlight color casts blend naturally without banding or artifacts in the midtones
**Plans**: TBD

Plans:
- [ ] 03-01: TBD

### Phase 4: Spatial Effects
**Goal**: User can add physically-motivated light behavior -- halation glow at bright edges, bloom light scatter, and radial vignetting -- to their photo
**Depends on**: Phase 2
**Requirements**: FX-02, FX-03, FX-07
**Success Criteria** (what must be TRUE):
  1. User can see a red-orange glow bleeding from high-contrast bright edges (halation), distinct from general light scatter
  2. User can see colorless white light scattering from the brightest areas of the image (bloom), visually distinct from halation
  3. User can apply radial edge darkening (vignette) with visible control over intensity and feathering
  4. All three spatial effects render within the 30fps preview budget without dropping frames
**Plans**: TBD

Plans:
- [ ] 04-01: TBD
- [ ] 04-02: TBD

### Phase 5: Signal-Dependent Film Grain
**Goal**: User can add film grain that looks authentically analog -- denser in bright areas, varying per RGB channel -- unlike the uniform noise every other mobile app applies
**Depends on**: Phase 1
**Requirements**: FX-01
**Success Criteria** (what must be TRUE):
  1. Grain is visibly denser in bright/highlight areas and sparser in shadows (signal-dependent behavior)
  2. Grain varies independently per RGB channel, producing subtle color variation rather than monochrome noise
  3. Grain appearance is organic and film-like rather than digital/uniform when compared to reference analog film images
**Plans**: TBD

Plans:
- [ ] 05-01: TBD

### Phase 6: Editor Controls and Interaction
**Goal**: User has full manual control over every film effect through sliders, can compare before/after, and can freely adjust any parameter without losing other edits
**Depends on**: Phase 3, Phase 4, Phase 5
**Requirements**: UI-02, UI-04, UI-05
**Success Criteria** (what must be TRUE):
  1. User can adjust each effect parameter via sliders (grain intensity, halation radius/intensity, bloom threshold/intensity, vignette intensity/smoothness, tone mapping strength, film compression amount)
  2. User can see a before/after comparison of original vs edited photo via tap-hold toggle or split view
  3. User can change any parameter at any time without restarting or losing values set for other effects (non-destructive editing)
  4. Slider changes are reflected in real-time in the preview (no lag or delay between slider movement and visual update)
**Plans**: TBD

Plans:
- [ ] 06-01: TBD
- [ ] 06-02: TBD

### Phase 7: Full-Resolution Export
**Goal**: User can save their edited photo at full original resolution to their device gallery, with metadata preserved, without crashes on large images
**Depends on**: Phase 6
**Requirements**: IO-02, IO-03
**Success Criteria** (what must be TRUE):
  1. User can export the edited photo as a full-resolution JPEG to their device gallery
  2. Exported image matches the preview (same effects, same parameters, no visual differences beyond resolution)
  3. Exported JPEG preserves original EXIF metadata (orientation, date, camera info)
  4. Export completes without OOM crash on images up to 48MP
**Plans**: TBD

Plans:
- [ ] 07-01: TBD

### Phase 8: Launch Readiness
**Goal**: App is ready for Play Store submission with all required store assets
**Depends on**: Phase 7
**Requirements**: LAUNCH-01, LAUNCH-02, LAUNCH-03
**Success Criteria** (what must be TRUE):
  1. App has a designed icon that displays correctly on device home screens and app drawer
  2. Play Store screenshots exist showing the key features (grain, halation, split toning, before/after)
  3. Play Store listing has a complete description, feature highlights, and correct category assignment
**Plans**: TBD

Plans:
- [ ] 08-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8
Note: Phases 3, 4, and 5 can execute in parallel after Phase 2 (3 and 4 depend on Phase 2; 5 depends only on Phase 1). Phase 6 waits for all three.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. GPU Pipeline Foundation | 0/3 | Not started | - |
| 2. Tone Mapping and Film Compression | 0/1 | Not started | - |
| 3. Split Toning | 0/1 | Not started | - |
| 4. Spatial Effects | 0/2 | Not started | - |
| 5. Signal-Dependent Film Grain | 0/1 | Not started | - |
| 6. Editor Controls and Interaction | 0/2 | Not started | - |
| 7. Full-Resolution Export | 0/1 | Not started | - |
| 8. Launch Readiness | 0/1 | Not started | - |
