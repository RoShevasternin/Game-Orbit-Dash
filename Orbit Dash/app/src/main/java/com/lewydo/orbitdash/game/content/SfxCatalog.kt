package com.lewydo.orbitdash.game.content

import com.lewydo.orbitdash.engine.RunEngine
import kotlin.math.pow

// ----------------------------------------------------------------------------
//  КАТАЛОГ ЗВУКІВ — РЕЦЕПТИ З ПРОТОТИПУ ОДИН В ОДИН.
//
//  У JSX-прототипі жодного аудіофайлу немає: кожен звук — виклик
//  beep(f, dur, type, vol, slide) на WebAudio-осциляторі. Тут ті самі п'ять
//  чисел під тими самими подіями. Хто їх озвучує — SoundSynth (запікає у wav
//  один раз), хто грає — SoundUtil. Каталог не знає ні про libGDX, ні про
//  файли: це довідник, як BoostCatalog.
//
//  Beep = осцилятор WebAudio:
//    f      — стартова частота, Гц
//    dur    — тривалість, с; гучність за цей час експоненційно спадає до 0.001
//    wave   — форма: sine / square / sawtooth
//    vol    — пікова гучність (лінійний gain, як у WebAudio)
//    slide  — зсув частоти до кінця, Гц: f → max(40, f + slide), експоненційно
//
//  ДРАБИНИ. Комбо і ланцюжок гемів підіймають висоту на півтон за крок:
//  semitone(base, n) = base · 2^(n/12) — та сама формула, що в прототипі.
//  Кожен щабель — окремий запечений звук: SoundPool міняє pitch лише разом
//  із тривалістю, а крок 70–80 мс мусить лишатись рівним на всій драбині.
// ----------------------------------------------------------------------------

enum class Wave { SINE, SQUARE, SAW }

/** Один осцилятор прототипу. Числа — з beep(...) без перерахунків. */
data class Beep(
    val f    : Float,
    val dur  : Float,
    val wave : Wave,
    val vol  : Float,
    val slide: Float,
)

/** Звук події: один або кілька осциляторів разом (ORBIT3 — два). */
class Sfx(val name: String, val layers: List<Beep>) {
    constructor(name: String, vararg beeps: Beep) : this(name, beeps.toList())
    override fun toString() = name
}

object SfxCatalog {

    /** Ланцюжок гемів: підряд до 12 півтонів — октава над 700 Гц. */
    const val GEM_CHAIN_MAX = 12

    private fun semitone(base: Float, n: Int): Float = base * 2f.pow(n / 12f)

    // ── інтерфейс ───────────────────────────────────────────────────────────
    /** Будь-яка кнопка (у прототипі — showRewarded / вибір скіна). */
    val UI_TICK   = Sfx("ui_tick",   Beep(500f, 0.05f, Wave.SQUARE, 0.06f,   0f))
    /** Перемикач (у прототипі — dev-тумблер; у налаштуваннях там звуку немає). */
    val CHECK_BOX = Sfx("check_box", Beep(760f, 0.08f, Wave.SINE,   0.08f, 200f))
    /** Завантажено, з'явилось «TAP TO START». */
    val READY     = Sfx("ready",     Beep(660f, 0.14f, Wave.SINE,   0.09f, 260f))
    /** Тап по лоадеру → меню. */
    val MENU_IN   = Sfx("menu_in",   Beep(520f, 0.12f, Wave.SINE,   0.10f, 260f))

    // ── ран ─────────────────────────────────────────────────────────────────
    val RUN_START   = Sfx("run_start",   Beep(440f, 0.10f, Wave.SINE,   0.08f,  220f))
    /** Стрибок назовні (dir > 0); всередину — на 40 Гц вище. */
    val TAP_OUT     = Sfx("tap_out",     Beep(300f, 0.06f, Wave.SQUARE, 0.07f,  200f))
    val TAP_IN      = Sfx("tap_in",      Beep(340f, 0.06f, Wave.SQUARE, 0.07f,  200f))
    /** MAGNET / GEM x2 / SLOW-MO — бусти з таймером. */
    val BOOST       = Sfx("boost",       Beep(700f, 0.18f, Wave.SINE,   0.11f,  350f))
    val SHIELD_UP   = Sfx("shield_up",   Beep(600f, 0.15f, Wave.SINE,   0.10f,  200f))
    /** Щит з'їв шип. */
    val SHIELD_SAVE = Sfx("shield_save", Beep(360f, 0.15f, Wave.SQUARE, 0.10f, -120f))
    val PULSE       = Sfx("pulse",       Beep(120f, 0.35f, Wave.SAW,    0.16f,  -60f))
    /** Третя орбіта: дзвін угору + суббас униз, разом. */
    val ORBIT3      = Sfx("orbit3",
        Beep(500f, 0.40f, Wave.SINE, 0.12f, 500f),
        Beep( 90f, 0.50f, Wave.SINE, 0.20f, -30f),
    )
    val DEATH       = Sfx("death",       Beep(220f, 0.50f, Wave.SAW,    0.15f, -170f))
    val REVIVE      = Sfx("revive",      Beep(520f, 0.25f, Wave.SINE,   0.12f,  420f))

    // ── магазин / місії — екранів ще немає, звуки вже названі ───────────────
    val BUY       = Sfx("buy",       Beep(700f, 0.12f, Wave.SINE, 0.10f, 250f))   // апгрейд
    val UNLOCK    = Sfx("unlock",    Beep(700f, 0.15f, Wave.SINE, 0.10f, 300f))   // скін куплено
    val CLAIM     = Sfx("claim",     Beep(760f, 0.18f, Wave.SINE, 0.12f, 300f))   // місія / стрік
    val PURCHASE  = Sfx("purchase",  Beep(760f, 0.20f, Wave.SINE, 0.12f, 350f))   // орбіта 3 / без реклами
    val NAME_SAVE = Sfx("name_save", Beep(600f, 0.08f, Wave.SINE, 0.08f, 150f))

    // ── драбини ─────────────────────────────────────────────────────────────
    /** Іскра спіймана: n = combo після +1 (1..MAX_COMBO). Індекс 0 — про запас. */
    private val COMBO = List(RunEngine.MAX_COMBO + 1) { n ->
        Sfx("combo_$n", Beep(semitone(950f, n), 0.07f, Wave.SQUARE, 0.09f, 250f))
    }
    /** Гем: n = довжина ланцюжка (0 — перший гем, далі +1 за кожен у вікні). */
    private val GEM = List(GEM_CHAIN_MAX + 1) { n ->
        Sfx("gem_$n", Beep(semitone(700f, n), 0.08f, Wave.SINE, 0.10f, 250f))
    }

    fun combo(n: Int): Sfx = COMBO[n.coerceIn(0, RunEngine.MAX_COMBO)]
    fun gem(n: Int)  : Sfx = GEM[n.coerceIn(0, GEM_CHAIN_MAX)]

    /** Усе, що треба запекти. Порядок — лише порядок файлів у кеші. */
    val all: List<Sfx> = listOf(
        UI_TICK, CHECK_BOX, READY, MENU_IN,
        RUN_START, TAP_OUT, TAP_IN, BOOST, SHIELD_UP, SHIELD_SAVE, PULSE, ORBIT3, DEATH, REVIVE,
        BUY, UNLOCK, CLAIM, PURCHASE, NAME_SAVE,
    ) + COMBO + GEM
}
