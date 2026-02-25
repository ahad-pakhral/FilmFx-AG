# Pitfalls Research

**Domain:** GPU-accelerated photo editing (Android, OpenGL ES 3.0, android-gpuimage)
**Researched:** 2026-02-25
**Confidence:** MEDIUM-HIGH

## Critical Pitfalls

### Pitfall 1: Gamma/Color Space Confusion in Processing Pipeline

**What goes wrong:**
Performing blending, blurring, or tone mapping operations in gamma (sRGB) space instead of linear space produces incorrect results — halation glows look wrong, grain distribution is non-physical, and tone mapping clips highlights incorrectly. This is the #1 visual quality killer.

**Why it happens:**
Input images from the gallery are sRGB-encoded (gamma 2.2). Developers skip linearization because "it looks fine on screen" and the error is subtle until you compare against reference footage. OpenGL ES doesn't auto-convert unless you use `GL_SRGB8_ALPHA8` internal formats.

**How to avoid:**
- Linearize input (`pow(color, 2.2)`) as the FIRST step in the shader pipeline
- Perform ALL math (blur, blend, grain, tone map) in linear space
- Apply gamma correction (`pow(color, 1/2.2)`) as the LAST step before display
- Use `GL_SRGB8_ALPHA8` for texture internal format where supported (ES 3.0+)
- Processing order: linearize → tone map → color grade → effects → grain → vignette → gamma

**Warning signs:**
- Halation glow looks "flat" or "muddy" instead of naturally bright
- Highlight rolloff has harsh edges rather than smooth shoulder
- Grain appears uniform instead of signal-dependent
- Before/after comparison with Dehancer shows obvious color differences

**Phase to address:**
Foundation/Pipeline Setup — must be correct from day one, retrofitting is extremely painful

---

### Pitfall 2: GPU Texture Memory OOM on Full-Resolution Export

**What goes wrong:**
Loading a 48MP image (8000×6000 = 192MB RGBA) into GPU memory for full-resolution export causes OOM crashes, especially on devices with limited GPU memory. Multiple intermediate framebuffers for multi-pass effects (halation needs 3+ blur passes) multiply the memory requirement.

**Why it happens:**
Preview works fine at 1080p (8MB per texture), so developers don't discover the problem until implementing full-res export. Each FBO in the chain needs its own texture. A 7-effect chain on a 48MP image = 7 × 192MB = 1.3GB of GPU textures.

**How to avoid:**
- Implement tile-based rendering for export: split image into overlapping tiles (e.g., 2048×2048), process each tile independently, stitch results
- Keep preview pipeline at downsampled resolution (1080p max)
- Reuse FBOs by ping-ponging between two framebuffers instead of allocating one per effect
- Call `glDeleteTextures()` / `glDeleteFramebuffers()` immediately after each export tile
- Monitor GPU memory with Android GPU Inspector (AGI) during development
- Implement `onTrimMemory()` callback to release GPU resources under pressure

**Warning signs:**
- App crashes silently (no Java exception — native GPU OOM)
- Export works on flagship devices but crashes on mid-range
- Memory usage spikes during export in profiler
- `glGetError()` returns `GL_OUT_OF_MEMORY`

**Phase to address:**
Export Pipeline — design tile-based export architecture from the start, don't bolt it on later

---

### Pitfall 3: Shader Precision Artifacts Across GPU Vendors

**What goes wrong:**
Shaders that work perfectly on one GPU vendor produce visible banding, color shifting, or noise pattern artifacts on another. `mediump` float has different actual precision on Adreno (16-bit), Mali (16-bit), and PowerVR (varies). The GLSL spec defines minimums, not guarantees.

**Why it happens:**
Developers test on one device (typically Samsung/Adreno or Pixel) and assume it works everywhere. Mali GPUs are particularly strict about precision, while Adreno may silently promote `mediump` to `highp`. Film effects require high precision — tone mapping curves, grain noise functions, and color wheel calculations are precision-sensitive.

