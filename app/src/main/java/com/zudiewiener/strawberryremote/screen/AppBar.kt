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
package com.zudiewiener.strawberryremote.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppBar() {
    var showAbout by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Text(
            text = "Strawberry Remote",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
        IconButton(
            onClick = { showAbout = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "About",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "Unknown"
        } catch (e: Exception) {
            Log.e("AppBar", "Could not get version name: ${e.message}")
            "Unknown"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Strawberry Remote",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = """
                    Version $versionName

                    A network remote control for the Strawberry Music Player.

                    Setting up the remote:
                    • In Strawberry, go to Tools → Settings → Remote and enable "Use Remote Network Client".
                    • Note the Player IP Address shown there - enter this in the client to connect (it cannot be changed).
                    • Note the Remote Port - this can be changed if needed.
                    • Click Apply or OK at the bottom of the settings screen for changes to take effect.
                    • If you're using a firewall, you may need to allow local connections for the selected port.

                    Playlist token (optional):
                    Playlist changes - adding, removing, or creating a playlist - can be protected with a token, so only clients that know it can modify your playlists. This guards against unwanted changes from other devices on your network.
                    • Enabled in Strawberry's Remote settings by setting a Remote Token - leave that field empty to disable the protection entirely.
                    • If a token is set, the client asks for it when you connect. Enter it to enable playlist changes, or choose Bypass to continue read-only (you can still view and play, but not add, remove, or create playlists).
                    • An incorrect token can be retried - after too many incorrect attempts, the server disconnects the client as a safeguard.
                    • Turning the token requirement on or off on the player takes effect immediately for clients already connected, prompting for the token if it becomes required.

                    For the best view of the song list, enable auto-rotate on your device so the screen can switch to landscape and show more columns.

                    Song list:
                    • Double-tap a song to play it.
                    • Long-press a song for options - add the currently playing song to another playlist, or remove a song from this playlist.

                    © 2026 zudiewiener.com

                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}