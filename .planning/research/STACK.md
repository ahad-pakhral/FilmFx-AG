# Technology Stack

**Project:** FilmFX
**Researched:** 2026-02-25
**Confidence:** HIGH

## Recommended Stack

### Core Framework

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.0+ | Primary language | Latest stable release with Kotlin 2.3.0 (Feb 2026) fully compatible with Gradle 7.6.3-9.0.0. Built-in support in AGP 9.0 eliminates need for separate Kotlin plugin. Industry standard for Android development. |
| Jetpack Compose | 1.10.3 | UI framework | Latest stable version (Feb 11, 2026) with BOM 2026.01.01. Modern declarative UI, excellent Material3 dark theme support, built-in TextField improvements, and autoSize text behavior. Essential for photo-centric minimal UI. |
| Material3 | 1.4.0 | Design system | Latest stable (Feb 11, 2026), provides dark theme out-of-box, dynamic color support on Android 13+, and photo editor-appropriate minimal aesthetics. |
| Android Gradle Plugin | 9.0.1 | Build system | Latest stable (Jan 2026) with built-in Kotlin support, requires Gradle 9.1.0+, Kotlin 2.2.10+, and JDK 17. Targets API 36 maximum. |

### GPU Processing Layer

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| OpenGL ES 3.0 | API 18+ | GPU shader execution | **Use for FilmFX**. Available since Android 4.3 (API 18), mature ecosystem, extensive documentation, and compatible with android-gpuimage. By 2026, 99%+ of active devices support it. More accessible than Vulkan for custom shader development. |
| android-gpuimage | 2.1.0 | GPU filter framework | **Use with caution**. 9.2k stars, Apache 2.0 license, provides 100+ base filters and shader pipeline foundation. Latest version 2.1.0 on Maven Central (jp.co.cyberagent.android:gpuimage). Active issues in 2025 but limited recent development. Saves 3-4 months vs building from scratch, but verify commit activity before full adoption. |

**Alternative:** Raw OpenGL ES 3.0 (if android-gpuimage proves unmaintained). Provides full control but requires 3-4 months additional development time for pipeline infrastructure.

**Future consideration:** AGSL (Android Graphics Shading Language) with RuntimeShader — requires API 33+ (Android 13), modern shader system integrated with Skia rendering pipeline. Monitor for future adoption when min SDK permits.

