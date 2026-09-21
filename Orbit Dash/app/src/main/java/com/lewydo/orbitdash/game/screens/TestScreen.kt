package com.lewydo.orbitdash.game.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lewydo.orbitdash.engine.RunEngine
import com.lewydo.orbitdash.game.content.info
import com.lewydo.orbitdash.game.actors.background.AStarField
import com.lewydo.orbitdash.game.actors.debug.ADebugHud
import com.lewydo.orbitdash.game.actors.debug.addDebugHud
import com.lewydo.orbitdash.game.actors.layout.constraintLayout.AConstraintLayout
import com.lewydo.orbitdash.game.actors.progress.ABarProgress
import com.lewydo.orbitdash.game.actors.progress.AMaskProgress
import com.lewydo.orbitdash.game.actors.vfx.ABlurBack
import com.lewydo.orbitdash.game.utils.Block
import com.lewydo.orbitdash.game.utils.TIME_ANIM_SCREEN
import com.lewydo.orbitdash.game.utils.actor.addAndFillActor
import com.lewydo.orbitdash.game.utils.actor.animDelay
import com.lewydo.orbitdash.game.utils.actor.disable
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.gdxGame
import com.lewydo.orbitdash.game.utils.vfx.VfxImage
import com.lewydo.orbitdash.game.utils.vfx.VfxTexture
import com.lewydo.orbitdash.game.utils.vfx.effects.base.RoundRectEffect

class TestScreen : AdvancedScreen() {

    // ------------------------------------------------------------------------
    // Actors
    // ------------------------------------------------------------------------
    private val aStarField by lazy { AStarField(this) }

    // ------------------------------------------------------------------------
    // Field
    // ------------------------------------------------------------------------
    /** Час демо-смуги, секунди по колу. У грі час веде рушій. */
    private var barTime = 0f

    /** Маска для AMaskProgress: проста форма — печеться шейдером, ассет не потрібен. */
    private val capsuleTex = VfxTexture(268f, 25f, shape = RoundRectEffect().apply { radius = 10f; fillAlpha = 0.10f; strokeAlpha = 1f; strokeWidth = 5f })

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------
    override fun show() {
        super.show()
        animShowScreen()

        disposableSet.add(capsuleTex)
    }

    override fun Group.addActorsOnStageUI() {
        addAndFillActor(aStarField)
    }

    override fun AConstraintLayout.addActorsOnRootConstraintLayout() {
        addMsdfSandbox()
        addDebugHud(ADebugHud(this@TestScreen))
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val v = stageUI.screenToStageCoordinates(Vector2(screenX.toFloat(), screenY.toFloat()))
        aStarField.animRippleAt(v.x, v.y)
        return false
    }

    // ------------------------------------------------------------------------
    // Screen Animations
    // ------------------------------------------------------------------------
    override fun animShowScreen(blockEnd: Block) {
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    override fun animHideScreen(blockEnd: Block) {
        rootConstraintLayout.disable()
        rootConstraintLayout.animDelay(TIME_ANIM_SCREEN) { blockEnd() }
    }

    // ------------------------------------------------------------------------
    // MSDF sandbox v2 — усі способи використання на одному екрані (ТИМЧАСОВЕ)
    // ------------------------------------------------------------------------

    private fun AConstraintLayout.addMsdfSandbox() {
        addProgressStand()
        addMaskProgressStand()
    }

    // ── AMaskProgress: прогрес будь-якої форми (ТИМЧАСОВЕ) ──────────────────
    //
    // Те саме, що смуга вище, але форму задає МАСКА, а не математика. Маска —
    // звичайна текстура: тут капсула, запечена VfxTexture(RoundRectEffect), тож
    // ассет не потрібен. Замість неї може бути будь-що, хоч фігура з дірками:
    // де в масці прозоро — там прозоро й у прогресі.
    private fun AConstraintLayout.addMaskProgressStand() {
        val bar = AMaskProgress(this@TestScreen, mask = capsuleTex.region)

        bar.fill       = TextureRegion(gdxGame.assetsAll.test_progress) //TextureRegion(makeGradient())   // картинка заповнення
        bar.trackAlpha = 0.2f                            // фон — колір, не картинка
        bar.setSize(268f, 25f)
        add(bar) { centerX(); topToTop(margin = 460f) }
        bar.fx.slides = true

        // Той самий цикл, що й у смуги: рухається лише frac
        bar.addAction(Actions.forever(Actions.run { bar.frac = barTime / 6f }))
    }

    // ── ОДНА ПРОГРЕС-СМУГА: як нею користуватись (ТИМЧАСОВЕ) ────────────────
    //
    // Відкрити екран: LoaderScreen.NEXT_SCREEN_NAME → TestScreen::class.java.name.
    //
    // Смуга — один актор ABarProgress: форму рахує шейдер, тож ні текстури,
    // ні окремого ефекту тримати не треба. Далі в її житті міняється РІВНО
    // ОДНЕ ЧИСЛО: bar.frac. Що брати в інших випадках — docs/progress.md.
    private fun AConstraintLayout.addProgressStand() {

        // 1. АКТОР І ВИГЛЯД — одне місце.
        val bar = ABarProgress(this@TestScreen).apply {
            radius     = 4f                                   // половина висоти = капсула
            trackAlpha = 0.2f                                 // доріжка: біла, ледь помітна
            fillColor.set(RunEngine.Boost.MAGNET.info.color)  // заповнення — колір MAGNET
        }

        // 2. РОЗМІР І МІСЦЕ. Розмір актора = розмір смуги у world-юнітах.
        bar.setSize(200f, 8f)
        add(bar) { center() }

        // Картинка замість кольору — один рядок, текстурний режим вмикається сам
        bar.picture = TextureRegion(makeGradient())
        bar.fx.texturedTrackAlpha = 0.2f
        bar.fx.textureSlides      = true

        // 3. ЖИТТЯ. frac — єдине, що рухається. Тут — цикл 1 → 0 за 6 секунд
        //    і секунда паузи на нулі, щоб було видно, як смуга гасне в крапку.
        //    У грі замість цього стоїть один рядок у syncFrom():
        //        bar.frac = engine.boostFrac
        bar.addAction(Actions.forever(Actions.run {
            barTime  = (barTime + Gdx.graphics.deltaTime) % 7f
            bar.frac = if (barTime < 6f) barTime / 6f else 0f
        }))

        // 4. ТЕКСТУРНИЙ РЕЖИМ — коли дизайнер дав картинку смуги замість кольору.
        //    Розкоментуй три рядки: заповнення стане картинкою, яку прогрес
        //    ПРОЯВЛЯЄ зліва (картинка стоїть на місці, не тягнеться).
        //
        // bar.picture = atlas.progress_fill
        //
        //    Доріжку теж картинкою (видно, «що проявиться»):
        // bar.fx.texturedTrackAlpha = 0.2f
        //
        //    А якщо картинка має ЇХАТИ, а не проявлятись (як під маскою, коли
        //    рухаєш x), — один прапорець; доріжку тоді лиши кольоровою:
        // bar.fx.textureSlides = true
    }

    /** Тестова картинка: райдужний градієнт 256×1. У грі замість неї — регіон з атласу. */
    private fun makeGradient(): Texture {
        val pm  = Pixmap(256, 1, Pixmap.Format.RGBA8888)
        val col = Color()
        for (x in 0 until 256) {
            col.fromHsv(x / 256f * 300f, 0.85f, 1f)
            col.a = 1f
            pm.drawPixel(x, 0, Color.rgba8888(col))
        }
        return Texture(pm).also {
            it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            pm.dispose()
        }
    }

}
