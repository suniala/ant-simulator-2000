@file:OptIn(ExperimentalStdlibApi::class)

package ants.engine

import ants.common.Ant
import ants.common.AntId
import ants.common.AntState
import ants.common.Direction
import ants.common.Distance
import ants.common.Pheromone
import ants.common.PheromoneId
import ants.common.PheromoneStrength
import ants.common.Turn
import ants.common.World
import ants.common.calculateMovement
import ants.common.direction
import ants.common.directionTo
import ants.common.distance
import ants.common.position
import ants.common.random
import ants.common.state
import ants.common.strength
import arrow.optics.copy
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("MayBeConstant")
object Params {
    val ants = 1
    val antWorkerDelayMs = 10L
    val antGoesOutsideProbability = 0.9f
    val antTurnsProbability = 0.1f
    val antTurnMultiplier = 10f
    val antMovePerIteration = Distance(2f)
    val dropPheromonePerDistance = Distance(1f)
    val pheromoneWorkerDelayMs = 1_000L
    val pheromoneDecayPerWorkerIteration = 0.1f
    val surroundingsLimit = Distance(50f)
}

sealed class StateMsg

data class NextAntStateMsg(val antId: AntId, val f: (Ant, Collection<Pheromone>) -> Pair<Ant, Boolean>) : StateMsg()

data class GetStateMsg(val response: CompletableDeferred<State>) : StateMsg()

data class UpdatePheromoneMsg(val pheromoneId: PheromoneId, val f: (Pheromone) -> Pheromone?) : StateMsg()

@OptIn(ObsoleteCoroutinesApi::class)
private fun CoroutineScope.stateActor(ants: PersistentMap<AntId, Ant>) = actor<StateMsg> {
    var state = State(ants = ants, pheromones = persistentMapOf())

    for (msg in channel) {
        when (msg) {
            is NextAntStateMsg -> {
                val ant = checkNotNull(state.ants[msg.antId])
                val surroundings = state.pheromones.filterValues {
                    distance(
                        it.position,
                        ant.position
                    ) le Params.surroundingsLimit
                }.values
                val (nextAnt, dropPheromone) = msg.f(ant, surroundings)

                val nextPheromones =
                    if (dropPheromone) {
                        val pheromoneId = PheromoneId((state.pheromones.keys.maxByOrNull { it.id }?.id ?: 0) + 1)
                        launch { pheromoneWorker(channel, pheromoneId) }
                        state.pheromones.put(
                            pheromoneId,
                            Pheromone(pheromoneId, PheromoneStrength.fullStrength(), nextAnt.position)
                        )
                    } else state.pheromones
                state = state.copy(ants = state.ants.put(ant.id, nextAnt), pheromones = nextPheromones)
            }

            is GetStateMsg -> msg.response.complete(state)

            is UpdatePheromoneMsg -> {
                val pheromone = checkNotNull(state.pheromones[msg.pheromoneId])
                val nextPheromone = msg.f(pheromone)
                state = state.copy(
                    pheromones = if (nextPheromone != null)
                        state.pheromones.put(pheromone.id, nextPheromone)
                    else state.pheromones.remove(pheromone.id)
                )
            }
        }
    }
}

suspend fun createEngine(scope: CoroutineScope): SendChannel<StateMsg> =
    scope.run {
        val ants = (1..Params.ants)
            .map {
                Ant(
                    AntId(it),
                    AntState.INSIDE,
                    World.middlePosition(),
                    Direction.randomDirection(),
                    persistentSetOf()
                )
            }
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
        // Maybe we can keep local state here if it is not interesting to other parties?
        var privateState = AntWorkerPrivateState(Params.dropPheromonePerDistance)

        while (true) {
            delay(Params.antWorkerDelayMs)

            stateChannel.send(NextAntStateMsg(antId) { ant, p ->
                val (updatedAnt, dropPheromone, updatedPrivateState) = doAnt(ant, p, privateState)
                privateState = updatedPrivateState
                Pair(updatedAnt, dropPheromone)
            })
        }
    }
}

