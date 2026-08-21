package com.fethica.popupbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val GrabberWidth = 36.dp
private val GrabberHeight = 5.dp
private val GrabberTouchTargetWidth = 88.dp
private val GrabberTouchTargetHeight = 48.dp
private val GrabberTopPadding = 4.dp
private val ChevronSize = 28.dp
private val RoundButtonSize = 40.dp
private val RoundButtonTopPadding = 8.dp
private val EdgeHorizontalPadding = 8.dp
private const val GrabberAlpha = 0.4f

/** The collapse affordance shown at the top of the expanded popup. */
@Composable
internal fun PopupCloseButton(
    style: PopupCloseButtonStyle,
    position: PopupCloseButtonPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (style) {
        PopupCloseButtonStyle.Grabber -> {
            val collapseDescription = stringResource(R.string.popupbar_collapse)
            Box(
                modifier
                    .size(width = GrabberTouchTargetWidth, height = GrabberTouchTargetHeight)
                    .testTag("popupbar:close")
                    .semantics { contentDescription = collapseDescription }
                    .clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .padding(top = GrabberTopPadding)
                        .size(width = GrabberWidth, height = GrabberHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GrabberAlpha),
                        ),
                )
            }
        }

        PopupCloseButtonStyle.Chevron -> {
            val closeDescription = stringResource(R.string.popupbar_close)
            IconButton(
                onClick = onClick,
                modifier = modifier
                    .edgePadding(position)
                    .testTag("popupbar:close"),
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = closeDescription,
                    modifier = Modifier.size(ChevronSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        PopupCloseButtonStyle.Round -> {
            val closeDescription = stringResource(R.string.popupbar_close)
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier
                    .padding(top = RoundButtonTopPadding)
                    .edgePadding(position)
                    .size(RoundButtonSize)
                    .testTag("popupbar:close"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeDescription,
                )
            }
        }

        PopupCloseButtonStyle.None -> Box(modifier.size(0.dp))
    }
}

private fun Modifier.edgePadding(position: PopupCloseButtonPosition): Modifier =
    if (position == PopupCloseButtonPosition.Center) this else padding(horizontal = EdgeHorizontalPadding)
