package com.lewydo.orbitdash.game.actors.vfx

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.lewydo.orbitdash.game.utils.advanced.AdvancedScreen
import com.lewydo.orbitdash.game.utils.vfx.VfxGroup
import com.lewydo.orbitdash.game.utils.vfx.effects.base.MaskEffect

/**
 * Маскування через alpha-текстуру або атласний регіон.
 *
 * autoCache успадковується (true) — маска кешується коли контент стабільний,
 * авто-перемальовується коли діти рухаються або міняється маска (stateKey).
 *
 * Використання:
 *   AMask(screen, gdxGame.assetsAll.MASK_X)          // Texture (як раніше)
 *   AMask(screen, gdxGame.assetsAll.mask_region)     // TextureRegion з атласу
 *   aMask.maskRegion = atlas.findRegion("mask_x")    // зміна на льоту
 */
class AMask(
    override val screen: AdvancedScreen,
    maskTexture: Texture? = null,
) : VfxGroup(screen) {

    constructor(screen: AdvancedScreen, region: TextureRegion) : this(screen, null) {
        pendingRegion = region
    }

    private val maskEffect = MaskEffect(maskTexture)
    private var pendingRegion: TextureRegion? = null

    /** Маска як Texture (назад-сумісно) */
    var maskTexture: Texture?
        get()      = maskEffect.maskTexture
        set(value) { maskEffect.maskTexture = value; rerenderOnce() }

    /** Маска як TextureRegion (атлас) */
    var maskRegion: TextureRegion?
        get()      = maskEffect.maskRegion
        set(value) { maskEffect.maskRegion = value; rerenderOnce() }

    fun rerenderStaticOnce() { rerenderOnce() }

    override fun addActorsOnGroup() {
        super.addActorsOnGroup()
        pendingRegion?.let { maskEffect.maskRegion = it; pendingRegion = null }
        addEffect(maskEffect)
    }
}