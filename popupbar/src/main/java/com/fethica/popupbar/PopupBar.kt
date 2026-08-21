package com.fethica.popupbar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spec §3.3: fixed colors for one [PopupBar] instance. Build one with [PopupBarDefaults.colors].
 *
 * Deliberately not a `data class`: the generated `componentN()`/`copy()` pin the property list into
 * the ABI, so adding a colour later would be a binary-incompatible change for every consumer.
 * `copy`, `equals` and `hashCode` are written out instead, the way Material 3's own colour holders
 * are.
 *
 * There is deliberately no `containerColor` here: the surface behind the bar is the popup layer that
 * morphs into the full-screen card, so it belongs to [PopupHost] (its `containerColor` parameter) and
 * a per-bar copy could only ever disagree with what is actually painted.
 */
@Immutable
public class PopupBarColors(
    public val titleColor: Color,
    public val subtitleColor: Color,
    public val progressColor: Color,
    public val progressTrackColor: Color,
    public val actionColor: Color,
) {
    public fun copy(
        titleColor: Color = this.titleColor,
        subtitleColor: Color = this.subtitleColor,
        progressColor: Color = this.progressColor,
        progressTrackColor: Color = this.progressTrackColor,
        actionColor: Color = this.actionColor,
    ): PopupBarColors = PopupBarColors(
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        progressColor = progressColor,
        progressTrackColor = progressTrackColor,
        actionColor = actionColor,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupBarColors) return false
        return titleColor == other.titleColor &&
            subtitleColor == other.subtitleColor &&
            progressColor == other.progressColor &&
            progressTrackColor == other.progressTrackColor &&
            actionColor == other.actionColor
    }

    override fun hashCode(): Int {
        var result = titleColor.hashCode()
        result = 31 * result + subtitleColor.hashCode()
        result = 31 * result + progressColor.hashCode()
        result = 31 * result + progressTrackColor.hashCode()
        result = 31 * result + actionColor.hashCode()
        return result
    }
}

/**
 * Spec §3.3: title/subtitle text styles for one [PopupBar] instance. Build one with
 * [PopupBarDefaults.textStyles]. Not a `data class`, for the same ABI reason as [PopupBarColors].
 */
@Immutable
public class PopupBarTextStyles(
    public val title: TextStyle,
    public val subtitle: TextStyle,
) {
    public fun copy(
        title: TextStyle = this.title,
        subtitle: TextStyle = this.subtitle,
    ): PopupBarTextStyles = PopupBarTextStyles(title = title, subtitle = subtitle)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupBarTextStyles) return false
        return title == other.title && subtitle == other.subtitle
    }

    override fun hashCode(): Int = 31 * title.hashCode() + subtitle.hashCode()
}

/**
 * Default colors, text styles and per-[PopupBarStyle] geometry for [PopupBar].
 *
 * The `Dp` accessors delegate to [metrics] (defined in `PopupStyles.kt`) so the size table for a
 * style lives in exactly one place.
 */
public object PopupBarDefaults {
    @Composable
    public fun colors(
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        progressColor: Color = MaterialTheme.colorScheme.primary,
        progressTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        actionColor: Color = MaterialTheme.colorScheme.onSurface,
    ): PopupBarColors = PopupBarColors(
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        progressColor = progressColor,
        progressTrackColor = progressTrackColor,
        actionColor = actionColor,
    )

    @Composable
    public fun textStyles(
        title: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        subtitle: TextStyle = MaterialTheme.typography.bodySmall,
    ): PopupBarTextStyles = PopupBarTextStyles(title = title, subtitle = subtitle)

    public fun height(style: PopupBarStyle): Dp = style.metrics().height
    public fun imageSize(style: PopupBarStyle): Dp = style.metrics().imageSize
    public fun imageCornerRadius(style: PopupBarStyle): Dp = style.metrics().imageCornerRadius
    public fun horizontalMargin(style: PopupBarStyle): Dp = style.metrics().horizontalMargin
    public fun bottomMargin(style: PopupBarStyle): Dp = style.metrics().bottomMargin
    public fun cornerRadius(style: PopupBarStyle): Dp = style.metrics().cornerRadius
    public fun shadowElevation(style: PopupBarStyle): Dp = style.metrics().shadowElevation

    /** Forwards [PopupDefaults.snapSpec]; [PopupDefaults] stays the canonical owner. */
    public val snapSpec: AnimationSpec<Float> get() = PopupDefaults.snapSpec

    /** Forwards [PopupDefaults.presentationSpec]; [PopupDefaults] stays the canonical owner. */
    public val presentationSpec: AnimationSpec<Float> get() = PopupDefaults.presentationSpec
}

/** Stable test tag for the bar's image slot, independent of [PopupBarStyle]. */
internal fun PopupBarDefaults.imageSlotTag(): String = "popupbar:imageSlot"

