package com.lewydo.orbitdash.services.analytics

import com.tiktok.TikTokBusinessSdk
import com.tiktok.appevents.base.EventName

// ----------------------------------------------------------------------------
//  TikTok — НЕ продуктова аналітика, а сигнали для алгоритму закупівлі.
//  Йому потрібні лише «цінні» юзери: пройшов туторіал, росте, взаємодіє з
//  метою, витрачає валюту. Решту подій свідомо не шлемо — шум погіршує
//  оптимізацію кампаній.
// ----------------------------------------------------------------------------
class TikTokAnalyticsProvider : AnalyticsProvider {

    private fun track(event: EventName) = TikTokBusinessSdk.trackTTEvent(event)

    override fun tutorialComplete() = track(EventName.COMPLETE_TUTORIAL)

    /** Новий рекорд = юзер прогресує — найближчий аналог «рівня» в раннері. */
    override fun runEnd(score: Int, durationSec: Int, gemsEarned: Int, deathRing: Int, newBest: Boolean) {
        if (newBest) track(EventName.ACHIEVE_LEVEL)
    }

    override fun missionClaimed(type: String, reward: Int) = track(EventName.UNLOCK_ACHIEVEMENT)

    override fun spendGems(amount: Int, item: String) = track(EventName.SPEND_CREDITS)
}
