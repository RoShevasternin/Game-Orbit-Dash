package com.lewydo.orbitdash.game.content

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.engine.RunEngine.Boost
import com.lewydo.orbitdash.game.utils.gdxGame

// ----------------------------------------------------------------------------
//  КАТАЛОГ БУСТІВ — ОДИН КЛЮЧ, ОДИН ЗАПИС.
//
//  RunEngine.Boost каже, ЩО це і як воно грає (dur, weight, startOffer — чисті
//  числа, рушій лишається без libGDX). Каталог каже, ЯК воно виглядає і як
//  зветься для гравця. Отримувач бере з запису те, що йому треба:
//
//      val info = boost.info
//      aHex.setColorRGB(info.color)
//      aIcon.drawable = TextureRegionDrawable(info.icon)
//
//  БУСТЕРИ НЕ В ТЕМІ, І ЦЕ НАВМИСНО. Тема фарбує ролі світу (фон, гравець,
//  здобич), і вони змінюються зі скіном. Колір бустера — не оформлення, а мова
//  гри: фіолетовий означає «магніт» так само, як червоне світло означає «стій».
//  Межа проста: якщо об'єкт упізнається за ФОРМОЮ, колір може бути темним
//  (спайк — єдина восьмипроменева зірка на полі, тому він у ThemeManager).
//  Якщо колір — ЄДИНИЙ розрізнювач, він константа. П'ять бустерів мають
//  однаковий шестикутник і різняться лише кольором та іконкою.
//
//  ІКОНКА — GETTER, НІКОЛИ НЕ ПОЛЕ. object живе довше за GDXGame: Android може
//  знищити Activity, лишивши процес, і gdxGame.assetsAll (by lazy у GDXGame)
//  стане НОВИМ об'єктом. Закешований тут TextureRegion після такого повернення
//  вказував би на атлас мертвої гри. Резолвимо при зверненні — це читання поля,
//  не пошук. Той самий клас пасток, що whiteTex у VfxTextures (патч 20).
// ----------------------------------------------------------------------------

/** Усе, що гра знає про буст. Правила читаються наскрізь із enum. */
data class BoostInfo(
    val boost: Boost,
    /** Те, що читає гравець. Ім'я в коді може бути іншим: FRENZY → «GEM x2». */
    val label: String,
    val color: Color,
) {
    /** Іконка з атласу ALL. Резолвиться при зверненні — див. шапку файлу. */
    val icon: TextureRegion get() = BoostCatalog.iconOf(boost)

    // Правила — наскрізь із enum, щоб отримувач не тримав два джерела
    val dur       : Float   get() = boost.dur
    val weight    : Int     get() = boost.weight
    val startOffer: Boolean get() = boost.startOffer

    /** Ім'я для аналітики: рівно enum, у нижньому регістрі. */
    val analytics: String get() = boost.name.lowercase()
}

object BoostCatalog {

    // Color.valueOf, а не Color.WHITE навіть для SLOW: WHITE — спільний
    // статичний об'єкт libGDX, і випадкова мутація тінту зачепила б усіх.
    private fun row(boost: Boost, label: String, hex: String) =
        boost to BoostInfo(boost, label, Color.valueOf(hex))

    private val table: Map<Boost, BoostInfo> = mapOf(
        row(Boost.SHIELD, "SHIELD",  "4DD9FF"),
        row(Boost.MAGNET, "MAGNET",  "C07BFF"),
        row(Boost.FRENZY, "GEM x2",  "FFD54A"),
        row(Boost.SLOW,   "SLOW-MO", "FFFFFF"),
        row(Boost.PULSE,  "PULSE",   "FF9F2E"),
    )

    operator fun get(boost: Boost): BoostInfo = table.getValue(boost)

    /** Єдине місце, де ім'я буста зустрічається з іменем файлу в атласі. */
    internal fun iconOf(boost: Boost): TextureRegion = with(gdxGame.assetsAll) {
        when (boost) {
            Boost.SHIELD -> boost_icon_shield
            Boost.MAGNET -> boost_icon_magnet
            Boost.FRENZY -> boost_icon_frenzy
            Boost.SLOW   -> boost_icon_slow_mo
            Boost.PULSE  -> boost_icon_pulse
        }
    }
}

/** Точка входу: boost.info.color, boost.info.icon, boost.info.label. */
val Boost.info: BoostInfo get() = BoostCatalog[this]