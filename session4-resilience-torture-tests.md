# Session 4: Resilience and Race Condition Torture Tests

## Context

Stress tests targeting races between the local Firestore cache and the server: network
interruptions, the list-creation PERMISSION_DENIED race (the bug fixed with `retryWhen`),
undo/redo correctness under remote writes, and concurrent multi-user writes. These tests
use `firestore.disableNetwork()` / `enableNetwork()` / `waitForPendingWrites()` — public
Firestore SDK APIs — to control timing precisely.

Follows the patterns established in `SingleUserListIntegrationTest` (session 2) and
`MultiUserPropagationTest` (session 3).

## Reference files

- **Pattern**: `app/src/test/java/org/righteffort/ourgrocerylist/SingleUserListIntegrationTest.kt`
  and `MultiUserPropagationTest.kt` — copy class annotations exactly.
- **Shared instructions**: `shared-integration-test-instructions.md`

## New file

`app/src/test/java/org/righteffort/ourgrocerylist/ResilienceIntegrationTest.kt`

Mix of single-user and two-user tests. Use `userA` always; add `userB` only where noted.

## Test fixture changes required

### Expose `firestore` on `TestUser`

Add `lateinit var firestore: FirebaseFirestore` to the `TestUser` class and assign it in
`setupUser()`. Tests need it for network control and `waitForPendingWrites`.

### Add network-control helpers to `IntegrationTestFixtures.kt`

```kotlin
internal suspend fun TestUser.disableNetwork() =
    firestore.disableNetwork().awaitInRobolectric()

internal suspend fun TestUser.enableNetwork() =
    firestore.enableNetwork().awaitInRobolectric()

internal suspend fun TestUser.waitForPendingWrites() =
    firestore.waitForPendingWrites().awaitInRobolectric()
```

No changes to production code are needed.

---

## Test methods

### `items written offline appear after reconnect` (single-user)

1. Create list, drain until current.
2. `userA.disableNetwork()`.
3. Add items "Eggs", "Milk", "Bread" (all go to local cache only).
4. Assert all three appear immediately in `uncheckedItems` (served from cache).
5. `userA.enableNetwork()`.
6. `userA.waitForPendingWrites()`.
7. Assert all three still present — server confirmed, no items lost or duplicated.

Validates that fire-and-forget writes coalesce correctly on reconnect.

---

### `undo of offline write is consistent after reconnect` (single-user)

1. Create list, drain until current.
2. `userA.disableNetwork()`.
3. Add item "Eggs", drain until visible in uncheckedItems (from cache).
4. `userA.viewModel.undo()`, drain until "Eggs" absent.
5. `userA.enableNetwork()`.
6. `userA.waitForPendingWrites()`.
7. Assert "Eggs" still absent — the add and the undo both committed; net result is empty.

Validates that an undo performed entirely offline round-trips correctly to the server.

---

### `check-then-undo offline leaves item unchecked after reconnect` (single-user)

1. Create list, add "Milk", drain until visible. `waitForPendingWrites()` to confirm server
   has it.
2. `userA.disableNetwork()`.
3. `checkItem("Milk")`, drain until "Milk" in `checkedItems`.
4. `undo()`, drain until "Milk" back in `uncheckedItems`.
5. `userA.enableNetwork()`, `waitForPendingWrites()`.
6. Assert "Milk" in `uncheckedItems` and `checkedItems` is empty.

Ensures undo of a check operation doesn't leave phantom checked state on the server.

---

### `item listener recovers after network interruption` (single-user)

1. Create list, add "Apples", drain until visible. `waitForPendingWrites()`.
2. `userA.disableNetwork()`.
3. `userA.enableNetwork()`.
4. Add "Oranges" (written post-reconnect).
5. Drain until both "Apples" and "Oranges" in `uncheckedItems`.

Validates that the snapshot listener reattaches cleanly after a transient disconnect and
does not miss items added immediately after reconnect.

---

### `list creation PERMISSION_DENIED race: item add is responsive` (single-user)

