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
            .background(MaterialTheme.colorScheme.surface)
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
                    
                    For the best view of the song list, enable auto-rotate on your device so the screen can switch to landscape and show more columns.
                    
                    Song list:
                    • Double-tap a song to play it.
                    • Long-press a song for options - add the currently playing song to another playlist, or remove a song from this playlist.
                    
                    © 2026 zudiewiener.com
                    
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}