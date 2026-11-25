package com.motomove.himachal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MotoGreen = Color(0xFF0E6B47)
val MotoGreenContainer = Color(0xFFD6F0E4)
val MotoBlueStart = Color(0xFF1565C0)
val MotoBlueEnd = Color(0xFF2196F3)
val MotoOrangeStart = Color(0xFFD84315)
val MotoAmberEnd = Color(0xFFF59E0B)
val MotoPurple = Color(0xFF7C3AED)
val MotoPurpleSoft = Color(0xFFF3EEFF)
val MotoSurfaceBg = Color(0xFFF3F6F4)
val MotoDarkNavy = Color(0xFF0F172A)
val MotoVerifiedGreen = Color(0xFF15803D)

private val MotoMoveColors = lightColorScheme(
    primary = MotoGreen,
    onPrimary = Color.White,
    primaryContainer = MotoGreenContainer,
    onPrimaryContainer = Color(0xFF052E1E),
    secondary = MotoOrangeStart,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Color(0xFF5D2205),
    background = MotoSurfaceBg,
    surface = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEBF0ED)
)

@Composable
fun MotoMoveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotoMoveColors,
        content = content
    )
}
