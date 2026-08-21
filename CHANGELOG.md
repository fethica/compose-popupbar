# Changelog

## 0.1.0 — 2026-08-21

- Added hidden, collapsed, and expanded popup state with animated and immediate transitions.
- Added the morphing popup host, travelling artwork slot, docking-bar motion, predictive back, drag haptics, and optional scrim.
- Added floating, floating-compact, prominent, and compact popup bars with optional seekable progress and action slots.
- Added drag, snap, nested-scroll, and gesture-free interaction styles.
- Added grabber, chevron, round, and hidden close-button styles with leading, center, and trailing placement.
- Added localized accessibility semantics, RTL behavior, pure JVM coverage, instrumented Compose coverage, and a full sample app.
- Fixed the title and subtitle marquee stopping after three short cycles on barely-truncated text; it now scrolls continuously for as long as the text stays truncated.

### Public surface settled before the first tag

- `PopupHost` takes `containerColor`: the surface the whole morph is made of, from the collapsed bar's background to the full-screen card.
- `PopupBarColors` no longer carries `containerColor`. The card is painted once, by the host, so a per-bar copy could only disagree with it.
- `PopupBarColors` and `PopupBarTextStyles` are `@Immutable class` with explicit `copy`, `equals` and `hashCode` rather than `data class`, so the property list is not pinned into the ABI.
- `PopupHost`'s `popupBar` slot has a receiver, `PopupBarScope`, whose `PopupBarImageSlot(modifier)` reserves the collapsed end of the travelling artwork. A hand-written bar can now take part in it, not just the built-in `PopupBar`.
- `PopupContentScope.PopupImageSlot` and `PopupBarScope.PopupBarImageSlot` both default `modifier` to `Modifier`.
- The library publishes its Compose dependencies as `api`, so a consumer that declares only this artifact can name every type in its signatures.
