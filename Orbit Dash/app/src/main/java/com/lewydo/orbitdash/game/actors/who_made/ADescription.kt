package com.lewydo.orbitdash.game.actors.who_made

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.lewydo.orbitdash.BuildConfig
import com.lewydo.orbitdash.R
import com.lewydo.orbitdash.game.actors.AScrollPane
import com.lewydo.orbitdash.game.actors.ATmpGroup
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.utils.actor.addActors
import com.lewydo.orbitdash.game.utils.actor.setOnClickListener
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame

class ADescription(override val screen: AdvancedScreen): AdvancedGroup() {

    private val appName    = gdxGame.activity.getString(R.string.app_name)
    private val appVersion = BuildConfig.VERSION_NAME
    private val subject    = "$appName | version: $appVersion"

    private val veldanEmail     = "veldan1202@gmail.com"
    private val veldanTelegram  = "vel_dan"
    private val veldanInstagram = "___vel__dan___"

    private val lilyEmail     = "Lilyadesign05@gmail.com"
    private val lilyTelegram  = "Over_lilya"
    private val lilyInstagram = "Lilya.design"

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aVerticalGroup = AAutoLayout(
        screen     = screen,
        direction  = AAutoLayout.Direction.VERTICAL,
        gapMain    = 126f,
        alignCross = AAutoLayout.AlignCross.CENTER,
        sizingH    = AAutoLayout.Sizing.HUG
    )
    private val aBackgroundImg = Image(gdxGame.assetsAll.DIALOG_CLEAR_GRID)
    private val aScrollPane    = AScrollPane(aVerticalGroup)

    private val aVeldanGroup          = ATmpGroup(screen)
    private val aVeldanDescriptionImg = Image(gdxGame.assetsAll.DIALOG_CLEAR_GRID)

    private val aLilyGroup          = ATmpGroup(screen)
    private val aLilyDescriptionImg = Image(gdxGame.assetsAll.DIALOG_CLEAR_GRID)

    private val aSeparatorImg = Image(gdxGame.assetsAll.DIALOG_CLEAR_GRID)

    private val aSpaceTop    = Actor()
    private val aSpaceBottom = Actor()

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addAndFillActor(aBackgroundImg)
        addAndFillActor(aScrollPane)
        setUpVerticalGroup()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------

    private fun setUpVerticalGroup() {
        aVerticalGroup.width  = width
        aVerticalGroup.height = 1f

        setUpVeldanGroup()
        setUpLilyGroup()
        aSeparatorImg.setSize(1657f, 3f)

        aSpaceTop.setSize(126f, 126f)
        aSpaceBottom.setSize(126f, 126f)

        aVerticalGroup.add(aSpaceTop)

        aVerticalGroup.add(aVeldanGroup)
        aVerticalGroup.add(aSeparatorImg)
        aVerticalGroup.add(aLilyGroup)

        aVerticalGroup.add(aSpaceBottom)

    }

    private fun setUpVeldanGroup() {
        aVeldanGroup.setSize(1657f, 1835f)
        aVeldanGroup.addAndFillActor(aVeldanDescriptionImg)

        val aInstagramBtn = Actor()
        val aTelegramBtn  = Actor()
        val aEmailBtn     = Actor()

        aVeldanGroup.addActors(
            aInstagramBtn,
            aTelegramBtn,
            aEmailBtn,
        )

        aInstagramBtn.setBounds(0f, 241f, 1541f, 112f)
        aTelegramBtn .setBounds(0f, 129f, 1656f, 112f)
        aEmailBtn    .setBounds(0f, 17f, 1541f, 112f)

        aInstagramBtn.setOnClickListener() { gdxGame.activity.openInstagram(veldanInstagram) }
        aTelegramBtn.setOnClickListener() { gdxGame.activity.openTelegram(veldanTelegram) }
        aEmailBtn.setOnClickListener() { gdxGame.activity.openEmail(veldanEmail, subject) }
    }

    private fun setUpLilyGroup() {
        aLilyGroup.setSize(1657f, 1611f)
        aLilyGroup.addAndFillActor(aLilyDescriptionImg)

        val aInstagramBtn = Actor()
        val aTelegramBtn  = Actor()
        val aEmailBtn     = Actor()

        aLilyGroup.addActors(
            aInstagramBtn,
            aTelegramBtn,
            aEmailBtn,
        )

        aInstagramBtn.setBounds(0f, 224f, 1541f, 112f)
        aTelegramBtn .setBounds(0f, 112f, 1656f, 112f)
        aEmailBtn    .setBounds(0f, 0f, 1541f, 112f)

        aInstagramBtn.setOnClickListener() { gdxGame.activity.openInstagram(lilyInstagram) }
        aTelegramBtn.setOnClickListener() { gdxGame.activity.openTelegram(lilyTelegram) }
        aEmailBtn.setOnClickListener() { gdxGame.activity.openEmail(lilyEmail, subject) }
    }

}