# Race Conditions in List Create and Delete

## Race 1: observe before list exists (import/add list)

### Problem

`addList()` and `importListFromCsv()` both set `_currentListId.value = listId` immediately after `createList().await()` returns. This triggers `observeItems()` via `uiState`'s `flatMapLatest`, which registers a Firestore snapshot listener on the `lists/$listId/items` subcollection.

That listener's security rules check calls:
```
let data = get(/databases/$(database)/documents/lists/$(listId)).data;
```

Even though `createList().await()` guarantees the write was server-acknowledged, there is an empirical window where Firestore's rules engine `get()` returns null for the just-written document. When this happens, `observeItems()` receives a permission error and closes the flow, surfacing as a fatal error dialog.

### Fix

Decouple navigation from `observeItems` startup. `_currentListId` is
set immediately after `createList()` returns so the UI switches to the
new list without delay. A separate `_confirmedListIds:
MutableStateFlow<Set<String>>` is populated by the `init` block's
`observeLists()` collector each time it fires. In `uiState`, the
`flatMapLatest` combines `_currentListId` with `_confirmedListIds`: if
the current list ID is not yet confirmed, it emits an empty-items
state and suspends via `awaitCancellation()`; when `_confirmedListIds`
updates to include the ID, `flatMapLatest` cancels the placeholder and
starts the real `observeItems` Firestore listener. This ensures the
Firestore rules engine always sees the list document before the items
listener is registered, while the UI reflects the new list
immediately.

---

## Race 2: deleted list persists in UI (delete list)

### Problem

`deleteCurrentList()` calls a cloud function via `listRepository.deleteList()` and awaits it. The cloud function completes and all Firestore documents are deleted. However, the `observeLists()` snapshot listener fires asynchronously — potentially hundreds of milliseconds later. Until it fires, `uiState.lists` still contains the deleted list and `_currentListId` still points to it.

### Fix

#### Correct fix

See decouple-delete-instructions.md

#### Abandoned fix

Maintain `_optimisticallyDeletedListIds: MutableStateFlow<Set<String>>`. After `deleteList()` returns:
1. Add `listId` to `_optimisticallyDeletedListIds`.
2. Immediately compute the next list from `uiState.value.lists` and update `_currentListId`.

`uiState` now includes `_optimisticallyDeletedListIds` as a third flow in its `combine`, filtering it from `lists` before rendering. The list picker therefore updates immediately, with no visible stale period.

When `observeLists()` eventually fires and confirms the deletion (the ID is absent from `newIds`), the `init` block cleans up the entry from `_optimisticallyDeletedListIds`.

---

## Files changed

- `app/src/main/java/org/righteffort/ourgrocerylist/ui/ShoppingViewModel.kt`
