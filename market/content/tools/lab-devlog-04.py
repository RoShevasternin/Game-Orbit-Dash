#!/usr/bin/env python3
# ─────────────────────────────────────────────────────────────────────────────
# Лабораторна збірка для запису DEVLOG 04: автопілот + ЛОГ ЗВУКОВИХ ПОДІЙ.
#   python3 lab-devlog-04.py <копія проєкту Orbit Dash>
#
# Те саме, що lab-devlog-03.py (IS_DEBUG=false, лоадер → GameScreen, debugGod,
# автопілот, порожній bankRun), ПЛЮС дві речі, без яких DEVLOG 04 не зібрати:
#
#   • OD_SFX-лог. GameScreen.sfx() пише в logcat «OD_SFX <мс> <назва>» на кожну
#     подію. adb screenrecord звук НЕ пише — тому доріжку ролика я синтезую
#     окремо, тими самими рецептами SfxCatalog, і кладу кожен звук рівно на його
#     мітку. Це не імітація: у грі грає той самий wav із тих самих п'яти чисел.
#
#   • ПЛЕСК (clapper). Перші 2 кадри рану екран білий, у лог іде «OD_CLAP <мс>».
#     Це єдина точка, за якою відео і лог зводяться КАДР У КАДР: шукаю в записі
#     перший білий кадр — його час і є час OD_CLAP. Без нього звук поїде на
#     десятки мілісекунд, і тап звучатиме повз стрибок.
#
# Налаштування ті самі: debug.od.theme / debug.od.boost / debug.od.orbit3.
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

# 1b · log() мовчить при IS_DEBUG=false — у лабораторії потрібен окремий канал
patch(f"{APP}/util/util.kt",
      [("    if (IS_DEBUG) Log.i(\"OD_DEBUG\", message)",
        "    if (IS_DEBUG) Log.i(\"OD_DEBUG\", message)\n    else if (message.startsWith(\"OD_\")) Log.i(\"OD_LAB\", message)   // LAB")])

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

# 4 · екран гри: автопілот + лог звуку + плеск
AUTOPILOT = '''
    // ═══ LAB: автопілот, лог звуку і плеск — лише в лабораторній збірці ═══
    private val labBoosts = listOf(RunEngine.Boost.MAGNET, RunEngine.Boost.FRENZY, RunEngine.Boost.SLOW)
    private var labBoostEvery = 9f
    private var labBoostT = 4f          // перший буст через ~5 с після старту
    private var labBoostI = 0
    private var labTapCd  = 0f
    private var labReady  = false
    private var labClapF  = 0           // скільки кадрів ще тримати білий плеск

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
        log("OD_LABSETUP theme=${ThemeManager.currentId} boostEvery=$labBoostEvery")
    }

    /** Мітка часу звукової події. Синтезатор ролика кладе звук рівно сюди. */
    private fun labSfxLog(name: String) = log("OD_SFX ${System.currentTimeMillis()} $name")

    /** Два білі кадри на старті рану — точка зведення відео й логу. */
    private fun labClap() {
        labClapF = 2
        log("OD_CLAP ${System.currentTimeMillis()}")
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
    # лог кожної звукової події — рівно там, де вона реально грає
    ("    private fun sfx(sound: Sfx, buzzMs: Int = 0) {\n        gdxGame.soundUtil.play(sound)",
     "    private fun sfx(sound: Sfx, buzzMs: Int = 0) {\n        labSfxLog(sound.name)   // LAB\n        gdxGame.soundUtil.play(sound)"),
    # плеск на старті рану
    ("        gdxGame.analytics.runStart()\n        sfx(SfxCatalog.RUN_START)",
     "        gdxGame.analytics.runStart()\n        labClap()             // LAB\n        sfx(SfxCatalog.RUN_START)"),
    # білий кадр поверх усього, поки labClapF > 0
    ("    override fun render(delta: Float) {\n        super.render(delta)",
     """    override fun render(delta: Float) {
        if (labClapF > 0) {                                   // LAB: плеск
            labClapF--
            com.badlogic.gdx.utils.ScreenUtils.clear(1f, 1f, 1f, 1f)
            return
        }
        super.render(delta)"""),
])
print("лабораторію підготовлено:", LAB)
