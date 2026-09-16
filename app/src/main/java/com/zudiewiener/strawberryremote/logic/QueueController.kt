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
package com.zudiewiener.strawberryremote.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.RequestPlaylistSongs
import nw.remote.ResponsePlaylistSongs

/**
 * One visible column's identity. is_numeric is the server's own knowledge of
 * the underlying Playlist::Column (Track, Year, Length, PlayCount etc. are
 * numeric/measurement-like; Title, Artist, Album, Genre etc. are text) - used
 * to decide left vs. center alignment without guessing from formatted content.
 */
data class ColumnInfo(
    val name: String,
    val isNumeric: Boolean = false
)

/**
 * One row of the queue view - either the current/last-played row or an
 * upcoming song. rowIndex is the absolute position within the playlist,
 * needed for RequestPlaySong / RequestRemoveSongFromPlaylist. Rows kept in
 * local previous/history do NOT get a meaningful rowIndex re-sent to the
 * server (same staleness reasoning as the Qt client): callers should only
 * offer play/remove actions on current/upcoming rows.
 */
data class QueueRowData(
    val values: List<String> = emptyList(),
    val rowIndex: Int = 0
)

/**
 * Everything a screen needs to render the queue view, as one value rather
 * than four independent pieces. Deliberately NOT four separate StateFlows
 * (columns/previousRows/currentRow/upcomingRows each on their own): a
 * collector observing four independent flows has no guarantee of ever
 * seeing all four at a mutually consistent moment. Bundling into one atomic
 * StateFlow<QueueState>, updated with a single assignment per transition,
 * makes that inconsistency structurally impossible: any collector always
 * sees one complete, self-consistent snapshot.
 */
data class QueueState(
    val columns: List<ColumnInfo> = emptyList(),
    val previousRows: List<QueueRowData> = emptyList(),
    val currentRow: QueueRowData? = null,
    val upcomingRows: List<QueueRowData> = emptyList()
)

/**
 * Owns the queue/row content for whichever playlist is currently being
 * viewed: columns, previous/current/upcoming rows, and the
 * RequestPlaylistSongs request/response cycle. Has no opinion on which
 * playlist that is or how it got selected - callers (SharedViewModel /
 * PlaylistController via callback) always pass the playlist id explicitly,
 * keeping this class from needing to know about playlist selection at all.
 */
class QueueController(private val sendMessage: (Message) -> Unit) {

    companion object {
        private const val MAX_PREVIOUS_ROWS = 50

        // Exact display string Strawberry sends for this column (from
        // Playlist::column_name() server-side) - not a stable identifier in
        // the protocol, just the visible header text, so this match is
        // inherently a little fragile (silently stops working if the column
        // is ever renamed/relocalized, with no compile-time signal). A
        // future protocol round could add a proper column-kind enum to
        // ColumnInfo so clients don't have to match on display text.
        private const val PLAY_COUNT_COLUMN_NAME = "Play Count"
    }

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    /** Called when the viewed playlist identity changes - old rows/columns no longer apply. */
    fun resetView() {
        _queueState.value = QueueState()
    }

    /**
     * Appends a row to a previousRows list, capped at MAX_PREVIOUS_ROWS.
     * Removes any existing entry with the same rowIndex first - previousRows
     * represents "recently played" and should never show the same absolute
     * playlist position twice, whether from a legitimate replay (user jumps
     * back to an earlier row) or two near-simultaneous transitions both
     * reporting the same outgoing row. Pure function (takes and returns a
     * list) rather than mutating state directly, since every call site now
     * needs to fold its result into one single QueueState assignment
     * alongside other field changes from the same transition.
     */
    private fun pushToPrevious(rows: List<QueueRowData>, row: QueueRowData): List<QueueRowData> {
        val deduplicated = rows.filterNot { it.rowIndex == row.rowIndex }
        val updated = deduplicated + row
        return if (updated.size > MAX_PREVIOUS_ROWS) updated.takeLast(MAX_PREVIOUS_ROWS) else updated
    }

    /**
     * Bumps the Play Count column of a row that's about to become "previous"
     * by one, to match what the server-side player itself just did by
     * finishing that song naturally. Applied at every site that pushes a row
     * into previousRows: the row being pushed is always our locally-cached
     * copy of what was current a moment ago, never something freshly
     * re-fetched in the same message that reports the transition (a full
     * resend's row list only contains the *new* current/upcoming rows, not
     * the one that just fell out of the window) - so this staleness applies
     * equally whether the transition arrived via PLAYLIST_ADVANCED or a full
     * ResponsePlaylistSongs resend. Best-effort: if there's no Play Count
     * column, or its current value doesn't parse as a plain integer (e.g. an
     * unexpected locale-formatted thousands separator), the row is returned
     * unchanged rather than guessing - the next full resend will correct the
     * displayed count regardless.
     */
    private fun incrementPlayCount(row: QueueRowData, columns: List<ColumnInfo>): QueueRowData {
        val columnIndex = columns.indexOfFirst { it.name == PLAY_COUNT_COLUMN_NAME }
        if (columnIndex < 0) return row
        val currentValue = row.values.getOrNull(columnIndex)?.toIntOrNull() ?: return row
        val updatedValues = row.values.toMutableList().apply {
            this[columnIndex] = (currentValue + 1).toString()
        }
        return row.copy(values = updatedValues)
    }

