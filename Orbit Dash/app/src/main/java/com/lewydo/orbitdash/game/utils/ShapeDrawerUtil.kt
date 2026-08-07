package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import space.earlygrey.shapedrawer.ShapeDrawer

class ShapeDrawerUtil(batch: Batch): Disposable {

    private val disposableSet = mutableSetOf<Disposable>()

    val drawer = ShapeDrawer(batch, getRegion())

    override fun dispose() {
        disposableSet.disposeAll()
    }

    fun update() {
        drawer.update()
    }

    fun getRegion(color: Color = Color.WHITE): TextureRegion {
        return TextureRegion(getTexture(color), 0, 0, 4, 4)
    }

    fun getTexture(color: Color = Color.WHITE): Texture {
        val pixmap = Pixmap(4, 4, Pixmap.Format.RGBA8888).apply {
            setColor(color)
            fill()
        }
        val texture = Texture(pixmap).also {
            pixmap.dispose()
            disposableSet.add(it)
        }

        return texture
    }

}