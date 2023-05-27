package ants.engine

import ants.common.Ant
import ants.common.AntId
import kotlinx.collections.immutable.PersistentMap

// NOTE: @optics does not work for this data class but produces a compilation error:
// "Unresolved reference: engine"
data class State(val ants: PersistentMap<AntId, Ant>) {
    companion object
}
