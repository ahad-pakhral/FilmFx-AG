# Phase 06: Editor Controls and Interaction - Research

**Researched:** 2026-02-26
**Domain:** Jetpack Compose UI, Haptics, Persistence (Room)
**Confidence:** HIGH

## Summary

This research identifies the technical path for implementing professional-grade editor controls in FilmFX. The core challenge is moving beyond standard Jetpack Compose components to implement specialized behaviors like "Slow Drag" sensitivity and "Soft Snapping". We also establish Room as the persistence layer for project management.

**Primary recommendation:** Use a custom `pointerInput` based slider for vertical sensitivity and Room with TypeConverters for parameter persistence.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **Haptic Feedback**: "Anchor Only" — vibrate only at default/zero.
- **Snapping**: "Soft Snap" — brief pause/pull near default.
- **Value Visibility**: "Persistent" — numeric values always visible.
- **Precision Mode**: "Slow Drag" — vertical distance sensitivity.
- **Reset Trigger**: Double-tap knob to reset.
- **Immersive Design**: Edge-to-edge layout (no top padding).
- **Global Reset**: Long-press Undo for "Reset All".
- **Top Bar**: Back (Left), Undo/Redo (Center), Export (Right).
- **Auto-Save**: Save every change.
- **Independent Storage**: Local photo copies for projects.

### Claude's Discretion
- Implementation details of the "Projects Grid" (though structure is defined).
- Technical stack for persistence (Room recommended).

### Deferred Ideas (OUT OF SCOPE)
- Full UI management of project deletion/management (Foundation in P6, full UI in P7).
- Settings Screen specifics.
- Splash Screen.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| UI-02 | Manual sliders for all effects | Custom Slider implementation for precise control |
| UI-04 | Before/After comparison | `pointerInput` long-press detection on preview |
| UI-05 | Non-destructive editing | Room persistence + ViewState management |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Room | 2.6.1 | Persistence | Robust SQLite abstraction for projects |
| Jetpack Compose | 1.5.8 | UI | Primary UI toolkit |
| Android Haptics | - | Feedback | Standard platform API via `LocalHapticFeedback` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-serialization | 1.6.2 | JSON Handling | For storing parameters as JSON string in Room |
| ViewModel | 2.7.0 | State Management| standard holder for editor state and undo stack |

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/filmfx/app/
├── data/              # Room DB, Entities, DAOs
├── ui/
│   ├── components/    # Custom sliders, Toolbars
│   ├── editor/        # Editor screen & ViewModel
│   └── projects/      # Projects grid (Draft)
└── model/             # Domain models (EffectParameters)
```

### Pattern 1: Sensitivity-Aware Slider
**What:** A custom composable that wraps a track and thumb, using `detectDragGestures` to calculate delta.
**Sensitivity Formula:** `deltaX * (1.0f / (1.0f + verticalDistance / 200dp))`

### Pattern 2: Undo/Redo Stack
**What:** A `ViewModel` holding a `MutableStateFlow<EditorState>` and a `LinkedList<ProjectParameters>` for history.

## Common Pitfalls

### Pitfall 1: Gesture Conflict
**What goes wrong:** Slider drag being intercepted by the preview's pan/zoom sensor.
**How to avoid:** Use `pointerInput` with `PointerEventPass.Initial` or careful hit-testing.

### Pitfall 2: Room Blocking UI
**What goes wrong:** Auto-saving on every slider tick blocks the main thread.
**How to avoid:** Debounce saves (e.g., 500ms after last change) or use `Dispatchers.IO` with `flow`.

## Code Examples

### Custom Slider Haptics (Anchor Snap)
```kotlin
val haptic = LocalHapticFeedback.current
Slider(
    value = value,
    onValueChange = { newValue ->
        if (Math.abs(newValue - defaultValue) < 0.05f) {
            if (value != defaultValue) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            onValueChange(defaultValue)
        } else {
            onValueChange(newValue)
        }
    }
)
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4 + Compose Test |
| Config file | build.gradle.kts |
| Quick run command | `./gradlew test` |
| Full suite command | `./gradlew connectedAndroidTest` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UI-02 | Slider resets on double-tap | Compose UI | `./gradlew connectedCheck` | ❌ Wave 0 gap |
| UI-05 | State persists on app restart | Integration | `./gradlew connectedCheck` | ❌ Wave 0 gap |

## Sources
### Primary (HIGH confidence)
- Android Official Docs: Room Persistence
- Jetpack Compose Docs: PointerInput & Gestures
- Codebase: `EditorScreen.kt` (existing shader integration)

## Metadata
**Confidence breakdown:**
- Standard stack: HIGH - Room/Compose are mature
- Architecture: MEDIUM - Custom slider requires careful tuning
- Pitfalls: HIGH - Known Android/Compose performance issues

**Research date:** 2026-02-26
**Valid until:** 2026-03-26
