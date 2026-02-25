# Architecture Research

**Domain:** GPU-accelerated photo editing on Android
**Researched:** 2026-02-25
**Confidence:** HIGH

## Standard Architecture

### System Overview

GPU-accelerated Android photo editors use a layered architecture that separates UI concerns from GPU rendering and image processing:

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  Jetpack Compose UI → AndroidView(GPUImageView)             │
│  ViewModel (UI State + Filter Parameters)                    │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────┴────────────────────────────────────────┐
│                  RENDERING LAYER                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ GPUImageView │  │ GPUImage     │  │ Filter Chain │      │
│  │ (GLSurface)  │  │ (Manager)    │  │ (Pipeline)   │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                  │                  │              │
│         └─────────┬────────┴──────────────────┘              │
├───────────────────┴──────────────────────────────────────────┤
│              OPENGL ES 3.0 RENDER THREAD                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Texture Upload → Shader Execution → FBO Rendering   │    │
│  └─────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│                   DATA LAYER                                 │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ Image      │  │ Filter     │  │ Export     │            │
│  │ Loader     │  │ State      │  │ Service    │            │
│  │ (Coil)     │  │ Repository │  │            │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **UI Layer** | User input, parameter controls, before/after display | Jetpack Compose with ViewModel for state management |
| **GPUImageView** | OpenGL surface management, lifecycle handling, continuous rendering | Subclass of GLSurfaceView wrapped in AndroidView for Compose |
| **GPUImage** | Filter chain orchestration, bitmap I/O, texture management | Core class from android-gpuimage, manages GPUImageFilter chains |
| **GPUImageFilter** | Individual effect implementation via GLSL shaders | Subclass GPUImageFilter with custom vertex/fragment shaders |
| **Render Thread** | OpenGL ES context, texture operations, shader compilation | Managed automatically by GLSurfaceView, uses queueEvent() for cross-thread communication |
| **Image Loader** | Downsampling for preview, disk/memory caching, bitmap decoding | Coil 3.x for coroutine-based loading with automatic downsampling |
| **Export Service** | Full-resolution processing, file I/O, MediaStore integration | Background coroutine with GPUImage.getBitmapWithFilterApplied() |

## Recommended Project Structure

```
app/src/main/java/com/filmfx/
├── ui/                      # Presentation layer (Compose)
│   ├── screens/             # Screen-level composables
│   │   ├── EditorScreen.kt  # Main editing interface
│   │   └── GalleryScreen.kt # Photo picker
│   ├── components/          # Reusable UI components
│   │   ├── FilterControls.kt    # Sliders, color wheels
│   │   ├── BeforeAfterView.kt   # Comparison component
│   │   └── GPUImageComposable.kt # AndroidView wrapper for GPUImageView
│   └── theme/               # Material 3 dark theme
│
├── viewmodel/               # State management
│   ├── EditorViewModel.kt   # Filter parameters, UI state
│   └── GalleryViewModel.kt  # Image selection state
│
├── rendering/               # GPU rendering layer
│   ├── FilmFXImageView.kt   # Custom GPUImageView subclass
│   ├── FilmFXRenderer.kt    # Custom renderer if needed
│   └── filters/             # Custom filter implementations
│       ├── SignalDependentGrainFilter.kt
│       ├── HalationFilter.kt
│       ├── SplitToningFilter.kt
│       ├── AcesToneMapFilter.kt
│       └── FilmCompressionFilter.kt
│
├── domain/                  # Business logic
│   ├── model/               # Data classes
│   │   ├── FilterParams.kt  # Parameter value objects
│   │   └── EditState.kt     # Editing session state
│   ├── repository/          # Data access
│   │   ├── ImageRepository.kt   # Load/save operations
│   │   └── FilterStateRepository.kt # Persist filter params
│   └── usecase/             # Business operations
│       ├── LoadImageUseCase.kt
│       ├── ApplyFiltersUseCase.kt
│       └── ExportImageUseCase.kt
│
└── util/                    # Shared utilities
    ├── ShaderUtils.kt       # GLSL shader loading/compilation helpers
    ├── ImageUtils.kt        # Bitmap operations
    └── MediaStoreUtils.kt   # Gallery interaction
```

