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
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.asKotlinRandom

val worldSize = Size(1024f, 1024f)

data class WorldPosition(val x: Float, val y: Float) {
    init {
        assert(0f.rangeUntil(worldSize.width).contains(x))
        assert(0f.rangeUntil(worldSize.height).contains(y))
    }
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
                        delay(1_000)
                        maybeWorldState = getWorldState()
                    }

                    Text("Some status text...")
                })
            }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val purpleColor = Color(0xFFBA68C8)
                maybeWorldState?.let { worldState ->
                    worldState.ants.forEach { ant ->
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

suspend fun massiveRun(ants: ImmutableSet<AntId>, action: suspend (AntId) -> Unit) {
    coroutineScope { // scope for coroutines
        ants.forEach { antId ->
            launch {
                while (true) {
                    action(antId)
                }
            }
        }
    }
}

sealed class StateMsg
class MoveAntMsg(val antId: AntId) : StateMsg()
class GetStateMsg(val response: CompletableDeferred<WorldState>) : StateMsg()

data class AntId(val id: Int)
data class Ant(val id: AntId, val position: WorldPosition)
data class WorldState(val ants: PersistentMap<AntId, Ant>)

@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.worldStateActor() = actor<StateMsg> {
    var state = WorldState(
        ants = (1..2)
            .map { Ant(AntId(it), WorldPosition(it.toFloat() * 10, it.toFloat() * 10)) }
            .associateBy { it.id }
            .toPersistentHashMap()
    )

    for (msg in channel) {
        when (msg) {
            is MoveAntMsg -> {
                val ant = checkNotNull(state.ants[msg.antId])
                val updated = ant.copy(position = ant.position.copy(x = ant.position.x + 10))
                state = state.copy(ants = state.ants.put(ant.id, updated))
            }

            is GetStateMsg -> msg.response.complete(state)
        }
    }
}

val random = java.util.Random(1234).asKotlinRandom()
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
        maybeWorldStateChannel = worldStateActor()
        val initialResponse = CompletableDeferred<WorldState>()
        checkNotNull(maybeWorldStateChannel).send(GetStateMsg(initialResponse))

        initialResponse.await().let { initialState ->
            withContext(Dispatchers.Default) {
                massiveRun(initialState.ants.keys) {
                    delay(random.nextLong(100, 1000))
                    checkNotNull(maybeWorldStateChannel).send(MoveAntMsg(it))
                }
            }
        }
    }
}
