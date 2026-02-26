# Phase 06: Editor Controls and Interaction - Context

**Gathered:** 2026-02-26
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers the user interface for manual parameter control, real-time preview feedback, and the structural persistence required for non-destructive editing. It transforms the "render test" into a functional "photo editor".

</domain>

<decisions>
## Implementation Decisions

### Slider Interaction Details
- **Haptic Feedback**: "Anchor Only" — only vibrate/provide haptic feedback when a slider hits its default or zero position.
- **Snapping**: "Soft Snap" — provide a brief pause or magnetic pull when released very close to the default value.
- **Value Visibility**: "Persistent" — numeric values remain always visible next to labels (maintaining existing implementation).
- **Precision Mode**: "Slow Drag" — sensitivity should decrease as the user drags a slider thumb further vertically from the track.
- **Reset Trigger**: Double-tap any individual slider knob to reset that specific parameter to its default.

### Editor Layout & Interactions
- **Immersive Design**: Remove all unnecessary padding above top bars. UI should extend to the absolute top of the screen (edge-to-edge layout).
- **Top Bar Configuration**:
  - Left: Back arrow (closes photo, preserves project).
  - Center: Undo/Redo buttons.
  - Right: Export button.
- **Global Reset**: Long-press the "Undo" button to reveal a "Reset All" icon below it. Tapping this resets all sliders (must be undoable). Click away to hide.
- **Before/After**: (Implied Lightroom style) Toggle or tap-hold to compare original vs current edits.

### Persistence & Storage
- **Auto-Save**: Record every parameter change immediately; work is never lost.
- **Independent Storage**: Store a copy of the imported photo locally within the app to ensure projects are self-contained and independent of the system gallery.
- **Exit Behavior**: Navigating "Back" closes the editor but keeps the project available in the projects tab/grid with all settings intact.

</decisions>

<specifics>
## Specific Ideas
- **UI Feel**: Professional, dark theme, Lightroom-inspired.
- **Navigation**: Support both system gesture navigation (swipe-to-back) and explicit UI buttons.

</specifics>

<deferred>
## Deferred Ideas
*These items were requested/detailed but may be planned as follow-up work or handled in Phase 6.1/7 depending on roadmap strictly:*
- **Projects Grid**: A home screen grid of project thumbnails with a "Delete" option on long-press. (Foundation for this is in Phase 6, but full UI management may span Phase 6-7).
- **Settings Screen**: Minimal "About" section with a hidden debug toggle (tap version 7 times).
- **Splash Screen**: 1-second branded intro.

</deferred>

---

*Phase: 06-editor-controls-and-interaction*
*Context gathered: 2026-02-26*
