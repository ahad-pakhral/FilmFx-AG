# Product Requirements Document (PRD) - VERSION 3.0
# FilmFX - Physics-Based Film Emulation App

---

## Document Information

| Field | Value |
|-------|-------|
| **Version** | 3.0 (Expert-Research-Enhanced) |
| **Date** | 2026-02-25 |
| **Status** | Draft |
| **Platform** | Android (v1) |

---

## 1. Executive Summary

### 1.1 Product Overview

**FilmFX** is a mobile application that applies **authentic physics-based film emulation effects** to photos, featuring:
- **Halation** - The signature red-orange glow unique to analog film
- **Signal-Dependent Film Grain** - Grain density varies with brightness (the key differentiator)
- **Bloom** - Light scattering from lens imperfections
- **Split Toning** - Color grading technique used in Hollywood
- **Tone Mapping** - Cinematic highlight rolloff

### 1.2 Problem Statement

Users want to give their digital photos a cinematic, film-like aesthetic. Existing solutions either:
- Apply superficial filters that don't capture authentic film characteristics
- **Lack signal-dependent grain** - the #1 differentiator from real film
- **Don't properly simulate halation** - red-orange glow around bright areas
- Require expensive desktop software (DaVinci Resolve, Dehancer)
- Lack real-time preview

### 1.3 What Makes Film Look "Film"

Based on extensive expert research:

| Effect | Expert Perspective | Why It Matters |
|--------|-------------------|-----------------|
| **Signal-Dependent Grain** | "Add more grain into highlights than in shadows. In negative film, blacks were almost clean." - Colorists | Grain density ∝ brightness - this is the KEY differentiator |
| **Halation** | "Red-orange halo near contrasting boundaries of over-exposed areas" - Dehancer | Film-specific physics, not just blur |
| **Tone Mapping** | "Gradual rolloff rather than immediate hard clip" - Noam Kroll | Cinematic vs clinical look |
| **Split Toning** | "Warm highlights + cool shadows = most popular" - DIY Photography | Color character |

---

## 2. Research Foundation

This PRD is built on extensive expert research:

### Key Expert Sources
- **Dehancer** - Industry-leading film emulation (Hollywood colorists)
- **Frame.io** - Professional colorist workflows
- **VSCO** - Mobile film emulation pioneers
- **The Darkroom** - Film lab technical expertise
- **Professional cinematographers** - Real-world film experience

### Key Findings

1. **Signal-Dependent Grain** is THE critical differentiator
2. **Halation** has specific red-orange color from film emulsion layers
3. **Processing order matters** - Grain should be applied BEFORE color grading
4. **Film compression** - Highlights roll off gradually, don't clip

---

## 3. User Stories

### 3.1 Core User Stories

| ID | User Story | Acceptance Criteria |
|----|------------|-------------------|
| **US1** | As a user, I want realistic film grain | Grain appears DENSER in bright areas (signal-dependent) |
| **US2** | As a user, I want halation around bright lights | Red-orange glow appears around highlights |
| **US3** | As a user, I want cinematic highlights | Highlights roll off smoothly, don't clip |
| **US4** | As a user, I want warm highlights + cool shadows | Split toning with customizable colors |

### 3.2 Detailed User Stories

```
US1: Signal-Dependent Film Grain
─────────────────────────────────
Given: User applies film grain effect
When: The image has bright highlights and dark shadows
Then: Grain should appear DENSER in bright areas (highlights)
And: Grain should appear SPARSER in dark areas (shadows)
And: This matches real film physics (NOT uniform noise)
And: Grain varies per RGB channel (like real color film)

US2: Halation Effect
─────────────────────────────────
Given: User enables halation
When: Looking at bright light sources or high-contrast edges
Then: A warm red-orange glow should appear
And: NOT just a white blur (must be colored)
And: Should appear at edges of bright areas, not everywhere

US3: Cinematic Tone Mapping
─────────────────────────────────
Given: User applies tone mapping
When: Image has very bright areas (overexposed)
Then: Bright areas should roll off GRADUALLY
And: Should NOT clip to pure white abruptly
And: Should preserve highlight detail/texture

US4: Split Toning
─────────────────────────────────
Given: User adjusts split toning
When: Image has highlights and shadows
Then: Highlights should take on ONE color (e.g., orange)
And: Shadows should take on ANOTHER color (e.g., teal)
And: Transition between colors should be smooth
```

---

## 4. Functional Requirements

### 4.1 Core Features

#### F1: Signal-Dependent Film Grain (MUST HAVE)
| ID | Requirement | Priority |
|----|-------------|----------|
| F1.1 | Grain density varies with brightness | **MUST HAVE** |
| F1.2 | Per-channel RGB grain (3 layers) | Should Have |
| F1.3 | Adjustable grain size/frequency | Must Have |
| F1.4 | ISO-based presets (50/250/500) | Should Have |
| F1.5 | Negative vs Positive film types | Should Have |

#### F2: Halation Effect (MUST HAVE)
| ID | Requirement | Priority |
|----|-------------|----------|
| F2.1 | Red-orange colored glow | **MUST HAVE** |
| F2.2 | Multi-scale blur for falloff | Must Have |
| F2.3 | Threshold control (source limiter) | Must Have |
| F2.4 | Radius/diffusion control | Must Have |
| F2.5 | Hue control (red vs orange) | Should Have |
| F2.6 | Global diffusion (midtone warmth) | Should Have |

