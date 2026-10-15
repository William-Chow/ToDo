package com.menu.my.todo

import androidx.compose.ui.graphics.Color
import com.menu.my.todo.ui.theme.DarkPrimary
import com.menu.my.todo.ui.theme.DarkSurface
import com.menu.my.todo.ui.theme.LightPrimary
import com.menu.my.todo.ui.theme.LightSurface
import com.menu.my.todo.ui.theme.systemBarBackdrop

import org.junit.Test

import org.junit.Assert.*

/**
 * What the system bars are told sits behind them.
 *
 * On API 26–28 the same answer also picks the navigation bar's scrim, so it has to come from the
 * app's own colours and nothing else: when the scrim followed the system's night mode and only the
 * icons followed the app, a dark app on a light system drew white icons over a near-white scrim.
 * The enableEdgeToEdge call that applies the answer is not reachable from a JVM test; this is the
 * decision it is handed.
 */
class SystemBarBackdropTest {

    @Test
    fun aDarkAppThemeGetsADarkNavigationBar() {
        assertTrue(systemBarBackdrop(DarkPrimary, DarkSurface).navigationBarIsDark)
    }

    @Test
    fun aLightAppThemeGetsALightNavigationBar() {
        assertFalse(systemBarBackdrop(LightPrimary, LightSurface).navigationBarIsDark)
    }

    @Test
    fun theStatusBarFollowsTheTopAppBarRatherThanTheTheme() {
        // The light theme's TopAppBar is a deep blue, so the status bar over it is dark and needs
        // light icons even though the theme is light.
        assertTrue(systemBarBackdrop(LightPrimary, LightSurface).statusBarIsDark)
    }

    @Test
    fun aPalePrimaryGetsDarkStatusBarIcons() {
        // Material You's dark schemes put a pale tone in primary.
        assertFalse(systemBarBackdrop(Color(0xFFD0BCFF), DarkSurface).statusBarIsDark)
    }

    @Test
    fun eachBarIsJudgedOnWhatIsBehindItAlone() {
        val backdrop = systemBarBackdrop(statusBarBehind = Color.Black, navigationBarBehind = Color.White)

        assertTrue(backdrop.statusBarIsDark)
        assertFalse(backdrop.navigationBarIsDark)
    }
}
