# Project Research Summary

**Project:** FilmFX
**Domain:** GPU-accelerated film emulation photo editing (Android)
**Researched:** 2026-02-25
**Confidence:** MEDIUM-HIGH

## Executive Summary

FilmFX is an Android photo editing app that applies physics-based film emulation effects -- signal-dependent grain, halation, bloom, split toning, and ACES tone mapping -- using custom OpenGL ES 3.0 shaders through the android-gpuimage framework, with a Jetpack Compose UI and Coil for image loading. The recommended approach is a layered MVVM architecture where Compose handles UI state, a ViewModel mediates filter parameters, and a dedicated GPU rendering layer runs android-gpuimage filters on a GLSurfaceView wrapped via AndroidView. This is a well-understood architecture for GPU-accelerated image editors on Android, with strong official documentation behind each layer. The critical insight is that the rendering pipeline must operate in linear color space from day one -- linearize sRGB input first, do all math in linear, and gamma-correct last -- because retrofitting color space correctness into an existing shader chain is extremely costly.

The primary risks are threefold. First, android-gpuimage (v2.1.0, 9.2k GitHub stars) has limited recent development activity; it provides an excellent foundation that saves months of pipeline work, but the team should be prepared to fork or replace it if it proves unmaintained. Second, GPU memory management is the dominant scaling challenge: preview at 1080p is trivial, but full-resolution export of 48MP images requires tile-based rendering to avoid OOM crashes, and multi-pass effects like halation multiply the memory requirement. Third, shader precision and performance vary wildly across Android's three GPU families (Adreno, Mali, PowerVR), so all custom shaders must use `highp` precision and be tested on at least one device from each family.

The competitive landscape validates the approach. VSCO, Dehancer, and RNI Films dominate the film emulation space but are iOS-focused or subscription-locked. FilmFX's differentiators -- physics-accurate signal-dependent grain (grain density varies with luminance, not uniform noise) and multi-scale halation (three blur passes at different radii) -- are genuinely novel on Android. However, deferring film stock presets to v2 is a calculated risk: every competitor ships 40-60+ presets, and users may expect them at launch. The v1 bet is that granular manual controls over authentic physics-based effects are compelling enough without one-click presets.

## Key Findings

### Recommended Stack

The stack is modern, well-documented, and version-compatible as of February 2026. Kotlin 2.3.0 with AGP 9.0.1 (which has built-in Kotlin support, eliminating the separate plugin), Jetpack Compose 1.10.3 via BOM 2026.01.01, Material 3 1.4.0 for dark theme, and Coil 3.4.0 for image loading. The GPU layer uses android-gpuimage 2.1.0 with custom OpenGL ES 3.0 shaders.

**Core technologies:**
- **Kotlin 2.3.0 + AGP 9.0.1**: Primary language and build system -- built-in Kotlin support in AGP eliminates plugin conflicts
- **Jetpack Compose 1.10.3 + Material 3**: Declarative UI with dark theme -- modern, testable, official Android UI toolkit
- **android-gpuimage 2.1.0**: GPU filter pipeline foundation -- saves 3-4 months vs raw OpenGL, provides 100+ base filters and shader chaining
- **OpenGL ES 3.0**: Shader execution -- 99%+ device coverage at API 18+, more mature driver support than Vulkan across GPU families
- **Coil 3.4.0**: Image loading and caching -- Kotlin-first, Compose-native, 25-40% faster than 2.x, automatic downsampling prevents OOM
- **kotlinx-coroutines 1.10.2**: Async operations -- non-blocking image processing, lifecycle-aware scopes for GPU operations

**Critical version requirement:** minSdk = 21 (Coil 3.x minimum), targetSdk = 35 (Google Play 2026 requirement), JDK 17 (AGP 9.0 minimum).

### Expected Features

**Must have (table stakes) -- users will leave without these:**
- Gallery import and full-resolution JPEG export
- Real-time preview at 30fps minimum (competitors advertise "zero lag")
- Before/after comparison (tap-hold toggle)
- Basic color adjustments (brightness, contrast, saturation)
- Vignette with adjustable intensity
- Individual effect sliders for granular control
- Non-destructive parameter editing

