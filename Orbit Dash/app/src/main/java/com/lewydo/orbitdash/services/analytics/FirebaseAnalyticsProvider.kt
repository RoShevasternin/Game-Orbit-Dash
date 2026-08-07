package com.lewydo.orbitdash.services.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

class FirebaseAnalyticsProvider : AnalyticsProvider {

    private val fa = Firebase.analytics

    private object Event {
        const val CUBE_MILESTONE          = "cube_milestone"
        const val BUY_LEVEL_UPGRADE       = "buy_level_upgrade"
        const val GOAL_COMPLETED          = "goal_completed"
        const val GOAL_FAILED             = "goal_failed"
        const val COLLECT_MERGE_BONUS     = "collect_merge_bonus"
        const val COLLECT_MERGE_BONUS_X2  = "collect_merge_bonus_x2"
        const val COLLECT_OFFLINE         = "collect_offline"
        const val COLLECT_OFFLINE_X2      = "collect_offline_x2"
        const val COLLECT_NEW_LEVEL       = "collect_new_level"
        const val COLLECT_NEW_LEVEL_X2    = "collect_new_level_x2"
        const val AD_WATCHED              = "ad_watched"
    }

    private object Param {
        const val CUBE_LEVEL  = "cube_level"
        const val BUY_LEVEL   = "buy_level"
        const val GOAL_TYPE   = "goal_type"
        const val REWARD      = "reward"
        const val AMOUNT      = "amount"
        const val PLACEMENT   = "placement"
    }

    // Tutorial
    override fun tutorialBegin()    = fa.logEvent(FirebaseAnalytics.Event.TUTORIAL_BEGIN,    null)
    override fun tutorialComplete() = fa.logEvent(FirebaseAnalytics.Event.TUTORIAL_COMPLETE, null)

    // Progression
    override fun levelUp(level: Int) =
        fa.logEvent(FirebaseAnalytics.Event.LEVEL_UP, bundle {
            putInt(FirebaseAnalytics.Param.LEVEL, level)
        })

    override fun cubeMilestone(cubeLevel: Int) =
        fa.logEvent(Event.CUBE_MILESTONE, bundle {
            putInt(Param.CUBE_LEVEL, cubeLevel)
        })

    override fun buyLevelUpgrade(newBuyLevel: Int) =
        fa.logEvent(Event.BUY_LEVEL_UPGRADE, bundle {
            putInt(Param.BUY_LEVEL, newBuyLevel)
        })

    // Goals
    override fun goalCompleted(goalType: String, reward: Long) =
        fa.logEvent(Event.GOAL_COMPLETED, bundle {
            putString(Param.GOAL_TYPE, goalType)
            putLong(Param.REWARD, reward)
        })

    override fun goalFailed(goalType: String) =
        fa.logEvent(Event.GOAL_FAILED, bundle {
            putString(Param.GOAL_TYPE, goalType)
        })

    // Economy
    override fun collectMergeBonus(amount: Long)   = fa.logEvent(Event.COLLECT_MERGE_BONUS,    bundle { putLong(Param.AMOUNT, amount) })
    override fun collectMergeBonusX2(amount: Long) = fa.logEvent(Event.COLLECT_MERGE_BONUS_X2, bundle { putLong(Param.AMOUNT, amount) })
    override fun collectOffline(amount: Long)      = fa.logEvent(Event.COLLECT_OFFLINE,        bundle { putLong(Param.AMOUNT, amount) })
    override fun collectOfflineX2(amount: Long)    = fa.logEvent(Event.COLLECT_OFFLINE_X2,     bundle { putLong(Param.AMOUNT, amount) })
    override fun collectNewLevel(amount: Long)     = fa.logEvent(Event.COLLECT_NEW_LEVEL,      bundle { putLong(Param.AMOUNT, amount) })
    override fun collectNewLevelX2(amount: Long)   = fa.logEvent(Event.COLLECT_NEW_LEVEL_X2,   bundle { putLong(Param.AMOUNT, amount) })

    // Ads
    override fun adWatched(placement: String) =
        fa.logEvent(Event.AD_WATCHED, bundle {
            putString(Param.PLACEMENT, placement)
        })

    private fun bundle(block: Bundle.() -> Unit) = Bundle().apply(block)
}