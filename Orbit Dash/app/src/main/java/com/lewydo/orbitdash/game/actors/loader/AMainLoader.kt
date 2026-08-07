package com.lewydo.orbitdash.game.actors.loader

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.BuildConfig
import com.lewydo.orbitdash.game.actors.label.AMsdfLabel
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AAnchor
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AAnchorOf
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.orbit.AOrbitEmblem
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.TITLE_1
import com.lewydo.orbitdash.game.utils.TITLE_2
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.animHide
import com.lewydo.orbitdash.game.utils.actor.animToTarget
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.font.msdf.MsdfStyle
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager
import java.util.concurrent.atomic.AtomicBoolean

// ═════════════════════════════════════════════════════════════════════════════
//  AMainLoader — брендблок із ДВОМА СТАНАМИ.
//
//    State.LOADER — великі орбіти й титули по центру, прогрес, брендинг.
//    State.MENU   — орбіти й титули на якорях, прогрес і брендинг відсутні.
//
//    LoaderScreen: AMainLoader(this)
//    MenuScreen:   AMainLoader(this, AMainLoader.State.MENU)
//
//  ПРИНЦИП ЗАМІСТЬ ПЕРЕВІРОК: кожен стан — це власний список акторів.
//  Ми не додаємо все підряд і не глушимо зайве прапорцями; те, чого в стані
//  немає, просто НЕ ДОДАЄТЬСЯ в лейаут. Тому жодного if (state == …) по
//  тілу класу: рішення приймається один раз у buildLoaderState() /
//  buildMenuState(), а решта коду про стани не знає взагалі.
//
//  Кінцеві координати задають НЕВИДИМІ ЯКОРІ (aEmblemTarget, aTitlesTarget).
//  Вони є в обох станах, тому і анімація, і миттєве застосування читають
//  одні й ті самі числа — розʼїхатись не можуть за побудовою.
// ═════════════════════════════════════════════════════════════════════════════
class AMainLoader(
    override val screen: AdvancedScreen,
    startState: State = State.LOADER,
) : AConstraintLayout(screen) {

    enum class State { LOADER, MENU }

    companion object {
        // Блимання TAP TO START: альфа гуляє між цими значеннями
        private const val BLINK_MIN  = 0.22f
        private const val BLINK_MAX  = 0.62f
        private const val BLINK_TIME = 0.75f

        private const val HIDE_TIME  = 0.25f

        /** Має збігатися з часом animMorphTo/animToTarget (їх дефолт 0.85f). */
        private const val MORPH_TIME = 0.85f

        private const val TITLE_SCALE = 36f / 44f   // 0.818

        // Геометрія
        private const val TITLES_H       = 109f     // 53 + 3 + 53
        private const val TITLE_H        = 53f
        private const val EMBLEM_LOADER  = 220f
        private const val EMBLEM_MENU    = 150f
        private const val TITLES_GAP     = 28f
    }

    private val textBranding = """
        Powered by LibGDX
        Developed by Lewydo™
        Version ${BuildConfig.VERSION_NAME}
    """.trimIndent()

    private val themeColors = ThemeManager.current

    // ------------------------------------------------------------------------
    // Font
    // ------------------------------------------------------------------------
    private val msdf by lazy { gdxGame.msdfManager }

    private val shadowTitle1 = msdf.dropShadow(0f, 0f, 24f, themeColors.player.cpy().apply { a = 0.70f })
    private val shadowTitle2 = msdf.dropShadow(0f, 0f, 24f, themeColors.gem.cpy().apply { a = 0.70f })

    private val styleTitle1 = MsdfStyle(msdf, msdf.fontInter_ExtraBold, 44f, themeColors.player)
        .apply { letterSpacing = 18f; effects.add(shadowTitle1) }
    private val styleTitle2 = styleTitle1.copy(color = themeColors.gem, keepEffects = false)
        .apply { effects.add(shadowTitle2) }

    private val styleProgress = MsdfStyle(msdf, msdf.fontInter_Medium, 13f, GameColor.white_45)
        .apply { letterSpacing = 24f }
    private val styleBranding = MsdfStyle(msdf, msdf.fontInter_Medium, 10f, GameColor.white_25)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val onceTapToStart = AtomicBoolean(false)
    private val onceMorph      = AtomicBoolean(false)

    var state = startState
        private set

    /** true, коли показано TAP TO START — екран пускає тап лише тоді. */
    var isReadyToStart = false
        private set

    /** Викликається після завершення переходу LOADER → MENU. */
    var onCompletedAnimTapToStart: Block = {}

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aEmblem       = AOrbitEmblem(screen)
    private val aEmblemTarget = AAnchor()

    private val aTitlesGroup  = AConstraintLayout(screen)
    private val aTitlesTarget = AAnchor()
    private val aTitle1Lbl    = AMsdfLabel(TITLE_1, styleTitle1)
    private val aTitle2Lbl    = AMsdfLabel(TITLE_2, styleTitle2)

    val aTitlesAnchor = AAnchorOf(aTitlesGroup)

    private val aProgressLbl  = AMsdfLabel("0%", styleProgress)
    private val aBrandingLbl  = AMsdfLabel(textBranding, styleBranding)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        fillTitlesGroup()   // вміст групи титулів — однаковий у будь-якому стані
        addTargets()        // якорі потрібні обом станам

        when (state) {
            State.LOADER -> buildLoaderState()
            State.MENU   -> buildMenuState()
        }

        addActor(aTitlesAnchor)

        syncTheme()
    }

    override fun act(delta: Float) {
        super.act(delta)
        syncTheme()
    }

    /** Титули завжди живуть у власній групі: так вони їдуть і масштабуються разом. */
    private fun fillTitlesGroup() {
        aTitlesGroup.setSize(width, TITLES_H)

        aTitle1Lbl.setSize(width, TITLE_H)
        aTitlesGroup.add(aTitle1Lbl) { centerX(); topToTop() }
        aTitle1Lbl.setAlignment(Align.center)

        aTitle2Lbl.setSize(width, TITLE_H)
        aTitlesGroup.add(aTitle2Lbl) { centerX(); bottomToBottom() }
        aTitle2Lbl.setAlignment(Align.center)
    }

    private fun addTargets() {
        aEmblemTarget.setSize(EMBLEM_MENU, EMBLEM_MENU)
        add(aEmblemTarget) { center(); verticalBias = 0.9f }

        aTitlesTarget.setSize(width, TITLES_H)
        add(aTitlesTarget) { centerX(); topToBottom(aEmblemTarget, TITLES_GAP) }
    }

    // ------------------------------------------------------------------------
    // States
    // ------------------------------------------------------------------------

    /** Стартовий екран: усе велике, по центру, з прогресом і брендингом. */
    private fun buildLoaderState() {
        aBrandingLbl.setSize(width, 36f)
        add(aBrandingLbl) { centerX(); bottomToBottom(margin = 28f) }
        aBrandingLbl.setAlignment(Align.center)

        aEmblem.setSize(EMBLEM_LOADER, EMBLEM_LOADER)
        add(aEmblem) { centerX(); topToTop(); bottomToBottom(); verticalBias = 0.8f }

        add(aTitlesGroup) {
            centerX(); topToBottom(aEmblem); bottomToTop(aBrandingLbl); verticalBias = 0.7f
        }

        aProgressLbl.setSize(width, 16f)
        add(aProgressLbl) { centerX(); topToBottom(aTitlesGroup); bottomToTop(aBrandingLbl) }
        aProgressLbl.setAlignment(Align.center)
    }

    /**
     * Кінцевий стан. Прогрес і брендинг сюди просто не додаються — не треба
     * ні ховати їх, ні перевіряти стан деінде.
     */
    private fun buildMenuState() {
        onceMorph.set(true)
        onceTapToStart.set(true)
        isReadyToStart = false

        attachAtMenuAnchors()
    }

    /**
     * Посадити емблему й титули НА ЯКОРІ через констрейнти.
     *
     * Саме констрейнти, а не ручні координати: під час морфу актори detach-нуті
     * і летять за Action-ом, а після нього мають знову слухатись лейаута —
     * інакше будь-який зсув (банер, resize) розведе їх із якорями.
     */
    private fun attachAtMenuAnchors() {
        aEmblem.clearActions()
        aEmblem.setSize(aEmblemTarget.width, aEmblemTarget.height)
        add(aEmblem) { center(aEmblemTarget) }

        aTitlesGroup.clearActions()
        aTitlesGroup.setSize(aTitlesTarget.width, aTitlesTarget.height)
        aTitlesGroup.setOrigin(Align.center)
        aTitlesGroup.setScale(TITLE_SCALE)
        add(aTitlesGroup) { center(aTitlesTarget) }
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /** У стані MENU прогресу не існує — виклик просто нічого не робить. */
    fun setProgress(progress: Int) {
        if (aProgressLbl.parent == null) return

        if (progress >= 100) {
            tapToStart()
            return
        }

        aProgressLbl.setText("$progress%")
    }

    /**
     * Текст міняється на TAP TO START і починає «дихати» альфою.
     *
     * Actions, а не sin() у render: блимання живе разом з актором і само
     * зупиняється при clearActions()/remove. fadeIn/fadeOut не годяться —
     * вони ганяють альфу до 0 і 1, а нам треба коридор 0.22..0.62.
     */
    private fun tapToStart() {
        if (onceTapToStart.getAndSet(true)) return

        aProgressLbl.setText("TAP TO START")
        aProgressLbl.clearActions()
        aProgressLbl.color.a = BLINK_MAX

        aProgressLbl.addAction(
            Actions.forever(
                Actions.sequence(
                    Actions.alpha(BLINK_MIN, BLINK_TIME, Interpolation.sine),
                    Actions.alpha(BLINK_MAX, BLINK_TIME, Interpolation.sine)
                )
            )
        )

        isReadyToStart = true
    }

    // ------------------------------------------------------------------------
    // Animation
    // ------------------------------------------------------------------------

    /**
     * LOADER → MENU з анімацією. Захищено від повторного виклику: скільки б
     * тапів не прилетіло, морф стартує рівно один раз.
     */
    fun animTapToStart() {
        if (onceMorph.getAndSet(true)) return

        isReadyToStart = false
        state = State.MENU

        aEmblem.animMorphTo(aEmblemTarget)
        aTitlesGroup.animToTarget(aTitlesTarget, scale = TITLE_SCALE)

        aProgressLbl.clearActions()
        aProgressLbl.animHide(HIDE_TIME)
        aBrandingLbl.animHide(HIDE_TIME)

        // Навігація ПІСЛЯ завершення морфу, інакше екран міняється посеред руху
        clearActions()
        animDelay(MORPH_TIME) {
            attachAtMenuAnchors()
            onCompletedAnimTapToStart()
        }
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------
    private fun syncTheme() {
        val p = themeColors.player
        aTitle1Lbl.setTextColor(p)
        shadowTitle1.color.set(p.r, p.g, p.b, 0.70f)

        val g = themeColors.gem
        aTitle2Lbl.setTextColor(g)
        shadowTitle2.color.set(g.r, g.g, g.b, 0.70f)
    }

}