package com.lewydo.orbitdash.game.utils.vfx.effects.base

import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.lewydo.orbitdash.game.utils.vfx.Blit
import com.lewydo.orbitdash.game.utils.vfx.PingPong
import com.lewydo.orbitdash.game.utils.vfx.VfxContext
import com.lewydo.orbitdash.game.utils.vfx.VfxShaderCache

/**
 * Вбудовані ефекти системи.
 *
 * ─── ЯК ЧИТАТИ ЦЕЙ ФАЙЛ ────────────────────────────────────────────────────
 *
 * Кожен ефект — живий приклад того як писати нові:
 *
 * SINGLE-PASS (один Blit прохід):
 *   • override fragmentShader  → шлях до .glsl
 *   • override setUniforms()   → передаємо параметри
 *   Більше нічого. Базовий VfxEffect сам робить Blit + swap.
 *
 * MULTI-PASS (кілька Blit проходів):
 *   • override fragmentShader  → шлях до .glsl
 *   • override render()        → N разів: Blit.blit(...) + pingPong.swap()
 *   Правило: після кожного blit — обов'язково swap().
 *
 * DUAL-TEXTURE (дві текстури в шейдері):
 *   • override render()        → Blit.blit з uniforms лямбдою що bind-ить unit 1
 *
 * PASS-THROUGH (ефект вимкнений):
 *   • просто return без swap — src залишається незміненим для наступного ефекту
 *
 * ─── ШЕЙДЕР КЕШУЄТЬСЯ АВТОМАТИЧНО ─────────────────────────────────────────
 * val shader: ShaderProgram get() = VfxShaderCache.get(fragmentShader)
 * Не потрібно companion object lazy. VfxShaderCache компілює шейдер один раз.
 */

// ─────────────────────────────────────────────────────────────────────────────
// VfxEffect
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Базовий клас для всіх шейдерних ефектів.
 *
 * ─── Два vertex shaders ────────────────────────────────────────────────────
 *
 * У системі є ДВА контексти малювання з різними вимогами до vertex shader:
 *
 * 1. VfxGroup (Blit — raw GL quad):
 *    Вершини в NDC (-1..1). Матриця НЕ потрібна.
 *    Vertex shader: Blit.VERT → gl_Position = a_position
 *    Доступ: effect.shader
 *
 * 2. VfxImage (SpriteBatch):
 *    Вершини в world space (наприклад x=500, y=1200 у просторі 2160×3840).
 *    SpriteBatch множить на u_projTrans щоб перевести в NDC.
 *    Без u_projTrans → вершини за межами NDC → актор невидимий!
 *    Vertex shader: BATCH_VERT → gl_Position = u_projTrans * a_position
 *    Доступ: effect.batchShader
 *
 * ─── Як писати ефекти ──────────────────────────────────────────────────────
 *
 * Single-pass (більшість ефектів):
 * ```kotlin
 * class GrayscaleEffect(var strength: Float = 1f) : VfxEffect() {
 *     override val fragmentShader = "shader/grayscale.glsl"
 *     override fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {
 *         shader.setUniformf("u_strength", strength)
 *     }
 * }
 * ```
 *
 * Multi-pass (blur, bloom):
 * ```kotlin
 * class BlurEffect(var radius: Float = 8f) : VfxEffect() {
 *     override val fragmentShader = "shader/blur/gaussianBlurFS.glsl"
 *     override fun render(pingPong: PingPong, ctx: VfxContext) {
 *         if (radius <= 0f) return
 *         Blit.blit(pingPong.src, pingPong.dst, shader) { s ->
 *             s.setUniformf("u_direction", 1f, 0f)
 *             ...
 *         }
 *         pingPong.swap()
 *         ...
 *     }
 * }
 * ```
 */
abstract class VfxEffect {

    /** Шлях до fragment shader в assets */
    abstract val fragmentShader: String

    open fun setUniforms(shader: ShaderProgram, ctx: VfxContext) {}

    /**
     * Хеш параметрів ефекту для autoCache у VfxGroup.
     * За замовчуванням 0 = "статичний, кешується".
     * Анімовані ефекти (lava time, progress fill) перевизначають це —
     * тоді autoCache авто-перемальовує коли параметр змінюється,
     * і НЕ кешує коли він міняється щокадру (лава не застигає).
     */
    open fun stateKey(): Long = 0L
    open val isEnabled: Boolean get() = true

