See also deferred.md

# Fancy animations

A bit outdated
- **Check:** Checkbox fills with accent color and checkmark. Once animation completes, row collapses and item cross-fades into its alphabetical position in the checked section. Viewport stays anchored to the unchecked section — does not follow the item.
- **Uncheck:** Checkbox drains (checkmark disappears, border goes gray). Row collapses and item appears in the unchecked section. Viewport stays in the checked section.
- **Delete:** Row flushes light red, then slides horizontally off the left edge while collapsing vertically. A trashcan icon materializes at the bottom of the screen, appears to receive the deleted item (lid opens and closes), then fades out. No permanent trash list.
- Remote mutations animate the same way as local ones.

## LazyColumn `animateItem()` tuning options

Applies to `ItemRow` calls in `ShoppingListScreen.kt` (unchecked + checked `itemsIndexed`).

### Parameters

| Param | Default (`animateItem()`) | Snappier option | Notes |
|---|---|---|---|
| `fadeOutSpec` | `spring(stiffness = StiffnessMediumLow)` ~400–600ms | `tween(durationMillis = 150)` | Exit should be quick — confirmatory, not decorative |
| `placementSpec` | `spring(stiffness = StiffnessMediumLow)` slight bounce | `spring(stiffness = StiffnessMedium, dampingRatio = DampingRatioNoBouncy)` ~200ms | Crisp squeeze, no overshoot |
| `fadeInSpec` | `spring(stiffness = StiffnessMediumLow)` ~400–600ms | `tween(durationMillis = 200)` | Gentle entrance into new section feels "settled" |

Spring stiffness reference: `StiffnessVeryLow`=50, `StiffnessLow`=200, `StiffnessMediumLow`=400 (default), `StiffnessMedium`=1500, `StiffnessHigh`=10000.

### To apply the snappier set

```kotlin
Modifier.animateItem(
    fadeInSpec = tween(durationMillis = 200),
    placementSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy),
    fadeOutSpec = tween(durationMillis = 150),
)
```

Import needed (beyond defaults already in file):
```kotlin
import androidx.compose.animation.core.Spring
```


## UX — undo/redo

**Deferred: field-level pruning.** A smarter approach would compare
which fields each undo/redo entry touched against which fields the
remote write changed, and only discard entries with overlapping
fields. The data to support this is already present in the command
snapshots. Note that field-level independence is a structural
approximation — users may attach semantic relationships across fields
(e.g. changing "six-pack of beer" to "case of beer" while another user
changes quantity from 1 to 4). This is an inherent limitation of
treating fields independently and is acceptable for this app.

## UX — real-time collaboration

The Firestore snapshot listener fires on every client whenever any client mutates any item document. Remote mutations animate the same way as local ones.

**Conflict UX (two users edit the same item simultaneously):** Last
write wins on the server. An `onUpdate` Cloud Function trigger detects
that the writing client's `baseFingerprint` doesn't match the previous
document's `fingerprint`, indicating the writer was editing from stale
state. The trigger writes a conflict notification to per-client
notifications subcollection (see Firestore data model) for both the
writer, and the writer of the overwritten data. The client's listener
picks it up and surfaces a modal alert with a single dismiss
button. Example alert text for the writer: "Your edit to [item name]
was based on stale state. [Description of what was there before your
write — e.g. 'Another user had changed it to: name, quantity, checked
state']." The client deletes the notification document on dismiss. No
merge UI, no recovery assistance. The writer's changes still stand —
the notifications are informational only.

This conflict case is expected to be extremely rare in practice.

## CSV import/export

Accessible via the three-dot overflow menu.

**Headers (in order):** `name`, `checked`, `quantity`

**Export:** Always writes all three columns. Checked renders as TRUE/FALSE.

**Import (v0):** Replaces the existing list entirely (v0 has only one list, so import is a "load list from file" operation). Requires a confirmation dialog: "This will replace your current list. Continue?" Checked defaults to FALSE if absent. Quantity defaults to 1 if absent.

Import/export are for bootstrapping and backup. List sharing with collaborators is a separate feature (future, via the share icon).



