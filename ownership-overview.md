# Ownership & Lifecycle Overview

Note: some of this may be updated, especially recording listeners

## Object Ownership

### Application-scoped (singleton, app lifetime)

`OurGroceryListApp` owns everything global:

- **`clientId: String`** — lazy, created via UUID on first run, persisted to DataStore via `ClientIdRepository`, then held in memory for the app lifetime. Retrieved with `runBlocking` once.
- **`_currentUser: MutableStateFlow<User?>`** — owned here, **mutated only by `MainActivity`** during sign-in. Exposed as immutable `StateFlow`.
- **`listRepository: FirestoreListRepository`** — lazy singleton.
- **`sharingRepository`** — lazy singleton.

### Activity-scoped

`MainActivity` owns nothing persistent. Its job is to authenticate (via Firebase Auth) and set `app._currentUser.value`. It does this in `lifecycleScope.launch`, tied to the Activity's lifecycle.

### ViewModel-scoped

`ShoppingViewModel` owns a `Map<String, ListResources>` where each entry has:

- A `ShoppingRepository` instance for that list
- An `UndoRedoManager` for that list
- An `observationJob: Job` (collects `observeRemotelyModifiedItemIds()` flow)

Resources are created lazily on first access and **never torn down during list switches** — they persist for the ViewModel's lifetime. When the ViewModel is cleared, `viewModelScope` cancels, which cancels all `observationJob`s, which cancels the `callbackFlow`s, which calls `awaitClose { listener.remove() }` on all Firestore listeners.

---

## Firestore Listener Lifecycle

Both `observeItems()` and `observeRemotelyModifiedItemIds()` use `callbackFlow`:

```kotlin
override fun observeItems(): Flow<List<ShoppingItem>> = callbackFlow {
    val listener = collection.addSnapshotListener { snapshot, _ -> trySend(...) }
    awaitClose { listener.remove() }
}
```

Listener is created when the flow is collected, removed when it's cancelled. The `observationJob` in `ListResources` holds the collection alive; it dies with `viewModelScope`.

`observeLists()` in `FirestoreListRepository` adds a layer: it chains off `currentUserFlow.filterNotNull().flatMapLatest { ... }`. Two listeners (owned + editor queries) are active inside the inner `callbackFlow`. When the user changes (or becomes null), `flatMapLatest` cancels the inner flow, triggering `awaitClose` to remove both listeners.

---

## Coroutine Scopes

| Scope | Owner | Usage | Lifetime |
|---|---|---|---|
| `viewModelScope` | `ShoppingViewModel` | All ViewModel-launched coroutines | ViewModel creation → destruction |
| `lifecycleScope` | `MainActivity` | Auth/init in `onCreate` | Activity creation → destruction |
| Firestore listeners (implicit) | `callbackFlow` | Snapshot listeners for repos | Flow collection → cancellation |

---

## Race Conditions — What's Protected and What Isn't

**Protected:**

- `StateFlow.value` assignments are atomic (Kotlin/JVM guarantee), so `currentUser` and item list state are safe to read from any coroutine.
- All ViewModel mutations go through `viewModelScope`, which runs on `Dispatchers.Main.immediate` by default. Since it's single-threaded, `UndoRedoManager`'s `ArrayDeque` stacks need no locks — operations are serialized by the dispatcher.
- Firestore snapshot listeners call back on the main thread, so `trySend` in `callbackFlow` doesn't race with ViewModel consumers.
- `clientId` uses Kotlin's default `LazyThreadSafetyMode.SYNCHRONIZED`, so concurrent first-access is safe.

**Implicit / potential concerns:**

- `listResources` in `ShoppingViewModel` is a plain `mutableMapOf` — not thread-safe. Safe in practice because all access is on the main thread via `viewModelScope`, but not enforced.
- The `sendCombined()` helper inside `FirestoreListRepository.observeLists()` captures `ownedDocs` and `editorDocs` as `var`s in the closure. Two Firestore listeners could both fire and call `sendCombined()`. Currently safe because Firestore callbacks are main-thread, but fragile if that assumption ever changed.
- Undo/redo pruning on remote write (`pruneForRemoteWrite`) is called from within the `observationJob` collecting `observeRemotelyModifiedItemIds()`. That job is also on `viewModelScope`/main thread, so it's serialized with user-initiated undo/redo operations.

**Bottom line:** The design leans on "everything important happens on the main thread via viewModelScope/Dispatchers.Main" rather than explicit locks or `Mutex`. This is idiomatic for Android/Kotlin, but the safety is implicit rather than enforced.
