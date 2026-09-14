package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
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