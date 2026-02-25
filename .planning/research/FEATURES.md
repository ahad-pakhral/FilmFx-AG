# Feature Research

**Domain:** Film emulation photo editing apps (mobile)
**Researched:** 2026-02-25
**Confidence:** MEDIUM

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Film stock presets | Industry standard since VSCO (2011), users expect instant film looks (Portra, Gold, Velvia, etc.) | MEDIUM | VSCO, Dehancer, RNI Films, Filmborn all ship 40-60+ presets. Variants (+, ++, -) for intensity. PROJECT.md defers to v2 - HIGH RISK for user expectations |
| Film grain overlay | Every film emulation app offers grain adjustment (intensity, size) | LOW-MEDIUM | Simple noise overlay is table stakes. Most apps: slider for intensity. FilmFX's signal-dependent grain is differentiator, not table stakes |
| Basic color adjustments | Temperature, tint, saturation, contrast sliders | LOW | Users expect these before applying film effects |
| Vignette | Radial darkening at edges, adjustable intensity/falloff | LOW | Standard across all film apps (VSCO, Dazz Cam, RNI Films) |
| Before/after comparison | Tap-hold or split-view slider to compare edited vs original | MEDIUM | ACDSee 2026, Sidly, Photo Compare apps all feature this. Frustration point if missing |
| Gallery import | Pick photos from device photo library | LOW | Dual functionality: some apps also have camera, but gallery import is non-negotiable |
| Full-resolution export | Save edited photo back to gallery at original resolution | MEDIUM | Users expect HEIC, JPEG export at full resolution. RNI Films added HEIC in 2026 update |
| Real-time preview | See effect changes immediately without lag | HIGH | Dehancer advertises "zero lag" real-time preview. 30fps minimum, 60fps target critical for competitiveness |
| Adjustable effect sliders | Fine control over individual effects (not just presets) | LOW | Professional apps (Lightroom, RNI Films, Dehancer) provide granular sliders beyond one-click presets |
| Non-destructive editing | Ability to tweak/undo edits without losing quality | MEDIUM | Expected in professional mobile apps (Dehancer, RNI Films support non-destructive workflows) |

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|-------------|-------|
| Signal-dependent grain | Grain density varies by brightness (physics-based, matches real film) | HIGH | Current apps use uniform noise overlays. Dehancer has "realistic grain" but specifics unclear. Color.io uses pixel-by-pixel reconstruction - advanced. This is FilmFX's PRIMARY differentiator per PROJECT.md |
| Physics-based halation | Red-orange glow at high-contrast edges from light reflecting through film layers | HIGH | VSCO has "halation" feature (2026), but implementation unknown. Dehancer advertises "halation and bloom" as professional tools. 8mm Vintage Camera has adjustable halation. FilmFX's multi-scale blur approach may be more authentic |
| ACES filmic tone mapping | Industry-standard tone curve used by Pixar/Disney/DaVinci Resolve | MEDIUM | Most mobile apps use simpler tone curves. ACES provides cinematic highlight rolloff that professionals recognize |
| Color wheels for split toning | Separate highlight/shadow color control (vs simple dual-tone sliders) | MEDIUM | Photo Curves app offers color wheels (shadows/midtones/highlights). Rare in mobile film apps. More precise than temperature/tint sliders |
| Bloom (separate from halation) | Colorless white light scattering from bright areas | MEDIUM | VSCO Capture has live Bloom effect. Dehancer has bloom. Most apps conflate with halation or lens flare |
| Educational content | In-app manual explaining film photography concepts and how effects relate to analog | LOW | Filmborn's unique feature: user manual teaching film history/techniques. Differentiates through education, builds loyalty |
| Film compression modeling | Gradual highlight shoulder vs hard clipping (emulates film latitude) | MEDIUM-HIGH | Filmbox uses "physics-based" processing for negative's latitude and color response. Most apps clip highlights digitally |
| RAW file support | Import/edit RAW images from device or third-party cameras | MEDIUM | RNI Films, Dehancer, Moment support RAW. Professional feature, differentiates from casual apps |

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| AI auto-enhancement portraits | Users want quick "make it better" button | Lensa, Facetune criticized for making people look "filtered" vs enhanced. Produces uncanny valley results, face doesn't look like actual person | Manual controls that teach users to enhance subtly. AI should assist edits, not take over completely |
| Built-in camera with live preview | Film camera apps (Dazz Cam, Filmborn, VSCO Capture) have this, users expect it | Adds CameraX complexity (PROJECT.md notes this). Camera permission friction. Filmborn "uses full potential" with camera, but also allows import. Snapseed 2026 added camera as differentiator, not table stakes | Gallery import only for v1. Camera integration deferred to v2 once core effects engine validated |
| Direct social media sharing | Fotor, Canva, PicMonkey offer one-click Instagram/TikTok/Facebook sharing | SDK bloat (12+ platform integrations), privacy concerns, app store review complexity, maintenance burden for API changes | Export to gallery only. Users can share via system share sheet if needed. Keeps app lean and privacy-focused |
| Light leaks, dust, scratches overlays | Afterlight, Huji Cam, Film Camera Luxroom, 8mm Vintage Camera all have these texture overlays. Appears in "vintage aesthetic" user expectations | Superficial aesthetic that doesn't relate to film physics. Feels gimmicky vs authentic. VSCO Film FX has "dust & scratch" but is separate product line from core film emulation | Focus on physics-based effects (grain, halation, bloom) that actually come from film chemistry. Let texture overlays be a v3+ novelty feature |
| Aggressive AI features | 2026 trend: AI removal brush, sky replacement, generative fill | Battery drain (Neural Engine spikes 12-19% during photo browsing, 68% when camera open). Performance drops 17% at 38°C. Background indexing kills battery. Snapseed lacks these, not penalized | Keep effects GPU-based, avoid ML models. Film emulation doesn't need AI - it needs accurate physics simulation |
| Unlimited undo/edit history | Users want to try multiple variations without losing work | Memory overhead for high-res images. Complex state management. Non-destructive editing provides this via parameter reset without storing full history | Single undo for slider adjustments. Reset-to-original button. Non-destructive workflow means parameters can always be re-edited |
| Video support | RNI Films 2026: "colour-grade your videos", Dazz Cam supports video, Protake has film emulation for video | Massive complexity increase (frame processing, codec handling, audio passthrough, memory management). PROJECT.md explicitly out-of-scope for v1 | Photos only for v1. Video is v2+ feature after photo workflow validated |
| Subscription with hidden paywalls | Common monetization: weekly $9.99, features locked at export, watermark removal paywall | User hatred: "It said free, then asked me to pay." Aggressive paywall tactics damage reputation. VSCO criticized for gating previously free features | One-time purchase or upfront subscription with clear trial. No watermarks. No "export" paywall surprises. RNI Films offers "One-Off Purchase" lifetime option - good model |