**Should have (competitive differentiators) -- these ARE the product:**
- Signal-dependent film grain (grain density varies by luminance; competitors use uniform noise)
- Physics-based halation (red-orange glow via multi-scale blur; VSCO added halation in 2026 but implementation unknown)
- Bloom (colorless white light scattering, separate from halation)
- ACES filmic tone mapping (industry-standard cinematic highlight rolloff)
- Split toning with color wheels (shadows/highlights separation; rare in mobile apps)

**Defer to v1.x:**
- Film stock presets (Portra, CineStill, etc.) -- HIGH RISK deferral; every competitor ships these
- HEIC export format
- Film compression modeling (highlight shoulder)
- RAW file import

**Defer to v2+:**
- Built-in camera with live effects
- Video processing
- Light leaks, dust, scratches overlays (superficial, not physics-based)
- Direct social sharing (use system share sheet)

### Architecture Approach

The architecture follows a standard layered MVVM pattern with a critical twist: a dual-pipeline design separates preview rendering (downsampled to 1080p, real-time at 30-60fps) from export rendering (full resolution, offline processing). Jetpack Compose wraps GPUImageView via AndroidView, and filter parameters flow unidirectionally from Compose sliders through ViewModel StateFlows to the GL render thread via queueEvent(). The filter chain is composed as a GPUImageFilterGroup with the order: linearize -> ACES tone map -> split toning -> halation -> bloom -> grain -> vignette -> gamma correction.

**Major components:**
1. **Presentation Layer (Compose + ViewModel)** -- EditorScreen with sliders, color wheels, before/after toggle; ViewModel holds FilterParams as StateFlow
2. **Rendering Layer (android-gpuimage + custom filters)** -- GPUImageView in AndroidView, custom GPUImageFilter subclasses for each film effect, GL thread management
3. **Data Layer (Coil + Repositories)** -- Image loading with downsampling for preview, full-res for export, MediaStore integration for gallery I/O
4. **Filter Pipeline (GLSL shaders)** -- Custom fragment shaders implementing ACES curve, signal-dependent grain (Poisson noise model), multi-scale Gaussian blur for halation, HSL-based split toning

### Critical Pitfalls

1. **Gamma/color space confusion** -- Perform ALL shader math in linear space. Linearize input (pow 2.2) first, gamma-correct (pow 1/2.2) last. Retrofitting is extremely expensive. Must be correct from day one.
2. **GPU texture OOM on full-res export** -- 48MP image with 7-effect chain = 1.3GB GPU textures. Use tile-based rendering (2048x2048 overlapping tiles) and ping-pong FBO strategy (two FBOs, not one per effect).
3. **Shader precision artifacts across GPU vendors** -- Use `precision highp float` in all custom shaders. Mali GPUs are strict about precision; Adreno silently promotes. Test on all three GPU families.
4. **android-gpuimage lifecycle memory leaks** -- GLSurfaceView resources leak in Compose's AndroidView. Use DisposableEffect with explicit deleteImage() cleanup. Monitor with LeakCanary.
5. **Render pass splitting on tile-based GPUs** -- Each FBO switch forces expensive tile flush. Minimize switches with ping-pong rendering and separable Gaussian blur (H+V passes) for halation.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: GPU Pipeline Foundation
**Rationale:** Every other feature depends on a correctly configured rendering pipeline. Color space correctness, shader precision policy, and lifecycle management must be established before any effects are built. The architecture research is unambiguous: get the pipeline right first.
**Delivers:** Working android-gpuimage integration in Compose, linear color space pipeline, shader pre-compilation, gallery import via Coil with downsampled preview rendering, basic project scaffolding (Gradle config, manifest, theme).
**Addresses:** Gallery import, real-time preview infrastructure, image loading/caching.
**Avoids:** Pitfall 1 (gamma confusion), Pitfall 4 (lifecycle leaks), Pitfall 7 (shader compilation jank).

