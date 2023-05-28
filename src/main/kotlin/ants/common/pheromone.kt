package ants.common

import arrow.optics.optics

data class PheromoneId(val id: Int)

@optics
data class PheromoneStrength(val strength: Float) {
    operator fun times(multiplier: Float): PheromoneStrength = PheromoneStrength(strength * multiplier)

    init {
        require((0f..1f).contains(strength))
    }

    companion object {
        fun fullStrength() = PheromoneStrength(1f)
    }
}

@optics
data class Pheromone(val id: PheromoneId, val strength: PheromoneStrength, val position: WorldPosition) {
    fun isEffective(): Boolean = strength.strength >= 0.1f

    companion object
}
