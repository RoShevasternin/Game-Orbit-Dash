package com.lewydo.orbitdash.game.actors.particleEffect

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.ParticleEmitter
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.game.utils.actor.disable

open class AParticleEffectActor(
    val particleEffect: ParticleEffect,
) : Actor(), Disposable {

    var isRunning = false
        private set

    private var resetOnStart: Boolean = false

    // ------------------------------------------------------------------------
    // Emitter cache
    // ------------------------------------------------------------------------

    private class EmitterData(
        val particles: Array<out Sprite>,
        val active   : BooleanArray
    )

    private val emittersData: Array<EmitterData> = particleEffect.emitters.map { emitter ->
        val pField = emitter.javaClass.getDeclaredField("particles").apply { isAccessible = true }
        val aField = emitter.javaClass.getDeclaredField("active").apply   { isAccessible = true }
        EmitterData(
            pField.get(emitter) as Array<out Sprite>,
            aField.get(emitter) as BooleanArray
        )
    }.toTypedArray()

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    override fun act(delta: Float) {
        super.act(delta)
        if (!isRunning) return

        particleEffect.update(delta)   // LibGDX застосовує Tint timeline
        applyPerParticleColors()       // одразу перекриваємо RGB, зберігаємо alpha
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        if (!isRunning) return

        val finalAlpha = color.a * parentAlpha
        if (finalAlpha <= 0.01f) return
        if (finalAlpha < 0.99f) applyAlphaToEmitters(finalAlpha)

        particleEffect.draw(batch)
    }

    private fun applyAlphaToEmitters(finalAlpha: Float) {
        for (data in emittersData) {
            for (i in data.active.indices) {
                if (data.active[i]) {
                    val p = data.particles[i]
                    p.setAlpha(p.color.a * finalAlpha)
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Per-particle color
    // ------------------------------------------------------------------------

    // Палітра — RGB float (0..1). Встановлюється через setColorPalette()
    private var palette : Array<Color>? = null

    // slotColors[emitterIdx][slotIdx] = колір призначений цій частинці
    // null = слот вільний, треба призначити при наступній активації
    private var slotColors : Array<Array<Color?>>? = null

    /**
     * Встановлює палітру кольорів для per-particle рандомізації.
     * Після виклику кожна нова частинка отримає випадковий колір з [colors].
     * Передай порожній масив або null щоб вимкнути.
     */
    fun setColorPalette(vararg colors: Color) {
        palette = if (colors.isEmpty()) null else Array(colors.size) { colors[it].cpy() }
        slotColors = palette?.let {
            Array(emittersData.size) { i ->
                arrayOfNulls<Color>(emittersData[i].active.size)
            }
        }
    }

    private fun applyPerParticleColors() {
        val pal    = palette    ?: return
        val slots  = slotColors ?: return

        for (eIdx in emittersData.indices) {
            val data      = emittersData[eIdx]
            val slotRow   = slots[eIdx]

            for (i in data.active.indices) {
                if (data.active[i]) {
                    // Нова частинка — призначаємо колір один раз
                    val col = slotRow[i] ?: pal.random().also { slotRow[i] = it }
                    val p   = data.particles[i]
                    // Зберігаємо альфу яку вже встановив LibGDX (Tint timeline)
                    p.setColor(col.r, col.g, col.b, p.color.a)
                } else {
                    // Частинка вмерла — звільняємо слот
                    slotRow[i] = null
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Tint color
    // ------------------------------------------------------------------------

    fun setTintColor(colorIndex: Int, color: Color) {
        particleEffect.emitters.forEach { emitter ->
            applyTint(emitter, colorIndex, color)
        }
    }

    fun setTintColor(emitterName: String, colorIndex: Int, color: Color) {
        particleEffect.emitters
            .firstOrNull { it.name == emitterName }
            ?.let { applyTint(it, colorIndex, color) }
    }

    fun setFirstTintColor(color: Color) {
        setTintColor(0, color)
    }

    fun setFirstTintColor(emitterName: String, color: Color) {
        setTintColor(emitterName, 0, color)
    }

    fun setLastTintColor(color: Color) {
        particleEffect.emitters.forEach { emitter ->
            val lastIndex = emitter.tint.colors.size / 3 - 1
            applyTint(emitter, lastIndex, color)
        }
    }

    fun setLastTintColor(emitterName: String, color: Color) {
        particleEffect.emitters
            .firstOrNull { it.name == emitterName }
            ?.let { emitter ->
                val lastIndex = emitter.tint.colors.size / 3 - 1
                applyTint(emitter, lastIndex, color)
            }
    }

    private fun applyTint(emitter: ParticleEmitter, colorIndex: Int, color: Color) {
        val colors = emitter.tint.colors
        val idx    = colorIndex * 3
        if (idx + 2 < colors.size) {
            colors[idx]     = color.r
            colors[idx + 1] = color.g
            colors[idx + 2] = color.b
        }
    }

    // ------------------------------------------------------------------------
    // Transform
    // ------------------------------------------------------------------------

    private data class FloatRange(val lowMin: Float, val lowMax: Float, val highMin: Float, val highMax: Float)
    private val originalAngles = particleEffect.emitters.map { emitter ->
        val a = emitter.angle
        FloatRange(a.lowMin, a.lowMax, a.highMin, a.highMax)
    }

    override fun rotationChanged() {
        particleEffect.emitters.forEachIndexed { i, emitter ->
            val a = originalAngles[i]
            emitter.angle.setLow(a.lowMin + rotation, a.lowMax + rotation)
            emitter.angle.setHigh(a.highMin + rotation, a.highMax + rotation)
        }
    }

    override fun positionChanged() { particleEffect.setPosition(x, y) }
    override fun scaleChanged()    { particleEffect.scaleEffect(scaleX, scaleY, scaleY) }

    // ------------------------------------------------------------------------
    // Control
    // ------------------------------------------------------------------------

    fun start(isResetOnStart: Boolean = false) {
        disable()

        resetOnStart = isResetOnStart
        if (resetOnStart) {
            particleEffect.reset(false)
            slotColors?.forEach { row -> row.fill(null) } // скидаємо кольори при reset
        }
        particleEffect.start()
        isRunning = true
    }

    fun pause()            { isRunning = false }
    fun resume()           { isRunning = true }
    fun allowCompletion()  = particleEffect.allowCompletion()
    override fun dispose() = particleEffect.dispose()

    fun fitToSize(
        targetWidth : Float? = null,
        targetHeight: Float? = null,
        baseWidth   : Float = 1f,
        baseHeight  : Float = 1f,
    ) {
        val scaleX = targetWidth?.let  { it / baseWidth  } ?: 1f
        val scaleY = targetHeight?.let { it / baseHeight } ?: scaleX
        setScale(scaleX, scaleY)
    }
}