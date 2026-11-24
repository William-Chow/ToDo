package com.menu.my.todo.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * The consent gate in front of the ads: nothing is requested from AdMob, and the ads SDK is not
 * started, until [canRequestAds] says this user may be served ads.
 *
 * Google's EU user consent policy wants people in the EEA, the UK and Switzerland asked before the
 * first request. Where the law asks for nothing, ads are allowed straight away. Where it does, none
 * is requested until the form has been answered.
 *
 * That holds only while a European regulations message is published for this app in AdMob. Without
 * one the SDK (4.0.0) logs "no form(s) configured" and then allows ads everywhere, the EEA included,
 * with nobody asked — so the message has to be live before a build is, and has to stay live.
 *
 * Main thread only, like the consent SDK it wraps.
 */
object AdConsent {

    /** Observable, so that the banner appears as soon as an answer allows it. */
    var canRequestAds by mutableStateOf(false)
        private set

    /** Whether the user has to be offered a way back into the form, to change or withdraw an answer. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    /**
     * Refreshes what is known about this user's consent, and shows the form if one is required.
     *
     * [onResolved] can fire more than once: straight away for an answer stored by an earlier run,
     * which should not have to wait for the network, and again when this refresh or the form is
     * done. It does not fire if [activity] has gone by then; whichever one took over gathers again.
     */
    fun gather(activity: Activity, onResolved: () -> Unit) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                if (consentInformation.isConsentFormAvailable &&
                    consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                ) {
                    loadAndShowForm(activity, onResolved)
                } else {
                    settle(activity, onResolved)
                }
            },
            { error ->
                // No network, or no message published. An answer from an earlier run still counts,
                // so the SDK's own state decides.
                Log.w(AD_LOG_TAG, "Consent update failed: code=${error.errorCode} ${error.message}")
                settle(activity, onResolved)
            }
        )
        settle(activity, onResolved)
    }

    /**
     * Reopens the form from the app's own privacy entry. [onDismissed] gets false when the form
     * could not be shown.
     */
    fun showPrivacyOptions(activity: Activity, onDismissed: (shown: Boolean) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                Log.w(AD_LOG_TAG, "Privacy options failed: code=${error.errorCode} ${error.message}")
            }
            refresh(activity)
            onDismissed(error == null)
        }
    }

    /**
     * Not loadAndShowConsentFormIfRequired, which leaves no chance to notice between the download
     * and the showing that the activity was rotated away or closed.
     */
    private fun loadAndShowForm(activity: Activity, onResolved: () -> Unit) {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { form ->
                if (!activity.isGone()) {
                    form.show(activity) { error ->
                        if (error != null) {
                            Log.w(AD_LOG_TAG, "Consent form failed: code=${error.errorCode} ${error.message}")
                        }
                        settle(activity, onResolved)
                    }
                }
            },
            { error ->
                Log.w(AD_LOG_TAG, "Consent form not loaded: code=${error.errorCode} ${error.message}")
                settle(activity, onResolved)
            }
        )
    }

    private fun settle(activity: Activity, onResolved: () -> Unit) {
        if (activity.isGone()) return
        refresh(activity)
        onResolved()
    }

    /** Follows the SDK both ways, so that ads stop if consent has to be asked for again. */
    private fun refresh(context: Context) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        canRequestAds = consentInformation.canRequestAds()
        privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun Activity.isGone() = isFinishing || isDestroyed
}
