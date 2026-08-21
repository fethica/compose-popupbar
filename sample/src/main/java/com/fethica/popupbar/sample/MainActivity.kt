package com.fethica.popupbar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.fethica.popupbar.PopupBar
import com.fethica.popupbar.PopupHost
import com.fethica.popupbar.PopupInteractionStyle
import com.fethica.popupbar.PopupValue
import com.fethica.popupbar.rememberPopupState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PopupBarSample { dark ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
}

private enum class SampleTab(val label: String) {
    Library("Library"),
    Search("Search"),
    Settings("Settings"),
}

@Composable
private fun PopupBarSample(onDarkThemeChanged: (Boolean) -> Unit) {
    val state = rememberPopupState(initialValue = PopupValue.Collapsed)
    val player = remember { FakePlayer() }
    val coroutineScope = rememberCoroutineScope()
    var options by remember { mutableStateOf(SampleOptions()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(player) { player.run() }
    LaunchedEffect(options.dark) { onDarkThemeChanged(options.dark) }

    CompositionLocalProvider(
        LocalLayoutDirection provides if (options.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(
            colorScheme = if (options.dark) darkColorScheme() else lightColorScheme(),
        ) {
            val scrimColor = if (options.scrim) {
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)
            } else {
                Color.Transparent
            }
            PopupHost(
                state = state,
                barStyle = options.barStyle,
                interactionStyle = options.interactionStyle,
                closeButtonStyle = options.closeButtonStyle,
                closeButtonPosition = options.closeButtonPosition,
                scrimColor = scrimColor,
                hapticsEnabled = options.haptics,
                bottomBar = {
                    NavigationBar {
                        SampleTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            SampleTab.Library -> Icons.Filled.LibraryMusic
                                            SampleTab.Search -> Icons.Filled.Search
                                            SampleTab.Settings -> Icons.Filled.Settings
                                        },
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                },
                popupBar = {
                    PopupBar(
                        title = player.title,
                        subtitle = player.subtitle,
                        progress = { player.positionMs.toFloat() / player.durationMs.toFloat() },
                        onSeek = if (options.seekable) player::seekTo else null,
                        progressStyle = options.progressStyle,
                        actions = {
                            IconButton(onClick = player::toggle) {
                                Icon(
                                    imageVector = if (player.isPlaying) {
                                        Icons.Filled.Pause
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                                )
                            }
                            IconButton(onClick = { player.seekBy(10_000L) }) {
                                Icon(
                                    imageVector = Icons.Filled.FastForward,
                                    contentDescription = "Forward 10 seconds",
                                )
                            }
                        },
                    )
                },
                popupImage = if (options.hasImage) {
                    { ArtworkGradient() }
                } else {
                    null
                },
                popupContent = {
                    NowPlayingContent(
                        player = player,
                        scope = this,
                        scrollable = options.interactionStyle == PopupInteractionStyle.Scroll,
                    )
                },
            ) { popupPadding ->
                SampleDestination(
                    tab = SampleTab.entries[selectedTab],
                    padding = popupPadding,
                    options = options,
                    popupValue = state.currentValue,
                    onOptionsChange = { options = it },
                    onTrackSelected = { title, subtitle ->
                        player.selectTrack(title, subtitle)
                        coroutineScope.launch { state.present() }
                    },
                    onPresent = { coroutineScope.launch { state.present() } },
                    onExpand = { coroutineScope.launch { state.expand() } },
                    onCollapse = { coroutineScope.launch { state.collapse() } },
                    onHide = { coroutineScope.launch { state.hide() } },
                )
            }
        }
    }
}

@Composable
private fun SampleDestination(
    tab: SampleTab,
    padding: PaddingValues,
    options: SampleOptions,
    popupValue: PopupValue,
    onOptionsChange: (SampleOptions) -> Unit,
    onTrackSelected: (String, String) -> Unit,
    onPresent: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(padding),
    ) {
        when (tab) {
            SampleTab.Library -> LibraryScreen(onTrackSelected)
            SampleTab.Search -> SearchScreen()
            SampleTab.Settings -> OptionsScreen(
                options = options,
                popupValue = popupValue,
                onOptionsChange = onOptionsChange,
                onPresent = onPresent,
                onExpand = onExpand,
                onCollapse = onCollapse,
                onHide = onHide,
            )
        }
    }
}
