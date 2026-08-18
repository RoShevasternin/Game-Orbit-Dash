package com.lewydo.orbitdash

import android.app.Application
import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.lewydo.orbitdash.util.log

lateinit var appContext: Context private set

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        FirebaseMessaging.getInstance().token.addOnSuccessListener { log("FCM token: $it") }

    }

}