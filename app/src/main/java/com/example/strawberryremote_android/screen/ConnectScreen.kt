package com.example.strawberryremote_android.screen

import android.util.Log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

import androidx.compose.ui.unit.sp
import com.example.strawberryremote_android.util.SharedViewModel

import kotlinx.coroutines.launch
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.InetSocketAddress

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    var ipAddress by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("5050") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    suspend fun connectToServer(ipAddress: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                // Set a connection timeout (e.g., 5 seconds)
                socket.connect(InetSocketAddress(ipAddress, port), 2000)
                Log.d("MyRemote", "Connected to server at $ipAddress:$port")
                sharedViewModel.socket = socket
                true // Connection successful
            } catch (e: SocketTimeoutException) {
                Log.d("MyRemote", "Connection timed out: ${e.message}")
                false // Connection failed due to timeout
            } catch (e: Exception) {
                Log.d("MyRemote", "Failed to connect: ${e.message}")
                false // Connection failed
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Strawberry Remote",
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("Please enter the Server IP address") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Please enter the port number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    // Validate the port number
                    val portNumber = port.toIntOrNull()
                    if (portNumber == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Invalid port number")
                        }
                        return@Button
                    }

                    // Attempt to connect to the server
                    coroutineScope.launch {
                        val isConnected = connectToServer(ipAddress, portNumber)
                        if (isConnected) {
                            // Navigate to SongInfoScreen on success
                            navController.navigate("songInfo")
                        } else {
                            // Show connection error using Snackbar
                            snackbarHostState.showSnackbar("Failed to connect to the server")
                        }
                    }
                }) {
                    Text(text = "Connect")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { /* Terminate the app */ }) {
                    Text(text = "Finish")
                }
            }
        }
    )
}
