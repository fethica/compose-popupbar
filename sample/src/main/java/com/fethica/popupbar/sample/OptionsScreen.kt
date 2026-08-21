package com.fethica.popupbar.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fethica.popupbar.PopupBarStyle
import com.fethica.popupbar.PopupCloseButtonPosition
import com.fethica.popupbar.PopupCloseButtonStyle
import com.fethica.popupbar.PopupInteractionStyle
import com.fethica.popupbar.PopupProgressStyle
import com.fethica.popupbar.PopupValue

internal data class SampleOptions(
    val barStyle: PopupBarStyle = PopupBarStyle.Floating,
    val interactionStyle: PopupInteractionStyle = PopupInteractionStyle.Drag,
    val closeButtonStyle: PopupCloseButtonStyle = PopupCloseButtonStyle.Grabber,
    val closeButtonPosition: PopupCloseButtonPosition = PopupCloseButtonPosition.Center,
    val progressStyle: PopupProgressStyle = PopupProgressStyle.Top,
    val seekable: Boolean = true,
    val hasImage: Boolean = true,
    val dark: Boolean = false,
    val rtl: Boolean = false,
    val scrim: Boolean = false,
    val haptics: Boolean = true,
)

@Composable
internal fun LibraryScreen(onTrackSelected: (String, String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("sample:library"),
    ) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                Text("PopupBar showcase", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Choose a fake track, then drag or tap the persistent player.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items((1..60).toList(), key = { it }) { index ->
            ListItem(
                headlineContent = { Text("Sourate ${surahName(index)}") },
                supportingContent = { Text(reciterName(index)) },
                leadingContent = {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onTrackSelected("Sourate ${surahName(index)}", reciterName(index))
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
internal fun SearchScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("sample:search"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null)
        Spacer(Modifier.height(12.dp))
        Text("Search demo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The popup remains docked while destinations change.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun OptionsScreen(
    options: SampleOptions,
    popupValue: PopupValue,
    onOptionsChange: (SampleOptions) -> Unit,
    onPresent: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("sample:options"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 24.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Live options", style = MaterialTheme.typography.headlineMedium)
            Text(
                "State: ${popupValue.displayName()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ActionRow(onPresent, onExpand, onCollapse, onHide)
        }
        item {
            EnumChoiceRow("Bar style", options.barStyle) {
                onOptionsChange(options.copy(barStyle = it))
            }
        }
        item {
            EnumChoiceRow("Interaction", options.interactionStyle) {
                onOptionsChange(options.copy(interactionStyle = it))
            }
        }
        item {
            EnumChoiceRow("Close button", options.closeButtonStyle) {
                onOptionsChange(options.copy(closeButtonStyle = it))
            }
        }
        item {
            EnumChoiceRow("Close position", options.closeButtonPosition) {
                onOptionsChange(options.copy(closeButtonPosition = it))
            }
        }
        item {
            EnumChoiceRow("Progress", options.progressStyle) {
                onOptionsChange(options.copy(progressStyle = it))
            }
        }
        item {
            Text("Toggles", style = MaterialTheme.typography.titleMedium)
            SwitchRow("Seekable progress", options.seekable) {
                onOptionsChange(options.copy(seekable = it))
            }
            SwitchRow("Artwork", options.hasImage) {
                onOptionsChange(options.copy(hasImage = it))
            }
            SwitchRow("Dark theme", options.dark) {
                onOptionsChange(options.copy(dark = it))
            }
            SwitchRow("RTL layout", options.rtl) {
                onOptionsChange(options.copy(rtl = it))
            }
            SwitchRow("Scrim", options.scrim) {
                onOptionsChange(options.copy(scrim = it))
            }
            SwitchRow("Haptics", options.haptics) {
                onOptionsChange(options.copy(haptics = it))
            }
        }
    }
}

@Composable
private fun ActionRow(
    onPresent: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onHide: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("State controls", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onPresent) { Text("Present") }
            Button(onClick = onExpand) { Text("Expand") }
            Button(onClick = onCollapse) { Text("Collapse") }
            Button(onClick = onHide) { Text("Hide") }
        }
    }
}

@Composable
private inline fun <reified T> EnumChoiceRow(
    title: String,
    selected: T,
    crossinline onSelected: (T) -> Unit,
) where T : Enum<T> {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            enumValues<T>().forEach { value ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    label = { Text(value.displayName()) },
                    modifier = Modifier.testTag("sample:option:${title}:${value.name}"),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Enum<*>.displayName(): String = name.replace(
    Regex("(?<=[a-z])(?=[A-Z])"),
    " ",
)

private fun surahName(index: Int): String = when ((index - 1) % 8) {
    0 -> "Al-Baqara"
    1 -> "Ali 'Imran"
    2 -> "An-Nisa"
    3 -> "Al-Ma'idah"
    4 -> "Al-An'am"
    5 -> "Al-A'raf"
    6 -> "Al-Anfal"
    else -> "At-Tawbah"
}

private fun reciterName(index: Int): String = when ((index - 1) % 4) {
    0 -> "Mishary Rashid Alafasy"
    1 -> "Abdul Basit Abdus Samad"
    2 -> "Mahmoud Khalil Al-Husary"
    else -> "Muhammad Siddiq Al-Minshawi"
}
