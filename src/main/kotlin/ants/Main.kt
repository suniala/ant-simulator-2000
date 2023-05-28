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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ants.common.World.worldSize
import ants.engine.GetStateMsg
import ants.engine.State
import ants.engine.StateMsg
import ants.engine.createEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay

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
fun App(aGetState: suspend () -> State?) {
    var maybeState by remember { mutableStateOf<State?>(null) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    LaunchedEffect(maybeState) {
                        delay(100)
                        maybeState = aGetState()
                    }

                    Text("Some status text...")
                })
            }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                maybeState?.let { worldState ->
                    worldState.ants
                        .values
                        .map { ant ->
                            Pair(
                                ant,
                                colors[ant.id.id % colors.size],
                            )
                        }
                        .filter { (ant, _) -> ant.state.isVisible() }
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

                    worldState.pheromones
                        .values
                        .filter { it.isEffective() }
                        .forEach { pheromone ->
                            drawCircle(
                                color = Color.Green.copy(alpha = pheromone.strength.strength),
                                radius = size.minDimension / 100f,
                                center = Offset(
                                    pheromone.position.x / worldSize.width * size.width,
                                    pheromone.position.y / worldSize.height * size.height
                                )
                            )
                        }
                }
            }
        }
    }
}

private var maybeStateChannel: SendChannel<StateMsg>? = null

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App {
            maybeStateChannel?.let { channel ->
                val stateResponse = CompletableDeferred<State>()
                channel.send(GetStateMsg(stateResponse))
                stateResponse.await()
            }
        }
    }

    LaunchedEffect(Unit) {
        maybeStateChannel = createEngine(this)
    }
}

