# Phase 02: Tone Mapping and Film Compression - Context

**Gathered:** 2026-02-25
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers the "Cinematic Engine" core: converting high-dynamic range linearized pixels into a visually pleasing, compressed image that mimics analog film latitude and highlight rolloff. 

Output: A multi-parameter GLSL filter that handles Exposure, Contrast, Shoulder, and Shadow Toe.

</domain>

<decisions>
## Implementation Decisions

### Algorithm & Parametrics
- **Exposure Control**: Photographic Linear scaling (-2.0 to +2.0 EV stops).
- **Midtone Contrast**: Dedicated slider controlling the slope of the curve.
- **Shoulder Adjustment**: Independent control over highlight roll-off (Softness/Limit).
- **Shadow "Toe"**: Independent control for lifting/crushing blacks.
- **Implementation Method**: Pure Math (GLSL) for 16-bit precision; no LUTs in this phase.

### Visual Character (Look & Feel)
- **Contrast Pivot**: Centered on Digital Mid-Values (0.5) for predictable UI behavior.
- **Highlight Desaturation**: Active logic to desaturate pixels as they approach peak white (emulates film chemistry).
- **Highlight Compression**: Aggressive "Safety" behavior at high intensities—highlights should roll off smoothly even if the sensor is clipped.
- **Saturation behavior**: Decoupled from the tone curve. Contrast adjustments do not automatically boost saturation, maintaining a "Professional/Clean" look.

### Claude's Discretion
- Mathematical curve selection (Sigmoid vs Piecewise vs ACES approximation).
- Internal optimization for the GLSL uniform updates.

</decisions>

<specifics>
## Specific Ideas
- The "100% Film Compression" setting should feel "Dreamy" and nearly impossible to clip, perfect for high-key outdoor shots.
</specifics>

<deferred>
## Deferred Ideas
- **LUT Support**: Moved to a future "Look Library" phase.
- **Color Wheels**: Handled in Phase 3 (Split Toning).
- **Film Grain**: Handled in Phase 5.
</deferred>

---

*Phase: 02-tone-mapping-and-film-compression*
*Context gathered: 2026-02-25*
