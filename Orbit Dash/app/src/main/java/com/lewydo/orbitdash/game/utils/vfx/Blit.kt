package com.lewydo.orbitdash.game.utils.vfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

/**
 * Raw GL fullscreen blit — аналог Unity's Graphics.Blit.
 *
 * Один fullscreen quad (2 трикутники) у NDC (-1..1) через шейдер.
 * Mesh компілюється раз і живе до кінця гри.
 *
 * ─── Nesting-safe ──────────────────────────────────────────────────────────
 * Використовує FboStack.push/pop замість dst.begin()/end() — щоб blit
 * всередині вкладеного VfxGroup не зламав прив'язку батьківського FBO.
 */
object Blit : Disposable {

    val VERT = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;
        varying  vec2  v_texCoords;
        varying  vec4  v_color;
        void main() {
            v_texCoords = a_texCoord0;
            v_color     = vec4(1.0);
            gl_Position = a_position;
        }
    """.trimIndent()

    @Suppress("GDXKotlinStaticResource")
    private var mesh: Mesh? = null

    private fun getMesh(): Mesh {
        val existing = mesh
        if (existing != null && existing.numIndices > 0) return existing
        existing?.dispose()
        return createMesh().also { mesh = it }
    }

    private fun createMesh(): Mesh {
        return Mesh(
            true, 4, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "${ShaderProgram.TEXCOORD_ATTRIBUTE}0")
        ).apply {
            setVertices(floatArrayOf(
                -1f, -1f,  0f, 0f,
                1f, -1f,  1f, 0f,
                1f,  1f,  1f, 1f,
                -1f,  1f,  0f, 1f,
            ))
            setIndices(shortArrayOf(0, 1, 2, 2, 3, 0))
        }
    }

    fun blit(
        src     : FrameBuffer,
        dst     : FrameBuffer,
        shader  : ShaderProgram,
        uniforms: (ShaderProgram) -> Unit = {}
    ) {
        // Nesting-safe: push/pop замість begin/end
        FboStack.push(dst)
        clearAndRender(src.colorBufferTexture, shader, uniforms)
        FboStack.pop()
    }

    fun blit(
        src     : Texture,
        dst     : FrameBuffer,
        shader  : ShaderProgram,
        uniforms: (ShaderProgram) -> Unit = {}
    ) {
        FboStack.push(dst)
        clearAndRender(src, shader, uniforms)
        FboStack.pop()
    }

    private fun clearAndRender(texture: Texture, shader: ShaderProgram, uniforms: (ShaderProgram) -> Unit) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFuncSeparate(
            GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
            GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA
        )

        shader.bind()
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        texture.bind(0)
        shader.setUniformi("u_texture", 0)
        uniforms(shader)

        getMesh().render(shader, GL20.GL_TRIANGLES)

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
    }

    override fun dispose() {
        runCatching { mesh?.dispose() }
        mesh = null
    }
}