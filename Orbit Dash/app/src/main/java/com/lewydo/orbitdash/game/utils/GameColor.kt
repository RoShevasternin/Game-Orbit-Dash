package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.graphics.Color
import com.lewydo.orbitdash.game.engine.RunEngine

object GameColor {

    val background : Color = Color.valueOf("0E1024")

    val white_25 : Color = Color.WHITE.cpy().apply { a = 0.25f }
    val white_45 : Color = Color.WHITE.cpy().apply { a = 0.45f }
    val white_65 : Color = Color.WHITE.cpy().apply { a = 0.65f }
    val white_90 : Color = Color.WHITE.cpy().apply { a = 0.90f }

    // ------------------------------------------------------------------------
    //  БУСТЕРИ — НЕ В ТЕМІ, І ЦЕ НАВМИСНО.
    //
    //  Тема фарбує ролі світу (фон, гравець, здобич), і вони змінюються зі
    //  скіном. Колір бустера — не оформлення, а мова гри: фіолетовий означає
    //  «магніт» так само, як червоне світло означає «стій».
    //
    //  Межа проста: якщо об'єкт упізнається за ФОРМОЮ, колір може бути темним
    //  (спайк — єдина восьмипроменева зірка на полі, тому він у ThemeManager).
    //  Якщо колір — ЄДИНИЙ розрізнювач, він константа. П'ять бустерів мають
    //  однаковий шестикутник і різняться лише кольором та літерою.
    // ------------------------------------------------------------------------
    object Boost {
        val shield : Color = Color.valueOf("4DD9FF")
        val magnet : Color = Color.valueOf("C07BFF")
        val frenzy : Color = Color.valueOf("FFD54A")
        val slow   : Color = Color.WHITE
        val pulse  : Color = Color.valueOf("FF9F2E")

        fun of(boost: RunEngine.Boost): Color = when (boost) {
            RunEngine.Boost.SHIELD -> shield
            RunEngine.Boost.MAGNET -> magnet
            RunEngine.Boost.FRENZY -> frenzy
            RunEngine.Boost.SLOW   -> slow
            RunEngine.Boost.PULSE  -> pulse
        }
    }
}