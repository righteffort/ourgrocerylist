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
firebase/
├── firestore.rules  # Firestore security rules
└── functions/       # Firebase functions

```

## Architecture layers (no skipping)

1. **Model** — pure data classes, zero Android dependencies
2. **Repository** — interface + implementations. Firestore.
3. **UndoRedoManager** — in-memory undo/redo stacks; DataStore persistence deferred
4. **ViewModel** — translates intents to Commands, sorts/splits items into UiState
5. **Compose UI** — pure function of UiState, emits callbacks upward

## Current state

Core functionality implemented: Add, edit, delete, check/uncheck items. Edit dialog with add-mode and edit-mode (mode-free composable, ViewModel constructs `ItemDialogState`). Undo/redo via `UndoRedoManager` (in-memory stacks, Command pattern with `reverse()`). ViewModel and UndoRedoManager unit tests in place. No Firebase. Backed by in-memory FakeShoppingRepository.

List sharing across users & devices implemented via Firestore; last-writer wins.

## Conventions

- Package: `org.righteffort.ourgrocerylist`
- Single Activity, no Fragments
- No third-party DI — manual injection via Application class
- No mocks! Use fakes when necessary
- Accent color: `#8775B8`
- ViewModel constructed via `viewModelFactory`/`initializer` DSL (no separate Factory class)

## Key design and implementation rules

- Never swallow errors, such as
  - catch blocks that are empty, log-and-continue, or rethrow a weaker type
  - ?. chains or ?: default fallbacks on data that must be present — if it's absent, that's an error, not a null
  - as? casts on data that must be a particular type — a failed cast should throw, not silently produce null
  - conditional checks gated on if (x != null) where x being null indicates a bug rather than a normal case
  If nothing else, bubble the exception to the top level of the app, surface a dialog to the user, and log the problem.
- Do not compromise strong typechecking (e.g. by using typescript syntax, overly permissive casts in any language, forced casts, risky non-null assertions)
- The implementation should avoid code that enumerates user-editable fields, in order to minimize the locations that need to change when future user-editable are added (e.g. units, category).
- The edit dialog composable has no concept of mode — it renders `ItemDialogState`. Add-vs-edit branching lives in the ViewModel's construction of `ItemDialogState`.
- The Compose UI layer makes no decisions — it renders UiState and emits callbacks
- The ViewModel has no Compose imports and no Firestore imports
- The repository interface has no Firestore types
- Every mutation is a Command (sealed class) — this is the foundation for undo/redo
- All mutations write directly to Firestore. Last-writer wins.
- Undo/redo stacks persisted via `kotlinx.serialization` (JSON) in Preferences DataStore — not Proto DataStore
- On remote write to an item, truncate undo/redo stacks from the first entry referencing that item toward oldest (v0). Recent entries preserved. Field-level pruning deferred.

## How to work together
- Break large implementation tasks into human-reviewable chunks, to
  enable course-correction. But not at the expense of excessive
  stubbing or throwaway code.
- For trivial questions (e.g. "what is the project id?") just ask.

## Repository Structure

- `main` branch: stable/production code
- `dev` branch: current active development branch (PRs target `dev` before merging to `main`)
