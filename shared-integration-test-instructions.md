# Shared: Integration Test Session Instructions

## General rules

Obey CLAUDE.md

Ask for clarification if anything is unclear.

## Verification

Run only the target test class after writing tests:
```
./gradlew testLocalDebugUnitTest --rerun -PrunIntegration --tests 'org.righteffort.ourgrocerylist.CLASSNAME'
```

**Iteration limit**: Run tests, read output, add diagnostic logging or fix once, re-run. Stop after 2 iterations regardless of outcome and report what passed, what failed, and what you observed.

## Key patterns (from FirstMultiUserIntegrationTest.kt)

**Drain loop** — standard way to wait for a condition:
```kotlin
while (stateA.uncheckedItems.isEmpty()) { stateA = turbineA.awaitItem() }
```

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
