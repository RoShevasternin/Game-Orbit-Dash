package com.lewydo.orbitdash.game.actors.panel.boost

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.SizeScaler
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

// ----------------------------------------------------------------------------
//  ОДНА КАПСУЛА ЗАРЯДУ. Німа: знає свій вигляд, не знає ігрових правил.
//
//  Колір НЕ з теми — це колір бустера SHIELD, однаковий у всіх скінах.
//  Гравець має бачити звʼязок «підібрав блакитний шестикутник → загорілась
//  блакитна капсула», і зміна скіна не сміє його рвати.
// ----------------------------------------------------------------------------
class AShieldPip(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    companion object {
        private const val GLOW_ALPHA  = 0.30f
        private const val EMPTY_ALPHA = 0.14f
        private const val FLASH_TIME  = 0.15f

        private const val GLOW_W = 50f
        private const val GLOW_H = 35f
    }

    /** FULL — заряд є. EMPTY — слот був, але заряд витрачено. */
    enum class State { FULL, EMPTY }

    override val sizeScaler = SizeScaler(SizeScaler.Axis.X, 40f)

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aGlowImg = Image(gdxGame.assetsAll.shield_pip)
    private val aPipImg  = Image(gdxGame.assetsAll.shield_pip)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    var state: State = State.FULL
        private set

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addGlowImg()
        addPipImg()

        applyState()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (aGlowImg.parent == null) return
        aGlowImg.setSizeScaled(GLOW_W, GLOW_H)
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addGlowImg() {
        aGlowImg.setSizeScaled(GLOW_W, GLOW_H)
        add(aGlowImg) { center() }
    }

    private fun addPipImg() {
        add(aPipImg) { fillParent() }
    }

    // ------------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------------

    /** Миттєва зміна стану. */
    fun setState(newState: State) {
        if (newState == state) return
        state = newState
        applyState()
    }

    /**
     * Білий спалах у момент, коли щит поглинув удар.
     *
     * Запускає його ПАНЕЛЬ, а не піпса: капсула не знає, що сталося в рані,
     * вона лише вміє блимнути. Стан на цей момент уже EMPTY — спалах
     * домальовує поверх і сам повертає капсулу до її стану.
     */
    fun animFlash() {
        clearActions()

        aPipImg.setColorRGB(Color.WHITE)
        aPipImg.color.a = 1f
        aGlowImg.setColorRGB(Color.WHITE)
        aGlowImg.color.a = 1f

        addAction(Actions.sequence(
            Actions.delay(FLASH_TIME),
            Actions.run { applyState() },
        ))
    }

    // ------------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------------
    private fun applyState() {
        when (state) {
            State.FULL -> {
                aPipImg.setColorRGB(GameColor.Boost.shield)
                aPipImg.color.a = 1f
                aGlowImg.setColorRGB(GameColor.Boost.shield)
                aGlowImg.color.a = GLOW_ALPHA
            }
            State.EMPTY -> {
                // Ледь помітна біла: слот видно, але він явно не горить.
                aPipImg.setColorRGB(Color.WHITE)
                aPipImg.color.a = EMPTY_ALPHA
                aGlowImg.color.a = 0f
            }
        }
    }

}