package com.lewydo.orbitdash.game.manager

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.ParticleEffectLoader
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.g2d.ParticleEffect

class ParticleEffectManager(var assetManager: AssetManager) {

    // ------------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------------

    private val particleLoader = ParticleEffectLoader(InternalFileHandleResolver())

    var loadableParticleEffectList = mutableListOf<ParticleEffectData>()

    /** Атлас за замовчуванням для всіх партиклів. */
    private val defaultAtlas = "atlas/particles.atlas"

    // ------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------

    fun load() {
        assetManager.setLoader(ParticleEffect::class.java, ".p", particleLoader)
        loadableParticleEffectList.onEach { data ->
            val params = ParticleEffectLoader.ParticleEffectParameter().apply {
                // Картинки беруться з АТЛАСУ: регіон шукається за іменем файлу
                // БЕЗ розширення ("circle_cube.png" у .p → регіон "circle_cube").
                // Один атлас = емітери батчаться між собою і зі сценою.
                //
                // КІЛЬКА АТЛАСІВ: кожен ефект може мати свій — просто вкажи
                // atlas = "atlas/xxx.atlas" у його ParticleEffectData.
                // (Загальне правило: ефекти, що грають ОДНОЧАСНО, тримай в
                //  ОДНОМУ атласі — інакше повертаються зайві bind-и.)
                atlasFile = data.atlas ?: defaultAtlas
            }
            assetManager.load(data.path, ParticleEffect::class.java, params)
        }
    }

    fun init() {
        loadableParticleEffectList.onEach { it.effect = assetManager[it.path, ParticleEffect::class.java] }
        loadableParticleEffectList.clear()
    }

    // ------------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------------

    enum class EnumParticleEffect(val data: ParticleEffectData) {
        //LOADER(ParticleEffectData("particle/loader/loader.p")),

        CONFETTI(ParticleEffectData("particle/confetti/confetti.p")),

        // Приклад окремого атласу для важкого ефекту:
        // BOSS(ParticleEffectData("particle/boss/boss.p", atlas = "atlas/particles_boss.atlas")),
    }

    // ------------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------------

    data class ParticleEffectData(
        val path : String,
        /** null = defaultAtlas. Вкажи, якщо цей ефект в іншому атласі. */
        val atlas: String? = null,
    ) {
        lateinit var effect: ParticleEffect
    }

}