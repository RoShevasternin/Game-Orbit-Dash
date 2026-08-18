package com.lewydo.orbitdash.game.model

import com.lewydo.orbitdash.game.state.GameState
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.ln
import kotlin.math.pow

class PlayerModel(
    private val state: GameState,
    private val scope: CoroutineScope
) {

    // ------------------------------------------------------------------------
    // Flows
    // ------------------------------------------------------------------------
    val xpFlow       = state.xpFlow
    val skinIdFlow   = state.skinIdFlow

    val isLoadedFlow = state.isLoadedFlow

    val currentSkinId: Int get() = state.skinIdFlow.value

    /** Читати тільки ПІСЛЯ isLoadedFlow: до завантаження тут порожньо. */
    val pid: String get() = state.pidFlow.value

    /** Тему НЕ чіпаємо тут — на flow підписаний GDXGame, джерело правди одне. */
    fun setSkin(id: Int) {
        if (!ThemeManager.isValidId(id) || id == currentSkinId) return
        state.skinIdFlow.value = id
    }

}