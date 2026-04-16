See also done.md

## Accent color

`#8775B8` — muted medium purple. Used for: toolbar background, filled checkboxes, active undo/redo buttons, text field focus border, save button background.

## UX — list view

**Top bar:** List name pill with dropdown chevron (future list switching). Three-dot overflow menu (⋯) on the right — home for import/export, settings, and other infrequently accessed actions. Top bar otherwise empty in v0.

**Add item field:** Persistent text field immediately below the top bar. Idle state shows placeholder "Add item." When active (user taps the field), it shows the text being typed plus three icons: pencil (open edit dialog for the new item), × (cancel and clear), ✓ (commit — adds the item and clears the field).

**List structure:**
- Unchecked items first, alphabetized case-insensitively as entered by the user. Numbers sort before letters. No semantic reinterpretation of names (e.g. "Bulk, Lemon drops" is a user convention, not a category).
- A short accent-color divider bar separates unchecked from checked items.
- Checked items below the divider, also alphabetized, with strikethrough text.
- Alternating row background (primary/secondary surface) for readability.
- Quantity shown inline right-aligned as "×N" when quantity ≠ 1; nothing shown when quantity is 1.
- Tapping any item row opens the edit dialog.

**Bottom bar:** Persistent, minimal height. Contains undo and redo icon+label buttons, left-aligned. Each button uses the accent color when its action is available; grayed out otherwise. This is a permanent bar, not a snackbar.

---

## UX — edit dialog

Opened by tapping any item row (edit mode), or via the pencil icon in the add-item field (add mode).

**Two modes (ViewModel concern, not dialog concern):**
- **Edit mode** (tapped existing row): Dialog title "Edit item." Save issues an `EditItem` command. Delete button and stepper-to-zero deletion are available.
- **Add mode** (pencil icon from add field): Dialog title "Add item." The item does not exist yet. Save issues an `AddItem` command with the name and quantity from the dialog fields. Cancel discards entirely. Delete button is hidden (there is nothing to delete).

The dialog composable itself has no concept of mode. The ViewModel constructs an `ItemDialogState` with title, initial field values, `showDelete` flag, and callback lambdas (`onSave`, `onDelete`, `onCancel`). The composable renders fields, wires buttons to lambdas, and conditionally shows delete based on the flag. All add-vs-edit branching lives in the ViewModel's construction of `ItemDialogState`, not in the dialog.

**Fields:**
- **Name** — text input, focused on open.
- **Quantity** — integer stepper (− and + buttons flanking an editable text field). Step size is ±1. Accepts any number > 0, not necessarily integer. Renders as integer if whole number; otherwise up to three decimal places, no trailing zeros. Tapping − when quantity is 1 triggers deletion when delete is available (edit mode); − is disabled at 1 when delete is not available (add mode). The dialog derives this from the presence of the `onDelete` callback — no mode check.

**Delete item** — explicit button with trash icon, in red, inside the dialog.

**Cancel / Save** — at the bottom. Item's position in the list only updates on Save (name edits do not cause live re-sort while typing).

**Deletion flow (edit mode only, both paths):** Dialog dismisses.

## UX — undo/redo

Covers all mutations: add, delete, edit (name, quantity), check, uncheck.

Undo and redo stacks are persisted across process death using
`kotlinx.serialization` to JSON in Preferences DataStore. Undo is
per-client and scoped to that client's own mutations.

Undo and redo are not special from the server's point of view — they
are just mutations like any other, written directly to Firestore
carrying their own `baseFingerprint`. You can think of them as
client-side conveniences for performing mutations that the end-user
could perform manually.

**Undo/redo stack pruning on remote writes (v0):** When the client
receives a remote write (a snapshot listener update from another
client) that modifies an item, the stacks are pruned by truncation,
not selective removal. For each stack (undo and redo independently):
scan from newest entry toward oldest. The first entry referencing the
modified item (by ID) and everything older than it are
discarded. Entries newer than the conflicted entry are preserved.

This maintains two properties: (1) the user's most recent actions
remain undoable — pressing undo still does what they expect, and (2)
the stack never has gaps where a missing entry's undo would have set
up state that a later entry depends on. If the user undoes far enough
to reach the truncation point, the stack is simply empty and undo
grays out.

This is conservative — it discards entries that might still be safe
(e.g. a name undo is still valid if the remote write only changed
`checked`) — but it is simple and predictable.

## Resolved from architecture review (all but conflict notifications implemented).

- **Direct Firestore writes** with after-the-fact conflict detection via `onUpdate` trigger. No callable Cloud Functions. See `conflict-detection-design.md`.
- **Version field removed** from `ShoppingItem`. Conflict detection uses client-computed fingerprints stored on the Firestore document, not in the app model.
- **Edit dialog has two modes**: add (from pencil icon, issues `AddItem`) and edit (from row tap, issues `EditItem`).
- **Conflict notifications** delivered via per-client Firestore subcollection, not FCM.
- **Collection structure** uses subcollections: `lists/{listId}/items/{itemId}`.
- **Stepper step size** is ±1.
- **ItemFields composition** separates user-editable fields (name, quantity, checked) from system/identity fields (id) in the model. `EditItem` command takes `newFields: ItemFields`, not individual field parameters. Conflict metadata lives only on the Firestore document.
- **Edit dialog is mode-free** — the composable renders `ItemDialogState` with no add-vs-edit branching. The ViewModel constructs the appropriate state.
- Optimistic updates not needed. Firestore SDK updates its local cache synchronously on write before network confirmation, so the Flow updates fast enough that a separate optimistic layer in the ViewModel adds complexity with no benefit.

