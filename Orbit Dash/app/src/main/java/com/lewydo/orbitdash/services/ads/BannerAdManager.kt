package com.lewydo.orbitdash.services.ads

import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.lewydo.orbitdash.BuildConfig
import com.lewydo.orbitdash.MainActivity
import com.lewydo.orbitdash.databinding.ActivityMainBinding
import com.lewydo.orbitdash.util.log

class BannerAdManager(
    private val activity: MainActivity,
    private val binding : ActivityMainBinding,
) {

    companion object {
        private const val BANNER_ID = BuildConfig.ADMOB_BANNER_ID
    }

    private var adView: AdView? = null

    /** Чи екран ЗАРАЗ хоче банер. Потрібно, бо onAdLoaded може прийти вже після hide(). */
    private var isWanted = false

    /** Стеля висоти банера в dp. 50 — класика, 60 — комфортний максимум. */
    var maxHeightDp = 56

    private val density get() = activity.resources.displayMetrics.density

    /**
     * Стеля в пікселях: не вище maxHeightDp і не більше 10% екрана.
     * Друга умова рятує маленькі екрани, де 56dp — це вже забагато.
     */
    private val maxHeightPx: Int
        get() = minOf(
            (maxHeightDp * density).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.10f).toInt()
        )

    /**
     * Adaptive banner з обмеженням висоти.
     *
     * Висота anchored-банера похідна від ЗАЯВЛЕНОЇ ширини, тому обмежуємо саме
     * її: пробуємо повну ширину екрана, потім 360dp, потім 320dp — і беремо
     * першу, що влазить у стелю. Якщо не влізло нічого — класичний 320×50.
     *
     * getLargeAnchoredAdaptiveBannerAdSize — актуальний API (старий
     * getCurrentOrientationAnchoredAdaptiveBannerAdSize задепрекейчено),
     * але він знімає стелю 50dp, тому обмеження мусимо ставити самі.
     */
    private val adaptiveSize: AdSize
        get() {
            val screenWidthDp = (activity.resources.displayMetrics.widthPixels / density).toInt()
            val limit = maxHeightPx

            for (widthDp in intArrayOf(screenWidthDp, 360, 320)) {
                if (widthDp <= 0) continue
                val size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
                if (size == AdSize.INVALID) continue
                if (size.getHeightInPixels(activity) <= limit) return size
            }
            return AdSize.BANNER
        }

    fun show() = activity.runOnUiThread {
        isWanted = true

        // Банер уже створений — просто показуємо, без повторного завантаження
        adView?.let { existing ->
            binding.bannerContainer.visibility = View.VISIBLE
            publishHeight(existing.adSize)
            return@runOnUiThread
        }

        val size = adaptiveSize
        adView = AdView(activity).apply {
            adUnitId = BANNER_ID
            setAdSize(size)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    // Екран міг уже піти в гру, поки банер вантажився
                    if (!isWanted) return
                    binding.bannerContainer.visibility = View.VISIBLE
                    publishHeight(size)
                    log("Banner loaded: ${size.getHeightInPixels(activity)}px")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    binding.bannerContainer.visibility = View.GONE
                    AdSizeManager.updateBannerHeight(0)
                    log("Banner failed: ${error.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }

        binding.bannerContainer.addView(adView)
    }

    fun hide() = activity.runOnUiThread {
        isWanted = false
        binding.bannerContainer.visibility = View.GONE
        AdSizeManager.updateBannerHeight(0)
    }

    fun destroy() = activity.runOnUiThread {
        isWanted = false
        adView?.destroy()
        adView = null
        binding.bannerContainer.removeAllViews()
        binding.bannerContainer.visibility = View.GONE
        AdSizeManager.updateBannerHeight(0)
    }

    fun onResume() { adView?.resume() }
    fun onPause()  { adView?.pause() }

    private fun publishHeight(size: AdSize?) {
        AdSizeManager.updateBannerHeight(size?.getHeightInPixels(activity) ?: 0)
    }
}