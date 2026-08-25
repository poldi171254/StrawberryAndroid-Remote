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
import nw.remote.PlaylistRejectReason
import nw.remote.RequestAddSongToPlaylist
import nw.remote.RequestPlaySong
import nw.remote.RequestRemoveSongFromPlaylist
import nw.remote.ResponseAddSongToPlaylist
import nw.remote.ResponseRemoveSongFromPlaylist

/** One playlist tab, mirroring the Qt client's playlist_names_/playlist_ids_ pair. */
data class PlaylistTab(
    val id: Int,
    val name: String
)

/**
 * Owns playlist identity and selection only: the tab list, which playlist is
 * actually producing audio (active) vs. which one the queue view is showing
 * (viewed), and the playlist-mutating requests (play/add/remove). Row/column
 * content for whichever playlist is being viewed is QueueController's job -
 * this class only tells it, via [onViewedPlaylistChanged], when the viewed
 * playlist identity has changed; it never touches row/column state itself.
 *
 * [getToken] is consulted on every add/remove request rather than cached,
 * since the currently-held session token (owned by AuthController) can
 * change between requests - always send whatever's currently held, let the
 * server decide whether it's needed.
 */
class PlaylistController(
    private val sendMessage: (Message) -> Unit,
    private val getToken: () -> String,
    private val onViewedPlaylistChanged: (playlistId: Int) -> Unit
) {

    private val _playlists = MutableStateFlow<List<PlaylistTab>>(emptyList())
    val playlists: StateFlow<List<PlaylistTab>> = _playlists.asStateFlow()

    /** Index into playlists for whichever playlist is actually producing audio. -1 if none. */
    private val _activePlaylistIndex = MutableStateFlow(-1)
    val activePlaylistIndex: StateFlow<Int> = _activePlaylistIndex.asStateFlow()

    /** Index into playlists for whichever playlist the queue view is currently showing. -1 if none. */
    private val _viewedPlaylistIndex = MutableStateFlow(-1)
    val viewedPlaylistIndex: StateFlow<Int> = _viewedPlaylistIndex.asStateFlow()

    /** One-shot error surface for failed add/remove actions - UI shows as a snackbar and clears. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun consumeActionError() {
        _actionError.value = null
    }

    var activePlaylistId: Int = 0
        private set
    var hasActivePlaylist: Boolean = false
        private set
    var viewedPlaylistId: Int = 0
        private set
    var hasViewedPlaylist: Boolean = false
        private set

    /**
     * From MSG_TYPE_RESPONSE_INITIAL_INFO - the one-time initial playlist
     * snapshot. Takes already-mapped tabs and an already-resolved active id
     * (rather than the raw protobuf list) so this class never needs to know
     * the exact generated protobuf type for a playlist entry.
     */
    fun applyInitialPlaylists(tabs: List<PlaylistTab>, activePlaylistId: Int?) {
        _playlists.value = tabs
        hasActivePlaylist = activePlaylistId != null
        _activePlaylistIndex.value = if (activePlaylistId != null) {
            tabs.indexOfFirst { it.id == activePlaylistId }
        } else {
            -1
        }
        if (activePlaylistId != null) {
            this.activePlaylistId = activePlaylistId
            viewPlaylist(activePlaylistId)
        }
    }

    /** From MSG_TYPE_PLAYLIST_ACTIVATED - follow the newly-active playlist, mirroring the Qt client. */
    fun onPlaylistActivated(playlistId: Int) {
        activePlaylistId = playlistId
        hasActivePlaylist = true
        _activePlaylistIndex.value = _playlists.value.indexOfFirst { it.id == playlistId }
        viewPlaylist(playlistId)
    }

    /** User tapped a different playlist tab. */
    fun selectTab(index: Int) {
        val tabs = _playlists.value
        if (index < 0 || index >= tabs.size) return
        viewPlaylist(tabs[index].id)
    }

    private fun viewPlaylist(playlistId: Int) {
        viewedPlaylistId = playlistId
        hasViewedPlaylist = true
        _viewedPlaylistIndex.value = _playlists.value.indexOfFirst { it.id == playlistId }
        onViewedPlaylistChanged(playlistId)
    }

    /** rowIndex must come from a current/upcoming row - never from a previous/history row. */
    fun requestPlaySong(rowIndex: Int) {
        if (!hasViewedPlaylist) return
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_PLAY_SONG)
            .setRequestPlaySong(
                RequestPlaySong.newBuilder()
                    .setPlaylistId(viewedPlaylistId)
                    .setRowIndex(rowIndex)
                    .build()
            )
            .build()
        sendMessage(request)
    }

    /**
     * Adds the currently-playing song to another playlist. If newPlaylistName
     * is non-empty the server creates a new playlist and ignores
     * targetPlaylistId. Always includes the currently-held session token
     * (empty string if none) - the server decides whether it's needed.
     */
    fun addCurrentSongToPlaylist(targetPlaylistId: Int, newPlaylistName: String = "") {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_ADD_SONG_TO_PLAYLIST)
            .setRequestAddSongToPlaylist(
                RequestAddSongToPlaylist.newBuilder()
                    .setTargetPlaylistId(targetPlaylistId)
                    .setNewPlaylistName(newPlaylistName)
                    .setToken(getToken())
                    .build()
            )
            .build()
        sendMessage(request)
    }

    /** rowIndex must come from a current/upcoming row - never from a previous/history row. */
    fun removeSongFromPlaylist(rowIndex: Int) {
        if (!hasViewedPlaylist) return
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_REMOVE_SONG_FROM_PLAYLIST)
            .setRequestRemoveSongFromPlaylist(
                RequestRemoveSongFromPlaylist.newBuilder()
                    .setPlaylistId(viewedPlaylistId)
                    .setRowIndex(rowIndex)
                    .setToken(getToken())
                    .build()
            )
            .build()
        sendMessage(request)
    }

    /**
     * Returns the reject reason only when it's a token-related one, so the
     * caller can forward it to AuthController - this class surfaces every
     * other rejection (or plain "not accepted") as its own generic
     * actionError, unchanged from before the token feature existed.
     */
    fun onResponseAddSongToPlaylist(response: ResponseAddSongToPlaylist): PlaylistRejectReason? {
        if (response.accepted) return null
        return if (isTokenReason(response.rejectReason)) {
            response.rejectReason
        } else {
            _actionError.value = "Failed to add song to playlist"
            null
        }
    }

    fun onResponseRemoveSongFromPlaylist(response: ResponseRemoveSongFromPlaylist): PlaylistRejectReason? {
        if (response.accepted) return null
        return if (isTokenReason(response.rejectReason)) {
            response.rejectReason
        } else {
            _actionError.value = "Failed to remove song from playlist"
            null
        }
    }

    private fun isTokenReason(reason: PlaylistRejectReason): Boolean =
        reason == PlaylistRejectReason.PLAYLIST_REJECT_TOKEN_REQUIRED ||
            reason == PlaylistRejectReason.PLAYLIST_REJECT_TOKEN_MISMATCH

    /** Called on disconnect - a fresh connect starts with a clean slate. */
    fun reset() {
        _playlists.value = emptyList()
        _activePlaylistIndex.value = -1
        _viewedPlaylistIndex.value = -1
        activePlaylistId = 0
        hasActivePlaylist = false
        viewedPlaylistId = 0
        hasViewedPlaylist = false
    }
}
