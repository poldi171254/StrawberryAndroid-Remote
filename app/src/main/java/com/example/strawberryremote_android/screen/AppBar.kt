package com.example.strawberryremote_android.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp) // Add 20dp padding at the top
            .padding(16.dp), // Optional: Add additional padding around the text
        contentAlignment = Alignment.Center // Center the content inside the Box
    ) {
        Text(
            text = "Strawberry Remote",
            fontSize = 24.sp,
            color = Color(0xFF333333), // Dark gray text
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center // Center the text horizontally
        )
    }
}