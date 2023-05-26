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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

@Composable
@Preview
fun App(getCounter: () -> Int) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    var counter by remember { mutableStateOf(0) }
                    LaunchedEffect(counter) {
                        delay(1_000)
                        counter = getCounter()
                    }

                    Text(text = counter.toString())
                })
            }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val purpleColor = Color(0xFFBA68C8)
                val worldSize = Size(1024f, 1024f)
                drawCircle(
                    color = purpleColor,
                    radius = size.minDimension / 19f,
                    center = Offset(300f / worldSize.width * size.width, 400f / worldSize.height * size.height)
                )
                drawCircle(
                    color = purpleColor,
                    radius = size.minDimension / 19f,
                    center = Offset(600f / worldSize.width * size.width, 300f / worldSize.height * size.height)
                )
            }
        }
    }
}

suspend fun massiveRun(action: suspend () -> Unit) {
    val n = 131  // number of coroutines to launch
    val k = 300 // times an action is repeated by each coroutine
    val time = measureTimeMillis {
        coroutineScope { // scope for coroutines
            repeat(n) {
                launch {
                    repeat(k) { action() }
                }
            }
        }
    }
    println("Completed ${n * k} actions in $time ms")
}

val counterContext = newSingleThreadContext("CounterContext")
var counter = 0

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App { counter }
    }

    LaunchedEffect(Unit) {
        // confine everything to a single-threaded context
        withContext(counterContext) {
            massiveRun {
                counter++
                delay(111)
            }
        }
        println("Counter = $counter")
    }
}
