@file:OptIn(ExperimentalStdlibApi::class)

package ants.engine

import ants.common.Ant
import ants.common.AntId
import ants.common.AntState
import ants.common.Direction
import ants.common.Distance
import ants.common.Turn
import ants.common.World
import ants.common.calculateMovement
import ants.common.direction
import ants.common.position
import ants.common.random
import ants.common.state
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class StateMsg

class NextAntStateMsg(val antId: AntId, val f: (Ant) -> Ant) : StateMsg()

class GetStateMsg(val response: CompletableDeferred<State>) : StateMsg()

@OptIn(ObsoleteCoroutinesApi::class)
private fun CoroutineScope.stateActor(ants: PersistentMap<AntId, Ant>) = actor<StateMsg> {
    var state = State(ants = ants)

    for (msg in channel) {
        when (msg) {
            is NextAntStateMsg -> {
                val ant = checkNotNull(state.ants[msg.antId])
                val nextAnt = msg.f(ant)
                state = state.copy(ants = state.ants.put(ant.id, nextAnt))
            }

            is GetStateMsg -> msg.response.complete(state)
        }
    }
}

suspend fun createEngine(scope: CoroutineScope): SendChannel<StateMsg> =
    scope.run {
        val ants = (1..100)
            .map { Ant(AntId(it), AntState.INSIDE, World.middlePosition(), Direction.randomDirection()) }
            .associateBy { it.id }
            .toPersistentHashMap()

        stateActor(ants).also { stateActor ->
            launch {
                stateActor.let { stateChannel ->
                    val initialResponse = CompletableDeferred<State>()
                    stateChannel.send(GetStateMsg(initialResponse))

                    initialResponse.await().let { initialState ->
                        withContext(Dispatchers.Default) {
                            initialState.ants.keys.forEach { antId ->
                                launch { antWorker(stateChannel, antId) }
                            }
                        }
                    }
                }
            }
        }
    }

private fun CoroutineScope.antWorker(
    stateChannel: SendChannel<StateMsg>,
    antId: AntId,
) {
    launch {
        while (true) {
            delay(random.nextLong(1, 10) * 2)

            stateChannel.send(NextAntStateMsg(antId) { ant ->
                when (ant.state) {
                    AntState.INSIDE -> {
                        val moveOutside = random.nextLong(1, 1000) <= 1
                        if (moveOutside) Ant.state.modify(ant) { AntState.OUTSIDE }
                        else ant
                    }

                    AntState.OUTSIDE -> {
                        val moveBy = Distance(1f)
                        val turnOptions = sequence {
                            // First options is to continue straight or turn slightly
                            yield(
                                if (random.nextLong(0, 20) < 1)
                                    Turn((random.nextFloat() - 0.5f) * 10)
                                else Turn(0f)
                            )
                            // If that option is not viable, consider these random turns either left
                            // or right until a suitable turn is found.
                            // NOTE: The point of using this range is that it provides a limited and
                            // exhaustive set of options. If this set does not solve the turning
                            // problem then there is a logical error somewhere, and we want the
                            // program to fail with an error.
                            (1..18)
                                // Shuffle the options, otherwise the ants will make the smallest
                                // possible turns and end up circling the edges of the world.
                                .shuffled(random)
                                .forEach { multiplier ->
                                    yield(Turn(multiplier * 10f))
                                    yield(Turn(-multiplier * 10f))
                                }
                        }
                        val (newDirection, newPosition) = turnOptions
                            .map { turnBy ->
                                val newDirection = ant.direction.turn(turnBy)
                                val positionDelta = calculateMovement(newDirection, moveBy)
                                Pair(newDirection, ant.position.move(positionDelta))
                            }
                            .first { (_, positionOption) ->
                                // Choose the first option that does not move us outside the world.
                                World.contains(positionOption)
                            }
                        Ant.position.modify(ant) { newPosition }
                            .let { Ant.direction.modify(it) { newDirection } }
                    }
                }
            })
        }
    }
}
