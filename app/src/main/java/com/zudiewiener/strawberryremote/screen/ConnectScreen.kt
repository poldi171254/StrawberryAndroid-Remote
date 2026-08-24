package com.zudiewiener.strawberryremote.screen

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.zudiewiener.strawberryremote.net.ConnectionState
import com.zudiewiener.strawberryremote.util.SharedViewModel
import kotlinx.coroutines.launch
import java.net.Inet4Address

@Composable
fun ConnectScreen(navController: NavController, sharedViewModel: SharedViewModel) {
    val context = LocalContext.current
    val savedConfig by sharedViewModel.savedConfig.collectAsState()
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8888") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val connectionState by sharedViewModel.connectionState.collectAsState()
    val fatalConnectionError by sharedViewModel.fatalConnectionError.collectAsState()
    val activity = LocalActivity.current
    var connectionErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(savedConfig) {
        if (savedConfig != null) {
            ipAddress = savedConfig!!.ip
            port = savedConfig!!.port.toString()
        } else {
            val localIp = getLocalIpAddress(context)
            Log.d("ConnectScreen", "Local IP detected: $localIp")
            if (localIp != null) {
                val subnet = localIp.trim().substringBeforeLast('.')
                ipAddress = "$subnet.xxx"
            } else {
                ipAddress = "192.168.1.xxx" // fallback if no network detected
            }
        }
    }

    LaunchedEffect(connectionState) {
        when (val state = connectionState) {
            is ConnectionState.Ready -> {
                navController.navigate("songInfo")
            }
            is ConnectionState.Error -> {
                // Fatal errors (e.g. incompatible server version) get their
                // own modal below. Everything else - wrong IP, refused
                // connection, timeout - is retryable, so it also gets a
                // visible modal instead of an easy-to-miss snackbar, but one
                // that just dismisses rather than exiting the app.
                if (fatalConnectionError == null) {
                    connectionErrorMessage = state.message
                }
            }
            else -> Unit
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
                .padding(horizontal = 20.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Strawberry Music Player Remote",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(40.dp))
                Text(
                    text = "Enter the player's IP address and port",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = {
                        Text(
                            text = "Server IP Address",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = {
                        Text(
                            text = "Port Number",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val portNumber = port.toIntOrNull()
                        if (portNumber == null || portNumber !in 8888..65535) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Port must be a number between 8888 and 65535"
                                )
                            }
                            return@Button
                        }
                        if (!isValidIp(ipAddress)) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Please enter a valid IP address"
                                )
                            }
                            return@Button
                        }
                        sharedViewModel.connect(ipAddress, portNumber)
                    },
                    enabled = connectionState !is ConnectionState.Connecting,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text(
                        text = if (connectionState is ConnectionState.Connecting)
                            "Connecting..." else "Connect",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = { activity?.finish() },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.outline,
                        contentColor = MaterialTheme.colorScheme.onSurface
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

    // Modal dialog for unrecoverable connection errors (e.g. incompatible
    // server version). Not dismissable by back-press or tapping outside: OK
    // closes the app, since retrying here wouldn't help.
    fatalConnectionError?.let { message ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Can't Connect") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { activity?.finish() }) {
                    Text("OK")
                }
            }
        )
    }

    // Modal dialog for an ordinary (retryable) connection failure - dismisses
    // back to this screen so the user can correct the IP/port and try again.
    connectionErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { connectionErrorMessage = null },
            title = { Text("Connection Failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { connectionErrorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun isValidIp(ip: String): Boolean {
    val parts = ip.trim().split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val num = part.toIntOrNull() ?: return false
        num in 0..255
    }
}

private fun getLocalIpAddress(context: Context): String? {
    return try {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network)
            ?: return null

        linkProperties.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (e: Exception) {
        Log.e("ConnectScreen", "Error getting local IP: ${e.message}")
        null
    }
}