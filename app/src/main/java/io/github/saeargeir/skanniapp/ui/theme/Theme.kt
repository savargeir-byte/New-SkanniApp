package io.github.saeargeir.skanniapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = IcelandBlue40,
    onPrimary = Color.White,
    primaryContainer = Glacier,
    onPrimaryContainer = IcelandBlue40,
    
    secondary = IcelandGreen40,
    onSecondary = Color.White,
    secondaryContainer = Moss,
    onSecondaryContainer = IcelandGreen40,
    
    tertiary = IcelandPurple40,
    onTertiary = Color.White,
    tertiaryContainer = Aurora,
    onTertiaryContainer = IcelandPurple40,
    
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Error,
    
    background = NeutralGray50,
    onBackground = NeutralGray900,
    surface = Color.White,
    onSurface = NeutralGray900,
    surfaceVariant = NeutralGray100,
    onSurfaceVariant = NeutralGray600,
    
    outline = NeutralGray300,
    outlineVariant = NeutralGray200,
)

private val DarkColorScheme = darkColorScheme(
    primary = IcelandBlue80,
    onPrimary = NeutralGray900,
    primaryContainer = IcelandBlue40,
    onPrimaryContainer = IcelandBlue80,
    
    secondary = IcelandGreen80,
    onSecondary = NeutralGray900,
    secondaryContainer = IcelandGreen40,
    onSecondaryContainer = IcelandGreen80,
    
    tertiary = IcelandPurple80,
    onTertiary = NeutralGray900,
    tertiaryContainer = IcelandPurple40,
    onTertiaryContainer = IcelandPurple80,
    
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = NeutralGray900,
    onBackground = NeutralGray100,
    surface = NeutralGray800,
    onSurface = NeutralGray100,
    surfaceVariant = NeutralGray700,
    onSurfaceVariant = NeutralGray300,
    
    outline = NeutralGray500,
    outlineVariant = NeutralGray600,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SkanniAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}