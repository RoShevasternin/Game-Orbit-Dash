package com.lewydo.orbitdash.game.actors.button//package com.lewydo.orbitdash.game.actors.button
//
//import com.badlogic.gdx.scenes.scene2d.Actor
//import com.badlogic.gdx.scenes.scene2d.InputEvent
//import com.badlogic.gdx.scenes.scene2d.InputListener
//import com.badlogic.gdx.scenes.scene2d.Touchable
//import com.badlogic.gdx.scenes.scene2d.ui.Image
//import com.badlogic.gdx.scenes.scene2d.utils.Drawable
//import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
//import com.lewydo.orbitdash.game.manager.util.SoundUtil
//import com.lewydo.orbitdash.game.utils.TextureEmpty
//import com.lewydo.orbitdash.game.utils.actor.addAndFillActors
//import com.lewydo.orbitdash.game.utils.actor.animHide
//import com.lewydo.orbitdash.game.utils.actor.animShow
//import com.lewydo.orbitdash.game.utils.advanced.AdvancedGroup
//import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
//import com.lewydo.orbitdash.game.utils.gdxGame
//import com.lewydo.orbitdash.game.utils.region
//
//open class AButton(
//    override val screen: AdvancedScreen,
//    type: Type
//) : AdvancedGroup() {
//
//    private val defaultImage  = Image(getStyleByType(type).default)
//    private val pressedImage  = Image(getStyleByType(type).pressed).apply { color.a = 0f }
//    private val disabledImage = Image(getStyleByType(type).disabled).apply { color.a = 0f }
//
//    private var onClickBlock: () -> Unit = { }
//
//    var touchDownBlock   : AButton.(x: Float, y: Float) -> Unit = { _, _ -> }
//    var touchDraggedBlock: AButton.(x: Float, y: Float) -> Unit = { _, _ -> }
//    var touchUpBlock     : AButton.(x: Float, y: Float) -> Unit = { _, _ -> }
//
//    private var clickSound: SoundUtil.AdvancedSound? = null
//    private var area: Actor? = null
//
//    private var animShowTime = 0.050f
//    private var animHideTime = 0.400f
//
//    var isAnimState = true
//
//    override fun addActorsOnGroup() {
//        addAndFillActors(getActors())
//        addListener(getListener())
//    }
//
//
//    private fun getActors() = listOf<Actor>(
//        defaultImage,
//        pressedImage,
//        disabledImage,
//    )
//
//
//
//    private fun getListener() = object : InputListener() {
//        var isWithin     = false
//        var isWithinArea = false
//
//        private var activePointer = -1
//
//        override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
//            if (activePointer != -1) return false  // ← вже є активний палець
//            activePointer = pointer
//
//            touchDownBlock(x, y)
//            touchDragged(event, x, y, pointer)
//            clickSound?.let { gdxGame.soundUtil.play(it) }
//            event?.stop()
//            return true
//        }
//
//        override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
//            if (pointer != activePointer) return
//            touchDraggedBlock(x, y)
//
//            isWithin = x in 0f..width && y in 0f..height
//            area?.let { isWithinArea = x in 0f..it.width && y in 0f..it.height }
//
//            if (isWithin || isWithinArea) press() else unpress()
//        }
//
//        override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
//            if (pointer != activePointer) return
//            activePointer = -1
//
//            touchUpBlock(x, y)
//
//            if (isWithin || isWithinArea) {
//                unpress()
//                onClickBlock()
//            }
//        }
//    }
//
//    fun press() {
//        if (isAnimState) {
//            defaultImage.clearActions()
//            pressedImage.clearActions()
//
//            defaultImage.animHide(animShowTime)
//            pressedImage.animShow(animShowTime)
//        } else {
//            defaultImage.color.a = 0f
//            pressedImage.color.a = 1f
//        }
//    }
//
//    fun unpress() {
//        if (isAnimState) {
//            defaultImage.clearActions()
//            pressedImage.clearActions()
//
//            defaultImage.animShow(animHideTime)
//            pressedImage.animHide(animHideTime)
//        } else {
//            defaultImage.color.a = 1f
//            pressedImage.color.a = 0f
//        }
//    }
//
//    open fun disable(useDisabledStyle: Boolean = true) {
//        touchable = Touchable.disabled
//
//        if (useDisabledStyle) {
//            if (isAnimState) {
//                defaultImage.clearActions()
//                pressedImage.clearActions()
//                disabledImage.clearActions()
//
//                defaultImage.animHide()
//                pressedImage.animHide()
//                disabledImage.animShow()
//            } else {
//                defaultImage.color.a  = 0f
//                pressedImage.color.a  = 0f
//                disabledImage.color.a = 1f
//            }
//        }
//
//    }
//
//    open fun enable() {
//        touchable = Touchable.enabled
//
//        if (isAnimState) {
//            defaultImage.clearActions()
//            pressedImage.clearActions()
//            disabledImage.clearActions()
//
//            defaultImage.animShow()
//            pressedImage.animHide()
//            disabledImage.animHide()
//        } else {
//            defaultImage.color.a  = 1f
//            pressedImage.color.a  = 0f
//            disabledImage.color.a = 0f
//        }
//
//    }
//
//    fun pressAndDisable(useDisabledStyle: Boolean = false) {
//        press()
//        disable(useDisabledStyle)
//    }
//
//    fun unpressAndEnable() {
//        unpress()
//        enable()
//    }
//
//    fun setStyle(style: AButtonStyle) {
//        defaultImage.drawable  = style.default
//        pressedImage.drawable  = style.pressed
//        disabledImage.drawable = style.disabled
//    }
//
//    fun setOnClickListener(sound: SoundUtil.AdvancedSound? = gdxGame.soundUtil.CLICK, block: () -> Unit) {
//        clickSound   = sound
//        onClickBlock = block
//    }
//
//    fun addArea(actor: Actor) {
//        area = actor
//        actor.addListener(getListener())
//    }
//
//    private fun getStyleByType(type: Type) = when(type) {
//        Type.NONE -> AButtonStyle(
//            default = TextureRegionDrawable(TextureEmpty.region),
//            pressed = TextureRegionDrawable(TextureEmpty.region),
//            disabled = TextureRegionDrawable(TextureEmpty.region),
//        )
//        Type.SETTINGS -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.settings_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.settings_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.settings_press),
//        )
//        Type.BUY -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.buy_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.buy_def),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.buy_dis),
//        )
//        Type.COLLECT -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.collect_frame_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.collect_frame_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.collect_frame_press),
//        )
//        Type.BACK -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.back_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.back_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.back_press),
//        )
//        Type.MENU_ITEM -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.menu_item_section_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.menu_item_section_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.menu_item_section_press),
//        )
//        Type.MENU_RESET_GAME -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.reset_game_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.reset_game_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.reset_game_press),
//        )
//        Type.MENU_CLOSE -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.close_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.close_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.close_press),
//        )
//        Type.YES -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.yes_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.yes_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.yes_press),
//        )
//        Type.NO -> AButtonStyle(
//            default = TextureRegionDrawable(gdxGame.assetsAll.no_def),
//            pressed = TextureRegionDrawable(gdxGame.assetsAll.no_press),
//            disabled = TextureRegionDrawable(gdxGame.assetsAll.no_press),
//        )
//    }
//
//    // ---------------------------------------------------
//    // Style
//    // ---------------------------------------------------
//
//    data class AButtonStyle(
//        var default: Drawable,
//        var pressed: Drawable,
//        var disabled: Drawable,
//    )
//
//    enum class Type {
//        NONE, SETTINGS, BUY, COLLECT, BACK,
//        MENU_ITEM, MENU_RESET_GAME, MENU_CLOSE,
//        YES, NO,
//
//
//    }
//
//}