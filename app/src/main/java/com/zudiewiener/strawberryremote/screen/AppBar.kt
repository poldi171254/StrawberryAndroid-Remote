package com.zudiewiener.strawberryremote.screen

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar() {
    var showAbout by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Strawberry Remote") },
        actions = {
            IconButton(onClick = { showAbout = true }) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "About",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets
    )

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
                    
                    © 2026 zudiewiener.com
                    
                    Requires Strawberry Music Player with Remote Network Client enabled.
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