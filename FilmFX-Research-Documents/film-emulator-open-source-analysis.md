# Open Source Components Analysis - Don't Reinvent the Wheel
## FilmFX Mobile App - Technical Decision Document

---

## Executive Summary

This document analyzes open source libraries and tools that can accelerate development of our physics-based film emulation app. After extensive research, we've identified **what to use** and **what to build ourselves**.

---

## 1. GPU Image Processing Frameworks

### 1.1 android-gpuimage (RECOMMENDED ✓)

**Repository**: https://github.com/cats-oss/android-gpuimage  
**Stars**: 9.2k | **License**: Apache 2.0 | **Status**: Actively maintained

#### What It Provides:
- **100+ GPU-accelerated filters** using OpenGL ES 2.0
- Filter chain support (compose multiple effects)
- Both GLSurfaceView and background processing modes
- Compatible with iOS GPUImage shaders (easy porting)

#### Included Filters (Relevant to Our Needs):
| We Need | android-gpuimage Has | Status |
|---------|---------------------|--------|
| Brightness/Contrast | ✓ GPUImageBrightnessFilter | USE |
| Saturation | ✓ GPUImageSaturationFilter | USE |
| RGB Tone Curve | ✓ GPUImageToneCurveFilter | USE |
| Vignette | ✓ GPUImageVignetteFilter | USE |
| Gaussian Blur | ✓ GPUImageGaussianBlurFilter | USE for bloom base |
| Sharpen | ✓ GPUImageSharpenFilter | USE |
| Color Matrix | ✓ GPUImageColorMatrixFilter | MODIFY for film stocks |
| Sepia | ✓ GPUImageSepiaToneFilter | REFERENCE |
| Lookup (LUT) | ✓ GPUImageLookupFilter | CONSIDER |
| Blend Modes | ✓ 30+ blend filters | USE for grain overlay |

