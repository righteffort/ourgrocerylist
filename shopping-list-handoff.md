# Android multi-user shopping list app — design & architecture handoff

This document captures all design and architecture decisions made so far for, to be continued and finalized in a new conversation. I've attach a screenshot containing mockups for the list view and the edit dialog.

Please put the rendered markdown of doc in the sidebar so we can revise it as needed.

---

## Design goals

- As simple as possible — minimize features, configurability, and code surface area.
- Readable, human-understandable, self-documenting code.
- Well-delineated components with clear responsibilities and collaborators.
- Amenable to unit testing with minimal reliance on clunky mocks.

---

## What the app is

A native Android shopping list app in Kotlin. Multiple named lists, each containing named items with optional quantity. Users check items off as they shop. Designed for household use, with real-time multi-user collaboration via Firestore as the target state.

There is no offline-only mode. The app always runs against Firestore — pointed at the Firebase local emulator during development and testing, and at the real cloud in production. The same code runs in both environments; the only difference is injected configuration. Fakes are used in unit tests only.

---

## V0 scope

- Single list, hardcoded name: **"List"** (not "WinCo").
- No authentication, no cloud — Firebase local emulator (Firestore + Cloud Functions) only.
- The architecture must support adding multi-user and cloud collaboration later without structural rework.

---

## Accent color

`#8775B8` — muted medium purple. Used for: toolbar background, filled checkboxes, active undo/redo buttons, text field focus border, save button background.

---

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

## UX — animations

**Check:** Checkbox fills with accent color and checkmark. Once animation completes, row collapses and item cross-fades into its alphabetical position in the checked section. Viewport stays anchored to the unchecked section — does not follow the item.

**Uncheck:** Checkbox drains (checkmark disappears, border goes gray). Row collapses and item appears in the unchecked section. Viewport stays in the checked section.

**Delete:** Row flushes light red, then slides horizontally off the left edge while collapsing vertically. A trashcan icon materializes at the bottom of the screen, appears to receive the deleted item (lid opens and closes), then fades out. No permanent trash list.

Remote mutations (from another user) trigger the same animations as local ones, minus user-initiated affordances (e.g. no trashcan arc for remote deletes — just the red flush and slide).

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

**Deletion flow (edit mode only, both paths):** Dialog dismisses, then delete animation plays in the list view. Both paths — explicit delete button and stepper reaching zero — are identical from the animation's perspective.

---

## UX — undo/redo

Covers all mutations: add, delete, edit (name, quantity), check, uncheck.

Undo and redo stacks are persisted across process death using `kotlinx.serialization` to JSON in Preferences DataStore. Undo is per-client and scoped to that client's own mutations.

Undo and redo are not special from the server's point of view — they are just mutations like any other, routed through the Cloud Function with version checking. You can think of them as client-side conveniences for performing mutations that the end-user could perform manually.

In the very unlikely event that a conflict occurs when applying an undo or redo, the user is notified (see conflict UX below) and all entries in both the undo and redo stacks referencing that item (by ID) are discarded. This avoids leaving the stack in a logically inconsistent state where later entries depend on the state of a conflicted item.

---

## UX — real-time collaboration

The Firestore snapshot listener fires on every client whenever any client mutates any item document. Remote mutations animate the same way as local ones.

**Conflict UX (two users edit the same item simultaneously):** Last write wins on the server. The Cloud Function writes a conflict notification document to the losing client's per-client notifications subcollection (see Firestore data model). The client's listener picks it up and surfaces a modal alert with a single dismiss button. Alert text: "Your changes to [item name] were discarded. [Description of what the other user did — e.g. 'Another user deleted it' or 'Another user changed it to: name, quantity, checked state']." The client deletes the notification document on dismiss. No merge UI, no recovery assistance.

This conflict case is expected to be extremely rare in practice.

---

## CSV import/export

Accessible via the three-dot overflow menu.

**Headers (in order):** `name`, `checked`, `quantity`

**Export:** Always writes all three columns. Checked renders as TRUE/FALSE.

**Import (v0):** Replaces the existing list entirely (v0 has only one list, so import is a "load list from file" operation). Requires a confirmation dialog: "This will replace your current list. Continue?" Checked defaults to FALSE if absent. Quantity defaults to 1 if absent.

Import/export are for bootstrapping and backup. List sharing with collaborators is a separate feature (future, via the share icon).

---

## Tech stack

- Kotlin, Jetpack Compose (single Activity, no Fragments), MVVM
- Firestore Android SDK — offline cache provides local operation; no Room
- Jetpack Preferences DataStore + `kotlinx.serialization` (JSON) — undo/redo stack persistence only
- Firebase local emulator (Firestore + Cloud Functions) for development
- No third-party DI framework in v0 — manual injection via Application class singleton

