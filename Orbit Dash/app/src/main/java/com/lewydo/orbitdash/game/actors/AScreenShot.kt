package com.lewydo.orbitdash.game.actors

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.captureScreenShot

class AScreenShot(override val screen: AdvancedScreen) : AdvancedGroup() {

    private lateinit var regionScreenShot: TextureRegion
    private val boundsScreenShot = Rectangle()

    private val vecTmp           = Vector2(0f, 0f)
    private val vecGroupPosition = Vector2()
    private val vecBottomLeft    = Vector2()
    private val vecTopRight      = Vector2()

    override fun addActorsOnGroup() {
        updateBoundsScreenShot()
        regionScreenShot = TextureRegion(
            Texture(
                boundsScreenShot.width.toInt(),
                boundsScreenShot.height.toInt(),
                Pixmap.Format.RGB888
            )
        ).apply { flip(false, true) }

        addAndFillActor(Image(regionScreenShot))
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) throw Exception("Error draw: ${this::class.simpleName}")

        batch.flush()
        updateBoundsScreenShot()
        captureScreenShot(
            regionScreenShot,
            boundsScreenShot.x.toInt(),
            boundsScreenShot.y.toInt(),
            boundsScreenShot.width.toInt(),
            boundsScreenShot.height.toInt()
        )

        super.draw(batch, parentAlpha)
    }

    private fun updateBoundsScreenShot() {
        vecGroupPosition.set(localToStageCoordinates(vecTmp.set(0f, 0f)))

        // Отримуємо екранні координати (перетворюємо їх у пікселі)
        val bottomLeft = vecBottomLeft.set(vecGroupPosition.x, vecGroupPosition.y)
        val topRight   = vecTopRight.set(vecGroupPosition.x + width, vecGroupPosition.y + height)

        // Конвертуємо координати з FitViewport у ScreenViewport
        screen.viewportUI.project(bottomLeft)
        screen.viewportUI.project(topRight)

        // Отримуємо екранні значення
        val screenX = bottomLeft.x
        val screenY = bottomLeft.y
        val screenW = topRight.x - bottomLeft.x
        val screenH = topRight.y - bottomLeft.y

        //log("screenX = $screenX, screenY = $screenY, screenW = $screenW, screenH = $screenH")

        boundsScreenShot.set(screenX, screenY, screenW, screenH)
        boundsScreenShot.set(0f, 0f, screenW, screenH)
    }

}