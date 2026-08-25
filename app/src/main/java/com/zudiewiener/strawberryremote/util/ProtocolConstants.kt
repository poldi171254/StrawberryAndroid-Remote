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

/**
 *  Constants for the Strawberry NetworkRemote protocol.
 */

object ProtocolConstants {
	// Protocol version history:
	// 1 - initial protocol (song info, transport control, engine state push)
	// 2 - position/length in ResponseSongMetadata, version field in Message
	// 3 - playlist listing / initial info bundling
	// 4 - playlist songs queue view, play/add/remove-from-playlist requests,
	//     playlist changed/activated broadcasts
	// TODO: confirm whether the token-auth feature (ResponseConnect.auth_enabled,
	//     per-request tokens, reject_reason, RequestValidateToken/
	//     ResponseValidateToken, AuthStatusChanged,
	//     REASON_DISCONNECT_TOO_MANY_FAILED_ATTEMPTS) is already covered by
	//     version 5, or needs its own bump to 6 - see chat note.
	const val PROTOCOL_VERSION = 5

	// Oldest server protocol version this app accepts (unused for now;
	// mirrors the server-side kMinSupportedVersion concept).
	const val MIN_SUPPORTED_VERSION = 5
}
