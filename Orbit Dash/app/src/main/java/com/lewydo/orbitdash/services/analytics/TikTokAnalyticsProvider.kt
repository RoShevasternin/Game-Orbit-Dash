package com.lewydo.orbitdash.services.analytics

import com.tiktok.TikTokBusinessSdk
import com.tiktok.appevents.base.EventName

class TikTokAnalyticsProvider : AnalyticsProvider {

    private fun track(event: EventName) = TikTokBusinessSdk.trackTTEvent(event)

    // Tutorial
    override fun tutorialBegin()    = Unit  // TikTok не має tutorial begin
    override fun tutorialComplete() = track(EventName.COMPLETE_TUTORIAL)

    // Progression — TikTok цікавить досягнення рівнів для ретаргетингу
    override fun levelUp(level: Int)            = track(EventName.ACHIEVE_LEVEL)
    override fun cubeMilestone(cubeLevel: Int)  = track(EventName.UNLOCK_ACHIEVEMENT)
    override fun buyLevelUpgrade(newBuyLevel: Int) = Unit  // не потрібно для TikTok

    // Goals — досягнення = unlock achievement
    override fun goalCompleted(goalType: String, reward: Long) = track(EventName.UNLOCK_ACHIEVEMENT)
    override fun goalFailed(goalType: String)                  = Unit

    // Economy — spend credits при будь-якому meaningful витраті
    override fun collectMergeBonus(amount: Long)   = Unit
    override fun collectMergeBonusX2(amount: Long) = track(EventName.UNLOCK_ACHIEVEMENT)  // ← дивився рекламу
    override fun collectOffline(amount: Long)      = Unit
    override fun collectOfflineX2(amount: Long)    = track(EventName.UNLOCK_ACHIEVEMENT)
    override fun collectNewLevel(amount: Long)     = Unit
    override fun collectNewLevelX2(amount: Long)   = track(EventName.UNLOCK_ACHIEVEMENT)

    // Ads
    override fun adWatched(placement: String) = Unit
}