package com.example.strawberryremote_android.screen

import android.util.Log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import java.net.NetworkInterface

@Composable
fun ConnectScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    var ipAddress by remember { mutableStateOf("192.168.1.xxx") }
    var port by remember { mutableStateOf("5050") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Get the client's local IP address when the screen is first displayed
    LaunchedEffect(Unit) {
        ipAddress = getLocalIpAddress() ?: "192.168.1.xxx"
    }

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
        topBar = {
            AppBar() // Ensure AppBar is defined here
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding) // Use Scaffold's padding to account for AppBar
                    .padding(20.dp), // Additional padding for content
                verticalArrangement = Arrangement.SpaceBetween, // Space between top and bottom
                horizontalAlignment = Alignment.Start // Left-align content
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "This is a very basic remote for the Strawberry Music Player",
                        fontSize = 18.sp,
                        color = Color(0xFF333333), // Dark gray text
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    Text(
                        text = "Please enter the Players IP address",
                        fontSize = 14.sp,
                        color = Color(0xFF333333), // Dark gray text
                    )
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("Server IP Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, // Evenly space buttons
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            // Validate and connect
                            val portNumber = port.toIntOrNull()
                            if (portNumber == null) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Invalid port number")
                                }
                                return@Button
                            }
                            coroutineScope.launch {
                                val isConnected = connectToServer(ipAddress, portNumber)
                                if (isConnected) {
                                    navController.navigate("songInfo")
                                } else {
                                    snackbarHostState.showSnackbar("Failed to connect to the server")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary // Text color for primary
                        )
                    ) {
                        Text(text = "Connect", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val activity: MainActivity = MainActivity()
                            activity.finish()
                            System.exit(0)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary // Text color for secondary
                        )
                    ) {
                        Text(text = "Finish", color = Color.White)
                    }
                }
            }
        }
    )
}

fun getLocalIpAddress(): String? {
    try {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val networkInterface = networkInterfaces.nextElement()
            // Skip loopback and inactive interfaces
            if (networkInterface.isLoopback || !networkInterface.isUp) continue

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                // Check if the address is an IPv4 address
                if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                    val ip = address.hostAddress
                    // Format the IP address to show only the first three parts
                    return ip?.substringBeforeLast('.') + ".xxx"
                }
            }
        }
    } catch (e: Exception) {
        Log.e("MyRemote", "Error getting local IP address: ${e.message}")
    }
    return null
}