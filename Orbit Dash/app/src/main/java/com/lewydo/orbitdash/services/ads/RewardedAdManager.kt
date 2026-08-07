package com.lewydo.orbitdash.services.ads

import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.lewydo.orbitdash.BuildConfig
import com.lewydo.orbitdash.MainActivity
import com.lewydo.orbitdash.R
import com.lewydo.orbitdash.util.log

class RewardedAdManager(private val activity: MainActivity) {

    companion object {
        private const val REWARDED_ID = BuildConfig.ADMOB_REWARDED_ID
    }

    private var rewardedAd: RewardedAd? = null

    val isReady: Boolean get() = rewardedAd != null

    fun load() {
        activity.runOnUiThread {
            RewardedAd.load(
                activity, REWARDED_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        log("Rewarded ad loaded")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        log("Rewarded ad failed: ${error.message}")
                    }
                }
            )
        }
    }

    // onEarned — коли юзер переглянув рекламу (дати нагороду)
    // onDismissed — коли закрив без перегляду (нагороди немає)
    // onFailed — реклама не готова
    fun show(
        onEarned   : () -> Unit,
        onDismissed: () -> Unit = {},
        onFailed   : () -> Unit = {},
    ) {
        val ad = rewardedAd ?: run { onFailed(); return }

        activity.runOnUiThread {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    load()  // одразу завантажуємо наступну
                    onDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    load()
                    onFailed()
                }
            }
            ad.show(activity) { onEarned() }
        }
    }

    fun destroy() { rewardedAd = null }
}