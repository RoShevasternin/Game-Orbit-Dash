package com.lewydo.orbitdash

import android.app.Application
import android.content.Context

lateinit var appContext: Context private set

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        //FirebaseMessaging.getInstance().token.addOnSuccessListener { log("FCM token: $it") }

    }

}