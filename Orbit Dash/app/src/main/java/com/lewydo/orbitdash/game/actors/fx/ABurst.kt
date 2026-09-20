package com.lewydo.orbitdash.game.actors.fx

import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import kotlin.math.pow

// ═════════════════════════════════════════════════════════════════════════════
//  ABurst — розліт білих крапок у точці події. Порт burst() з прототипу.
//
//  ЧОМУ НЕ libGDX ParticleEffect: у проєкті він є (ParticleEffectManager,
//  AParticleEffectPool, particles.atlas), але жоден ігровий код його не кличе,
//  і не дарма. Партикл-емітер — це свій атлас (ще 386 КБ і ЩЕ ОДНА текстура в
//  батчі, тобто зайвий draw call поверх msdf), свій .p-файл, який правиться в
//  десктопному редакторі, і купа параметрів, з яких нам потрібні п'ять чисел.
//  Тут п'ять крапок із ТОЇ САМОЇ msdf-текстури, що й іскра: батч не рветься,
//  числа стоять у коді поруч із прототипом, редактор не потрібен.
//
//  ЧОМУ ВЛАСНИЙ draw(), А НЕ П'ЯТЬ АКТОРІВ: рівно та сама причина, що в AComet
//  («актор малює ~14 квадів рівно тоді, коли летить») — п'ять Image'ів довелося
//  б створювати, класти в лейаут і скидати позиції, а тут два масиви float і
//  жодної алокації в кадрі.
//
//  ЧИСЛА — З ПРОТОТИПУ, ПОДІЛЕНІ НАВПІЛ (поле 640 px → 320 юнітів поля):
//  швидкість 60..420 → 30..210, розмір 3 + 5·life → 1.5 + 2.5·life радіуса.
//  Кут — рівномірно 0..360: розліт НЕ залежить від напряму руху, бо іскра
//  «лопається» на місці, а не бризкає за м'ячем.
// ═════════════════════════════════════════════════════════════════════════════
class ABurst(override val screen: AdvancedScreen) : AdvancedGroup() {

    companion object {
        /** Стеля крапок в одному бурсті. Іскрі треба 5, решта — запас під інші події. */
        private const val MAX = 8

        private const val SPEED_MIN = 30f     // 60 engine
        private const val SPEED_MAX = 210f    // 420 engine
        private const val LIFE_MIN  = 0.4f
        private const val LIFE_MAX  = 0.9f

        /** Діаметр крапки = DOT_BASE + DOT_LIFE·life: гасне і стискається разом. */
        private const val DOT_BASE = 3f       // 6 engine
        private const val DOT_LIFE = 5f       // 10 engine

        /** Ореол навколо крапки — та сама пропорція, що в ASpark (18 на 6). */
        private const val GLOW_K = 3f
        private const val GLOW_A = 0.55f

        /**
         * Тертя прототипу — 0.98 ЗА КАДР, тобто прив'язане до FPS. Переводимо
         * в за-секунду через його ж 60 FPS: на 60 виходить один в один, на 120
         * крапки більше не летять удвічі далі.
         */
        private const val DRAG_PER_FRAME = 0.98f
    }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    //
    //  Масиви, а не список об'єктів: бурст спалахує в найгарячіший момент
    //  кадру, і алокація тут — те саме GC-смикання, від якого пули.
    //
    private val px   = FloatArray(MAX)
    private val py   = FloatArray(MAX)
    private val vx   = FloatArray(MAX)
    private val vy   = FloatArray(MAX)
    private val life = FloatArray(MAX)

    private var count = 0

    /** Регіони беремо ЖИВІ, не копії: після втрати GL-контексту VfxTexture
     *  перестворює текстуру під тим самим region-об'єктом (див. docs/vfx.md). */
    private val dot  get() = gdxGame.assetsMsdf.circle
    private val glow get() = gdxGame.assetsMsdf.glow

    init { isVisible = false }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    /** Дітей немає — усе малює draw(). */
    override fun addActorsOnGroup() = Unit

    /** Розсипати [n] крапок із центру актора. Позицію ставить викликач ДО цього. */
    fun fire(n: Int = 5) {
        count = n.coerceIn(1, MAX)
        for (i in 0 until count) {
            val a  = MathUtils.random(0f, MathUtils.PI2)
            val sp = MathUtils.random(SPEED_MIN, SPEED_MAX)
            px[i] = 0f
            py[i] = 0f
            vx[i] = MathUtils.cos(a) * sp
            vy[i] = MathUtils.sin(a) * sp
            life[i] = MathUtils.random(LIFE_MIN, LIFE_MAX)
        }
        isVisible = true
        toFront()
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (!isVisible) return

        // Реальний час, як і хвиля: мікрофриз зупиняє світ, не ефекти
        val drag = DRAG_PER_FRAME.pow(delta * 60f)
        var alive = 0

        for (i in 0 until count) {
            if (life[i] <= 0f) continue
            life[i] -= delta
            if (life[i] <= 0f) continue

            px[i] += vx[i] * delta
            py[i] += vy[i] * delta
            vx[i] *= drag
            vy[i] *= drag
            alive++
        }

        if (alive == 0) { count = 0; isVisible = false }
    }

    // ------------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------------
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (batch == null || count == 0) return

        val a = color.a * parentAlpha
        if (a <= 0.004f) return

        // Центр актора — точка події; крапки летять за його межі, як glow у
        // решті об'єктів поля. Координати — батьківські, тому x/y додаємо.
        val cx = x + width * 0.5f
        val cy = y + height * 0.5f

        val prev = batch.packedColor
        // Адитивне змішування — «lighter» прототипу: крапки складаються зі
        // світінням шипа, а не перекривають його. Той самий прийом, що в AComet.
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

        for (i in 0 until count) {
            val l = life[i]
            if (l <= 0f) continue

            val d  = DOT_BASE + DOT_LIFE * l
            val al = a * l.coerceAtMost(1f)
            val sx = cx + px[i]
            val sy = cy + py[i]

            val g = d * GLOW_K
            batch.setColor(1f, 1f, 1f, al * GLOW_A)
            batch.draw(glow, sx - g * 0.5f, sy - g * 0.5f, g, g)

            batch.setColor(1f, 1f, 1f, al)
            batch.draw(dot, sx - d * 0.5f, sy - d * 0.5f, d, d)
        }

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.packedColor = prev
    }
}
