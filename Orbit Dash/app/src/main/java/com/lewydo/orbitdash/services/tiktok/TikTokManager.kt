package com.lewydo.orbitdash.services.tiktok

import android.app.Application
import com.lewydo.orbitdash.BuildConfig
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.util.log
import com.tiktok.TikTokBusinessSdk
import com.tiktok.appevents.base.TTBaseEvent
import java.util.concurrent.atomic.AtomicBoolean

object TikTokManager {

    private val once = AtomicBoolean(true)

    fun initialize(app: Application, appIds: List<String>, appSecret: String) {
        if (once.getAndSet(false)) {
            val ttAppIds = appIds.joinToString(",")

            val config = TikTokBusinessSdk.TTConfig(app, appSecret)
                .setAppId(app.packageName)
                .setTTAppId(ttAppIds)
                .setLogLevel(
                    if (BuildConfig.DEBUG) TikTokBusinessSdk.LogLevel.DEBUG
                    else TikTokBusinessSdk.LogLevel.NONE
                )
                .apply { if (BuildConfig.DEBUG) openDebugMode() }

            TikTokBusinessSdk.initializeSdk(config, object : TikTokBusinessSdk.TTInitCallback {
                override fun success() {
                    log("TikTok SDK initialized SUCCESSFULLY, ids=$ttAppIds")
                }

                override fun fail(code: Int, msg: String) {
                    log("TikTok SDK FAILED: $code $msg")
                }
            })
        }
    }

    fun sendTestEvent() {
        gdxGame.activity.runOnUiThread {
            val event = TTBaseEvent.newBuilder("test_event")
                .addProperty("ts", System.currentTimeMillis())
                .build()
            TikTokBusinessSdk.trackTTEvent(event)
            TikTokBusinessSdk.flush()   // ← форсимо негайну відправку
            log("TikTok test event sent")
        }
    }
}

//object TikTokManager {
//
//    fun initialize(app: Application) {
//        val appIds    = "${BuildConfig.TIKTOK_APP_ID},${BuildConfig.TIKTOK_APP_ID_2}"
//        val appSecret = BuildConfig.TIKTOK_APP_SECRET
//        val debug     = BuildConfig.DEBUG
//
//        val config = TikTokBusinessSdk.TTConfig(app, appSecret)
//            .setAppId(app.packageName)
//            .setTTAppId(appIds)
//            .setLogLevel(
//                if (debug) TikTokBusinessSdk.LogLevel.DEBUG
//                else TikTokBusinessSdk.LogLevel.NONE
//            )
//            .apply { if (debug) openDebugMode() }
//
//        TikTokBusinessSdk.initializeSdk(config, object : TikTokBusinessSdk.TTInitCallback {
//            override fun success() { log("TikTok SDK initialized SUCCESSFULLY") }
//            override fun fail(code: Int, msg: String) { log("TikTok SDK FAILED: $code $msg") }
//        })
//    }
//
//}