package com.zudiewiener.strawberryremote.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zudiewiener.strawberryremote.R
import com.zudiewiener.strawberryremote.ui.theme.StrawberryRemoteAndroidTheme
import com.zudiewiener.strawberryremote.util.SharedViewModel

class MainActivity : ComponentActivity() {
    private val sharedViewModel: SharedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StrawberryRemoteAndroidTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background image — underlies all screens
                    FadedBackground()

                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "welcome"
                    ) {
                        composable("welcome") { WelcomeScreen(navController) }
                        composable("connect") { ConnectScreen(navController, sharedViewModel) }
                        composable("songInfo") { SongInfoScreen(navController, sharedViewModel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FadedBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.strawberry),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f  // 30% opacity — adjust to taste
        )
    }
}