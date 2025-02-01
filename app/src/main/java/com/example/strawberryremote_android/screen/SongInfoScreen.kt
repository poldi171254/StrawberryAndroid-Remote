package com.example.strawberryremote_android.screen

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.io.OutputStream
import java.net.Socket

@Composable
fun SongInfoScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val socket = sharedViewModel.socket

    // State variables to hold song metadata and player state
    var title by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var playcount by remember { mutableStateOf("") }
    var songlength by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    // Function to send a message to the server and wait for a response
    fun sendMessageAndWaitForResponse(message: Message) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Send the message to the server
                val outputStream: OutputStream? = socket?.getOutputStream()
                outputStream?.write(message.toByteArray())
                outputStream?.flush()

                // Wait for a response from the server
                val inputStream = socket?.getInputStream()
                val response = Message.parseFrom(inputStream)

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
                        playcount = songMetadata.playcount.toString()
                        songlength = songMetadata.songlength
                        statusMessage = when (playerState) {
                            PlayerState.PLAYER_STATUS_PLAYING -> "Playing"
                            else -> "Paused"
                        }
                    }
                    else -> {
                        // Handle other message types if needed
                    }
                }
            } catch (e: Exception) {
                // Handle errors (e.g., connection issues)
                statusMessage = "Error: ${e.message}"
            }
        }
    }

    // UI for the SongInfoScreen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Title: $title")
        Text(text = "Album: $album")
        Text(text = "Artist: $artist")
        Text(text = "Year: $year")
        Text(text = "Genre: $genre")
        Text(text = "Playcount: $playcount")
        Text(text = "Songlength: $songlength")
        Text(text = "Status Message: $statusMessage")
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                // Create a RequestNextTrack message
                val requestNextTrack = RequestNextTrack.newBuilder().setNext(true).build()
                val message = Message.newBuilder()
                    .setType(MsgType.MSG_TYPE_REQUEST_NEXT)
                    .setRequestNextTrack(requestNextTrack)
                    .build()

                // Send the message and wait for a response
                sendMessageAndWaitForResponse(message)
            }) {
                Text(text = "Next")
            }
            // Other buttons (Play, Pause, Previous, Finish) can be added here
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { /* Disconnect and close the app */ }) {
            Text(text = "Finish")
        }
    }
}