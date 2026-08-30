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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zudiewiener.strawberryremote.logic.ColumnInfo
import com.zudiewiener.strawberryremote.logic.PlaylistTab
import com.zudiewiener.strawberryremote.logic.QueueRowData
import com.zudiewiener.strawberryremote.screen.common.HorizontalScrollIndicators

// Row height stays fixed (used for each LazyColumn item and the header row);
// text-column width is content-driven, numeric-column width is fixed - see
// computeColumnWidths.
private val ROW_HEIGHT = 48.dp
private val HEADER_ROW_HEIGHT = 36.dp
private val MIN_COLUMN_WIDTH = 40.dp
private val MAX_COLUMN_WIDTH = 240.dp
private val NUMERIC_COLUMN_WIDTH = 56.dp
private val COLUMN_HORIZONTAL_PADDING = 4.dp

/** How many previous (already-played) rows SongInfoScreen keeps visible above the current row. */
internal const val MAX_PREVIOUS_ROWS_SHOWN = 2

/** One entry in the flattened row list the LazyColumn renders - carries the
 * per-row rendering flags alongside the data so a single items() call can
 * cover previous/current/upcoming without three separate loops. */
private data class QueueRowEntry(
    val row: QueueRowData,
    val isCurrent: Boolean,
    val isPrevious: Boolean,
    val isActionable: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueueTable(
    columns: List<ColumnInfo>,
    availableWidth: Dp,
    previousRows: List<QueueRowData>,
    currentRow: QueueRowData?,
    upcomingRows: List<QueueRowData>,
    playlists: List<PlaylistTab>,
    viewedPlaylistIndex: Int,
    mutablePlaylistsEnabled: Boolean,
    onMutationBlocked: () -> Unit,
    onPlayRow: (Int) -> Unit,
    onAddCurrentToPlaylist: (Int, String) -> Unit,
    onRemoveRow: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val canScrollBackward by remember { derivedStateOf { scrollState.value > 0 } }
    val canScrollForward by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }

    // Which row (by rowIndex) currently has its context menu open, if any.
    // Also tracks whether that row is the current (bold) row, since only the
    // current row offers "Add to another playlist".
    var menuForRow by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }

    // Per-column alignment comes straight from the server's own knowledge of
    // each Playlist::Column's nature (numeric/measurement-like vs. free
    // text) - no need to guess from formatted cell content.
    val columnCentered = remember(columns) { columns.map { it.isNumeric } }

    // Content-based column widths: each column is sized to its widest
    // currently-visible header/value (clamped to MIN/MAX), so a numeric
    // column like Track or Year doesn't take the same space as Title or
    // Album just because they share a header row. Only the rows currently
    // loaded are measured - cheap even at up to ~100 rows, and re-measures
    // automatically as the queue changes.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val headerStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val bodyStyle = MaterialTheme.typography.bodyMedium

    val measuredRows = remember(previousRows, currentRow, upcomingRows) {
        buildList {
            addAll(previousRows)
            currentRow?.let { add(it) }
            addAll(upcomingRows)
        }
    }

    val columnWidths = remember(columns, measuredRows, availableWidth) {
        computeColumnWidths(
            columns = columns,
            rows = measuredRows,
            availableWidth = availableWidth,
            textMeasurer = textMeasurer,
            headerStyle = headerStyle,
            bodyStyle = bodyStyle,
            density = density
        )
    }

    // Flattened for a single LazyColumn items() call - previous/current/
    // upcoming only differ in their rendering flags, not in how they're laid
    // out, so there's no need for three separate loops.
    val entries = remember(previousRows, currentRow, upcomingRows) {
        buildList {
            previousRows.forEach {
                add(QueueRowEntry(it, isCurrent = false, isPrevious = true, isActionable = false))
            }
            currentRow?.let {
                add(QueueRowEntry(it, isCurrent = true, isPrevious = false, isActionable = true))
            }
            upcomingRows.forEach {
                add(QueueRowEntry(it, isCurrent = false, isPrevious = false, isActionable = true))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row - fixed, not part of the vertically-scrolling list below.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_ROW_HEIGHT)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEachIndexed { index, column ->
                    Text(
                        text = column.name,
                        modifier = Modifier
                            .width(columnWidths.getOrElse(index) { MIN_COLUMN_WIDTH })
                            .padding(horizontal = COLUMN_HORIZONTAL_PADDING),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (columnCentered.getOrElse(index) { false }) TextAlign.Center else TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Rows scroll vertically - the server's window can hold up to
            // 100 songs (its own configured PlaylistSize), far more than
            // fits on screen at once. LazyColumn only composes/measures the
            // rows actually visible, so this stays cheap regardless of
            // window size.
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(
                    items = entries,
                    // current/upcoming: rowIndex is a stable key, excluding
                    // list position since that shifts for every surviving
                    // row whenever the window slides (PLAYLIST_ADVANCED) - a
                    // position-based key would make LazyColumn treat every
                    // row as "new" on every advance. previous/history:
                    // rowIndex-based keys have proven not crash-proof in
                    // practice (a duplicate rowIndex reached the UI at least
                    // twice despite dedup in QueueController.pushToPrevious -
                    // likely an ordering hazard between the multiple
                    // near-simultaneous responses the server can now send
                    // for one transition), so this region deliberately uses
                    // list position instead: it's always short (capped at
                    // MAX_PREVIOUS_ROWS_SHOWN), non-interactive, and doesn't
                    // need slide-stability, so position-based keys are both
                    // unconditionally collision-free and cost nothing here.
                    key = { index, entry ->
                        if (entry.isPrevious) {
                            "p-$index"
                        } else {
                            "${if (entry.isCurrent) "c" else "u"}-${entry.row.rowIndex}"
                        }
                    }
                ) { index, entry ->
                    QueueRow(
                        row = entry.row,
                        columnCount = columns.size,
                        columnWidths = columnWidths,
                        columnCentered = columnCentered,
                        isCurrent = entry.isCurrent,
                        isPrevious = entry.isPrevious,
                        isActionable = entry.isActionable,
                        zebraIndex = index,
                        scrollState = scrollState,
                        onDoubleTap = { onPlayRow(entry.row.rowIndex) },
                        // Add/remove require a valid token when the server has
                        // auth enabled - if the user bypassed or hasn't resolved
                        // it yet, tell them why rather than doing nothing. Only
                        // reachable for actionable rows in the first place -
                        // QueueRow doesn't attach the long-press gesture at all
                        // when isActionable is false (previous/history rows).
                        onLongPress = {
                            if (mutablePlaylistsEnabled) {
                                menuForRow = entry.row.rowIndex to entry.isCurrent
                            } else {
                                onMutationBlocked()
                            }
                        }
                    )
                }
            }
        }

        HorizontalScrollIndicators(canScrollBackward, canScrollForward)
    }

    // Top-level menu: exactly two options on the current row, one on upcoming rows.
    menuForRow?.let { (rowIndex, isCurrentRow) ->
        Box {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuForRow = null }
            ) {
                if (isCurrentRow) {
                    DropdownMenuItem(
                        text = { Text("Add to another playlist") },
                        onClick = {
                            menuForRow = null
                            showAddToPlaylistDialog = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from playlist") },
                    onClick = {
                        onRemoveRow(rowIndex)
                        menuForRow = null
                    }
                )
            }
        }
    }

    // Second step: a separate dialog listing the available target playlists.
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            excludePlaylistId = playlists.getOrNull(viewedPlaylistIndex)?.id,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectExisting = { playlistId ->
                showAddToPlaylistDialog = false
                onAddCurrentToPlaylist(playlistId, "")
            },
            onSelectNew = {
                showAddToPlaylistDialog = false
                showNewPlaylistDialog = true
            }
        )
    }

    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onConfirm = { name ->
                showNewPlaylistDialog = false
                if (name.isNotBlank()) {
                    onAddCurrentToPlaylist(0, name)
                }
            }
        )
    }
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistTab>,
    excludePlaylistId: Int?,
    onDismiss: () -> Unit,
    onSelectExisting: (Int) -> Unit,
    onSelectNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to another playlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                for (playlist in playlists) {
                    if (playlist.id == excludePlaylistId) continue
                    TextButton(
                        onClick = { onSelectExisting(playlist.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = playlist.name,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
                TextButton(
                    onClick = onSelectNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "New playlist...",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    row: QueueRowData,
    columnCount: Int,
    columnWidths: List<Dp>,
    columnCentered: List<Boolean>,
    isCurrent: Boolean,
    isPrevious: Boolean,
    isActionable: Boolean,
    zebraIndex: Int,
    scrollState: ScrollState,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val backgroundColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        zebraIndex % 2 == 0 -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }
    val textColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        isPrevious -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(backgroundColor)
            .horizontalScroll(scrollState)
            .then(
                if (isActionable) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = onDoubleTap,
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (col in 0 until columnCount) {
            val value = row.values.getOrElse(col) { "" }
            Text(
                text = value,
                modifier = Modifier
                    .width(columnWidths.getOrElse(col) { MIN_COLUMN_WIDTH })
                    .padding(horizontal = COLUMN_HORIZONTAL_PADDING),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (columnCentered.getOrElse(col) { false }) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor
            )
        }
    }
}

/**
 * Sizes each column based on its kind (ColumnInfo.isNumeric, the server's
 * own knowledge of the underlying Playlist::Column):
 * - Numeric columns (Track, Year, Length, PlayCount, ...) get a flat fixed
 *   width - their content is always short and bounded, so measuring them is
 *   wasted work, and a fixed width also stops the column visibly resizing
 *   as different rows with slightly different digit counts scroll by.
 * - Text columns (Title, Artist, Album, Genre, ...) are still sized to
 *   their widest currently-visible header/value (clamped to [MIN_COLUMN_
 *   WIDTH, MAX_COLUMN_WIDTH]), since their content varies a lot and
 *   benefits from being sized to what's actually there.
 * Any width left over after the fixed numeric columns are subtracted from
 * availableWidth is distributed proportionally across the text columns only,
 * so the table still fills the available width when there's slack.
 */
private fun computeColumnWidths(
    columns: List<ColumnInfo>,
    rows: List<QueueRowData>,
    availableWidth: Dp,
    textMeasurer: TextMeasurer,
    headerStyle: TextStyle,
    bodyStyle: TextStyle,
    density: Density
): List<Dp> {
    if (columns.isEmpty()) return emptyList()

    val clampedWidths = columns.mapIndexed { index, column ->
        if (column.isNumeric) {
            NUMERIC_COLUMN_WIDTH
        } else {
            var maxPx = textMeasurer.measure(column.name, headerStyle).size.width
            for (row in rows) {
                val value = row.values.getOrElse(index) { "" }
                if (value.isNotEmpty()) {
                    val widthPx = textMeasurer.measure(value, bodyStyle).size.width
                    if (widthPx > maxPx) maxPx = widthPx
                }
            }
            val natural = with(density) { maxPx.toDp() } + (COLUMN_HORIZONTAL_PADDING * 2)
            natural.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
        }
    }

    val totalFixed = columns.indices
        .filter { columns[it].isNumeric }
        .fold(0.dp) { acc, i -> acc + clampedWidths[i] }
    val totalCalculated = columns.indices
        .filter { !columns[it].isNumeric }
        .fold(0.dp) { acc, i -> acc + clampedWidths[i] }

    val extra = availableWidth - totalFixed - totalCalculated
    return if (extra > 0.dp && totalCalculated > 0.dp) {
        columns.indices.map { index ->
            if (columns[index].isNumeric) {
                clampedWidths[index]
            } else {
                clampedWidths[index] + extra * (clampedWidths[index] / totalCalculated)
            }
        }
    } else {
        clampedWidths
    }
}