---

## Architecture

### Core principle: Command pattern

Every user mutation — add, delete, edit, check, uncheck — is reified as a `Command` object carrying enough data to execute and reverse itself. The undo stack is a list of Commands. Commands are serializable to JSON via `kotlinx.serialization` for DataStore persistence.

**Command variants (sealed class):** `AddItem`, `DeleteItem`, `EditItem`, `CheckItem`, `UncheckItem`. Each carries the full snapshot data needed for reversal (e.g. `DeleteItem` carries the complete item so undo is a straightforward re-add).

---

### Layer 1 — Model

Pure Kotlin data classes. `ShoppingItem(id, name, quantity, checked, version)`. The `version` field is a monotonic integer incremented by the Cloud Function on every successful write. Zero Android or Firestore dependencies. Trivially testable, no mocks needed.

---

### Layer 2 — Repository interface + Firestore implementation

**Interface exposes:**
- `observeItems(): Flow<List<ShoppingItem>>`
- `apply(command: Command)`

Firestore types appear only in the implementation, never in the interface. Tests use a fake in-memory implementation (`MutableStateFlow<List<ShoppingItem>>`). No Mockito, no emulator required in unit tests.

**Conflict handling:** All mutations — add, delete, edit, check, uncheck, and their undo/redo counterparts — route through a Cloud Function rather than writing directly to Firestore from the client. The Cloud Function runs within a Firestore transaction: it reads the current document version, compares it to the expected version sent by the client, writes and increments the version on match, or rejects and sends a conflict notification on mismatch. Undo and redo are not special — they are just mutations carrying the expected version like any other. The repository interface abstracts this — callers simply call `apply(command)`.

---

### Layer 3 — UndoRedoManager

Single responsibility: manages undo and redo stacks, coordinates with Preferences DataStore for persistence.

**Exposes:** `execute(command)`, `undo()`, `redo()`, `StateFlow<UndoRedoState>` (whether each action is currently available).

Calls through to the repository. No UI knowledge. Testable with fake repository.

---

### Layer 4 — ViewModel

Translates user intents into Commands, hands them to UndoRedoManager. Collects `Flow<List<ShoppingItem>>` from repository, applies sorting and checked/unchecked split, merges with undo/redo availability into a single `UiState` value that Compose renders. Also surfaces one-shot conflict notification events received from the client's Firestore notifications subcollection listener.

No Firestore knowledge. No Compose knowledge.

---

### Layer 5 — Compose UI

Pure function of ViewModel `UiState`. Emits user intent callbacks upward. No logic, no decisions. Not unit tested.

---

### Collaborator graph (linear, no skips)

```
UI → ViewModel → UndoRedoManager → Repository interface
                                          ↕
UndoRedoManager ↔ DataStore     Firestore implementation
```

No layer skips. No circular dependencies. No shared mutable state outside the repository.

---

### Optimistic updates

Not needed. Firestore SDK updates its local cache synchronously on write before network confirmation, so the Flow updates fast enough that a separate optimistic layer in the ViewModel adds complexity with no benefit.

---

## Firestore data model

Each list item is its own Firestore document. This gives independent write paths per item — two users editing different items simultaneously never interact. This is the foundation for the "independent item updates interleave automatically" requirement.

**Collection structure:** `lists/{listId}/items/{itemId}` — items are a subcollection under the list document. This supports multi-list and per-list security rules without migration when those features are added.

**Item document fields:** `name` (string), `quantity` (number), `checked` (boolean), `version` (integer, incremented by the Cloud Function on every successful write).

**Conflict notifications:** `lists/{listId}/notifications/{clientId}/pending/{notificationId}` — per-client subcollection. Each document contains the conflict description (what the other user did, the item name involved). The client listens to its own subcollection, surfaces the alert, then deletes the document after the user dismisses it.

---

## Resolved from architecture review

- **Version field** added to `ShoppingItem` — monotonic integer, incremented server-side in a Firestore transaction by the Cloud Function.
- **All mutations route through the Cloud Function**, including undo/redo. No separate write paths.
- **Edit dialog has two modes**: add (from pencil icon, issues `AddItem`) and edit (from row tap, issues `EditItem`).
- **Conflict notifications** delivered via per-client Firestore subcollection, not FCM.
- **Collection structure** uses subcollections: `lists/{listId}/items/{itemId}`.
- **Stepper step size** is ±1.
- **Mockups** are aspirational (show "WinCo" and share icon); v0 uses hardcoded "List" and no share icon.