**How to avoid:**
- Default to `precision highp float;` in all custom film effect shaders — the performance cost is minimal for image processing (not gaming)
- Use `lowp` ONLY for final color output and texture sampling coordinates
- Test on at least one device from each GPU family: Adreno (Samsung Galaxy/Pixel), Mali (Samsung Exynos/MediaTek), PowerVR (older budget devices)
- Add shader compilation error logging in debug builds
- Implement runtime GPU capability detection: `glGetShaderPrecisionFormat()`

**Warning signs:**
- Visible banding in gradient areas (tone mapping output)
- Grain pattern looks different between devices
- Color wheel toning produces slightly different hues on different phones
- Shader compiles but produces `GL_INVALID_OPERATION` on specific devices

**Phase to address:**
Shader Development — establish precision policy before writing first custom shader

---

### Pitfall 4: android-gpuimage Lifecycle Memory Leaks

**What goes wrong:**
GPUImage's internal GL context and textures leak when the Activity/Fragment is destroyed and recreated (rotation, backgrounding, process death). The `GPUImageView` holds references to GL resources that aren't properly released.

**Why it happens:**
android-gpuimage was designed before modern Android lifecycle (no ViewModel, no Compose). Its cleanup relies on `GLSurfaceView.onPause()` which isn't always called in Compose's `AndroidView` interop. Taking multiple photos or re-importing images without cleanup compounds the leak.

**How to avoid:**
- Wrap GPUImage in a lifecycle-aware component that calls cleanup on `onDispose` (Compose) or `onDestroy` (Fragment)
- Explicitly call `GPUImage.deleteImage()` and release GL textures before loading new images
- Use `DisposableEffect` in Compose to ensure cleanup:
  ```kotlin
  DisposableEffect(Unit) {
      onDispose { gpuImage.deleteImage() }
  }
  ```
- Monitor with LeakCanary in debug builds
- Implement `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` to release preview textures

