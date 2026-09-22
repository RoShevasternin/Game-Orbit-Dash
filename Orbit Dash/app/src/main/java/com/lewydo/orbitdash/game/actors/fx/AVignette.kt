package com.lewydo.orbitdash.game.actors.fx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.effects.BoostVignetteEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.VignetteEffect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  AVIGNETTE — периферійний сигнал стану. Порт віньєтки з прототипу
//  (prototype/orbit-dash-tutorial.jsx, рядки 1246–1331), числа один в один.
//
//  НАВІЩО. Гравець дивиться на м'яч, а не на HUD. Смуга буста в панелі каже
//  «скільки лишилось», але не кричить «зараз діє МАГНІТ». Край екрана кричить,
//  не забираючи жодного пікселя з поля.
//
//  ТРИ ШАРИ:
//    aBase — чорний градієнт, лежить ЗАВЖДИ: глибина картинки, кути гаснуть.
//            Запечена текстура: профіль сталий, міняється лише тінт.
//    aEdge — кольоровий край активного буста І ЙОГО КІЛЬЦЯ (MAGNET стягує,
//            SLOW-MO розганяє назовні) — один живий шейдер на одному кваді.
//    aFx   — іскорки GEM x2 по периметру, власний draw() як в ABurst.
//
//  ЧОМУ КІЛЬЦЯ В ШЕЙДЕРІ КРАЮ, А НЕ ОКРЕМО. Край і так повноекранний квад
//  щокадру, поки діє буст. Три кільця в тому ж проході — це кілька операцій на
//  піксель, який уже обробляється: нуль draw call і нуль зайвої заливки.
//  Заміряно 21.09: малювання сцени 4,5–5,9 мс із магнітом при 4,0–5,2 без
//  бустів. Три окремі шейдерні квади коштували б три екрани заливки, а
//  ShapeDrawer — 2 мс CPU на вершини й многокутник замість кола (64 сторони
//  на радіусі ~1000 px). Аналітичне кільце кругле на будь-якому радіусі.
//
//  ДВА ЦЕНТРИ, І ЦЕ НАВМИСНО:
//    градієнти  — від центру ЕКРАНА: рамка має бути рівна з усіх боків (у
//                 прототипі поле майже посеред кадру 9:16, у нас екран 9:20.6
//                 і поле високо — з центром на полі низ горів, верх ні);
//    кільця     — від центру ОРБІТИ: вони показують, куди тягне магніт, а тягне
//                 він до поля. Тому актор слідкує за полем: [follow].
//
//  РОЗМІР КВАДА. Шейдер дає одиницю на вписаному колі, тож квад мусить бути
//  рівно 2 × зовнішній радіус, а радіус — від центру до НАЙДАЛЬШОГО КУТА екрана
//  (у прототипі 800 px при 752 до кута). Квад вилазить за екран — це нормально,
//  растеризатор відсікає зайве. Кільця живуть у тому ж кваді: їхні радіуси й
//  центр передаються в частках цього ж зовнішнього радіуса.
// ─────────────────────────────────────────────────────────────────────────────
class AVignette(override val screen: AdvancedScreen) : AdvancedGroup() {

