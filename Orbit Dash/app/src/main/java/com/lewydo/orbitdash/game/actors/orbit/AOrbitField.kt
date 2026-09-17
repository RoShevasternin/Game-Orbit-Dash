package com.lewydo.orbitdash.game.actors.orbit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

// ═════════════════════════════════════════════════════════════════════════════
//  AOrbitField — ігрове поле: кільця + посадка об'єктів на них.
//
//  ОДНА СИСТЕМА ЧИСЕЛ. sizeScaler з DESIGN_W = 320, тобто дизайн-розмір поля
//  дорівнює ДІАМЕТРУ ЗОВНІШНЬОЇ ОРБІТИ — поле обрізане рівно по ній. Тому всі
//  числа в цьому файлі ті самі, що у Figma-мокапі (360×800).
//
//  РОЗКЛАДКИ ЗАДАНІ В ДІАМЕТРАХ, бо саме так вони підписані в дизайні.
//  На радіус ділимо в одному місці — там, де цього вимагає тригонометрія.
//
//  ДВА РЕЖИМИ:
//   • автономний — поле саме лерпає кільця (меню, превʼю, дебаг);
//   • ВЕДЕНИЙ (driven) — радіуси приходять з RunEngine через syncFrom().
//     Під час рану джерело правди ОДНЕ: колізії рахуються по тих самих
//     радіусах, що малюються. Два незалежні лерпи розійшлись би фазами —
//     гравець бачив би кільце в одному місці, а вмирав у іншому.
//
//  ЩО ПОЛЕ ЗНАЄ: геометрію. ЩО НЕ ЗНАЄ: правил гри.
// ═════════════════════════════════════════════════════════════════════════════
class AOrbitField(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, DESIGN_W)

    companion object {
        /** Дизайн-розмір поля = діаметр зовнішньої орбіти. */
        const val DESIGN_W = 320f

        /**
         * Ореол під кільцями: диск кольору м'яча на HALO_GAP всередині орбіти
         * м'яча (по радіусу): 190 → 170, 320 → 300, у трьох кільцях 130 → 110.
         */
        private const val HALO_GAP   = 10f
        private const val HALO_ALPHA = 0.05f
        /** Без м'яча (автономний режим) ореол лерпає до активного кільця — як радіус м'яча в рушії. */
        private const val HALO_LERP  = 14f

        const val MAX_RINGS = 3

        /** Діаметр зовнішньої орбіти. Він же — парковка для неактивного кільця. */
        private const val D_OUTER = 320f

        /**
         * ДІАМЕТРИ кілець (design), зсередини назовні.
         *
         * У LAYOUT_2 третє паркується на D_OUTER — рівно під зовнішнім. Тоді
         * активація ORBIT III читається як «зовнішня орбіта розділилась»:
         * третє проявляється на місці, а перші два стискаються всередину.
         */
        private val LAYOUT_2 = floatArrayOf(190f, D_OUTER, D_OUTER)
        private val LAYOUT_3 = floatArrayOf(130f, 225f,    D_OUTER)

        // Figma: ring-inner 3, ring-outer-active 4.5
        private const val RING_THICKNESS     = 3f
        private const val RING_THICKNESS_ACT = 4.5f

        /**
         * Активна орбіта = кільце теми, яскравіше. ×1.9 — саме так у Figma NEON:
         * 2B3060 → 525AB6 покомпонентно. Для решти тем виводиться тим самим
         * множником, окремого кольору в палітрі не треба.
         */
        private const val ACTIVE_BRIGHTEN = 1.9f

        /**
         * Запас квада кільця назовні від осі орбіти — під glow і пунктир
         * активної (≥ AOrbitRing.ACTIVE_REACH). Тому поле 320 малює до
         * 320 + 2·9 = 338 — рівно як фрейм OrbitField у Figma.
         */
        private const val RING_PAD = 14f

        /**
         * Пунктир активної орбіти їде за м'ячем: 0.9 px прототипу на градус
         * (st.dashOff -= v·wdt·0.9), px прототипу = 2 design поля → 0.45.
         */
        private const val DASH_FOLLOW = 0.45f
        /** Без м'яча (меню, превʼю) — повільно сам, design-юнітів дуги за секунду. */
        private const val DASH_IDLE_SPEED = -25f

        /** Лерп автономного режиму. У driven швидкість задає рушій. */
        private const val LAYOUT_LERP = 2.5f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aHaloImg = AMsdfImage(screen, gdxGame.assetsMsdf.circle_msdf).apply { color.a = HALO_ALPHA }
    private val rings    = Array(MAX_RINGS) { AOrbitRing(screen) }

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------
    private val diameters = LAYOUT_2.copyOf()
    private var target    = LAYOUT_2

    /** true — радіуси приходять ззовні (RunEngine), власний лерп мовчить. */
    private var driven = false

    var ringCount = 2
        private set

    var activeRing = 0
        set(value) { field = value.coerceIn(0, MAX_RINGS - 1) }

    /** Проявлення третього кільця 0→1. У driven приходить з рушія. */
    var ring3Alpha = 0f

    /**
     * Зсув пунктиру за ЦЕЙ кадр, design. syncRings() віддає його кільцю й
     * обнуляє — сума за ран ніде не накопичується, фазу тримає кільце в 0..1.
     */
    private var dashStep = 0f
    /** Кут м'яча з минулого syncFrom — щоб пунктир їхав за ним, а не за часом. */
    private var lastBallAngle: Float? = null

    /** Діаметр ореолу, design. У driven — від орбіти м'яча, інакше лерп до активного кільця. */
    private var haloD = LAYOUT_2[0] - 2f * HALO_GAP

    /** Колір активної орбіти: кільце теми × ACTIVE_BRIGHTEN. Один об'єкт, без алокацій у кадрі. */
    private val activeRingColor = Color()

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addHaloImg()

        for (i in 0 until MAX_RINGS) {
            addActor(rings[i])
            applyRingSize(i)
        }
        syncRings()
    }

    override fun act(delta: Float) {
        super.act(delta)

        if (!driven) {
            for (i in 0 until MAX_RINGS) {
                val diff = target[i] - diameters[i]
                if (MathUtils.isZero(diff, 0.1f)) {
                    if (diameters[i] != target[i]) { diameters[i] = target[i]; applyRingSize(i) }
                } else {
                    diameters[i] += diff * MathUtils.clamp(LAYOUT_LERP * delta, 0f, 1f)
                    applyRingSize(i)
                }
            }
            ring3Alpha = if (ringCount == 3) MathUtils.clamp(ring3Alpha + 1.5f * delta, 0f, 1f) else 0f
            dashStep += DASH_IDLE_SPEED * delta

            val haloTarget = diameters[activeRing] - 2f * HALO_GAP
            haloD += (haloTarget - haloD) * MathUtils.clamp(HALO_LERP * delta, 0f, 1f)
            applyHaloSize()
        }

        syncRings()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (rings[0].parent != null) {
            applyHaloSize()
            for (i in 0 until MAX_RINGS) applyRingSize(i)
        }
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addHaloImg() {
        addActor(aHaloImg)
        applyHaloSize()
    }

    // ------------------------------------------------------------------------
    // Public API · керування ззовні
    // ------------------------------------------------------------------------

    /**
     * Прийняти геометрію від рушія. [ringR] — діаметри в одиницях, які
     * збігаються з design поля (див. RunEngine: одиниці прототипу = діаметри).
     * [ballD] — діаметр орбіти м'яча в тих самих одиницях (engine.radius):
     * ореол іде за м'ячем, а не за кільцем — разом зі слоу-мо й паузою.
     */
    fun syncFrom(ringR: FloatArray, count: Int, active: Int, r3Alpha: Float, ballAngle: Float, ballD: Float) {
        driven = true

        for (i in 0 until MAX_RINGS) diameters[i] = ringR[i]
        ringCount  = count.coerceIn(2, MAX_RINGS)
        activeRing = active
        ring3Alpha = r3Alpha

        // Пунктир їде за м'ячем: приріст кута → зсув дуги. Через різницю, бо
        // кут нормалізований 0..360 і на стику стрибає
        lastBallAngle?.let { prev ->
            val delta = ((ballAngle - prev) % 360f + 540f) % 360f - 180f
            dashStep += DASH_FOLLOW * delta
        }
        lastBallAngle = ballAngle

        haloD = ballD - 2f * HALO_GAP

        if (rings[0].parent != null) {
            applyHaloSize()
            for (i in 0 until MAX_RINGS) applyRingSize(i)
        }
    }

    /** Повернути полю самостійність (превʼю, меню). */
    fun releaseDriven() { driven = false; lastBallAngle = null }

    // ------------------------------------------------------------------------
    // Public API · геометрія
    // ------------------------------------------------------------------------

    fun diameterOf(index: Int): Float = diameters[index.coerceIn(0, MAX_RINGS - 1)]

    fun radiusOf(index: Int): Float = diameterOf(index) * 0.5f

    /**
     * Посадити актора на кільце під кутом.
     * angleDeg: 0 = праворуч, 90 = вгору, проти годинникової (математична).
     */
    fun positionOn(actor: Actor, ringIndex: Int, angleDeg: Float) =
        positionAt(actor, radiusOf(ringIndex), angleDeg)

    /**
     * Довільний радіус — для переходів між кільцями й сутностей рушія.
     * Позиціонуємо за ЦЕНТРОМ актора: у об'єктів різні розміри.
     */
    fun positionAt(actor: Actor, radiusDesign: Float, angleDeg: Float) {
        val rad = angleDeg * MathUtils.degreesToRadians
        val r   = radiusDesign.toActual
        actor.setPosition(
            width  / 2f + MathUtils.cos(rad) * r,
            height / 2f + MathUtils.sin(rad) * r,
            Align.center,
        )
    }

    fun centerX() = width / 2f
    fun centerY() = height / 2f

    // ------------------------------------------------------------------------
    // Public API · розкладка (автономний режим)
    // ------------------------------------------------------------------------
    fun setRingCount(count: Int) {
        val c = count.coerceIn(2, MAX_RINGS)
        if (c == ringCount) return
        ringCount = c
        target    = if (c >= 3) LAYOUT_3 else LAYOUT_2
    }

    fun setRingCountInstant(count: Int) {
        setRingCount(count)
        target.copyInto(diameters)
        if (rings[0].parent != null) for (i in 0 until MAX_RINGS) applyRingSize(i)
    }

    // ------------------------------------------------------------------------
    // Rings
    // ------------------------------------------------------------------------

    /**
     * Радіус AOrbitRing виводиться з розміру: «поставити діаметр» = «задати
     * сторону квадрата» = діаметр + 2·RING_PAD, а вісь орбіти — рівно на
     * RING_PAD від краю (outerPad). Так glow і пунктир мають куди лягти.
     */
    private fun applyRingSize(index: Int) {
        val ring = rings[index]
        val side = diameters[index] + 2f * RING_PAD
        ring.setSizeScaled(side, side)
        ring.outerPad = RING_PAD.toActual
        ring.setPosition(width / 2f, height / 2f, Align.center)
    }

    /** Ореол — той самий підхід, що й кільця: розмір живий, тому не через констрейнт. */
    private fun applyHaloSize() {
        aHaloImg.setSizeScaled(haloD, haloD)
        aHaloImg.setPosition(width / 2f, height / 2f, Align.center)
    }

    private fun syncRings() {
        val theme = ThemeManager.current
        aHaloImg.setColorRGB(theme.player)
        activeRingColor.set(theme.ring).mul(ACTIVE_BRIGHTEN).clamp()

        for (i in 0 until MAX_RINGS) {
            val ring   = rings[i]
            val active = i == activeRing

            ring.ringColor = if (active) activeRingColor else theme.ring
            ring.thickness = (if (active) RING_THICKNESS_ACT else RING_THICKNESS).toActual

            if (active) {
                ring.setActive(theme.player, sizeScaler.factor)
                ring.advanceDash(dashStep.toActual)
            } else ring.setInactive()

            // Третє кільце проявляється, решта завжди видимі
            ring.color.a = if (i < 2) 1f else ring3Alpha
        }
        dashStep = 0f
    }

}