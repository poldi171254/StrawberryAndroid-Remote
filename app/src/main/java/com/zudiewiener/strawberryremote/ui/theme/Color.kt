/*
 * Client for the Strawberry Music Player
 * Copyright 2026, Leopold List <leo@zudiewiener.com>
 *
 * Client for the Strawberry Music Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Client for the Strawberry Music Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Client for the Strawberry Music Player.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */
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
// Header/chrome tone (AppBar) - distinct from the plain translucent-black
// surface/surfaceVariant above so top-level navigation reads as its own
// region rather than blending into the content behind it. Muted strawberry
// red, more opaque than DarkSurface so it holds as a solid bar against the
// background image.
val DarkHeaderSurface = Color(0xCC5C1A1A)
// Same hue as DarkHeaderSurface but lower opacity, for the playlist tab row
// just below the AppBar - gives it a visibly distinct (but related) tone
// rather than blending into an identical block with the title bar above it.
val DarkTabRowSurface = Color(0x995C1A1A)

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
// Header/chrome tone - light strawberry pink, more opaque than LightSurface.
val LightHeaderSurface = Color(0xCCFFCDD2)
// Same hue as LightHeaderSurface but lower opacity, for the playlist tab
// row - see DarkTabRowSurface for why this needs to differ from the AppBar.
val LightTabRowSurface = Color(0x99FFCDD2)