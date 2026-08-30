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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zudiewiener.strawberryremote.logic.PlaylistTab
import com.zudiewiener.strawberryremote.screen.common.HorizontalScrollIndicators

/**
 * Custom-built rather than Material3's SecondaryScrollableTabRow: that
 * component doesn't expose its internal scroll state, so there was no way to
 * drive a real "can scroll further" indicator off it, or scroll the active
 * tab into view programmatically. Built on LazyRow/LazyListState rather than
 * a plain Row/ScrollState specifically for the latter - LazyListState's
 * layoutInfo gives per-item on-screen position/size, which is what makes
 * centering the active tab (see the LaunchedEffect below) straightforward
 * and reliable; doing the equivalent with raw pixel math on a plain Row
 * would mean manually tracking each tab's position via onGloballyPositioned
 * and reasoning about scroll-offset coordinate spaces by hand.
 */
@Composable
internal fun PlaylistTabsRow(
    playlists: List<PlaylistTab>,
    selectedIndex: Int,
    activeIndex: Int,
    onSelect: (Int) -> Unit
) {
    if (playlists.isEmpty()) return
    val listState = rememberLazyListState()

    // Keeps whichever playlist is actually playing centered in view, rather
    // than leaving the user to scroll and find it - runs once whenever the
    // active playlist itself changes (including on first composition, since
    // LaunchedEffect fires on its key's initial value too).
    //
    // Two steps, since LazyListState only exposes layout info (offset/size)
    // for items that are currently visible: scrollToItem() first jumps so
    // the target tab is visible at all (aligned to the viewport's start),
    // then its now-available layoutInfo is used to fine-tune with
    // animateScrollBy() so it lands precisely centered rather than merely
    // visible.
    LaunchedEffect(activeIndex) {
        if (activeIndex < 0 || activeIndex >= playlists.size) return@LaunchedEffect
        listState.scrollToItem(activeIndex)
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (itemInfo != null) {
            val viewportCenter = listState.layoutInfo.viewportSize.width / 2
            val itemCenter = itemInfo.offset + itemInfo.size / 2
            listState.animateScrollBy((itemCenter - viewportCenter).toFloat())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            itemsIndexed(playlists, key = { _, tab -> tab.id }) { index, tab ->
                PlaylistTabItem(
                    name = tab.name,
                    isSelected = index == selectedIndex,
                    isActive = index == activeIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
        HorizontalScrollIndicators(
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward
        )
    }
}

@Composable
private fun PlaylistTabItem(
    name: String,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
        )
    }
}