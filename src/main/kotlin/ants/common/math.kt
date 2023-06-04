package ants.common

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
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

fun directionTo(a: WorldPosition, b: WorldPosition): Direction {
    require(a != b) { "can't calculate direction for identical points $a and $b" }
    val (dx, dy) = b - a
    return when {
        // Get more accurate results for the simple cases.
        dx == 0f && dy < 0f -> Direction(0f)
        dx > 0f && dy == 0f -> Direction(90f)
        dx == 0f && dy > 0f -> Direction(180f)
        dx < 0 && dy == 0f -> Direction(270f)
        else -> {
            val rad = atan(dy / dx)
            val deg = Math.toDegrees(rad.toDouble())
            val add = when {
                dx < 0f -> 270f
                else -> 90f
            }
            Direction(deg.toFloat() + add)
        }
    }
}

fun distance(a: WorldPosition, b: WorldPosition): Distance {
    return Distance(hypot(b.x - a.x, b.y - a.y))
}
