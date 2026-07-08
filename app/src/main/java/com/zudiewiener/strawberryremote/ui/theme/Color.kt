package com.zudiewiener.strawberryremote.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colours
val StrawberryRed = Color(0xFFB71C1C)
val StrawberryRedLight = Color(0xFFEF9A9A)
val StrawberryGreen = Color(0xFF2E7D32)
val StrawberryGreenLight = Color(0xFFA5D6A7)

// Dark theme (used over dark overlay background)
val DarkPrimary = StrawberryRedLight      // light red — readable on dark
val DarkSecondary = StrawberryGreenLight  // light green — readable on dark
val DarkOnPrimary = Color(0xFF1C0000)
val DarkOnSecondary = Color(0xFF00210B)
val DarkSurface = Color(0x99000000)       // semi-transparent dark surface
val DarkOnSurface = Color(0xFFFFFFFF)     // white text
val DarkOnBackground = Color(0xFFFFFFFF)  // white text
val DarkSurfaceVariant = Color(0x66000000) // darker semi-transparent
val DarkOnSurfaceVariant = Color(0xFFFFFFFF)

// Light theme (fallback)
val LightPrimary = StrawberryRed
val LightSecondary = StrawberryGreen
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSurface = Color(0x99FFFFFF)      // semi-transparent light surface
val LightOnSurface = Color(0xFF1C1B1F)
val LightOnBackground = Color(0xFF1C1B1F)
val LightSurfaceVariant = Color(0x66FFFFFF)
val LightOnSurfaceVariant = Color(0xFF1C1B1F)