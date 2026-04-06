# Remove unnecessary awaits on Firestore mutations

## Background

The Firestore SDK updates its local cache immediately on any client-side write,
notifying active snapshot listeners before the server round-trip completes.
Awaiting a Firestore write Task therefore blocks the coroutine until server
acknowledgment — which can be indefinitely long while offline — for no benefit
to the app. Fire-and-forget is the correct pattern for mutations where the
return value is not needed and the snapshot listener is the source of truth for
resulting state.

The one exception is `createList`, where the await is load-bearing: the ID must
be returned to the caller, and the Race 1 fix depends on the server having
acknowledged the document before `observeLists` is trusted to confirm
visibility. Leave `createList` alone.

## Changes

### `app/.../repository/FirestoreListRepository.kt`

`renameList`: remove the await.

```kotlin
override suspend fun renameList(listId: String, name: String) {
    firestore.document("lists/$listId").update("name", name)
}
```

### `app/.../repository/FirestoreShoppingRepository.kt`

All item mutations (`AddItem`, `EditItem`, `DeleteItem`) go through `apply`.
Remove the await from each write in `apply`:

```kotlin
is Command.DeleteItem ->
    collection.document(command.item.id).delete()

// and whatever set/update calls handle AddItem and EditItem
```

Review the full `apply` implementation and remove `.await()` from every Firestore
write call within it.

## What does not change

- `createList` — await is load-bearing (see above).
- All `suspend` function signatures — they remain `suspend` for interface
  consistency and future-proofing, even where no suspension now occurs.
- Error handling — without await, server-side write failures are no longer
  catchable at the call site. This is acceptable: the existing pattern for
  unrecoverable errors (bubbling to the top level) applies, and transient
  failures will be retried by the SDK automatically.

## Verification

- Manually test rename and item add/edit/delete while online: changes appear
  immediately as before.
- Manually test while offline: changes appear immediately in the UI and are
  flushed to the server on reconnection.
