package com.example.strawberryremote_android.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.strawberryremote_android.util.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nw.remote.Message
import nw.remote.MsgType
import nw.remote.PlayerState
import nw.remote.RequestNextTrack
import nw.remote.RequestPause
import nw.remote.RequestPlay
import nw.remote.RequestPreviousTrack
import java.io.OutputStream

@Composable
fun SongInfoScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val socket = sharedViewModel.socket

    // State variables to hold song metadata and player state
    var title by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var playCount by remember { mutableStateOf("") }
    var songLength by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    // Function to send a message to the server and wait for a response
    fun sendMessageAndWaitForResponse(message: Message) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send the message to the server
                Log.d("MyRemote", "Trying OutputStream")
                val outputStream: OutputStream = socket!!.getOutputStream()
                outputStream.write(message.toByteArray())
                outputStream.flush()
                Log.d("MyRemote", "Message sent")
                if (message.type == MsgType.MSG_TYPE_REQUEST_PAUSE){
                    statusMessage = "Paused"
                }
                else {
                    // Wait for a response from the server
                    Log.d("MyRemote", "Trying InputStream")
                    val inputStream = socket?.getInputStream()
                    val buffer = ByteArray(2048)

                    // Read the response from the server
                    val bytesRead = inputStream!!.read(buffer)
                    Log.d("MyRemote", "There are $bytesRead bytes read")

                    if (bytesRead > 0) {
                        Log.d("MyRemote", "Got InputStream")

                        // Create a new byte array with only the valid bytes
                        val messageBytes = buffer.copyOfRange(0, bytesRead)

                        // Parse the message
                        val response = Message.parseFrom(messageBytes)
                        //Log.d("MyRemote", "Got Response of type $response.type and $messageBytes bytes")


                        // Process the response
                        when (response.type) {
                            MsgType.MSG_TYPE_REPLY_SONG_INFO -> {
                                val songMetadata = response.responseSongMetadata.songMetadata
                                val playerState = response.responseSongMetadata.playerState

                                // Update the UI with the new song metadata
                                title = songMetadata.title
                                album = songMetadata.album
                                artist = songMetadata.artist
                                year = songMetadata.stryear
                                genre = songMetadata.genre
                                playCount = songMetadata.playcount.toString()
                                songLength = songMetadata.songlength
                                statusMessage = when (playerState) {
                                    PlayerState.PLAYER_STATUS_PLAYING -> "Playing"
                                    else -> "Paused"
                                }
                            }
                            else -> {
                                Log.d("MyRemote", "Error reading Message")
                            }
                        }

                    }
                }

            } catch (e: Exception) {
                // Handle errors (e.g., connection issues)
                Log.d("MyRemote", "Message exception: ${e.message}")
                statusMessage = "Error: ${e.message}"
            }
        }
    }

    // Function to request song information (RequestPlay message)
    fun requestSongInfo() {
        val requestPlayMessage = Message.newBuilder()
            .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
            .build()

        // Send the message and wait for a response
        Log.d("MyRemote", "Sending message for Song Info")
        sendMessageAndWaitForResponse(requestPlayMessage)
    }

    // Request song information when the screen is first loaded
    LaunchedEffect(Unit) {
        requestSongInfo()
    }

    // UI for the SongInfoScreen
    Scaffold(
        topBar = {
            AppBar() // Ensure AppBar is defined here
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding) // Use Scaffold's padding to account for AppBar
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween // Push buttons to the bottom
            ) {
                // Song info fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    SongInfoField(label = "Title", value = title)
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoField(label = "Album", value = album)
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoField(label = "Artist", value = artist)
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoField(label = "Year", value = year)
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoField(label = "Genre", value = genre)
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoField(label = "Play Count", value = playCount)
                    Spacer(modifier = Modifier.height(16.dp))
                    SongInfoField(label = "Song Length", value = songLength)
                }

                // Status message
                Row (
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp),
                ) {
                    Text(
                        text = "Player Status:",
                        modifier = Modifier.width(100.dp), // Fixed width for labels
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFADD8E6)) // Light blue background
                            .padding(8.dp)
                    ) {
                        Text(text = statusMessage)
                    }
                }

                // Buttons at the bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            val requestPlayTrack = RequestPlay.newBuilder().setPlay(true).build()
                            val message = Message.newBuilder()
                                .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
                                .setRequestPlay(requestPlayTrack)
                                .build()
                            sendMessageAndWaitForResponse(message)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = "Play")
                    }
                    Button(
                        onClick = {
                            val requestPause = RequestPause.newBuilder().setPause(true).build()
                            val message = Message.newBuilder()
                                .setType(MsgType.MSG_TYPE_REQUEST_PAUSE)
                                .setRequestPause(requestPause)
                                .build()
                            sendMessageAndWaitForResponse(message)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = "Pause")
                    }
                    Button(
                        onClick = {
                            val requestNextTrack = RequestNextTrack.newBuilder().setNext(true).build()
                            val message = Message.newBuilder()
                                .setType(MsgType.MSG_TYPE_REQUEST_NEXT)
                                .setRequestNextTrack(requestNextTrack)
                                .build()
                            sendMessageAndWaitForResponse(message)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = "Next")
                    }
                    Button(
                        onClick = {
                            val requestPreviousTrack = RequestPreviousTrack.newBuilder().setPrevious(true).build()
                            val message = Message.newBuilder()
                                .setType(MsgType.MSG_TYPE_REQUEST_PREVIOUS)
                                .setRequestPreviousTrack(requestPreviousTrack)
                                .build()
                            sendMessageAndWaitForResponse(message)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(text = "Previous")
                    }
                }

                // Finish button
                Button(
                    onClick = {
                        val activity: MainActivity = MainActivity()
                        activity.finish()
                        System.exit(0)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(text = "Finish")
                }
            }
        }
    )
}

@Composable
fun SongInfoField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(100.dp), // Fixed width for labels
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .background(Color(0xFFADD8E6)) // Light blue background
                .padding(8.dp)
        ) {
            Text(text = value)
        }
    }
}