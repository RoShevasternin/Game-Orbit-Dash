package com.lewydo.orbitdash.services.leaderboard

import android.app.Activity
import android.content.Intent
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.lewydo.orbitdash.util.log

// ----------------------------------------------------------------------------
// LeaderboardManager — Google Play Games Services v2, чотири лідерборди
//
//   submitIfSignedIn(scores) — усі результати, ТИХО (лише якщо вже увійшов)
//   showAll(scores)          — вхід за потреби → результати → список лідербордів Google
//
//   Лідерборд тримає НАЙКРАЩЕ надіслане значення гравця, тож накопичувальні
//   (комбо, програші) надсилаємо загальною сумою — вона лише росте.
//   Багатство — поточний баланс: у таблиці лишається найбільший, який був.
//   Надіслати старше чи те саме значення — безпечно, Play Games його відкине.
// ----------------------------------------------------------------------------

/** ID лідербордів із Play Console (strings.xml). */
data class LeaderboardIds(
    val best   : String,
    val combo  : String,
    val crashes: String,
    val rich   : String,
)

/** Що відправляти: рекорд рану, усього комбо, усього програшів, баланс гемів. */
data class LeaderboardScores(
    val best   : Long,
    val combo  : Long,
    val crashes: Long,
    val rich   : Long,
)

class LeaderboardManager(
    private val activity: Activity,
    private val ids     : LeaderboardIds,
) {

    companion object {
        // довільний код для startActivityForResult (UI лідерборда)
        const val RC_LEADERBOARD_UI = 9004

        /** Заглушка в strings.xml, поки лідерборд не створено в Play Console. */
        private const val NO_ID = "000"
    }

    private var isAuthenticated = false

    /**
     * Відкриття вже в дорозі. Раніше від подвійного тапу беріг екран-трамплін
     * (він гасив меню), тепер меню лишається живим і клікабельним увесь час,
     * поки Google асинхронно готує intent.
     */
    private var isOpening = false

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
    // Submit
    // ------------------------------------------------------------------------

    /**
     * Тихо: лише якщо гравець уже увійшов. Кличеться після кожного рану, а
     * вікно входу Google посеред гри ставило застосунок на паузу. Хто не
     * увійшов, відправить результати, коли сам відкриє RANKS (showAll).
     */
    fun submitIfSignedIn(scores: LeaderboardScores) {
        if (!isAuthenticated) return
        submitAll(scores)
    }

    private fun submitAll(scores: LeaderboardScores) {
        submit(ids.best,    scores.best)
        submit(ids.combo,   scores.combo)
        submit(ids.crashes, scores.crashes)
        submit(ids.rich,    scores.rich)
    }

    /**
     * submitScoreImmediate, а не submitScore: той відправляє наосліп, і
     * неправильний ID чи неопублікований лідерборд мовчки губляться. Тут Google
     * відповідає — у лозі видно, прийняв він результат чи ні.
     */
    private fun submit(id: String, value: Long) {
        if (value <= 0 || id.isBlank() || id == NO_ID) return
        PlayGames.getLeaderboardsClient(activity)
            .submitScoreImmediate(id, value)
            .addOnSuccessListener { log("Leaderboard: $id = $value прийнято") }
            .addOnFailureListener { e -> log("Leaderboard: $id = $value відхилено: ${e.message}") }
    }

    // ------------------------------------------------------------------------
    // Show standard Google UI
    // ------------------------------------------------------------------------

    /**
     * RANKS: список усіх лідербордів гри. [scores] — із сейву: відправляємо
     * перед показом, щоб гравець бачив себе навіть без жодного рану після входу.
     */
    fun showAll(scores: LeaderboardScores) {
        if (isOpening) return
        isOpening = true

        ensureSignedIn(onFailed = { isOpening = false }) {
            submitAll(scores)
            PlayGames.getLeaderboardsClient(activity)
                .allLeaderboardsIntent
                .addOnSuccessListener { intent: Intent ->
                    isOpening = false
                    // startActivityForResult обов'язковий навіть без результату —
                    // API так отримує identity пакета (вимога Google).
                    activity.startActivityForResult(intent, RC_LEADERBOARD_UI)
                }
                .addOnFailureListener { e ->
                    isOpening = false
                    log("Leaderboard: showAll failed: ${e.message}")
                }
        }
    }

    // ------------------------------------------------------------------------
    // Sign-in helper
    // ------------------------------------------------------------------------
    //
    // Якщо вже авторизовані — одразу виконуємо дію. Інакше пробуємо
    // manualSignIn (показує діалог входу Google), і за успіху виконуємо.

    private fun ensureSignedIn(onFailed: () -> Unit = {}, onReady: () -> Unit) {
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
                    onFailed()
                }
            }
    }
}
