# Android multi-user shopping list app — design & architecture handoff

## Design goals

- As simple as possible — minimize features, configurability, and code surface area.
- Readable, human-understandable, self-documenting code.
- Well-delineated components with clear responsibilities and collaborators.
- Amenable to unit testing with minimal reliance on clunky mocks.

## What the app is

A native Android shopping list app in Kotlin. Multiple named lists,
each containing named items with optional quantity. Users check items
off as they shop. Designed for household use, with real-time
multi-user collaboration via Firestore as the target state.

There is no special case for offline operation. The app always runs
against Firestore — pointed at the Firebase local emulator during
development and testing, and at the real cloud in production, relying
on the local cache provided by the Firestore SDK when offline. The
same code runs in both environments; the only difference is injected
configuration. Fakes are used in unit tests only.

## Tech stack

- Kotlin, Jetpack Compose (single Activity, no Fragments), MVVM
- Firestore Android SDK — offline cache provides local operation; no Room
- Jetpack Preferences DataStore + `kotlinx.serialization` (JSON) — undo/redo stack persistence only
- Firebase local emulator (Firestore + Cloud Functions) for development
- No third-party DI framework in v0 — manual injection via Application class singleton

## Architecture

### Core principle: Command pattern

Every user mutation — add, delete, edit, check, uncheck — is reified as a `Command` object carrying enough data to execute and reverse itself. The undo stack is a list of Commands. Commands are serializable to JSON via `kotlinx.serialization` for DataStore persistence.

**Command variants (sealed class):** `AddItem`, `DeleteItem`, `EditItem`, `CheckItem`, `UncheckItem`. Each carries the full snapshot data needed for reversal (e.g. `DeleteItem` carries the complete item so undo is a straightforward re-add). `EditItem` carries `previousSnapshot: ShoppingItem` (for undo/version) and `newFields: ItemFields` (the user-editable state being applied).

### Layer 1 — Model

Pure Kotlin data classes with composition separating user-editable fields from system fields:

- `ItemFields(name, quantity, checked)` — all user-editable state.
- `ShoppingItem(id, fields: ItemFields)` — `id` is item identity. Conflict detection metadata (`fingerprint`, `baseFingerprint`, `baseFields`, `clientId`) lives only on the Firestore document, not in the app's data model. The repository maps them on write and strips them on read.

Zero Android or Firestore dependencies. Trivially testable, no mocks needed.

### Layer 2 — Repository interface + Firestore implementation

**Interface exposes:**
- `observeItems(): Flow<List<ShoppingItem>>`
- `apply(command: Command)`

Firestore types appear only in the implementation, never in the interface. Tests use a fake in-memory implementation (`MutableStateFlow<List<ShoppingItem>>`). No Mockito, no emulator required in unit tests.

**Conflict handling:** All mutations write directly to Firestore. The Firestore SDK handles offline persistence, write ordering, retry, and optimistic local cache updates. An `onUpdate` Cloud Function trigger detects conflicts after the fact by comparing the incoming `baseFingerprint` against the previous document's `fingerprint`. On mismatch, the trigger writes a conflict notification to the writing client's notifications subcollection. See `conflict-detection-design.md` for full details. The repository interface abstracts this — callers simply call `apply(command)`.

### Layer 3 — UndoRedoManager

Single responsibility: manages undo and redo stacks, coordinates with Preferences DataStore for persistence.

**Exposes:** `execute(command)`, `undo()`, `redo()`, `StateFlow<UndoRedoState>` (whether each action is currently available).

Calls through to the repository. No UI knowledge. Testable with fake repository.

### Layer 4 — ViewModel

Translates user intents into Commands, hands them to UndoRedoManager. Collects `Flow<List<ShoppingItem>>` from repository, applies sorting and checked/unchecked split, merges with undo/redo availability into a single `UiState` value that Compose renders. Also surfaces one-shot conflict notification events received from the client's Firestore notifications subcollection listener.

No Firestore knowledge. No Compose knowledge.

### Layer 5 — Compose UI

Pure function of ViewModel `UiState`. Emits user intent callbacks upward. No logic, no decisions. Not unit tested.

### Collaborator graph (linear, no skips)

```
UI → ViewModel → UndoRedoManager → Repository interface
                                          ↕
UndoRedoManager ↔ DataStore     Firestore implementation
```

No layer skips. No circular dependencies. No shared mutable state outside the repository.

## Firestore data model

Each list item is its own Firestore document. This gives independent write paths per item — two users editing different items simultaneously never interact. This is the foundation for the "independent item updates interleave automatically" requirement.

**Collection structure:** `lists/{listId}/items/{itemId}` — items are a subcollection under the list document. This supports multi-list and per-list security rules without migration when those features are added.

**Item document fields:** See `conflict-detection-design.md` for full document structure. Core fields: `fields` (map: name, quantity, checked), `fingerprint`, `baseFields`, `baseFingerprint`, `clientId`. 

**Conflict notifications:** (Not implemented) `lists/{listId}/notifications/{clientId}/pending/{notificationId}` — per-client subcollection. Each document contains the human-readable conflict description. The client listens to its own subcollection, surfaces the alert, then deletes the document after the user dismisses it.

### Item document structure

```
lists/{listId}/items/{itemId}
{
  fields: {
    name: string,
    quantity: number,
    checked: boolean
  },
  fingerprint: string,
  clientId: string,
  baseFields: {
    name: string,
    quantity: number,
    checked: boolean
  },
  baseFingerprint: string,
}
```

**`fields`** — the current state of the item. This is what the app renders.

**`fingerprint`** — a stable fingerprint of the current `fields`, computed and written by the client on every mutation. The Cloud Function never computes fingerprints — it only compares strings.

**`clientId`** — identifies which client performed this write. Stable per device, persisted across app restarts. Generated on first launch (UUID), stored in Preferences DataStore.

**`baseFields`** — the state of `fields` observed by the client immediately before it performed this write. Written by the client on every mutation: the client copies the current `fields` into `baseFields` before overwriting `fields` with the new values.

**`baseFingerprint`** — the `fingerprint` value of the state the client based its edit on. If the client is editing from up-to-date state, this matches the document's `fingerprint` field prior to the write. If another client wrote in between, it won't match.

