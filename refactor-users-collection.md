# Firestore Schema Redesign: User-Namespaced Collection Hierarchy

## Problem Being Solved

We have decided to shift from having a top-level lists collection to
having a top-level users collection with each user's lists as a
subcollection.

## New Data Model

```
users/{ownerUid}/lists/{listId}                     — list document (name, editors map)
users/{ownerUid}/lists/{listId}/items/{itemId}      — items
```

## Security Rules

In @firebase/firestore.rules

Owner authorization uses path-derived identity (no `get()`).

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

`isEditor()` still calls `get()` on the list document

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

### PERMISSION_DENIED retry logic
The `retryWhen` logic in both `observeItems` and
`observeRemotelyModifiedItemIds` can be removed, as the retry loop was
a side-effect of placing all lists at the top-level.

## Editor List Discovery

User B finds lists where they are an editor via a collection group query:

```kotlin
firestore.collectionGroup("lists")
    .whereArrayContains("editorUids", user.uid)
    .addSnapshotListener { ... }
```

The index for this query will be statically declared in `firestore.indexes.json`
(`collectionGroup: "lists"`, field `editorUids`, `arrayConfig: CONTAINS`) and is
Firestore-managed — no application code maintains it.

This requires denormalizing editor UIDs into a top-level `editorUids: List<String>` array
field on the list document, alongside the existing `editors` map (which stores `{uid:
{email}}` for display). `addEditor` and `removeEditor` write both fields; they are already
transactional so this is a contained change. The `whereArrayContains` query replaces the
current `whereNotEqualTo("editors.${user.uid}", null)` — the latter uses a dynamic field
path that cannot be covered by a statically-defined collection group index.

firestore.rules will need changes to reflect the new structure, but
should express the same conceptual restrictions.

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

## Tests

Let's get the code restructuring and firestore.rules changes working
first before tackling revising the tests.

### ShoppingViewModel

I can imagine that setup for unit tests in
app/src/test/java/org/righteffort/ourgrocerylist/ui/ShoppingViewModelTest.kt
will need some adjustments.

I expect that setup and conceivably some details of interaction with
ShoppingViewModel in the integration tests in
app/src/test/java/org/righteffort/ourgrocerylist/*IntegrationTest.kt
will need changes.

### Firestore Rules

In firebase/rules-tests/test/firestore.spec.ts the text tables that
drive the majority of the tests should be largely changed, but setup
will need to change.

## How to work

* Form an initial multi-step plan that proceeds through detailed
  design, multiple implementation steps, following my tests. As you
  work you may need to flex the plan.
* As we embark on each step, ask clarifying questions if any.
* Obey CLAUDE.md thoroughly. If you see contradictions or unclear
  instructions there, discuss.
* Leave code you touch at least as clean as you found it.
* Use ourgrocerylist-handoff.md for general reference,
  though it reflects the existing schema, not the new schema.
* When we are all done update ourgrocerylist-handoff.md
* Verification: once we are done all of these test invocation should pass
  * ./gradlew testDebugUnitTest
  * ./gradlew testDebugUnitTest --rerun -PrunIntegration
  * (cd firebase/rules-tests && yarn test)
* If you notice improvements that are out of scope for the goal of
  this refactor, collect them in a new file,
  refactor-users-collection-future-work.md

## Progress

- [x] **Step 1 — `ListRepository` interface + `FakeListRepository`**
  - [x] `renameList/deleteList/addEditor` accept `ListMetadata` instead of bare `listId`
  - [x] `FakeListRepository.createList` populates `ownerUid = owner.uid`
  - [x] `ShoppingViewModelTest` `ListMetadata` call sites updated with `ownerUid`; `OTHER_UID` constant added
- [x] **Step 2 — `FirestoreListRepository`**
  - [x] `observeLists()`: owned query → `collection("users/$uid/lists")`
  - [x] `observeLists()`: editor query → `collectionGroup("lists").whereArrayContains("editorUids", uid)`
  - [x] `toListMetadata()`: populate `ownerUid` from document reference path
  - [x] `createList()`: write to `users/{uid}/lists`
  - [x] `renameList/deleteList/addEditor`: accept `ListMetadata`, use `users/{ownerUid}/lists/{listId}`
  - [x] `addEditor`: write `editorUids` array alongside `editors` map
- [x] **Step 3 — `FirestoreShoppingRepository`**
  - [x] Add `ownerUid: String` constructor param
  - [x] Collection path: `users/$ownerUid/lists/$listId/items`
  - [x] Remove both `retryWhen` blocks
- [x] **Step 4 — `ShoppingViewModel`**
  - [x] `repositoryFactory` signature: `(ownerUid: String, listId: String) -> ShoppingRepository`
  - [x] `getOrCreateResources`: accept `ListMetadata`; `checkNotNull(listResources[listId])` for call sites where resources must already exist
  - [x] `addList`: construct `ListMetadata` with `ownerUid = user.uid`
  - [x] `renameCurrentList/deleteCurrentList/shareList`: use new `currentList()` helper on `ListSelectionState`
  - [x] `ShoppingViewModelTest` `repositoryFactory` lambda updated to `{ _, _ -> FakeShoppingRepository() }`
  - [x] Verify project compiles
- [x] **Step 5 — App wiring** (`OurGroceryListApp` + `IntegrationTestFixtures`)
  - [x] `repositoryFactory` lambda accepts and forwards `ownerUid` (`MainActivity.kt` + `IntegrationTestFixtures.kt`)
  - [x] Verify project compiles
  - [x] Run `./gradlew testDebugUnitTest`
- [x] **Step 6 — `firestore.rules`**
  - [x] Rewrite for `users/{ownerId}/lists/{listId}` hierarchy
  - [x] Owner auth via path-derived identity (no `get()`)
  - [x] `isEditor()` uses `get()` on list document, checks `editorUids` array
  - [x] Preserve `notifications` subcollection
- [x] **Step 7 — `firestore.indexes.json`**
  - [x] Add collectionGroup index: `lists` / `editorUids` / CONTAINS
- [x] **Step 8 — Firebase Functions** (`index.ts`)
  - [x] Update `cleanUpDeletedList` trigger path to `users/{ownerId}/lists/{listId}`
- [x] **Step 9 — Test updates**
  - [x] `firestore.spec.ts`: rewrite paths, seed data, and list-query tests
  - [x] `ShoppingViewModelTest`: update `repositoryFactory` lambda signature
  - [x] Integration test fixtures: verify no remaining old-path assumptions
  - [x] Run `./gradlew testDebugUnitTest`
  - [x] Run `./gradlew testDebugUnitTest --rerun -PrunIntegration`
  - [x] Run `(cd firebase/rules-tests && yarn test)`
- [x] **Step 10 — Wrap-up**
  - [x] Update `ourgrocerylist-handoff.md` to reflect new schema
