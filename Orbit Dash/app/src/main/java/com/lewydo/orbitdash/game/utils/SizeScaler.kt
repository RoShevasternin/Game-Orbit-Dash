package com.lewydo.orbitdash.game.utils

import com.badlogic.gdx.math.Vector2

// ----------------------------------------------------------------------------
//  Локальний «viewport» компонента: перетворення дизайн-координат макета
//  в актуальні юніти сцени. Дизайн-розмір фіксований (число з Figma),
//  актуальний приходить із лейауту в sizeChanged().
//
//  Актор і scale тут ні до чого: Actor.scale не бачить лейаут і вимагає
//  transform-флашів. Це чиста арифметика розміщення.
// ----------------------------------------------------------------------------
class SizeScaler(
    private val axis      : Axis,
    private val designSize: Float,
) {

    /** actual / design. 1f, поки розмір іще не прийшов. */
    var factor = 1f
        private set

    fun calculateScale(actualSize: Vector2) {
        val axisSize = when (axis) {
            Axis.X -> actualSize.x
            Axis.Y -> actualSize.y
        }
        // Захист лише від нульового ДИЗАЙН-розміру: нульовий actual легітимний
        // на першому кадрі, поки лейаут ще не розклався.
        factor = if (designSize > 0f) axisSize / designSize else 1f
    }

    // ------------------------------------------------------------------------
    //  Float: нуль лишається нулем — жодних divOr.
    // ------------------------------------------------------------------------
    fun toActual(designValue: Float): Float = designValue * factor
    fun toDesign(actualValue: Float): Float = if (factor > 0f) actualValue / factor else actualValue

    // ------------------------------------------------------------------------
    //  Vector2: ЗАВЖДИ копія. Мутація аргументу — баг, що спливає далеко
    //  від місця виклику. Ціна копії — копійки проти дня дебагу.
    // ------------------------------------------------------------------------
    fun toActual(designValue: Vector2): Vector2 =
        Vector2(toActual(designValue.x), toActual(designValue.y))

    fun toDesign(actualValue: Vector2): Vector2 =
        Vector2(toDesign(actualValue.x), toDesign(actualValue.y))

    enum class Axis { X, Y }
}