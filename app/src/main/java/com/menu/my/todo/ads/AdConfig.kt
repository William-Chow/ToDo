package com.menu.my.todo.ads

/**
 * Ad unit IDs, kept in one place so they are easy to swap.
 *
 * These are Google's public test units: they always fill and never earn revenue. Requesting a
 * *real* ad unit while developing is an AdMob policy violation and can get the account suspended,
 * so keep these until the app is ready to publish, then replace both constants with the IDs from
 * the AdMob console (Ad units -> the unit's ID, format ca-app-pub-xxx/yyy) and swap the
 * APPLICATION_ID meta-data in AndroidManifest.xml too.
 *
 * See https://developers.google.com/admob/android/test-ads for the full list of test units.
 */
object AdConfig {
    /** Fixed-size (320x50) banner test unit, used by [AdBanner]. */
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
}
