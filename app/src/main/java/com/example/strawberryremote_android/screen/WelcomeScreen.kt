package com.example.strawberryremote_android.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.strawberryremote_android.R
import com.example.strawberryremote_android.ui.theme.StrawberryRemoteAndroidTheme
import com.example.strawberryremote_android.util.SharedViewModel

class MainActivity : ComponentActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StrawberryRemoteAndroidTheme{val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "welcome") {
                    composable("welcome") { WelcomeScreen(navController) }
                    composable("connect") { ConnectScreen(navController, sharedViewModel) }
                    composable("songInfo") { SongInfoScreen(navController, sharedViewModel) }
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    val backgroundImage = painterResource(id = R.drawable.strawberry)
    Scaffold(
        topBar = {
            AppBar() // Ensure AppBar is defined here
        },
        content = { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding) // Use Scaffold's padding to account for AppBar
                    .background(Color(0xFFFFEBEE)) // Light pink background as fallback
            ) {
                Image(
                    painter = backgroundImage,
                    contentDescription = "Strawberry Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween, // Space between top and bottom
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Welcome message (centered)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Welcome to the Strawberry Remote",
                            fontSize = 24.sp,
                            color = Color(0xFF333333), // Dark gray text
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Buttons at the bottom
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, // Evenly space buttons
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { navController.navigate("connect") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary // Text color for primary
                            )
                        ) {
                            Text(text = "Continue")
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
                            Text(text = "Finish")
                        }
                    }
                }
            }
        }
    )
}
