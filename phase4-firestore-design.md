# Phase 4: Firestore Integration — Design Document

See CLAUDE.md ourgrocerylist-handoff.md and deferred.md for additional context.

## Overview

This document describes the design for integrating Firebase Firestore
and Cloud Functions into the Android shopping list app. The existing
architecture (Command pattern, Repository interface, UndoRedoManager,
ViewModel) is unchanged. The additions are a new Repository
implementation backed by Firestore, and the Firebase initialization
plumbing.

The section numbering below may have gaps, that is an artifact of past
edits and is safe to ignore.

---

## 0. Project structure (in place with generic sample code in index.ts)

```
project-root/
├── app/                        # Android app (existing)
├── firebase.json               # Emulator and deployment configuration
├── firebase/                   # All Firebase-specific config and server code
│   ├── .firebaserc             # Project alias (links to Firebase project ID)
│   ├── firestore.rules         # Firestore security rules
│   ├── firestore.indexes.json  # Firestore index definitions
│   └── functions/              # Cloud Functions source
│       ├── package.json
│       ├── tsconfig.json
│       └── src/
│           └── index.ts        # All Cloud Function definitions
└── ...
```

Rationale: keeps all Firebase server-side concerns out of the Android module. The Android app references Firebase only via the SDK and `app/google-services.json`.

---

## 1. Firestore emulator setup: `app/build.gradle.kts` implemented

### Product flavors in `app/build.gradle.kts`

The "environment" flavor control whether the built apk connects to an
emulator ("local", the default) or production ("prod").

### Firebase SDK emulator configuration in `OurGroceryListApp.kt`

We assume the developer will be using a physical device and that `adb
reverse` will be used to forward the emulator ports to the development
machine. `app/build.gradle.kts` is already set up to set
`USE_FIREBASE_EMULATOR`. Section 7 describes the needed changes.

`OurGroceryListApp` is the **only** place in the entire codebase where
`USE_FIREBASE_EMULATOR` is checked. All other code is identical across
flavors.

---

## 2. `FirestoreShoppingRepository`

### Location

```
app/src/main/java/org/righteffort/ourgrocerylist/repository/FirestoreShoppingRepository.kt
```

### Firestore document structure

Collection path: `lists/{listId}/items/{itemId}`

Each item document contains:
```
lists/{listId}/items/{itemId}
{
  fields: {
    name: string,
    quantity: number,
    checked: boolean
  },
  fingerprint: string,
  clientId: string
}
```

| Field                | Firestore type | Description                                      |
|----------------------|----------------|--------------------------------------------------|
| `fields.name`        | string         | Item name                                        |
| `fields.quantity`    | number         | Item quantity (double)                           |
| `fields.checked`     | boolean        | Whether the item is checked                      |
| `fingerprint`        | string         | Stable hash of user-editable fields (see below)  |
| `clientId`           | string         | Identifies which client performed this write. Stable per device, persisted across app restarts. Generated on first launch (UUID), stored in Preferences DataStore. |

The `fingerprint` field is written by the client alongside the data
fields, and is computed at the repository layer.

V0 uses a single hardcoded list ID (e.g. `"default"`). Multi-list support will parameterize this without structural change.

### `fingerprint`

`fingerprint` is by the client on `ItemFields` as follows:

1. Serialize the fields to a canonical JSON string (keys sorted, no
   extra whitespace, numbers formatted consistently). Does not need to
   be language-independent,
   (kotlinx.serialization)[https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/json.md]
   should suffice: `{"checked":false,"name":"Apples","quantity":2.0}`
2. SHA-256 hash the UTF-8 bytes
3. Hex-encode the result

### `observeItems(): Flow<List<ShoppingItem>>`

Attaches a Firestore `addSnapshotListener` to `lists/{listId}/items`. Each snapshot maps all documents to `ShoppingItem` instances and emits the full list. Uses `callbackFlow` to bridge the listener to a Kotlin Flow.

