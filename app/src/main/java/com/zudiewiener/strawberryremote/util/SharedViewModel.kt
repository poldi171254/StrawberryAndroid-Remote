
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
package com.zudiewiener.strawberryremote.util

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zudiewiener.strawberryremote.data.ConnectionConfig
import com.zudiewiener.strawberryremote.logic.AuthController
import com.zudiewiener.strawberryremote.logic.ColumnInfo
import com.zudiewiener.strawberryremote.logic.PlayerStatusController
import com.zudiewiener.strawberryremote.logic.PlaylistController
import com.zudiewiener.strawberryremote.logic.PlaylistTab
import com.zudiewiener.strawberryremote.logic.QueueController
import com.zudiewiener.strawberryremote.logic.QueueRowData
import com.zudiewiener.strawberryremote.net.ConnectionState
import com.zudiewiener.strawberryremote.net.StrawberryConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nw.remote.EngineState
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.ReasonDisconnect
import nw.remote.RequestConnect
import nw.remote.RequestInitialInfo
import nw.remote.RequestSongMetadata
import java.io.File

/**
 * Coordinates the connection and dispatches every incoming [Message] to
 * whichever controller(s) it concerns, but no longer holds any of the
 * message-meaning business logic directly: playlist identity/selection lives
 * in [PlaylistController], queue row/column content in [QueueController],
 * player status/countdown in [PlayerStatusController], and the auth/token
 * state machine in [AuthController]. This class owns instances of all four
 * plus the raw [StrawberryConnection], wires them together where they need
 * to collaborate (e.g. "auth resolved" -> "now request initial info"), and
 * re-exposes their StateFlows for the UI to collect.
 */
class SharedViewModel(application: Application) : AndroidViewModel(application) {

    private val configFile = File(application.filesDir, "connection.cfg")

    private val connection = StrawberryConnection(viewModelScope)

    private val authController = AuthController(
        sendMessage = { sendMessage(it) },
        onConnectHandshakeReady = { finishConnectHandshake() }
    )

    private val queueController = QueueController(
        sendMessage = { sendMessage(it) }
    )

    private val playlistController = PlaylistController(
        sendMessage = { sendMessage(it) },
        getToken = { authController.currentToken },
        onViewedPlaylistChanged = { playlistId ->
            // Only proactive fetch left in the queue lifecycle: an initial
            // window for whatever playlist just became viewed (new
            // connection, tab switch, or following a newly-activated
            // playlist). Everything after this is server-pushed - see
            // MSG_TYPE_PLAYLIST_ADVANCED and the full-resend cases below.
            queueController.resetView()
            queueController.requestPlaylistSongs(playlistId)
        }
    )

    private val playerStatusController = PlayerStatusController(viewModelScope)

    // --- Re-exported state ---

    /** Re-exported directly from StrawberryConnection - this class only ever reads it. */
    val connectionState: StateFlow<ConnectionState> = connection.connectionState

    /**
     * True only when the server told us it is shutting down (Strawberry was
     * closed on the desktop). Other disconnects - version rejection, network
     * loss, too-many-failed-attempts - leave this false, so the UI can react
     * differently: there is nothing to reconnect to after a shutdown.
     */
    private val _serverShutdown = MutableStateFlow(false)
    val serverShutdown: StateFlow<Boolean> = _serverShutdown.asStateFlow()

    /**
     * Non-null for connection errors the user can't fix by retrying (e.g. an
     * incompatible server protocol version) - distinct from ordinary
     * ConnectionState.Error, which covers retryable problems like a wrong IP
     * or a refused connection and is shown as a transient snackbar instead.
     */
    private val _fatalConnectionError = MutableStateFlow<String?>(null)
    val fatalConnectionError: StateFlow<String?> = _fatalConnectionError.asStateFlow()

    private val _savedConfig = MutableStateFlow<ConnectionConfig?>(null)
    val savedConfig: StateFlow<ConnectionConfig?> = _savedConfig.asStateFlow()

    val playerStatus: StateFlow<String> = playerStatusController.playerStatus
    val remainingTime: StateFlow<String> = playerStatusController.remainingTime

    val playlists: StateFlow<List<PlaylistTab>> = playlistController.playlists
    val activePlaylistIndex: StateFlow<Int> = playlistController.activePlaylistIndex
    val viewedPlaylistIndex: StateFlow<Int> = playlistController.viewedPlaylistIndex
    val actionError: StateFlow<String?> = playlistController.actionError

