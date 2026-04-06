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

Never set `_currentListId` (and thus start `observeItems`) until `observeLists()` has emitted with the new list ID present. This guarantees the list document is visible in Firestore before any listener registers against it.

A new `_pendingSelectListId: MutableStateFlow<String?>` tracks the intended destination. `addList()` and `importListFromCsv()` set it instead of `_currentListId`. The `init` block's `observeLists()` collector checks: when the pending ID appears in `newIds`, it clears `_pendingSelectListId` and sets `_currentListId` — then returns early to prevent the fallback logic from interfering.

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
