package com.lewydo.orbitdash.services.analytics

// ----------------------------------------------------------------------------
//  Єдина точка входу аналітики. object, а не class: стан — лише незмінний
//  список провайдерів, а доступ потрібен звідусіль (екрани, GDXGame, менеджери)
//  без протягування посилання.
//
//  Словники значень тримаємо ТУТ, поруч із подіями: рядки в події — це
//  контракт зі звітами, їхні друкарські помилки не ловить компілятор, тож
//  єдине джерело констант — захист від "revive"/"Revive"/"REVIVE" у трьох
//  місцях коду.
// ----------------------------------------------------------------------------
object AnalyticsManager {

    // ------------------------------------------------------------ Dictionaries

    /** Де показано rewarded. */
    object Placement {
        const val REVIVE      = "revive"
        const val GEMS_MENU   = "gems_menu"
        const val BOOST_START = "boost_start"
        const val X2_GEMS     = "x2_gems"
    }

    /** Звідки прийшли геми. */
    object GemSource {
        const val RUN     = "run"
        const val MISSION = "mission"
        const val STREAK  = "streak"
        const val AD      = "ad"
    }

    /** На що витрачено геми. */
    object Item {
        const val ORBIT3 = "orbit3"
        const val NO_ADS = "no_ads"
        fun upgrade(id: String) = "upgrade_$id"
        fun skin(name: String)  = name.lowercase()
    }

    /** Стартовий буст рану. */
    object Boost {
        const val NONE = "none"
    }

    // ---------------------------------------------------------------- Backend

    private val providers by lazy {
        listOf(
            FirebaseAnalyticsProvider(),
            TikTokAnalyticsProvider(),
        )
    }

    private inline fun emit(block: AnalyticsProvider.() -> Unit) =
        providers.forEach { it.block() }

    // ------------------------------------------------------------------ Runs
    fun runStart(startBoost: String = Boost.NONE) = emit { runStart(startBoost) }

    fun runEnd(score: Int, durationSec: Int, gemsEarned: Int, deathRing: Int, newBest: Boolean) =
        emit { runEnd(score, durationSec, gemsEarned, deathRing, newBest) }

    // ------------------------------------------------------------------ Meta
    fun missionClaimed(type: String, reward: Int) = emit { missionClaimed(type, reward) }
    fun streakClaimed(day: Int, reward: Int)      = emit { streakClaimed(day, reward) }
    fun tutorialComplete()                        = emit { tutorialComplete() }

    // ------------------------------------------------------------------- Ads
    fun adReward(placement: String) = emit { adReward(placement) }

    // --------------------------------------------------------------- Economy
    fun earnGems(amount: Int, source: String) = emit { earnGems(amount, source) }
    fun spendGems(amount: Int, item: String)  = emit { spendGems(amount, item) }

    // ------------------------------------------------------- User properties
    fun setUserId(pid: String)       = emit { setUserId(pid) }
    fun setHasOrbit3(owned: Boolean) = emit { setHasOrbit3(owned) }
    fun setActiveSkin(name: String)  = emit { setActiveSkin(name) }
    fun setNoAds(owned: Boolean)     = emit { setNoAds(owned) }
    fun setBestBucket(best: Int)     = emit { setBestBucket(best) }
}
