package dev.twiview.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0B0B0D)
    val Surface = Color(0xFF151518)
    val Surface2 = Color(0xFF1E1E23)
    val Line = Color(0xFF2A2A31)
    val Text = Color(0xFFEDEDF0)
    val Muted = Color(0xFF9A9AA6)
    val Faint = Color(0xFF63636E)
    val Accent = Color(0xFFB08CFF)
    val Live = Color(0xFFFF3B3B)
    val Notice = Color(0xFFC8E86B)
}

private val scheme = darkColorScheme(
    primary = C.Accent,
    onPrimary = Color(0xFF1A0E33),
    secondary = C.Notice,
    background = C.Bg,
    onBackground = C.Text,
    surface = C.Bg,
    onSurface = C.Text,
    surfaceVariant = C.Surface2,
    onSurfaceVariant = C.Muted,
    surfaceContainer = C.Surface,
    surfaceContainerHigh = C.Surface2,
    surfaceContainerHighest = C.Surface2,
    surfaceContainerLow = C.Surface,
    outline = C.Line,
    outlineVariant = C.Line,
    error = C.Live,
)

private val base = Typography()
private val typography = base.copy(
    titleLarge = base.titleLarge.copy(fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontSize = 15.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun TwiviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
