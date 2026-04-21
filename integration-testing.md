# Integration Testing: Firebase + Robolectric

## Why Robolectric

Firebase Android SDK requires an Android `Context` for `FirebaseApp.initializeApp()`, plus
`Looper.getMainLooper()` for its main-thread callback dispatcher, and `SharedPreferences` for
internal caching. All of these are stubs (`RuntimeException("Stub!")`) in the unit-test
classpath. A synthesized `Context` subclass is not enough — you'd also need working `Looper`
and `Handler`, which are the same stubs.

Robolectric replaces all stubs with working shadow implementations and is the standard
JVM-based solution. The alternative is instrumented tests (on-device/emulator), which carry
their own setup burden.

## Looper mode: INSTRUMENTATION_TEST

```kotlin
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
```

Default Robolectric looper mode is `PAUSED`: the main looper only processes messages when
you explicitly call `ShadowLooper.idleMainLooper()`, and that call must come from the main
thread. Firebase delivers Firestore snapshot callbacks via the main looper, so without
pumping, callbacks never arrive.

`INSTRUMENTATION_TEST` mode runs the main looper on its own dedicated thread (mimicking a
real device). Firestore callbacks are delivered automatically — no manual pumping needed.
This enables clean `awaitItem()` usage in turbine.

### PAUSED mode fallback pattern

If stuck on `PAUSED` mode, pump the looper from the main thread between polling iterations.
`awaitItem()` cannot be used because it suspends (giving up the main thread), so poll
`.value` instead (valid once a subscriber has activated `WhileSubscribed`):

```kotlin
userA.viewModel.uiState.test {           // subscribes → WhileSubscribed activates
    val deadline = System.currentTimeMillis() + 15_000
    while (uiState.value.lists.none { ... }) {
        check(System.currentTimeMillis() < deadline) { "Timed out" }
        Thread.sleep(50)                 // let real gRPC threads run
        ShadowLooper.idleMainLooper()    // deliver pending Firestore callbacks
    }
    cancelAndIgnoreRemainingEvents()
}
```

`ShadowLooper.idleMainLooper()` from a background thread (e.g. `Dispatchers.IO`) throws
`UnsupportedOperationException: main looper can only be controlled from main thread`.

## WhileSubscribed and StateFlow.value

`uiState` is built with `SharingStarted.WhileSubscribed(5000)`. The upstream `combine()`
only runs while there are active subscribers. Reading `.value` in a polling loop without
first subscribing always returns the initial `UiState()`.

Fix: subscribe via turbine's `test {}` or `testIn(backgroundScope)` before polling.
The ViewModel's `init` block subscriptions (plain `viewModelScope.launch`) are always active,
but they only update `_currentListId` / `_confirmedListIds`, not `uiState.value` directly.

## Two-ViewModel testing with turbineScope

```kotlin
turbineScope {
    val turbineA = viewModelA.uiState.testIn(backgroundScope, timeout = 15.seconds)
    val turbineB = viewModelB.uiState.testIn(backgroundScope, timeout = 15.seconds)

    // Both WhileSubscribed combines are now active. Interleave freely:
    var stateA = turbineA.awaitItem()
    while (stateA.lists.none { it.name == "User A List" }) { stateA = turbineA.awaitItem() }

    var stateB = turbineB.awaitItem()
    while (...) { stateB = turbineB.awaitItem() }

    turbineA.cancelAndIgnoreRemainingEvents()
    turbineB.cancelAndIgnoreRemainingEvents()
}
```

`testIn(backgroundScope)` collects each flow concurrently in turbine's background scope.
Each has its own independent drain loop. This is cleaner than nesting `test {}` blocks or
using `combine()` to merge the two flows.

## awaitInRobolectric for Firebase Tasks

Firebase Auth operations (e.g. `createUserWithEmailAndPassword`) return a `Task<T>`. Plain
`.await()` may work with `INSTRUMENTATION_TEST` mode, but `awaitInRobolectric()` is a safe
fallback that pumps the main looper until the task completes:

```kotlin
private suspend fun <T> Task<T>.awaitInRobolectric(): T {
    while (!isComplete) {
        ShadowLooper.idleMainLooper()
    }
    return await()
}
```

## Firebase Functions region mismatch

`FirebaseFunctions.getInstance(app)` defaults to `us-central1`.
`setUpFirebaseEmulators` calls `useEmulator()` on the `us-west1` instance.
These are separate objects — the `us-central1` instance never gets `useEmulator()` called,
so calls go to the wrong URL and return 404 with no emulator log entry.

Always specify the region explicitly when constructing the repository in tests:

```kotlin
val functions = FirebaseFunctions.getInstance(testUser.app, "us-west1")
```

`Firebase.appFunctions` (extension property on `Firebase` companion) is a shorthand for
the default app's `us-west1` instance. In tests with named `FirebaseApp` instances it
cannot be used — call `FirebaseFunctions.getInstance(namedApp, region)` directly.

## Two named FirebaseApp instances

Two independent Firestore/Auth clients require two named `FirebaseApp` instances:

```kotlin
val appA = FirebaseApp.initializeApp(context, options, "userA")
val appB = FirebaseApp.initializeApp(context, options, "userB")
val firestoreA = FirebaseFirestore.getInstance(appA)
val firestoreB = FirebaseFirestore.getInstance(appB)
```

Tear down with `appA.delete()` / `appB.delete()` in `@After`.

## Test dispatcher

`Dispatchers.setMain(UnconfinedTestDispatcher())` makes the ViewModel's `viewModelScope`
coroutines run eagerly on the calling thread. Required when using `runTest` so that
`viewModelScope.launch` blocks execute without needing `advanceUntilIdle()`. Reset in
`@After` with `Dispatchers.resetMain()`.

With `INSTRUMENTATION_TEST` looper mode, the main looper runs on its own thread, so this
may not be strictly necessary — but it keeps coroutine execution predictable.
