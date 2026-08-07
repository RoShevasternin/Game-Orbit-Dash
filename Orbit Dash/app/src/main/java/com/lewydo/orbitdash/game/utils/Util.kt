package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.GDXGame

typealias Block = () -> Unit

val gdxGame: GDXGame get() = Gdx.app.applicationListener as GDXGame

val Texture.region: TextureRegion get() = TextureRegion(this)
val Float.toMS: Long get() = (this * 1000).toLong()
val TextureEmpty get() = Texture(1, 1, Pixmap.Format.Alpha)

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

fun Float.divOr(num: Float, or: Float): Float = if (this != 0f && num != 0f) this / num else or

fun Vector2.divOr(scalar: Float, or: Float): Vector2 {
    x = x.divOr(scalar, or)
    y = y.divOr(scalar, or)
    return this
}

fun Vector2.divOr(scalar: Vector2, or: Float): Vector2 {
    x = x.divOr(scalar.x, or)
    y = y.divOr(scalar.y, or)
    return this
}

fun captureScreenShot(region: TextureRegion, x: Int, y: Int, w: Int, h: Int) {
    Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, region.texture.textureObjectHandle)
    Gdx.gl20.glCopyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h)
}