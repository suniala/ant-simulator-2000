package ants.engine

import ants.common.Ant
import ants.common.AntId
import ants.common.Direction
import ants.common.Turn
import ants.common.World
import ants.common.WorldPosition
import ants.common.direction
import ants.common.position
import ants.common.random
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

sealed class StateMsg
class MoveAntMsg(val antId: AntId, val f: (Ant) -> Pair<WorldPosition, Direction>) : StateMsg()
class GetStateMsg(val response: CompletableDeferred<State>) : StateMsg()

@OptIn(ObsoleteCoroutinesApi::class)
private fun CoroutineScope.stateActor(ants: PersistentMap<AntId, Ant>) = actor<StateMsg> {
    var state = State(ants = ants)

    for (msg in channel) {
        when (msg) {
            is MoveAntMsg -> {
                val ant = checkNotNull(state.ants[msg.antId])
                val (position, direction) = msg.f(ant)
                val updated = Ant.position.modify(ant) { position }
                    .let { Ant.direction.modify(it) { direction } }

                state = state.copy(ants = state.ants.put(ant.id, updated))
            }

            is GetStateMsg -> msg.response.complete(state)
        }
    }
}

suspend fun createEngine(scope: CoroutineScope): SendChannel<StateMsg> =
    scope.run {
        val ants = (1..100)
            .map { Ant(AntId(it), World.randomPosition(), Direction.randomDirection()) }
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
                                launch {
                                    while (true) {
                                        delay(random.nextLong(50, 100))

                                        stateChannel.send(MoveAntMsg(antId) { ant ->
                                            val turnBy =
                                                if (random.nextLong(0, 20) < 1) Turn((random.nextFloat() - 0.5f) * 10)
                                                else Turn(0f)
                                            val moveBy = 1f
                                            val newDirection = ant.direction.turn(turnBy)

                                            val dx: Float = when {
                                                newDirection.isVertical() -> 0f
                                                newDirection.degrees < 180f -> moveBy / cos(abs(90 - newDirection.degrees))
                                                else -> moveBy / cos(abs(270 - newDirection.degrees))
                                            }
                                            val dy: Float = when {
                                                newDirection.isHorizontal() -> 0f
                                                newDirection.degrees < 90f || newDirection.degrees > 270f -> moveBy / sin(
                                                    abs(90 - newDirection.degrees)
                                                )

                                                else -> moveBy / sin(abs(270 - newDirection.degrees))
                                            }

                                            Pair(ant.position.move(dx, dy), newDirection)
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
