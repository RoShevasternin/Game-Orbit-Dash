package com.lewydo.orbitdash.game.model

import com.lewydo.orbitdash.game.state.GameState
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import com.lewydo.orbitdash.services.analytics.AnalyticsManager
import com.lewydo.orbitdash.services.leaderboard.LeaderboardScores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.ln
import kotlin.math.pow

// ----------------------------------------------------------------------------
//  ЄДИНА ТОЧКА ЗМІНИ ЕКОНОМІКИ.
//
//  Геми ростуть із чотирьох джерел (ран, реклама в меню, місії, стрік) і
//  витрачаються з трьох (апгрейди, скіни, orbit3). Якщо кожне місце саме
//  змінює лічильник і саме шле подію — одне з них рано чи пізно забуде, і
//  дірку в економіці буде видно лише через тиждень у звіті Firebase.
//
//  Тому: змінити геми можна ТІЛЬКИ через addGems/spendGems, і подія
//  відправляється там само. Плата — модель знає про AnalyticsManager;
//  прийнятно, бо той і так глобальний object без стану.
// ----------------------------------------------------------------------------
class PlayerModel(
    private val state: GameState,
    private val scope: CoroutineScope
) {

    // ------------------------------------------------------------------------
    // Flows
    // ------------------------------------------------------------------------
    val skinIdFlow   = state.skinIdFlow

    val gemsFlow     = state.gemsFlow
    val bestFlow     = state.bestFlow
    val runsFlow     = state.runsFlow
    val comboTotalFlow = state.comboTotalFlow

    val orbit3Flow   = state.orbit3Flow
    val noAdsFlow    = state.noAdsFlow

    val volMasterFlow = state.volMasterFlow
    val volMusicFlow  = state.volMusicFlow
    val volSfxFlow    = state.volSfxFlow

    val isLoadedFlow = state.isLoadedFlow

    // ------------------------------------------------------------------------
    // Snapshots — для читання «просто зараз» (рендер, Config рану)
    // ------------------------------------------------------------------------
    val currentSkinId: Int get() = state.skinIdFlow.value

    val gems  : Int     get() = state.gemsFlow.value
    val best  : Int     get() = state.bestFlow.value
    val runs  : Int     get() = state.runsFlow.value
    val comboTotal: Int get() = state.comboTotalFlow.value
    val orbit3: Boolean get() = state.orbit3Flow.value
    val noAds : Boolean get() = state.noAdsFlow.value

    val volMaster: Float get() = state.volMasterFlow.value
    val volMusic : Float get() = state.volMusicFlow.value
    val volSfx   : Float get() = state.volSfxFlow.value

    /** Читати тільки ПІСЛЯ isLoadedFlow: до завантаження тут порожньо. */
    val pid: String get() = state.pidFlow.value

    // ------------------------------------------------------------------------
    // Skin
    // ------------------------------------------------------------------------
    /** Тему НЕ чіпаємо тут — на flow підписаний GDXGame, джерело правди одне. */
    fun setSkin(id: Int) {
        if (!ThemeManager.isValidId(id) || id == currentSkinId) return
        state.skinIdFlow.value = id
        AnalyticsManager.setActiveSkin(ThemeManager.nameOf(id))
    }

    // ------------------------------------------------------------------------
    // Audio
    // ------------------------------------------------------------------------
    //
    //  Повзунок тягнуть пальцем — значення міняється десятки разів на секунду.
    //  Тому saveGame() тут НЕ кличемо: досить автозбереження (30 с) і запису
    //  на паузі, а диск не молотить під кожен піксель ходу повзунка.
    //
    fun setMasterVolume(v: Float) { state.volMasterFlow.value = v.coerceIn(0f, 1f) }
    fun setMusicVolume (v: Float) { state.volMusicFlow.value  = v.coerceIn(0f, 1f) }
    fun setSfxVolume   (v: Float) { state.volSfxFlow.value    = v.coerceIn(0f, 1f) }

    // ------------------------------------------------------------------------
    // Economy
    // ------------------------------------------------------------------------

    /**
     * Нарахувати геми. [source] — з AnalyticsManager.GemSource.
     * Нуль і від'ємне тихо ігноруються: викликач часто передає результат
     * обчислення (напр. RunEngine.bank(), який віддає 0 при повторі), і
     * змушувати кожного перевіряти — це та сама забута перевірка.
     */
    fun addGems(amount: Int, source: String) {
        if (amount <= 0) return
        state.gemsFlow.value += amount
        AnalyticsManager.earnGems(amount, source)
    }

    /**
     * Списати геми. [item] — з AnalyticsManager.Item.
     * @return false, якщо не вистачило — тоді баланс НЕ змінено.
     * Перевірка тут, а не у викликача: інакше кожен екран магазину нестиме
     * свою копію умови, і одна з них колись пропустить від'ємний баланс.
     */
    fun spendGems(amount: Int, item: String): Boolean {
        if (amount <= 0) return false
        if (state.gemsFlow.value < amount) return false

        state.gemsFlow.value -= amount
        AnalyticsManager.spendGems(amount, item)
        return true
    }

    // ------------------------------------------------------------------------
    // Run
    // ------------------------------------------------------------------------

    /**
     * Зафіксувати завершений ран: лічильник ранів, рекорд і комбо.
     * [combos] — ПРИРІСТ за цей ран (після ревайву той самий ран
     * фіксується вдруге — рахувати треба лише нові).
     * @return true, якщо це новий рекорд — для NEW BEST-чипа і run_end.
     *
     * ГЕМИ СЮДИ НЕ ВХОДЯТЬ СВІДОМО: на момент смерті сума ще не остаточна —
     * гравець може подвоїти її через x2·AD. Тому рекорд фіксуємо одразу, а
     * геми — окремо, коли гравець визначився:
     *
     *     player.addGems(engine.bank(mult), GemSource.RUN)
     *
     * RunEngine.bank() сам віддасть 0 при повторному виклику, тож подвійного
     * нарахування не буде навіть якщо натиснути і x2, і RESTART.
     */
    fun commitRun(score: Int, combos: Int): Boolean {
        state.runsFlow.value += 1
        if (combos > 0) state.comboTotalFlow.value += combos

        val newBest = score > state.bestFlow.value
        if (newBest) {
            state.bestFlow.value = score
            // Властивість користувача, а не подія: змінюється рідко, зріз
            // аудиторії має бути стабільним.
            AnalyticsManager.setBestBucket(score)
        }
        return newBest
    }

    /**
     * Чотири лідерборди: рекорд рану, COMBO (усього комбо),
     * програші (кожен ран закінчується смертю — тож це просто runs),
     * багатство (поточний баланс; у таблиці лишається найбільший).
     */
    fun leaderboardScores() = LeaderboardScores(
        best    = best.toLong(),
        combo   = comboTotal.toLong(),
        crashes = runs.toLong(),
        rich    = gems.toLong(),
    )

    // ------------------------------------------------------------------------
    // Purchases
    // ------------------------------------------------------------------------

    /** @return false, якщо не вистачило гемів або вже куплено. */
    fun buyOrbit3(price: Int): Boolean {
        if (orbit3) return false
        if (!spendGems(price, AnalyticsManager.Item.ORBIT3)) return false

        state.orbit3Flow.value = true
        AnalyticsManager.setHasOrbit3(true)
        return true
    }

    /** Купівля за реальні гроші — списання гемів немає, лише прапорець. */
    fun unlockNoAds() {
        if (noAds) return
        state.noAdsFlow.value = true
        AnalyticsManager.setNoAds(true)
    }

}