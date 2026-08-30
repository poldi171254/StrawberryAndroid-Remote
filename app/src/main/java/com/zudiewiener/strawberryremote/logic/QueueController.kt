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
    }

    private val _columns = MutableStateFlow<List<ColumnInfo>>(emptyList())
    val columns: StateFlow<List<ColumnInfo>> = _columns.asStateFlow()

    private val _previousRows = MutableStateFlow<List<QueueRowData>>(emptyList())
    val previousRows: StateFlow<List<QueueRowData>> = _previousRows.asStateFlow()

    private val _currentRow = MutableStateFlow<QueueRowData?>(null)
    val currentRow: StateFlow<QueueRowData?> = _currentRow.asStateFlow()

    private val _upcomingRows = MutableStateFlow<List<QueueRowData>>(emptyList())
    val upcomingRows: StateFlow<List<QueueRowData>> = _upcomingRows.asStateFlow()

    /** Called when the viewed playlist identity changes - old rows/columns no longer apply. */
    fun resetView() {
        _previousRows.value = emptyList()
        _currentRow.value = null
        _upcomingRows.value = emptyList()
        _columns.value = emptyList()
    }

    /**
     * Appends a row to previousRows, capped at MAX_PREVIOUS_ROWS. Removes any
     * existing entry with the same rowIndex first - previousRows represents
     * "recently played" and should never show the same absolute playlist
     * position twice, whether from a legitimate replay (user jumps back to
     * an earlier row) or two near-simultaneous transitions both reporting
     * the same outgoing row. Kept as defense-in-depth even after removing
     * the redundant PLAYLIST_CHANGED-triggered re-fetch (see SharedViewModel)
     * that was the main source of these near-simultaneous responses.
     */
    private fun pushToPrevious(row: QueueRowData) {
        val deduplicated = _previousRows.value.filterNot { it.rowIndex == row.rowIndex }
        val updated = deduplicated + row
        _previousRows.value = if (updated.size > MAX_PREVIOUS_ROWS) {
            updated.takeLast(MAX_PREVIOUS_ROWS)
        } else {
            updated
        }
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

        val newColumns = playlistSongs.columnsList.map {
            ColumnInfo(name = it.name, isNumeric = it.isNumeric)
        }
        if (newColumns != _columns.value) {
            // Visible columns changed on the desktop mid-session: old cached
            // rows would no longer line up against new headers.
            _previousRows.value = emptyList()
            _columns.value = newColumns
        }

        val rows = playlistSongs.rowsList
        val newCurrent: QueueRowData? = if (rows.isNotEmpty()) {
            QueueRowData(rows[0].valuesList, rows[0].rowIndex)
        } else null
        val newUpcoming = if (rows.size > 1) {
            rows.drop(1).map { QueueRowData(it.valuesList, it.rowIndex) }
        } else emptyList()

        val oldCurrent = _currentRow.value
        if (oldCurrent != null && oldCurrent.values != newCurrent?.values) {
            pushToPrevious(oldCurrent)
        }

        _currentRow.value = newCurrent
        _upcomingRows.value = newUpcoming
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

        val upcoming = _upcomingRows.value
        val promotedIndex = upcoming.indexOfFirst { it.rowIndex == newCurrentRow }
        if (promotedIndex < 0) {
            // The advanced-to row wasn't in our cached window - local state
            // has drifted from the server's for some reason. Rather than
            // guess, ask for a fresh full window; the server is always the
            // source of truth (see onResponsePlaylistSongs).
            requestPlaylistSongs(playlistId)
            return
        }

        _currentRow.value?.let { oldCurrent ->
            pushToPrevious(oldCurrent)
        }

        _currentRow.value = upcoming[promotedIndex]
        val remaining = upcoming.subList(promotedIndex + 1, upcoming.size)
        _upcomingRows.value = if (trailingRow != null) remaining + trailingRow else remaining
    }
}