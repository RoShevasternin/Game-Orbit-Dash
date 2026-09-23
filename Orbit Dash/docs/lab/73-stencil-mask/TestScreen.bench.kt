package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.vfx.AMask
import com.lewydo.orbitdash.game.actors.vfx.AStencilMask
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.debug.PerfMonitor
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.util.log

// ----------------------------------------------------------------------------
//  LAB · Стенд «маска через FBO проти маски через стенсил».
//
//  N однакових груп: у кожній картинка, більша за групу, крутиться — тож
//  autoCache в AMask не спрацьовує, і обидва способи працюють ЩОКАДРУ.
//  Маска — msdf-коло (альфа-регіон), одна й та сама для обох.
//
//  Тап — наступний режим: NONE (без маски, базова лінія) → AMASK (FBO) →
//  STENCIL. PerfMonitor пише PERF-рядки в logcat щосекунди.
// ----------------------------------------------------------------------------
class TestScreen : AdvancedScreen() {

    companion object {
        private const val N       = 96
        private const val COLS    = 8
        private const val CELL    = 150f
        private const val STEP_X  = 30f     // групи перекриваються: сітка щільніша за клітинку
        private const val STEP_Y  = 50f
        private const val PIC     = 260f    // картинка більша за маску — обрізання видно
        /** …_STATIC — картинка не крутиться: AMask має закешуватись, стенсил працює як завжди. */
        private val MODES = listOf("NONE", "AMASK", "STENCIL", "AMASK_STATIC", "STENCIL_STATIC")
    }

    private val aStarField by lazy { AStarField(this) }
    private val bench      = Group()
    private var mode       = 0

    override fun show() {
        super.show()
        animShowScreen()
        PerfMonitor.isLogEnabled = true
        build()
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
        addActor(bench)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        addDebugHud(ADebugHud(this@TestScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mode = (mode + 1) % MODES.size
        build()
        return true
    }

    private fun build() {
        bench.clear()
        val mask  = gdxGame.assetsMsdf.circle
        val stepX = STEP_X
        val stepY = STEP_Y
        val x0    = (stageUI.width  - ((COLS - 1) * stepX + CELL)) / 2f
        val y0    = (stageUI.height - ((N / COLS - 1) * stepY + CELL)) / 2f
        val static = mode >= 3

        for (i in 0 until N) {
            val g: Group = when (mode) {
                1, 3 -> AMask(this, mask)
                2, 4 -> AStencilMask(this, mask)
                else -> Group()
            }
            g.setBounds(x0 + (i % COLS) * stepX, y0 + (i / COLS) * stepY, CELL, CELL)

            val pic = Image(gdxGame.assetsAll.test_progress).apply {
                setSize(PIC, PIC)
                setPosition((CELL - PIC) / 2f, (CELL - PIC) / 2f)
                setOrigin(Align.center)
                rotation = i * 7f
                if (!static) addAction(Actions.forever(Actions.rotateBy(360f, 4f + i * 0.3f)))
            }
            g.addActor(pic)
            bench.addActor(g)
        }
        log("LAB MODE = ${MODES[mode]} × $N")
    }

    override fun animShowScreen(blockEnd: Block) { rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() } }
    override fun animHideScreen(blockEnd: Block) { rootConstraintLayout.disable(); rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() } }
}
