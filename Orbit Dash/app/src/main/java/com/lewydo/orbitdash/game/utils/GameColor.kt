package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.graphics.Color

object GameColor {

    val background : Color = Color.valueOf("0E1024")

    val white_25 : Color = Color.WHITE.cpy().apply { a = 0.25f }
    val white_45 : Color = Color.WHITE.cpy().apply { a = 0.45f }
    val white_65 : Color = Color.WHITE.cpy().apply { a = 0.65f }
}