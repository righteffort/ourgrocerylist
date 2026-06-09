# Conflict detection via direct Firestore writes and onUpdate trigger

## Overview

Clients write directly to Firestore. The SDK handles offline persistence, ordered delivery, retry, and optimistic local updates. A Cloud Function `onUpdate` trigger detects conflicts after the fact and notifies affected clients. No callable functions, no client-side command queue, no custom sync engine.

## Item document structure

See ourgrocerylist-handoff.md, especially the `baseFingerprint` field.

## Write flow (client)

On every mutation the client:

1. Reads the current item from local state (which reflects the Firestore SDK's cache — either confirmed server state or its own pending writes).
2. Writes the document with:
   - `fields` = the new values
   - `fingerprint` = fingerprint of the new `fields`
   - `clientId` = this client's ID
   - `baseFields` = the current `fields` (before the edit)
   - `baseFingerprint` = the current `fingerprint` value (before the edit)
   - // not needed probably `baseClientId` = the current `clientId` value (before the edit)

For `AddItem`: no `baseFields` or `baseFingerprint` (new document, no prior state). No conflict detection is performed for `DeleteItem`.

## Conflict detection (Cloud Function onUpdate trigger)

The trigger fires on every update to an item document. It receives `change.before` and `change.after`.

```
if change.after.baseFingerprint != change.before.fingerprint:
    // Conflict: the writer was editing from stale state.
    // The writer's changes still stand (last-write-wins),
    // but we notify both the 'losing' writer and the 'winning' 
	// writer.
    write conflict notification for change.before.clientId
    write conflict notification for change.after.clientId
```

The Cloud Function performs a single string comparison. It never computes a fingerprint.

### Information available to the trigger

| Field | Source | Meaning |
|---|---|---|
| `change.before.fields` | Previous document state | What was actually there before this write |
| `change.before.fingerprint` | Previous document state | Fingerprint of the previous state (compared against `baseFingerprint`) |
| `change.before.baseFields` | Previous document state | What was there before the *previous* write |
| `change.before.clientId` | Previous document state | Who made the previous write |
| `change.after.fields` | New document state | What the current writer changed it to |
| `change.after.fingerprint` | New document state | Fingerprint of the new state |
| `change.after.baseFields` | New document state | What the writer saw before editing (same as their local state) |
| `change.after.baseFingerprint` | New document state | Fingerprint the writer expected to be current — compared against `change.before.fingerprint` |
| `change.after.clientId` | New document state | Who made this write |

This is enough to construct any notification, e.g.: "You changed [item] to [new state], but your edit was based on stale state. The item had been changed to [change.before.fields] by another user."

## Conflict policy (lives entirely in the trigger)

The trigger can define "conflict" as loosely or strictly as desired:

- **Strict:** any fingerprint mismatch is a conflict.
- **By field:** only notify if the name changed (quantity bumps or check/uncheck are low-stakes).
- **By timing:** only notify if two different clients edited within N seconds.
- **By operation type:** never notify for check/uncheck (trivial actions).

The policy can be tuned after launch without a client update. The client just writes documents and listens for notifications.

## Conflict notifications

Same structure as the handoff doc:

```
lists/{listId}/notifications/{clientId}/pending/{notificationId}
{
  itemName: string,
  description: string,
  timestamp: serverTimestamp
}
```

The client listens to its own `pending` subcollection. On receiving a notification, it surfaces a modal alert and deletes the document on dismiss.

## Fingerprint computation

Client-side only. The Cloud Function never computes fingerprints — it only compares the `baseFingerprint` and `fingerprint` strings.

Canonical serialization via kotlix.serialize of `fields` as a deterministic JSON string (keys sorted alphabetically), then SHA-256 hash:

```json
{"checked":false,"name":"Bulk, Lemon drops","quantity":3.0}
```

## Conflict notification listener

TODO: This is out of date.

### Location

The listener lives in `FirestoreShoppingRepository`. It is a second snapshot listener, separate from the items listener, attached to `lists/{listId}/notifications/{clientId}/pending`.

### Flow of events

```
Firestore notification subcollection snapshot
    → for each new document: map to ConflictEvent
    → emit into a SharedFlow<ConflictEvent>
    → ViewModel collects and surfaces modal alert
    → on user dismiss: repository deletes notification document
```

### `ConflictEvent` data class

> REVIEW this should be 1:1 with the conflict document, so once we have that nailed down we'll need to modify these types.

```kotlin
data class ConflictEvent(
    val notificationId: String,
    val itemId: String,
    val name: String,
    val conflictType: ConflictType,
    val winningFields: ItemFields?,   // null when conflictType is Deleted
)

enum class ConflictType { Deleted, Edited }
```

### Repository interface addition

```kotlin
fun observeConflicts(): Flow<ConflictEvent>
suspend fun dismissConflict(notificationId: String)
```

`dismissConflict` deletes the notification document from Firestore. The ViewModel calls it after the user dismisses the alert.

`FakeShoppingRepository` implements `observeConflicts()` as an empty flow and `dismissConflict()` as a no-op. Existing tests are unaffected.

### ViewModel integration

The ViewModel collects `observeConflicts()` in `viewModelScope` and exposes a `SharedFlow<ConflictEvent>` (or a `StateFlow<ConflictEvent?>`) to the UI. The Compose UI observes this and shows a modal alert. On dismiss, the UI calls a ViewModel method which calls `dismissConflict`.

Additionally, on conflict, the ViewModel discards all undo and redo stack entries referencing the conflicted item ID. This requires a new method on `UndoRedoManager`:

```kotlin
fun discardItemFromStacks(itemId: String)
```

---

## What this replaces

This design replaces an abandoned plan of routing all mutations through a callable Cloud Function with preventive conflict detection and other complexity. Firestore's SDK handles offline persistence, write ordering, retry, and local cache updates natively.

## Impact on existing architecture

- **Repository `apply(command)`** writes directly to Firestore instead of calling a Cloud Function.
- **`baseFingerprint`** replaces `version` in the data model. `ShoppingItem.version` is removed.
- **`previousFields`** and `clientId` are new document fields, written on every mutation.
- **Cloud Function** changes from a callable to an `onUpdate` trigger. Simpler, fewer moving parts.
- **Undo/redo** is unaffected. Commands still carry snapshots. The undo of an edit writes to Firestore like any other mutation, carrying its own `baseFingerprint`. If the undo is stale, the trigger detects it.
- **Conflict notification listener** is unchanged — client listens to its per-client subcollection.
