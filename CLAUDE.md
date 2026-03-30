# CLAUDE.md

## What this is

Native Android shopping list app. Kotlin, Jetpack Compose, MVVM. See `shopping-list-handoff.md` for full design and architecture decisions.

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

## Key design rules

- `ShoppingItem` uses composition: `ItemFields` (user-editable: name, quantity, checked) vs system fields (id). `EditItem` command takes `newFields: ItemFields`, not individual field parameters.
- The edit dialog composable has no concept of mode — it renders `ItemDialogState`. Add-vs-edit branching lives in the ViewModel's construction of `ItemDialogState`.
- The Compose UI layer makes no decisions — it renders UiState and emits callbacks
- The ViewModel has no Compose imports and no Firestore imports
- The repository interface has no Firestore types
- Every mutation is a Command (sealed class) — this is the foundation for undo/redo
- Undo/redo stacks persisted via `kotlinx.serialization` (JSON) in Preferences DataStore — not Proto DataStore
- On undo/redo conflict, discard all stack entries referencing the conflicted item (by ID), not just the failed entry
- Conflict detection uses `ItemFields.fingerprint` — a stable hash of user-editable fields, replacing monotonic version numbers. The fingerprint is a computed property of `ItemFields`, so `Command.reverse()` naturally produces commands with the correct expected fingerprint. The client computes and sends both expected and new fingerprint with each mutation; the Cloud Function reads the stored fingerprint from the Firestore document and compares — no server-side hash computation. Cross-platform hash implementation (canonical JSON → SHA-256) deferred to Firestore phase.

## Repository Structure

- `main` branch: stable/production code
- `dev` branch: active development branch (PRs target `dev` before merging to `main`)
