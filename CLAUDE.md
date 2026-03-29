# CLAUDE.md

## What this is

Native Android shopping list app. Kotlin, Jetpack Compose, MVVM. See `shopping-list-handoff.md` for full design and architecture decisions.

## Project structure

```
app/src/main/java/org/righteffort/ourgrocerylist/
├── model/           # Pure Kotlin data classes (ShoppingItem, Command)
├── repository/      # ShoppingRepository interface + FakeShoppingRepository
├── ui/              # ViewModel, UiState, Compose screens, theme
│   └── theme/
└── OurGroceryListApp.kt  # Application class, manual DI
```

## Architecture layers (no skipping)

1. **Model** — pure data classes, zero Android dependencies
2. **Repository** — interface + implementations. Fake for now, Firestore later.
3. **UndoRedoManager** — not yet implemented
4. **ViewModel** — translates intents to Commands, sorts/splits items into UiState
5. **Compose UI** — pure function of UiState, emits callbacks upward

## Current state

Phase 1 skeleton. Add items, check/uncheck, sorted list with divider. No edit dialog, no undo/redo, no delete, no Firebase. Backed by in-memory FakeShoppingRepository.

## Conventions

- Package: `org.righteffort.ourgrocerylist`
- Single Activity, no Fragments
- No third-party DI — manual injection via Application class
- No Mockito — tests use FakeShoppingRepository
- Accent color: `#8775B8`
- ViewModel constructed via `viewModelFactory`/`initializer` DSL (no separate Factory class)

## Key design rules

- The Compose UI layer makes no decisions — it renders UiState and emits callbacks
- The ViewModel has no Compose imports and no Firestore imports
- The repository interface has no Firestore types
- Every mutation is a Command (sealed class) — this is the foundation for undo/redo
- Undo/redo stacks persisted via `kotlinx.serialization` (JSON) in Preferences DataStore — not Proto DataStore
- On undo/redo conflict, discard all stack entries referencing the conflicted item (by ID), not just the failed entry
- ShoppingItem.version is opaque below the Firestore repository layer — other code carries it but never reads or increments it

## Repository Structure

- `main` branch: stable/production code
- `dev` branch: active development branch (PRs target `dev` before merging to `main`)
