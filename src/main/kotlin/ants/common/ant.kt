package ants.common

import arrow.optics.optics
import kotlinx.collections.immutable.PersistentSet

data class AntId(val id: Int)

enum class AntState {
    INSIDE,
    OUTSIDE,
    BACK_TO_NEST;

    fun isVisible(): Boolean = this != INSIDE
}

@optics
data class Ant(
    val id: AntId,
    val state: AntState,
    val position: WorldPosition,
    val direction: Direction,
    val visitedPheromones: PersistentSet<PheromoneId>,
) {
    companion object
}