    companion object {
        /**
         * Зовнішній радіус градієнта у частках відстані до НАЙДАЛЬШОГО КУТА
         * екрана. У прототипі 800 px при 752 до кута — 1.064.
         *
         * Саме до кута, а не півдіагональ: інакше на високому екрані градієнт
         * не дотягується до нижніх кутів і знизу лишається темна смуга
         * (спіймано на пристрої 21.09).
         */
        private const val ROUT_K = 1.064f

        // ── профілі: частка радіуса без градієнта + альфа у вузлі 0.55 ──
        private const val BASE_INNER = 0.376f   // 300 px прототипу
        private const val BASE_MID_A = 0.55f    // = пряма лінія, як у нього
        private const val EDGE_INNER = 0.413f   // 330 px прототипу
        private const val EDGE_MID_A = 0.30f    // довго майже нічого, потім різко

        /** Альфа постійної чорної віньєтки. */
        const val BASE_A = 0.42f

        // ── сила кольорового краю на кожен буст ──
        private const val A_MAGNET = 0.34f
        private const val A_FRENZY = 0.26f
        private const val A_SLOW   = 0.34f

        // ── огинаюча: I = kin · punch · warn · kout ──
        private const val KIN       = 0.12f   // різкий, але не миттєвий вхід
        private const val PUNCH     = 0.8f    // +80 % у перші PUNCH_T
        private const val PUNCH_T   = 0.45f
        private const val WARN_FROM = 1.6f    // за скільки до кінця починає блимати
        private const val FADE_OUT  = 0.25f   // м'яко гасне в останні

        /** Власний характер: GEM x2 мерехтить швидко, SLOW-MO важко дихає. */
        private const val FRENZY_HZ = 7f
        private const val SLOW_HZ   = 1.6f

        // ── MAGNET: кільця стягуються з-за краю до орбіти ──
        private const val MAG_RINGS   = 3
        /** Звідки прилітають — частка відстані до кута екрана (820 px / 752). */
        private const val MAG_R_FAR   = 1.090f
        /**
         * Куди прилітають — частка радіуса ПОЛЯ (420 px при полі 320 у
         * прототипі). Саме від поля, а не від екрана: кільце має гаснути на
         * орбіті, інакше воно зупиняється далеко від неї й здається, що летить
         * повз (спіймано на пристрої 21.09).
         */
        private const val MAG_R_NEAR  = 1.31f
        private const val MAG_SPEED   = 0.55f    // обертів фази за секунду
        private const val MAG_A       = 0.24f
        /** Товщина — в ЮНІТАХ: 8..30 px прототипу при 2 px на юніт. Через частку
         *  радіуса на високому екрані виходили вдвічі товщі смуги. */
        private const val MAG_TH_MIN  = 4f
        private const val MAG_TH_MAX  = 15f

        // ── SLOW-MO: хвилі розходяться від орбіти назовні ──
        private const val SLOW_WAVES  = 2
        private const val SLOW_R_NEAR = 1.03f    // 330 px при полі 320 — частка ПОЛЯ
        private const val SLOW_R_FAR  = 1.077f   // 810 px — частка кута екрана
        private const val SLOW_SPEED  = 0.28f
        private const val SLOW_A      = 0.22f
        private const val SLOW_TH     = 1.5f     // 3 px прототипу

        // ── GEM x2: іскорки спалахують по периметру екрана ──
        private const val SPARK_N     = 16
        private const val SPARK_MARGIN = 13f     // 26 px прототипу на ширині 720
        private const val SPARK_SPEED  = 1.2f    // циклів за секунду
        /** Ореол більший, промені коротші: хрестики самі по собі виглядали
         *  як білі палички, а світіння читається як іскра. */
        private const val SPARK_GLOW   = 24f
        private const val SPARK_GLOW_A = 0.8f
        private const val SPARK_RAY    = 1.4f    // промінь у спокої
        private const val SPARK_RAY_A  = 1.8f    // + на піку
        private const val SPARK_LINE   = 0.8f    // товщина променя

        /** Підсвітка = колір буста, розбавлений білим. У прототипі — #FFF6D0. */
        private const val HILIGHT = 0.7f

        /** Розмір запеченої «фігури» — КОНСТАНТА: інакше кеш плодить текстури. */
        private const val TEX     = 256f
        private const val DENSITY = 0.5f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val baseTex = VfxTexture(TEX, TEX, shape = VignetteEffect(BASE_INNER, BASE_MID_A), density = DENSITY)

    private val aBase = Image(baseTex.region).apply { color = Color(0f, 0f, 0f, BASE_A) }

    /** Край + кільця — один живий шейдер на одному повноекранному кваді. */
    private val fxEdge = BoostVignetteEffect(EDGE_INNER, EDGE_MID_A)
    private val aEdge  = VfxImage(screen, screen.drawerUtil.getRegion(), fxEdge)

    private val aFx = Fx()

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    /** Реальний час: блимання й дихання не сповільнюються разом зі світом. */
    private var time = 0f

    /** Що діє зараз і наскільки сильно — одне джерело для всіх трьох шарів. */
    private var boost: RunEngine.Boost? = null
    private var power = 0f

    /** Центр градієнтів (екран) і зовнішній радіус, у локальних координатах. */
    private var cx = 0f
    private var cy = 0f
    private var rOut = 0f

    /** Центр кілець — центр орбіти. Поки поля немає, збігається з [cx]/[cy]. */
    private var ringCx = 0f
    private var ringCy = 0f

    /** Радіус поля: внутрішній край кілець міряється від нього, не від екрана. */
    private var fieldR = 0f

    /** За чим центрувати кільця. Поле; null — по центру екрана. */
    private var target: Actor? = null

    /** Робочий вектор для переводу координат — без алокацій щокадру. */
    private val tmp = Vector2()

    // Кільця: радіус, товщина й альфа на кожне. Масиви, а не актори —
    // щоб малювати їх одним проходом у Fx, без жодної алокації в кадрі.
    private val ringR  = FloatArray(MAG_RINGS)
    private val ringTh = FloatArray(MAG_RINGS)
    private val ringA  = FloatArray(MAG_RINGS)
    private var ringsIn = true

    /** DEBUG: постійну віньєтку можна прибрати кнопкою й подивитись без неї. */
    var baseOn: Boolean
        get() = aBase.isVisible
        set(value) { aBase.isVisible = value }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addActor(aBase)
        addActor(aEdge)
        addActor(aFx)
        aEdge.isVisible = false
        applyQuad()
    }

