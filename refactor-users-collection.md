# Option 3 Redesign: User-Namespaced Collection Hierarchy

## Problem Being Solved

The current data model (`lists/{listId}/items`) causes a PERMISSION_DENIED race on list
creation. The items collection security rules call `get()` on the parent list document to
verify ownership. Firestore's `addSnapshotListener` reaches the server before the list
document write commits, so rules evaluation fails transiently.

Restructuring to encode `ownerUid` in the path eliminates the need for `get()` in the
ownership check. The race is an owner-creating-new-list problem only; editors are added
after the list exists and are unaffected.

## New Data Model

```
users/{ownerUid}/lists/{listId}                     — list document (name, editors map)
users/{ownerUid}/lists/{listId}/items/{itemId}      — items
```

## Security Rules

Owner authorization uses path-derived identity (no `get()`):

```
match /users/{ownerId}/lists/{listId} {
  allow read, write: if request.auth.uid == ownerId
    || isEditor(request.auth.uid, ownerId, listId);
}
match /users/{ownerId}/lists/{listId}/items/{itemId} {
  allow read, write: if request.auth.uid == ownerId
    || isEditor(request.auth.uid, ownerId, listId);
}
```

`isEditor()` still calls `get()` on the list document, but this is fine: editors are only
added after the list is confirmed server-side, so the list document always exists by then.

## Components That Change

### `ListMetadata` model
Add `ownerUid: String`. Everything that constructs the items collection path needs it.

### `FirestoreListRepository`
- **`observeLists()`**: owned-lists query changes from
  `collection("lists").whereEqualTo("owner.uid", uid)` to `collection("users/$uid/lists")`.
  Editor list discovery — see Editor List Discovery section below.
- **`createList()`**: writes to `users/{uid}/lists` instead of `lists/`.
- **`deleteList()`, `renameList()`, `addEditor()`**: all currently take only `listId` but
  now need `ownerUid` to locate the document. Options: add `ownerUid` parameter to each
  method, or change the `ListRepository` interface to accept `ListMetadata` (or an
  `(ownerUid, listId)` pair) instead of a bare `listId`.

### `FirestoreShoppingRepository`
- `collection` path changes from `lists/{listId}/items` to
  `users/{ownerUid}/lists/{listId}/items`.
- Constructor takes `ownerUid` in addition to `listId`.

### `ShoppingViewModel`
- `repositoryFactory` lambda signature gains `ownerUid`:
  `(ownerUid: String, listId: String) -> ShoppingRepository`.
- `getOrCreateResources()` must source `ownerUid` from `ListMetadata` (now available in
  `_lists`). The `listResources` map key can remain `listId`.
- All calls to `listRepository.deleteList()`, `renameList()`, `addEditor()` must supply
  `ownerUid` from the current `ListMetadata`.

### PERMISSION_DENIED retry logic (can be reverted)
`retryWhen` in both `observeItems` and `observeRemotelyModifiedItemIds`, and the
`logAndEmitError` change in `getOrCreateResources`, are no longer needed and should be
removed.

## Editor List Discovery

User B finds lists where they are an editor via a collection group query:

```kotlin
firestore.collectionGroup("lists")
    .whereArrayContains("editorUids", user.uid)
    .addSnapshotListener { ... }
```

The index for this query is statically declarable in `firestore.indexes.json`
(`collectionGroup: "lists"`, field `editorUids`, `arrayConfig: CONTAINS`) and is
Firestore-managed — no application code maintains it.

This requires denormalizing editor UIDs into a top-level `editorUids: List<String>` array
field on the list document, alongside the existing `editors` map (which stores `{uid:
{email}}` for display). `addEditor` and `removeEditor` write both fields; they are already
transactional so this is a contained change. The `whereArrayContains` query replaces the
current `whereNotEqualTo("editors.${user.uid}", null)` — the latter uses a dynamic field
path that cannot be covered by a statically-defined collection group index.

## What Stays the Same

- `ShoppingRepository` interface — only the impl's collection path changes
- `ListRepository` interface — minor signature changes at most
- `UndoRedoManager` — no changes
- `Command` sealed class — no changes
- ViewModel logic for undo/redo, item operations, list switching — no changes
- Firebase Functions (`emailToUid`, `deleteList`) — need internal path updates but not
  interface changes

## Migration

No migration necessary, app is still under development.
