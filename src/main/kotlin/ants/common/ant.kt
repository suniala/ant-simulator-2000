package ants.common

import arrow.optics.optics

data class AntId(val id: Int)

@optics
data class Ant(val id: AntId, val position: WorldPosition, val direction: Direction) {
    companion object
}