### Phase 2: Core Film Effects (Shaders)
**Rationale:** The five physics-based effects are the product's reason to exist. They should be built and tested individually before wiring into the UI. Halation is the most complex (multi-pass blur with FBO management) and should set the performance baseline for the pipeline.
**Delivers:** Custom GLSL shaders for ACES tone mapping, signal-dependent grain, halation (multi-scale blur), bloom, split toning, and vignette. Each filter as a GPUImageFilter subclass. Filter chain composition via GPUImageFilterGroup.
**Uses:** OpenGL ES 3.0, android-gpuimage filter chain, ping-pong FBO strategy.
**Implements:** Rendering layer filter pipeline from architecture.
**Avoids:** Pitfall 3 (shader precision), Pitfall 5 (render pass splitting), Pitfall 6 (dependent texture reads).

### Phase 3: Editor UI and Controls
**Rationale:** With the pipeline and effects working, the UI can be built to expose them. Slider controls, color wheels for split toning, and before/after comparison are the primary user-facing surfaces. Non-destructive editing (parameter state management) is built here because it underpins undo and future preset saving.
**Delivers:** EditorScreen with effect sliders, color wheel component for split toning, before/after tap-hold comparison, basic color adjustments (brightness, contrast, saturation), non-destructive parameter editing via ViewModel state.
**Addresses:** All table-stakes UI features, adjustable effect sliders, before/after comparison, non-destructive editing.
**Uses:** Jetpack Compose, Material 3 dark theme, ViewModel + StateFlow.

### Phase 4: Export Pipeline and Polish
**Rationale:** Export requires a separate rendering pipeline (dual pipeline pattern) and is the most memory-constrained operation. Tile-based rendering for large images is architecturally significant and should not be deferred. Polish includes error handling, loading states, and EXIF preservation.
**Delivers:** Full-resolution JPEG export with tile-based rendering for large images, MediaStore integration, export progress indicator, error handling (OOM recovery, permission flows, shader failures), EXIF data preservation.
**Addresses:** Full-resolution export, export UX (progress indicator, no overwrites).
**Avoids:** Pitfall 2 (GPU texture OOM on export).

### Phase 5: Testing, Optimization, and Launch Readiness
**Rationale:** Cross-device GPU testing is mandatory for an OpenGL-based app. Performance optimization (adaptive preview quality, FBO management tuning) requires a complete system to profile. This phase also addresses the film presets gap if user testing reveals it as a launch blocker.
**Delivers:** Cross-GPU testing and fixes (Adreno, Mali, PowerVR), performance profiling with AGI, adaptive quality for mid-range devices, final APK size optimization via R8.
**Addresses:** 30fps preview target validation, export speed targets, device compatibility.

### Phase Ordering Rationale