    val columns: StateFlow<List<ColumnInfo>> = queueController.columns
    val previousRows: StateFlow<List<QueueRowData>> = queueController.previousRows
    val currentRow: StateFlow<QueueRowData?> = queueController.currentRow
    val upcomingRows: StateFlow<List<QueueRowData>> = queueController.upcomingRows

    /** Null hides the token prompt entirely. See AuthController.TokenPromptState for the three visible states. */
    val tokenPrompt: StateFlow<AuthController.TokenPromptState?> = authController.tokenPrompt

    /** False disables add/remove UI (the server rejects those requests regardless; this just avoids a round trip). */
    val mutablePlaylistsEnabled: StateFlow<Boolean> = authController.mutablePlaylistsEnabled

    fun consumeActionError() {
        playlistController.consumeActionError()
    }

    init {
        loadConfig()

        // Every parsed incoming message, from the initial connection through
        // any number of silent reconnects, flows through here.
        viewModelScope.launch {
            connection.incomingMessages.collect { message ->
                processResponse(message)
            }
        }

        viewModelScope.launch {
            connection.connectionState.collect { state ->
                when (state) {
                    // The handshake is business logic (needs a specific
                    // client-name payload) so it's sent from here, triggered
                    // whenever the connection reaches a fresh
                    // TCP-connected-but-not-yet-handshaked state - true for
                    // both a brand new connect() and every successful silent
                    // reconnect.
                    is ConnectionState.Connected -> sendHandshake()

                    // A fresh connect (or a drop that gave up reconnecting)
                    // starts every controller from a clean slate.
                    is ConnectionState.Disconnected -> {
                        authController.reset()
                        playlistController.reset()
                    }
                    else -> Unit
                }
            }
        }
    }

    // --- Config ---

