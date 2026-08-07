package com.lewydo.orbitdash.services.analytics

interface AnalyticsProvider {

    // Tutorial
    fun tutorialBegin()
    fun tutorialComplete()

    // Progression
    fun levelUp(level: Int)
    fun cubeMilestone(cubeLevel: Int)   // тільки для важливих рівнів: 3,5,7,10...
    fun buyLevelUpgrade(newBuyLevel: Int)

    // Goals
    fun goalCompleted(goalType: String, reward: Long)
    fun goalFailed(goalType: String)

    // Economy
    fun collectMergeBonus(amount: Long)
    fun collectMergeBonusX2(amount: Long)
    fun collectOffline(amount: Long)
    fun collectOfflineX2(amount: Long)
    fun collectNewLevel(amount: Long)
    fun collectNewLevelX2(amount: Long)

    // Ads (для майбутнього)
    fun adWatched(placement: String)
}