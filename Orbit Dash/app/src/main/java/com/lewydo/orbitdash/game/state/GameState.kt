package com.lewydo.orbitdash.game.state

import com.lewydo.orbitdash.game.data.PlayerData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

class GameState {

    // ------------------------------------------------------------------------
    // Flows
    // ------------------------------------------------------------------------
    val pidFlow    = MutableStateFlow("")
    val skinIdFlow = MutableStateFlow(0)

    // Економіка й прогрес
    val gemsFlow   = MutableStateFlow(0)
    val bestFlow   = MutableStateFlow(0)
    val runsFlow   = MutableStateFlow(0)
    val comboTotalFlow = MutableStateFlow(0)

    // Покупки
    val orbit3Flow = MutableStateFlow(false)
    val noAdsFlow  = MutableStateFlow(false)

    // Звук: повзунки гравця, 0..1 (формула — AudioMixer)
    val volMasterFlow = MutableStateFlow(1f)
    val volMusicFlow  = MutableStateFlow(1f)
    val volSfxFlow    = MutableStateFlow(1f)

    /** LOAD SIGNAL
     * Стає true ПІСЛЯ повного loadFrom. Моделі, що залежать від збереженого
     * стану (напр. GoalsModel), чекають саме його — це усуває race з gridFlow.
     */
    val isLoadedFlow = MutableStateFlow(false)

    // ------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------
    //
    //  Стан — ДЗЕРКАЛО сейву, без жодної логіки. Усі правила (чи вистачає
    //  гемів, чи це рекорд) живуть у PlayerModel: тут лише перекладання
    //  значень туди-сюди, тож loadFrom неможливо «зламати правилом».
    //
    fun loadFrom(data: PlayerData) {
        skinIdFlow.value = data.skinId

        gemsFlow.value   = data.gems
        bestFlow.value   = data.best
        runsFlow.value   = data.runs
        comboTotalFlow.value = data.comboTotal

        orbit3Flow.value = data.orbit3
        noAdsFlow.value  = data.noAds

        volMasterFlow.value = data.volMaster
        volMusicFlow.value  = data.volMusic
        volSfxFlow.value    = data.volSfx

        // Старий сейв без pid → народжуємо тут: loadFrom — єдине місце, де
        // стан гарантовано проходить при кожному запуску, і міграція
        // відбувається сама, без окремого кроку в PlayerDataMigration.
        pidFlow.value = data.pid.ifEmpty { newPid() }

        // сигнал "усе завантажено" — після всіх присвоєнь
        isLoadedFlow.value = true
    }

    fun toPlayerData() = PlayerData(
        skinId = skinIdFlow.value,
        pid    = pidFlow.value,

        gems   = gemsFlow.value,
        best   = bestFlow.value,
        runs   = runsFlow.value,
        comboTotal = comboTotalFlow.value,

        orbit3 = orbit3Flow.value,
        noAds  = noAdsFlow.value,

        volMaster = volMasterFlow.value,
        volMusic  = volMusicFlow.value,
        volSfx    = volSfxFlow.value,
    )

    /**
     * 16 hex-символів: досить для унікальності, читабельно в консолях.
     * kotlin.uuid.Uuid (стабільний із Kotlin 2.4) віддає hex без дефісів
     * одразу — на відміну від java.util.UUID, якому потрібен replace("-").
     * Ентропія та сама: під капотом SecureRandom.
     */
    private fun newPid() = Uuid.random().toHexString().take(16).uppercase()
}