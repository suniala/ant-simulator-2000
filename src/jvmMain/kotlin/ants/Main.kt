package ants

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
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
import kotlinx.coroutines.delay

@Composable
@Preview
fun App() {
    MaterialTheme {
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

        var text by remember { mutableStateOf("Hello, World!") }
        LaunchedEffect(text) {
            delay(1_000)
            text = "$text."
        }

        Text(
            text = text
        )
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
