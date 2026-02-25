# Phase 03 Context: Split Toning

## Vision
Transform the cinematic tonal range (Phase 2) with stylish color grading. Shadows and Highlights can be tinted independently to create classic film looks (e.g., Cool Shadows / Warm Highlights).

## Decisions

### 1. Algorithm & Blending: Soft Bell-Curve
- **Description**: Transitions between shadow and highlight tints will not be a hard cut at 50% luminance.
- **Implementation**: Use overlapping gaussian or sigmoid functions so that colors blend naturally through the midtones.
- **Goal**: Avoid banding or "digital" looking color divisions.

### 2. UI: 2D Joystick Pads
- **Description**: Two circular controls (one for Shadows, one for Highlights).
- **Interaction**: 
    - Angle from center = Hue.
    - Distance from center = Saturation.
    - Center = No Tint (Neutral).
- **Benefit**: Highly intuitive for mobile touch interaction.

### 3. Balance Control
- **Description**: A "Balance" slider to shift the transition point.
- **Function**: Allows the user to push the shadow tint further into the highlights or vice-versa.

### 4. Color Space: Oklab
- **Description**: Perform the color tinting in the Oklab perceptual color space.
- **Rationale**: Oklab is designed for uniform perception of lightness and chroma, resulting in more natural-looking colors compared to Linear RGB.
- **Fallback**: If Oklab proves too complex or performant-heavy for GLSL, revert to high-precision Linear RGB.

## Requirements
- `FX-04`: Separate Highlight/Shadow color casts.
- `UI-03`: Color wheel (Refined to Joystick) controls.
- `PIPE-02`: Must maintain 16-bit precision through these color transformations.
