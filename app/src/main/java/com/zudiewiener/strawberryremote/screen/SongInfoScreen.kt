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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.zudiewiener.strawberryremote.logic.AuthController
import com.zudiewiener.strawberryremote.net.ConnectionState
import com.zudiewiener.strawberryremote.screen.common.PlaybackIconButton
import com.zudiewiener.strawberryremote.screen.songinfo.MAX_PREVIOUS_ROWS_SHOWN
import com.zudiewiener.strawberryremote.screen.songinfo.PlaylistTabsRow
import com.zudiewiener.strawberryremote.screen.songinfo.QueueTable
import com.zudiewiener.strawberryremote.util.SharedViewModel
import kotlinx.coroutines.launch
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.RequestNextTrack
import nw.remote.RequestPause
import nw.remote.RequestPlay
import nw.remote.RequestPreviousTrack

@Composable
fun SongInfoScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val remainingTime by sharedViewModel.remainingTime.collectAsState()
    val playerStatus by sharedViewModel.playerStatus.collectAsState()
    val connectionState by sharedViewModel.connectionState.collectAsState()
    val serverShutdown by sharedViewModel.serverShutdown.collectAsState()

    val playlists by sharedViewModel.playlists.collectAsState()
    val activePlaylistIndex by sharedViewModel.activePlaylistIndex.collectAsState()
    val viewedPlaylistIndex by sharedViewModel.viewedPlaylistIndex.collectAsState()
    val columns by sharedViewModel.columns.collectAsState()
    val previousRows by sharedViewModel.previousRows.collectAsState()
    val currentRow by sharedViewModel.currentRow.collectAsState()
    val upcomingRows by sharedViewModel.upcomingRows.collectAsState()
    val actionError by sharedViewModel.actionError.collectAsState()
    val mutablePlaylistsEnabled by sharedViewModel.mutablePlaylistsEnabled.collectAsState()
    val tokenPrompt by sharedViewModel.tokenPrompt.collectAsState()

    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Message for the modal shutdown dialog; null means no dialog.
    var shutdownMessage by remember { mutableStateOf<String?>(null) }

    // React to connection loss.
    //  - Strawberry closed on the desktop: modal dialog, OK exits the app,
    //    since there is nothing left to reconnect to.
    //  - Anything else (rejection, network loss): brief message, then back to
    //    the connect screen so the user can try again.
    LaunchedEffect(connectionState) {
        // A too-many-failed-attempts lockout is handled entirely by the
        // global TokenPromptDialog (MainActivity) - skip the ordinary
        // error/disconnect handling below so they don't stack.
        if (tokenPrompt is AuthController.TokenPromptState.LockedOut) return@LaunchedEffect
        when (val state = connectionState) {
            is ConnectionState.Error -> {
                Log.d("SongInfoScreen", "Error: ${state.message}, serverShutdown=$serverShutdown")
                if (serverShutdown) {
                    shutdownMessage = state.message
                } else {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                    navController.navigate("connect") {
                        popUpTo("connect") { inclusive = true }
                    }
                }
            }
            is ConnectionState.Disconnected -> {
                Log.d("SongInfoScreen", "Disconnected state")
                navController.navigate("connect") {
                    popUpTo("connect") { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    // Failed add/remove actions surface as a one-shot snackbar.
    LaunchedEffect(actionError) {
        actionError?.let { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
            sharedViewModel.consumeActionError()
        }
    }

    // Lifecycle observer — requests fresh song info on resume from sleep/background
    // Also handles disconnect when leaving the screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sharedViewModel.requestSongInfo()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sharedViewModel.disconnect()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            PlaylistTabsRow(
                playlists = playlists,
                selectedIndex = viewedPlaylistIndex,
                activeIndex = activePlaylistIndex,
                onSelect = { index -> sharedViewModel.selectPlaylistTab(index) }
            )

            // Queue area scrolls vertically instead of being sized to
            // exactly fit the screen - the server's configured playlist
            // window can hold up to 100 rows (see
            // QueueController.requestPlaylistSongs), far more than fits on
            // any phone screen at once. Staying in sync as playback
            // progresses is handled by server pushes (PLAYLIST_ADVANCED /
            // full resends), not by the client re-requesting.
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                // Column widths are computed inside QueueTable from actual
                // header/content measurements (see computeColumnWidths) - it
                // needs the raw available width, not a pre-divided guess.
                val availableWidth = this.maxWidth

                QueueTable(
                    columns = columns,
                    availableWidth = availableWidth,
                    previousRows = previousRows.takeLast(MAX_PREVIOUS_ROWS_SHOWN),
                    currentRow = currentRow,
                    upcomingRows = upcomingRows,
                    playlists = playlists,
                    viewedPlaylistIndex = viewedPlaylistIndex,
                    mutablePlaylistsEnabled = mutablePlaylistsEnabled,
                    onMutationBlocked = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Playlist changes are unavailable: enter the server's " +
                                        "token, or ask the server to disable the token requirement.",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onPlayRow = { rowIndex -> sharedViewModel.requestPlaySong(rowIndex) },
                    onAddCurrentToPlaylist = { targetId, newName ->
                        sharedViewModel.addCurrentSongToPlaylist(targetId, newName)
                    },
                    onRemoveRow = { rowIndex -> sharedViewModel.removeSongFromPlaylist(rowIndex) }
                )
            }

            // Status, remaining time, and playback controls combined into one
            // compact row - this used to be three stacked rows plus a
            // full-width Exit button, which left almost no vertical space for
            // the queue table in landscape (short screen height).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerStatus,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = remainingTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaybackIconButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_PREVIOUS)
                                    .setRequestPreviousTrack(
                                        RequestPreviousTrack.newBuilder()
                                            .setPrevious(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
                                    .setRequestPlay(
                                        RequestPlay.newBuilder().setPlay(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.Pause,
                        contentDescription = "Pause",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_PAUSE)
                                    .setRequestPause(
                                        RequestPause.newBuilder().setPause(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        onClick = {
                            sharedViewModel.sendMessage(
                                Message.newBuilder()
                                    .setType(MsgType.MSG_TYPE_REQUEST_NEXT)
                                    .setRequestNextTrack(
                                        RequestNextTrack.newBuilder().setNext(true).build()
                                    )
                                    .build()
                            )
                        }
                    )
                    PlaybackIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Exit",
                        containerColor = MaterialTheme.colorScheme.outline,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { activity?.finish() }
                    )
                }
            }
        }
    }

    // Modal dialog shown only when Strawberry itself has shut down.
    // Not dismissable by back-press or tapping outside: OK closes the app.
    shutdownMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Disconnected") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text("OK")
                }
            }
        )
    }
}