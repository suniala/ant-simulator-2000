package ants.engine

import ants.common.Ant
import ants.common.AntId
import kotlinx.collections.immutable.PersistentMap

// NOTE: @optics does not work for this data class but produces a compilation error:
// "Type mismatch: inferred type is Unit but WorldState was expected"
data class WorldState(val ants: PersistentMap<AntId, Ant>) {
    companion object
}