data class AntWorkerPrivateState(val distanceUntilPheromoneDrop: Distance)
data class AntWorkerResult(val ant: Ant, val dropPheromone: Boolean, val privateState: AntWorkerPrivateState)

fun doAnt(ant: Ant, surroundings: Collection<Pheromone>, privateState: AntWorkerPrivateState): AntWorkerResult =
    when (ant.state) {
        AntState.INSIDE -> {
            val moveOutside = random.nextFloat() <= Params.antGoesOutsideProbability
            AntWorkerResult(
                if (moveOutside) ant.copy { Ant.state set AntState.OUTSIDE }
                else ant,
                false,
                privateState
            )
        }

        AntState.OUTSIDE -> {
            val turnBy =
                if (random.nextFloat() <= Params.antTurnsProbability)
                    Turn((random.nextFloat() - 0.5f) * 2 * Params.antTurnMultiplier)
                else Turn(0f)
            val moveBy = Params.antMovePerIteration
            val newDirection = ant.direction.turn(turnBy)
            val newPosition = ant.position.move(calculateMovement(newDirection, moveBy))
            if (World.contains(newPosition)) {
                val updatedAnt = ant.copy {
                    Ant.position set newPosition
                    Ant.direction set newDirection
                }
                if (privateState.distanceUntilPheromoneDrop.le(moveBy)) {
                    AntWorkerResult(
                        updatedAnt,
                        true,
                        privateState.copy(distanceUntilPheromoneDrop = Params.dropPheromonePerDistance)
                    )
                } else {
                    AntWorkerResult(
                        updatedAnt,
                        false,
                        privateState.copy(distanceUntilPheromoneDrop = privateState.distanceUntilPheromoneDrop - moveBy)
                    )
                }
            } else {
//                            val closestUnvisitedPheromone = surroundings.asSequence()
//                                .filter { !ant.visitedPheromones.contains(it.id) }
//                                .map { Pair(it, distance(it.position, ant.position)) }
//                                .minByOrNull { (_, distance) -> distance }
//                            val fallBackDir = closestUnvisitedPheromone
//                                ?.let { target ->
//                                    directionTo(ant.position, target.first.position)
//                                }
//                            // Just go back where we came from
//                                ?: ant.direction.turn(Turn(180f))
//                            val newPosition2 = ant.position.move(calculateMovement(newDirection, moveBy))
//                            Pair(newPosition2, fallBackDir)
                val updatedAnt = ant.copy {
                    Ant.direction set ant.direction.turn(Turn(180f))
                    Ant.state set AntState.BACK_TO_NEST
                }
                AntWorkerResult(updatedAnt, false, privateState)
            }
        }

        AntState.BACK_TO_NEST -> {
            val closestUnvisitedPheromone = surroundings.asSequence()
                .filter { !ant.visitedPheromones.contains(it.id) }
                .map { Pair(it, distance(it.position, ant.position)) }
                .minByOrNull { (_, distance) -> distance }!!
            val newDirection = directionTo(ant.position, closestUnvisitedPheromone.first.position)
            val moveBy = Params.antMovePerIteration
            val newPosition = ant.position.move(calculateMovement(newDirection, moveBy))
            val updatedAnt = ant.copy {
                Ant.position set newPosition
                Ant.direction set newDirection
            }
            AntWorkerResult(updatedAnt, false, privateState)
        }
    }

private fun CoroutineScope.pheromoneWorker(
    stateChannel: SendChannel<StateMsg>,
    pheromoneId: PheromoneId,
) {
    launch {
        while (true) {
            delay(Params.pheromoneWorkerDelayMs)
            stateChannel.send(UpdatePheromoneMsg(pheromoneId) { pheromone ->
                if (pheromone.isEffective()) {
                    pheromone.copy { Pheromone.strength transform { it * (1 - Params.pheromoneDecayPerWorkerIteration) } }
                } else {
                    cancel()
                    null
                }
            })
        }
    }
}
