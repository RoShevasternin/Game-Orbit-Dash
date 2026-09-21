package com.lewydo.orbitdash.game.actors.ui

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.utils.actor.setColorRGB
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

// ─────────────────────────────────────────────────────────────────────────────
//  ABoostHex — шестикутник буста з його іконкою, у кольорі цього буста.
//
//  Спільна цеглинка на два місця: на полі її носить ABooster, додаючи ореоли
//  й пульс, у HUD — APanelBooster, додаючи смугу часу. Однакова там і там не
//  випадково: гравець мусить упізнати «той самий предмет, що я підібрав»,
//  тому форма, іконка й колір мають бути буквально одним кодом.
//
//  Правил гри не знає: сказали boost — намалювала. Звідси й пакет ui/, поруч
//  з ARoundRect і ABadgeDot: цеглинки вигляду, які можна ставити будь-куди.
//
//  Розмір задає власник — діти заповнюють групу (fillParent). Текстура
//  boost_hex запечена 35×40, тож квад із таким відношенням не спотворює фігуру.
//
//  Ореол тут не живе навмисно: у HUD він був би шумом, а на полі це ДВА
//  кільця з різними фазами пульсу — деталь поля, не самої фігури.
// ─────────────────────────────────────────────────────────────────────────────
class ABoostHex(override val screen: AdvancedScreen) : AConstraintLayout(screen) {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aHex  = Image(gdxGame.assetsMsdf.boost_hex)
    private val aIcon = Image(RunEngine.Boost.MAGNET.info.icon)

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    var boost: RunEngine.Boost = RunEngine.Boost.MAGNET
        set(value) {
            if (value == field) return
            field = value

            applyInfo()
        }

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addHex()
        addIcon()

        applyInfo()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addHex() {
        add(aHex) { fillParent() }
    }

    private fun addIcon() {
        add(aIcon) { fillParent() }
    }

    // ------------------------------------------------------------------------
    // Apply
    // ------------------------------------------------------------------------

    /** Один запит до каталогу: колір і іконка приходять разом, одним записом. */
    private fun applyInfo() {
        val info = boost.info

        aHex.setColorRGB(info.color)
        aIcon.setColorRGB(info.color)

        aIcon.drawable = TextureRegionDrawable(info.icon)
    }

}