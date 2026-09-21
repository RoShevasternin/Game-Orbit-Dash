package com.lewydo.orbitdash.game.actors.fx

import com.lewydo.orbitdash.game.actors.progress.ARingProgress
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen

// ─────────────────────────────────────────────────────────────────────────────
//  AWave — кільцева хвиля в точці події. Порт wave() з прототипу:
//  радіус росте ease-out, альфа й товщина падають лінійно, життя 0.4 с.
//
//  ЧОМУ ARingProgress, А НЕ НОВИЙ ШЕЙДЕР: хвиля — це та сама обводка кола,
//  що вже малює кільце комбо й щит у ABall, лише з frac = 1 і радіусом, який
//  їде. Новий .glsl додав би третій однаковий шейдер і ще один перемикач у
//  батчі; тут же — той самий актор, ті самі юніформи, той самий білий регіон.
//
//  ЧИСЛА — З ПРОТОТИПУ, ПОДІЛЕНІ НАВПІЛ: у нього поле 640 px, у нас 320 юнітів
//  поля (TO_FIELD = 0.5). wave(10, 64, 0.4, 3) → r 5..32, товщина 1.5 → 0.75.
//
//  Межі актора = КВАД під максимальний радіус (як RING_QUAD у ABall): шейдер
//  малює всередині квада, тож він мусить умістити r 32 + півтовщини + AA.
// ─────────────────────────────────────────────────────────────────────────────
class AWave(screen: AdvancedScreen) : ARingProgress(screen) {

    companion object {
        /** Квад під хвилю: 2×(R_TO + W_FROM/2 + AA 1.5) з запасом. */
        const val QUAD = 72f

        private const val R_FROM = 5f      // 10 engine
        private const val R_TO   = 32f     // 64 engine
        private const val W_FROM = 1.5f    // 3 engine
        private const val W_MIN  = 0.75f   // «+1.5» прототипу — хвиста не тоншає
        private const val LIFE   = 0.4f    // с
        private const val ALPHA  = 0.9f
    }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    /** Скільки хвиля вже живе. LIFE і більше — актор вільний. */
    private var time = LIFE

    init {
        frac = 1f             // повне кільце: заповнення тут ні до чого
        isVisible = false
    }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /** Запустити хвилю з нуля. Позицію ставить викликач ДО цього. */
    fun fire() {
        time = 0f
        isVisible = true
        toFront()
        apply(0f)
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!isVisible) return

        // Реальний час: світ на 30 мс завмирає (RunEngine.hitStopT), а хвиля —
        // ні. Саме через це вона й розходиться із застиглого кадру.
        time += delta
        if (time >= LIFE) { isVisible = false; return }
        apply(time / LIFE)
    }

    // ------------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------------

    /** [f] — прогрес 0..1. Радіус ease-out quad, решта — лінійно, як у прототипі. */
    private fun apply(f: Float) {
        val ease = 1f - (1f - f) * (1f - f)
        radius    = R_FROM + (R_TO - R_FROM) * ease
        thickness = W_FROM * (1f - f) + W_MIN
        color.a   = (1f - f) * ALPHA
    }
}
