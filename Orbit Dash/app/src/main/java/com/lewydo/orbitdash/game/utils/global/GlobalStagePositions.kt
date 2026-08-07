package com.lewydo.orbitdash.game.utils.global

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor

object GlobalStagePositions {

    enum class Key { COIN, }

    private val providers = HashMap<Key, () -> Vector2>()

    fun register(key: Key, provider: () -> Vector2) {
        providers[key] = provider
    }

    // Зручний хелпер для Actor
    fun register(key: Key, actor: Actor, offsetX: Float = 0f, offsetY: Float = 0f) {
        providers[key] = {
            if (actor.stage != null)
                actor.localToStageCoordinates(Vector2(offsetX, offsetY))
            else
                Vector2.Zero.cpy()
        }
    }

    fun get(key: Key): Vector2 = providers[key]?.invoke() ?: Vector2.Zero.cpy()

    fun unregister(key: Key) { providers.remove(key) }

    fun clear() { providers.clear() }
}