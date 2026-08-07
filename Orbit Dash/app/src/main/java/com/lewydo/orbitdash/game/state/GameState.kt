package com.lewydo.orbitdash.game.state

import com.lewydo.orbitdash.game.data.PlayerData
import kotlinx.coroutines.flow.MutableStateFlow

class GameState {

    // ------------------------------------------------------------------------
    // Flows
    // ------------------------------------------------------------------------
    val xpFlow     = MutableStateFlow(0L)
    val skinIdFlow = MutableStateFlow(0)

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

        // сигнал "усе завантажено" — після всіх присвоєнь
        isLoadedFlow.value = true
    }

    fun toPlayerData() = PlayerData(
        xp     = xpFlow.value,
        skinId = skinIdFlow.value,
    )
}