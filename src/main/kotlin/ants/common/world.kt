@file:OptIn(ExperimentalStdlibApi::class)

package ants.common

import androidx.compose.ui.geometry.Size
import arrow.optics.optics
import kotlin.random.asKotlinRandom

val random = java.util.Random(System.currentTimeMillis()).asKotlinRandom()

object World {
    val worldSize = Size(1024f, 1024f)

    fun contains(position: WorldPosition): Boolean =
        0f.rangeUntil(worldSize.width).contains(position.x) && 0f.rangeUntil(worldSize.height).contains(position.y)

    fun randomPosition(): WorldPosition =
        WorldPosition(random.nextFloat() * worldSize.width, random.nextFloat() * worldSize.height)
}

data class Turn(val degrees: Float) {
    init {
        require((-360f).rangeUntil(360f).contains(degrees))
    }
}

@optics
data class Direction(val degrees: Float) {
    init {
        require(0f.rangeUntil(360f).contains(degrees))
    }

    fun isHorizontal(): Boolean = degrees == 90f || degrees == 270f
    fun isVertical(): Boolean = degrees == 0f || degrees == 180f

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

@optics
data class WorldPosition(val x: Float, val y: Float) {
    fun move(dx: Float, dy: Float) = WorldPosition(x = x + dx, y = y + dy)

    companion object
}
