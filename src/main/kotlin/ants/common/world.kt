@file:OptIn(ExperimentalStdlibApi::class)

package ants.common

import androidx.compose.ui.geometry.Size
import arrow.optics.optics
import kotlin.random.asKotlinRandom

val random = java.util.Random(System.currentTimeMillis()).asKotlinRandom()

object World {
    val worldSize = Size(1024f, 1024f)

    @ExperimentalStdlibApi
    fun contains(position: WorldPosition): Boolean =
        0f.rangeUntil(worldSize.width).contains(position.x) && 0f.rangeUntil(worldSize.height).contains(position.y)

    fun randomPosition(): WorldPosition =
        WorldPosition(random.nextFloat() * worldSize.width, random.nextFloat() * worldSize.height)

    fun middlePosition(): WorldPosition =
        WorldPosition(worldSize.width / 2, worldSize.height / 2)
}

data class Turn(val degrees: Float) {
    init {
        require((-360f).rangeUntil(360f).contains(degrees))
    }
}

@optics
data class Direction(val degrees: Float) {
    init {
        require(0f.rangeUntil(360f).contains(degrees)) { "$degrees is not valid" }
    }

    fun turn(t: Turn): Direction {
        val newDegrees = degrees + t.degrees

        return when {
            newDegrees < 0 -> Direction(newDegrees + 360)
            newDegrees >= 360 -> Direction(newDegrees - 360)
            else -> Direction(newDegrees)
        }
    }

    companion object {
        fun randomDirection(): Direction = Direction(random.nextFloat() * 360)
    }
}

data class Distance(val raw: Float) : Comparable<Distance> {
    operator fun minus(other: Distance): Distance = Distance(raw - other.raw)
    infix fun le(other: Distance): Boolean = raw <= other.raw

    init {
        require(raw >= 0)
    }

    override fun compareTo(other: Distance): Int = raw.compareTo(other.raw)
}

data class PositionDelta(val dx: Float, val dy: Float)

@optics
data class WorldPosition(val x: Float, val y: Float) {
    fun move(d: PositionDelta) = WorldPosition(x = x + d.dx, y = y + d.dy)

    operator fun minus(b: WorldPosition): PositionDelta = PositionDelta(x - b.x, y - b.y)
    operator fun plus(b: PositionDelta) = WorldPosition(x + b.dx, y + b.dy)

    companion object
}
