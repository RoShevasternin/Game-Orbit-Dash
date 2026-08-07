package com.lewydo.orbitdash.services.leaderboard

import android.app.Activity
import android.content.Intent
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.lewydo.orbitdash.util.log

// ----------------------------------------------------------------------------
// LeaderboardManager — Google Play Games Services v2
//
//   submitScore() — відправити рахунок (XP) у лідерборд
//   showLeaderboard() — відкрити стандартний UI Google
//
//   Sign-in у v2 автоматичний при старті (PlayGamesSdk.initialize).
//   Лідерборд по XP: level рахується локально з XP (PlayerModel.xpToLevel).
// ----------------------------------------------------------------------------

class LeaderboardManager(
    private val activity      : Activity,
    private val leaderboardId : String
) {

    companion object {
        // довільний код для startActivityForResult (UI лідерборда)
        const val RC_LEADERBOARD_UI = 9004
    }

    private var isAuthenticated = false

    // ------------------------------------------------------------------------
    // Init — викликати в MainActivity.onCreate
    // ------------------------------------------------------------------------

    fun initialize() {
        PlayGamesSdk.initialize(activity)

        // v2: sign-in автоматичний. Перевіряємо результат.
        PlayGames.getGamesSignInClient(activity)
            .isAuthenticated
            .addOnCompleteListener { task ->
                isAuthenticated = task.isSuccessful && task.result.isAuthenticated
                log("Leaderboard: authenticated = $isAuthenticated")
            }
    }

    // ------------------------------------------------------------------------
    // Submit score (XP)
    // ------------------------------------------------------------------------

    fun submitScore(xp: Long) {
        if (xp <= 0) return
        ensureSignedIn {
            PlayGames.getLeaderboardsClient(activity).submitScore(leaderboardId, xp)
            log("Leaderboard: submitted XP=$xp")
        }
    }

    // ------------------------------------------------------------------------
    // Show standard Google UI
    // ------------------------------------------------------------------------

    fun showLeaderboard() {
        ensureSignedIn {
            PlayGames.getLeaderboardsClient(activity)
                .getLeaderboardIntent(leaderboardId)
                .addOnSuccessListener { intent: Intent ->
                    // startActivityForResult обов'язковий навіть без результату —
                    // API так отримує identity пакета (вимога Google).
                    activity.startActivityForResult(intent, RC_LEADERBOARD_UI)
                }
                .addOnFailureListener { e ->
                    log("Leaderboard: showLeaderboard failed: ${e.message}")
                }
        }
    }

    // ------------------------------------------------------------------------
    // Sign-in helper
    // ------------------------------------------------------------------------
    //
    // Якщо вже авторизовані — одразу виконуємо дію. Інакше пробуємо
    // manualSignIn (показує діалог входу Google), і за успіху виконуємо.

    private fun ensureSignedIn(onReady: () -> Unit) {
        if (isAuthenticated) {
            onReady()
            return
        }
        PlayGames.getGamesSignInClient(activity)
            .signIn()
            .addOnCompleteListener { task ->
                isAuthenticated = task.isSuccessful && task.result.isAuthenticated
                if (isAuthenticated) {
                    onReady()
                } else {
                    log("Leaderboard: sign-in failed/declined")
                }
            }
    }
}