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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ants.common.World
import ants.common.World.worldSize
import ants.engine.GetStateMsg
import ants.engine.StateMsg
import ants.engine.WorldState
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

private var maybeWorldStateChannel: SendChannel<StateMsg>? = null

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
        maybeWorldStateChannel = createEngine(this)
    }
}

