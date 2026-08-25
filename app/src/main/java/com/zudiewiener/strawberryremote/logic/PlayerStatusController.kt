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
import nw.remote.PlayerState
import nw.remote.ResponseSongMetadata
import kotlin.time.Duration.Companion.seconds

/**
 * Owns player status text and the remaining-time countdown only. The server
 * sends the authoritative position with each song info reply; between
 * replies the value is ticked down locally once per second while playing.
 */
class PlayerStatusController(private val scope: CoroutineScope) {

    private val _playerStatus = MutableStateFlow("")
    val playerStatus: StateFlow<String> = _playerStatus.asStateFlow()

    private val _remainingTime = MutableStateFlow("")
    val remainingTime: StateFlow<String> = _remainingTime.asStateFlow()

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
        remainingSeconds = if (length > position) (length - position) else 0
        _remainingTime.value = formatRemaining(remainingSeconds)

        if (state == PlayerState.PLAYER_STATUS_PLAYING) {
            startCountdown()
        } else {
            stopCountdown()
        }
    }

    fun setPlaying() {
        _playerStatus.value = "Playing"
    }

    fun setPaused() {
        _playerStatus.value = "Paused"
        stopCountdown()
    }

    fun setStopped() {
        _playerStatus.value = "Stopped"
        stopCountdown()
    }

    fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun startCountdown() {
        stopCountdown()
        if (remainingSeconds <= 0) return
        countdownJob = scope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1.seconds)
                remainingSeconds -= 1
                _remainingTime.value = formatRemaining(remainingSeconds)
            }
        }
    }

    private fun formatRemaining(seconds: Int): String {
        if (seconds <= 0) return "0:00"
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