- **Foundation before effects:** The linear color space pipeline is a hard prerequisite for correct visual output from every effect. Building effects on a broken pipeline means rebuilding them later.
- **Effects before UI:** Sliders are meaningless without working shaders behind them. Building effects in isolation allows focused shader debugging without UI complexity.
- **UI before export:** Users need to see and tune effects before the export path matters. The preview pipeline validates effect quality; export is an output format concern.
- **Export before polish:** Tile-based export is architecturally complex and OOM-prone. It must be designed and tested, not bolted on during polish.
- **Polish last:** Performance optimization requires a complete system to profile. Cross-GPU testing catches vendor-specific issues that only manifest with the full effect chain running.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 2 (Core Film Effects):** Signal-dependent grain (Poisson noise model) and multi-scale halation are niche shader implementations with sparse mobile-specific documentation. Will need reference implementations from Dehancer/Color.io analysis and academic papers on film grain modeling.
- **Phase 4 (Export Pipeline):** Tile-based GPU rendering with overlapping borders and stitch logic is non-trivial. android-gpuimage's getBitmapWithFilterApplied() may not support tiling natively -- may require custom FBO management.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Foundation):** Well-documented android-gpuimage + Compose integration, Coil setup, and Gradle configuration. Official docs cover all integration points.
- **Phase 3 (Editor UI):** Standard Compose UI patterns -- sliders, color pickers, state management. No novel technical challenges.
- **Phase 5 (Testing/Optimization):** Standard profiling and optimization workflow with official Android tools (AGI, Android Studio Profiler).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified from official 2026 documentation. Compatibility matrix confirmed. AGP 9.0 + Kotlin 2.3.0 is the standard 2026 Android setup. |
| Features | MEDIUM | Competitor analysis is thorough but competitor implementation details (especially Dehancer's grain and VSCO's halation) are opaque. Film preset deferral risk is real but unquantified. |
| Architecture | HIGH | MVVM + android-gpuimage + Compose is a well-established pattern. Dual pipeline, filter chain composition, and AndroidView interop are all documented with code examples. |
| Pitfalls | MEDIUM-HIGH | Color space, OOM, and precision pitfalls are well-documented across multiple sources. android-gpuimage lifecycle issues confirmed by GitHub issues. Tile-based export has fewer mobile-specific references. |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **android-gpuimage maintenance status:** Version 2.1.0 works, but limited recent development. Validate commit activity before deep integration. Have a fallback plan (fork or raw OpenGL pipeline) if blocking bugs are discovered.
- **Signal-dependent grain algorithm:** The Poisson noise model for luminance-dependent grain is the core differentiator, but specific mobile-optimized implementations are not well-documented. Will need to prototype and validate against film reference images during Phase 2.
- **Halation multi-scale blur performance:** Three blur passes per frame at different radii is expensive on mobile GPUs. The separable Gaussian optimization helps, but real-world performance on mid-range devices (Mali GPUs specifically) needs empirical validation.
- **Film preset deferral risk:** Every competitor ships presets. Deferring to v1.x is a bet that manual controls are sufficient for launch. Consider adding 3-5 basic presets as Phase 3 stretch goal if development velocity allows.
- **Tile-based export with android-gpuimage:** The library's export API (getBitmapWithFilterApplied) processes the full image. Tiling may require bypassing this API and managing FBOs directly. Needs investigation in Phase 4 planning.
- **EXIF data preservation on export:** Not addressed in depth by any research file. Android's ExifInterface API should handle this, but needs verification that GPU-processed bitmaps retain metadata when saved via MediaStore.

## Sources

### Primary (HIGH confidence)
- [Android Jetpack Compose Releases](https://developer.android.com/jetpack/androidx/releases/compose) -- Version 1.10.3 confirmed
- [Coil 3.4.0 Changelog](https://coil-kt.github.io/coil/changelog/) -- Performance benchmarks, API changes
- [AGP 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) -- Built-in Kotlin, Gradle 9.1.0+ requirement
- [OpenGL ES on Android](https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl) -- API levels, device support
- [android-gpuimage GitHub](https://github.com/cats-oss/android-gpuimage) -- Architecture, filter API, known issues
- [Android Graphics Architecture](https://source.android.com/docs/core/graphics/architecture) -- System-level rendering pipeline
- [Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose) -- AndroidView interop patterns
- [NVIDIA GPU Gems: Importance of Being Linear](https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-24-importance-being-linear) -- Color space correctness

### Secondary (MEDIUM confidence)
- [Dehancer Features](https://www.dehancer.com/features) -- Competitor analysis, grain/halation/bloom feature set
- [VSCO Halation](https://www.vsco.co/features/halation) -- Competitor halation implementation (details opaque)
- [RNI Films](https://mobile.reallyniceimages.com/) -- Competitor feature set, HEIC export, RAW support
- [Filmbox Physics-Based Processing](https://videovillage.com/filmbox/) -- Reference for film emulation accuracy
- [Color.io Volumetric Grain](https://www.color.io/user-guide/volumetric-film-grain) -- Advanced grain modeling reference
- [Efficient Render Passes on TBR Hardware](https://medium.com/androiddevelopers/efficient-render-passes-on-tile-based-rendering-hardware-621070158e40) -- Mobile GPU optimization
- [OpenGL ES Shading Language Potholes](https://bitiotic.com/blog/2013/09/24/opengl-es-shading-language-potholes-and-problems/) -- Cross-vendor shader issues

### Tertiary (LOW confidence)
- [Undo/Redo in Image Editor](https://blog.fossasia.org/implementing-undo-and-redo-in-image-editor-of-phimpme-android/) -- Command pattern for editing state (single source)
- [GPU Driver Quirks Discussion](https://www.esper.io/blog/android-dessert-bites-14-gpu-driver-updates-3819534) -- ARM Mali/Adreno analysis (blog post)

---
*Research completed: 2026-02-25*
*Ready for roadmap: yes*