    /** Центрувати КІЛЬЦЯ за цим актором (полем). Градієнти лишаються по екрану. */
    fun follow(actor: Actor) { target = actor }

    override fun act(delta: Float) {
        super.act(delta)
        time += delta
        applyQuad()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        applyQuad()
    }

    /**
     * Квад під обидва градієнти: сторона 2 × ROUT_K × відстань до найдальшого
     * кута ЕКРАНА. Група живе в корені, піднятому над банером, тож міряємо на
     * сцені, а ставимо в локальних координатах.
     */
    private fun applyQuad() {
        val sw = screen.worldWidth
        val sh = screen.worldHeight
        val sx = sw * 0.5f
        val sy = sh * 0.5f

        val maxR = maxOf(
            hypot(sx, sy), hypot(sw - sx, sy),
            hypot(sx, sh - sy), hypot(sw - sx, sh - sy),
        )
        rOut = ROUT_K * maxR
        val side = 2f * rOut

        stageToLocalCoordinates(tmp.set(sx, sy))
        cx = tmp.x
        cy = tmp.y

        aBase.setSize(side, side); aBase.setPosition(cx, cy, Align.center)
        aEdge.setSize(side, side); aEdge.setPosition(cx, cy, Align.center)

        // Кільця — навколо орбіти: поле СУСІД, не батько, тому через сцену
        val t = target
        if (t != null && t.stage != null && stage != null) {
            t.localToStageCoordinates(tmp.set(t.width * 0.5f, t.height * 0.5f))
            stageToLocalCoordinates(tmp)
            ringCx = tmp.x
            ringCy = tmp.y
            fieldR = t.width * 0.5f
        } else {
            ringCx = cx
            ringCy = cy
            fieldR = rOut * 0.33f
        }

        aFx.setBounds(0f, 0f, width, height)
    }

    // ------------------------------------------------------------------------
    // Sync · стан із рушія
    // ------------------------------------------------------------------------

