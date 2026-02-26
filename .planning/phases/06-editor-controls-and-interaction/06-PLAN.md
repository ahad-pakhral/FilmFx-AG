# Implementation Plan: Phase 06 - Editor Controls and Interaction

This phase transforms the FilmFX engine integration into a functional, persistent photo editor. We will implement high-precision controls, project-based saving, and an immersive user experience.

## Proposed Changes

### [Component] Data & Persistence
Implementing the foundation for project management and non-destructive editing.

#### [NEW] [Project.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/data/Project.kt)
- Define a `Project` entity for Room.
- Fields: `id`, `name`, `originalUri`, `localUri`, `parametersJson`, `lastModified`.

#### [NEW] [ProjectDao.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/data/ProjectDao.kt)
- Standard CRUD operations for projects.

#### [NEW] [AppDatabase.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/data/AppDatabase.kt)
- Room database initialization with `TypeConverters` for JSON parameter mapping.

---

### [Component] UI Foundation & State
Refactoring `EditorScreen.kt` to use a `ViewModel` and implementing immersive layout.

#### [MODIFY] [MainActivity.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/MainActivity.kt)
- Ensure edge-to-edge layout is properly handled via insets.

#### [NEW] [EditorViewModel.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/ui/EditorViewModel.kt)
- Manage `EditorState` (parameters, undo/redo stack, current project).
- Implement auto-save logic with debounce.

#### [MODIFY] [EditorScreen.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/ui/EditorScreen.kt)
- Use `EditorViewModel` for state management.
- Remove inline state variables for parameters.
- Add Top Bar with Back, Undo/Redo, and Export.
- Implement Long-press Undo for "Reset All".

---

### [Component] Interactive Controls
Creating specialized interactive components for professional feel.

#### [NEW] [PrecisionSlider.kt](file:///Users/ahad/Documents/FilmFX-AG/app/src/main/java/com/filmfx/app/ui/components/PrecisionSlider.kt)
- **Haptics**: Vibrate on default/zero anchor.
- **Snapping**: Soft magnetic snap near default.
- **Precision**: Vertical drag distance based sensitivity.
- **Reset**: Double-tap knob to reset to default.

---

## Verification Plan

### Automated Tests
Since no test infrastructure exists, we will establish baseline tests:
- **Unit Test**: `ProjectDaoTest.kt` - Verify saving and loading project parameters.
- **Compose Test**: `PrecisionSliderTest.kt` - Verify double-tap reset and haptic triggers (via mock).
- Command: `./gradlew test` (Wait for build success).

### Manual Verification
- **Slider Feel**: Test vertical dragging to confirm sensitivity decreases (slower movement).
- **Haptics**: Confirm vibration when slider passes the default point.
- **Persistence**: Change a parameter, kill the app, and reopen to confirm it persists.
- **Undo/Redo**: Verify that "Reset All" can be undone.
- **Immersive Mode**: Confirm UI extends behind the status bar area (edge-to-edge).
