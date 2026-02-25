# Phase 02: Tone Mapping and Film Compression - Research

## Objective
Identify the mathematical models for a parametric cinematic tone mapper in GLSL, specifically implementing:
1. Exposure (-2 to +2 EV)
2. Parametric Midtone Contrast (Pivot = 0.5)
3. Independent Shoulder (Highlight Rolloff)
4. Independent Shadow "Toe"
5. Highlight Desaturation logic

## Implementation Path

### 1. Exposure (EV Stops)
Formula: `linearColor * pow(2.0, exposure)`
Where `exposure` is a uniform in range [-2.0, 2.0].

### 2. Parametric Tone Mapping Curve (Sigmoid/ACES Approximation)
A common parametric curve for film emulation is the "Naka-Rushton-like" or a Sigmoid curve. 
Proposed formula for normalized [0,1] range:
`f(x) = (x^contrast) / (x^contrast + (pivot^contrast * (1.0/shoulder - 1.0)))`

However, a more robust piecewise approach for ACES-like results with independent toe/shoulder control:
- **Toe**: Power function for blacks `x^toe_strength`.
- **Midtones**: Linear or low-order polynomial.
- **Shoulder**: Exponential or rational rolloff `1.0 - exp(-k * x)`.

### 3. Highlight Desaturation
Logic: `float luminance = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));`
`float factor = smoothstep(desat_threshold, 1.0, luminance);`
`color.rgb = mix(color.rgb, vec3(luminance), factor * desat_strength);`

### 4. GLSL Uniforms
```glsl
uniform float exposure;        // -2.0 to 2.0
uniform float contrast;        // 0.5 to 2.0
uniform float shoulder;        // 0.0 to 1.0 (0 = hard clip, 1 = dreamy roll)
uniform float shadowToe;       // 0.0 to 1.0
uniform float midPivot;        // 0.5 fixed
```

## Validation Architecture
- **Test Set 1 (Neutral)**: Exposure=0, Contrast=1.0, Shoulder=0, Toe=0. Result should be roughly identity (after initial linearization).
- **Test Set 2 (High Key)**: Exposure=+2.0, Shoulder=1.0. Check for gray rolloff in peaks instead of flat #FFFFFF blocks.
- **Test Set 3 (Low Key)**: Exposure=-2.0, Toe=1.0. Check for deep blacks without banding.

## External References
- ACES Fitted Curves (Lottes, Uchimura, Hill)
- "Tone Mapping for HDR Content" (Reinhard)
- "The Art of Digital Color" (Cinematic Latitudes)

## RESEARCH COMPLETE
