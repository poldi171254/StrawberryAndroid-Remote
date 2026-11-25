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
import kotlinx.coroutines.launch
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.RequestSetVolume

/**
 * Owns the server-controlled volume slider only: whether volume control is
 * currently enabled on the server, the current volume, and sending volume
 * changes while the user drags.
 *
 * Rules (protocol v6):
 *  - Nothing is ever sent unless volume control is enabled.
 *  - While the user's finger is on the slider, incoming VolumeChanged
 *    broadcasts are ignored so the slider doesn't fight the drag.
 *  - Sends while dragging are throttled to about one per [THROTTLE_MS]; the
 *    final value is always sent on release.
 *  - The server never replies to RequestSetVolume, and no token is needed.
 *
 * Incoming values (initial info, VolumeChanged broadcasts) only move the
 * slider - they never cause a send.
 */
class VolumeController(
    private val scope: CoroutineScope,
    private val sendMessage: (Message) -> Unit
) {

    private val _enabled = MutableStateFlow(false)
    /** True when the slider should be visible. Defaults to false until the server says otherwise. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _volume = MutableStateFlow(0)
    /** Current volume, 0 to 100. */
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private var dragging = false
    private var lastSentAtNanos = 0L
    private var pendingValue: Int? = null
    private var trailingJob: Job? = null

    /** From ResponseConnect.volume_control_enabled, and VolumeControlStatusChanged mid-session. */
    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        if (!enabled) cancelPending()
    }

    /** From ResponseInitialInfo.volume. Programmatic only - never sent back. */
    fun onInitialVolume(volume: Int) {
        if (dragging) return
        _volume.value = volume.coerceIn(0, 100)
    }

    /** From VolumeChanged. Always sent by the server, enabled or not; ignored while the user is dragging. */
    fun onVolumeChanged(volume: Int) {
        if (dragging) return
        _volume.value = volume.coerceIn(0, 100)
    }

    /** The user is moving the slider. Called continuously during a drag (and once for a tap). */
    fun onUserChange(volume: Int) {
        if (!_enabled.value) return
        dragging = true
        val value = volume.coerceIn(0, 100)
        _volume.value = value

        val sinceLastMs = (System.nanoTime() - lastSentAtNanos) / 1_000_000
        if (lastSentAtNanos == 0L || sinceLastMs >= THROTTLE_MS) {
            trailingJob?.cancel()
            trailingJob = null
            pendingValue = null
            send(value)
        } else {
            // Too soon: remember the latest value and flush it when the
            // throttle window ends, so a slow-moving drag never sits on a
            // stale value for longer than one window.
            pendingValue = value
            if (trailingJob == null) {
                trailingJob = scope.launch {
                    delay(THROTTLE_MS - sinceLastMs)
                    trailingJob = null
                    pendingValue?.let { send(it) }
                    pendingValue = null
                }
            }
        }
    }

    /** The user lifted their finger. Always sends the final value. */
    fun onUserFinished() {
        val wasDragging = dragging
        dragging = false
        cancelPending()
        if (!wasDragging || !_enabled.value) return
        send(_volume.value)
    }

    /** Called on disconnect - a fresh connect starts with a clean slate. */
    fun reset() {
        cancelPending()
        dragging = false
        _enabled.value = false
        lastSentAtNanos = 0L
    }

    private fun cancelPending() {
        trailingJob?.cancel()
        trailingJob = null
        pendingValue = null
    }

    private fun send(value: Int) {
        if (!_enabled.value) return
        lastSentAtNanos = System.nanoTime()
        sendMessage(
            Message.newBuilder()
                .setType(MsgType.MSG_TYPE_REQUEST_SET_VOLUME)
                .setRequestSetVolume(RequestSetVolume.newBuilder().setVolume(value).build())
                .build()
        )
    }

    private companion object {
        const val THROTTLE_MS = 100L
    }
}