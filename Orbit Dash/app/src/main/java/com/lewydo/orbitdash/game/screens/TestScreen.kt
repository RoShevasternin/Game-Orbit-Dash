package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.ABlurBack
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.effects.base.RoundRectEffect

class TestScreen : AdvancedScreen() {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStarField by lazy { AStarField(this) }

    // Маска стенда: заокруглений прямокутник, запечений у текстуру. Її край
    // різкий, і саме по ньому видно, чи лишився вихідний прохід повнорозмірним.
    private val maskTex = VfxTexture(200f, 300f, shape = RoundRectEffect().apply { radius = 24f })

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()

        disposableSet.add(maskTex)
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
        addBlurBackStand()
    }

    // ── ABlurBack: робоча густина окремо від вихідної. ТИМЧАСОВЕ ─────────────
    // Тло — AStarField (дрібні точки, найгірший випадок для мінификації знімка).
    // isStaticEffect = true: знімок і весь ланцюг раз, далі один квад — саме
    // так це працюватиме в попапі.
    private fun AConstraintLayout.addBlurBackStand() {
        val back = ABlurBack(this@TestScreen).apply {
            setSize(200f, 300f)
            blur           = 4f
            maskRegion     = maskTex.region
            //isStaticEffect = true
        }
        add(back) { size(300f, 300f); center() }
        back.debug()
    }

}