    /**
     * The client's only proactive request-driven use of this: the initial
     * fetch when a playlist is first displayed (new connection, tab switch,
     * or following a newly-activated playlist). After that, staying in sync
     * is entirely server-pushed - see onPlaylistAdvanced() and
     * onResponsePlaylistSongs() (the latter also serves as the full-resend
     * target for human-initiated changes). The request no longer specifies a
     * count - the server's own configured PlaylistSize setting is now the
     * sole source of truth for the window size, so there was nothing left
     * for the client to usefully ask for.
     */
    fun requestPlaylistSongs(playlistId: Int) {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_PLAYLIST_SONGS)
            .setRequestPlaylistSongs(
                RequestPlaylistSongs.newBuilder()
                    .setPlaylistId(playlistId)
                    .build()
            )
            .build()
        sendMessage(request)
    }

    fun onResponsePlaylistSongs(playlistSongs: ResponsePlaylistSongs, viewedPlaylistId: Int?) {
        if (viewedPlaylistId == null || playlistSongs.playlistId != viewedPlaylistId) {
            // Stale response for a playlist we've since navigated away from,
            // or no playlist currently being viewed at all.
            return
        }

        val current = _queueState.value

        val newColumns = playlistSongs.columnsList.map {
            ColumnInfo(name = it.name, isNumeric = it.isNumeric)
        }
        val columnsChanged = newColumns != current.columns

        val rows = playlistSongs.rowsList
        val newCurrent: QueueRowData? = if (rows.isNotEmpty()) {
            QueueRowData(rows[0].valuesList, rows[0].rowIndex)
        } else null
        val newUpcoming = if (rows.size > 1) {
            rows.drop(1).map { QueueRowData(it.valuesList, it.rowIndex) }
        } else emptyList()

        // Visible columns changing on the desktop mid-session means old
        // cached rows no longer line up against new headers - but the
        // outgoing current row itself is unaffected by that, so it can still
        // be pushed into the (now-cleared) history below in the same update.
        val basePreviousRows = if (columnsChanged) emptyList() else current.previousRows
        val oldCurrent = current.currentRow
        val valuesChanged = oldCurrent != null && oldCurrent.values != newCurrent?.values

        val newPreviousRows = if (valuesChanged) {
            pushToPrevious(basePreviousRows, incrementPlayCount(oldCurrent!!, current.columns))
        } else {
            basePreviousRows
        }

        // Single assignment for the whole transition - see QueueState's
        // doc comment for why this matters.
        _queueState.value = QueueState(
            columns = newColumns,
            previousRows = newPreviousRows,
            currentRow = newCurrent,
            upcomingRows = newUpcoming
        )
    }

    /**
     * PLAYLIST_ADVANCED: the one common, high-frequency case where a song
     * finishes naturally and the next row starts playing automatically.
     * Incrementally slides the local window rather than waiting for a full
     * resend - the promoted row is expected to already be the top of
     * upcomingRows (per the protocol's guarantee that it was already in the
     * client's cached window a moment ago).
     */
    fun onPlaylistAdvanced(
        playlistId: Int,
        newCurrentRow: Int,
        trailingRow: QueueRowData?,
        viewedPlaylistId: Int?
    ) {
        if (viewedPlaylistId == null || playlistId != viewedPlaylistId) return

        val current = _queueState.value
        val upcoming = current.upcomingRows
        val promotedIndex = upcoming.indexOfFirst { it.rowIndex == newCurrentRow }
        if (promotedIndex < 0) {
            // The advanced-to row wasn't in our cached window - local state
            // has drifted from the server's for some reason. Rather than
            // guess, ask for a fresh full window; the server is always the
            // source of truth (see onResponsePlaylistSongs).
            requestPlaylistSongs(playlistId)
            return
        }

        val newPreviousRows = current.currentRow?.let { oldCurrent ->
            pushToPrevious(current.previousRows, incrementPlayCount(oldCurrent, current.columns))
        } ?: current.previousRows

        val newCurrentRowData = upcoming[promotedIndex]
        val remaining = upcoming.subList(promotedIndex + 1, upcoming.size)
        val newUpcoming = if (trailingRow != null) remaining + trailingRow else remaining

        // Single assignment for the whole transition - see QueueState's
        // doc comment for why this matters.
        _queueState.value = current.copy(
            previousRows = newPreviousRows,
            currentRow = newCurrentRowData,
            upcomingRows = newUpcoming
        )
    }
}