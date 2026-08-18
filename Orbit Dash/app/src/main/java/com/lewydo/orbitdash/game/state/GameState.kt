package com.lewydo.orbitdash.game.state

import com.lewydo.orbitdash.game.data.PlayerData
import kotlinx.coroutines.flow.MutableStateFlow

class GameState {

    // ------------------------------------------------------------------------
    // Flows
    // ------------------------------------------------------------------------
    val xpFlow     = MutableStateFlow(0L)
    val skinIdFlow = MutableStateFlow(0)
    val pidFlow    = MutableStateFlow("")

    /** LOAD SIGNAL
     * Стає true ПІСЛЯ повного loadFrom. Моделі, що залежать від збереженого
     * стану (напр. GoalsModel), чекають саме його — це усуває race з gridFlow.
     */
    val isLoadedFlow = MutableStateFlow(false)

    // ------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------
    fun loadFrom(data: PlayerData) {
        xpFlow.value     = data.xp
        skinIdFlow.value = data.skinId

        // Старий сейв без pid → народжуємо тут: loadFrom — єдине місце, де
        // стан гарантовано проходить при кожному запуску, і міграція
        // відбувається сама, без окремого кроку в PlayerDataMigration.
        pidFlow.value = data.pid.ifEmpty { newPid() }

        // сигнал "усе завантажено" — після всіх присвоєнь
        isLoadedFlow.value = true
    }

    fun toPlayerData() = PlayerData(
        xp     = xpFlow.value,
        skinId = skinIdFlow.value,
        pid    = pidFlow.value,
    )

    /** 16 hex-символів: досить для унікальності, читабельно в консолях. */
    private fun newPid() =
        java.util.UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
}