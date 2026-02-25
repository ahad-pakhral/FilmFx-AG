# Summary: Plan 04-02 - Bloom and Vignette Filters

## Objective
Implement lens-based light scatter (Bloom) and radial darkening (Vignette).

## Completed Tasks
- [x] Create `BloomFilter.kt` with luminance threshold and additive blending.
- [x] Create `VignetteFilter.kt` with quadratic radial falloff and smoothstep control.

## Key Files Created/Modified
- `app/src/main/java/com/filmfx/app/engine/filters/BloomFilter.kt`
- `app/src/main/java/com/filmfx/app/engine/filters/VignetteFilter.kt`

## Decisions & Outcomes
- Adopted the same `TwoInputFilter` blending pattern for Bloom as used in Halation for consistent project patterns.
- Vignette uses a smoothstep falloff between 0.8 and 0.4 distance to ensure a professional look.

## Issues & Mitigations
- None.
