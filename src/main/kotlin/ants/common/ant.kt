package ants.common

import arrow.optics.optics

data class AntId(val id: Int)

enum class AntState {
    INSIDE,
    OUTSIDE;

    fun isVisible(): Boolean = this == OUTSIDE
}

@optics
data class Ant(val id: AntId, val state: AntState, val position: WorldPosition, val direction: Direction) {
    companion object
}
