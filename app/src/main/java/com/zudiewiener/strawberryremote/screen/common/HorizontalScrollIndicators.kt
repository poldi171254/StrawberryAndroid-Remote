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
package com.zudiewiener.strawberryremote.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Fading chevrons at either edge of a horizontally-scrollable region,
 * visible only when there's actually more content in that direction. Shared
 * by the queue table's column scroll and the playlist tab row - takes plain
 * booleans rather than a specific scroll-state type so both a ScrollState-
 * backed Row (queue table) and a LazyListState-backed LazyRow (playlist
 * tabs, which needs LazyListState for its scroll-to-center behavior) can
 * each compute canScrollBackward/canScrollForward their own way and share
 * this same rendering.
 */
@Composable
internal fun BoxScope.HorizontalScrollIndicators(canScrollBackward: Boolean, canScrollForward: Boolean) {
    if (canScrollForward) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "More columns",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small
                )
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
    if (canScrollBackward) {
        Icon(
            imageVector = Icons.Filled.ChevronLeft,
            contentDescription = "More columns",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small
                )
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}