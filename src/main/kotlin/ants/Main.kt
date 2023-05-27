@file:OptIn(ExperimentalStdlibApi::class)

package ants

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ants.Direction.Companion.randomDirection
import ants.World.randomPosition
import ants.World.worldSize
import arrow.optics.optics
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
import kotlin.random.asKotlinRandom

val random = java.util.Random(System.currentTimeMillis()).asKotlinRandom()

object World {
    val worldSize = Size(1024f, 1024f)

    fun contains(position: WorldPosition): Boolean =
        0f.rangeUntil(worldSize.width).contains(position.x) && 0f.rangeUntil(worldSize.height).contains(position.y)

    fun randomPosition(): WorldPosition =
        WorldPosition(random.nextFloat() * worldSize.width, random.nextFloat() * worldSize.height)
}

data class Turn(val degrees: Float) {
    init {
        require((-360f).rangeUntil(360f).contains(degrees))
    }
}

@optics
data class Direction(val degrees: Float) {
    init {
        require(0f.rangeUntil(360f).contains(degrees))
    }

    fun isHorizontal(): Boolean = degrees == 90f || degrees == 270f
    fun isVertical(): Boolean = degrees == 0f || degrees == 180f

    fun turn(t: Turn): Direction {
        val newDegrees = degrees + t.degrees

        return when {
            newDegrees < 0 -> Direction(newDegrees + 360)
            newDegrees >= 360 -> Direction(newDegrees - 360)
            else -> Direction(newDegrees)
        }
    }

    companion object {
        fun randomDirection(): Direction = Direction(random.nextFloat() * 360)
    }
}

@optics
data class WorldPosition(val x: Float, val y: Float) {
    fun move(dx: Float, dy: Float) = WorldPosition(x = x + dx, y = y + dy)

    companion object
}

val colors = listOf(
    Color.Black,
    Color.Blue,
    Color.Cyan,
    Color.DarkGray,
    Color.Gray,
    Color.Green,
    Color.LightGray,
    Color.Magenta,
    Color.Yellow
)

@Composable
@Preview
fun App(getWorldState: suspend () -> WorldState?) {
    var maybeWorldState by remember { mutableStateOf<WorldState?>(null) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    LaunchedEffect(maybeWorldState) {
                        delay(100)
                        maybeWorldState = getWorldState()
                    }

                    Text("Some status text...")
                })
            }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                maybeWorldState?.let { worldState ->
                    worldState.ants
                        .values
                        .map { ant ->
                            Pair(
                                ant,
                                colors[ant.id.id % colors.size],
                            )
                        }
                        .filter { (ant, _) -> World.contains(ant.position) }
                        .forEach { (ant, color) ->
                            drawCircle(
                                color = color,
                                radius = size.minDimension / 42f,
                                center = Offset(
                                    ant.position.x / worldSize.width * size.width,
                                    ant.position.y / worldSize.height * size.height
                                )
                            )
                        }
                }
            }
        }
    }
}

sealed class StateMsg
class MoveAntMsg(val antId: AntId, val f: (Ant) -> Pair<WorldPosition, Direction>) : StateMsg()
class GetStateMsg(val response: CompletableDeferred<WorldState>) : StateMsg()

data class AntId(val id: Int)

@optics
data class Ant(val id: AntId, val position: WorldPosition, val direction: Direction) {
    companion object
}

// NOTE: @optics does not work for this data class but produces a compilation error:
// "Type mismatch: inferred type is Unit but WorldState was expected"
data class WorldState(val ants: PersistentMap<AntId, Ant>) {
    companion object
}

@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.worldStateActor(ants: PersistentMap<AntId, Ant>) = actor<StateMsg> {
    var state = WorldState(ants = ants)

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

var maybeWorldStateChannel: SendChannel<StateMsg>? = null

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App {
            maybeWorldStateChannel?.let { channel ->
                val stateResponse = CompletableDeferred<WorldState>()
                channel.send(GetStateMsg(stateResponse))
                stateResponse.await()
            }
        }
    }

    LaunchedEffect(Unit) {
        val ants = (1..100)
            .map { Ant(AntId(it), randomPosition(), randomDirection()) }
            .associateBy { it.id }
            .toPersistentHashMap()

        worldStateActor(ants).also { worldStateActor ->
            maybeWorldStateChannel = worldStateActor

            worldStateActor.let { worldStateChannel ->
                val initialResponse = CompletableDeferred<WorldState>()
                worldStateChannel.send(GetStateMsg(initialResponse))

                initialResponse.await().let { initialState ->
                    withContext(Dispatchers.Default) {
                        initialState.ants.keys.forEach { antId ->
                            launch {
                                while (true) {
                                    delay(random.nextLong(50, 100))

                                    worldStateChannel.send(MoveAntMsg(antId) { ant ->
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
