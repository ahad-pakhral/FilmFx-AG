# Phase 1: GPU Pipeline Foundation - Context

**Gathered:** 2026-02-25
**Status:** Ready for planning

<domain>
## Phase Boundary

Establishing the core real-time render pipeline for images, ensuring correct color space and cross-device compatibility.

</domain>

<decisions>
## Implementation Decisions

### Pipeline Architecture
- Hybrid approach: Group related effects (e.g. all color math) into single passes, but keep distinct steps (like blur) separate.
- Primary rendering target for preview: Offscreen texture/FBO.
- Shader compilation: Pre-compile all shaders at startup.
- Progress indicator: A dedicated splash/loading screen while shaders compile.

### Color Space Management
- Color space conversion: Assume sRGB and hardcode the conversion initially.
- Internal precision: Half-float (16-bit per channel).
- Final tone mapping: Filmic Tone Mapping (e.g. ACES-like curve).
- Base color operations: Standardize on sRGB/Rec.709 internally initially.

### Texture Handling
- Preview texture resolution limit: Cap at 1080p.
- Downsampling method: Native OS downsampling.
- Filtering method: Bilinear filtering.
- Original CPU bitmap data: Discard it immediately after uploading to GPU.

### Error Handling/Fallback
- Unsupported GPU on launch: Show a friendly error message and prevent opening files.
- OOM during export: Automatically downscale and retry.
- Runtime shader error: Display an error toast ("Renderer encountered an issue") and provide a restart button for the user to tap and restart the context.

### Claude's Discretion
- Implementation details of the splash screen.
- Exact filmic tone mapping curve mathematical implementation.
- Standard Android/iOS memory best practices when discarding bitmaps.
- Styling of the error messages and the restart button for runtime shader errors.

</decisions>

<specifics>
## Specific Ideas

- The restart button for runtime errors should be prominent enough for a user to easily recover from a black screen.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