    private fun loadConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!configFile.exists()) return@launch
                val content = configFile.readText().trim()
                val parts = content.split(":")
                if (parts.size != 2) return@launch
                val ip = parts[0]
                val port = parts[1].toIntOrNull() ?: return@launch
                _savedConfig.value = ConnectionConfig(ip, port)
                Log.d("SharedViewModel", "Config loaded: $ip:$port")
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error loading config: ${e.message}")
            }
        }
    }

    private fun saveConfig(ip: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                configFile.writeText("$ip:$port")
                _savedConfig.value = ConnectionConfig(ip, port)
                Log.d("SharedViewModel", "Config saved: $ip:$port")
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error saving config: ${e.message}")
            }
        }
    }

    // --- Connection ---

    fun connect(ip: String, port: Int) {
        _serverShutdown.value = false
        saveConfig(ip, port)
        connection.connect(ip, port)
    }

    fun disconnect() {
        playerStatusController.stopCountdown()
        connection.disconnect()
    }

    /**
     * Used by the token prompt's Cancel button and by the LockedOut prompt's
     * OK button - both mean "leave the client", and both disconnect cleanly
     * first. The UI itself calls Activity.finish() right after, matching the
     * app's existing Exit-button convention elsewhere (see ConnectScreen /
     * SongInfoScreen).
     */
    fun exitAfterTokenPrompt() {
        disconnect()
    }

    fun submitToken(candidate: String) {
        authController.submitToken(candidate)
    }

    fun bypassToken() {
        authController.bypassToken()
    }

    fun dismissInvalidToken() {
        authController.dismissInvalid()
    }

    /**
     * First message on a new connection. The server validates the protocol
     * version carried on the message and replies with ResponseConnect, or
     * disconnects us with a reason.
     */
    private fun sendHandshake() {
        val clientName = "Strawberry Remote (${Build.MANUFACTURER} ${Build.MODEL})"
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_CONNECT)
            .setRequestConnect(
                RequestConnect.newBuilder().setClientName(clientName).build()
            )
            .build()
        sendMessage(request)
    }

    /** Called by AuthController once auth is resolved (or wasn't required) after connect. */
    private fun finishConnectHandshake() {
        connection.markReady()
        // The one and only initial request, sent once the server has
        // accepted us and any required auth is resolved - bundles song info,
        // playlists, and engine state.
        requestInitialInfo()
    }

    fun requestSongInfo() {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_SONG_INFO)
            .setRequestSongMetadata(
                RequestSongMetadata.newBuilder().setSend(true).build()
            )
            .build()
        sendMessage(request)
    }

    private fun requestInitialInfo() {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_INITIAL_INFO)
            .setRequestInitialInfo(
                RequestInitialInfo.newBuilder().setSend(true).build()
            )
            .build()
        sendMessage(request)
    }

    // --- Playlists / queue ---
    // Thin pass-throughs so screens keep a single ViewModel reference rather
    // than needing to know PlaylistController/QueueController exist.

    fun requestPlaySong(rowIndex: Int) {
        playlistController.requestPlaySong(rowIndex)
    }

    fun addCurrentSongToPlaylist(targetPlaylistId: Int, newPlaylistName: String = "") {
        playlistController.addCurrentSongToPlaylist(targetPlaylistId, newPlaylistName)
    }

    fun removeSongFromPlaylist(rowIndex: Int) {
        playlistController.removeSongFromPlaylist(rowIndex)
    }

    fun selectPlaylistTab(index: Int) {
        playlistController.selectTab(index)
    }

    // --- Messaging ---

    fun sendMessage(message: Message) {
        connection.sendMessage(message)
    }

    private fun processResponse(response: Message) {
        when (response.type) {
            MsgType.MSG_TYPE_RESPONSE_CONNECT -> {
                if (response.responseConnect.accepted) {
                    if (response.version < ProtocolConstants.MIN_SUPPORTED_VERSION) {
                        // The server accepted us (its own check only rejects
                        // clients too OLD for it), but its own protocol
                        // version is older than what this app needs for the
                        // playlist/queue features to work at all. Not
                        // something the user can fix by retrying - surface it
                        // as a fatal error rather than a transient snackbar.
                        Log.w(
                            "SharedViewModel",
                            "Server protocol version ${response.version} is older than " +
                                    "the minimum this app supports (${ProtocolConstants.MIN_SUPPORTED_VERSION})"
                        )
                        _fatalConnectionError.value =
                            "This version of Strawberry's Network Remote is too old for this app. Please update Strawberry."
                        connection.disconnect()
                        return
                    }
                    Log.d(
                        "SharedViewModel",
                        "Handshake accepted, server protocol version ${response.version}"
                    )
                    // AuthController decides whether to proceed straight to
                    // finishConnectHandshake() or hold off for the token
                    // prompt - either way it calls back into
                    // onConnectHandshakeReady() when it's safe to continue.
                    authController.onResponseConnect(response.responseConnect.authEnabled)
                } else {
                    connection.setError("Server refused the connection")
                }
            }
            MsgType.MSG_TYPE_REPLY_SONG_INFO -> {
                playerStatusController.applySongMetadata(response.responseSongMetadata)
            }
            MsgType.MSG_TYPE_RESPONSE_INITIAL_INFO -> {
                val initialInfo = response.responseInitialInfo
                playerStatusController.applySongMetadata(initialInfo.songInfo)

                val tabs = initialInfo.playlists.playlistsList.map {
                    PlaylistTab(id = it.id, name = it.name)
                }
                val activeId = initialInfo.playlists.playlistsList.firstOrNull { it.isCurrent }?.id
                playlistController.applyInitialPlaylists(tabs, activeId)
                // applyInitialPlaylists() already triggers the initial queue
                // fetch via onViewedPlaylistChanged if there's an active
                // playlist to follow - nothing further needed here.
            }
            MsgType.MSG_TYPE_RESPONSE_PLAYLIST_SONGS -> {
                queueController.onResponsePlaylistSongs(
                    response.responsePlaylistSongs,
                    if (playlistController.hasViewedPlaylist) playlistController.viewedPlaylistId else null
                )
            }
            MsgType.MSG_TYPE_PLAYLIST_ADVANCED -> {
                // The common, high-frequency case: a song finished naturally
                // and the next row in the playlist started automatically.
                // trailing_row is a proto3 message-type field, so presence is
                // checked via hasTrailingRow() rather than a sentinel value.
                val advanced = response.playlistAdvanced
                val trailingRow = if (advanced.hasTrailingRow()) {
                    QueueRowData(advanced.trailingRow.valuesList, advanced.trailingRow.rowIndex)
                } else {
                    null
                }
                queueController.onPlaylistAdvanced(
                    advanced.playlistId,
                    advanced.newCurrentRow,
                    trailingRow,
                    if (playlistController.hasViewedPlaylist) playlistController.viewedPlaylistId else null
                )
            }
            MsgType.MSG_TYPE_RESPONSE_PLAY_SONG -> {
                // No client action needed on success: jump-to-song is a
                // human-initiated change, so the server automatically pushes
                // a full ResponsePlaylistSongs resend for it - the queue
                // updates itself via MSG_TYPE_RESPONSE_PLAYLIST_SONGS below.
                // If the playlist wasn't previously active, PLAYLIST_ACTIVATED
                // also fires and follows it (see that case below).
            }
            MsgType.MSG_TYPE_PLAYLIST_ACTIVATED -> {
                val activated = response.playlistActivated
                // onPlaylistActivated() follows the newly-active playlist
                // (mirroring the Qt client) and its internal viewPlaylist()
                // call already triggers the initial queue fetch via
                // onViewedPlaylistChanged - nothing further needed here.
                playlistController.onPlaylistActivated(activated.playlistId)
            }
            MsgType.MSG_TYPE_PLAYLIST_CHANGED -> {
                // No client action: the server's automatic full-resend
                // (RESPONSE_PLAYLIST_SONGS, human-initiated changes) and
                // PLAYLIST_ADVANCED (natural progression) are the sole
                // sources of truth for the queue now. Reacting to this too
                // (as a "fallback") was producing a third near-simultaneous
                // response for the same transition, which was implicated in
                // an ordering hazard that corrupted the local previous/
                // current/upcoming window - removed rather than kept as a
                // defensive fallback.
            }
            MsgType.MSG_TYPE_RESPONSE_ADD_SONG_TO_PLAYLIST -> {
                playlistController.onResponseAddSongToPlaylist(response.responseAddSongToPlaylist)
                    ?.let { reason -> authController.onTokenRejected(reason) }
                // On success, no direct action needed here: add/remove is a
                // human-initiated change to playlist contents, so the server
                // automatically pushes a full ResponsePlaylistSongs resend
                // for whichever playlist(s) it affects.
            }
            MsgType.MSG_TYPE_RESPONSE_REMOVE_SONG_FROM_PLAYLIST -> {
                playlistController.onResponseRemoveSongFromPlaylist(response.responseRemoveSongFromPlaylist)
                    ?.let { reason -> authController.onTokenRejected(reason) }
            }
            MsgType.MSG_TYPE_RESPONSE_VALIDATE_TOKEN -> {
                authController.onValidateTokenResponse(response.responseValidateToken.valid)
            }
            MsgType.MSG_TYPE_AUTH_STATUS_CHANGED -> {
                authController.onAuthStatusChanged(response.authStatusChanged.authEnabled)
            }
            MsgType.MSG_TYPE_ENGINE_STATE_CHANGE -> {
                when (response.engineStateChange.state) {
                    EngineState.ENGINE_STATE_PLAYING -> {
                        playerStatusController.setPlaying()
                        // Requesting song info here restarts the countdown with
                        // a fresh position, which is also how a desktop seek resyncs.
                        requestSongInfo()
                        // No queue re-fetch needed: a natural song advance is
                        // exactly what MSG_TYPE_PLAYLIST_ADVANCED handles
                        // incrementally now, and any other cause of a play
                        // state change arrives via a full resend instead.
                    }
                    EngineState.ENGINE_STATE_PAUSED -> playerStatusController.setPaused()
                    else -> playerStatusController.setStopped()
                }
            }
            MsgType.MSG_TYPE_DISCONNECT -> {
                playerStatusController.stopCountdown()
                val reason = response.requestDisconnect.reasonDisconnect
                if (reason == ReasonDisconnect.REASON_DISCONNECT_TOO_MANY_FAILED_ATTEMPTS) {
                    // Takes priority over the generic disconnect banner: an
                    // explicit "locked out" step with its own OK, matching
                    // the token prompt's other terminal states.
                    authController.onLockedOut()
                }
                val text = when (reason) {
                    ReasonDisconnect.REASON_DISCONNECT_VERSION_MISMATCH ->
                        "Server rejected this app: protocol version too old"
                    ReasonDisconnect.REASON_DISCONNECT_UNKNOWN_MSGTYPE ->
                        "Server rejected an unsupported request"
                    ReasonDisconnect.REASON_DISCONNECT_NO_HANDSHAKE ->
                        "Server rejected this app: handshake missing"
                    ReasonDisconnect.REASON_DISCONNECT_SERVER_SHUTDOWN ->
                        "Strawberry has been closed on the desktop."
                    ReasonDisconnect.REASON_DISCONNECT_TOO_MANY_FAILED_ATTEMPTS ->
                        "Disconnected: too many failed authentication attempts."
                    else -> "Server closed the connection"
                }
                Log.w("SharedViewModel", text)
                if (reason == ReasonDisconnect.REASON_DISCONNECT_SERVER_SHUTDOWN) {
                    _serverShutdown.value = true
                }
                connection.setError(text)
            }
            else -> Log.d("SharedViewModel", "Unhandled response type: ${response.type}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerStatusController.stopCountdown()
        connection.shutdown()
    }
}