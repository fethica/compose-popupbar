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

/** Spec §3.3: fixed colors for one [PopupBar] instance. Build one with [PopupBarDefaults.colors]. */
@Immutable
public data class PopupBarColors(
    public val containerColor: Color,
    public val titleColor: Color,
    public val subtitleColor: Color,
    public val progressColor: Color,
    public val progressTrackColor: Color,
    public val actionColor: Color,
)

/** Spec §3.3: title/subtitle text styles for one [PopupBar] instance. Build one with [PopupBarDefaults.textStyles]. */
@Immutable
public data class PopupBarTextStyles(
    public val title: TextStyle,
    public val subtitle: TextStyle,
)

/**
 * Default colors, text styles and per-[PopupBarStyle] geometry for [PopupBar].
 *
 * The `Dp` accessors delegate to [metrics] (defined in `PopupStyles.kt`) so the size table for a
 * style lives in exactly one place.
 */
public object PopupBarDefaults {
    @Composable
    public fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleColor: Color = MaterialTheme.colorScheme.onSurface,
        subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        progressColor: Color = MaterialTheme.colorScheme.primary,
        progressTrackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        actionColor: Color = MaterialTheme.colorScheme.onSurface,
    ): PopupBarColors = PopupBarColors(
        containerColor = containerColor,
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

/**
 * Placeholder for the bar's thumbnail. Task 7 turns this into a slot the host registers and
 * travels the popup image through, the same way `PopupImageSlot` does inside `popupContent`.
 */
@Composable
internal fun BarImageSlot(modifier: Modifier) {
    Box(modifier.testTag(PopupBarDefaults.imageSlotTag()))
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
        if (progress != null && progressStyle == PopupProgressStyle.Top) {
            PopupProgressStrip(progress, onSeek, colors, Modifier.align(Alignment.TopCenter))
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
        if (progress != null && progressStyle == PopupProgressStyle.Bottom) {
            PopupProgressStrip(progress, onSeek, colors, Modifier.align(Alignment.BottomCenter))
        }
    }
}
