package ants.engine

import ants.common.Ant
import ants.common.AntId
import ants.common.AntState
import ants.common.Direction
import ants.common.Distance
import ants.common.Pheromone
import ants.common.PheromoneId
import ants.common.PheromoneStrength
import ants.common.PositionDelta
import ants.common.Turn
import ants.common.World
import kotlinx.collections.immutable.persistentSetOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EngineTest {
    @Test
    fun `ant moves straight`() {
        val ant = Ant(AntId(1), AntState.OUTSIDE, World.middlePosition(), Direction(0f), persistentSetOf())
        val params = AntParams(antTurnsProbability = 0f)

        val (nextAnt, _, _, _) = doAnt(
            params,
            constantRng(1),
            ant,
            emptySet(),
            AntWorkerPrivateState(Distance(0f))
        )
        assertEquals(
            World.middlePosition() + PositionDelta(0f, -params.antMovePerIteration.raw),
            nextAnt.position
        )
    }

    @Test
    fun `ant encounters edge of the world`() {
        val ant = Ant(AntId(1), AntState.OUTSIDE, World.middlePosition().copy(y = 1f), Direction(0f), persistentSetOf())
        val params = AntParams(antTurnsProbability = 0f, antMovePerIteration = Distance(2f))

        val (nextAnt, _, _, _) = doAnt(
            params,
            constantRng(1),
            ant,
            emptySet(),
            AntWorkerPrivateState(Distance(0f))
        )
        assertEquals(AntState.BACK_TO_NEST, nextAnt.state)
        assertEquals(ant.position, nextAnt.position)
        assertEquals(ant.direction.turn(Turn(180f)), nextAnt.direction)
    }

    @Test
    fun `ant on the way back`() {
        val ant = Ant(AntId(1), AntState.BACK_TO_NEST, World.middlePosition(), Direction(180f), persistentSetOf())
        val params = AntParams(antMovePerIteration = Distance(2f))

        val surroundings = persistentSetOf(
            Pheromone(
                PheromoneId(1),
                PheromoneStrength.fullStrength(),
                ant.position.plus(PositionDelta(1f, 1f))
            ),
            Pheromone(
                PheromoneId(2),
                PheromoneStrength.fullStrength(),
                ant.position.plus(PositionDelta(1f, 3f))
            ),
        )
        val (nextAnt, _, _, _) = doAnt(
            params,
            constantRng(1),
            ant,
            surroundings,
            AntWorkerPrivateState(Distance(0f))
        )
        assertEquals(AntState.BACK_TO_NEST, nextAnt.state)
        assertEquals(Direction(degrees = 135f), nextAnt.direction)
        assertEquals(persistentSetOf(PheromoneId(1)), nextAnt.visitedPheromones)

        val (nextAnt2, _, _, _) = doAnt(
            params,
            constantRng(1),
            nextAnt,
            surroundings,
            AntWorkerPrivateState(Distance(0f))
        )
        assertEquals(AntState.BACK_TO_NEST, nextAnt2.state)
        assertEquals(Direction(degrees = 194.63757f), nextAnt2.direction)
    }
}
