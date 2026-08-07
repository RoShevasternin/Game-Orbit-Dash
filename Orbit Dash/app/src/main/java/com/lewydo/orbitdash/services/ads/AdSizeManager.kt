package com.lewydo.orbitdash.services.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// AdSizeManager — міст між Android-банером і GDX-екранами.
//
// Банер — це Android View поверх GL-поверхні. Його висота відома лише після
// onAdLoaded (adaptive banner: 32–90dp залежно від ширини екрана), тому
// «захардкодити 50dp» не можна — на планшеті буде щілина, на вузькому екрані
// банер перекриє кнопки.
//
// StateFlow, а не колбек: conflated (зайвих емітів немає), має .value для
// синхронного читання і переживає зміну екрана — новий екран одразу отримає
// актуальну висоту, а не чекатиме наступного завантаження банера.
//
// 0 = банера немає (прихований / не завантажився / куплено Remove Ads).
// ─────────────────────────────────────────────────────────────────────────────
object AdSizeManager {

    private val _bannerHeight = MutableStateFlow(0)

    /** Підписка для екранів. Емітить лише коли висота реально змінилась. */
    val bannerHeightFlow: StateFlow<Int> = _bannerHeight.asStateFlow()

    /** Синхронне читання поточної висоти в пікселях. */
    val bannerHeightPx: Int get() = _bannerHeight.value

    val isBannerShown: Boolean get() = _bannerHeight.value > 0

    /** Викликає лише BannerAdManager. */
    fun updateBannerHeight(px: Int) {
        _bannerHeight.value = px.coerceAtLeast(0)
    }
}