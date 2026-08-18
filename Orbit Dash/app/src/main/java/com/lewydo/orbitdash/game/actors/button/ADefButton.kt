package com.lewydo.orbitdash.game.actors.button

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.button.base.AButtonBase
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.ui.ABadgeDot
import com.lewydo.orbitdash.game.actors.ui.ARoundRect
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

// ═════════════════════════════════════════════════════════════════════════════
//  AMenuButton — ЄДИНА кнопка меню: заокруглений фон + текст (+ опційний бейдж).
//
//  ЧОМУ ОДИН КЛАС, А НЕ APlayButton/ADailyButton/AShopButton:
//  кнопки меню відрізняються трьома НЕЗАЛЕЖНИМИ осями — вигляд (Variant),
//  розмір (H_LARGE/H_SMALL + розмір шрифта у стилі) і наявність бейджа.
//  Класи на кожну кнопку були б добутком цих осей, причому кожен відрізнявся б
//  трьома числами. Ще й ім'я привʼязувало б віджет до МІСЦЯ: та сама кнопка в
//  шопі звалася б ABuyButton і була б написана вдруге.
//
//  МЕЖА: окремий клас доречний, коли змінюється СКЛАД дітей, а не параметри.
//  Скін-плитка (кільце + орбіта + ціна) — окремий клас. PLAY — ні.
//
//  ФОН БІЛИЙ, колір — тінт (aBg.color). Тому та сама кнопка стає
//  primary/accent/ghost без нових ассетів і слідує за темою.
//
//    val play = AMenuButton(screen, "PLAY", styleLarge, Variant.PRIMARY)
//    val shop = AMenuButton(screen, "SHOP", styleSmall)          // GHOST
//    daily.isBadgeVisible = true                                 // точка «є що забрати»
// ═════════════════════════════════════════════════════════════════════════════
class ADefButton(
    override val screen: AdvancedScreen,
    text: String,
    styleMsdf: MsdfStyle,
    variant: Variant = Variant.GHOST,
) : AButtonBase(screen) {

    /**
     * ВИГЛЯД, а не ідентичність: PLAY у меню й BUY у шопі — обидва PRIMARY.
     *
     *   PRIMARY — заповнена, колір player (головна дія екрана)
     *   ACCENT  — заповнена, колір gem    (нагорода, покупка за геми)
     *   GHOST   — контурна, біла         (усе інше)
     */
    enum class Variant { PRIMARY, ACCENT, GHOST }

    companion object {
        /** Висоти з макета — щоб екрани не вигадували свої числа. */
        const val H_LARGE = 56f
        const val H_SMALL = 44f

        private const val RADIUS = 18f

        // GHOST: майже прозора заливка + тонка рамка
        private const val GHOST_FILL         = 0.08f
        private const val GHOST_STROKE       = 2f      // ≥ aaWidth, інакше лінія не добере альфи
        private const val GHOST_STROKE_ALPHA = 0.22f

        // Прес: масштаб + притемнення фону
        private const val PRESS_SCALE  = 0.94f
        private const val PRESS_DIM    = 0.80f
        private const val PRESS_TIME   = 0.08f
        private const val UNPRESS_TIME = 0.18f

        // Disabled
        private const val DISABLED_ALPHA = 0.40f
        private const val FADE_TIME      = 0.15f

        // Бейдж
        private const val BADGE_SIZE = 10f
        private const val BADGE_PAD  = 8f
    }

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aBg = ARoundRect(screen)
    val label = AMsdfLabel(text, styleMsdf)

    /** Створюється ЛІНИВО — на кнопках без бейджа не існує як актор. */
    private var aBadge: ABadgeDot? = null

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------

    /** Вигляд. Змінюй коли завгодно — це лише параметри фону. */
    var variant = variant
        set(value) { field = value; applyVariant() }

    /**
     * Точка «є що забрати» у правому верхньому куті (DAILY).
     * Перше true створює актор; далі — просто isVisible.
     */
    var isBadgeVisible = false
        set(value) {
            field = value
            if (value && aBadge == null) createBadge()
            aBadge?.isVisible = value
        }

    /**
     * Перекрити колір фону вручну: для гри без теми або для нестандартної
     * кнопки (напр. деструктивний RESET). null = веде variant + тема.
     * Колір тексту завжди визначає variant.
     */
    var bgOverride: Color? = null

    /**
     * Бекінг-поле, а не делегат до aBg: applyVariant() перезаписує фон
     * повністю і мусить знати, який радіус ВІДНОВИТИ. Делегат цю памʼять
     * втрачав — кастомний радіус зникав при першій зміні варіанта.
     */
    var radius: Float = RADIUS
        set(value) { field = value; aBg.radius = value }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        super.addActorsOnGroup()   // origin + слухач тапів

        addAndFillActor(aBg)
        addAndFillActor(label)

        label.disable()
        label.setAlignment(Align.center)

        applyVariant()   // ініціалізатор поля НЕ проходить через сеттер
        syncTheme()      // щоб перший кадр був уже правильного кольору

        // Якщо бейдж створився ДО потрапляння на сцену, фон ляже поверх нього
        aBadge?.toFront()
    }

    /**
     * Кольори синхронізуються ЩОКАДРУ, бо ThemeManager не перемикає палітру,
     * а лерпає її (~0.35 с) — разовий виклик зловив би проміжний кадр.
     */
    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
    }

    override fun sizeChanged() {
        super.sizeChanged()   // AdvancedGroup ресайзить fillActors
        layoutBadge()
    }

    // ------------------------------------------------------------------------
    // Look
    // ------------------------------------------------------------------------

    /** Геометрія й заливка — те, що НЕ залежить від теми. */
    private fun applyVariant() {
        aBg.radius = radius
        when (variant) {
            Variant.PRIMARY, Variant.ACCENT -> {
                aBg.fillAlpha   = 1f
                aBg.strokeWidth = 0f
            }
            Variant.GHOST -> {
                aBg.fillAlpha   = GHOST_FILL
                aBg.strokeWidth = GHOST_STROKE
                aBg.strokeAlpha = GHOST_STROKE_ALPHA
            }
        }
    }

    private fun syncTheme() {
        val t = ThemeManager.current

        when (variant) {
            Variant.PRIMARY -> { tint(aBg.color, bgOverride ?: t.player); label.setTextColor(t.bg) }
            Variant.ACCENT  -> { tint(aBg.color, bgOverride ?: t.gem);    label.setTextColor(t.bg) }
            Variant.GHOST   -> { tint(aBg.color, bgOverride ?: Color.WHITE); label.setTextColor(Color.WHITE) }
        }

        aBadge?.let { tint(it.color, t.gem) }
    }

    /**
     * RGB із теми, АЛЬФУ не чіпаємо.
     *
     * Критично: press()/disable() анімують саме color.a. Якби тут стояло
     * color.set(src), кожен кадр повертав би альфу в 1 — і притемнення на
     * тапі та згасання в disabled просто не працювали б.
     */
    private fun tint(target: Color, src: Color) {
        target.set(src.r, src.g, src.b, target.a)
    }

    // ------------------------------------------------------------------------
    // Badge
    // ------------------------------------------------------------------------
    private fun createBadge() {
        val badge = ABadgeDot(screen)
        badge.setSize(BADGE_SIZE, BADGE_SIZE)
        badge.touchable = Touchable.disabled

        aBadge = badge
        addActor(badge)
        layoutBadge()
    }

    private fun layoutBadge() {
        aBadge?.setPosition(
            width  - BADGE_SIZE - BADGE_PAD,
            height - BADGE_SIZE - BADGE_PAD,
        )
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------
    fun setText(text: CharSequence) { label.setText(text) }

    // ------------------------------------------------------------------------
    // States
    // ------------------------------------------------------------------------
    override fun press() {
        clearActions(); aBg.clearActions()
        addAction(Actions.scaleTo(PRESS_SCALE, PRESS_SCALE, PRESS_TIME, Interpolation.fastSlow))
        aBg.addAction(Actions.alpha(PRESS_DIM, PRESS_TIME))
    }

    override fun unpress() {
        clearActions(); aBg.clearActions()
        addAction(Actions.scaleTo(1f, 1f, UNPRESS_TIME, Interpolation.fastSlow))
        aBg.addAction(Actions.alpha(1f, UNPRESS_TIME))
    }

    override fun disable() {
        touchable = Touchable.disabled
        fadeParts(DISABLED_ALPHA)
    }

    override fun enable() {
        touchable = Touchable.enabled
        clearActions()
        addAction(Actions.scaleTo(1f, 1f, UNPRESS_TIME, Interpolation.fastSlow))
        fadeParts(1f)
    }

    /** Гасимо частини окремо, а не групу: у групи альфа зайнята прес-анімацією. */
    private fun fadeParts(alpha: Float) {
        aBg.clearActions();   aBg.addAction(Actions.alpha(alpha, FADE_TIME))
        label.clearActions(); label.addAction(Actions.alpha(alpha, FADE_TIME))
        aBadge?.let { it.clearActions(); it.addAction(Actions.alpha(alpha, FADE_TIME)) }
    }

}