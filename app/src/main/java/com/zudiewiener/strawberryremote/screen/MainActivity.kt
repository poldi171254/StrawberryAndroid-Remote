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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zudiewiener.strawberryremote.R
import com.zudiewiener.strawberryremote.ui.theme.StrawberryRemoteAndroidTheme
import com.zudiewiener.strawberryremote.util.SharedViewModel

class MainActivity : ComponentActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StrawberryRemoteAndroidTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    FadedBackground()
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "welcome"
                    ) {
                        composable("welcome") { WelcomeScreen(navController) }
                        composable("connect") { ConnectScreen(navController, sharedViewModel) }
                        composable("songInfo") { SongInfoScreen(navController, sharedViewModel) }
                    }

                    // Rendered above the NavHost so it appears regardless of
                    // which screen is active - covers both the initial
                    // connect-time prompt and a mid-session AuthStatusChanged
                    // prompt with the same dialog.
                    val tokenPromptState by sharedViewModel.tokenPrompt.collectAsState()
                    tokenPromptState?.let { state ->
                        TokenPromptDialog(
                            state = state,
                            onSubmit = { token -> sharedViewModel.submitToken(token) },
                            onBypass = { sharedViewModel.bypassToken() },
                            onCancel = {
                                sharedViewModel.exitAfterTokenPrompt()
                                finish()
                            },
                            onInvalidOk = { sharedViewModel.dismissInvalidToken() },
                            onLockedOutOk = {
                                sharedViewModel.exitAfterTokenPrompt()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FadedBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.strawberry),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.2f
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
    }
}
