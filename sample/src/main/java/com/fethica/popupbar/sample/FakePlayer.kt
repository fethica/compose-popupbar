package com.fethica.popupbar.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

internal class FakePlayer {
    var title by mutableStateOf("Sourate Al-Baqara")
    var subtitle by mutableStateOf("Mishary Rashid Alafasy")
    var isPlaying by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    val durationMs: Long = 30L * 60_000L

    fun toggle() {
        isPlaying = !isPlaying
    }

    fun seekTo(fraction: Float) {
        positionMs = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
    }

    fun seekBy(deltaMs: Long) {
        positionMs = (positionMs + deltaMs).coerceIn(0L, durationMs)
    }

    fun selectTrack(newTitle: String, newSubtitle: String) {
        title = newTitle
        subtitle = newSubtitle
        positionMs = 0L
        isPlaying = true
    }

    suspend fun run() {
        while (true) {
            delay(250)
            if (isPlaying) {
                positionMs = (positionMs + 250L).coerceAtMost(durationMs)
            }
        }
    }
}