    /**
     * Наскільки ефект розповзається ЗА межі свого джерела, у world-юнітах.
     * Потрібно, щоб VfxTexture сам порахував bleed — поле під ефект назовні.
     * 0 = ефект нічого не виносить (маска, тінт).
     */
    open fun reachUnits(): Float = 0f

    /**
     * МІНІМАЛЬНО потрібна ефекту роздільність РОБОЧОГО буфера, текселів на юніт.
     * null = байдуже (тінт, HSL, маска) — ефект не голосує.
     *
     * Ланцюг бере МАКСИМУМ із вимог (найвибагливіший вирішує), стеля — екранна
     * густина. Блюр просить мало (σ ≈ 12 текселів — розмитій картинці більше не
     * треба). Маска не голосує тут узагалі: різкість потрібна її ВИХОДУ, не
     * входу, — див. outputDensity().
     * Так VfxTexture рахує density у конструкторі, а VfxGroup — щокадру.
     */
    open fun preferredDensity(): Float? = null

    /**
     * Густина ВЛАСНОГО ВИХОДУ, текселів на юніт. null (типово) — ефект пише в
     * робочий буфер разом з усіма.
     *
     * Не-null робить ефект ТЕРМІНАЛЬНИМ: VfxGroup дає йому окремий буфер
     * вихідної роздільності (стеля — екранна) і кличе renderToOutput() замість
     * render(). Такий ефект МУСИТЬ БУТИ ОСТАННІМ у ланцюгу — інакше VfxGroup
     * кине виняток.
     *
     * Сенс: решта ланцюга працює в дешевому робочому буфері, а різкий буфер
     * платиться рівно один раз, на тому проході, якому він справді потрібен.
     */
    open fun outputDensity(): Float? = null

    /**
     * Малює робочий буфер src у вихідний dst. Кличеться замість render() і лише
     * для термінального ефекту. Перевизначити ОБОВ'ЯЗКОВО, якщо outputDensity()
     * не null: розміри можуть не збігатись (читай їх із src/dst), і підняти
     * джерело — обов'язок ефекту.
     */
    open fun renderToOutput(src: FrameBuffer, dst: FrameBuffer, ctx: VfxContext) {}

    // ─── Shader для VfxGroup (Blit — NDC quad) ────────────────────────────
    // Vertex = Blit.VERT: gl_Position = a_position (без матриці)
    val shader: ShaderProgram
        get() = VfxShaderCache.get(fragmentShader, Blit.VERT)

    // ─── Shader для VfxImage (SpriteBatch — world space) ─────────────────
    // Vertex = BATCH_VERT: gl_Position = u_projTrans * a_position
    // SpriteBatch передає вершини у world coords і множить на projMatrix.
    // Без u_projTrans → вершини за межами NDC → нічого не видно!
    val batchShader: ShaderProgram
        get() = VfxShaderCache.get(fragmentShader, BATCH_VERT)

    // ─── Render (для VfxGroup) ────────────────────────────────────────────
    open fun render(pingPong: PingPong, ctx: VfxContext) {
        if (!isEnabled) return
        Blit.blit(pingPong.src, pingPong.dst, shader) { s -> setUniforms(s, ctx) }
        pingPong.swap()
    }

    companion object {
        /**
         * Vertex shader для SpriteBatch з підтримкою нормалізованих UV.
         *
         * u_uvMin, u_uvMax — UV bounds регіону (VfxImage передає автоматично).
         * Для повної текстури: (0,0)..(1,1) → v_localUV = v_texCoords.
         * Для atlas region: нормалізовано до 0..1 в межах регіону.
         */
        val BATCH_VERT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4  u_projTrans;
            varying vec4  v_color;
            varying vec2  v_texCoords;

            uniform vec2  u_uvMin;
            uniform vec2  u_uvMax;
            varying vec2  v_localUV;

            void main() {
                v_color     = a_color;
                v_color.a   = v_color.a * (255.0 / 254.0);
                v_texCoords = a_texCoord0;
                
                vec2 uvRange = u_uvMax - u_uvMin;
                v_localUV    = (uvRange.x > 0.0 && uvRange.y > 0.0) ? (a_texCoord0 - u_uvMin) / uvRange : a_texCoord0;
                    
                gl_Position = u_projTrans * a_position;
            }
        """.trimIndent()
    }
}