## Feature Dependencies

```
[Gallery import]
    └──requires──> [Image loading/caching system (Coil)]
                       └──requires──> [Preview downsampling for performance]

[Real-time preview]
    └──requires──> [GPU filter pipeline (android-gpuimage)]
                       └──requires──> [OpenGL ES 3.0 custom shaders]

[Signal-dependent grain]
    └──requires──> [Linearize image (sRGB → linear)]
                       └──requires──> [ACES tone mapping (already in processing chain)]

[Halation effect]
    └──requires──> [Multi-scale blur (3 passes at different radii)]
                       └──requires──> [GPU shader optimization (memory bandwidth critical)]

[Split toning (color wheels)]
    └──requires──> [HSL separation in shader]
                       └──requires──> [Tone mapping (defines highlight/shadow boundaries)]

[Full-resolution export]
    └──requires──> [GPU memory management (avoid OOM on large images)]
                       └──requires──> [Tiling or downsampling strategy]

[Before/after comparison]
    └──enhances──> [All effect controls]
                       └──requires──> [Dual render targets or toggle shader params]

[Vignette]
    └──requires──> [Last in processing chain (after grain)]

[Non-destructive editing]
    └──requires──> [Parameter state management]
                       └──enables──> [Preset saving (v2 feature)]
```

### Dependency Notes