#### F3: Tone Mapping (MUST HAVE)
| ID | Requirement | Priority |
|----|-------------|----------|
| F3.1 | ACES Filmic tone mapping | **MUST HAVE** |
| F3.2 | Highlight rolloff (film compression) | Must Have |
| F3.3 | Shadow rolloff | Should Have |

#### F4: Split Toning (MUST HAVE)
| ID | Requirement | Priority |
|----|-------------|----------|
| F4.1 | Highlight color picker | **MUST HAVE** |
| F4.2 | Shadow color picker | **MUST HAVE** |
| F4.3 | Balance point control | Must Have |
| F4.4 | Intensity control | Must Have |

#### F5: Bloom Effect
| ID | Requirement | Priority |
|----|-------------|----------|
| F5.1 | Adjustable threshold | Should Have |
| F5.2 | Adjustable radius | Should Have |
| F5.3 | Adjustable intensity | Should Have |

#### F6: Vignette
| ID | Requirement | Priority |
|----|-------------|----------|
| F6.1 | Radial darkening | Should Have |
| F6.2 | Adjustable intensity | Should Have |
| F6.3 | Adjustable smoothness | Should Have |

### 4.2 Preset Requirements

#### F7: Film Stock Presets

Each preset must include:
- Halation parameters (unique per stock)
- Grain characteristics (ISO-based)
- Split toning colors
- Tone mapping style

| Preset | Characteristics |
|--------|---------------|
| **Portra 400** | Warm skin tones, medium grain, warm highlights/cool shadows |
| **Kodak 2383** | Cinema look, fine grain, rich blacks |
| **CineStill 800T** | Dramatic halation, tungsten colors, cyan shadows |
| **Tri-X 400** | B&W, bold grain, high contrast |
| **Cinematic** | Teal & orange, punchy |

---

## 5. Technical Specification

### 5.1 Technology Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Language | Kotlin | 1.9.x |
| UI Framework | Jetpack Compose | BOM 2024.01.00 |
| GPU Filters | **android-gpuimage** | 2.1.0 |
| Custom Shaders | OpenGL ES 3.0 | 3.0 |
| Image Loading | Coil | 3.4.0 |
| Camera | CameraX | 1.3.x |

### 5.2 Architecture

**Pattern**: MVVM + Clean Architecture + Custom OpenGL Pipeline

### 5.3 Performance Targets

| Metric | Target | Minimum |
|--------|--------|---------|
| Preview FPS | 60 fps | 30 fps |
| Signal-dependent grain | Must work at 60fps | - |
| Halation render | <16ms | <33ms |

---

## 6. Quality Assurance

### 6.1 Signal-Dependent Grain Test

```kotlin
@Test
fun grain_isSignalDependent() {
    // BRIGHT areas should have DENSER grain
    val brightImage = createGradient(brightness = 0.9f)
    val darkImage = createGradient(brightness = 0.1f)
    
    val brightGrain = measureGrainDensity(applyGrain(brightImage))
    val darkGrain = measureGrainDensity(applyGrain(darkImage))
    
    // MUST be significantly higher in bright areas
    assertThat(brightGrain).isGreaterThan(darkGrain * 2.0)
}
```

### 6.2 Halation Color Test

```kotlin
@Test
fun halation_isRedOrangeTinted() {
    val result = applyHalation(whiteImage)
    
    // Red channel should be higher than green and blue
    assertThat(result.red).isGreaterThan(result.green)
    assertThat(result.red).isGreaterThan(result.blue)
}
```

### 6.3 Device Test Matrix

| Tier | Devices | Tests |
|------|---------|-------|
| **High** | Pixel 7+, Samsung S23+ | Full functional + 60fps |
| **Mid** | Pixel 5-6a, A52+ | Full functional + 30fps |
| **Low** | Moto G Power | Functional + signal-dependent grain works |

---

## 7. Risk Assessment

### 7.1 Technical Risks

| Risk | Mitigation |
|------|------------|
| Grain doesn't look "film-like" | Implement signal-dependent algorithm (density ∝ brightness) |
| Halation looks like white blur | Add red-orange tint, edge-focus |
| Performance on low-end devices | Pre-computed grain textures, adaptive quality |

---

## Appendix A: Glossary

| Term | Expert Definition |
|------|------------------|
| **Signal-Dependent Grain** | Grain density varies with brightness - more photons = more developed grains = denser grain |
| **Halation** | Red-orange glow from light reflecting off film base back through emulsion layers |
| **Highlight Rolloff** | Gradual compression of bright areas instead of hard clipping |
| **Split Toning** | Different colors applied to highlights vs shadows |
| **ACES** | Academy Color Encoding System - industry standard tone mapping |

---

## Appendix B: Expert Quotes

> "The trick is simple and it is to add more grain into highlights than in the shadows. In negative film (natural grain) blacks were almost clean of grain." — **Professional Colorists**

> "Halation is a visual effect that appears when shooting on a film as a red-orange halo near the contrasting boundaries of over-exposed areas." — **Dehancer**

> "The best place to add the Film Grain effect is before the creative look components in the Timeline node graph." — **Frame.io**

> "On film, if you were to overexpose an image severely, you would see a gradual rolloff rather than an immediate hard clip." — **Noam Kroll**

---

*End of PRD v3.0*
