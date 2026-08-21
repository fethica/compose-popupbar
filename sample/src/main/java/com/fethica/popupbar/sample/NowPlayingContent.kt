package com.fethica.popupbar.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fethica.popupbar.PopupContentScope

@Composable
internal fun NowPlayingContent(
    player: FakePlayer,
    scope: PopupContentScope,
    scrollable: Boolean,
) {
    if (scrollable) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .testTag("sample:scroll-content"),
        ) {
            item {
                NowPlayingHeader(
                    player = player,
                    scope = scope,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            items(100) { index ->
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        text = "Verse ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "A scrolling content row that demonstrates nested popup gestures.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .testTag("sample:fixed-content"),
        ) {
            NowPlayingHeader(player = player, scope = scope)
        }
    }
}

@Composable
private fun NowPlayingHeader(
    player: FakePlayer,
    scope: PopupContentScope,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(48.dp))
        scope.PopupImageSlot(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .testTag("sample:artwork-slot"),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = player.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = player.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Slider(
            value = player.positionMs.toFloat() / player.durationMs.toFloat(),
            onValueChange = player::seekTo,
            modifier = Modifier.testTag("sample:content-progress"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(player.positionMs), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(player.durationMs), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { player.seekBy(-10_000L) }) {
                Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds")
            }
            IconButton(
                onClick = player::toggle,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { player.seekBy(10_000L) }) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun ArtworkGradient() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6750A4), Color(0xFF03DAC6)),
                ),
            )
            .testTag("sample:artwork"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = "Sample artwork",
            tint = Color.White,
            modifier = Modifier.size(80.dp),
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
