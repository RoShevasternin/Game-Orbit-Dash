package com.lewydo.orbitdash.game.actors.brand

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.lewydo.orbitdash.game.actors.layout.autoLayout.AAutoLayout
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.screens.BrandScreen
import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
import com.lewydo.orbitdash.game.utils.gdxGame

class ABrandGroup(override val screen: BrandScreen): AConstraintLayout(screen) {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aVerticalGroup = AAutoLayout(screen,
        direction  = AAutoLayout.Direction.VERTICAL,
        alignMain  = AAutoLayout.AlignMain.CENTER,
        alignCross = AAutoLayout.AlignCross.CENTER
    )

    private val aBrandLogo = ABrandLogo(screen)
    private val aLewydoImg = Image(gdxGame.assetsBrand.lewydo)
    private val aSloganImg = Image(gdxGame.assetsBrand.slogan)
    private val aBrandLine = Image(gdxGame.assetsBrand.brand_line)

    private val listActor = listOf<Actor>(aBrandLogo, aLewydoImg, aSloganImg)

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun addActorsOnGroup() {
        addVerticalGroup()
        addBrandLine()

        setUpActors()
    }

    // ------------------------------------------------------------------------
    // Add Actors
    // ------------------------------------------------------------------------
    private fun addVerticalGroup() {
        add(aVerticalGroup) { fillParent() }

        aBrandLogo.setSize(208f, 208f)
        aLewydoImg.setSize(208f, 74f)
        aSloganImg.setSize(208f, 19f)

        listActor.forEach { aVerticalGroup.add(it) }
    }

    private fun addBrandLine() {
        aBrandLine.setSize(146f, 1f)
        add(aBrandLine) { centerX(aVerticalGroup); topToBottom(aVerticalGroup, 20f) }
    }

    // ------------------------------------------------------------------------
    // Set Up Actors
    // ------------------------------------------------------------------------
    private fun setUpActors() {
        // Всі актори починають невидимими
        aBrandLogo.color.a = 0f
        aLewydoImg.color.a = 0f
        aSloganImg.color.a = 0f
        aBrandLine.color.a = 0f

        aLewydoImg.setOrigin(Align.center)
        aSloganImg.setOrigin(Align.center)
        aBrandLine.setOrigin(Align.center)

        aLewydoImg.setScale(1.06f)
        aSloganImg.setScale(1.06f)
        aBrandLine.setScale(0f, 1f)
    }

    // ------------------------------------------------------------------------
    // Animations
    // ------------------------------------------------------------------------
    fun playIntroAnimation(onComplete: () -> Unit) {
        // 1. Логотип — fade in (0.3 → 1.1)
        aBrandLogo.addAction(
            Actions.sequence(
                Actions.delay(0.3f),
                Actions.fadeIn(0.8f, Interpolation.fade)
            )
        )

        // 2. Lewydo — розфокус → фокус (0.9 → 1.5)
        aLewydoImg.addAction(
            Actions.sequence(
                Actions.delay(0.9f),
                Actions.parallel(
                    Actions.fadeIn(0.6f, Interpolation.fade),
                    Actions.scaleTo(1f, 1f, 0.6f, Interpolation.fade)
                )
            )
        )

        // 3. Slogan — розфокус → фокус (1.3 → 1.9)
        aSloganImg.addAction(
            Actions.sequence(
                Actions.delay(1.3f),
                Actions.parallel(
                    Actions.fadeIn(0.6f, Interpolation.fade),
                    Actions.scaleTo(1f, 1f, 0.6f, Interpolation.fade)
                )
            )
        )

        // 4. Brand line — розкривається (1.7 → 2.4)
        // + callback після завершення
        aBrandLine.addAction(
            Actions.sequence(
                Actions.delay(1.7f),
                Actions.parallel(
                    Actions.fadeIn(0.3f),
                    Actions.scaleTo(1f, 1f, 0.7f, Interpolation.exp5Out)
                ),
                // Пауза щоб гравець побачив повну картину
                Actions.delay(0.6f),
                Actions.run { onComplete() }
            )
        )
    }

}