    /**
     * Кличеться щокадру з HUD. Джерело те саме, що в APanelBooster:
     * [RunEngine.activeBoost] і [RunEngine.boostFrac] — окремого стану немає.
     */
    fun syncFrom(engine: RunEngine) {
        val b = engine.activeBoost
        boost = b
        if (b == null) {
            power = 0f
            aEdge.isVisible = false
            return
        }

        val left = engine.boostFrac * engine.boostTot          // скільки лишилось, с
        val el   = engine.boostTot - left                      // скільки вже діє, с

        val kin   = min(1f, el / KIN)
        val punch = 1f + PUNCH * (1f - min(1f, el / PUNCH_T))
        val warn  = if (left < WARN_FROM) {
            // чим ближче кінець, тим частіше блимає
            0.35f + 0.65f * abs(cos(time * (5f + (WARN_FROM - left) * 9f)))
        } else 1f
        val kout  = min(1f, left / FADE_OUT)
        power = kin * punch * warn * kout

        val own = when (b) {
            RunEngine.Boost.MAGNET -> A_MAGNET
            RunEngine.Boost.FRENZY -> A_FRENZY * (0.82f + 0.18f * sin(time * FRENZY_HZ))
            RunEngine.Boost.SLOW   -> A_SLOW   * (0.8f  + 0.2f  * sin(time * SLOW_HZ))
            else                   -> 0f
        }

        aEdge.isVisible = true
        aEdge.setColorRGB(b.info.color)
        aEdge.color.a = power.coerceIn(0f, 1f)   // спільна огинаюча — на все
        fxEdge.edgeA  = own                       // сила саме краю

        syncRings(b)
        pushRings(b)
    }

    /**
     * MAGNET: три кільця йдуть із-за краю ДО орбіти й товщають на підльоті —
     * видно, як тягне. SLOW-MO: дві тонкі хвилі, навпаки, розходяться назовні,
     * і повільно — час під бустом і так сповільнений.
     */
    private fun syncRings(b: RunEngine.Boost) {
        val inward = b == RunEngine.Boost.MAGNET
        val n = when (b) {
            RunEngine.Boost.MAGNET -> MAG_RINGS
            RunEngine.Boost.SLOW   -> SLOW_WAVES
            else                   -> 0
        }
        ringsIn = inward
        if (n == 0) {
            for (i in ringA.indices) ringA[i] = 0f
            return
        }

        val speed = if (inward) MAG_SPEED else SLOW_SPEED
        for (i in ringA.indices) {
            if (i >= n) { ringA[i] = 0f; continue }

            val ph = (time * speed + i.toFloat() / n) % 1f
            // power сюди не входить: огинаюча вже в тінті актора, і в шейдері
            // вона множить край і кільця однаково
            ringA[i] = if (inward) {
                sin(ph * MathUtils.PI) * MAG_A
            } else {
                (1f - ph) * sin(ph * MathUtils.PI) * SLOW_A
            }.coerceIn(0f, 1f)

            // Далекий край — від кута екрана, ближній — від поля
            val far  = rOut * MAG_R_FAR
            val near = fieldR * MAG_R_NEAR
            ringR[i] = if (inward) {
                far - ph * (far - near)
            } else {
                val from = fieldR * SLOW_R_NEAR
                from + ph * (rOut * SLOW_R_FAR - from)
            }
            ringTh[i] = if (inward) {
                MAG_TH_MIN + (MAG_TH_MAX - MAG_TH_MIN) * (1f - ph)
            } else {
                SLOW_TH
            }
        }
    }

    /**
     * Кільця — у шейдер. Усе в частках зовнішнього радіуса; центр — зсув від
     * центру квада, y перевернутий, бо в UV він іде вниз.
     */
    private fun pushRings(b: RunEngine.Boost) {
        val inv = 1f / rOut
        fxEdge.ringCenter.set((ringCx - cx) * inv, -(ringCy - cy) * inv)
        fxEdge.aa = 1.5f * inv
        val rings = arrayOf(fxEdge.ring0, fxEdge.ring1, fxEdge.ring2)
        for (i in rings.indices) {
            rings[i].set(ringR[i] * inv, ringTh[i] * inv, ringA[i])
        }
        val c = b.info.color
        // Гребінь хвилі SLOW-MO світліший за край, як у прототипі
        if (ringsIn) fxEdge.ringTint.set(c.r, c.g, c.b, 1f)
        else         fxEdge.ringTint.set(hi(c.r), hi(c.g), hi(c.b), 1f)
    }

