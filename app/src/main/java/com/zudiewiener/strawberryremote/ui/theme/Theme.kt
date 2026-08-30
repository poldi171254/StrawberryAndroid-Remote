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

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    background = DarkSurface,
    onBackground = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    // Used for header/chrome elements that need to read as a distinct region
    // from the plain surface/surfaceVariant tones above. surfaceContainerHigh
    // (AppBar) is deliberately more opaque than surfaceContainer (playlist
    // tab row) so the two don't blend into one indistinguishable block -
    // see DarkHeaderSurface / DarkTabRowSurface.
    surfaceContainerHigh = DarkHeaderSurface,
    surfaceContainer = DarkTabRowSurface
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    surface = LightSurface,
    onSurface = LightOnSurface,
    background = LightSurface,
    onBackground = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerHigh = LightHeaderSurface,
    surfaceContainer = LightTabRowSurface
)

@Composable
fun StrawberryRemoteAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: derives accent colors (primary/secondary) from the
    // device's wallpaper on Android 12+ (API 31+). Surface and text roles
    // stay pinned to our own fixed values below, since wallpaper-derived
    // extraction doesn't guarantee readable contrast against our translucent
    // background image. Falls back entirely to the fixed Strawberry brand
    // palette on older devices, where the dynamic color APIs don't exist.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamicScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme) {
                dynamicScheme.copy(
                    surface = DarkSurface,
                    onSurface = DarkOnSurface,
                    background = DarkSurface,
                    onBackground = DarkOnBackground,
                    surfaceVariant = DarkSurfaceVariant,
                    onSurfaceVariant = DarkOnSurfaceVariant,
                    surfaceContainerHigh = DarkHeaderSurface,
                    surfaceContainer = DarkTabRowSurface
                )
            } else {
                dynamicScheme.copy(
                    surface = LightSurface,
                    onSurface = LightOnSurface,
                    background = LightSurface,
                    onBackground = LightOnBackground,
                    surfaceVariant = LightSurfaceVariant,
                    onSurfaceVariant = LightOnSurfaceVariant,
                    surfaceContainerHigh = LightHeaderSurface,
                    surfaceContainer = LightTabRowSurface
                )
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}