package com.menu.my.todo.ads

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

const val AD_LOG_TAG = "TodoAds"

/**
 * Keeps one interstitial preloaded and shows it after a task is saved, rate limited.
 *
 * Saving is the app's most frequent action, so showing an ad every time would be both hostile and
 * a good way to attract an invalid-traffic review. Both limits below have to pass before an ad is
 * shown, and the counters only reset once an ad is actually displayed.
 */
class InterstitialAdController(private val activity: Activity) {

    private var ad: InterstitialAd? = null
    private var loading = false
    private var savesSinceLastAd = 0

    // Null until the first ad is shown. Using 0 here would measure the gap from device boot
    // instead, suppressing the first ad on a freshly booted device for no reason.
    private var lastShownAt: Long? = null

    fun preload() {
        if (loading || ad != null) return
        loading = true
        InterstitialAd.load(
            activity,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    Log.i(AD_LOG_TAG, "Interstitial loaded")
                    loading = false
                    ad = loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(AD_LOG_TAG, "Interstitial failed: code=${error.code} ${error.message}")
                    loading = false
                    ad = null
                }
            }
        )
    }

    /** Call after a task is saved. Shows an ad only if both rate limits allow it. */
    fun onTaskSaved() {
        savesSinceLastAd++
        val ready = ad ?: return preload()
        val enoughSaves = savesSinceLastAd >= MIN_SAVES_BETWEEN_ADS
        val enoughTime = lastShownAt?.let {
            SystemClock.elapsedRealtime() - it >= MIN_INTERVAL_MS
        } ?: true
        if (!enoughSaves || !enoughTime) return

        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(AD_LOG_TAG, "Interstitial show failed: ${error.message}")
                ad = null
                preload()
            }
        }
        savesSinceLastAd = 0
        lastShownAt = SystemClock.elapsedRealtime()
        ready.show(activity)
    }

    private companion object {
        const val MIN_SAVES_BETWEEN_ADS = 3
        // elapsedRealtime is monotonic, so this survives the user changing the clock.
        const val MIN_INTERVAL_MS = 3 * 60 * 1000L
    }
}