    /** Канал кольору, розбавлений білим на HILIGHT. */
    private fun hi(c: Float) = c + (1f - c) * HILIGHT

    /** Новий ран — кольорового краю немає, поки не підібрано буст. */
    fun reset() {
        boost = null
        power = 0f
        aEdge.isVisible = false
        time = 0f
    }

    override fun dispose() {
        super.dispose()
        baseTex.dispose()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fx — третій шар: характер буста. Малює САМ, адитивно, без акторів.
    // ─────────────────────────────────────────────────────────────────────────
    private inner class Fx : Actor() {

        /** Регіон беремо ЖИВИЙ: після втрати контексту VfxTexture перестворює
         *  текстуру під тим самим об'єктом (див. docs/vfx.md). */
        private val glow get() = gdxGame.assetsMsdf.glow

        /**
         * Білий 4×4 — ОДИН РАЗ у поле, не в getter. ShapeDrawerUtil.getRegion()
         * щоразу створює нову Pixmap і нову Texture; з getter'ом це було 32
         * текстури на кадр і 20 FPS на GEM x2 (спіймано на пристрої 21.09).
         */
        private val white = screen.drawerUtil.getRegion()

        override fun draw(batch: Batch?, parentAlpha: Float) {
            if (batch == null) return

            val b = boost ?: return
            if (power <= 0.004f) return

            val a = color.a * parentAlpha
            if (a <= 0.004f) return

            val prev = batch.packedColor
            // «lighter» прототипу: ефект складається з тим, що під ним
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)

            if (b == RunEngine.Boost.FRENZY) sparks(batch, b.info.color, a)

            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            batch.packedColor = prev
        }

        /**
         * GEM x2: іскорки спалахують у точках периметра екрана. Точка на цикл
         * псевдовипадкова від його номера — без random у кадрі, тож та сама
         * секунда дає ту саму картинку, як у прототипі.
         */
        private fun sparks(batch: Batch, col: Color, a: Float) {
            val m   = SPARK_MARGIN
            val w   = width  - 2f * m
            val h   = height - 2f * m
            val per = 2f * (w + h)

            for (k in 0 until SPARK_N) {
                val cyc = time * SPARK_SPEED + k * 0.618f          // золотий перетин — рівні фази
                val n   = MathUtils.floor(cyc)
                val f   = cyc - n
                val amp = sin(f * MathUtils.PI).let { it * it } * power
                if (amp < 0.02f) continue

                val rnd = abs(sin((n + 1) * 78.233f + k * 12.9898f) * 43758.5453f) % 1f
                var d = rnd * per
                val sx: Float
                val sy: Float
                when {
                    d < w         -> { sx = m + d;             sy = m }
                    d - w < h     -> { d -= w;          sx = m + w;     sy = m + d }
                    d - w - h < w -> { d -= w + h;      sx = m + w - d; sy = m + h }
                    else          -> { d -= 2f * w + h; sx = m;         sy = m + h - d }
                }

                val g = SPARK_GLOW
                batch.setColor(col.r, col.g, col.b, (amp * SPARK_GLOW_A * a).coerceAtMost(1f))
                batch.draw(glow, x + sx - g * 0.5f, y + sy - g * 0.5f, g, g)

                // чотирипроменева зірочка — два тонкі квади білим регіоном
                val ray = SPARK_RAY + SPARK_RAY_A * amp
                batch.setColor(hi(col.r), hi(col.g), hi(col.b), (amp * a).coerceAtMost(1f))
                batch.draw(white, x + sx - ray, y + sy - SPARK_LINE * 0.5f, ray * 2f, SPARK_LINE)
                batch.draw(white, x + sx - SPARK_LINE * 0.5f, y + sy - ray, SPARK_LINE, ray * 2f)
            }
        }
    }
}
