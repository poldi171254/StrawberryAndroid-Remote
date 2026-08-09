package com.zudiewiener.strawberryremote.util

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zudiewiener.strawberryremote.data.ConnectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nw.remote.EngineState
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.PlayerState
import nw.remote.ReasonDisconnect
import nw.remote.RequestAddSongToPlaylist
import nw.remote.RequestConnect
import nw.remote.RequestInitialInfo
import nw.remote.RequestPlaySong
import nw.remote.RequestPlaylistSongs
import nw.remote.RequestRemoveSongFromPlaylist
import nw.remote.RequestSongMetadata
import nw.remote.ResponseSongMetadata
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()

    /** TCP connection established; handshake sent, waiting for the server to accept. */
    object Connected : ConnectionState()

    /** Handshake accepted by the server - the remote is usable. */
    object Ready : ConnectionState()

    data class Error(val message: String) : ConnectionState()
}

/** One playlist tab, mirroring the Qt client's playlist_names_/playlist_ids_ pair. */
data class PlaylistTab(
    val id: Int,
    val name: String
)

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

class SharedViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Used only until the UI's first BoxWithConstraints measurement lands
        // and calls refreshVisibleQueue() with the real, screen-derived count.
        private const val INITIAL_UPCOMING_COUNT = 10
        private const val MAX_PREVIOUS_ROWS = 50
    }

    private val configFile = File(application.filesDir, "connection.cfg")

    private var _socket: Socket? = null
    private var readerJob: Job? = null
    private var countdownJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /**
     * True only when the server told us it is shutting down (Strawberry was
     * closed on the desktop). Other disconnects - version rejection, network
     * loss - leave this false, so the UI can react differently: there is
     * nothing to reconnect to after a shutdown.
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

    private val _playerStatus = MutableStateFlow("")
    val playerStatus: StateFlow<String> = _playerStatus.asStateFlow()

    /**
     * Seconds left in the current track, formatted as m:ss. The server sends
     * the authoritative position with each song info reply; between replies the
     * value is ticked down locally once per second while playing.
     */
    private val _remainingTime = MutableStateFlow("")
    val remainingTime: StateFlow<String> = _remainingTime.asStateFlow()

    private var remainingSeconds = 0

    private val _savedConfig = MutableStateFlow<ConnectionConfig?>(null)
    val savedConfig: StateFlow<ConnectionConfig?> = _savedConfig.asStateFlow()

    // --- Playlists ---

    private val _playlists = MutableStateFlow<List<PlaylistTab>>(emptyList())
    val playlists: StateFlow<List<PlaylistTab>> = _playlists.asStateFlow()

    /** Index into playlists for whichever playlist is actually producing audio. -1 if none. */
    private val _activePlaylistIndex = MutableStateFlow(-1)
    val activePlaylistIndex: StateFlow<Int> = _activePlaylistIndex.asStateFlow()

    /** Index into playlists for whichever playlist the queue view is currently showing. -1 if none. */
    private val _viewedPlaylistIndex = MutableStateFlow(-1)
    val viewedPlaylistIndex: StateFlow<Int> = _viewedPlaylistIndex.asStateFlow()

    private var activePlaylistId: Int = 0
    private var hasActivePlaylist: Boolean = false
    private var viewedPlaylistId: Int = 0
    private var hasViewedPlaylist: Boolean = false

    // The UI is the only thing that knows the actual measured screen space,
    // so it owns upcomingCount. This caches the last value it asked for, so
    // internal auto-refreshes (after play/add/remove, broadcasts) can reuse
    // it instead of guessing a flat default.
    private var lastRequestedUpcomingCount = INITIAL_UPCOMING_COUNT

    // --- Queue (column-driven, mirrors the Qt client's queueTable exactly) ---

    private val _columns = MutableStateFlow<List<ColumnInfo>>(emptyList())
    val columns: StateFlow<List<ColumnInfo>> = _columns.asStateFlow()

    private val _previousRows = MutableStateFlow<List<QueueRowData>>(emptyList())
    val previousRows: StateFlow<List<QueueRowData>> = _previousRows.asStateFlow()

    private val _currentRow = MutableStateFlow<QueueRowData?>(null)
    val currentRow: StateFlow<QueueRowData?> = _currentRow.asStateFlow()

    private val _upcomingRows = MutableStateFlow<List<QueueRowData>>(emptyList())
    val upcomingRows: StateFlow<List<QueueRowData>> = _upcomingRows.asStateFlow()

    /** One-shot error surface for failed add/remove actions - UI shows as a snackbar and clears. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun consumeActionError() {
        _actionError.value = null
    }

    init {
        loadConfig()
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
        viewModelScope.launch {
            // Fresh session: clear any shutdown flag left over from a previous one.
            _serverShutdown.value = false
            _connectionState.value = ConnectionState.Connecting
            val result = withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, port), 2000)
                    socket
                } catch (e: SocketTimeoutException) {
                    Log.e("SharedViewModel", "Connection timed out: ${e.message}")
                    null
                } catch (e: Exception) {
                    Log.e("SharedViewModel", "Connection failed: ${e.message}")
                    null
                }
            }
            if (result != null) {
                _socket = result
                _connectionState.value = ConnectionState.Connected
                saveConfig(ip, port)
                startReader()
                sendHandshake()
            } else {
                _connectionState.value = ConnectionState.Error("Failed to connect to $ip:$port")
            }
        }
    }

    fun disconnect() {
        stopReader()
        stopCountdown()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _socket?.close()
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error closing socket: ${e.message}")
            } finally {
                _socket = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
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

    // --- Reader loop ---
    // One long-lived coroutine owns ALL reads from the socket. Every incoming
    // message - replies and unsolicited server pushes alike - arrives here and
    // is dispatched through processResponse(). Nothing else may read the stream.

    private fun startReader() {
        stopReader()
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            val socket = _socket ?: return@launch
            try {
                val inputStream = socket.getInputStream()
                while (isActive) {
                    val msg = readMessage(inputStream) ?: break
                    Log.d("SharedViewModel", "Received: ${msg.type}")
                    processResponse(msg)
                }
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Reader stopped: ${e.message}")
            }
            // Don't overwrite an Error state (e.g. a server rejection) with a
            // generic Disconnected when the socket subsequently closes.
            if (isActive && _connectionState.value !is ConnectionState.Error) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    private fun stopReader() {
        readerJob?.cancel()
        readerJob = null
    }

    // --- Remaining-time countdown ---

    private fun formatRemaining(seconds: Int): String {
        if (seconds <= 0) return "0:00"
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun startCountdown() {
        stopCountdown()
        if (remainingSeconds <= 0) return
        countdownJob = viewModelScope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1.seconds)
                remainingSeconds -= 1
                _remainingTime.value = formatRemaining(remainingSeconds)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
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

    private fun requestPlaylistSongs(playlistId: Int, upcomingCount: Int) {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_PLAYLIST_SONGS)
            .setRequestPlaylistSongs(
                RequestPlaylistSongs.newBuilder()
                    .setPlaylistId(playlistId)
                    .setUpcomingCount(upcomingCount)
                    .build()
            )
            .build()
        sendMessage(request)
    }

    /**
     * The single entry point for asking the server for the queue view.
     * upcomingCount comes from the UI's actual measured screen space - the
     * ViewModel has no opinion on how many rows fit on any given device.
     */
    fun refreshVisibleQueue(upcomingCount: Int) {
        if (!hasViewedPlaylist) return
        lastRequestedUpcomingCount = upcomingCount
        requestPlaylistSongs(viewedPlaylistId, upcomingCount)
    }

    /** rowIndex must come from a current/upcoming QueueRowData - never from previousRows. */
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
     * targetPlaylistId. The server only ever adds whatever is actually
     * playing, not an arbitrary row - matches the "current row only" context
     * menu restriction from the Qt client.
     */
    fun addCurrentSongToPlaylist(targetPlaylistId: Int, newPlaylistName: String = "") {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_ADD_SONG_TO_PLAYLIST)
            .setRequestAddSongToPlaylist(
                RequestAddSongToPlaylist.newBuilder()
                    .setTargetPlaylistId(targetPlaylistId)
                    .setNewPlaylistName(newPlaylistName)
                    .build()
            )
            .build()
        sendMessage(request)
    }

    /** rowIndex must come from a current/upcoming QueueRowData - never from previousRows. */
    fun removeSongFromPlaylist(rowIndex: Int) {
        if (!hasViewedPlaylist) return
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_REMOVE_SONG_FROM_PLAYLIST)
            .setRequestRemoveSongFromPlaylist(
                RequestRemoveSongFromPlaylist.newBuilder()
                    .setPlaylistId(viewedPlaylistId)
                    .setRowIndex(rowIndex)
                    .build()
            )
            .build()
        sendMessage(request)
    }

    /**
     * Called when the user taps a different playlist tab. Only resets local
     * state and the viewed pointer - it does NOT request songs itself. The
     * UI's BoxWithConstraints effect (keyed on viewedPlaylistIndex) is
     * responsible for calling refreshVisibleQueue() with the correct count,
     * so there is exactly one source of truth for upcomingCount.
     */
    fun selectPlaylistTab(index: Int) {
        val tabs = _playlists.value
        if (index < 0 || index >= tabs.size) return
        resetQueueViewTo(tabs[index].id)
    }

    private fun resetQueueViewTo(playlistId: Int) {
        viewedPlaylistId = playlistId
        hasViewedPlaylist = true
        val idx = _playlists.value.indexOfFirst { it.id == playlistId }
        _viewedPlaylistIndex.value = idx
        _previousRows.value = emptyList()
        _currentRow.value = null
        _upcomingRows.value = emptyList()
        _columns.value = emptyList()
    }

    // --- Messaging ---
    // Sends are write-only. Replies come back through the reader loop.
    // The protocol version is stamped centrally here so no builder site can
    // forget it.

    fun sendMessage(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = _socket ?: run {
                    // No active socket - most commonly a stray send that lost
                    // a race against an intentional disconnect() (e.g. a
                    // measurement effect re-firing during a screen's exit
                    // transition). Not a real failure worth surfacing to the
                    // user: connectionState already reflects "not connected"
                    // through whatever caused the socket to go away.
                    Log.w("SharedViewModel", "sendMessage called with no active socket - message dropped")
                    return@launch
                }

                // Don't keep sending into a connection the server has rejected or dropped.
                if (_connectionState.value is ConnectionState.Error) return@launch

                val stamped = message.toBuilder()
                    .setVersion(ProtocolConstants.PROTOCOL_VERSION)
                    .build()
                val payload = stamped.toByteArray()

                // 4-byte big-endian length prefix to match server framing
                val lengthHeader = ByteArray(4)
                lengthHeader[0] = (payload.size shr 24 and 0xFF).toByte()
                lengthHeader[1] = (payload.size shr 16 and 0xFF).toByte()
                lengthHeader[2] = (payload.size shr 8 and 0xFF).toByte()
                lengthHeader[3] = (payload.size and 0xFF).toByte()

                val out = socket.getOutputStream()
                synchronized(socket) {
                    out.write(lengthHeader)
                    out.write(payload)
                    out.flush()
                }
                Log.d("SharedViewModel", "Message sent: ${stamped.type}, ${payload.size} bytes")
            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error sending message: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // --- Private helpers ---

    private fun readMessage(inputStream: InputStream): Message? {
        return try {
            val lengthBytes = ByteArray(4)
            var totalRead = 0
            while (totalRead < 4) {
                val read = inputStream.read(lengthBytes, totalRead, 4 - totalRead)
                if (read == -1) return null
                totalRead += read
            }
            val messageLength = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                    ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                    ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                    (lengthBytes[3].toInt() and 0xFF)

            val messageBytes = ByteArray(messageLength)
            totalRead = 0
            while (totalRead < messageLength) {
                val read = inputStream.read(messageBytes, totalRead, messageLength - totalRead)
                if (read == -1) return null
                totalRead += read
            }
            Message.parseFrom(messageBytes)
        } catch (e: Exception) {
            Log.e("SharedViewModel", "Error reading message: ${e.message}")
            null
        }
    }

    /** Shared by MSG_TYPE_REPLY_SONG_INFO and the songInfo field inside MSG_TYPE_RESPONSE_INITIAL_INFO. */
    private fun applySongMetadata(response: ResponseSongMetadata) {
        val state = response.playerState
        if (state == PlayerState.PLAYER_STATUS_UNSPECIFIED ||
            state == PlayerState.PLAYER_STATUS_EMPTY
        ) {
            // Nothing loaded in the player.
            _playerStatus.value = "No song selected"
            stopCountdown()
            remainingSeconds = 0
            _remainingTime.value = ""
        } else {
            _playerStatus.value = when (state) {
                PlayerState.PLAYER_STATUS_PLAYING -> "Playing"
                PlayerState.PLAYER_STATUS_PAUSED -> "Paused"
                PlayerState.PLAYER_STATUS_IDLE -> "Idle"
                PlayerState.PLAYER_STATUS_ERROR -> "Error"
                else -> "Unknown"
            }

            // Resync the countdown from the server's authoritative position.
            val length = response.lengthSeconds
            val position = response.positionSeconds
            remainingSeconds = if (length > position) (length - position) else 0
            _remainingTime.value = formatRemaining(remainingSeconds)

            if (state == PlayerState.PLAYER_STATUS_PLAYING) {
                startCountdown()
            } else {
                stopCountdown()
            }
        }
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
                        disconnect()
                        return
                    }
                    Log.d(
                        "SharedViewModel",
                        "Handshake accepted, server protocol version ${response.version}"
                    )
                    _connectionState.value = ConnectionState.Ready
                    // The one and only initial request, sent once the server has
                    // accepted us - bundles song info, playlists, and engine state.
                    requestInitialInfo()
                } else {
                    _connectionState.value =
                        ConnectionState.Error("Server refused the connection")
                }
            }
            MsgType.MSG_TYPE_REPLY_SONG_INFO -> {
                applySongMetadata(response.responseSongMetadata)
            }
            MsgType.MSG_TYPE_RESPONSE_INITIAL_INFO -> {
                val initialInfo = response.responseInitialInfo
                applySongMetadata(initialInfo.songInfo)

                val tabs = mutableListOf<PlaylistTab>()
                var newActiveIndex = -1
                hasActivePlaylist = false
                initialInfo.playlists.playlistsList.forEachIndexed { idx, pl ->
                    tabs.add(PlaylistTab(id = pl.id, name = pl.name))
                    if (pl.isPlaying) {
                        activePlaylistId = pl.id
                        newActiveIndex = idx
                        hasActivePlaylist = true
                    }
                }
                _playlists.value = tabs
                _activePlaylistIndex.value = newActiveIndex

                if (hasActivePlaylist) {
                    resetQueueViewTo(activePlaylistId)
                    // First request uses the placeholder count; the UI's own
                    // measurement effect will immediately follow up with the
                    // real, screen-derived count once it composes.
                    requestPlaylistSongs(activePlaylistId, lastRequestedUpcomingCount)
                }
            }
            MsgType.MSG_TYPE_RESPONSE_PLAYLIST_SONGS -> {
                val playlistSongs = response.responsePlaylistSongs
                if (hasViewedPlaylist && playlistSongs.playlistId != viewedPlaylistId) {
                    // Stale response for a playlist we've since navigated away from.
                    return
                }

                val newColumns = playlistSongs.columnsList.map {
                    ColumnInfo(name = it.name, isNumeric = it.isNumeric)
                }
                if (newColumns != _columns.value) {
                    // Visible columns changed on the desktop mid-session: old
                    // cached rows would no longer line up against new headers.
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
                    val updated = _previousRows.value + oldCurrent
                    _previousRows.value = if (updated.size > MAX_PREVIOUS_ROWS) {
                        updated.takeLast(MAX_PREVIOUS_ROWS)
                    } else {
                        updated
                    }
                }

                _currentRow.value = newCurrent
                _upcomingRows.value = newUpcoming
            }
            MsgType.MSG_TYPE_RESPONSE_PLAY_SONG -> {
                if (response.responsePlaySong.accepted && hasViewedPlaylist) {
                    // Refreshes the queue for whatever playlist we just played
                    // from. If that playlist was previously inactive,
                    // PLAYLIST_ACTIVATED (below) handles the tab/active-state update.
                    requestPlaylistSongs(viewedPlaylistId, lastRequestedUpcomingCount)
                }
            }
            MsgType.MSG_TYPE_PLAYLIST_ACTIVATED -> {
                val activated = response.playlistActivated
                activePlaylistId = activated.playlistId
                hasActivePlaylist = true
                _activePlaylistIndex.value = _playlists.value.indexOfFirst { it.id == activePlaylistId }

                // Follow the newly-active playlist, mirroring the Qt client.
                resetQueueViewTo(activePlaylistId)
                requestPlaylistSongs(activePlaylistId, lastRequestedUpcomingCount)
            }
            MsgType.MSG_TYPE_PLAYLIST_CHANGED -> {
                val changed = response.playlistChanged
                if (hasViewedPlaylist && changed.playlistId == viewedPlaylistId) {
                    requestPlaylistSongs(viewedPlaylistId, lastRequestedUpcomingCount)
                }
            }
            MsgType.MSG_TYPE_RESPONSE_ADD_SONG_TO_PLAYLIST -> {
                if (!response.responseAddSongToPlaylist.accepted) {
                    _actionError.value = "Failed to add song to playlist"
                }
                // On success, the server's PLAYLIST_CHANGED broadcast for the
                // target playlist refreshes the view if we're looking at it.
            }
            MsgType.MSG_TYPE_RESPONSE_REMOVE_SONG_FROM_PLAYLIST -> {
                if (!response.responseRemoveSongFromPlaylist.accepted) {
                    _actionError.value = "Failed to remove song from playlist"
                }
            }
            MsgType.MSG_TYPE_ENGINE_STATE_CHANGE -> {
                when (response.engineStateChange.state) {
                    EngineState.ENGINE_STATE_PLAYING -> {
                        _playerStatus.value = "Playing"
                        requestSongInfo()
                        // The current/upcoming rows may have shifted; refresh
                        // the queue too, but only if we're looking at the
                        // playlist that's actually playing.
                        if (hasViewedPlaylist && hasActivePlaylist && viewedPlaylistId == activePlaylistId) {
                            requestPlaylistSongs(activePlaylistId, lastRequestedUpcomingCount)
                        }
                    }
                    EngineState.ENGINE_STATE_PAUSED -> {
                        _playerStatus.value = "Paused"
                        stopCountdown()
                    }
                    else -> {
                        _playerStatus.value = "Stopped"
                        stopCountdown()
                    }
                }
            }
            MsgType.MSG_TYPE_DISCONNECT -> {
                stopCountdown()
                val reason = response.requestDisconnect.reasonDisconnect
                val text = when (reason) {
                    ReasonDisconnect.REASON_DISCONNECT_VERSION_MISMATCH ->
                        "Server rejected this app: protocol version too old"
                    ReasonDisconnect.REASON_DISCONNECT_UNKNOWN_MSGTYPE ->
                        "Server rejected an unsupported request"
                    ReasonDisconnect.REASON_DISCONNECT_NO_HANDSHAKE ->
                        "Server rejected this app: handshake missing"
                    ReasonDisconnect.REASON_DISCONNECT_SERVER_SHUTDOWN ->
                        "Strawberry has been closed on the desktop."
                    else -> "Server closed the connection"
                }
                Log.w("SharedViewModel", text)
                if (reason == ReasonDisconnect.REASON_DISCONNECT_SERVER_SHUTDOWN) {
                    _serverShutdown.value = true
                }
                _connectionState.value = ConnectionState.Error(text)
            }
            else -> Log.d("SharedViewModel", "Unhandled response type: ${response.type}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopReader()
        stopCountdown()
        try {
            _socket?.close()
        } catch (e: Exception) {
            Log.e("SharedViewModel", "Error in onCleared: ${e.message}")
        }
        _socket = null
    }
}