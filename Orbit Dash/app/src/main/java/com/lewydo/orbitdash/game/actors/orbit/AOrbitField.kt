package com.lewydo.orbitdash.game.actors.orbit

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
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

        private const val RING_THICKNESS     = 3f
        private const val RING_THICKNESS_ACT = 4f

        /** Лерп автономного режиму. У driven швидкість задає рушій. */
        private const val LAYOUT_LERP = 2.5f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val rings = Array(MAX_RINGS) { AOrbitRing(screen) }

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

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
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
        }

        syncRings()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (rings[0].parent != null) for (i in 0 until MAX_RINGS) applyRingSize(i)
    }

    // ------------------------------------------------------------------------
    // Public API · керування ззовні
    // ------------------------------------------------------------------------

    /**
     * Прийняти геометрію від рушія. [ringR] — діаметри в одиницях, які
     * збігаються з design поля (див. RunEngine: одиниці прототипу = діаметри).
     */
    fun syncFrom(ringR: FloatArray, count: Int, active: Int, r3Alpha: Float) {
        driven = true

        for (i in 0 until MAX_RINGS) diameters[i] = ringR[i]
        ringCount  = count.coerceIn(2, MAX_RINGS)
        activeRing = active
        ring3Alpha = r3Alpha

        if (rings[0].parent != null) for (i in 0 until MAX_RINGS) applyRingSize(i)
    }

    /** Повернути полю самостійність (превʼю, меню). */
    fun releaseDriven() { driven = false }

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
     * Радіус AOrbitRing виводиться з розміру, тому «поставити діаметр» =
     * «задати сторону квадрата»: діаметр + обводка (вона малюється всередину).
     */
    private fun applyRingSize(index: Int) {
        val ring = rings[index]
        val side = diameters[index] + RING_THICKNESS_ACT
        ring.setSizeScaled(side, side)
        ring.setPosition(width / 2f, height / 2f, Align.center)
    }

    private fun syncRings() {
        val theme = ThemeManager.current
        for (i in 0 until MAX_RINGS) {
            val ring   = rings[i]
            val active = i == activeRing

            ring.ringColor = if (active) theme.player else theme.ring
            ring.thickness = (if (active) RING_THICKNESS_ACT else RING_THICKNESS).toActual

            // Третє кільце проявляється, решта завжди видимі
            ring.color.a = if (i < 2) 1f else ring3Alpha
        }
    }

}