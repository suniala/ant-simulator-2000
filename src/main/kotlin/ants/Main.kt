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
import kotlin.random.asKotlinRandom

val random = java.util.Random(System.currentTimeMillis()).asKotlinRandom()

object World {
    val worldSize = Size(1024f, 1024f)

    fun contains(position: WorldPosition): Boolean =
        0f.rangeUntil(worldSize.width).contains(position.x) && 0f.rangeUntil(worldSize.height).contains(position.y)

    fun randomPosition(): WorldPosition = WorldPosition(random.nextFloat() * worldSize.width, random.nextFloat() * worldSize.height)
}

@optics
data class WorldPosition(val x: Float, val y: Float) {
    fun move(dx: Float, dy: Float) = WorldPosition(x = x + dx, y = y + dy)
    companion object
}

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
                val purpleColor = Color(0xFFBA68C8)
                maybeWorldState?.let { worldState ->
                    worldState.ants
                        .filter { World.contains(it.value.position) }
                        .forEach { ant ->
                            drawCircle(
                                color = purpleColor,
                                radius = size.minDimension / 42f,
                                center = Offset(
                                    ant.value.position.x / worldSize.width * size.width,
                                    ant.value.position.y / worldSize.height * size.height
                                )
                            )
                        }
                }
            }
        }
    }
}

sealed class StateMsg
class MoveAntMsg(val antId: AntId, val f: (Ant) -> WorldPosition) : StateMsg()
class GetStateMsg(val response: CompletableDeferred<WorldState>) : StateMsg()

data class AntId(val id: Int)

@optics
data class Ant(val id: AntId, val position: WorldPosition) {
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
                val updated = Ant.position.modify(ant) { msg.f(ant) }
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
            .map { Ant(AntId(it), randomPosition()) }
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
                                        ant.position.move(
                                            (random.nextFloat() - 0.5f) * 10,
                                            (random.nextFloat() - 0.5f) * 10,
                                        )
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
