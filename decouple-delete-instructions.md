# Decouple list deletion from subcollection cleanup

First, re-read CLAUDE.md.

## Goal

Replace the callable `deleteList` Cloud Function with a direct client-side
Firestore delete + an `onDocumentDeleted` trigger for subcollection cleanup.

Benefits:
- The Firestore SDK updates its local cache immediately on a direct client
  write, so `observeLists()` fires without delay. The list disappears from the
  UI picker the moment the user confirms deletion, with no extra coordination
  needed in the ViewModel.
- Disconnected operation works naturally: the delete is queued offline and
  flushed on reconnection. Do NOT await the delete — awaiting a Firestore write
  Task blocks indefinitely while offline (the Task only completes on server
  acknowledgment). Fire-and-forget is correct here; the snapshot listener is
  the source of truth for resulting state.
- Ownership is already enforced by `firebase/firestore.rules` (`allow
  delete: if isAllowedUser() && isOwner()`), so no server-side
  ownership check is needed and no changes to that file are required.

## Changes

### `firebase/functions/src/index.ts`

Remove the `deleteList` callable export and its imports of `firebaseTools` and
`deleteListCore`. Add an `onDocumentDeleted` trigger:

```typescript
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";

export const cleanUpDeletedList = onDocumentDeleted(
  { document: "lists/{listId}", region: "us-west1" },
  async (event) => {
    console.log(`cleanUpDeletedList(${event.params.listId})`);
    await getFirestore().recursiveDelete(event.data!.ref);
  }
);
```

`event.data.ref` is a `DocumentReference` to the already-deleted list
document.  The Firebase Admin SDK's implementation of
`recursiveDelete` finds descendants via a path-pattern query, not by
traversing from the parent, so it correctly deletes the `items`
subcollection (and any other subcollections) regardless of whether the
parent document exists.  The redundant delete of the already-absent
document is a no-op.

API reference: https://googleapis.dev/nodejs/firestore/latest/Firestore.html#recursiveDelete

### `firebase/functions/src/deleteListCore.ts` — delete this file

### `firebase/functions/src/deleteListCore.test.ts` — delete this file

### `firebase/functions/src/firebase-tools.d.ts` — delete this file

### `firebase/functions/package.json`

Remove `firebase-tools` from `dependencies`.

### `app/.../repository/FirestoreListRepository.kt`

Remove the `callDeleteList` constructor parameter and its default stub.

Change `deleteList` to delete the document directly, without awaiting:

```kotlin
override suspend fun deleteList(listId: String) {
    firestore.document("lists/$listId").delete()
}
```

Note: the function signature is `suspend` because the interface requires it, but
no suspension actually occurs.

### `app/.../OurGroceryListApp.kt`

Remove the injection of the `callDeleteList` callable when constructing
`FirestoreListRepository`.

## Verification

- Confirm `firebase-tools` is no longer imported anywhere in functions source
- Confirm unit tests compile: `./gradlew compileDebugUnitTestSources`
- Human runs existing unit tests and manual visual tests of offline
  and online list delete

