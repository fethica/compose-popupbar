# compose-popupbar

`compose-popupbar` is a media-agnostic popup bar for Jetpack Compose: it keeps a compact bar docked above your bottom navigation and morphs it into full-screen content through an interactive, interruptible transition. It is inspired by and gratefully credits [LNPopupController](https://github.com/LeoNatan/LNPopupController); the Android library owns presentation and gestures while your app supplies all content, artwork, progress, and actions.

| Collapsed | Mid-morph | Expanded |
| --- | --- | --- |
| ![A floating popup bar above bottom navigation](docs/images/popupbar-collapsed.png) | ![The popup bar morphing into full-screen content](docs/images/popupbar-mid-morph.png) | ![Expanded popup content](docs/images/popupbar-expanded.png) |

## Requirements

| | |
| --- | --- |
| `minSdk` | 24 |
| `compileSdk` | 36.1 (`release(36) { minorApiLevel = 1 }`) |
| Compose | BOM 2026.01.01 (the library exposes it as `api`, so it lines your app up on the same versions) |
| Building this repo | Kotlin 2.3.10, AGP 9.0.1, JDK 21 |

## Install from JitPack

Add JitPack to dependency resolution in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the library to the app module:

```kotlin
dependencies {
    implementation("com.github.fethica.compose-popupbar:popupbar:0.1.0")
}
```

JitPack builds from git, so the version has to be something git can resolve: a tag that has been pushed (`git tag 0.1.0 && git push --tags`) or a commit hash. A version that only exists in `gradle.properties` will not resolve.

## Use a composite build locally

Keep this repository beside your app and conditionally include it from the app's `settings.gradle.kts`:

```kotlin
val popupBarCheckout = file("../compose-popupbar")
if (popupBarCheckout.exists()) {
    includeBuild(popupBarCheckout)
}
```

Keep the same published coordinate in the app module:

```kotlin
implementation("com.github.fethica.compose-popupbar:popupbar:0.1.0")
```

Gradle substitutes the included `:popupbar` project for that coordinate, so local and published builds use the same app code.

## Minimal usage

```kotlin
@Composable
fun PlayerScreen() {
    val popupState = rememberPopupState(PopupValue.Collapsed)

    MaterialTheme {
        PopupHost(
            state = popupState,
            bottomBar = {
                NavigationBar {
                    // NavigationBarItem(...)
                }
            },
            popupBar = {
                PopupBar(
                    title = "Title",
                    subtitle = "Subtitle",
                )
            },
            popupContent = {
                Box(Modifier.fillMaxSize()) {
                    // Full-screen player content
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                // App screen content
            }
        }
    }
}
```

The bar itself expands the popup when tapped. Call the state methods from your own UI or app state to present, expand, collapse, or hide it.

## Option matrix

| Option | Value | Effect |
| --- | --- | --- |
| `PopupBarStyle` | `Floating` (default) | 64 dp rounded card with horizontal and bottom margins. |
| `PopupBarStyle` | `FloatingCompact` | Shorter 48 dp floating card. |
| `PopupBarStyle` | `Prominent` | 64 dp edge-to-edge bar flush with the docking bar. |
| `PopupBarStyle` | `Compact` | 40 dp edge-to-edge bar with compact artwork. |
| `PopupInteractionStyle` | `Drag` (default) | The bar and expanded content continuously follow a vertical drag. |
| `PopupInteractionStyle` | `Snap` | Commits after the distance or velocity threshold, then animates to an anchor. |
| `PopupInteractionStyle` | `Scroll` | Adds nested-scroll handoff for long popup content. |
| `PopupInteractionStyle` | `None` | Disables swipes; bar taps, close controls, and state methods still work. |
| `PopupCloseButtonStyle` | `Grabber` (default) | Wide, accessible drag-handle collapse target. |
| `PopupCloseButtonStyle` | `Chevron` | Down-chevron icon button. |
| `PopupCloseButtonStyle` | `Round` | Filled tonal circular close button. |
| `PopupCloseButtonStyle` | `None` | Draws no close control. |
| `PopupCloseButtonPosition` | `Leading` | Places the close control at logical start. |
| `PopupCloseButtonPosition` | `Center` (default) | Centers the close control. |
| `PopupCloseButtonPosition` | `Trailing` | Places the close control at logical end. |
| `PopupProgressStyle` | `None` (default) | Draws no bar progress strip. |
| `PopupProgressStyle` | `Top` | Draws the progress strip along the bar's top edge. |
| `PopupProgressStyle` | `Bottom` | Draws the progress strip along the bar's bottom edge. |

When `PopupBar` is given an `onSeek`, seek by dragging along the hairline; taps always expand. The strip claims a 16 dp touch band hugging its edge for that drag and nothing else: a tap on the band falls through to the popup, a vertical swipe from it still drags the popup open, and an action button whose target reaches into the band keeps firing inside its own bounds. The hairline is 2 dp flush against the edge whether or not it is seekable, thickening by a dp only while a seek drag is live.

`PopupHost` also accepts:

- **`containerColor`**: the surface the morph is made of. It is the collapsed bar's background, and the same surface grows into the full-screen card. That is why it lives on the host rather than in `PopupBarColors`, where a second value could only disagree with what is painted.
- **`scrimColor`**: drawn over the screen as the popup expands, `Color.Transparent` by default. In a light theme give it a translucent dark value: against a pale screen an unscrimmed morph reads as a flat wipe rather than a card growing out of the bar.
- **`hapticsEnabled`**: threshold and settle feedback during user gestures. Programmatic transitions never buzz.

`PopupBar` accepts optional progress, seeking, action slots, colors, text styles, marquee configuration, and an accessibility-description override. Titles scroll continuously when truncated, so a barely-clipped title doesn't stop marqueeing after a couple of short cycles.

## State API

| Member | Meaning |
| --- | --- |
| `present()` | Animate from `Hidden` to `Collapsed`. Also finishes a presentation left part-way by a cancelled `present()` or `hide()`; no-op once fully presented. |
| `expand()` | Present if necessary, then animate to `Expanded`. |
| `collapse()` | Animate to `Collapsed`; no-op while hidden. |
| `hide()` | Collapse if necessary, then animate out to `Hidden`. Finishes a cancelled exit the same way; no-op once fully hidden. |
| `snapTo(value)` | Move immediately to `Hidden`, `Collapsed`, or `Expanded`. |
| `currentValue` | Last settled `PopupValue`. |
| `targetValue` | Destination of the current gesture or animation. |
| `progress` | Continuous expansion fraction: `0f` collapsed, `1f` expanded. |
| `presentation` | Continuous visibility fraction: `0f` hidden, `1f` presented. |
| `isHidden`, `isCollapsed`, `isExpanded` | Convenient settled-state flags. |
| `isAnimationRunning` | Whether presentation or expansion is animating. |

All transitions are suspending and respect the `confirmValueChange` callback passed to `rememberPopupState`.

## Designing the content

Popup content is measured at the full host size throughout the morph, so it does not reflow while dragging. The host applies no window insets to it, so apply `statusBarsPadding()` inside your own content, and then leave 48 dp below the status-bar inset clear: that band belongs to the host's close affordance, whichever `PopupCloseButtonStyle` is in use.

Insets elsewhere:

- **`bottomBar`** owns its `navigationBars` inset, exactly like a plain `NavigationBar` outside this library. The host does not add one. Only when `bottomBar` is empty does the host apply the navigation-bar inset itself, so the popup bar does not sit under the gesture pill.
- **`content`** receives `PaddingValues` with a bottom value only: the docking bar plus, once presented, the popup bar and its margins. Every other edge is yours, including the status bar.

Apply that inset as `contentPadding` on your scrollable content, not as outer padding on an opaque container: the bar must float over your list, not sit on a strip. Non-scrolling content applies it as inner padding on its own column instead. Either way it is a bottom value only; the host paints nothing behind the bar itself, only the card, through `containerColor`.

```kotlin
LazyColumn(
    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
) {
    // the last row scrolls up past the bar; the list stays visible through its margins and the gap above it
}
```

When using shared artwork, pass one composable through `popupImage` and reserve its expanded destination exactly once:

```kotlin
PopupHost(
    state = popupState,
    popupImage = { AlbumArtwork() },
    popupBar = { PopupBar(title = "Title") },
    popupContent = {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
            Spacer(Modifier.height(48.dp))
            PopupImageSlot(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
            )
            // Player controls
        }
    },
) { padding -> AppContent(Modifier.padding(padding)) }
```

The host keeps that artwork composable alive and moves it between the bar thumbnail and content slot. For lyrics, queues, or other long content, use `PopupInteractionStyle.Scroll` with a nested-scroll child such as `LazyColumn`.

`PopupBar` reserves the collapsed end of that trip for you. A hand-written bar reserves it through `PopupBarScope`, the receiver of the `popupBar` slot:

```kotlin
popupBar = {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        PopupBarImageSlot(Modifier.size(48.dp))
        Text("Title", Modifier.weight(1f))
    }
}
```

Call it at most once, and only when the host was given a `popupImage`. The corner radius follows the `PopupBarStyle`. If either end of the trip is missing, the host draws the artwork only at the end that exists, and only once the morph has fully settled there.

## Accessibility and RTL

The collapsed bar exposes one merged node with `Role.Button`, a default description of `"title, subtitle"`, a click action labelled "Expand" (`onClickLabel`, localized), and progress semantics when progress is supplied. Behind an expanded popup the screen content and the docking bar are hidden from accessibility services, so TalkBack cannot walk off the popup into UI the user cannot see. Collapsed popup content is hidden from accessibility services, while close controls use localized library strings in English, French, and Arabic.

Layout uses logical start/end positions. Leading and trailing controls mirror in RTL, and the seekable progress strip reverses its fill and touch mapping. The sample app has live RTL and dark-theme toggles for verification.

## Not yet

- Paging to previous or next popup items.
- Built-in blurred or translucent popup backgrounds.

## License

MIT. See [LICENSE](LICENSE).
