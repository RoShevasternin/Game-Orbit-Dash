#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Лабораторна збірка для запису DEVLOG 03: автопілот + бусти за розкладом.
#   python3 lab-devlog-03.py <копія проєкту Orbit Dash>
#
# Проєкт НЕ чіпає — працює лише з копією (rsync у скретчпад). Що робить:
#   • IS_DEBUG = false               — жодної дебаг-панелі й статистики в кадрі
#   • LoaderScreen → GameScreen      — без меню, тап на лоадері одразу в ран
#   • RunEngine.debugGod             — шип не вбиває: запис без смертей
#   • GameScreen.labAutopilot()      — тап на іскрі (комбо) і від шипа; буст
#                                      кожні N с по колу MAGNET → GEM ×2 → SLOW-MO;
#                                      на сусіднє кільце за бустом, якщо чисто
#   • bankRun() — порожній           — геми рану не йдуть у сейв і в лідерборди
#
# Налаштування — системні властивості, без перезбірки:
#   adb shell setprop debug.od.theme 0      # id палітри ThemeManager (0 = NEON)
#   adb shell setprop debug.od.boost 9      # буст кожні N секунд
#   adb shell setprop debug.od.orbit3 1     # третя орбіта з початку
# ─────────────────────────────────────────────────────────────────────────────
import sys, os

LAB = sys.argv[1].rstrip("/")
APP = f"{LAB}/app/src/main/java/com/lewydo/orbitdash"
ENG = f"{LAB}/engine/src/main/kotlin/com/lewydo/orbitdash/engine/RunEngine.kt"

def patch(path, pairs):
    s = open(path, encoding="utf-8").read()
    for old, new in pairs:
        if old not in s: sys.exit(f"якір не знайдено у {os.path.basename(path)}:\n{old[:120]}")
        s = s.replace(old, new, 1)
    open(path, "w", encoding="utf-8").write(s)
    print("ok", os.path.relpath(path, LAB))

# 1 · без дебаг-інтерфейсу
patch(f"{APP}/game/utils/global/global.kt",
      [("val IS_DEBUG = BuildConfig.DEBUG", "val IS_DEBUG = false   // LAB: запис девлогу")])

# 2 · лоадер одразу в гру
patch(f"{APP}/game/screens/LoaderScreen.kt",
      [("private val NEXT_SCREEN_NAME = MenuScreen::class.java.name",
        "private val NEXT_SCREEN_NAME = GameScreen::class.java.name   // LAB")])

# 3 · рушій: безсмертя
patch(ENG, [
    ("    var debugEzCombo = false",
     "    var debugEzCombo = false\n\n    /** LAB: шип не вбиває — запис девлогу без смертей. */\n    var debugGod = false"),
    ("Kind.SPIKE -> if (e.s > 0.5f && e.ring == ringIndex && arrived && invuln <= 0f) {",
     "Kind.SPIKE -> if (e.s > 0.5f && e.ring == ringIndex && arrived && invuln <= 0f && !debugGod) {"),
])

# 4 · екран гри: автопілот
AUTOPILOT = '''
    // ═══ LAB: автопілот для запису девлогу — лише в лабораторній збірці ═══
    private val labBoosts = listOf(RunEngine.Boost.MAGNET, RunEngine.Boost.FRENZY, RunEngine.Boost.SLOW)
    private var labBoostEvery = 9f
    private var labBoostT = 4f          // перший буст через ~5 с після старту
    private var labBoostI = 0
    private var labTapCd  = 0f
    private var labReady  = false

    private fun labProp(name: String): String? = try {
        Runtime.getRuntime().exec(arrayOf("getprop", name)).inputStream
            .bufferedReader().readText().trim().ifEmpty { null }
    } catch (e: Exception) { null }

    private fun labSetup() {
        if (labReady) return
        labReady = true
        labProp("debug.od.theme")?.toIntOrNull()?.let { if (ThemeManager.isValidId(it)) ThemeManager.switchTo(it) }
        labProp("debug.od.boost")?.toFloatOrNull()?.let { labBoostEvery = it }
        if (labProp("debug.od.orbit3") == "1") { debugOrbit3 = true; engine.debugSetOrbit3(true) }
        log("LAB theme=${ThemeManager.currentId} boostEvery=$labBoostEvery")
    }

    private fun labAngDiff(a: Float, b: Float): Float = ((a - b) % 360f + 540f) % 360f - 180f

    private fun labAutopilot(dt: Float) {
        labSetup()
        engine.debugGod = true
        if (engine.phase != RunEngine.Phase.RUN) return

        labBoostT += dt
        if (labBoostT >= labBoostEvery) {
            labBoostT = 0f
            engine.debugSpawnBoost(labBoosts[labBoostI++ % labBoosts.size])
        }

        labTapCd -= dt
        if (labTapCd > 0f) return

        val ang  = engine.angle
        val ring = engine.ringIndex
        var next = ring + engine.dir
        if (next > engine.ringCount - 1 || next < 0) next = ring - engine.dir
        val rr   = engine.ringR[ring]
        val step = 300f / rr * 57.3f / 60f          // максимум градусів за кадр на цьому кільці

        var tap = false
        var nearSpike = 999f
        for (e in engine.entities) {
            if (e.s < 0.5f || e.ring != ring || e.kind != RunEngine.Kind.SPIKE) continue
            val sparkA = engine.sparkAngle(e)
            if (sparkA != null) {
                // іскра ще висить: тап рівно на ній — і комбо, і ухил
                val rs = labAngDiff(sparkA, ang)
                if (rs > -1f && rs <= step * 1.2f + 1f) tap = true
                if (rs > 0f) nearSpike = minOf(nearSpike, rs)
            } else {
                val rel = labAngDiff(e.a, ang)
                if (rel >= 0f && rel <= step * 1.5f + 12f) tap = true
                if (rel > 0f) nearSpike = minOf(nearSpike, rel)
            }
        }
        if (!tap && nearSpike > 30f) {
            // буст на сусідньому кільці попереду й шлях чистий — сходити за ним
            val boostNext = engine.entities.any { e ->
                e.s > 0.5f && e.ring == next && e.kind == RunEngine.Kind.BOOST && labAngDiff(e.a, ang) in 5f..70f
            }
            val blocked = engine.entities.any { s ->
                s.kind == RunEngine.Kind.SPIKE && s.ring == next && labAngDiff(s.a, ang) in -5f..25f
            }
            if (boostNext && !blocked) tap = true
        }
        if (tap) { engine.tap(); labTapCd = 0.25f }
    }
'''
patch(f"{APP}/game/screens/GameScreen.kt", [
    ("        engine.update(delta * debugTimeScale)\n",
     "        engine.update(delta * debugTimeScale)\n        labAutopilot(delta)   // LAB\n"),
    ("    private fun bankRun() {\n        val gems = engine.bank()\n",
     "    private fun bankRun() {\n        if (true) return      // LAB: нічого не банкуємо й не шлемо в лідерборди\n        val gems = engine.bank()\n"),
    ("    private val runListener = object : RunEngine.Listener {",
     AUTOPILOT + "\n    private val runListener = object : RunEngine.Listener {"),
])
print("лабораторію підготовлено:", LAB)
