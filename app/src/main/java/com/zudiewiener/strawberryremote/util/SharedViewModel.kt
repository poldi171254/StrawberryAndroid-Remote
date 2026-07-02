package com.zudiewiener.strawberryremote.util

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zudiewiener.strawberryremote.data.ConnectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.PlayerState
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
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

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _songInfo = MutableStateFlow(SongInfo())
    val songInfo: StateFlow<SongInfo> = _songInfo.asStateFlow()

    private val _playerStatus = MutableStateFlow("")
    val playerStatus: StateFlow<String> = _playerStatus.asStateFlow()

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
                saveConfig(ip, port) // Only save on successful connection
            } else {
                _connectionState.value = ConnectionState.Error("Failed to connect to $ip:$port")
            }
        }
    }

    fun disconnect() {
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

    // --- Messaging ---

    fun sendMessage(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = _socket ?: run {
                    _connectionState.value = ConnectionState.Error("Not connected")
                    return@launch
                }

                val payload = message.toByteArray()

                // 4-byte big-endian length prefix to match server framing
                val lengthHeader = ByteArray(4)
                lengthHeader[0] = (payload.size shr 24 and 0xFF).toByte()
                lengthHeader[1] = (payload.size shr 16 and 0xFF).toByte()
                lengthHeader[2] = (payload.size shr 8 and 0xFF).toByte()
                lengthHeader[3] = (payload.size and 0xFF).toByte()

                val out = socket.getOutputStream()
                out.write(lengthHeader)
                out.write(payload)
                out.flush()
                Log.d("SharedViewModel", "Message sent: ${message.type}, ${payload.size} bytes")

                if (message.type == MsgType.MSG_TYPE_REQUEST_PAUSE) {
                    _playerStatus.value = "Paused"
                    return@launch
                }

                val response = readResponse(socket.getInputStream())
                if (response != null) {
                    processResponse(response)
                } else {
                    _connectionState.value = ConnectionState.Error("Empty response from server")
                }

            } catch (e: Exception) {
                Log.e("SharedViewModel", "Error sending message: ${e.message}")
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // --- Private helpers ---

    private fun readResponse(inputStream: InputStream): Message? {
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
            Log.e("SharedViewModel", "Error reading response: ${e.message}")
            null
        }
    }

    private fun processResponse(response: Message) {
        when (response.type) {
            MsgType.MSG_TYPE_REPLY_SONG_INFO -> {
                val metadata = response.responseSongMetadata.songMetadata
                val state = response.responseSongMetadata.playerState
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
                    else -> "Paused"
                }
            }
            else -> Log.d("SharedViewModel", "Unhandled response type: ${response.type}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            _socket?.close()
        } catch (e: Exception) {
            Log.e("SharedViewModel", "Error in onCleared: ${e.message}")
        }
        _socket = null
    }
}