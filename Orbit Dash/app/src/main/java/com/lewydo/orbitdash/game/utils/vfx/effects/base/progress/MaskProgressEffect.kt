package com.lewydo.orbitdash.game.utils.vfx.effects.base.progress

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// MaskProgressEffect — універсальний прогрес: форму задає МАСКА-текстура.
//
// ЧИМ ВІДРІЗНЯЄТЬСЯ ВІД BarProgressEffect. Той малює капсулу математикою:
// ідеальний край на будь-якому розмірі, нуль текстур, один семплер — і тому
// нічого, крім капсули. Цей бере форму картинкою, тож уміє будь-що: зірку,
// кільце, підкову, форму з дірками. Ціна — до трьох текстур замість нуля.
// Смуга в HUD лишається на BarProgressEffect; сюди йде все складніше.
//
// ТРИ КАРТИНКИ, і всі необов'язкові:
//   маска  — drawable САМОГО АКТОРА (як завжди в VfxImage). Її альфа каже, де
//            взагалі є пікселі: дірка лишається діркою, півпрозоре лишається
//            півпрозорим. Проста форма — запекти VfxTexture(RoundRectEffect),
//            складна — PNG від дизайнера. Немає маски (білий регіон) — прямокутник.
//   fill   — чим заповнюється. Немає → [fillColor].
//   track  — фон під заповненням. Немає → [trackColor] з [trackAlpha].
// Кольори працюють тінтом і поверх картинок: білий = картинка як є.
//
// ЧОМУ ЦЕ НЕ AMask І НЕ FBO. VfxGroup із маскою рендерить піддерево в буфер і
// перемальовує його, щойно діти зрушили, — у прогресі це кожен кадр. Тут маска
// читається прямо в шейдері: один draw, нуль render target'ів.
//
// ТЕКСТУРНІ ЮНІТИ. Батч тримає свою текстуру на юніті 0, тому fill і track
// сідають на 1 і 2, а активний юніт ОБОВ'ЯЗКОВО повертається на 0: libGDX
// Texture.bind(n) лишає активним саме n, і наступна прив'язка батча пішла б не
// туди. Невикористаний семплер дивиться на юніт 0 — щоб не читати з нічого.
// ─────────────────────────────────────────────────────────────────────────────
class MaskProgressEffect : VfxEffect() {

    override val fragmentShader = "shader/base/progress/maskProgressFS.glsl"

    /** Уздовж чого росте заповнення. */
    enum class Axis { X, Y }

    /** Картинка заповнення. null — суцільний [fillColor]. */
    var fill: TextureRegion? = null

    /** Картинка фону. null — суцільний [trackColor] з [trackAlpha]. */
    var track: TextureRegion? = null

    /** Заповнення 0..1. */
    var frac = 1f

    /** Напрямок: X — зліва направо, Y — знизу вгору. */
    var axis = Axis.X

    /** true — від протилежного краю (справа наліво / згори вниз). */
    var reversed = false

    /**
     * false — картинка заповнення прибита, край її проявляє (шторка);
     * true  — картинка їде так, що її кінець тримається краю заповнення.
     */
    var slides = false

    /** М'якість краю заповнення в частках довжини. 0.002 ≈ пів пікселя на 200 юнітах. */
    var softEdge = 0.002f

    /** Альфа заповнення. 1 — суцільне; менше — привид поверх фону. */
    var fillAlpha = 1f

    /** Альфа фону. 0 — фону немає взагалі. */
    var trackAlpha = 0.2f

    /** Тінт заповнення. Білий = «картинка як є» або чистий колір. */
    val fillColor = Color(Color.WHITE)

    /** Тінт фону. */
    val trackColor = Color(Color.WHITE)

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        val f = fill
        val t = track

        // Прив'язуємо ЩОКАДРУ через region.texture: після втрати GL-контексту
        // VfxTexture створює нову текстуру під тим самим регіоном, і закешований
        // тут об'єкт указував би на мертву.
        f?.texture?.bind(1)
        t?.texture?.bind(2)
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)

        shader.setUniformi("u_fillTex",  if (f != null) 1 else 0)
        shader.setUniformi("u_trackTex", if (t != null) 2 else 0)

        shader.setUniformf("u_fillUv",  f?.u ?: 0f, f?.v ?: 0f, f?.u2 ?: 1f, f?.v2 ?: 1f)
        shader.setUniformf("u_trackUv", t?.u ?: 0f, t?.v ?: 0f, t?.u2 ?: 1f, t?.v2 ?: 1f)
        shader.setUniformf("u_useFillTex",  if (f != null) 1f else 0f)
        shader.setUniformf("u_useTrackTex", if (t != null) 1f else 0f)

        shader.setUniformf("u_fillColor",  fillColor.r,  fillColor.g,  fillColor.b)
        shader.setUniformf("u_trackColor", trackColor.r, trackColor.g, trackColor.b)
        shader.setUniformf("u_fillA", fillAlpha)
        shader.setUniformf("u_trackA", trackAlpha)
        shader.setUniformf("u_frac", frac.coerceIn(0f, 1f))
        shader.setUniformf("u_axis", if (axis == Axis.Y) 1f else 0f)
        shader.setUniformf("u_reversed", if (reversed) 1f else 0f)
        shader.setUniformf("u_slide", if (slides) 1f else 0f)
        shader.setUniformf("u_soft", softEdge)
    }

    override fun stateKey(): Long {
        var k = 57L
        k = k * 31 + frac.toRawBits()
        k = k * 31 + fillAlpha.toRawBits()
        k = k * 31 + trackAlpha.toRawBits()
        k = k * 31 + softEdge.toRawBits()
        k = k * 31 + fillColor.toIntBits().toLong()
        k = k * 31 + trackColor.toIntBits().toLong()
        k = k * 31 + (if (axis == Axis.Y) 1L else 0L)
        k = k * 31 + (if (reversed) 2L else 0L)
        k = k * 31 + (if (slides) 4L else 0L)
        k = k * 31 + (fill?.let { System.identityHashCode(it) } ?: 0).toLong()
        k = k * 31 + (track?.let { System.identityHashCode(it) } ?: 0).toLong()
        return k
    }
}
