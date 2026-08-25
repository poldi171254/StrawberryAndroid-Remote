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
import nw.remote.RequestValidateToken

/**
 * Owns the token/authentication state machine only: whether the server
 * currently requires a token, prompting the user for one, validating it with
 * the server, and tracking whether mutable-playlist actions (add/remove) are
 * currently allowed. Knows nothing about playlists, songs, or the connection
 * itself - SharedViewModel's dispatch hands it exactly the message types
 * that concern it (ResponseConnect, ResponseValidateToken,
 * AuthStatusChanged) plus the reject_reason from a mutation response when
 * that response's rejection was token-related.
 *
 * The token itself is session-only: held in memory only while a connection
 * with auth enabled is up, never persisted, and cleared on disconnect.
 */
class AuthController(
    private val sendMessage: (Message) -> Unit,
    private val onConnectHandshakeReady: () -> Unit
) {

    /**
     * Entry - the initial text-field step: Submit / Bypass / Cancel.
     * Invalid - shown after a rejected token: message + OK only, OK returns to Entry.
     * LockedOut - shown once the server has disconnected us for too many failed
     * attempts: message + OK only. There's nothing left to retry once the
     * server has closed the connection over this, so OK is expected to exit
     * the app (see SharedViewModel.exitAfterTokenPrompt / MainActivity).
     */
    sealed class TokenPromptState {
        object Entry : TokenPromptState()
        object Invalid : TokenPromptState()
        object LockedOut : TokenPromptState()
    }

    private var token: String = ""
    private var pendingCandidate: String = ""

    /**
     * True only while the *initial* connect-time prompt (triggered from
     * onResponseConnect) is unresolved. Distinguishes that case from a
     * mid-session prompt (AuthStatusChanged, or a reactive TOKEN_REQUIRED/
     * TOKEN_MISMATCH rejection) - only the former should call
     * onConnectHandshakeReady() once resolved, since a mid-session prompt
     * means the connection is already past handshake and initial info has
     * long since loaded.
     */
    private var awaitingInitialHandshake = false

    private val _tokenPrompt = MutableStateFlow<TokenPromptState?>(null)
    /** Non-null whenever the token dialog should be showing; null = hidden. */
    val tokenPrompt: StateFlow<TokenPromptState?> = _tokenPrompt.asStateFlow()

    private val _mutablePlaylistsEnabled = MutableStateFlow(true)
    val mutablePlaylistsEnabled: StateFlow<Boolean> = _mutablePlaylistsEnabled.asStateFlow()

    /** The token to attach to any RequestAddSongToPlaylist / RequestRemoveSongFromPlaylist. */
    val currentToken: String
        get() = token

    /**
     * Called after ResponseConnect is parsed. If auth isn't required, calls
     * onConnectHandshakeReady() immediately; otherwise shows the Entry
     * prompt and defers onConnectHandshakeReady() until it resolves.
     */
    fun onResponseConnect(authEnabled: Boolean) {
        if (authEnabled) {
            awaitingInitialHandshake = true
            _mutablePlaylistsEnabled.value = false
            _tokenPrompt.value = TokenPromptState.Entry
        } else {
            awaitingInitialHandshake = false
            _mutablePlaylistsEnabled.value = true
            _tokenPrompt.value = null
            onConnectHandshakeReady()
        }
    }

    /**
     * Mid-session auth toggle (AuthStatusChanged broadcast). Turning auth on
     * gets the same treatment as connect-time minus the handshake gating -
     * the connection is already up and running, so this just withdraws
     * mutable-playlist actions and prompts. Turning it off is handled
     * silently - no user input needed, and any prompt that happened to be up
     * is dismissed.
     */
    fun onAuthStatusChanged(authEnabled: Boolean) {
        awaitingInitialHandshake = false
        token = ""
        pendingCandidate = ""
        if (authEnabled) {
            _mutablePlaylistsEnabled.value = false
            _tokenPrompt.value = TokenPromptState.Entry
        } else {
            _mutablePlaylistsEnabled.value = true
            _tokenPrompt.value = null
        }
    }

    /**
     * Reactive fallback: a mutation request came back TOKEN_REQUIRED or
     * TOKEN_MISMATCH even though RequestValidateToken should already have
     * caught this proactively (e.g. a token that was valid at validation
     * time got revoked server-side mid-session). Whatever the exact reason,
     * the response is the same: the currently-held token no longer works, so
     * re-open the prompt rather than just showing a generic action error.
     */
    fun onTokenRejected(reason: PlaylistRejectReason) {
        onAuthStatusChanged(true)
    }

    /**
     * User tapped Submit on the Entry step. Tentatively holds the candidate
     * token and asks the server to validate it - not applied to
     * mutablePlaylistsEnabled/the real token until the response comes back,
     * so an invalid guess never gets sent on a real mutating request.
     */
    fun submitToken(candidate: String) {
        pendingCandidate = candidate
        sendMessage(
            Message.newBuilder()
                .setType(MsgType.MSG_TYPE_REQUEST_VALIDATE_TOKEN)
                .setRequestValidateToken(
                    RequestValidateToken.newBuilder().setToken(candidate).build()
                )
                .build()
        )
    }

    /** Response to a RequestValidateToken sent by submitToken(). */
    fun onValidateTokenResponse(valid: Boolean) {
        if (valid) {
            token = pendingCandidate
            pendingCandidate = ""
            _mutablePlaylistsEnabled.value = true
            _tokenPrompt.value = null
            if (awaitingInitialHandshake) {
                awaitingInitialHandshake = false
                onConnectHandshakeReady()
            }
        } else {
            token = ""
            pendingCandidate = ""
            _tokenPrompt.value = TokenPromptState.Invalid
        }
    }

    /** User dismissed the Invalid step (OK) - back to Entry for a retry. */
    fun dismissInvalid() {
        _tokenPrompt.value = TokenPromptState.Entry
    }

    /**
     * User tapped Bypass - proceeds without a token, mutable playlists
     * disabled. If this resolved the initial connect-time prompt, the
     * connection still needs to proceed past handshake (read-only is still
     * a valid way to use the app).
     */
    fun bypassToken() {
        token = ""
        pendingCandidate = ""
        _mutablePlaylistsEnabled.value = false
        _tokenPrompt.value = null
        if (awaitingInitialHandshake) {
            awaitingInitialHandshake = false
            onConnectHandshakeReady()
        }
    }

    /**
     * Server disconnected us for too many failed validate attempts (see
     * ReasonDisconnect.REASON_DISCONNECT_TOO_MANY_FAILED_ATTEMPTS). Shown as
     * its own explicit step rather than folded into the generic disconnect
     * banner - the caller is expected to disconnect and exit the app once
     * the user acknowledges it, since there's nothing left to retry.
     */
    fun onLockedOut() {
        token = ""
        pendingCandidate = ""
        _tokenPrompt.value = TokenPromptState.LockedOut
    }

    /**
     * Called whenever the connection ends, to reset to a clean slate for the
     * next connection attempt. Deliberately does nothing while LockedOut is
     * showing: StrawberryConnection's own silent-reconnect logic doesn't
     * know this disconnect was auth-related and may keep retrying in the
     * background, eventually surfacing as a generic connection Error - that
     * must not silently clear the LockedOut dialog out from under the user
     * before they've acknowledged it.
     */
    fun reset() {
        if (_tokenPrompt.value == TokenPromptState.LockedOut) return
        awaitingInitialHandshake = false
        token = ""
        pendingCandidate = ""
        _mutablePlaylistsEnabled.value = true
        _tokenPrompt.value = null
    }
}
