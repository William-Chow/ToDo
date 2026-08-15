package com.menu.my.todo.ads

import com.menu.my.todo.BuildConfig

/**
 * Ad unit IDs, switched by build type.
 *
 * Debug builds always request Google's public test units. Requesting a live unit while developing
 * counts as invalid traffic and can get the AdMob account suspended, so the live IDs below are
 * unreachable from a debug build by construction rather than by remembering to swap them back.
 *
 * The app ID in AndroidManifest.xml is the live one in both build types — it only identifies the
 * app to the SDK and is safe to use alongside test units.
 */
object AdConfig {
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    private const val LIVE_BANNER = "ca-app-pub-4541063798492496/2403188045"
    private const val LIVE_INTERSTITIAL = "ca-app-pub-4541063798492496/1951958027"

    val BANNER_AD_UNIT_ID = if (BuildConfig.DEBUG) TEST_BANNER else LIVE_BANNER
    val INTERSTITIAL_AD_UNIT_ID = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else LIVE_INTERSTITIAL
}
