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
import nw.remote.RequestConnect
import nw.remote.RequestSongMetadata
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()

    /** TCP connection established; handshake sent, waiting for the server to accept. */
    object Connected : ConnectionState()

    /** Handshake accepted by the server - the remote is usable. */
    object Ready : ConnectionState()

    data class Error(val message: String) : ConnectionState()
}

data class SongInfo(
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val year: String = "",
    val genre: String = "",
    val playCount: String = "",
    val songLength: String = ""
)

class SharedViewModel(application: Application) : AndroidViewModel(application) {

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

    private val _songInfo = MutableStateFlow(SongInfo())
    val songInfo: StateFlow<SongInfo> = _songInfo.asStateFlow()

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
                delay(1000)
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

    // --- Messaging ---
    // Sends are write-only. Replies come back through the reader loop.
    // The protocol version is stamped centrally here so no builder site can
    // forget it.

    fun sendMessage(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = _socket ?: run {
                    _connectionState.value = ConnectionState.Error("Not connected")
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

    private fun processResponse(response: Message) {
        when (response.type) {
            MsgType.MSG_TYPE_RESPONSE_CONNECT -> {
                if (response.responseConnect.accepted) {
                    Log.d(
                        "SharedViewModel",
                        "Handshake accepted, server protocol version ${response.version}"
                    )
                    _connectionState.value = ConnectionState.Ready
                    // The one and only initial request, sent once the server has
                    // accepted us.
                    requestSongInfo()
                } else {
                    _connectionState.value =
                        ConnectionState.Error("Server refused the connection")
                }
            }
            MsgType.MSG_TYPE_REPLY_SONG_INFO -> {
                val metadata = response.responseSongMetadata.songMetadata
                val state = response.responseSongMetadata.playerState
                if (state == PlayerState.PLAYER_STATUS_UNSPECIFIED ||
                    state == PlayerState.PLAYER_STATUS_EMPTY
                ) {
                    // Nothing loaded in the player - clear any stale song details.
                    _songInfo.value = SongInfo()
                    _playerStatus.value = "No song selected"
                    stopCountdown()
                    remainingSeconds = 0
                    _remainingTime.value = ""
                } else {
                    _songInfo.value = SongInfo(
                        title = metadata.title,
                        album = metadata.album,
                        artist = metadata.artist,
                        year = metadata.stryear,
                        genre = metadata.genre,
                        playCount = metadata.playcount.toString(),
                        songLength = metadata.songlength
                    )
                    _playerStatus.value = when (state) {
                        PlayerState.PLAYER_STATUS_PLAYING -> "Playing"
                        PlayerState.PLAYER_STATUS_PAUSED -> "Paused"
                        PlayerState.PLAYER_STATUS_IDLE -> "Idle"
                        PlayerState.PLAYER_STATUS_ERROR -> "Error"
                        else -> "Unknown"
                    }

                    // Resync the countdown from the server's authoritative position.
                    val length = response.responseSongMetadata.lengthSeconds
                    val position = response.responseSongMetadata.positionSeconds
                    remainingSeconds = if (length > position) (length - position) else 0
                    _remainingTime.value = formatRemaining(remainingSeconds)

                    if (state == PlayerState.PLAYER_STATUS_PLAYING) {
                        startCountdown()
                    } else {
                        stopCountdown()
                    }
                }
            }
            MsgType.MSG_TYPE_ENGINE_STATE_CHANGE -> {
                when (response.engineStateChange.state) {
                    EngineState.ENGINE_STATE_PLAYING -> {
                        _playerStatus.value = "Playing"
                        // The reply to this restarts the countdown with a fresh
                        // position, which is also how a desktop seek resyncs.
                        requestSongInfo()
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