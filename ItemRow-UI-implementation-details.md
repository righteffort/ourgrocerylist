# `ItemRow` design notes

Tap opens the edit dialog; horizontal swipe (either direction, past 80dp)
toggles the item's checked state. UX modeled on the Gmail message list.

## Structure

```
SwipeToDismissBox       ← owns horizontal drag
├── backgroundContent   ← reveal color + icon (left or right edge)
└── Surface(onClick)    ← owns tap; gives ripple + a11y for free
    └── Row { name, quantity }
```

The `Surface(onClick = ...)` pattern (rather than `Modifier.clickable` on the
inner `Row`) follows
[Jetcaster's `EpisodeListItem`](https://github.com/android/compose-samples/blob/main/Jetcaster/mobile/src/main/java/com/example/jetcaster/ui/shared/EpisodeListItem.kt),
which is the only example in the entire `android/` GitHub org that combines
`SwipeToDismissBox` with a tap handler on its content. Tap and swipe
disambiguate via Compose's standard touch-slop mechanism: a short pointer-up
without movement fires the click; movement past slop is claimed by the
`AnchoredDraggable` underneath `SwipeToDismissBox` and the click is cancelled.

## Why `LaunchedEffect` instead of `confirmValueChange`

The official Android
[swipe-to-dismiss guide](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/swipe-to-dismiss)
still shows `confirmValueChange`, but as of Compose Material3 1.5.0-alpha that
parameter is **deprecated**. The deprecation message points at
`AnchoredDraggableDynamicAnchorsSample`, which addresses a different problem
(restricting which anchors are reachable) and doesn't fit the
"trigger-a-side-effect-on-swipe" use case.

The idiomatic replacement is to observe `currentValue` and call `reset()`:

```kotlin
LaunchedEffect(dismissState.currentValue) {
    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
        onToggle()
        dismissState.reset()
    }
}
```

`reset()` is a `suspend fun` on `SwipeToDismissBoxState` that animates back
to `Settled` —
[API ref](https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-swipe-to-dismiss-box-state/reset.html).
It must run in a coroutine, which is what `LaunchedEffect` provides.

We don't get the dismiss-slide-off-screen animation, and we don't want it:
toggling moves the item between the unchecked and checked sections of the
`LazyColumn`. The item's key changes (`u_<id>` ↔ `c_<id>`), so the row
that springs back is a different composable instance than the one that lands
in the other section. `Modifier.animateItem` on the `LazyColumn` items
handles the visual transition between sections.

## Why `requireOffset()` instead of `targetValue` for the threshold-met color

The original hand-rolled implementation brightened the reveal background
once the swipe offset crossed 80dp, as feedback that releasing now will
commit the toggle. Replicating that with the `SwipeToDismissBox` API is
trickier than it looks.

Plausible-looking options that don't work:

- **`dismissState.targetValue != Settled`** — the docs describe `targetValue`
  as "the closest state to the current offset (taking into account positional
  thresholds)," but in practice it represents the *settled* state the swipe
  will land on and doesn't flip reliably during a continuous drag. The color
  doesn't change while dragging.
- **`dismissState.progress`** — this is the fraction from `currentValue` to
  `targetValue`. Useful for a continuous color *lerp* (the official docs
  example uses it that way), but it doesn't give us a clean "have I crossed
  80dp" boolean.

What does work is reading the actual pixel offset and comparing to the
threshold, which is exactly what the original code did:

```kotlin
val thresholdMet = try {
    abs(dismissState.requireOffset()) >= swipeThresholdPx
} catch (_: IllegalStateException) {
    false
}
```

`requireOffset()` is the only public offset accessor on
`SwipeToDismissBoxState`. The non-throwing `offset` property exists on the
underlying `AnchoredDraggableState` but is not re-exposed at the M3 layer.
`requireOffset()` throws while anchors are still being initialized on the
very first frame —
[migration guide](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/migrate-swipeable)
explains that `Float.NaN` was deliberately turned into a throw "to catch
issues early in your workflow." The `try/catch` handles that brief window.

## Reveal-icon alignment

Swipe right (`StartToEnd`) → content slides right → left edge exposed →
icon goes on the **left** (`Alignment.CenterStart`).
Swipe left (`EndToStart`) → mirror.

This matches the original behavior and the Gmail-app convention.
