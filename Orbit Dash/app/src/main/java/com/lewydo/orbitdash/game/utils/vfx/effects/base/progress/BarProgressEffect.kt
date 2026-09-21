package com.lewydo.orbitdash.game.utils.vfx.effects.base.progress

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.effects.base.VfxEffect

// ─────────────────────────────────────────────────────────────────────────────
// BarProgressEffect — смуга прогресу капсулою (single-pass).
//
// Те саме, що RingProgressEffect робить для кільця, тільки по прямій: доріжка
// з альфою trackAlpha на всю довжину і заповнення на frac зліва. Одна фігура —
// один актор і один draw; двома акторами це коштувало б два перемикання
// шейдера в батчі HUD.
//
// Доріжка й заповнення мають окремі кольори, бо в грі вони різні: доріжка
// біла й ледь видима, заповнення — кольору активного буста.
//
// Радіус — у world-юнітах, як у RoundRectEffect: не «пливе» при зміні розміру;
// шейдер клемпить його до половини меншої сторони, як Figma.
//
// ТЕКСТУРНИЙ РЕЖИМ (textured = true): заповнення малює текстура drawable,
// натягнута на всю смугу, — прогрес її проявляє зліва, як шторка. Не тягне
// й не розтягує: UV стоять на місці. fillColor стає тінтом (WHITE = як є).
//
// textureSlides = true міняє шторку на «в'їзд»: картинка їде так, що її правий
// край тримається краю заповнення. Це поведінка «посунути картинку під маскою»,
// тільки без маски: ні FBO, ні перемальовки кешу щокадру.
// ─────────────────────────────────────────────────────────────────────────────
class BarProgressEffect : VfxEffect() {

    override val fragmentShader = "shader/base/progress/barProgressFS.glsl"

    /** Радіус кінців. Половина висоти = капсула. */
    var radius = 1.5f

    /** Заповнення 0..1, зліва направо. */
    var frac = 1f

    /** Альфа заповнення. 1 — суцільне; менше — смуга-привид поверх доріжки. */
    var fillAlpha = 1f

    /** Альфа незаповненої частини. */
    var trackAlpha = 0.2f

    /** Ширина зони згладжування в юнітах. */
    var aaWidth = 1.2f

    /** Колір доріжки. Білий = «беру з тінту актора». Міняти через .set(). */
    val trackColor = Color(Color.WHITE)

    /** Колір заповнення. Білий = те саме. У текстурному режимі — тінт текстури. */
    val fillColor = Color(Color.WHITE)

    /**
     * Заповнення малює текстура актора (drawable), а не fillColor: смуга
     * проявляє її зліва, UV прибиті до всієї смуги. Постав drawable з
     * картинкою — інакше «текстурою» буде білий 4×4 регіон.
     */
    var textured = false

    /** > 0 (лише з textured) — доріжка тією ж текстурою з цією альфою замість trackColor. */
    var texturedTrackAlpha = 0f

    /**
     * Як поводиться картинка (лише з [textured]):
     *   false — прибита до смуги, край заповнення її відкриває (шторка);
     *   true  — їде за краєм заповнення, «в'їжджаючи» зліва.
     *
     * З true доріжку краще лишати кольоровою ([trackAlpha]): за краєм заповнення
     * картинка вже скінчилась, і [texturedTrackAlpha] розмазав би її останній
     * стовпчик пікселів.
     */
    var textureSlides = false

    override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
        shader.setUniformf("u_size", ctx.width, ctx.height)
        shader.setUniformf("u_radius", radius)
        shader.setUniformf("u_aa", aaWidth)
        shader.setUniformf("u_frac", frac.coerceIn(0f, 1f))
        shader.setUniformf("u_fillA", fillAlpha)
        shader.setUniformf("u_trackA", trackAlpha)
        shader.setUniformf("u_trackColor", trackColor.r, trackColor.g, trackColor.b)
        shader.setUniformf("u_fillColor", fillColor.r, fillColor.g, fillColor.b)
        shader.setUniformf("u_useTex", if (textured) 1f else 0f)
        shader.setUniformf("u_texTrackA", texturedTrackAlpha)
        shader.setUniformf("u_texSlide", if (textureSlides) 1f else 0f)
    }

    override fun stateKey(): Long {
        var k = 41L
        k = k * 31 + radius.toRawBits()
        k = k * 31 + frac.toRawBits()
        k = k * 31 + fillAlpha.toRawBits()
        k = k * 31 + trackAlpha.toRawBits()
        k = k * 31 + aaWidth.toRawBits()
        k = k * 31 + trackColor.toIntBits().toLong()
        k = k * 31 + fillColor.toIntBits().toLong()
        k = k * 31 + (if (textured) 1L else 0L)
        k = k * 31 + texturedTrackAlpha.toRawBits()
        k = k * 31 + (if (textureSlides) 1L else 0L)
        return k
    }
}
