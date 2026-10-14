package com.fvstexpress.driver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colours and sizes copied 1:1 from mockup-driver/assets/css/style.css (:root variables).
 * CSS px are used as dp/sp (the mockup is a 390 px phone, i.e. a ~390 dp Android screen).
 */
object Fv {
    val Bg = Color(0xFF10131A)              // --color-bg
    val Surface = Color(0xFF1A1E28)         // --color-surface
    val SurfaceAlt = Color(0xFF212633)      // --color-surface-alt
    val Border = Color(0xFF2B3140)          // --color-border
    val BorderStrong = Color(0xFF3A4152)    // --color-border-strong
    val Text = Color(0xFFE8EAF0)            // --color-text
    val TextMuted = Color(0xFFD6DDEB)       // --color-text-muted
    val TextFaint = Color(0xFFD6DDEB)       // --color-text-faint
    val Accent = Color(0xFF5B8BF5)          // --color-accent
    val AccentHover = Color(0xFF7BA0F7)     // --color-accent-hover
    val AccentSoft = Color(0xFF1E2C4D)      // --color-accent-soft
    val Success = Color(0xFF3EC97A)
    val SuccessSoft = Color(0xFF16301F)
    val Warning = Color(0xFFE0A63E)
    val WarningSoft = Color(0xFF3A2C11)
    val Danger = Color(0xFFF0685C)
    val DangerSoft = Color(0xFF3D1F1C)
    val NeutralSoft = Color(0xFF262C3A)
    val Purple = Color(0xFFB299F5)
    val OnSuccess = Color(0xFF06210F)
    val GraphTick = Color(0xFF05070C)       // .day-graph-bar segments
    val GraphWindow = Color(0xFF858EA3)     // .dgb-window
    val MapBg = Color(0xFF1F2433)

    val RadiusSm = 4.dp   // --radius-sm
    val RadiusMd = 8.dp   // --radius-md
    val RadiusLg = 14.dp  // --radius-lg
    val TopBarHeight = 52.dp
    val BottomNavHeight = 58.dp
    val ContentPadding = 14.dp

    // Legacy names still used by a few views.
    val Pickup get() = Warning
    val Dropoff get() = Accent
}

/** Kept for the few places that reference leg colours by this name. */
object LegColors {
    val Pickup = Fv.Warning
    val Dropoff = Fv.Accent
    val Urgent = Fv.Danger
    val Done = Fv.Success
}

private val scheme = darkColorScheme(
    primary = Fv.Accent,
    onPrimary = Color.White,
    secondary = Fv.Accent,
    background = Fv.Bg,
    onBackground = Fv.Text,
    surface = Fv.Surface,
    onSurface = Fv.Text,
    surfaceVariant = Fv.SurfaceAlt,
    onSurfaceVariant = Fv.TextFaint,
    outline = Fv.BorderStrong,
    outlineVariant = Fv.Border,
    error = Fv.Danger,
    surfaceContainer = Fv.Surface,
    surfaceContainerHigh = Fv.Surface,
    surfaceContainerHighest = Fv.SurfaceAlt,
    surfaceContainerLow = Fv.Surface,
    surfaceContainerLowest = Fv.Bg,
)

// body { font-size: 14.5px; line-height: 1.4 } with the system font (Roboto on Android).
private val base = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.5.sp, lineHeight = 20.sp, color = Fv.Text)

@Composable
fun FvstTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            bodyLarge = base, bodyMedium = base.copy(fontSize = 13.sp, lineHeight = 18.sp), bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
            labelLarge = base.copy(fontSize = 13.sp), labelMedium = base.copy(fontSize = 11.5.sp), labelSmall = base.copy(fontSize = 10.5.sp),
            titleLarge = base.copy(fontSize = 19.sp), titleMedium = base.copy(fontSize = 16.5.sp), titleSmall = base.copy(fontSize = 15.sp),
        ),
        content = content,
    )
}
