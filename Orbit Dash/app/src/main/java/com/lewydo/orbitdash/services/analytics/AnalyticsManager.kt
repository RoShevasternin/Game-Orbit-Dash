package com.lewydo.orbitdash.services.analytics

class AnalyticsManager {

    private val providers: List<AnalyticsProvider> = listOf(
        FirebaseAnalyticsProvider(),
        TikTokAnalyticsProvider(),
    )

    private fun emit(block: AnalyticsProvider.() -> Unit) =
        providers.forEach { it.block() }

    // ── Tutorial ──────────────────────────────────────────────────────────────

    fun tutorialBegin()    = emit { tutorialBegin() }
    fun tutorialComplete() = emit { tutorialComplete() }

    // ── Progression ───────────────────────────────────────────────────────────

    fun levelUp(level: Int) = emit { levelUp(level) }

    // Викликати тільки при першому досягненні рівня (мілстоун)
    // Корисні рівні для балансу: 3, 5, 7, 10, 12, 15...
    fun cubeMilestone(cubeLevel: Int) = emit { cubeMilestone(cubeLevel) }

    fun buyLevelUpgrade(newBuyLevel: Int) = emit { buyLevelUpgrade(newBuyLevel) }

    // ── Goals ─────────────────────────────────────────────────────────────────

    fun goalCompleted(goalType: String, reward: Long) = emit { goalCompleted(goalType, reward) }
    fun goalFailed(goalType: String)                  = emit { goalFailed(goalType) }

    // ── Economy ───────────────────────────────────────────────────────────────

    fun collectMergeBonus(amount: Long)    = emit { collectMergeBonus(amount) }
    fun collectMergeBonusX2(amount: Long)  = emit { collectMergeBonusX2(amount) }
    fun collectOffline(amount: Long)       = emit { collectOffline(amount) }
    fun collectOfflineX2(amount: Long)     = emit { collectOfflineX2(amount) }
    fun collectNewLevel(amount: Long)      = emit { collectNewLevel(amount) }
    fun collectNewLevelX2(amount: Long)    = emit { collectNewLevelX2(amount) }

    // ── Ads ───────────────────────────────────────────────────────────────────

    fun adWatched(placement: String) = emit { adWatched(placement) }
}