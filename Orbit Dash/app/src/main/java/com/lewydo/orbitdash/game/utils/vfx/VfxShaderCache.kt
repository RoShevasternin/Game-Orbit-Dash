package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable
import com.lewydo.orbitdash.util.log

/**
 * Централізований кеш ShaderProgram.
 *
 * ─── ВИПРАВЛЕНО: ключ = fragmentPath + vertexSrc ────────────────────────────
 *
 * Раніше ключем був ТІЛЬКИ fragmentPath. Але один фрагмент може
 * компілюватись з різними vertex shaders:
 *   effect.shader      → Blit.VERT   (NDC, для FBO ефектів у VfxGroup)
 *   effect.batchShader → BATCH_VERT  (world→NDC, для VfxImage через SpriteBatch)
 *
 * Зі старим ключем: хто перший запитав фрагмент — той vertex і закешувався,
 * другий отримував ЧУЖИЙ vertex shader мовчки → поламаний рендер без помилки.
 *
 * Тепер ключ враховує обидва → кожна пара (fragment, vertex) має свій
 * скомпільований ShaderProgram. Компіляція все одно одноразова на пару.
 */
object VfxShaderCache : Disposable {

    private val cache = HashMap<String, ShaderProgram>()

    fun get(fragmentPath: String, vertexSrc: String = Blit.VERT): ShaderProgram {
        // Ключ = фрагмент + хеш vertex source (стабільний, не залежить від довжини)
        val key = fragmentPath + "#" + vertexSrc.hashCode()
        return cache.getOrPut(key) {
            val fragSrc = Gdx.files.internal(fragmentPath).readString()
            ShaderProgram(vertexSrc, fragSrc).also { shader ->
                if (!shader.isCompiled) {
                    throw IllegalStateException(
                        "VfxShaderCache: не вдалось скомпілювати '$fragmentPath':\n${shader.log}"
                    )
                }
                // Лог попереджень навіть якщо скомпілювалось (unused varyings тощо)
                if (shader.log.isNotBlank()) {
                    log("$fragmentPath:\n${shader.log}")
                }
            }
        }
    }

    override fun dispose() {
        cache.values.forEach { runCatching { it.dispose() } }
        cache.clear()
    }
}