This is a regression test for the bug where the item snapshot listener died on
PERMISSION_DENIED before the list document committed, leaving items invisible.

1. `userA.viewModel.addList("New List")` — do NOT wait for server confirmation; proceed
   immediately once the list appears in `uiState.lists` (i.e. the optimistic write has
   fired `observeLists`, which is what triggered the race).
2. Immediately (same tick) add item "Instant" via `openAddDialog` / `onSave`.
3. Drain until "Instant" appears in `uncheckedItems` within the test timeout.
4. `waitForPendingWrites()`. Assert "Instant" still present.

The race window: `observeLists` fires on `hasPendingWrites=true`, item listener starts,
gets PERMISSION_DENIED, `retryWhen` must recover before the test times out.

---

### `multiple rapid list creations: items isolated` (single-user)

1. Call `addList("L1")`, `addList("L2")`, `addList("L3")` in rapid succession (no draining
   between).
2. Drain until all three appear in `uiState.lists` and `currentListName` is "L3".
3. Add "ItemC" to L3, drain until visible.
4. `selectList(L2.id)`, drain until current. Add "ItemB", drain until visible.
5. `selectList(L1.id)`, drain until current. Add "ItemA", drain until visible.
6. Assert each list has exactly its own one item; no bleed.

Validates that rapid list creation doesn't confuse resource creation or listener wiring.

---

### `remote write prunes undo stack; owner cannot undo past it` (two-user)

Uses `setupSharedList` from session 3.

1. A adds "Eggs", drain until B sees "Eggs".
2. A adds "Milk", drain until B sees "Milk". A now has two undo entries.
3. B calls `editItem` on "Eggs" → "Bread". Drain until A sees "Bread" in `uncheckedItems`.
   At this point A's undo stack entry referencing "Eggs"' ID should be pruned.
4. A calls `undo()` once. Assert "Milk" disappears (the undo for "Milk" is still valid).
5. A calls `undo()` again. Assert `uncheckedItems` still contains "Bread" — the "Eggs" add
   entry was pruned and cannot be undone. `undoAvailable` should now be `false`.

---

### `remote delete of item prunes undo; no resurrection` (two-user)

Uses `setupSharedList`.

1. A adds "Ghost", drain until B sees it.
2. B deletes "Ghost". Drain until A sees "Ghost" absent.
3. A calls `undo()`. Assert "Ghost" does NOT reappear — the undo entry was pruned by B's
   remote delete.

---

### `writes by both users while one is offline converge` (two-user)

Uses `setupSharedList`.

1. `userA.disableNetwork()`.
2. While A is offline: A adds "Offline-A". B adds "Online-B", drain until B sees it.
3. `userA.enableNetwork()`, `userA.waitForPendingWrites()`.
4. Drain until A sees both "Offline-A" and "Online-B" in `uncheckedItems`.
5. Drain until B sees both items too.

Validates eventual consistency when writes are interleaved across an offline window.

---

### `B observes A's offline writes after A reconnects` (two-user)

Uses `setupSharedList`.

1. `userA.disableNetwork()`.
2. A adds "Secret", "Hidden" (cached only).
3. Assert B does NOT yet see these items (confirm B's current state has neither).
4. `userA.enableNetwork()`, `userA.waitForPendingWrites()`.
5. Drain B until both "Secret" and "Hidden" appear in B's `uncheckedItems`.

---

### `listener for non-current list stays warm; switch shows cached items` (single-user)

1. Create list A, add "Apples", `waitForPendingWrites()`.
2. Create list B (`addList`), drain until current. Add "Bread", `waitForPendingWrites()`.
3. `userA.disableNetwork()`.
4. `selectList(A.id)`, drain until `currentListName == "List A"`.
5. Assert "Apples" appears immediately in `uncheckedItems` without awaiting any new items
   (i.e. the first state after selecting list A already has "Apples" — served from the
   warm cache kept by the background `observeRemotelyModifiedItemIds` listener).

This validates the "fringe benefit: keeps cache warm" comment on
`observeRemotelyModifiedItemIds`.
