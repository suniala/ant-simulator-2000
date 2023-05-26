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
import kotlin.system.measureTimeMillis

@Composable
@Preview
fun App(getCounter: suspend () -> Int?) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    var counter by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(counter) {
                        delay(1_000)
                        counter = getCounter()
                    }

                    Text(text = counter?.toString() ?: "-")
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
    val n = 100  // number of coroutines to launch
    val k = 1000 // times an action is repeated by each coroutine
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

// Message types for counterActor
sealed class CounterMsg
object IncCounter : CounterMsg() // one-way message to increment counter
class GetCounter(val response: CompletableDeferred<Int>) : CounterMsg() // a request with reply

// This function launches a new counter actor
@OptIn(ObsoleteCoroutinesApi::class)
fun CoroutineScope.counterActor() = actor<CounterMsg> {
    var counter = 0 // actor state
    for (msg in channel) { // iterate over incoming messages
        when (msg) {
            is IncCounter -> counter++
            is GetCounter -> msg.response.complete(counter)
        }
    }
}

val random = java.util.Random(1234).asKotlinRandom()
var counter: SendChannel<CounterMsg>? = null

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App {
            counter?.let { c ->
                val counterResponse = CompletableDeferred<Int>()
                c.send(GetCounter(counterResponse))
                counterResponse.await()
            }
        }
    }

    LaunchedEffect(Unit) {
        counter = counterActor() // create the actor
        withContext(Dispatchers.Default) {
            massiveRun {
                delay(random.nextLong(100, 1000))
                checkNotNull(counter).send(IncCounter)
            }
        }
        // send a message to get a counter value from an actor
        val response = CompletableDeferred<Int>()
        checkNotNull(counter).send(GetCounter(response))
        println("Counter = ${response.await()}")
        checkNotNull(counter).close() // shutdown the actor
    }
}
