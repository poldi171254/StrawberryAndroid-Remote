package com.zudiewiener.strawberryremote.util
/**
 *  Constants for the Strawberry NetworkRemote protocol.
 */

 object ProtocolConstants {
	// Protocol version history:
	// 1 - initial protocol (song info, transport control, engine state push)
	// 2 - position/length in ResponseSongMetadata, version field in Message
	const val PROTOCOL_VERSION = 3

	// Oldest server protocol version this app accepts (unused for now;
	// mirrors the server-side kMinSupportedVersion concept).
	const val MIN_SUPPORTED_VERSION = 2
 }
