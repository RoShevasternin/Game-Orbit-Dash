package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.math.Vector2

class SizeScaler(
    private val axis      : Axis,
    private val designSize: Float
) {

    private var scale = 1f

    fun calculateScale(actualSize: Vector2) {
        val axisSize = when(axis) {
            Axis.X -> actualSize.x
            Axis.Y -> actualSize.y
        }
        scale = designSize.divOr(axisSize, 1f)
    }

    fun toActual(designValue: Vector2): Vector2 {
        return designValue.divOr(scale, 1f)
    }

    fun toDesign(actualValue: Vector2): Vector2 {
        return actualValue.scl(scale)
    }

    fun toActual(designValue: Float): Float {
        return designValue.divOr(scale, 1f)
    }

    fun toDesign(actualValue: Float): Float {
        return (actualValue * scale)
    }

    enum class Axis { X, Y }

}