package com.zudiewiener.strawberryremote.net

import android.util.Log
import com.zudiewiener.strawberryremote.util.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nw.remote.Message
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.seconds

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()

    /** TCP connection established; caller (SharedViewModel) still needs to send the handshake. */
    object Connected : ConnectionState()

    /** Handshake accepted by the server - the remote is usable. Set via markReady(). */
    object Ready : ConnectionState()

    data class Error(val message: String) : ConnectionState()
}

/**
 * Owns the raw TCP connection to a Strawberry Network Remote server: socket
 * lifecycle, message framing (4-byte length-prefix), the reader loop, and
 * silent reconnection on an unexpected drop.
 *
 * Deliberately knows nothing about playlists, songs, or what any particular
 * message *means* - it only deals in raw Message objects going in and out.
 * SharedViewModel owns all of that business logic and drives protocol-level
 * decisions (like markReady(), called once it has validated a handshake
 * response) by calling back into this class.
 */
class StrawberryConnection(private val scope: CoroutineScope) {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private val RECONNECT_RETRY_DELAY = 2.seconds
    }

    private var socket: Socket? = null
    private var readerJob: Job? = null

    // Remembered purely so a silent reconnect knows where to retry - this
    // class doesn't persist it anywhere; that's SharedViewModel's concern.
    private var lastIp: String? = null
    private var lastPort: Int = 0

    /** True once we've had a genuinely working (handshake-accepted) connection this session. */
    private var hasConnectedSuccessfully = false
    private var isReconnecting = false

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Every parsed incoming Message, in order. SharedViewModel collects this
    // and interprets each one - this class has no opinion on message content.
    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    val incomingMessages: SharedFlow<Message> = _incomingMessages.asSharedFlow()

    /**
     * "Failed to connect" only makes sense for a first-time attempt; once
     * we've actually been connected, a subsequent drop is more accurately
     * described as a lost connection, not a failed one.
     */
    private fun connectionLostMessage(): String =
        if (hasConnectedSuccessfully) "Connection to Strawberry was lost" else "Failed to connect to the server"

    fun connect(ip: String, port: Int) {
        lastIp = ip
        lastPort = port
        scope.launch {
            hasConnectedSuccessfully = false
            _connectionState.value = ConnectionState.Connecting
            val newSocket = openSocket(ip, port)
            if (newSocket != null) {
                onSocketConnected(newSocket)
            } else {
                _connectionState.value = ConnectionState.Error("Failed to connect to $ip:$port")
            }
        }
    }

    fun disconnect() {
        stopReader()
        scope.launch(Dispatchers.IO) {
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e("StrawberryConnection", "Error closing socket: ${e.message}")
            } finally {
                socket = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    /** Called by SharedViewModel once it has parsed and validated a handshake acceptance. */
    fun markReady() {
        _connectionState.value = ConnectionState.Ready
        hasConnectedSuccessfully = true
    }

    /** Called by SharedViewModel for protocol-level failures (refused handshake, server DISCONNECT). */
    fun setError(message: String) {
        _connectionState.value = ConnectionState.Error(message)
    }

    fun sendMessage(message: Message) {
        scope.launch(Dispatchers.IO) {
            try {
                val currentSocket = socket ?: run {
                    // No active socket - most commonly a stray send that lost
                    // a race against an intentional disconnect(). Not a real
                    // failure worth surfacing: connectionState already
                    // reflects "not connected" through whatever caused the
                    // socket to go away.
                    Log.w("StrawberryConnection", "sendMessage called with no active socket - message dropped")
                    return@launch
                }

                // Don't keep sending into a connection already known to be bad.
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

                val out = currentSocket.getOutputStream()
                synchronized(currentSocket) {
                    out.write(lengthHeader)
                    out.write(payload)
                    out.flush()
                }
                Log.d("StrawberryConnection", "Message sent: ${stamped.type}, ${payload.size} bytes")
            } catch (e: Exception) {
                // A write failure here (e.g. "Broken pipe") most often means
                // the OS silently dropped the connection - stop the reader
                // (it's pointed at the same dead socket) and try a silent
                // reconnect rather than surfacing the raw exception.
                Log.e("StrawberryConnection", "Error sending message: ${e.message}")
                stopReader()
                handleConnectionLost()
            }
        }
    }

    private suspend fun openSocket(ip: String, port: Int): Socket? = withContext(Dispatchers.IO) {
        try {
            val newSocket = Socket()
            newSocket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
            newSocket
        } catch (e: SocketTimeoutException) {
            Log.e("StrawberryConnection", "Connection timed out: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e("StrawberryConnection", "Connection failed: ${e.message}")
            null
        }
    }

    /** Shared by a fresh connect() and a successful silent reconnect. */
    private fun onSocketConnected(newSocket: Socket) {
        socket = newSocket
        _connectionState.value = ConnectionState.Connected
        startReader()
        // The handshake message itself is business logic (needs a specific
        // client-name payload) - SharedViewModel sends it by observing this
        // Connected state and calling sendMessage() with a RequestConnect.
    }

    private fun startReader() {
        stopReader()
        readerJob = scope.launch(Dispatchers.IO) {
            val currentSocket = socket ?: return@launch
            try {
                val inputStream = currentSocket.getInputStream()
                while (isActive) {
                    val msg = readMessage(inputStream) ?: break
                    Log.d("StrawberryConnection", "Received: ${msg.type}")
                    _incomingMessages.emit(msg)
                }
            } catch (e: Exception) {
                Log.e("StrawberryConnection", "Reader stopped: ${e.message}")
            }
            // isActive is false here if this job was cancelled on purpose
            // (e.g. disconnect()); only an unexpected end of the loop - the
            // socket dying while we were still meant to be connected -
            // triggers a reconnect attempt.
            if (isActive && _connectionState.value !is ConnectionState.Error) {
                handleConnectionLost()
            }
        }
    }

    private fun stopReader() {
        readerJob?.cancel()
        readerJob = null
    }

    /**
     * Makes a short series of silent reconnect attempts using the last-known
     * ip/port before surfacing anything as an error - gives a briefly-dropped
     * WiFi radio (e.g. right after the device wakes from sleep) time to come
     * back rather than failing on the very first, possibly-premature attempt.
     * connectionState stays Connecting for the whole loop, so the UI doesn't
     * navigate away unless every attempt fails.
     */
    private fun handleConnectionLost() {
        if (isReconnecting) return
        isReconnecting = true

        try {
            socket?.close()
        } catch (e: Exception) {
            // Already broken - nothing meaningful to clean up.
        }
        socket = null

        val ip = lastIp
        val port = lastPort
        if (ip == null) {
            isReconnecting = false
            _connectionState.value = ConnectionState.Error(connectionLostMessage())
            return
        }

        scope.launch {
            _connectionState.value = ConnectionState.Connecting
            var newSocket: Socket? = null
            for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
                newSocket = openSocket(ip, port)
                if (newSocket != null) break
                if (attempt < MAX_RECONNECT_ATTEMPTS) {
                    delay(RECONNECT_RETRY_DELAY)
                }
            }
            isReconnecting = false
            if (newSocket != null) {
                onSocketConnected(newSocket)
            } else {
                _connectionState.value = ConnectionState.Error(connectionLostMessage())
            }
        }
    }

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
            Log.e("StrawberryConnection", "Error reading message: ${e.message}")
            null
        }
    }

    /** Called from SharedViewModel.onCleared() - final teardown, no reconnect attempted. */
    fun shutdown() {
        stopReader()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.e("StrawberryConnection", "Error in shutdown: ${e.message}")
        }
        socket = null
    }
}

