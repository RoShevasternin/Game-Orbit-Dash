package com.lewydo.orbitdash.game.actors.orbit

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.objects.decor.ABallDecor
import com.lewydo.orbitdash.game.actors.objects.decor.AGemDecor
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.theme.ThemeManager

// ═════════════════════════════════════════════════════════════════════════════
//  ГЛОБАЛЬНИЙ ГОДИННИК ОРБІТ.
//
//  Кути живуть ПОЗА екраном — тому нова емблема на MenuScreen підхоплює рух
//  тієї, що крутилась на лоадері, замість стрибати в стартову позицію.
//  Та сама ідея, що й StarFieldClock: усе, що має пережити зміну екрана,
//  мусить лежати в глобальному стані, а не в полі актора.
// ═════════════════════════════════════════════════════════════════════════════
object OrbitEmblemClock {

    var ballAngleDeg = 90f    // старт: верхня точка
        private set
    var gemAngleDeg  = 0f     // старт: права точка
        private set

    private var lastFrameId = -1L

    /** Крутиться один раз на кадр, скільки б емблем не було на сцені. */
    fun update(delta: Float, ballSpeed: Float, gemSpeed: Float) {
        val frame = com.badlogic.gdx.Gdx.graphics.frameId
        if (frame == lastFrameId) return
        lastFrameId = frame

        ballAngleDeg = (ballAngleDeg + ballSpeed * delta) % 360f
        gemAngleDeg  = (gemAngleDeg  + gemSpeed  * delta) % 360f
    }
}

class AOrbitEmblem(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val BALL_SPEED_DEG = -51f     // 360° за ≈7 c, годинникової
        private const val GEM_SPEED_DEG  = 36f    // 360° за ≈10 c, зустрічно

        /**
         * Скільки обертів навколо себе робить ромб за один оберт по орбіті.
         * 1f — рух «приклеєний» до орбіти; 2–3f — гем помітно виблискує.
         */
        private const val GEM_SPIN_RATIO = 2f
    }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 220f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aOrbitGlowImg = Image(gdxGame.assetsLoader.ORBIT_GLOW)
    private val aRingOuter    = AOrbitRing(screen)
    private val aRingInner    = AOrbitRing(screen)
    private val aBall         = ABallDecor(screen)
    private val aGem          = AGemDecor(screen)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    private val tmpVec = Vector2()

    var isSpinning = true

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addOrbitGlow()

        addRingOuter()
        addRingInner()

        addBall()
        addGem()

        syncTheme()
    }

    private var themeVersion = -1

    override fun act(delta: Float) {
        super.act(delta)   // спершу констрейнти розкладуть кільця під поточний розмір

        if (themeVersion != ThemeManager.version) {
            themeVersion = ThemeManager.version
            syncTheme()
        }

        if (isSpinning) OrbitEmblemClock.update(delta, BALL_SPEED_DEG, GEM_SPEED_DEG)

        val ballAngleDeg = OrbitEmblemClock.ballAngleDeg
        val gemAngleDeg  = OrbitEmblemClock.gemAngleDeg

        placeOnOrbit(aBall, aRingOuter, ballAngleDeg)
        placeOnOrbit(aGem,  aRingInner, gemAngleDeg)

        // Ромб обертається навколо себе синхронно з рухом по орбіті:
        // прокрутився по колу — прокрутився і сам. Кульку не крутимо,
        // вона симетрична й обертання на ній не читалося б.
        aGem.spin = gemAngleDeg * GEM_SPIN_RATIO
    }

    /** При анімації розміру перерозкладаємо все, що задано у дизайн-одиницях. */
    override fun sizeChanged() {
        super.sizeChanged()
        if (aRingInner.parent == null) return
        aOrbitGlowImg.setSizeScaled(660f, 660f)
        aRingInner.setSizeScaled(140f, 140f)
        aBall.setSizeScaled(26f, 26f)
        aGem.setSizeScaled(26f, 26f)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addOrbitGlow() {
        aOrbitGlowImg.setSizeScaled(660f, 660f)
        add(aOrbitGlowImg) { center() }
    }

    private fun addRingOuter() {
        add(aRingOuter) { fillParent() }
    }

    private fun addRingInner() {
        aRingInner.setSizeScaled(140f, 140f)
        add(aRingInner) { center() }
    }

    /**
     * addActor, а НЕ add{ } — навмисно.
     *
     * Позицією мʼяча і гема керує placeOnOrbit(). Якби вони були констрейнт-
     * вузлами, то під час sizeTo група інвалідується, перед draw() іде
     * validate() → layout() → усі вузли стають за констрейнтами, і center()
     * повертав би обʼєкти в центр саме на час морфу.
     */
    private fun addBall() {
        aBall.setSizeScaled(26f, 26f)
        addActor(aBall)
    }

    private fun addGem() {
        aGem.setSizeScaled(26f, 26f)
        addActor(aGem)
    }

    // ------------------------------------------------------------------------
    // Logic
    // ------------------------------------------------------------------------

    /** Центр обʼєкта — на лінії кільця під кутом angleDeg. */
    private fun placeOnOrbit(actor: Actor, ring: AOrbitRing, angleDeg: Float) {
        val rad = angleDeg * MathUtils.degRad
        val cx = ring.x + ring.width  * 0.5f
        val cy = ring.y + ring.height * 0.5f
        val r  = ring.currentRadius
        actor.setPosition(
            cx + MathUtils.cos(rad) * r - actor.width  * 0.5f,
            cy + MathUtils.sin(rad) * r - actor.height * 0.5f
        )
    }

    private fun syncTheme() {
        val t = ThemeManager.current
        aOrbitGlowImg.setColorRGB(t.player)
        aRingOuter.ringColor.set(t.ring)
        aRingInner.ringColor.set(t.ring)
        // aBall і aGem синхронізують себе самі
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /**
     * Морф до позиції актора-ЯКОРЯ — не треба вгадувати координати.
     *
     *   private val aEmblemTarget = Actor()
     *
     *   aEmblemTarget.setSize(150f, 150f)
     *   aEmblemTarget.isVisible = false
     *   add(aEmblemTarget) { centerX(); topToTop(margin = 60f) }
     *
     *   // потім:
     *   aEmblem.animMorphTo(aEmblemTarget)
     */
    fun animMorphTo(
        target: Actor,
        time: Float = 0.85f,
        interpolation: Interpolation = Interpolation.exp10Out,
        blockEnd: Block = {},
    ) {
        val p = parent ?: return

        // Через stage — працює, навіть якщо якір лежить в іншій групі
        val pos = target.localToStageCoordinates(tmpVec.set(0f, 0f))
        p.stageToLocalCoordinates(pos)

        animMorph(target.width, pos.x, pos.y, time, interpolation, blockEnd)
    }

    /**
     * Морф явними координатами. Перед анімацією САМА відвʼязується від
     * констрейнтів (detach) — інакше лейаут щокадру повертав би емблему
     * на місце, задане center()/bias.
     */
    fun animMorph(
        toWidth: Float,
        toX: Float,
        toY: Float,
        time: Float = 0.85f,
        interpolation: Interpolation = Interpolation.exp10Out,
        blockEnd: Block = {},
    ) {
        (parent as? AConstraintLayout)?.detach(this)

        clearActions()
        addAction(
            Actions.sequence(
                Actions.parallel(
                    Actions.sizeTo(toWidth, toWidth, time, interpolation),
                    Actions.moveTo(toX, toY, time, interpolation),
                ),
                Actions.run(blockEnd)
            )
        )
    }

}