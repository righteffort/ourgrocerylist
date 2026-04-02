## Multi-list feature — spec and design decisions

### Spec

1. The overflow menu has "Add list", "Rename list", "Share list", "Delete list". The latter 3 items are only shown if the user owns the current list.

2. Delete list shows a confirmation dialog: "Are you sure? The list and all its items will be deleted immediately and permanently."

3. The default list created on first use is named "Groceries".

4. Undo/redo is per-list.

---

### Design decisions

**List navigation:** List name shown as a pill with a `▾` chevron in the top bar. Tapping it opens a dropdown of all accessible lists. This is the primary list-switching UI.

**List ordering in the dropdown:** Owned lists sorted alphabetically first, then editor lists sorted alphabetically. On startup (or when the current list is removed), the app selects the first owned list alphabetically; if the user owns none, the first list alphabetically.

**Zero-list recovery:** If `observeLists()` emits an empty set (new user, or user deleted all their lists), the ViewModel automatically recreates a "Groceries" list.

**`ListRepository` (new, global interface):** Owns all list-level operations: `observeLists()`, `createList()`, `renameList()`, `deleteList()`. Source of truth is Firestore; discovery is via direct Firestore queries (no separate index document). `FirestoreListRepository` runs two parallel queries — `whereEqualTo("owner.uid", uid)` for owned lists, `whereNotEqualTo("editors.$uid", null)` for editor lists — merged and deduplicated client-side. Firestore evaluates security rules per result document, so both queries are safe with existing rules.

**`ShoppingRepository` stays per-list and item-scoped.** `ensureListDocument` and `_ready` removed: list document existence is now guaranteed by `ListRepository.createList()` before any `ShoppingRepository` is instantiated for that list. No readiness gate needed.

**ViewModel owns per-list resource lifecycle.** `ShoppingViewModel` maintains a `listResources` map from `listId` to a `(ShoppingRepository, UndoRedoManager, observationJob)` triple. All accessible lists' resources are kept alive simultaneously so that remote-change listeners and undo/redo stacks persist across list switches and for all lists (not just the current one).

**Auth timing.** `OurGroceryListApp._currentUser: MutableStateFlow<User?>` starts null, set by `MainActivity` after auth succeeds. `FirestoreListRepository.observeLists()` uses `flatMapLatest` on this flow, so list observation is gated on auth completion. The ViewModel is constructed before auth finishes but emits nothing until `_currentUser` is set.

**`SharingRepository.addEditor` signature change.** Changed from `addEditor(email)` to `addEditor(listId, email)`. Makes it a stateless singleton — no per-list factory needed.

**List-level mutations are not undoable.** The Command sealed class stays scoped to item operations (add, edit, delete, check, uncheck). Add list, rename list, delete list, and share list do not participate in undo/redo.

**`deleteList` cloud function.** Uses the `firebase-tools` npm package (`firestore.delete(..., { recursive: true })`), which recursively deletes the list document and all subcollections (`items/`, `notifications/`). The function verifies auth and ownership before deleting. Type declarations for `firebase-tools` live in `src/firebase-tools.d.ts`.

**`UiState` additions.** Added `currentListName: String`, `lists: List<ListMetadata>`, `isOwner: Boolean`. The Compose UI uses these to render the list pill, dropdown, and conditional overflow menu items.

**`ListMetadata` model.** `data class ListMetadata(id: String, name: String, isOwner: Boolean)`. Ownership is never assumed to change (the owner field is set at list creation and not updated by the app).
