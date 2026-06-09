# Session 4: Multi-User Concurrent Writes and Undo Isolation

## Context

Tests involving simultaneous writes from multiple users and undo/redo correctness under multi-user conditions. These are the most complex scenarios — they require careful drain ordering and may need diagnostic logging.

## Reference files

- **Pattern**: `app/src/test/java/org/righteffort/ourgrocerylist/FirstMultiUserIntegrationTest.kt` and session 3's `MultiUserPropagationTest.kt` (copy the `setupSharedList` helper).
- **Shared instructions**: `shared-integration-test-instructions.md`

## New file

`app/src/test/java/org/righteffort/ourgrocerylist/MultiUserConcurrentTest.kt`

Two `TestUser` values (`userA`, `userB`). Same `@Before`/`@After` structure. Copy or import `setupSharedList` helper from session 3.

For the three-user test, add a `userC` with `email = "test3@test.invalid"` and `appName = "userC"` and set it up in `@Before`/`@After` only for that test (or add it to the class-level setup).

## Test methods to implement

### `concurrent adds from both users; both see all items`
Use `setupSharedList`. A calls `addItem("Apples")`, B calls `addItem("Bread")` (no awaiting between them). Drain A until `uncheckedItems` contains both. Drain B until `uncheckedItems` contains both.

### `A and B check different items; both see all items checked`
Use `setupSharedList`. A adds "Apples" and "Bread"; drain until both users see both items. A checks "Apples", B checks "Bread" (no awaiting between). Drain both users until `checkedItems.size == 2` and `uncheckedItems` is empty.

### `A adds items rapidly; B sees all of them`
Use `setupSharedList`. A calls `addItem()` 5 times in succession without awaiting. Drain B until `uncheckedItems.size == 5`.

### `three users all add items; all see all items`
Set up A's list, share with B and C. Each of the three users calls `addItem()` with a distinct name. Drain all three turbines until each sees `uncheckedItems.size == 3`.

### `B's item unaffected by A's undo`
Use `setupSharedList`. A adds "Apples", B adds "Bread". Drain both until each sees both items. A calls `undo()`. Drain A until `uncheckedItems` no longer contains "Apples". Drain B; assert B's `uncheckedItems` still contains "Bread" (and not "Apples").

### `remote write prunes A's undo stack for that item only`
Use `setupSharedList`. A adds two items: "Item1" and "Item2" (two undo entries). B edits "Item1" (changing its name), which counts as a remote write to that item from A's perspective. Drain A until it sees B's edit. A calls `undo()` once. Assert the undo operated on "Item2" (the unaffected item), not "Item1", and `uiState` reflects accordingly.

### `new action after partial undo clears redo`
Single-user test (can use `setupSharedList` with just A acting, or restructure as single-user). A adds "Apples", "Bread", "Milk". Undoes "Milk", undoes "Bread" (assert `redoAvailable == true`). Adds "Eggs". Assert `redoAvailable == false` and `uncheckedItems` contains "Apples" and "Eggs" only.