```
Firestore snapshot listener
    → map each DocumentSnapshot to ShoppingItem
    → emit List<ShoppingItem> into callbackFlow
    → caller (ViewModel via UndoRedoManager) collects
```

Document-to-model mapping:

```
DocumentSnapshot → ShoppingItem(
    id = document.id,
    fields = ItemFields(
        name = getString("fields.name"),
        quantity = getDouble("fields.quantity"),
        checked = getBoolean("fields.checked")
    )
)
```

The metadata other than `id` is not mapped into the model (`fingerprint`, `clientId`) — it is a Firestore-layer concern only.

### `apply(command: Command)`

`apply` computes the fingerprint and writes directly to Firestore.

Because `Command.reverse()` naturally carries the correct field snapshots, undo and redo produce correct fingerprints without any special handling.

## 3. Client ID

### Purpose

A stable, per-device identifier used to route conflict notifications
to the correct client. Once we add multi-user support, a single user
may have multiple devices, so `clientId:userId` is a `*:1`
relationship.

### Generation and persistence

On first launch, generate a UUID:

```kotlin
UUID.randomUUID().toString()
```

Persist it in Preferences DataStore under the key `"client_id"`. On
subsequent launches, read the stored value. This is consistent with
the planned DataStore usage for undo/redo persistence.

### Location

Client ID management lives in a small `ClientIdRepository` class (or a top-level suspend function) in a new `app/src/main/java/org/righteffort/ourgrocerylist/client/` package. `OurGroceryListApp` reads the client ID at startup and passes it to `FirestoreShoppingRepository`.

---


## 4. Swapping `FakeShoppingRepository` for `FirestoreShoppingRepository`

### Changes to `OurGroceryListApp.kt`

`OurGroceryListApp` will:
1. Initialize Firebase (once, at startup).
2. Conditionally call `useEmulator()` based on `BuildConfig.USE_FIREBASE_EMULATOR`.
3. Read or generate the client ID from DataStore.
4. Instantiate `FirestoreShoppingRepository` with the Firestore instance, Functions instance, and client ID.
5. Instantiate `UndoRedoManager` with the repository (unchanged).

```kotlin
class OurGroceryListApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            Firebase.firestore.useEmulator("10.0.2.2", 8080)
            Firebase.functions.useEmulator("10.0.2.2", 5001)
        }
    }

    val clientId: String by lazy { /* read or generate from DataStore */ }

    val repository: ShoppingRepository by lazy {
        FirestoreShoppingRepository(
            firestore = Firebase.firestore,
            functions = Firebase.functions,
            listId = "default",
            clientId = clientId,
        )
    }

    val undoRedoManager: UndoRedoManager by lazy { UndoRedoManager(repository) }
}
```

## 5. Undo/redo stack pruning on remote writes

Implement this change only after the core Firestore integration is
done and unit tested.

When the client receives a remote write (a snapshot listener update
from another client) that modifies an item, the stacks are pruned by
truncation. For each stack (undo and redo independently): scan from
newest entry toward oldest. The first entry referencing the modified
item (by ID) and everything older than it are discarded. Entries newer
than the conflicted entry are preserved.

Suggestion: implement this having ViewModel invoke UndoRedoManager to
do the pruning when it detects a write by a different client in the
snapshot flow.

### `FakeShoppingRepository` additions

`FakeShoppingRepository` must implement the two new `ShoppingRepository` interface methods:
- `observeConflicts()` — returns `emptyFlow()`
- `dismissConflict(notificationId: String)` — no-op

---

## 8. Unit tests

Once the new code is stable we will move on to unit tests to cover the new functionality.

---

## 9. Open questions and decisions needed

### 9.1 `clientId` initialization timing

`clientId` is read from DataStore asynchronously, but `OurGroceryListApp` uses `lazy` (synchronous). The client ID must be available before the first Firestore mutation. Options:
- Block the calling coroutine with `runBlocking` in the `lazy` initializer (simple but blocks the main thread briefly on first access).
- Initialize the client ID in a coroutine at app startup and store it in a `Deferred`, awaited before first use.

> REVIEW: let's discuss and decide in chat.

