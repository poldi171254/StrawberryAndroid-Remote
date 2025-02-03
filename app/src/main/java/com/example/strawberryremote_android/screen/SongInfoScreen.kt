package com.example.strawberryremote_android.screen

import android.util.Log
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
import nw.remote.RequestPause
import nw.remote.RequestPlay
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
        Text(text = "Playcount: $playCount")
        Text(text = "Songlength: $songLength")
        Text(text = "Status Message: $statusMessage")
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                // Create a Play Track message
                val requestPlayTrack = RequestPlay.newBuilder().setPlay(true).build()
                val message = Message.newBuilder()
                .setType(MsgType.MSG_TYPE_REQUEST_PLAY)
                    .setRequestPlay(requestPlayTrack)
                    .build()
                sendMessageAndWaitForResponse(message)
            }) {
                Text(text = "Play")
            }
            Button(onClick = {
                // Create a Pause message
                val requestPause = RequestPause.newBuilder().setPause(true).build()
                val message = Message.newBuilder()
                    .setType(MsgType.MSG_TYPE_REQUEST_PAUSE)
                    .setRequestPause(requestPause)
                    .build()
                // Send the message and wait for a response
                sendMessageAndWaitForResponse(message)
            }) {
                Text(text = "Pause")
            }
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
                Text(text = "Previous")
            }
            // Other buttons (Play, Pause, Previous, Finish) can be added here
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { /* Disconnect and close the app */ }) {
            Text(text = "Finish")
        }
    }
}