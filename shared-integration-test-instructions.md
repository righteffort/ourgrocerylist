# Shared: Integration Test Session Instructions

## General rules

Obey CLAUDE.md

Ask for clarification if anything is unclear.

**Assume the code works**: When a test for a complex scenario appears soundly written but continues to fail, the cause may be a race condition or timing issue *in the code under test*, not in the test itself. Escalate to diagnosing the production code rather than tightening drain conditions further.

**Diagnostic logging for complex failures**: When a drain times out unexpectedly, add `println` statements before and inside the drain to print the current `state` on each iteration. This quickly reveals whether items are arriving at all, what their content is, and where the flow is stalling.

## Verification

Run only the target test class after writing tests:
```
./gradlew testDebugUnitTest --rerun -PrunIntegration --tests 'org.righteffort.ourgrocerylist.CLASSNAME'
```

**Iteration limit**: Run tests, read output, add diagnostic logging or fix once, re-run. Stop after 2 iterations regardless of outcome and report what passed, what failed, and what you observed.

## Key patterns (from FirstMultiUserIntegrationTest.kt)

**Drain loop** — standard way to wait for a condition:
```kotlin
while (stateA.uncheckedItems.isEmpty()) { stateA = turbineA.awaitItem() }
```

**`while` vs `do-while`**: Once `state` has been initialized by any prior `awaitItem()` call, use `while (condition) { state = awaitItem() }` — never `do { state = awaitItem() } while (condition)`. The do-while always consumes one item even when the condition is already satisfied, causing a spurious timeout if no further emission arrives. The only valid use of do-while is the *first* drain in a test where `state` is not yet initialized:
```kotlin
// First drain — state uninitialized, do-while is required
var state: UiState
do { state = awaitItem() } while (state.lists.none { it.name == "Groceries" })

// All subsequent drains — state is initialized, use while
while (state.currentListName != "Groceries") { state = awaitItem() }
```

**Compound drain for list activation**: When waiting for a list to become active (via `addList` or `selectList`), drain on *both* the list appearing in `state.lists` AND `state.currentListName` matching. A single-condition drain can exit before `_confirmedListIds` has been updated, leaving `observeItems` inactive and causing subsequent item drains to time out:
```kotlin
// Wrong — may exit before observeItems is active for the list
while (state.lists.none { it.name == "My List" }) { state = awaitItem() }

// Correct
while (state.lists.none { it.name == "My List" } || state.currentListName != "My List") {
    state = awaitItem()
}
```

**`addList` auto-switches**: `addList()` sets `_currentListId` to the new list internally. Do not call `selectList(id)` immediately after `addList` for the same list. If `_currentListId` is already that value, `StateFlow` deduplicates and emits nothing; any subsequent drain will time out.

**Multi-user turbine setup**:
```kotlin
turbineScope {
    val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
    val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
    // ...
    turbineA.cancelAndIgnoreRemainingEvents()
    turbineB.cancelAndIgnoreRemainingEvents()
}
```

**Single-user turbine setup** (from FirstSingleUserIntegrationTest.kt):
```kotlin
userA.viewModel.uiState.test(timeout = 15.seconds) {
    // ...
    cancelAndIgnoreRemainingEvents()
}
```

**Acting via dialog callbacks** (use this path for add/edit, same as the real UI):
```kotlin
userA.viewModel.openAddDialog("")
userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Milk", quantity = 2.0, checked = false))
```

**Using the other user's snapshot** when acting on an item one user observed but the other wrote:
```kotlin
userA.viewModel.checkItem(stateB.uncheckedItems.single()) // use B's snapshot to avoid races
```

## Class/file conventions

- Each session creates a **new test class and file** — do not modify the `First*` example files.
- Class names end in `IntegrationTest`, file name matches class name.
- Multi-user classes: copy the `@Before`/`@After` structure from `SharedListIntegrationTest`.
- Single-user classes: copy the `@Before`/`@After` structure from `DifferentIntegrationTest` (includes `Dispatchers.setMain`).
- No mocks. No `Thread.sleep`. No fixed delays.
