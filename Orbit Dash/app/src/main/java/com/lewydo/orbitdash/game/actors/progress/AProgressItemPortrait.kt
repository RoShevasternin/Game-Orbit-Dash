package com.lewydo.orbitdash.game.actors.progress

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.actors.vfx.AMask
import com.lewydo.orbitdash.game.utils.GameColor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.runGDX
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AProgressItemPortrait(override val screen: AdvancedScreen): AdvancedGroup() {

    private val LENGTH = 300f

    private val progressImage   = Image(screen.drawerUtil.getTexture(Color.PINK))
    private val mask            = AMask(screen, )// gdxGame.assetsAll.progress_mask_portrait)

    private val onePercentX = LENGTH / 100f

    // 0 .. 100 %
    val progressPercentFlow = MutableStateFlow(50f)


    override fun addActorsOnGroup() {
        addMask()

        coroutine?.launch {
            progressPercentFlow.collect { percent ->
                runGDX {
                    progressImage.x = (percent * onePercentX) - LENGTH
                }
            }
        }

    }

    // ---------------------------------------------------
    // Add Actors
    // ---------------------------------------------------
    private fun AdvancedGroup.addMask() {
        addAndFillActor(mask)
        mask.addProgress()
    }

    private fun AdvancedGroup.addProgress() {
        addAndFillActor(progressImage)
        progressImage.debug()
    }

}