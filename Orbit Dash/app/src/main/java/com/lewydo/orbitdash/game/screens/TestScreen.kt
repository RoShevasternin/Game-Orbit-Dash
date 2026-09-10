package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.msdf.AMsdfImage
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.actor.setSize
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

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
        val img = /*gdxGame.assetsMsdf.glowTex.image() */Image(gdxGame.assetsMsdf.glow)
        img.setSize(gdxGame.assetsMsdf.glowTex.outerWidth, gdxGame.assetsMsdf.glowTex.outerHeight)
        add(img) { center(); verticalBias = 0.7f }
        img.debug()
    }

    /** Поставити актора в лівий-нижній кут root. Розмір актор задає сам. */
    private fun AConstraintLayout.at(x: Float, y: Float, actor: Actor) {
        actor.debug()
        add(actor) { startToStart(margin = x); bottomToBottom(margin = y) }
    }

}