#### What We Need to Build:
- **Signal-dependent film grain** (doesn't exist - needs custom shader)
- **Halation** (multi-scale blur + red tint - needs custom shader)
- **Split toning** (custom shader needed)
- **ACES tone mapping** (custom shader needed)

#### Integration:
```groovy
dependencies {
    implementation 'jp.co.cyberagent.android:gpuimage:2.1.0'
}
```

#### Recommendation: **USE AS FOUNDATION** - Saves 3-4 months of shader development

---

### 1.2 GPUPixel

**Repository**: https://github.com/pixpark/gpupixel  
**Focus**: Beauty filters, face detection integration

#### What It Provides:
- C++11 + OpenGL/ES cross-platform
- Built-in beauty filters (skin smoothing, whitening)
- Face slimming, eye enlargement
- Used by major apps (Volcano, BIGO)

#### Why NOT for Our Project:
- Focuses on face beautification, not film effects
- Limited film/grain/color grading filters
- Less documentation than android-gpuimage
- Less community support

#### Verdict: **SKIP** - Not a good fit for film emulation

---

### 1.3 libplacebo (Advanced - Consider for v2)

**Repository**: https://github.com/haasn/libplacebo  
**Use**: Video player core, HDR tone mapping

#### What It Provides:
- High-performance film grain synthesis (AV1/H.274)
- Multiple tone mapping algorithms (ACES, Reinhard, BT.2446, etc.)
- HDR to SDR conversion
- GPU-accelerated (Vulkan + OpenGL)
- Used by VLC, mpv, Kodi

#### Why NOT for v1:
- Heavy C++ library, complex integration
- Designed for video playback, not real-time image editing
- Large binary size (~2MB+)
- Academic/research focus

#### Verdict: **BOOKMARK FOR v2** - Excellent for video export in future versions

---

## 2. Film Grain Implementation

### 2.1 Academic Implementation (alasdairnewson/film_grain_rendering_gpu)

**Repository**: https://github.com/alasdairnewson/film_grain_rendering_gpu  
**Stars**: 53 | **License**: GPL v3+

#### Algorithm Details:
Based on the IPOL paper "A Stochastic Film Grain Model for Resolution-Independent Rendering"

| Parameter | Description | Default |
|-----------|-------------|---------|
| -r | Average grain radius | 0.1 |
| -grainSigma | Grain radius std deviation | 0.0 |
| -filterSigma | Gaussian filter std (pixels) | 0.8 |
| -algorithmID | 0=pixel-wise, 1=grain-wise | 0 |
| -NmonteCarlo | Simulation quality | 800 |

#### Implementation Approach:
The algorithm uses Monte Carlo simulation to generate grain that is:
- **Resolution-independent** (works at any scale)
- **Signal-dependent** (density varies with brightness)
- **Physically-based** (matches real film characteristics)

#### Our Approach:
We have two options:

**Option A: Adapt this algorithm to GLSL**
- Convert C++/CUDA Monte Carlo to shader-based noise
- Implement signal-dependent density function
- Create multi-resolution grain texture

**Option B: Implement signal-dependent grain ourselves**
Based on our technical plan, the formula is:
```glsl
float density = -log(1.0 - clamp(luminance, 0.01, 0.99));
vec3 grain = hash(uv * frequency + seed) * density * amount;
```

#### Verdict: **REFERENCE THIS, BUILD OUR OWN** - The algorithm provides the physics, we implement in shaders

---

### 2.2 GPUOpen Film Grain (AMD)

**Reference**: https://gpuopen.com/learn/vdr-follow-up-fine-art-of-film-grain/

#### What It Provides:
- Symmetric grain ideal for traditional transfer functions (sRGB)
- Temporal grain synthesis for video
- Discussion of grain in HDR workflows

#### Key Insight:
AMD's implementation uses symmetric grain generation, which is simpler than the full Monte Carlo approach but still looks realistic for standard SDR content.

---

## 3. Image Loading & Caching

### 3.1 Coil (RECOMMENDED ✓)

**Repository**: https://github.com/coil-kt/coil  
**Size**: ~94KB | **Language**: Kotlin-first | **License**: Apache 2.0

#### Why Coil:
| Feature | Coil | Glide | Fresco |
|---------|------|------|--------|
| Size | 94KB | 222KB | 500KB+ |
| Kotlin-first | ✓ | ✗ | ✗ |
| Compose support | ✓ | Partial | ✗ |
| Coroutines | ✓ | ✗ | ✗ |
| Memory efficiency | ★★★★★ | ★★★★ | ★★★ |

#### Features for Our App:
- **ImageRequest** - Load, decode, transform
- **Memory cache** - LRU, configurable
- **Disk cache** - Automatic
- **Downsampling** - Reduce memory for large images
- **Transformed** - Apply transformations during load
- **Bitmap pooling** - Reuse bitmaps

#### Integration:
```groovy
// Coil 3.x (latest)
implementation("io.coil-kt.coil3:coil-compose:3.4.0")

// For our use case
val imageRequest = ImageRequest.Builder(context)
    .data(uri)
    .size(1024, 1024)  // Downsample for preview
    .memoryCacheKey(key)
    .transform { bitmap, chain ->
        // Apply initial transforms
        chain.proceed(bitmap)
    }
    .build()
```

#### What We Still Need:
- Custom transformations for film effects (GPU-based)
- Integration with our OpenGL pipeline
- Memory management for large image editing

#### Verdict: **USE** - Modern, lightweight, Kotlin-native

---

### 3.2 Glide

**Use Case**: If you need broad device compatibility or have existing Glide code

#### Pros:
- Most mature Android image library
- Excellent disk caching
- Great for gallery apps
- Extensive documentation

#### Cons:
- Larger than Coil
- Java-based, not Kotlin-first
- More complex API

#### Verdict: **USE COIL INSTEAD** - Better fit for modern Android development

---

## 4. Color Space & Tone Mapping

### 4.1 ACES Implementation (RECOMMENDED - USE DIRECTLY)

**Source**: Krzysztof Narkowicz's blog (public domain implementation)

```glsl
// Direct implementation - no library needed
float3 ACESFilm(float3 x) {
    float a = 2.51f;
    float b = 0.03f;
    float c = 2.43f;
    float d = 0.59f;
    float e = 0.14f;
    return saturate((x * (a * x + b)) / (x * (c * x + d) + e));
}
```

This is the industry-standard ACES tone curve, used in:
- Unreal Engine
- Unity (HDRP)
- DaVinci Resolve
- Blender

#### Verdict: **COPY DIRECTLY** - Public domain, one-line implementation

---

### 4.2 John Hable Filmic Curve

**Source**: "Filmic Tonemapping with Piecewise Power Curves" (public domain)

```glsl
// From Uncharted 2 - also public domain
float3 HableTonemap(float3 x) {
    float3 a = x * 0.15f;
    float3 b = x * 0.50f;
    float3 c = x * 0.26f;
    float3 d = x * 0.20f;
    float3 e = x * 0.02f;
    return saturate((e + a) / (b + c + 1.0f));
}
```

#### Verdict: **COPY DIRECTLY** - Simple, effective, public domain

---

### 4.3 OpenColorIO (OCIO)

**Repository**: https://github.com/AcademySoftwareFoundation/OpenColorIO  
**Industry Use**: Hollywood film/TV production

#### What It Provides:
- Complete color management framework
- LUT support (.cube, .3dl, CLF)
- Color space transformations
- Film looks via LUTs
- ACES integration
- 50+ supported applications

#### Why NOT for v1:
- Heavy library (~5MB+)
- Complex configuration
- No native Android support
- Designed for desktop pipelines

#### Alternative for Film Looks:
Create our own simple LUT loader using:
```glsl
// 3D LUT lookup in shader
vec3 applyLUT(vec3 color, sampler2D lut) {
    float sliceSize = 1.0 / 32.0;
    float slicePixelSize = sliceSize / 32.0;
    float sliceInnerSize = slicePixelSize * 31.0;
    
    float blueSlice0 = floor(color.b * 31.0);
    float xOffset = slicePixelSize * 0.5;
    float yOffset = slicePixelSize * 0.5;
    
    vec2 uv = vec2(
        xOffset + (color.r * sliceInnerSize) + (blueSlice0 * slicePixelSize),
        yOffset + (color.g * sliceInnerSize)
    );
    
    return texture(lut, uv).rgb;
}
```

#### Verdict: **SKIP FOR NOW** - Too heavy; implement simple LUT support instead

---

## 5. Image Resizing & Scaling

### 5.1 Android Built-in (RECOMMENDED ✓)

**Bitmap.createScaledBitmap()**: 
- Bilinear filtering by default
- Good enough for most preview resizing
- No additional dependency

```kotlin
val scaled = Bitmap.createScaledBitmap(
    original,
    targetWidth,
    targetHeight,
    Filter.TRUE  // Bilinear
)
```

#### Performance:
- 12MP → 1MP: ~50ms on Pixel 7
- Sufficient for preview generation

---

### 5.2 RenderScript Intrinsics Replacement Toolkit

**Repository**: https://github.com/android/renderscript-intrinsics-replacement-toolkit  
**Status**: Active development (v0.8 Beta)

#### What It Provides:
| Function | Description |
|----------|-------------|
| Blur | Gaussian blur |
| Resize | Image resizing |
| Blend | Image blending |
| Color Matrix | Color transformation |
| Convolve | Convolution operations |
| LUT | 1D Lookup Table |
| LUT 3D | 3D Lookup Table |
| YUV to RGB | Color space conversion |

#### Performance:
- **2x faster** than deprecated RenderScript
- Multithreaded CPU execution
- NEON/SSE optimization

#### Integration:
```groovy
dependencies {
    implementation 'androidx.renderscript:renderscript-intrinsics-replacement-toolkit:0.8-beta'
}
```

#### Why NOT:
- Deprecated path (RenderScript is deprecated in Android 12+)
- CPU-based (slower than GPU)
- Limited functionality

#### Verdict: **SKIP** - Use GPU-based resizing instead (android-gpuimage handles this)

---

### 5.3 FFmpeg/libswscale

**Use Case**: If we need highest quality Lanczos/Bicubic

#### Why It's Overkill:
- Large native library (~10MB+)
- Complex NDK integration
- Most apps don't need this quality level

#### Verdict: **SKIP** - Android's built-in scaling + GPU is sufficient

---

## 6. Image Format Support

### 6.1 HEIC/HEIF Support

| Approach | Pros | Cons |
|----------|------|------|
| **Android 11+ Built-in** | No extra code | Limited to Android 11+ |
| **BitmapFactory** | Simple | Decode to Bitmap first |
| **HeifDecoder** (AOSP) | More control | Limited docs |
| **libheif** | Full support | Native library |

#### Recommendation:
```kotlin
// Simple approach - rely on system
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    // Use built-in ImageDecoder
    val source = ImageDecoder.createSource(contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source)
} else {
    // Fallback for older devices - use Coil which handles this
    imageLoader.execute(request)
}
```

#### coil handles HEIC automatically on supported devices!

---

### 6.2 EXIF Handling

**Library**: AndroidX ExifInterface

```groovy
implementation 'androidx.exifinterface:exifinterface:1.3.7'
```

#### What It Provides:
- Read/write EXIF metadata
- Orientation correction
- Preserve camera info in exports

---

## 7. Camera Integration

### 7.1 CameraX (RECOMMENDED ✓)

**What It Provides:**
- Camera2 API abstraction
- ImageAnalysis for real-time processing
- ImageCapture for high-quality photos
- YUV to RGB conversion built-in

```kotlin
val camera = cameraProvider.bindToLifecycle(
    this,
    cameraSelector,
    preview,
    imageCapture,
    imageAnalysis
)

// YUV to RGB in analyzer
imageAnalysis.setAnalyzer(executor) { image ->
    // Process in background
    processImage(image) // Use GPU for processing
}
```

#### Verdict: **USE** - Standard Android camera library

---

### 7.2 YUV Conversion

**Options:**

| Method | Speed | Quality |
|--------|-------|---------|
| CameraX built-in | Fast | Good |
| libyuv (Chromium) | Fastest | Good |
| Custom shader | Fast | Best |

#### Recommendation:
Use **CameraX's built-in YUV→RGB** for initial implementation. If performance is an issue, consider libyuv for v2.

---

## 8. Summary: What to Use vs What to Build

### ✅ USE THESE (Save 6-9 months)

| Component | Library | Effort Saved |
|-----------|---------|--------------|
| GPU Filters | android-gpuimage | 3-4 months |
| Image Loading | Coil | 1 month |
| Basic Filters (blur, contrast, etc.) | android-gpuimage | 1 month |
| ACES Tone Mapping | Copy from Krzysztof Narkowicz | 1 week |
| Camera | CameraX | 2 months |
| Color Matrix | android-gpuimage | 1 week |

### 🛠️ BUILD OURSELVES (Core Differentiators)

| Component | Why | Effort |
|-----------|-----|--------|
| Signal-Dependent Grain | Physics-based, unique to our app | 2-3 weeks |
| Halation Effect | Multi-scale blur + color shift | 2-3 weeks |
| Split Toning | Custom shader | 1 week |
| Film Stock Presets | Color matrices + effects | 2 weeks |
| Real-time Preview Pipeline | Optimized GPU pipeline | 3-4 weeks |

### ❌ SKIP THESE

| Component | Reason |
|-----------|--------|
| libplacebo | Too heavy for v1 |
| OCIO | Overkill, no Android support |
| RenderScript Toolkit | Deprecated path |
| FFmpeg/libswscale | Overkill |
| GPUPixel | Not film-focused |

---

## 9. Recommended Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    FILMFX ARCHITECTURE                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                  UI LAYER (Compose)                  │   │
│  │  - Screens (Home, Editor, Export)                   │   │
│  │  - ViewModels                                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              COIL (Image Loading)                   │   │
│  │  - Load from gallery/camera                        │   │
│  │  - Downsample for preview                          │   │
│  │  - Memory + disk caching                           │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         ANDROID-GPUIMAGE (Base Filters)            │   │
│  │  - Brightness/Contrast/Saturation                  │   │
│  │  - Basic blur (for bloom base)                     │   │
│  │  - Vignette                                        │   │
│  │  - ColorMatrix                                     │   │
│  │  - Blend modes (for grain)                         │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│           ┌───────────────┼───────────────┐                 │
│           ▼               ▼               ▼                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │  CUSTOM    │  │   CUSTOM   │  │   CUSTOM   │         │
│  │  HALATION │  │   GRAIN    │  │ SPLIT TONE │         │
│  │  SHADER   │  │   SHADER   │  │   SHADER   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│           │               │               │                 │
│           └───────────────┼───────────────┘                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           CUSTOM TONE MAPPING SHADER                │   │
│  │  - ACES Filmic (from Krzysztof Narkowicz)         │   │
│  │  - Hable (from Uncharted 2)                        │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                 │
│                           ▼                                 │
│  ┌─────────────────────────────────────────────────────┐   │
│  │               EXPORT (to gallery)                   │   │
│  │  - Full resolution render                          │   │
│  │  - Preserve EXIF                                   │   │
│  │  - Coil for file output                            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 10. Development Timeline with These Decisions

| Phase | Task | Duration |
|-------|------|----------|
| **1** | Setup project + integrate android-gpuimage + Coil | 2 weeks |
| **2** | Implement camera capture + image import | 1 week |
| **3** | Build custom halation shader | 2 weeks |
| **4** | Build signal-dependent grain shader | 2 weeks |
| **5** | Build split toning shader | 1 week |
| **6** | Add tone mapping (ACES, Hable) | 1 week |
| **7** | Create 5 film presets | 1 week |
| **8** | Real-time preview optimization | 2 weeks |
| **9** | Export pipeline | 1 week |
| **10** | Testing + polish | 2 weeks |

**Total: ~15 weeks** (vs ~24 weeks if building everything from scratch)

**Savings: ~9 weeks (38%)**

---

## Appendix A: Library Quick Reference

| Library | Version | Maven Coordinate | Purpose |
|---------|---------|------------------|---------|
| android-gpuimage | 2.1.0 | `jp.co.cyberagent.android:gpuimage:2.1.0` | GPU filters |
| Coil | 3.4.0 | `io.coil-kt.coil3:coil-compose:3.4.0` | Image loading |
| Coil Network | 3.4.0 | `io.coil-kt.coil3:coil-network-okhttp:3.4.0` | Network images |
| CameraX | 1.3.x | `androidx.camera:camera-*` | Camera |
| ExifInterface | 1.3.7 | `androidx.exifinterface:exifinterface:1.3.7` | EXIF metadata |

---

## Appendix B: Resources

- android-gpuimage: https://github.com/cats-oss/android-gpuimage
- Coil: https://coil-kt.github.io/coil/
- Film grain algorithm: https://github.com/alasdairnewson/film_grain_rendering_gpu
- ACES implementation: https://knarkowicz.wordpress.com/2016/01/06/aces-filmic-tone-mapping-curve/
- Hable tonemapping: http://filmicworlds.com/blog/filmic-tonemapping-with-piecewise-power-curves/
- CameraX: https://developer.android.com/jetpack/androidx/releases/camera
- OCIO: https://opencolorio.org/

---

*Document prepared for FilmFX development decisions*
