package com.zudiewiener.strawberryremote.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zudiewiener.strawberryremote.util.ConnectionState
import com.zudiewiener.strawberryremote.util.SharedViewModel
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.RequestNextTrack
import nw.remote.RequestPause
import nw.remote.RequestPlay
import nw.remote.RequestPreviousTrack
import nw.remote.RequestSongMetadata

@Composable
fun SongInfoScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val songInfo by sharedViewModel.songInfo.collectAsState()
    val playerStatus by sharedViewModel.playerStatus.collectAsState()
    val connectionState by sharedViewModel.connectionState.collectAsState()
    val activity = LocalActivity.current

    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Error ||
            connectionState is ConnectionState.Disconnected) {
            navController.navigate("connect") {
                popUpTo("connect") { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        val request = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_SONG_INFO)
            .setRequestSongMetadata(
                RequestSongMetadata.newBuilder().setSend(true).build()
            )
            .build()
        sharedViewModel.sendMessage(request)
    }

    DisposableEffect(Unit) {
        onDispose {
            sharedViewModel.disconnect()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                SongInfoField(label = "Title", value = songInfo.title)
                Spacer(modifier = Modifier.height(16.dp))
                SongInfoField(label = "Album", value = songInfo.album)
                Spacer(modifier = Modifier.height(16.dp))
                SongInfoField(label = "Artist", value = songInfo.artist)
                Spacer(modifier = Modifier.height(16.dp))
                SongInfoField(label = "Year", value = songInfo.year)
                Spacer(modifier = Modifier.height(16.dp))
                SongInfoField(label = "Genre", value = songInfo.genre)
                Spacer(modifier = Modifier.height(16.dp))
                SongInfoField(label = "Play Count", value = songInfo.playCount)
                Spacer(modifier = Modifier.height(16.dp))
                SongInfoField(label = "Song Length", value = songInfo.songLength)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 5.dp),
            ) {
                Text(
                    text = "Status:",
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        val message = Message.newBuilder()
                            .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
                            .setRequestPlay(RequestPlay.newBuilder().setPlay(true).build())
                            .build()
                        sharedViewModel.sendMessage(message)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Play",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = {
                        val message = Message.newBuilder()
                            .setType(MsgType.MSG_TYPE_REQUEST_PAUSE)
                            .setRequestPause(RequestPause.newBuilder().setPause(true).build())
                            .build()
                        sharedViewModel.sendMessage(message)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Pause",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = {
                        val message = Message.newBuilder()
                            .setType(MsgType.MSG_TYPE_REQUEST_NEXT)
                            .setRequestNextTrack(
                                RequestNextTrack.newBuilder().setNext(true).build()
                            )
                            .build()
                        sharedViewModel.sendMessage(message)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = {
                        val message = Message.newBuilder()
                            .setType(MsgType.MSG_TYPE_REQUEST_PREVIOUS)
                            .setRequestPreviousTrack(
                                RequestPreviousTrack.newBuilder().setPrevious(true).build()
                            )
                            .build()
                        sharedViewModel.sendMessage(message)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Previous",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

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
fun SongInfoField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(100.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
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