**Warning signs:**
- Memory usage increases each time user imports a new photo
- App slows down after several editing sessions without restart
- LeakCanary reports `GPUImageView` or `GLThread` leaks
- Native memory grows while Java heap stays stable (GPU memory isn't tracked by Java GC)

**Phase to address:**
Foundation — establish lifecycle management pattern when first integrating GPUImage

---

### Pitfall 5: Render Pass Splitting on Tile-Based GPUs

**What goes wrong:**
Mobile GPUs (Adreno, Mali, PowerVR) use tile-based deferred rendering (TBDR). Switching framebuffer bindings mid-render forces the GPU to flush tiles to system memory and reload — this is extremely expensive and destroys real-time preview performance.

**Why it happens:**
Multi-pass effects (halation requires 3 blur passes, bloom adds more) inherently require FBO switches. Naive implementation creates a new render pass per effect, causing 7+ FBO switches per frame. Each switch = full tile flush + reload.

**How to avoid:**
- Minimize FBO switches by merging compatible effects into single shaders where possible
- Use ping-pong rendering (alternate between 2 FBOs) instead of creating separate FBOs per effect
- Order effects to minimize FBO switches: group single-pass effects together
- For multi-scale blur (halation): use separable Gaussian (horizontal + vertical) to reduce from O(n²) to O(n) texture reads
- Consider using `glInvalidateFramebuffer()` to tell driver when FBO contents won't be read back
- Profile with Snapdragon Profiler (Adreno) or ARM Mobile Studio (Mali)

**Warning signs:**
- Preview framerate drops below 30fps on mid-range devices
- GPU profiler shows "store/load" operations dominating frame time
- Adding one more effect causes disproportionate performance drop
- Performance differs wildly between GPU vendors

**Phase to address:**
Preview Pipeline — design FBO management strategy before implementing multi-pass effects

---

### Pitfall 6: Dependent Texture Reads in Fragment Shaders

**What goes wrong:**
Computing texture coordinates inside the fragment shader (e.g., for vignette, distortion, or grain UV offset) triggers "dependent texture reads" which prevent the GPU from prefetching texel data, stalling the pipeline.

**Why it happens:**
It's natural to compute UVs in the fragment shader for per-pixel effects. On desktop GPUs this is fine, but mobile tile-based GPUs optimize aggressively around known texture access patterns. When coordinates are computed dynamically, the GPU can't schedule texture fetches ahead of time.

**How to avoid:**
- Pre-compute texture coordinates in the vertex shader and pass as `varying`
- For effects that need per-pixel UV modification (vignette radius), pass the base parameters as uniforms and minimize fragment-side computation
- Use lookup textures (LUTs) instead of computing complex functions per-pixel
- For grain: generate noise texture on CPU, sample it in shader (don't compute noise per-pixel in fragment shader)

**Warning signs:**
- Unexpectedly low framerate despite simple-looking shader
- GPU profiler shows high "texture wait" or "stall" cycles
- Performance improves dramatically when you hardcode UV coordinates

**Phase to address:**
Shader Development — follow this rule from the first shader

---

### Pitfall 7: Shader Compilation Jank at Runtime

**What goes wrong:**
Compiling and linking shaders during user interaction (e.g., when switching effects or first applying a filter) causes visible frame drops or freezes (200-500ms per shader on some devices).

**Why it happens:**
Shader compilation is expensive and happens on the GL thread. If shaders are compiled lazily (first time an effect is applied), the user experiences a visible stutter. This is worse on older devices with slower shader compilers.

**How to avoid:**
- Pre-compile ALL shaders during app initialization (splash screen)
- Use a shader warmup phase: compile, link, and do a dummy render pass for each shader
- Cache compiled shader programs (store compiled binaries with `glGetProgramBinary()` on ES 3.0+)
- Show a loading indicator during initial shader compilation
- Only log shader compilation errors in debug builds (reading logs is itself expensive)

**Warning signs:**
- First application of each effect causes a visible hitch
- Users on older devices report "lag" when switching between effects
- Frame time spikes in profiler correlate with shader compilation

**Phase to address:**
Foundation — implement shader pre-compilation in app startup

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip linearization | Faster initial dev | Wrong visual output, must retrofit entire pipeline | Never — do it right from day one |
| Single FBO per effect | Simpler code | OOM on full-res, poor preview performance | Never — use ping-pong from start |
| `mediump` everywhere | Slightly faster shaders | Banding/artifacts on strict GPUs | Never for film effects — use highp |
| Hardcode preview resolution | Skip resolution logic | Looks terrible on tablets/foldables | MVP only — add resolution scaling early |
| Skip tile-based export | Faster export dev | OOM on images >12MP | MVP only if targeting flagship devices |
| Compute noise in fragment shader | Avoid texture upload | Poor performance, dependent reads | Never — use pre-generated noise texture |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| android-gpuimage + Compose | Direct Compose Image composable | Use `AndroidView` wrapping `GPUImageView` with `DisposableEffect` cleanup |
| Coil + GPUImage | Loading full-res into preview | Use Coil's `size()` to downsample for preview, load full-res only for export |
| Gallery picker + Bitmap | `MediaStore` returning rotated images | Always read EXIF orientation and apply rotation before GPU upload |
| OpenGL ES + Activity lifecycle | GL context lost on rotation | Handle `onSurfaceCreated` re-initialization, reload textures |
| SavedStateHandle + effect params | Losing all slider values on process death | Save effect parameters to ViewModel's SavedStateHandle |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Full-res preview rendering | <10fps, battery drain | Downsample to screen resolution for preview | Any image >4MP on mid-range device |
| Recreating filter chain on parameter change | Stutter on slider drag | Reuse filter instances, update uniforms only | Noticeable on any device |
| Synchronous bitmap decode on UI thread | ANR dialog | Use Coil async loading + coroutines | Images >2MP |
| Allocating new Bitmap per export | OOM after 2-3 exports | Reuse Bitmap with `BitmapFactory.Options.inBitmap` | After 3-4 exports without GC |
| Gaussian blur with large kernel | Exponential slowdown | Separable blur (H+V) + downscale-blur-upscale | Kernel radius >15px at 1080p |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No loading indicator during export | User thinks app froze, force-kills | Show progress bar with estimated time |
| Slider updates on finger lift only | Can't see effect while dragging | Update preview on every slider value change (throttled to 30fps) |
| No undo after export | User accidentally overwrites original | Always save as new file, never overwrite original |
| Effect order not visible | User confused why results differ from expectation | Show effect chain order in UI, match processing pipeline |
| Before/after flickers | Jarring comparison | Smooth crossfade or split-screen comparison |

## "Looks Done But Isn't" Checklist

- [ ] **Halation:** Often missing multi-scale blur — single Gaussian ≠ real halation. Verify 3 blur passes at different radii
- [ ] **Grain:** Often uniform noise — verify grain density varies with luminance (bright areas = more grain)
- [ ] **Tone mapping:** Often applied in gamma space — verify linearization happens before ACES curve
- [ ] **Export:** Often works only on test images — verify with 48MP images from modern phones
- [ ] **Color wheels:** Often hue-only — verify saturation and luminance range controls work
- [ ] **Vignette:** Often harsh circle — verify smooth radial falloff with adjustable feathering
- [ ] **Before/after:** Often reprocesses on toggle — verify original image is cached for instant comparison
- [ ] **EXIF data:** Often stripped on export — verify orientation, date, camera info preserved

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong color space throughout | HIGH | Audit every shader, add linearize/delinearize, retest all effects |
| GPU memory leaks | MEDIUM | Add LeakCanary, wrap GPUImage in lifecycle-aware component, add cleanup |
| Shader precision artifacts | LOW | Change precision qualifiers to highp, retest on target devices |
| No tile-based export | HIGH | Redesign export pipeline with tiling, overlapping borders, stitch logic |
| Render pass inefficiency | MEDIUM | Refactor FBO management to ping-pong, merge compatible shaders |
| Shader compilation jank | LOW | Move all compilation to init, add binary caching |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Gamma/color space confusion | Foundation (Pipeline Setup) | Visual comparison with reference images in linear vs gamma |
| GPU texture OOM | Export Pipeline | Export 48MP image on device with 3GB RAM without crash |
| Shader precision artifacts | Shader Development | Run full effect suite on Adreno, Mali, and PowerVR device |
| GPUImage lifecycle leaks | Foundation | LeakCanary shows zero GL-related leaks after 10 import cycles |
| Render pass splitting | Preview Pipeline | 30fps maintained with all 7 effects on mid-range device |
| Dependent texture reads | Shader Development | GPU profiler shows zero dependent texture read warnings |
| Shader compilation jank | Foundation | No frame drops on first effect application after cold start |

## Sources

- [Apple OpenGL ES Shader Best Practices](https://developer.apple.com/library/archive/documentation/3DDrawing/Conceptual/OpenGLES_ProgrammingGuide/BestPracticesforShaders/BestPracticesforShaders.html) — Precision, branching, dependent reads
- [Samsung OpenGL ES Usage Recommendations](https://developer.samsung.com/galaxy-gamedev/resources/articles/opengl.html) — Mali-specific optimizations
- [android-gpuimage Issue #273: Memory Leak Fix](https://github.com/CyberAgent/android-gpuimage/issues/273) — Known lifecycle leak patterns
- [Efficient Render Passes on TBR Hardware](https://medium.com/androiddevelopers/efficient-render-passes-on-tile-based-rendering-hardware-621070158e40) — FBO management for mobile GPUs
- [NVIDIA GPU Gems 3: The Importance of Being Linear](https://developer.nvidia.com/gpugems/gpugems3/part-iv-image-effects/chapter-24-importance-being-linear) — Color space correctness
- [LearnOpenGL: Gamma Correction](https://learnopengl.com/Advanced-Lighting/Gamma-Correction) — Linearization workflow
- [Qualcomm Game Developer Guide](https://docs.qualcomm.com/doc/80-78185-2/topic/mobile_best_practices.html) — Adreno tile-based rendering optimization
- [OpenGL ES Shading Language Potholes](https://bitiotic.com/blog/2013/09/24/opengl-es-shading-language-potholes-and-problems/) — Cross-vendor shader compatibility
- [Android Memory Management](https://developer.android.com/topic/performance/memory-management) — OOM prevention strategies

---
*Pitfalls research for: GPU-accelerated film emulation photo editing (Android)*
*Researched: 2026-02-25*
