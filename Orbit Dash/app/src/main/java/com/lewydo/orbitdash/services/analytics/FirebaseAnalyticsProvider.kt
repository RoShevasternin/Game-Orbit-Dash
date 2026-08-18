package com.lewydo.orbitdash.services.analytics

import android.os.Bundle
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics

class FirebaseAnalyticsProvider : AnalyticsProvider {

    private val fa = Firebase.analytics

    private object Event {
        const val RUN_START       = "run_start"
        const val RUN_END         = "run_end"
        const val MISSION_CLAIMED = "mission_claimed"
        const val STREAK_CLAIMED  = "streak_claimed"
        const val AD_REWARD       = "ad_reward"
    }

    private object Param {
        const val START_BOOST  = "start_boost"
        const val SCORE        = "score"
        const val DURATION     = "duration"
        const val DEATH_RING   = "death_ring"
        const val NEW_BEST     = "new_best"
        const val MISSION_TYPE = "mission_type"
        const val DAY          = "day"
        const val PLACEMENT    = "placement"
    }

    private object Prop {
        const val HAS_ORBIT3  = "has_orbit3"
        const val SKIN_ACTIVE = "skin_active"
        const val NO_ADS      = "no_ads"
        const val BEST_BUCKET = "best_bucket"
    }

    private inline fun bundle(block: Bundle.() -> Unit) = Bundle().apply(block)

    // ------------------------------------------------------------------ Runs
    override fun runStart(startBoost: String) =
        fa.logEvent(Event.RUN_START, bundle {
            putString(Param.START_BOOST, startBoost)
        })

    // VALUE = зароблені геми: стандартний параметр, який Firebase сам
    // агрегує у «цінність події» — звіти й аудиторії будують без рук.
    override fun runEnd(score: Int, durationSec: Int, gemsEarned: Int, deathRing: Int, newBest: Boolean) =
        fa.logEvent(Event.RUN_END, bundle {
            putLong(Param.SCORE, score.toLong())
            putLong(Param.DURATION, durationSec.toLong())
            putLong(FirebaseAnalytics.Param.VALUE, gemsEarned.toLong())
            putString(Param.DEATH_RING, deathRing.toString())
            putString(Param.NEW_BEST, newBest.toString())
        })

    // ------------------------------------------------------------------ Meta
    override fun missionClaimed(type: String, reward: Int) =
        fa.logEvent(Event.MISSION_CLAIMED, bundle {
            putString(Param.MISSION_TYPE, type)
            putLong(FirebaseAnalytics.Param.VALUE, reward.toLong())
        })

    override fun streakClaimed(day: Int, reward: Int) =
        fa.logEvent(Event.STREAK_CLAIMED, bundle {
            putLong(Param.DAY, day.toLong())
            putLong(FirebaseAnalytics.Param.VALUE, reward.toLong())
        })

    override fun tutorialComplete() =
        fa.logEvent(FirebaseAnalytics.Event.TUTORIAL_COMPLETE, null)

    // ------------------------------------------------------------------- Ads
    override fun adReward(placement: String) =
        fa.logEvent(Event.AD_REWARD, bundle {
            putString(Param.PLACEMENT, placement)
        })

    // --------------------------------------------------------------- Economy
    //  Стандартні події віртуальної валюти: Firebase малює по них готові
    //  sink/source-звіти економіки без жодного налаштування.
    override fun earnGems(amount: Int, source: String) =
        fa.logEvent(FirebaseAnalytics.Event.EARN_VIRTUAL_CURRENCY, bundle {
            putString(FirebaseAnalytics.Param.VIRTUAL_CURRENCY_NAME, "gems")
            putLong(FirebaseAnalytics.Param.VALUE, amount.toLong())
            putString(FirebaseAnalytics.Param.SOURCE, source)
        })

    override fun spendGems(amount: Int, item: String) =
        fa.logEvent(FirebaseAnalytics.Event.SPEND_VIRTUAL_CURRENCY, bundle {
            putString(FirebaseAnalytics.Param.VIRTUAL_CURRENCY_NAME, "gems")
            putLong(FirebaseAnalytics.Param.VALUE, amount.toLong())
            putString(FirebaseAnalytics.Param.ITEM_NAME, item)
        })

    // ------------------------------------------------------- User properties
    override fun setUserId(pid: String)         { fa.setUserId(pid) }
    override fun setHasOrbit3(owned: Boolean)   { fa.setUserProperty(Prop.HAS_ORBIT3, owned.toString()) }
    override fun setActiveSkin(name: String)    { fa.setUserProperty(Prop.SKIN_ACTIVE, name) }
    override fun setNoAds(owned: Boolean)       { fa.setUserProperty(Prop.NO_ADS, owned.toString()) }

    /** Бакети замість сирого числа: властивість міняється рідко, зрізи стабільні. */
    override fun setBestBucket(best: Int) {
        val bucket = when {
            best < 500  -> "0_499"
            best < 2000 -> "500_1999"
            else        -> "2000_plus"
        }
        fa.setUserProperty(Prop.BEST_BUCKET, bucket)
    }
}
