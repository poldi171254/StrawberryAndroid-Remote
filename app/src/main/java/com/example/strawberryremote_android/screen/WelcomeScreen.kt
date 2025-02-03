package com.example.strawberryremote_android.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.strawberryremote_android.R
import com.example.strawberryremote_android.util.SharedViewModel

class MainActivity : ComponentActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "welcome") {
                composable("welcome") { WelcomeScreen(navController) }
                composable("connect") { ConnectScreen(navController, sharedViewModel) }
                composable("songInfo") { SongInfoScreen(navController, sharedViewModel) }
            }
        }
    }
}

@Composable
fun WelcomeScreen(navController: NavController) {
    val backgroundImage = painterResource(id = R.drawable.strawberry)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Gray) // Optional fallback
    ){
        Image(
            painter = backgroundImage,
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to the Strawberry Remote",
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("connect") }) {
                Text(text = "Continue")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { /* Terminate the app */ }) {
                Text(text = "Finish")
            }
        }
    }

}