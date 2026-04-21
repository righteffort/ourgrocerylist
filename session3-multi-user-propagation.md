# Session 3: Multi-User Propagation Tests

## Context

Tests where one user's writes are observed by a second user. No concurrent writes yet — A acts, B observes (or vice versa). Builds on the two-user setup already established in `SharedListIntegrationTest`.

## Reference files

- **Pattern**: `app/src/test/java/org/righteffort/ourgrocerylist/FirstMultiUserIntegrationTest.kt`
  (class `SharedListIntegrationTest`) — copy its `@Before`/`@After`/class annotations exactly.
- **Shared instructions**: `shared-integration-test-instructions.md`

## New file

`app/src/test/java/org/righteffort/ourgrocerylist/MultiUserPropagationTest.kt`

Two `TestUser` values (`userA`, `userB`). Same `@Before`/`@After` structure as `SharedListIntegrationTest`.

## Shared setup helper

Extract a `suspend fun setupSharedList(turbineA, turbineB): String` and add it to `app/src/test/java/org/righteffort/ourgrocerylist/IntegrationTestFixtures.kt` that:
1. A creates a list, B creates a list.
2. Drains until each user sees their own list.
3. A selects their list, shares it with B.
4. Drains until B sees a list where `isOwner == false`.
5. B selects the shared list; drains until B sees it with no items.
6. Returns the shared `listId`.

Reuse this in every test below.

## Test methods to implement

### `editor sees pre-existing items`
A creates list, adds 3 items, then shares with B. (Don't use the shared helper here — share after items exist.) B drains until it sees all 3 items in `uncheckedItems`.

### `A renames shared list; B sees new name`
Use shared setup helper. A calls `renameCurrentList("Renamed List")`. B drains until `uiState.lists.first { it.id == listId }.name == "Renamed List"`. Also assert `uiState.currentListName` updated if B has it selected.

### `A renames item; B sees updated name`
Use shared setup helper. A adds item "OldName". B drains until it sees "OldName". A calls `editItem()` with `newFields.name = "NewName"` using B's snapshot. B drains until `uncheckedItems` contains "NewName" and not "OldName".

### `B deletes item A added; A sees deletion`
Use shared setup helper. A adds item "Eggs". B drains until it sees "Eggs". B calls `deleteItem()` using B's snapshot. A drains until `uncheckedItems` no longer contains "Eggs".

### `B unchecks item A checked; A sees it back in uncheckedItems`
Use shared setup helper. A adds item, checks it. B drains until it sees the item in `checkedItems`. B calls `uncheckItem()`. A drains until `uncheckedItems` contains the item and `checkedItems` is empty.

### `A's private list invisible to B`
A and B each have their own list (standard setup, but no sharing). A creates a second list "Private". Drain A until it sees "Private". Assert B's `uiState.lists` never contains a list named "Private" (check B's current state; no further waiting needed).

### `isOwner flag correct for both users`
Use shared setup helper. Assert A's `uiState.isOwner == true` for the shared list. Assert B's `uiState.isOwner == false` for the same list.

### `A deletes shared list; B no longer sees it`
Use shared setup helper. A calls `deleteCurrentList()`. B drains until `uiState.lists.none { it.id == listId }`.
