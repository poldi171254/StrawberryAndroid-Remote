/*
 * Client for the Strawberry Music Player
 * Copyright 2026, Leopold List <leo@zudiewiener.com>
 *
 * Client for the Strawberry Music Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Client for the Strawberry Music Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Client for the Strawberry Music Player.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.zudiewiener.strawberryremote.screen.songinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Position slider for the current song. The valid seek range is 0 to
 * length-1, so that is the slider's maximum. Dragging only updates the
 * displayed position locally; [onSeek] is called exactly once, on release.
 */
@Composable
fun SeekSlider(
    positionSeconds: Int,
    lengthSeconds: Int,
    enabled: Boolean,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxSeek = (lengthSeconds - 1).coerceAtLeast(0)

    // The value shown while the thumb is held, and kept briefly after release
    // so the thumb doesn't snap back to the old position for a frame before
    // the seek's locally-set position arrives. Cleared by the next position
    // change once the finger is up.
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(positionSeconds) {
        if (!isDragging) dragValue = null
    }

    val shown = dragValue ?: positionSeconds.coerceIn(0, maxSeek).toFloat()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Slider(
            value = shown,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                dragValue?.let { onSeek(it.roundToInt().coerceIn(0, maxSeek)) }
            },
            valueRange = 0f..maxSeek.coerceAtLeast(1).toFloat(),
            enabled = enabled && maxSeek > 0,
            modifier = Modifier.weight(1f)
        )
        Text(
            // Remaining time: follows the thumb while dragging, and the
            // playback position otherwise.
            text = formatTime(lengthSeconds - shown.roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}