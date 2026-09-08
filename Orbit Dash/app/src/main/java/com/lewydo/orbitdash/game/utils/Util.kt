package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.GDXGame
import com.lewydo.orbitdash.game.utils.vfx.VfxTextures

typealias Block = () -> Unit

val gdxGame: GDXGame get() = Gdx.app.applicationListener as GDXGame

val Texture.region: TextureRegion get() = TextureRegion(this)
val Float.toMS: Long get() = (this * 1000).toLong()
val TextureEmpty: Texture get() = VfxTextures.emptyTexture

fun disposeAll(vararg disposable: Disposable?) {
    disposable.forEach { it?.dispose() }
}

fun currentTimeMinus(time: Long) = System.currentTimeMillis().minus(time)

fun Iterable<Disposable>.disposeAll() {
    forEach { it.dispose() }
}

fun InputMultiplexer.addProcessors(vararg processor: InputProcessor) {
    processor.onEach { addProcessor(it) }
}

fun runGDX(block: Block) {
    Gdx.app.postRunnable { block.invoke() }
}

fun captureScreenShot(region: TextureRegion, x: Int, y: Int, w: Int, h: Int) {
    Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, region.texture.textureObjectHandle)
    Gdx.gl20.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h)
}


/**
 * Перестворити GL-only текстуру регіону — Texture(w, h, format), у яку
 * копіюють знімок екрана. Така текстура не керована: після втрати контексту
 * libGDX не має з чого її відновити, а glCopyTexSubImage2D у мертвий хендл дає
 * GL_INVALID_OPERATION і чорний знімок.
 *
 * setTexture() не чіпає UV — flip лишається як був, і Image, що тримає цей
 * регіон, працює далі без переприв'язки.
 */
fun TextureRegion.recreateGlOnlyTexture(w: Int, h: Int, format: Pixmap.Format) {
    runCatching { texture?.dispose() }
    setTexture(Texture(w.coerceAtLeast(1), h.coerceAtLeast(1), format))
}