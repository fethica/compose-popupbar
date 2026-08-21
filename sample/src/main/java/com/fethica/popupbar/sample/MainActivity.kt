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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fethica.popupbar.PopupHost
import com.fethica.popupbar.rememberPopupState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { SampleScreen() } }
    }
}

// Throwaway wiring for the Task 4 smoke check; Task 10 replaces this with the real demo.
@Composable
private fun SampleScreen() {
    val state = rememberPopupState()
    LaunchedEffect(Unit) { state.present() }
    PopupHost(
        state = state,
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
        Box(Modifier.fillMaxSize().padding(padding).background(Color.LightGray))
    }
}