- **Gallery import requires Coil:** Already in tech stack (PROJECT.md). Coil handles downsampling for preview, full-res for export.
- **Real-time preview requires GPU pipeline:** android-gpuimage foundation + custom shaders. 30fps minimum, 60fps target. GPU performance is critical path.
- **Signal-dependent grain requires linearization:** Must convert sRGB → linear before grain calculation, then back to sRGB. Linearization already in ACES tone mapping chain.
- **Halation requires multi-scale blur:** Three blur passes at different radii (Dehancer research). Memory bandwidth bottleneck - needs shader optimization.
- **Split toning requires tone mapping first:** Can't separate highlights/shadows until tone mapping defines brightness boundaries.
- **Full-resolution export requires GPU memory management:** Large images (20MP+) can OOM GPU. Need tiling or resolution limits.
- **Before/after comparison enhances all controls:** Not a blocker, but massively improves UX for effect tuning.
- **Vignette goes last:** Apply after grain to avoid brightening grain in darkened areas.
- **Non-destructive editing enables preset saving:** Parameter state management in v1 unlocks preset packs in v2.

## MVP Definition

### Launch With (v1)

Minimum viable product — what's needed to validate the concept.

- [ ] **Signal-dependent film grain** — Core differentiator, validates physics-based approach vs competitors' uniform noise
- [ ] **Halation effect** — Second core differentiator, red-orange glow that makes film look like film
- [ ] **Bloom effect** — White light scattering, complements halation for bright areas
- [ ] **ACES filmic tone mapping** — Cinematic highlight rolloff, foundational for professional look
- [ ] **Split toning with color wheels** — Precise color control, differentiates from simple temperature/tint sliders
- [ ] **Vignette** — Table stakes effect, low complexity
- [ ] **Gallery import** — Table stakes, users must be able to load photos
- [ ] **Real-time preview (30fps minimum)** — Table stakes for mobile editing in 2026, non-negotiable
- [ ] **Before/after comparison (tap-hold)** — Essential for effect tuning, users expect this
- [ ] **Basic color adjustments** — Brightness, contrast, saturation sliders (table stakes)
- [ ] **Full-resolution export (JPEG)** — Must export at original resolution to gallery
- [ ] **Individual effect sliders** — Grain intensity, halation intensity, bloom intensity, vignette strength, etc.
- [ ] **Non-destructive parameter editing** — Ability to adjust sliders and reset without quality loss

### Add After Validation (v1.x)

Features to add once core is working.

- [ ] **Film stock presets (Portra, CineStill, etc.)** — Trigger: If users struggle with manual controls or request "quick looks". Deferred per PROJECT.md but may become table stakes pressure
- [ ] **HEIC export format** — Trigger: User requests for smaller file sizes. RNI Films added this in 2026
- [ ] **Film compression modeling** — Trigger: If ACES tone mapping feels too digital. Gradual highlight shoulder vs hard clipping
- [ ] **RAW file import** — Trigger: Professional user requests. Differentiates from casual apps
- [ ] **Preset saving (custom user presets)** — Trigger: Users develop preferred settings and want to reuse. Requires non-destructive state management from v1
- [ ] **Advanced before/after (split-view slider)** — Trigger: If tap-hold feels insufficient. ACDSee/Sidly-style slider
- [ ] **Undo/redo for slider changes** — Trigger: User frustration with accidental slider movements

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] **Built-in camera with live effects preview** — Why defer: CameraX complexity, camera permissions friction. Validate effects engine with gallery import first (per PROJECT.md)
- [ ] **Video processing** — Why defer: Massive complexity, frame-by-frame processing. Photos only for v1 (per PROJECT.md)
- [ ] **Light leaks, dust, scratches texture overlays** — Why defer: Superficial aesthetic, doesn't align with physics-based core value. May revisit if users specifically request
- [ ] **Temporal effects (film breath, gate weave)** — Why defer: V2+ per PROJECT.md, requires video or sequence processing
- [ ] **Educational content (film photography manual)** — Why defer: Nice differentiator (Filmborn model) but not core to effects engine. Add if users want to learn "why" behind effects
- [ ] **Direct social sharing integrations** — Why defer: SDK bloat, maintenance burden. Export to gallery + system share sheet is sufficient
- [ ] **Advanced color grading (RGB curves, CMYK curves, Lab curves)** — Why defer: Professional feature (Photo Curves app), but core users want film emulation not full color grading suite
- [ ] **Batch processing** — Why defer: Need single-photo workflow validated first. RNI Films has this for professionals

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Signal-dependent grain | HIGH | HIGH | P1 |
| Halation effect | HIGH | HIGH | P1 |
| Gallery import | HIGH | LOW | P1 |
| Real-time preview (30fps) | HIGH | MEDIUM | P1 |
| Before/after comparison | HIGH | MEDIUM | P1 |
| Full-resolution export | HIGH | MEDIUM | P1 |
| ACES tone mapping | MEDIUM | MEDIUM | P1 |
| Split toning (color wheels) | MEDIUM | MEDIUM | P1 |
| Bloom effect | MEDIUM | MEDIUM | P1 |
| Vignette | MEDIUM | LOW | P1 |
| Basic color sliders | HIGH | LOW | P1 |
| Non-destructive editing | HIGH | MEDIUM | P1 |
| Film stock presets | HIGH (user expectation) | MEDIUM | P2 |
| HEIC export | LOW | LOW | P2 |
| Film compression modeling | MEDIUM | HIGH | P2 |
| RAW file import | LOW (niche professional) | MEDIUM | P2 |
| Preset saving (custom) | MEDIUM | LOW | P2 |
| Split-view slider (before/after) | LOW | MEDIUM | P2 |
| Undo/redo | MEDIUM | MEDIUM | P2 |
| Built-in camera | MEDIUM | HIGH | P3 |
| Video processing | LOW | VERY HIGH | P3 |
| Texture overlays (dust/scratches) | LOW | LOW | P3 |
| Educational content | LOW | MEDIUM | P3 |
| Direct social sharing | LOW | MEDIUM | P3 |
| Advanced color grading curves | LOW | HIGH | P3 |
| Batch processing | LOW | MEDIUM | P3 |