/** Empty thumbnail slot whose host-local bounds drive the travelling popup image. */
@Composable
internal fun BarImageSlot(modifier: Modifier) {
    val registry = LocalPopupImageRegistry.current
    val hostCoordinates = LocalPopupHostCoordinates.current
    val radiusPx = with(LocalDensity.current) {
        LocalPopupBarStyle.current.metrics().imageCornerRadius.toPx()
    }
    Box(
        modifier
            .then(
                if (registry == null) {
                    Modifier
                } else {
                    Modifier.reportPopupSlot(
                        registry = registry,
                        isBar = true,
                        hostCoordinates = hostCoordinates,
                        radiusPx = radiusPx,
                    )
                },
            )
            .testTag(PopupBarDefaults.imageSlotTag()),
    )
}

/**
 * The default popup bar. Spec §3.3: `[image slot] [title / subtitle, weight 1, marquee] [actions]`
 * with an optional progress hairline spanning the full width.
 *
 * [PopupBar] does not add its own tap target or its own accessibility merge boundary: [PopupHost]
 * wraps `popupBar()` in a bar-frame box that already carries the tap-to-expand `clickable`, the
 * `popupbar:bar` test tag, and `Modifier.semantics(mergeDescendants = true)` placed before that
 * `clickable` so the click's `Role`/`onClick` and this composable's `contentDescription`/
 * `progressBarRangeInfo` land in the same merged node. This composable's own `Modifier.semantics {}`
 * therefore does **not** set `mergeDescendants = true` and does **not** set `onClick`: doing either
 * here would create a second, independent accessibility node (TalkBack seeing two stops over one
 * bar) instead of contributing properties up into the host's merge.
 */
@Composable
public fun PopupBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: (() -> Float)? = null,
    onSeek: ((Float) -> Unit)? = null,
    progressStyle: PopupProgressStyle = PopupProgressStyle.None,
    actions: @Composable RowScope.() -> Unit = {},
    colors: PopupBarColors = PopupBarDefaults.colors(),
    textStyles: PopupBarTextStyles = PopupBarDefaults.textStyles(),
    marquee: Boolean = true,
    marqueeInitialDelayMillis: Int = 2_000,
    contentDescription: String? = null,
) {
    val style = LocalPopupBarStyle.current
    val metrics = style.metrics()
    val hasImage = LocalPopupHasImage.current
    val description = contentDescription
        ?: listOfNotNull(title, subtitle?.takeIf { it.isNotBlank() }).joinToString(", ")
    Box(
        modifier
            .fillMaxSize()
            // No `mergeDescendants = true` and no `onClick` here: PopupHost's bar-frame box is the
            // one merge boundary (it sits above this composable and carries the real `clickable`).
            // These properties merge up into that single node instead of creating a second one.
            .semantics {
                this.contentDescription = description
                role = Role.Button
                if (progress != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
                }
            },
    ) {
        // Composed BEFORE the Row, whichever edge it is on, so the Row is hit-tested first. A
        // seekable strip is a band, and the action buttons' 48dp targets reach into that band; a
        // press inside a button's own bounds has to fire the button. The strip still claims the rest
        // of the band, where the Row has no pointer target at all. `Bottom` used to be composed
        // last, which made it swallow the buttons' bottom edge — the two edges now behave alike.
        if (progress != null && progressStyle != PopupProgressStyle.None) {
            PopupProgressStrip(
                progress,
                onSeek,
                colors,
                progressStyle,
                Modifier.align(
                    if (progressStyle == PopupProgressStyle.Top) {
                        Alignment.TopCenter
                    } else {
                        Alignment.BottomCenter
                    },
                ),
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = if (hasImage) 8.dp else 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasImage) {
                BarImageSlot(
                    Modifier
                        .size(metrics.imageSize)
                        .clip(RoundedCornerShape(metrics.imageCornerRadius)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                // basicMarquee measures at unbounded width; Ellipsis truncation pre-empts that
                // measurement and silently keeps the text from ever scrolling, so Clip is the
                // canonical pairing whenever marquee is enabled. Ellipsis only when it's off.
                val textOverflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis
                Text(
                    text = title,
                    style = textStyles.title,
                    color = colors.titleColor,
                    maxLines = 1,
                    overflow = textOverflow,
                    modifier = if (marquee) {
                        Modifier.basicMarquee(initialDelayMillis = marqueeInitialDelayMillis)
                    } else {
                        Modifier
                    },
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = textStyles.subtitle,
                        color = colors.subtitleColor,
                        maxLines = 1,
                        overflow = textOverflow,
                        modifier = if (marquee) {
                            Modifier.basicMarquee(initialDelayMillis = marqueeInitialDelayMillis)
                        } else {
                            Modifier
                        },
                    )
                }
            }
            CompositionLocalProvider(LocalContentColor provides colors.actionColor) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}