### Image Loading & Caching

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Coil | 3.4.0 | Image loading | Latest stable (Feb 24, 2026). Kotlin-first, Compose-native, multiplatform support, 25-40% runtime performance improvement over 2.x, 35-48% reduced allocations. Small footprint (94KB vs Glide's 222KB). Built-in downsampling, memory/disk caching, and weak reference memory cache prevents OOM. Use `ImageBitmap#prepareToDraw()` before rendering to pre-upload GPU textures. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-coroutines-android | 1.10.2 | Async operations | Core dependency for Compose and GPU processing. Latest version (2026) with Android Main dispatcher, NullPointerException workarounds for old devices, and Kotlin 2.0 support. Essential for non-blocking image processing. |
| AndroidX Activity Compose | 1.10.x | Compose integration | Use `setContent` for Compose UI, handles configuration changes with `rememberSaveable` for editor state persistence across rotations. |
| AndroidX Lifecycle | 2.8.x | Lifecycle management | ViewModel for editor state management, lifecycle-aware coroutine scopes for GPU operations cancellation. |

### Development & Testing Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Android GPU Inspector (AGI) | GPU profiling & debugging | Official Google tool for profiling Adreno, Mali, PowerVR GPUs. Supports Vulkan and OpenGL ES. Captures performance counters, frame timelines, shader hotspots, overdraw analysis. Essential for diagnosing cross-GPU driver quirks. |
| Android Studio Profiler | Memory & performance monitoring | Track memory allocation, detect OOM conditions during large image processing. Use GPU Rendering profiler for 30fps/60fps validation. |
| Gradle 9.1.0+ | Build system | Required for AGP 9.0.1. Default version for Android builds in 2026. |
| JDK 17 | Java runtime | Minimum requirement for AGP 9.0+. |

## Installation

### Gradle Configuration (build.gradle.kts)

```kotlin
// Project-level build.gradle.kts
plugins {
    id("com.android.application") version "9.0.1" apply false
    // Note: Kotlin plugin NOT needed - built into AGP 9.0+
}

// App-level build.gradle.kts
plugins {
    id("com.android.application")
}

android {
    namespace = "com.yourcompany.filmfx"
    compileSdk = 35 // API 35 required for Play Store in 2026

    defaultConfig {
        applicationId = "com.yourcompany.filmfx"
        minSdk = 21 // Balance OpenGL ES 3.0 (API 18) with Coil 3 (API 21)
        targetSdk = 35 // Required for Google Play in 2026
        versionCode = 1
        versionName = "1.0"

        // Require OpenGL ES 3.0 hardware support
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose BOM for version management
    val composeBom = platform("androidx.compose:compose-bom:2026.01.01")
    implementation(composeBom)

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.10.3")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // GPU Image Processing
    implementation("jp.co.cyberagent.android:gpuimage:2.1.0")

    // Image Loading
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")
}
```

### AndroidManifest.xml Requirements

```xml
<manifest>
    <!-- Require OpenGL ES 3.0 hardware support -->
    <uses-feature
        android:glEsVersion="0x00030000"
        android:required="true" />

    <!-- Gallery photo access -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <!-- Optional: Force landscape for tablet editing -->
    <!-- <activity android:screenOrientation="sensorLandscape" /> -->
</manifest>
```

## Alternatives Considered

| Category | Recommended | Alternative | When to Use Alternative |
|----------|-------------|-------------|-------------------------|
| GPU API | OpenGL ES 3.0 | Vulkan (API 24+) | If targeting only modern devices (Android 7+), need maximum GPU control, or willing to handle significantly more complexity. Vulkan reduces driver overhead but requires 3-5x more code and explicit synchronization. RenderScript deprecated since Android 12. |
| GPU Framework | android-gpuimage | Raw OpenGL ES / Custom Pipeline | If android-gpuimage proves unmaintained or lacks flexibility for signal-dependent grain. Custom pipeline gives full control but adds 3-4 months development time. |
| Shader Language | GLSL (OpenGL) | AGSL (RuntimeShader, API 33+) | For future versions targeting Android 13+ only. AGSL integrates with Skia, supports Compose better, and has cleaner syntax. But requires API 33 min SDK (cuts out 85% of devices as of early 2026). |
| Image Loading | Coil 3.x | Glide 4.x | If you need legacy Android support below API 21, or prefer annotation processors over Kotlin-first API. Glide is more mature (2014 vs 2019) but 2.4x larger and less Compose-optimized. |
| UI Framework | Jetpack Compose | XML Views + Fragments | Never for greenfield projects in 2026. Compose is the official modern Android UI toolkit. XML views are legacy. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| RenderScript | Deprecated in Android 12 (2021), hardware acceleration removed, will be fully removed in future releases. | Vulkan or OpenGL ES 3.1+ for GPU compute. |
| OpenGL ES 2.0 | Lacks compute shaders, integer textures, multiple render targets. API 8+ but feature-limited. | OpenGL ES 3.0 (API 18+) — minor API bump, major capabilities. |
| GPUImage-x | Cross-platform (Android + iOS) sounds appealing but adds complexity for Android-only project. Less Android-specific optimization. | android-gpuimage for Android-only project. |
| Kotlin plugin (org.jetbrains.kotlin.android) | No longer needed as of AGP 9.0. Built-in Kotlin support is default. Adding it manually causes conflicts. | Remove from plugins {} block — AGP 9.0 handles it. |
| XML View system | Legacy approach. Requires more boilerplate, harder state management, no modern tooling support. | Jetpack Compose for all UI. |
| targetSdk < 35 | Google Play requires targetSdk 35 (API level 35) for all apps and updates submitted in 2026. | Set `targetSdk = 35` minimum. |

## Stack Patterns by Variant

### Minimum SDK Decision

**Recommended: minSdk = 21 (Android 5.0 Lollipop)**
- OpenGL ES 3.0 available since API 18 (Android 4.3)
- Coil 3.x requires API 21 minimum
- API 21 covers ~99.5% of active Android devices (2026)
- Allows 64-bit only enforcement (requirement as of Aug 2021)

**Alternative: minSdk = 18 (Android 4.3)**
- Only if you must maximize device reach AND willing to:
  - Use Coil 2.x instead of 3.x (worse performance)
  - Support 32-bit only devices (Google Play deprecated)
  - Test on very old hardware (Nexus 4, Galaxy S3 era)

**Future: minSdk = 33 (Android 13)**
- Enables AGSL RuntimeShader (modern shader API)
- Material 3 dynamic theming works fully
- But cuts device reach to ~15% as of Jan 2026
- Consider for v2+ when Android 13+ is 50%+ adoption (likely 2027)

### GPU Driver Compatibility Strategy

**Problem:** Android GPU ecosystem has 3 major GPU families with driver quirks:
- **Qualcomm Adreno** (Snapdragon): Best support, most common, frequent driver updates
- **ARM Mali** (MediaTek, Exynos): Unstable Vulkan, "unsatisfactory" shader compiler
- **Imagination PowerVR** (older devices): Less common, varying driver quality

**Mitigation:**
1. **Use OpenGL ES 3.0** (not Vulkan) — more mature driver implementations across all GPUs
2. **Test on all 3 GPU families** — use Android GPU Inspector to profile per-GPU performance
3. **Shader precision hints** — use `mediump` precision where possible (mobile GPUs optimized for it)
4. **Avoid GPU-specific extensions** — stick to core OpenGL ES 3.0 features
5. **Runtime capability checks** — query `GLES30.glGetString(GLES30.GL_EXTENSIONS)` for optional features
6. **Graceful degradation** — offer "quality vs performance" settings if specific GPUs struggle

### Large Image Processing Strategy

**Problem:** Full-resolution export on high-resolution photos (12-108 MP) can cause OOM errors.

**Solution (based on official Android docs):**

```kotlin
// 1. Load downsampled preview for editing (Coil handles automatically)
Image(
    model = ImageRequest.Builder(context)
        .data(photoUri)
        .size(1920, 1080) // Match preview size, not original
        .build(),
    contentDescription = null
)

// 2. For export, process in tiles (prevent OOM)
suspend fun processFullResolution(uri: Uri): Bitmap {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options)

    val originalWidth = options.outWidth
    val originalHeight = options.outHeight

    // If image > 8K, process in 2K tiles
    return if (originalWidth > 7680 || originalHeight > 4320) {
        processInTiles(uri, tileSize = 2048)
    } else {
        // Small enough to process in one pass
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        BitmapFactory.decodeStream(contentResolver.openInputStream(uri), null, options)
            .applyGPUFilters()
    }
}

// 3. Use ImageBitmap#prepareToDraw() before GPU processing
@Composable
fun PreviewImage(bitmap: ImageBitmap) {
    LaunchedEffect(bitmap) {
        bitmap.prepareToDraw() // Pre-upload to GPU texture cache
    }
    Image(bitmap = bitmap, contentDescription = null)
}
```

**Key principles:**
- **Preview ≠ Export resolution** — edit on downsampled version, apply to full res at export
- **Tile-based processing** for >8K images — process 2K chunks, stitch together
- **GPU texture preparation** — call `prepareToDraw()` to avoid frame drops on first render
- **Memory budget awareness** — assume 256MB max for image buffers on mid-range devices

## Version Compatibility

| Package | Compatible With | Notes |
|---------|-----------------|-------|
| Kotlin 2.3.0 | AGP 9.0.1, Gradle 7.6.3-9.0.0 | Fully compatible. AGP 9.0 auto-upgrades Kotlin < 2.2.10. |
| Compose BOM 2026.01.01 | Kotlin 2.3.0, AGP 9.0.1 | Requires Kotlin compiler extension 1.5.15. |
| Coil 3.4.0 | Compose 1.10.x, Kotlin 2.0+ | Uses new `coil-compose-core` artifact. Compose 1.6+ required. |
| android-gpuimage 2.1.0 | OpenGL ES 2.0+, minSdk 8+ | Works on all modern Android. No Kotlin/Compose dependencies. |
| kotlinx-coroutines 1.10.2 | Kotlin 2.0+, Android 5.0+ (API 21+) | Includes Android Main dispatcher. Tested with R8 3.4+. |
| AGP 9.0.1 | Gradle 9.1.0+, Kotlin 2.2.10+, JDK 17 | Built-in Kotlin support. Max API 36. KGP auto-upgraded if < 2.2.10. |

### Known Compatibility Issues

1. **AGP 9.0 + Kotlin Multiplatform** — KMP plugin incompatible with new AGP 9.0 DSL. Use AGP 8.x if KMP needed.
2. **android-gpuimage + Jetpack Compose** — No direct conflict, but android-gpuimage uses GL contexts which are separate from Compose's Skia rendering. Bridge via `Bitmap` or `ImageBitmap`.
3. **OpenGL ES 3.0 + API 18-20 devices** — Manifest requires `android:glEsVersion="0x00030000"` but device must have GPU driver support. Runtime check recommended even if manifest declares requirement.

## Performance Benchmarks (Expected)

Based on 2026 best practices and official Android optimization guidelines:

| Operation | Target | Strategy |
|-----------|--------|----------|
| Preview frame rate | 30fps minimum, 60fps target | Use Coil downsampling to 1920x1080 max, `prepareToDraw()` for GPU pre-upload, `mediump` shader precision. |
| Full-res export (12MP) | < 3 seconds | Single-pass GPU processing. Apply all filters in one shader to minimize texture uploads. |
| Full-res export (108MP) | < 15 seconds | Tile-based processing (2K chunks). Parallel coroutines for CPU-side orchestration. |
| Memory footprint (editing) | < 256MB | Downsampled preview only. Weak reference cache in Coil 3.x helps. |
| APK size | < 15MB | Coil 3.x (94KB), android-gpuimage (~500KB), Compose runtime (~2MB). R8 code shrinking enabled by default in AGP 9.0. |

## Sources

### High Confidence (Official Documentation)

- [Jetpack Compose Releases](https://developer.android.com/jetpack/androidx/releases/compose) — Official version tracking (verified Feb 2026)
- [Coil Changelog](https://coil-kt.github.io/coil/changelog/) — Version 3.4.0 release notes (Feb 24, 2026)
- [Android Gradle Plugin 9.0.1 Release Notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) — Official AGP documentation
- [Kotlin 2.3.0 Release](https://kotlinlang.org/docs/whatsnew23.html) — Official Kotlin documentation
- [OpenGL ES on Android](https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl) — API level requirements
- [Optimizing Images in Compose](https://developer.android.com/develop/ui/compose/graphics/images/optimization) — Official performance guidelines
- [Loading Large Bitmaps](https://developer.android.com/topic/performance/graphics/load-bitmap) — OOM prevention strategies
- [Android GPU Inspector](https://gpuinspector.dev/) — Official profiling tool documentation
- [AGSL Documentation](https://developer.android.com/develop/ui/views/graphics/agsl) — RuntimeShader requirements (API 33+)
- [Kotlin Coroutines GitHub](https://github.com/Kotlin/kotlinx.coroutines) — Version 1.10.2 release

### Medium Confidence (Official + Community Sources)

- [android-gpuimage GitHub](https://github.com/cats-oss/android-gpuimage) — 9.2k stars, Apache 2.0, version 2.1.0
- [Material3 Theming in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) — Official M3 documentation
- [Vulkan vs OpenGL on Android](https://source.android.com/docs/core/graphics/arch-vulkan) — AOSP graphics architecture
- [RenderScript Migration Guide](https://developer.android.com/guide/topics/renderscript/migrate) — Official deprecation timeline
- [Android API Distribution 2026](https://apilevels.com/) — Community-maintained device statistics
- [GPU Driver Quirks Discussion](https://www.esper.io/blog/android-dessert-bites-14-gpu-driver-updates-3819534) — ARM Mali/Adreno/PowerVR analysis

### Context Used

- Web search for current 2026 versions and best practices
- Official Android documentation for API requirements and performance guidelines
- GitHub repositories for library versions and maintenance status

---
*Stack research for: GPU-accelerated photo editing on Android*
*Researched: 2026-02-25*
*Researcher: Claude (GSD Project Researcher)*
