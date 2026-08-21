package com.fethica.popupbar.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fethica.popupbar.PopupHost
import com.fethica.popupbar.PopupInteractionStyle
import com.fethica.popupbar.rememberPopupState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { SampleScreen() } }
    }
}

// Throwaway wiring for the Task 4 smoke checks; Task 10 replaces this with the real demo.
// The counter button sits under the expanded popup: if a tap on the popup ever falls through,
// the number goes up.
@Composable
private fun SampleScreen() {
    val state = rememberPopupState()
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { state.present() }
    PopupHost(
        state = state,
        interactionStyle = PopupInteractionStyle.Drag,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                )
            }
        },
        popupBar = { Box(Modifier.fillMaxSize().background(Color.Red)) },
        popupContent = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(Color.LightGray),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = { taps++ }) { Text("SCREEN TAPS: $taps") }
        }
    }
}
