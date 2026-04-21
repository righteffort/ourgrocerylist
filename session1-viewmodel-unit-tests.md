# Session 1: ViewModel Unit Tests (High-Level Sketch)

**Status**: Needs review of existing unit tests before implementing — check what's already covered under `app/src/test/java/org/righteffort/ourgrocerylist/` before writing anything new.

These tests use `FakeShoppingRepository` (no emulator). Gradle command likely does **not** need `-PrunIntegration`.

## Scenarios to cover

**UiState partitioning and sorting**
- Add items "Zucchini", "Apples", "Milk"; `uncheckedItems` order is Apples, Milk, Zucchini.
- Check one item; it moves to `checkedItems`. `checkedItems` and `uncheckedItems` are each sorted independently.
- Check all items; `uncheckedItems` is empty.

**Undo/redo availability**
- After N sequential actions, N undos reduce `undoAvailable` to false and leave `uiState` at initial state.
- User undoes two actions, then adds a new item; `redoAvailable` becomes false and the previously undone actions cannot be redone.
- `undoAvailable` and `redoAvailable` reflect state correctly throughout a mixed undo/redo sequence.

**Per-list undo isolation**
- User creates List A and List B, adds one item to each, then calls `undo()` while on List A. Only List A's item disappears; List B's item and undo availability are unaffected after switching back.

**CSV import name-collision logic**
- `importListFromCsv("Groceries", csv)` when "Groceries" already exists: `importListDialogState` has `errorMessage` set and `proposedName = "Groceries (1)"`, no list created.
- Second call with "Groceries (1)" succeeds: `currentListName` becomes "Groceries (1)" and `uncheckedItems` contains the parsed items.
