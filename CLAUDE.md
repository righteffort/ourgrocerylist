# CLAUDE.md

## What this is

Native Android shopping list app. Kotlin, Jetpack Compose, MVVM. See `ourgrocerylist-handoff.md` for full design and architecture decisions.

## Project structure

```
app/src/main/java/org/righteffort/ourgrocerylist/
├── model/           # ShoppingItem (with ItemFields), Command sealed class
├── repository/      # ShoppingRepository interface + FakeShoppingRepository
├── ui/              # ViewModel, UiState, ItemDialogState, ItemDialog, screens, theme
│   └── theme/
└── OurGroceryListApp.kt  # Application class, manual DI
```

## Architecture layers (no skipping)

1. **Model** — pure data classes, zero Android dependencies
2. **Repository** — interface + implementations. Fake for now, Firestore later.
3. **UndoRedoManager** — in-memory undo/redo stacks; DataStore persistence deferred
4. **ViewModel** — translates intents to Commands, sorts/splits items into UiState
5. **Compose UI** — pure function of UiState, emits callbacks upward

## Current state

Phase 3. Add, edit, delete, check/uncheck items. Edit dialog with add-mode and edit-mode (mode-free composable, ViewModel constructs `ItemDialogState`). Undo/redo via `UndoRedoManager` (in-memory stacks, Command pattern with `reverse()`). ViewModel and UndoRedoManager unit tests in place. No Firebase. Backed by in-memory FakeShoppingRepository.

## Conventions

- Package: `org.righteffort.ourgrocerylist`
- Single Activity, no Fragments
- No third-party DI — manual injection via Application class
- No Mockito — tests use FakeShoppingRepository
- Accent color: `#8775B8`
- ViewModel constructed via `viewModelFactory`/`initializer` DSL (no separate Factory class)

## Key design and implementation rules

- Never swallow errors. Logging and continuing is also unacceptable. If nothing else, bubble the exception to the top level of the app, and surface a dialog to the user and log the problem.
- Do not compromise strong typechecking (e.g. by using typescript syntax, overly permissive casts in any language)
- `ShoppingItem` uses composition: `ItemFields` (user-editable: name, quantity, checked) + `id` (identity). No conflict metadata in the app model — `fingerprint`, `expectedFingerprint`, `previousFields`, `clientId` live only on the Firestore document. The repository maps them on write and strips them on read.
- `EditItem` command takes `newFields: ItemFields`, not individual field parameters.
- The implementation should avoid code that enumerates user-editable fields, in order to minimize the locations that need to change when future user-editable are added (e.g. units, category).
- The edit dialog composable has no concept of mode — it renders `ItemDialogState`. Add-vs-edit branching lives in the ViewModel's construction of `ItemDialogState`.
- The Compose UI layer makes no decisions — it renders UiState and emits callbacks
- The ViewModel has no Compose imports and no Firestore imports
- The repository interface has no Firestore types
- Every mutation is a Command (sealed class) — this is the foundation for undo/redo
- All mutations write directly to Firestore. Conflict detection is after-the-fact via `onUpdate` Cloud Function trigger, Ted will provide design when we get there.
- Undo/redo stacks persisted via `kotlinx.serialization` (JSON) in Preferences DataStore — not Proto DataStore
- On remote write to an item, truncate undo/redo stacks from the first entry referencing that item toward oldest (v0). Recent entries preserved. Field-level pruning deferred.

## How to work together
- Break large implementation tasks into human-reviewable chunks, to
  enable course-correction. But not at the expense of excessive
  stubbing or throwaway code.
- For trivial questions (e.g. "what is the project id?") just ask.

## Repository Structure

- `main` branch: stable/production code
- `dev` branch: active development branch (PRs target `dev` before merging to `main`)
