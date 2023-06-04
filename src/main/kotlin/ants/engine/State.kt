package ants.engine

import ants.common.Ant
import ants.common.AntId
import ants.common.Pheromone
import ants.common.PheromoneId
import ants.common.RNG
import kotlinx.collections.immutable.PersistentMap

// NOTE: @optics does not work for this data class but produces a compilation error:
// "Unresolved reference: engine"
data class State(
    val ants: PersistentMap<AntId, Ant>,
    val pheromones: PersistentMap<PheromoneId, Pheromone>,
    val rng: RNG
) {
    companion object
}
