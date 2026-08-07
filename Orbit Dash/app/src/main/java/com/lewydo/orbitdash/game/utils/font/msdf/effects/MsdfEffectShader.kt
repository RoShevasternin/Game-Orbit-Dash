package com.lewydo.orbitdash.game.utils.font.msdf.effects

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

// ─────────────────────────────────────────────────────────────────────────────
// MsdfEffectShader — обгортка ОДНОГО шейдера-шару (fill / stroke / shadow...).
//   Спільний VS + власний FS ефекту. Компілюється раз.
// ─────────────────────────────────────────────────────────────────────────────

class MsdfEffectShader(fragPath: String) : Disposable {

    val program: ShaderProgram

    init {
        ShaderProgram.pedantic = false
        program = ShaderProgram(
            Gdx.files.internal("shader/defaultVS.glsl"),
            Gdx.files.internal(fragPath),
        )
        if (!program.isCompiled) {
            throw IllegalStateException("MSDF effect shader compile error ($fragPath):\n${program.log}")
        }
    }

    override fun dispose() = program.dispose()
}