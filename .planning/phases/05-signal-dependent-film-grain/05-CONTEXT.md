# Phase 05: Signal-Dependent Film Grain - Context

**Gathered:** 2026-02-26
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers the core Signal-Dependent Film Grain engine. It involves creating a physics-based grain shader where density is proportional to image luminance, offering professional-grade control over texture, tonal response, and color variation.

</domain>

<decisions>
## Implementation Decisions

### Grain Structure & Texture
- **Texture Sliders:** Provide individual sliders for **Softness**, **Size**, and **Clumpiness**.
- **Scaling:** Grain must be **Resolution-Independent** (scales with output resolution to maintain consistent appearance).

### Signal-Dependence (Tonal Response)
- **Logarithmic Curve:** Use a logarithmic density model (`-log(1 - luminance)`) for authentic film-like response.
- **Coverage Sliders:** Provide sliders for **Shadow Coverage** (noise floor) and **Highlight Fade-out** (saturation towards pure white).
- **Control:** Include a **Shifting Slider** to balance grain density between shadows and highlights.

### Color Variation
- **Chroma Control:** Provide a **Toggle for Color Noise** and a **Chroma Intensity Slider**.
- **Correlation:** RGB patterns should be **Linked** (organic/dye-cloud style) rather than completely independent noise.
- **Order:** Grain is **Neutral** and applied after color grading (Linearize -> Tone Map -> Grade -> Grain).

### UI & Performance
- **Exposure:** All controls are **Grouped** and toggled closed by default to prevent UI clutter.
- **Workflow:** **Manual Only** — no one-tap presets; all control is in the user's hands.
- **Interaction:** **Manual Zoom** — let the user decide when to zoom in to inspect grain.
- **Compatibility:** Include a **Quality Toggle** (High Quality vs. Fast) to support older devices.

### Claude's Discretion
- Specific mathematical implementation of "clumpiness" (e.g., periodic noise vs. stochastic clusters).
- Optimization of the logarithmic curve constants for performance.

</decisions>

<specifics>
## Specific Ideas
- Reference: IPOL paper on stochastic film grain rendering (signal-dependent model).
- Reference: Dehancer/Cullen Kelly grain placement philosophies.
</specifics>

<deferred>
## Deferred Ideas
- Film Stock Presets (Portra 400, Delta 3200, etc.) — postponed to a later phase or v2.
</deferred>

---

*Phase: 05-signal-dependent-film-grain*
*Context gathered: 2026-02-26*
