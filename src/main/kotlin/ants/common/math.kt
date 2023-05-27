package ants.common

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

fun calculateMovement(direction: Direction, moveBy: Distance): PositionDelta {
    val degrees = direction.degrees

    val dx: Float = when {
        degrees == 0f || degrees == 180f -> 0f
        degrees < 180f -> moveBy.raw * cos(Math.toRadians(90.0 - degrees).toFloat())
        else -> moveBy.raw * cos(Math.toRadians(abs(270.0 - degrees)).toFloat())
    }
    val signX = if (direction.degrees < 180) 1 else -1

    val dy: Float = when {
        degrees == 90f || degrees == 270f -> 0f
        degrees < 90f || degrees > 270f -> moveBy.raw * sin(Math.toRadians(90.0 - degrees).toFloat())
        else -> moveBy.raw * sin(Math.toRadians(abs(270.0 - degrees)).toFloat())
    }
    val signY = if (direction.degrees > 90 && direction.degrees < 270) 1 else -1

    return PositionDelta(signX * dx, signY * dy)
}
