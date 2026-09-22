package com.lewydo.orbitdash.game.state

import com.lewydo.orbitdash.game.data.PlayerData
import com.lewydo.orbitdash.game.data.AppJson
import com.lewydo.orbitdash.game.data.decodeOrDefault
import com.lewydo.orbitdash.game.data.PlayerDataMigration
import com.lewydo.orbitdash.game.manager.DataStoreManager
import com.lewydo.orbitdash.game.model.PlayerModel
import com.lewydo.orbitdash.util.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

class SaveGameStateManager(
    private val gameState  : GameState,
    private val scope      : CoroutineScope
) {

    private val dataStore  = DataStoreManager.Player
    private val mutex      = Mutex()
    private var autoSaveJob: Job? = null

    // ------------------------------------------------------------------------
    // Load — при старті гри
    // ------------------------------------------------------------------------

    fun load() {
        scope.launch(Dispatchers.IO) {
            val raw = dataStore.get()
            // Безпечне декодування: несумісний/пошкоджений save → default (без краху)
            val decoded = decodeOrDefault(
                deserializer = PlayerData.serializer(),
                raw          = raw,
                default      = PlayerData(),
                tag          = "SaveGameStateManager",
            )
            // Міграція між версіями схеми (за потреби)
            val data = PlayerDataMigration.migrate(decoded)

            gameState.loadFrom(data)
            logState("GAME STATE LOADED", data)
        }
    }

    // ------------------------------------------------------------------------
    // Save — при паузі або вручну
    // ------------------------------------------------------------------------

    fun save() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val data = gameState.toPlayerData()
                val json = AppJson.encodeToString(PlayerData.serializer(), data)
                dataStore.update { json }
                logState("GAME STATE SAVED", data)
            }
        }
    }

    // ------------------------------------------------------------------------
    // Auto save — запускати в onCreate, зупиняти в onDestroy
    // ------------------------------------------------------------------------

    fun startAutoSave(intervalSec: Int = 30) {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay((intervalSec * 1000L).milliseconds)
                mutex.withLock {
                    val data = gameState.toPlayerData()
                    val json = AppJson.encodeToString(PlayerData.serializer(), data)
                    dataStore.update { json }
                    logState("GAME STATE AUTO-SAVED", data)
                }
            }
        }
    }

    fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    // ------------------------------------------------------------------------
    // Log
    // ------------------------------------------------------------------------

    /**
     * Дзеркало PlayerData: кожне поле сейву має бути тут. Сенс логу — побачити,
     * що кругообіг «стан → JSON → стан» нічого не загубив; поле, якого немає в
     * коробці, з логу не перевіриш. У релізі не друкується — log() мовчить.
     */
    private fun logState(title: String, data: PlayerData) {
        log("""
        
        ╔════════════════════════════════════════════╗
        ║  $title
        ╠════════════════════════════════════════════╣
        ║  SCHEMA       : v${data.schemaVersion}
        ║  PID          : ${data.pid}
        ║  SKIN_ID      : ${data.skinId}
        ╟────────────────────────────────────────────╢
        ║  GEMS         : ${data.gems}
        ║  BEST         : ${data.best}
        ║  RUNS         : ${data.runs}
        ║  COMBO TOTAL  : ${data.comboTotal}
        ╟────────────────────────────────────────────╢
        ║  ORBIT III    : ${if (data.orbit3) "OWNED" else "—"}
        ║  NO ADS       : ${if (data.noAds) "OWNED" else "—"}
        ╟────────────────────────────────────────────╢
        ║  SOUND        : master ${pct(data.volMaster)} · music ${pct(data.volMusic)} · sfx ${pct(data.volSfx)}
        ╚════════════════════════════════════════════╝
    """.trimIndent())
    }

    private fun pct(v: Float) = "${(v * 100).roundToInt()} %"
}