### Structure Rationale

- **ui/**: Pure Compose UI with no Android View code except AndroidView wrappers. Facilitates testing with preview composables.
- **viewmodel/**: Follows MVVM pattern, recommended for Compose apps. ViewModel survives configuration changes and exposes StateFlow/State for UI observation.
- **rendering/**: Isolated OpenGL/GPU concerns. Custom filters extend GPUImageFilter. Keeps shader code separate from business logic.
- **domain/**: Clean architecture separation. Repository pattern for data access, use cases for complex operations. Testable without Android framework.
- **util/**: Reusable helpers. Shader compilation, bitmap I/O, and platform-specific utilities.

## Architectural Patterns

### Pattern 1: Dual Pipeline (Preview + Export)

**What:** Separate rendering pipelines for real-time preview and full-resolution export.

**When to use:** Always for GPU-accelerated photo editors handling high-resolution images (12MP+).

**Trade-offs:**
- **Pros:** Real-time preview at 30-60fps, no OOM on export, better battery life
- **Cons:** Requires duplicate filter setup, more complex state management

**Example:**
```kotlin
class EditorViewModel : ViewModel() {
    // Preview pipeline (downsampled)
    private val previewImage: MutableState<Bitmap?> = mutableStateOf(null)
    private var previewGPUImage: GPUImage? = null

    // Export pipeline (full resolution)
    private var exportGPUImage: GPUImage? = null

    fun loadImage(uri: Uri, context: Context) {
        viewModelScope.launch {
            // Load downsampled for preview
            val downsampled = imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(1080, 1920) // Max preview size
                    .build()
            ).drawable?.toBitmap()

            previewImage.value = downsampled
            previewGPUImage?.setImage(downsampled)
        }
    }

    fun exportFullResolution(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // Load original resolution
            val fullRes = BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(uri)
            )

            // Create separate GPUImage instance for export
            exportGPUImage = GPUImage(context).apply {
                setImage(fullRes)
                // Apply same filter chain as preview
                setFilter(buildFilterChain(filterParams.value))
            }

            val result = exportGPUImage?.getBitmapWithFilterApplied()
            saveToGallery(result)
        }
    }
}
```

### Pattern 2: Filter Chain Composition

**What:** Build complex effects by chaining multiple GPUImageFilter instances sequentially.

**When to use:** For multi-stage effects like FilmFX (tone map → color grade → halation → grain → vignette).

**Trade-offs:**
- **Pros:** Modular, reusable filters, matches GPU pipeline architecture
- **Cons:** Each filter requires texture upload/download, potential performance overhead

**Example:**
```kotlin
fun buildFilmFXFilterChain(params: FilterParams): GPUImageFilter {
    val filters = mutableListOf<GPUImageFilter>()

    // 1. Tone mapping (ACES filmic curve)
    if (params.toneMapping > 0f) {
        filters.add(AcesToneMapFilter(params.toneMapping))
    }

    // 2. Split toning (separate highlight/shadow colors)
    if (params.splitToning.enabled) {
        filters.add(SplitToningFilter(
            highlightColor = params.splitToning.highlightColor,
            shadowColor = params.splitToning.shadowColor,
            balance = params.splitToning.balance
        ))
    }

    // 3. Halation (red-orange glow at high-contrast edges)
    if (params.halation > 0f) {
        filters.add(HalationFilter(
            intensity = params.halation,
            threshold = params.halationThreshold
        ))
    }

    // 4. Signal-dependent grain (density varies with luminance)
    if (params.grain > 0f) {
        filters.add(SignalDependentGrainFilter(
            intensity = params.grain,
            size = params.grainSize
        ))
    }

    // 5. Vignette (last to darken edges)
    if (params.vignette > 0f) {
        filters.add(GPUImageVignetteFilter(
            params.vignetteCenter,
            params.vignetteColor,
            params.vignetteStart,
            params.vignetteEnd
        ))
    }

    return GPUImageFilterGroup(filters)
}
```

### Pattern 3: Jetpack Compose + OpenGL Interop

**What:** Embed GLSurfaceView in Compose using AndroidView composable.

**When to use:** When building Compose UI with GPU-accelerated rendering requirements.

**Trade-offs:**
- **Pros:** Gradual migration path, leverage Compose benefits (state, modifiers, recomposition)
- **Cons:** AndroidView breaks Compose composition semantics, lifecycle management complexity

**Example:**
```kotlin
@Composable
fun GPUImagePreview(
    bitmap: Bitmap?,
    filter: GPUImageFilter?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GPUImageView(ctx).apply {
                // Configure surface type (SURFACE_VIEW or TEXTURE_VIEW)
                setRenderMode(GPUImage.SURFACE_TYPE_SURFACE_VIEW)
                setRatio(1f) // Aspect ratio

                // Set filter and image
                bitmap?.let { setImage(it) }
                filter?.let { setFilter(it) }
            }
        },
        update = { view ->
            // Called when state changes (recomposition)
            bitmap?.let { view.setImage(it) }
            filter?.let { view.setFilter(it) }
        }
    )
}

@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val filterParams by viewModel.filterParams.collectAsState()
    val bitmap by viewModel.previewBitmap.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // GPU preview
        GPUImagePreview(
            bitmap = bitmap,
            filter = viewModel.buildFilterChain(filterParams),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        // Controls
        FilterControlPanel(
            params = filterParams,
            onParamsChange = { viewModel.updateParams(it) }
        )
    }
}
```

### Pattern 4: Cross-Thread Communication with queueEvent

**What:** Use GLSurfaceView.queueEvent() to safely execute OpenGL operations from UI thread.

**When to use:** When responding to UI events (touch, slider changes) that affect rendering.

**Trade-offs:**
- **Pros:** Thread-safe, prevents OpenGL context violations
- **Cons:** Adds latency, requires understanding of GL thread model

**Example:**
```kotlin
class FilmFXImageView(context: Context) : GPUImageView(context) {

    // Called from UI thread (e.g., slider change)
    fun updateFilterParam(name: String, value: Float) {
        // Queue operation on GL render thread
        queueEvent {
            // This runs on the GL thread
            (filter as? CustomFilter)?.setParam(name, value)
            requestRender() // Trigger redraw
        }
    }

    // Touch events arrive on UI thread
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Queue GL operation safely
                queueEvent {
                    // Sample pixel color at touch point
                    val color = sampleColorAt(event.x, event.y)
                    post { // Post back to UI thread
                        onColorSampled(color)
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
```

## Data Flow

### Preview Rendering Flow

```
User Opens Image
    ↓
[Gallery Picker] → URI
    ↓
[ViewModel] → LoadImageUseCase
    ↓
[Coil ImageLoader]
    • Downsamples to preview size (1080x1920)
    • Applies memory cache
    • Returns Bitmap
    ↓
[ViewModel State] → MutableState<Bitmap>
    ↓
[Compose Recomposition] → GPUImagePreview
    ↓
[AndroidView.update] → GPUImageView.setImage()
    ↓
[GPUImage] → Texture Upload (GL thread)
    ↓
[Filter Chain] → Sequential shader passes
    • ACES tone map
    • Split toning
    • Halation
    • Signal-dependent grain
    • Vignette
    ↓
[GLSurfaceView] → Renders to screen (30-60fps)
```

### Filter Parameter Update Flow

```
User Adjusts Slider
    ↓
[Compose Slider] → onChange callback
    ↓
[ViewModel.updateParams()] → StateFlow<FilterParams>
    ↓
[Compose Recomposition]
    ↓
[AndroidView.update] → GPUImageView.setFilter(newFilter)
    ↓
[GLSurfaceView.queueEvent]
    ↓
[GL Thread] → Shader recompilation (if needed)
    ↓
[Render Loop] → Immediate redraw with new params
```

### Full-Resolution Export Flow

```
User Taps Export
    ↓
[ViewModel.exportImage()] → Background coroutine (Dispatchers.IO)
    ↓
[ImageRepository.loadFullResolution()] → No downsampling
    ↓
[Create separate GPUImage instance]
    • Avoids interfering with preview
    • Uses same filter chain
    ↓
[GPUImage.setImage(fullResBitmap)]
    ↓
[GPUImage.getBitmapWithFilterApplied()]
    • Renders to offscreen FBO
    • Reads pixels back to Bitmap
    • NOT real-time (may take 1-5 seconds)
    ↓
[MediaStore API] → Save to Pictures folder
    ↓
[Notify UI] → Show success toast
```

### Memory Management Flow

```
Large Image Handling:

Preview Path:
    Original (4000x3000, 48MB)
    ↓
    Coil Downsampling
    ↓
    Preview (1080x1920, 8MB)
    ↓
    GPU Texture Upload (8MB VRAM)
    ↓
    Filter Chain (2x texture buffers per filter = 16MB VRAM)

Export Path:
    Original (4000x3000, 48MB)
    ↓
    Single texture upload (48MB VRAM)
    ↓
    Filter Chain (96MB VRAM peak)
    ↓
    Result (48MB)
    ↓
    Immediate bitmap.recycle() + GPU cleanup
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| **MVP (single photo editing)** | Use standard GPUImage + simple filter chain. No need for undo/redo, state persistence, or batch processing. Focus on core effects quality. |
| **v1.5 (presets + history)** | Add Command pattern for undo/redo (store filter param deltas, not full bitmaps). Implement preset storage with JSON serialization. Introduce FilterStateRepository for persistence. |
| **v2.0 (batch processing)** | Implement WorkManager for background batch jobs. Use separate GPUImage instances per image (avoid texture thrashing). Add progress notifications. Consider reducing filter quality for batch mode (e.g., skip expensive halation). |

### Scaling Priorities

1. **First bottleneck: GPU memory (OOM crashes)**
   - **When:** Processing 4K+ images on low-end devices
   - **Fix:** Implement texture tiling (split large images into tiles), process sequentially, recycle immediately
   - **Alternative:** Set maximum resolution limit (e.g., 16MP), downscale larger images

2. **Second bottleneck: Export speed (>5 seconds for 4K)**
   - **When:** Complex filter chains (5+ filters) on full resolution
   - **Fix:** Optimize shaders (combine multiple effects into single shader pass), use OpenGL ES 3.0 compute shaders if available
   - **Alternative:** Add "fast export" mode with reduced filter quality

3. **Third bottleneck: Preview lag (<30fps)**
   - **When:** Heavy filters (multi-pass blur, convolution) on mid-range GPUs
   - **Fix:** Reduce preview resolution further (720p), skip expensive filters in preview (show approximation)
   - **Alternative:** Implement adaptive quality (detect frame drops, reduce resolution dynamically)

## Anti-Patterns

### Anti-Pattern 1: Loading Full-Resolution Images for Preview

**What people do:** Load original resolution (e.g., 12MP) into GPUImageView for preview.

**Why it's wrong:**
- Wastes GPU memory (texture size scales with resolution)
- Slower texture uploads (50ms+ for 12MP vs 5ms for 2MP)
- No perceptual benefit (screen is only 1080p-1440p)
- Causes OOM on low-end devices

**Do this instead:**
```kotlin
// BAD: Load full resolution
val bitmap = BitmapFactory.decodeFile(imagePath)
gpuImageView.setImage(bitmap) // 12MP texture upload

// GOOD: Downsample with Coil
val request = ImageRequest.Builder(context)
    .data(imageUri)
    .size(1080, 1920) // Match or slightly exceed screen size
    .build()
val bitmap = imageLoader.execute(request).drawable?.toBitmap()
gpuImageView.setImage(bitmap) // 2MP texture upload
```

### Anti-Pattern 2: Storing Bitmaps in Undo History

**What people do:** Store full Bitmap copies in ArrayList for undo/redo.

**Why it's wrong:**
- Each 2MP preview = 8MB memory
- 10 undo levels = 80MB+ memory usage
- Guaranteed OOM after a few edits

**Do this instead:**
```kotlin
// BAD: Store bitmaps
val undoStack = mutableListOf<Bitmap>()
undoStack.add(currentBitmap.copy()) // 8MB per copy

// GOOD: Store filter parameters (Command pattern)
data class FilterCommand(
    val filterType: FilterType,
    val params: Map<String, Float>
)

val undoStack = mutableListOf<FilterCommand>()
undoStack.add(FilterCommand(
    FilterType.GRAIN,
    mapOf("intensity" to 0.5f, "size" to 1.0f)
)) // ~100 bytes per command
```

### Anti-Pattern 3: Calling OpenGL from UI Thread

**What people do:** Call GPUImage methods directly from onClick/onChange callbacks.

**Why it's wrong:**
- OpenGL context is on separate thread
- Causes "called on wrong thread" crashes
- Undefined behavior, may appear to work but corrupt state

**Do this instead:**
```kotlin
// BAD: Direct call from UI thread
slider.setOnChangeListener { value ->
    gpuImageView.filter.setIntensity(value) // CRASH
}

// GOOD: Queue on GL thread
slider.setOnChangeListener { value ->
    gpuImageView.queueEvent {
        (gpuImageView.filter as? CustomFilter)?.setIntensity(value)
        gpuImageView.requestRender()
    }
}

// BETTER: Use ViewModel + State (Compose pattern)
@Composable
fun FilterControl(viewModel: EditorViewModel) {
    val intensity by viewModel.grainIntensity.collectAsState()
    Slider(
        value = intensity,
        onValueChange = { viewModel.updateGrainIntensity(it) }
    )
}
```

### Anti-Pattern 4: Recreating Filter Chain on Every Parameter Change

**What people do:** Rebuild entire GPUImageFilterGroup when single parameter changes.

**Why it's wrong:**
- Triggers shader recompilation (50-200ms)
- Destroys all filter state
- Causes visible lag/stutter

**Do this instead:**
```kotlin
// BAD: Rebuild entire chain
fun updateGrain(intensity: Float) {
    val newChain = buildFilterChain(params.copy(grain = intensity))
    gpuImageView.setFilter(newChain) // Recompiles all shaders
}

// GOOD: Update specific filter in-place
fun updateGrain(intensity: Float) {
    gpuImageView.queueEvent {
        val chain = gpuImageView.filter as GPUImageFilterGroup
        val grainFilter = chain.filters[3] as SignalDependentGrainFilter
        grainFilter.setIntensity(intensity)
        gpuImageView.requestRender()
    }
}

// EVEN BETTER: Keep filter references
class FilterManager {
    private var toneMapFilter: AcesToneMapFilter
    private var grainFilter: SignalDependentGrainFilter
    // ... other filters

    fun updateGrain(intensity: Float) {
        grainFilter.setIntensity(intensity)
    }
}
```

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **Android MediaStore** | Query for images, save exports via ContentResolver | Use scoped storage (Android 10+), request permissions at runtime |
| **Coil ImageLoader** | Singleton instance, ImageRequest with size constraints | Configure memory cache (25% app memory), disk cache (10% storage) |
| **OpenGL ES 3.0** | EGL context managed by GLSurfaceView | Minimum API 18 (Android 4.3), check GLES30 availability at runtime |
| **Material 3** | Jetpack Compose theming, dark mode by default | Use Surface for proper contrast, adapt controls to Material guidelines |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| **UI ↔ ViewModel** | StateFlow/State observation, event methods | Unidirectional data flow: state down, events up |
| **ViewModel ↔ Repository** | Suspend functions, Flow for continuous data | Use Dispatchers.IO for file operations, Dispatchers.Default for CPU work |
| **Repository ↔ GPUImage** | Direct method calls, Bitmap passing | Repository creates GPUImage instances, manages lifecycle |
| **Compose ↔ AndroidView** | Factory + update lambdas, avoid onReset for single views | Remember: update runs on recomposition, factory only once |
| **UI Thread ↔ GL Thread** | GLSurfaceView.queueEvent(), Handler.post() for callbacks | Never call OpenGL from UI thread, never call UI from GL thread directly |

## Build Order (Dependency-Driven)

Based on component dependencies, recommended build sequence:

### Phase 1: Foundation (Week 1)
1. **Data Layer First**
   - FilterParams data classes (no dependencies)
   - ImageRepository with Coil integration
   - Basic MediaStore utilities

2. **Core Rendering**
   - Custom GPUImageFilter subclasses (one at a time)
   - Test each filter in isolation with android-gpuimage sample app

### Phase 2: Preview Pipeline (Week 2)
3. **UI Layer**
   - ViewModel with StateFlow for filter params
   - GPUImageView wrapper in AndroidView
   - Basic EditorScreen with preview

4. **Filter Controls**
   - Slider components for numeric params
   - Color wheel for split toning
   - Wire controls to ViewModel

### Phase 3: Export Pipeline (Week 3)
5. **Export Service**
   - Full-resolution loading in ImageRepository
   - Separate GPUImage instance for export
   - MediaStore integration for saving

6. **Before/After Comparison**
   - Toggle between filtered and original
   - Split-view implementation

### Phase 4: Polish (Week 4)
7. **Performance Optimization**
   - Profile GPU memory usage
   - Optimize downsampling ratios
   - Add loading states

8. **Error Handling**
   - OOM recovery
   - Permission flows
   - Shader compilation failures

**Why this order:**
- Data layer has zero dependencies → build first
- Custom filters need testing before integration → build in isolation
- Preview must work before export (export uses same filter chain) → preview first
- Export is independent feature → can be built in parallel with controls
- Polish requires working system → comes last

## Sources

**Official Android Documentation:**
- [Android Graphics Architecture](https://source.android.com/docs/core/graphics/architecture) - System-level graphics pipeline (HIGH confidence)
- [Using Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose) - AndroidView integration patterns (HIGH confidence)
- [GLSurfaceView API Reference](https://developer.android.com/reference/android/opengl/GLSurfaceView) - OpenGL rendering thread (HIGH confidence)

**android-gpuimage Library:**
- [android-gpuimage GitHub](https://github.com/cats-oss/android-gpuimage) - Core architecture and filter implementation (HIGH confidence)
- [GPUImageFilter Source](https://github.com/cats-oss/android-gpuimage/blob/master/library/src/main/java/jp/co/cyberagent/android/gpuimage/filter/GPUImageFilter.java) - Custom shader integration (HIGH confidence)
- [Building a photo editing app with GPUImage](https://reintech.io/blog/building-photo-editing-app-android-gpuimage) - Usage patterns (MEDIUM confidence)

**Architecture Patterns:**
- [MVVM Architecture in Android](https://www.geeksforgeeks.org/android/mvvm-model-view-viewmodel-architecture-pattern-in-android/) - ViewModel integration (MEDIUM confidence)
- [ViewModel with Compose](https://developer.android.com/topic/libraries/architecture/viewmodel) - State management (HIGH confidence)
- [GPUImage GLSL Shaders Guide](https://medium.com/@chris.d.holman/gpuimage-glsl-shaders-5315ace97a50) - Shader patterns (MEDIUM confidence)

**Performance & Memory:**
- [Android Downsampling for Performance](https://medium.com/codex/android-downsampling-for-improved-performance-282a79586631) - Preview optimization (MEDIUM confidence)
- [Coil Image Loading](https://github.com/coil-kt/coil) - Memory-efficient loading (HIGH confidence)
- [OpenGL ES Memory Management](https://developer.samsung.com/game/opengl) - Texture optimization (MEDIUM confidence)
- [Undo/Redo Implementation](https://blog.fossasia.org/implementing-undo-and-redo-in-image-editor-of-phimpme-android/) - State management patterns (LOW confidence, single source)

**Threading & Rendering:**
- [GLSurfaceView queueEvent Pattern](https://learn.microsoft.com/en-us/dotnet/api/android.opengl.glsurfaceview.queueevent) - Cross-thread communication (HIGH confidence)
- [Android OpenGL ES Threading](https://www.learnopengles.com/tag/threading/) - Render thread concepts (MEDIUM confidence)

---
*Architecture research for: GPU-accelerated Android photo editing with OpenGL ES 3.0*
*Researched: 2026-02-25*
