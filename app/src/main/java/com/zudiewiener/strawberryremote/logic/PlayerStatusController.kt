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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.PlayerState
import nw.remote.RequestPlay
import nw.remote.RequestSeek
import nw.remote.ResponseSongMetadata
import kotlin.time.Duration.Companion.seconds

/**
 * Owns player status text, the song position/length, the remaining-time
 * countdown, and seeking. The server sends the authoritative position with
 * each song info reply; between replies the position is ticked forward
 * locally once per second while playing.
 */
class PlayerStatusController(
    private val scope: CoroutineScope,
    private val sendMessage: (Message) -> Unit
) {

    /** Only the two states a seek is defined for; everything else (stopped, idle, empty, error) is OTHER. */
    private enum class Mode { PLAYING, PAUSED, OTHER }

    private val _playerStatus = MutableStateFlow("")
    val playerStatus: StateFlow<String> = _playerStatus.asStateFlow()

    private val _remainingTime = MutableStateFlow("")
    val remainingTime: StateFlow<String> = _remainingTime.asStateFlow()

    private val _positionSeconds = MutableStateFlow(0)
    val positionSeconds: StateFlow<Int> = _positionSeconds.asStateFlow()

    private val _lengthSeconds = MutableStateFlow(0)
    val lengthSeconds: StateFlow<Int> = _lengthSeconds.asStateFlow()

    /** True while a seek is meaningful: a song with a usable length is loaded and the player is playing or paused. */
    private val _canSeek = MutableStateFlow(false)
    val canSeek: StateFlow<Boolean> = _canSeek.asStateFlow()

    private var mode = Mode.OTHER
    private var remainingSeconds = 0
    private var countdownJob: Job? = null

    /** Shared by MSG_TYPE_REPLY_SONG_INFO and the songInfo field inside MSG_TYPE_RESPONSE_INITIAL_INFO. */
    fun applySongMetadata(response: ResponseSongMetadata) {
        val state = response.playerState
        if (state == PlayerState.PLAYER_STATUS_UNSPECIFIED ||
            state == PlayerState.PLAYER_STATUS_EMPTY
        ) {
            // Nothing loaded in the player.
            _playerStatus.value = "No song selected"
            stopCountdown()
            remainingSeconds = 0
            _remainingTime.value = ""
            _positionSeconds.value = 0
            _lengthSeconds.value = 0
            setMode(Mode.OTHER)
            return
        }

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
        _lengthSeconds.value = length
        remainingSeconds = if (length > position) (length - position) else 0
        _positionSeconds.value = length - remainingSeconds
        _remainingTime.value = formatRemaining(remainingSeconds)

        // After the length is set: canSeek depends on it.
        setMode(
            when (state) {
                PlayerState.PLAYER_STATUS_PLAYING -> Mode.PLAYING
                PlayerState.PLAYER_STATUS_PAUSED -> Mode.PAUSED
                else -> Mode.OTHER
            }
        )

        if (state == PlayerState.PLAYER_STATUS_PLAYING) {
            startCountdown()
        } else {
            stopCountdown()
        }
    }

    fun setPlaying() {
        _playerStatus.value = "Playing"
        setMode(Mode.PLAYING)
    }

    fun setPaused() {
        _playerStatus.value = "Paused"
        setMode(Mode.PAUSED)
        stopCountdown()
    }

    fun setStopped() {
        _playerStatus.value = "Stopped"
        setMode(Mode.OTHER)
        stopCountdown()
    }

    /**
     * Seeks the current song. Called once, when the user releases the slider.
     *
     * Valid positions are 0 to length-1: the target is clamped into that
     * range, and nothing is sent when no song with a usable length is loaded
     * or the player is neither playing nor paused. A playing song gets only
     * the RequestSeek - never a RequestPlay, which the server treats as a
     * restart. A paused song gets the seek followed by a RequestPlay, since
     * a seek alone leaves it paused.
     *
     * The server doesn't reply to a seek, so the local position is set to the
     * sought value immediately and the countdown restarted from there; the
     * next song info reply (requested on the server's ENGINE_STATE_CHANGE,
     * about 300 ms after the seek settles) overwrites it with the real value.
     */
    fun seekTo(positionSeconds: Int) {
        val length = _lengthSeconds.value
        if (length <= 0 || mode == Mode.OTHER) return
        val target = positionSeconds.coerceIn(0, length - 1)

        sendMessage(
            Message.newBuilder()
                .setType(MsgType.MSG_TYPE_REQUEST_SEEK)
                .setRequestSeek(RequestSeek.newBuilder().setPositionSeconds(target).build())
                .build()
        )
        if (mode == Mode.PAUSED) {
            sendMessage(
                Message.newBuilder()
                    .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
                    .setRequestPlay(RequestPlay.newBuilder().setPlay(true).build())
                    .build()
            )
        }

        remainingSeconds = length - target
        _positionSeconds.value = target
        _remainingTime.value = formatRemaining(remainingSeconds)
        // A paused song starts playing via the server's ENGINE_STATE_CHANGE,
        // whose song info reply starts the countdown.
        if (mode == Mode.PLAYING) startCountdown()
    }

    fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun setMode(newMode: Mode) {
        mode = newMode
        _canSeek.value = newMode != Mode.OTHER && _lengthSeconds.value > 1
    }

    private fun startCountdown() {
        stopCountdown()
        if (remainingSeconds <= 0) return
        countdownJob = scope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1.seconds)
                remainingSeconds -= 1
                _positionSeconds.value = _lengthSeconds.value - remainingSeconds
                _remainingTime.value = formatRemaining(remainingSeconds)
            }
        }
    }

    private fun formatRemaining(seconds: Int): String {
        if (seconds <= 0) return "0:00"
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}