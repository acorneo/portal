package de.acorn42.portal

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.acorn42.portal.world.FirstMaze
import de.acorn42.portal.world.FixedStepClock
import de.acorn42.portal.world.WorldSimulation
import java.awt.Dimension
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PORTAL — The First Maze",
        state = rememberWindowState(width = 1080.dp, height = 780.dp),
    ) {
        val input = remember { DesktopInput() }
        val simulation = remember { WorldSimulation(FirstMaze.world) }
        val clock = remember { FixedStepClock() }
        val focusRequester = remember { FocusRequester() }
        var sphere by remember { mutableStateOf(simulation.sphere) }
        var windowFocused by remember { mutableStateOf(window.isFocused) }
        var contentFocused by remember { mutableStateOf(false) }
        val active = windowFocused && contentFocused

        DisposableEffect(window) {
            window.minimumSize = Dimension(600, 480)
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(event: WindowEvent) {
                    input.clear()
                    windowFocused = true
                }

                override fun windowLostFocus(event: WindowEvent) {
                    input.clear()
                    windowFocused = false
                }
            }
            window.addWindowFocusListener(listener)
            onDispose {
                window.removeWindowFocusListener(listener)
                input.clear()
            }
        }

        LaunchedEffect(windowFocused) {
            if (windowFocused) focusRequester.requestFocus()
        }

        LaunchedEffect(active) {
            input.clear()
            clock.reset()
            if (active) {
                var previousFrame = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val elapsed = (now - previousFrame).coerceAtLeast(0L) / 1_000_000_000.0
                        previousFrame = now
                        clock.advance(elapsed) { simulation.step(input.movement(), it) }
                        sphere = simulation.sphere
                    }
                }
            }
        }

        App(
            maze = simulation.maze,
            sphere = sphere,
            controls = if (active) "WASD / Arrow keys to move · Explore the corridors" else "Click the maze to resume · WASD / Arrow keys to move",
            active = active,
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    contentFocused = it.hasFocus
                    if (!it.hasFocus) input.clear()
                }
                .onPreviewKeyEvent { if (windowFocused) input.handle(it) else false }
                .focusable()
                .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } },
        )
    }
}
