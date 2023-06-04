package ants.engine

import ants.common.Ant
import ants.common.AntId
import ants.common.AntState
import ants.common.Direction
import ants.common.Distance
import ants.common.PositionDelta
import ants.common.World
import ants.common.WorldPosition
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EngineTest {
    @Test
    fun foo() {
        val ant = Ant(AntId(1), AntState.OUTSIDE, World.middlePosition(), Direction(0f), persistentSetOf())

        // TODO: should take random turning into account
        val (nextAnt, dropPheromone, nextPrivateState) = doAnt(ant, emptySet(), AntWorkerPrivateState(Distance(0f)))
        assertEquals(
            World.middlePosition() + PositionDelta(0f, -Params.antMovePerIteration.raw),
            nextAnt.position
        )

    }
}
