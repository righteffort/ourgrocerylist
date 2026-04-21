# Session 2: Single-User Integration Tests

## Context

Tests for single-user list lifecycle against the real Firestore emulator. These exercise the `observeLists()` → `uiState` pipeline and the ViewModel's list management logic end-to-end.

## Reference files

- **Pattern**: `app/src/test/java/org/righteffort/ourgrocerylist/FirstSingleUserIntegrationTest.kt`
  (class `DifferentIntegrationTest`) — copy its `@Before`/`@After`/class annotations exactly.
- **Shared instructions**: `shared-integration-test-instructions.md`

## New file

`app/src/test/java/org/righteffort/ourgrocerylist/SingleUserListIntegrationTest.kt`

One `TestUser` (`userA`). Same `@Before`/`@After` structure as `DifferentIntegrationTest`.

## Test methods to implement

### `list creation appears in uiState`
`userA.viewModel.addList("Groceries")`. Drain until `uiState.lists` contains a list named "Groceries". Assert `currentListName == "Groceries"` and `isOwner == true`.

### `list rename reflected in uiState`
Create list, drain until visible. Call `renameCurrentList("Weekend Run")`. Drain until `uiState.currentListName == "Weekend Run"`. Also assert the `uiState.lists` entry name updated.

### `delete list with others present selects another`
Create two lists ("Alpha", "Beta"), drain until both visible, select "Alpha". Call `deleteCurrentList()`. Drain until `uiState.lists` no longer contains "Alpha". Assert `currentListName` is "Beta" (or non-empty — whichever list remains).

### `delete only list triggers Groceries auto-creation`
Create one list, drain until visible, select it. Call `deleteCurrentList()`. Drain until `uiState.lists` is non-empty again. Assert `currentListName == "Groceries"`, `uncheckedItems` is empty, `isOwner == true`.

### `switching lists changes items`
Create two lists. Add item "Apples" to list A, item "Bread" to list B. Select list A; drain until `uncheckedItems` contains "Apples". Select list B; drain until `uncheckedItems` contains "Bread". Assert "Apples" is absent from list B's state.

### `items from different lists do not bleed`
Create two lists, add one item to each. After selecting list B and confirming its item is visible, assert `uncheckedItems` contains exactly one item (not both).
