package com.example.financetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.financetracker.data.prefs.ThemeMode

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

/** Access to the design tokens that Material's ColorScheme has no slot for. */
object FinanceTheme {
    val colors: FinanceColors
        @Composable get() = LocalFinanceColors.current
}

private val FinanceShapes = Shapes(
    extraSmall = RoundedCornerShape(8),
    small = RoundedCornerShape(8),
    medium = RoundedCornerShape(14),
    large = RoundedCornerShape(22),
    extraLarge = RoundedCornerShape(28),
)

/**
 * Brand theme. Dynamic (wallpaper) colour is intentionally not used so the brand stays the same on every phone.
 */
@Composable
fun FinanceTrackerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkFinanceColors else LightFinanceColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            primaryContainer = c.primarySoft, onPrimaryContainer = c.onPrimarySoft,
            secondary = c.primary, onSecondary = c.onPrimary,
            secondaryContainer = c.primarySoft, onSecondaryContainer = c.onPrimarySoft,
            tertiary = c.accent, onTertiary = c.onAccent,
            background = c.bg, onBackground = c.ink,
            surface = c.bg, onSurface = c.ink,
            surfaceVariant = c.surface2, onSurfaceVariant = c.inkMuted,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2,
            surfaceContainerHighest = c.surface2,
            outline = c.outline, outlineVariant = c.line,
            error = c.danger, onError = Color(0xFF3A0B07),
            errorContainer = c.dangerSoft, onErrorContainer = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = c.onPrimary,
            primaryContainer = c.primarySoft, onPrimaryContainer = c.onPrimarySoft,
            secondary = c.primary, onSecondary = c.onPrimary,
            secondaryContainer = c.primarySoft, onSecondaryContainer = c.onPrimarySoft,
            tertiary = c.accent, onTertiary = c.onAccent,
            background = c.bg, onBackground = c.ink,
            surface = c.bg, onSurface = c.ink,
            surfaceVariant = c.surface2, onSurfaceVariant = c.inkMuted,
            surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2,
            surfaceContainerHighest = c.surface2,
            outline = c.outline, outlineVariant = c.line,
            error = c.danger, onError = Color.White,
            errorContainer = c.dangerSoft, onErrorContainer = c.danger,
        )
    }

    // Keep status/navigation bar icons readable when the in-app theme differs from the system one.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalFinanceColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = FinanceShapes,
            content = content,
        )
    }
}
