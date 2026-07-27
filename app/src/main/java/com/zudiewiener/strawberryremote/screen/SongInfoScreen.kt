package com.zudiewiener.strawberryremote.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.zudiewiener.strawberryremote.util.ConnectionState
import com.zudiewiener.strawberryremote.util.SharedViewModel
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.RequestNextTrack
import nw.remote.RequestPause
import nw.remote.RequestPlay
import nw.remote.RequestPreviousTrack

@Composable
fun SongInfoScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val songInfo by sharedViewModel.songInfo.collectAsState()
    val playerStatus by sharedViewModel.playerStatus.collectAsState()
    val connectionState by sharedViewModel.connectionState.collectAsState()
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Navigate back to connect screen if connection is lost
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Error ||
            connectionState is ConnectionState.Disconnected) {
            navController.navigate("connect") {
                popUpTo("connect") { inclusive = true }
            }
        }
    }

    // Request initial song info on first load
    LaunchedEffect(Unit) {
        sharedViewModel.requestSongInfo()
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
        topBar = { AppBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Song info fields
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                SongInfoField(label = "Title", value = songInfo.title)
                Spacer(modifier = Modifier.height(12.dp))
                SongInfoField(label = "Album", value = songInfo.album)
                Spacer(modifier = Modifier.height(12.dp))
                SongInfoField(label = "Artist", value = songInfo.artist)
                Spacer(modifier = Modifier.height(12.dp))
                SongInfoField(label = "Year", value = songInfo.year)
                Spacer(modifier = Modifier.height(12.dp))
                SongInfoField(label = "Genre", value = songInfo.genre)
                Spacer(modifier = Modifier.height(12.dp))
                SongInfoField(label = "Play Count", value = songInfo.playCount)
                Spacer(modifier = Modifier.height(12.dp))
                SongInfoField(label = "Length", value = songInfo.songLength)
            }

            // Player status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status:",
                    modifier = Modifier.width(80.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    Text(
                        text = playerStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
            }

            // Exit button
            Button(
                onClick = { activity?.finish() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(
                    text = "Exit",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PlaybackIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            )
            .size(56.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun SongInfoField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(90.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}