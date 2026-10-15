package com.menu.my.todo.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, DYNAMIC
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

@Composable
fun TodoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> isSystemInDarkTheme()
    }
    // Dynamic color (Material You) is only available on Android 12+.
    val useDynamicColor = themeMode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val backdrop = systemBarBackdrop(statusBarBehind = colorScheme.primary, navigationBarBehind = colorScheme.surface)
        DisposableEffect(backdrop) {
            // The bars are told what is behind them through enableEdgeToEdge rather than by setting
            // the icon appearance directly, because on API 26–28 it also paints a scrim on the
            // navigation bar — and that scrim, left to itself, follows the *system's* night mode.
            // Setting only the icons to follow the app put white icons on a near-white scrim
            // whenever the app was dark and the system was not. Handing it one answer for both
            // keeps them together. Nothing paints the bars a solid colour: the TopAppBar and
            // NavigationBar are drawn underneath them.
            (view.context as ComponentActivity).enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { backdrop.statusBarIsDark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { backdrop.navigationBarIsDark }
            )
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** Whether what shows through each system bar is dark, and so needs light icons drawn over it. */
internal data class SystemBarBackdrop(val statusBarIsDark: Boolean, val navigationBarIsDark: Boolean)

/**
 * What the system bars sit over: [statusBarBehind] is the TopAppBar's `primary` and
 * [navigationBarBehind] the NavigationBar's `surface`.
 *
 * It is read off the app's own colours and deliberately takes nothing from the system, because the
 * app's theme can be set against the system's (LIGHT or DARK rather than SYSTEM). Luminance rather
 * than the chosen light/dark mode is what keeps it right under Material You, where the scheme comes
 * from the wallpaper and a light theme's `primary` need not be dark.
 */
internal fun systemBarBackdrop(statusBarBehind: Color, navigationBarBehind: Color) = SystemBarBackdrop(
    statusBarIsDark = statusBarBehind.luminance() <= 0.5f,
    navigationBarIsDark = navigationBarBehind.luminance() <= 0.5f
)

private const val TRANSPARENT = 0x00000000

// The navigation-bar scrims enableEdgeToEdge() uses by default, which androidx.activity does not
// expose. Only API 26–28 draws them; later versions draw their own, matched to the icons.
private const val LIGHT_SCRIM = 0xE6FFFFFF.toInt()
private const val DARK_SCRIM = 0x801B1B1B.toInt()