**Priority key:**
- P1: Must have for launch (validates core value: physics-based film emulation)
- P2: Should have, add when possible (addresses user expectations or extends differentiation)
- P3: Nice to have, future consideration (scope expansion after product-market fit)

## Competitor Feature Analysis

| Feature | VSCO (market leader) | Dehancer (premium) | RNI Films (pro-focused) | FilmFX Approach |
|---------|----------------------|--------------------|------------------------|-----------------|
| Film presets | 40+ presets (Kodak, Fuji, Ilford) with +/++/- variants | 60+ film stock profiles derived from darkroom prints | Meticulously recreated chemical processes, extensive library | **Defer to v2** - core effects engine first. HIGH RISK: users may expect presets at launch |
| Grain | Adjustable intensity slider (uniform noise overlay) | "Realistic grain" (specifics unclear) | "Scientific approach" to grain emulation | **Signal-dependent grain** - density varies by brightness (Poisson model). PRIMARY differentiator |
| Halation | Yes - "Cinematic Halation" feature (2026), adjustable | Yes - professional-grade halation | Unknown | **Physics-based multi-scale blur** - red-orange glow from film back-reflection. SECONDARY differentiator |
| Bloom | Yes - live Bloom effect in VSCO Capture | Yes - separate from halation | Unknown | **Yes** - colorless white light scattering, separate from halation |
| Tone mapping | Proprietary film-inspired curves | Advanced tone mapping (specifics unclear) | Film-accurate tone response | **ACES filmic** - industry standard, cinematic highlight rolloff |
| Split toning | Dual-tone sliders (simple) | Color wheels (shadows/midtones/highlights) | Unknown (likely color wheels for pro users) | **Color wheels** - separate highlight/shadow control, more precise than VSCO |
| Vignette | Yes - Fade + Vignette tools | Yes - adjustable intensity | Yes | **Yes** - adjustable intensity/smoothness |
| Real-time preview | Yes - instant preview | Yes - "zero lag" advertised | Yes - desktop-quality rendering on mobile | **30fps minimum, 60fps target** - performance critical, GPU optimized |
| Before/after | Yes - tap to toggle | Unknown | Yes | **Tap-hold** for v1, split-view slider for v1.x |
| Camera integration | VSCO Capture separate app (2026) with film simulations in-camera | iOS only, gallery import | Gallery import only | **Gallery only for v1** - camera deferred to v2 per PROJECT.md |
| Export | JPEG, unknown if HEIC | JPEG, HEIC, RAW export | HEIC (added 2026), JPEG, TIFF, DNG | **JPEG for v1, HEIC for v1.x** |
| Platform | iOS + Android | iOS only (mobile), desktop plugins | iOS only | **Android only for v1** - iOS deferred per PROJECT.md |
| Monetization | Subscription with aggressive paywall tactics (user complaints) | Professional pricing (premium plugin) | Freemium: monthly/yearly/one-time purchase | **One-time purchase or clear subscription** - no hidden paywalls, no watermarks |
| Texture overlays | Yes - Film FX has light leaks, dust, scratches, sprocket frames | Unknown (focuses on physics-based) | Unknown | **Defer to v3+** - superficial aesthetic, doesn't align with physics-based core |
| RAW support | Unknown | Yes - JPEG, HEIC, RAW import | Yes - RAW editing with batch processing | **Defer to v1.x** - professional feature, not core to validation |

