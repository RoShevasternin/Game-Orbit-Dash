package com.lewydo.orbitdash.services.analytics

// ----------------------------------------------------------------------------
//  Схема подій Orbit Dash. Один інтерфейс — багато бекендів (Firebase, TikTok).
//
//  УСІ методи мають default {} — провайдер перекриває ЛИШЕ те, що його мережа
//  вміє переварити. TikTok, наприклад, ігнорує економіку і ловить тільки
//  сигнали цінності юзера для алгоритму закупівлі.
// ----------------------------------------------------------------------------
interface AnalyticsProvider {

    // Runs — серце гри
    fun runStart(startBoost: String) {}
    fun runEnd(score: Int, durationSec: Int, gemsEarned: Int, deathRing: Int, newBest: Boolean) {}

    // Meta
    fun missionClaimed(type: String, reward: Int) {}
    fun streakClaimed(day: Int, reward: Int) {}
    fun tutorialComplete() {}

    // Ads
    fun adReward(placement: String) {}

    // Economy — віртуальна валюта «gems»
    fun earnGems(amount: Int, source: String) {}
    fun spendGems(amount: Int, item: String) {}

    // User identity & properties — зрізи аудиторії
    fun setUserId(pid: String) {}
    fun setHasOrbit3(owned: Boolean) {}
    fun setActiveSkin(name: String) {}
    fun setNoAds(owned: Boolean) {}
    fun setBestBucket(best: Int) {}
}
