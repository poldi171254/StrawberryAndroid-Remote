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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zudiewiener.strawberryremote.logic.AuthController

/**
 * Renders whichever step of the token-entry flow AuthController currently
 * wants shown. Rendered once, in MainActivity, above the NavHost, so it
 * appears regardless of which screen is active - both the initial
 * connect-time prompt and a mid-session AuthStatusChanged prompt use this
 * exact same dialog. None of the three steps are dismissable by back-press
 * or tapping outside: each is a required decision point, matching the app's
 * other blocking dialogs (fatal connection error, server shutdown).
 */
@Composable
fun TokenPromptDialog(
    state: AuthController.TokenPromptState,
    onSubmit: (String) -> Unit,
    onBypass: () -> Unit,
    onCancel: () -> Unit,
    onInvalidOk: () -> Unit,
    onLockedOutOk: () -> Unit
) {
    when (state) {
        is AuthController.TokenPromptState.Entry -> EntryDialog(onSubmit, onBypass, onCancel)
        is AuthController.TokenPromptState.Invalid -> InvalidDialog(onInvalidOk)
        is AuthController.TokenPromptState.LockedOut -> LockedOutDialog(onLockedOutOk)
    }
}

/**
 * Three actions (Submit / Bypass / Cancel) don't fit AlertDialog's two-slot
 * confirm/dismiss button layout without stacking or wrapping oddly - this
 * uses a plain Dialog with a hand-built button row instead, styled to match
 * AlertDialog's default look (same shape/elevation/color) so it's visually
 * consistent with the app's other dialogs.
 */
@Composable
private fun EntryDialog(
    onSubmit: (String) -> Unit,
    onBypass: () -> Unit,
    onCancel: () -> Unit
) {
    var token by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Remote Token Required",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This setting on the player require a token to make playlist changes. " +
                            "Enter the token configured in Strawberry's settings, or " +
                            "bypass to continue in read-only mode.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    TextButton(onClick = onBypass) {
                        Text("Bypass")
                    }
                    TextButton(onClick = { onSubmit(token) }) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
private fun InvalidDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Incorrect Token") },
        text = { Text("The token you entered was not accepted. Please try again.") },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun LockedOutDialog(onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Too Many Attempts") },
        text = {
            Text(
                "This device has been disconnected after too many failed token attempts. " +
                        "Closing the app - reopen it to try again."
            )
        },
        confirmButton = {
            TextButton(onClick = onOk) {
                Text("OK")
            }
        }
    )
}