## Sources

### Competitor Analysis (Film Emulation Apps)
- [VSCO Film Filters](https://www.vsco.co/features/film-filters) - Film preset library, Fuji/Kodak/Agfa/Ilford emulations
- [VSCO Film 02 Lightroom Presets - 9to5Mac](https://9to5mac.com/2026/02/10/vscos-film-02-preset-pack-available-for-adobe-lightroom-as-a-limited-time-release/) - 2026 update, Kodak/Fuji/Ilford emulations
- [VSCO Film FX](https://www.vsco.co/features/film-fx) - Light leaks, grain, dust, scratches, sprocket overlays
- [VSCO Halation Effect](https://www.vsco.co/features/halation) - Cinematic halation feature
- [Dehancer Film Emulation App](https://apps.apple.com/us/app/dehancer-film-emulation/id6443648413) - iOS app, 60+ film stocks, realistic grain/bloom/halation
- [Dehancer Features](https://www.dehancer.com/features) - Professional film emulation tools
- [RNI Films Mobile](https://mobile.reallyniceimages.com/) - Photo & RAW editor, film presets
- [RNI Films App Store](https://apps.apple.com/us/app/rni-films-photo-raw-editor/id1017098672) - HEIC export (2026), RAW editing
- [Filmborn - Mastin Labs](https://fstoppers.com/apps/mastin-labs-releases-filmborn-app-mobile-film-emulation-152597) - 9 film stocks, educational content
- [Filmborn Review - SLR Lounge](https://www.slrlounge.com/filmborn-full-review-of-mastin-labs-new-photography-app/) - In-app camera, film education
- [Filmborn - PetaPixel](https://petapixel.com/2016/11/04/mastin-labs-new-filmborn-app-brings-super-accurate-film-emulation-ios/) - Super accurate film emulation for iOS

### Vintage Camera Apps
- [Dazz Cam App Store](https://apps.apple.com/us/app/dazz-cam-vintage-camera/id1422471180) - 135 film, 120 film, toy cameras, disposable, 3D, double exposure
- [Dazz Cam Features](https://parallaxaview.com/best-dazz-cam-filters/) - Vintage lens flare, light leaks, film grain
- [DAZE CAM App Store](https://apps.apple.com/us/app/daze-cam-vintage-camera/id1464359734) - Date, glow, chroma, blur, vignette, halation, fisheye, grain, leak, dust
- [8mm Vintage Camera](https://apps.apple.com/us/app/8mm-vintage-camera/id406541444) - Film color, halation, dust/scratch, grain, projector, vignette, light leak
- [Vintage Camera Apps Guide - CapCut](https://www.capcut.com/resource/best-vintage-camera-apps-for-film-photography-aesthetics) - Grain, vignette, halation features
- [Film Camera Apps - Shotkit](https://shotkit.com/film-camera-apps/) - Huji Cam, Afterlight, MolyCam, OldRoll features
- [Light Leak V App Store](https://apps.apple.com/us/app/light-leak-v-photo-filters/id1560557448) - Film grain, glitches, dust, scratches textures

### Mobile Editing App Trends (2026)
- [Best Photo Editing Apps 2026 - Lovable](https://lovable.dev/guides/best-photo-editing-apps-iphone-android-2026) - Film-inspired presets as key trend
- [Snapseed Film Simulations - PetaPixel](https://petapixel.com/2026/02/19/snapseeds-new-built-in-camera-has-film-simulations-including-portra-and-superia/) - Kodak Portra, Gold, E200, Fuji Superia, Pro 400H
- [Best Photo Editing Apps 2026 - ThemFrames](https://themframes.com/features/the-best-photo-editing-apps-to-use-right-now/) - Real-time preview, export formats, performance benchmarks
- [Photo Editing Apps 2026 - Amateur Photographer](https://amateurphotographer.com/round-ups/best-photo-apps-for-phones/) - Lightroom, Luminar Neo, Snapseed comparisons

### Advanced Film Emulation & Grain
- [Wholegrain App - PetaPixel](https://petapixel.com/2026/02/12/photographers-new-app-gives-digital-photos-a-realistic-analog-appearance/) - Custom grain algorithm, color processing
- [GrainLab App Store](https://apps.apple.com/us/app/grainlab-film-grain-editor/id6630375395) - Accurate presets, endless tweakability
- [Film Grain Apps - Mobile Lens Mastery](https://mobilelensmastery.com/retro-film-grain-filters-apps/) - RNI Films, VSCO, Dehancer grain approaches
- [Volumetric Film Grain - Color.io](https://www.color.io/user-guide/volumetric-film-grain) - Pixel-by-pixel reconstruction, synthetic granules
- [Filmbox Film Emulation](https://videovillage.com/filmbox/) - Physics-based processing, indistinguishable match to negative

### Manual Controls & Professional Features
- [Filmic Pro v7](https://www.filmicpro.com/) - Manual sliders, gamma curves, log profiles
- [Photo Curves App](https://play.google.com/store/apps/details?id=com.foreachi.photocurves&hl=en_US) - Color wheels (shadows/midtones/highlights), RGB/CMYK/Lab curves
- [Best Video Color Correction Apps 2026 - Tipard](https://www.tipard.com/video/video-color-correction-app.html) - Manual controls, sliders, color wheels overview

### Before/After Comparison Features
- [ACDSee Photo Studio 2026](https://www.acdsee.com/en/photo-studio/whats-new/) - Before/After slider in Develop Mode
- [Before and After Photo Compare](https://apps.apple.com/us/app/before-and-after-photo-compare/id681734972) - 2 Image Slider mode
- [Sidly App](https://play.google.com/store/apps/details?id=com.sarafan.sidly&hl=en_US) - Customizable slider color, animation, direction

### Export & File Format Support
- [RNI Films HEIC Export](https://www.itopnews.de/2026/01/geheimtipp-aus-dem-app-store-rni-films/) - HEIC format support (2026)
- [ProCamera v15.0](https://procamera-app.com/en/blog/whats-new-in-procamera-v15-0-raw-10-bit-heic-design-revamp/) - RAW, 10-bit HEIC, design revamp
- [Dehancer iOS RAW Support](https://blog.dominey.photography/2023/01/09/dehancer-for-ios-film-emulation-on-the-go/) - JPG, HEIC, RAW import

### Anti-Features & User Complaints
- [Photo Editing Apps User Complaints 2026 - ThemFrames](https://themframes.com/features/the-best-photo-editing-apps-to-use-right-now/) - AI beautification uncanny valley, aggressive paywalls
- [Subscription Paywalls Analysis - DEV](https://dev.to/paywallpro/subscription-pricing-in-photo-video-apps-what-1200-paywalls-reveal-3ok9) - Weekly $9.99, export paywalls, watermark removal
- [AI Photo Enhancement Battery Drain - Alibaba](https://www.alibaba.com/product-insights/why-does-my-phone-battery-drain-faster-after-updating-to-the-latest-ios-with-ai-photo-enhancements.html) - Neural Engine spikes, performance drops, battery telemetry
- [Google Pixel Battery Problems 2026](https://samphonerepair.com.au/google-pixel-battery-performance-problems-2026/) - AI photo indexing, background analysis

### Preset Packs vs Manual Editing
- [Lightroom Film Presets 2026 - Imagen](https://imagen-ai.com/valuable-tips/lightroom-film-presets/) - Static presets vs AI profiles, consistency challenges
- [Is Dehancer Worth It - Jim Grootes](https://jimgrootes.com/is-dehancer-worth-it-a-simple-honest-look-at-this-film-emulation-plugin/) - Darkroom-derived profiles, customization vs efficiency

### Vintage App User Expectations
- [Best Vintage Photo Apps 2026 - Perfect Corp](https://www.perfectcorp.com/consumer/blog/photo-editing/best-vintage-photo-editing-apps) - Light leaks, vintage lens flare, film grain fundamentals
- [Film Camera Apps Review - The Tab](https://thetab.com/2025/04/22/right-i-tested-apps-that-make-your-photos-look-like-film-to-see-which-actually-work) - Huji Cam, 1998 Cam, Gudak authenticity approaches

---
*Feature research for: Film emulation photo editing apps (mobile)*
*Researched: 2026-02-25*
