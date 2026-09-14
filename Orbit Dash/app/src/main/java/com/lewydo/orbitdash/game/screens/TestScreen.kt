package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.ui.ARoundRect
import com.lewydo.orbitdash.game.actors.vfx.ABlur
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.actor.setSize
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.effects.RoundRectEffect
import com.lewydo.orbitdash.game.utils.vfx.effects.base.BlurEffect

class TestScreen : AdvancedScreen() {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStarField by lazy { AStarField(this) }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        addMsdfSandbox()
        addDebugHud(ADebugHud(this@TestScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val v = stageUI.screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
        aStarField.animRippleAt(v.x, v.y)
        return false
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.disable()
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // MSDF sandbox v2 — усі способи використання на одному екрані (ТИМЧАСОВЕ)
    // ------------------------------------------------------------------------

    private fun AConstraintLayout.addMsdfSandbox() {
        addBlurStand()
    }

    // ── ABlur: авто-густина (ліворуч) проти повної (праворуч). ТИМЧАСОВЕ ──────
    // Дитина рухається, тож autoCache перемальовує щокадру — саме той випадок,
    // де density має значення. Візуально дві плями мають бути однакові.
    private fun AConstraintLayout.addBlurStand() {
        fun stand(explicit: Float?) = ABlur(this@TestScreen).apply {
            setSize(140f, 140f)
            blur    = 30f
            density = explicit
            addActor(Image(drawerUtil.getTexture(Color.ORANGE)).apply {
                setBounds(30f, 30f, 80f, 80f)
                addAction(Actions.forever(Actions.sequence(
                    Actions.moveBy( 20f, 0f, 1f),
                    Actions.moveBy(-20f, 0f, 1f),
                )))
            })
        }
        val auto = stand(null)   // очікувано screen/4 ≈ 0.75 → буфер 105×105
        val full = stand(3f)     // як було до патча → 420×420
        add(auto) { startToStart(margin = 20f); bottomToBottom(margin = 120f) }
        add(full) { endToEnd(margin = 20f);     bottomToBottom(margin = 120f) }
    }

}