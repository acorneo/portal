package de.acorn42.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.acorn42.portal.view.MazeCanvas
import de.acorn42.portal.world.Maze
import de.acorn42.portal.world.FirstMaze
import de.acorn42.portal.world.Sphere
import de.acorn42.portal.world.VisibilityState
import de.acorn42.portal.world.WorldSimulation

private val Background = Color(0xFF080F1B)
private val Turquoise = Color(0xFF6FFFE2)
private val Muted = Color(0xFF9AAEC4)

/** Compatibility host for callers that display the default shared scene. */
@Composable
fun App() {
    val simulation = remember { WorldSimulation(FirstMaze.world) }
    App(simulation.maze, simulation.sphere, simulation.exploration.state)
}

/** Shared presentation only. The host owns simulation, frame timing, and input. */
@Composable
fun App(
    maze: Maze,
    sphere: Sphere,
    visibility: VisibilityState,
    modifier: Modifier = Modifier,
    controls: String = "PORTAL · Shared world",
    active: Boolean = true,
) {
    MaterialTheme(colorScheme = darkColorScheme(background = Background, primary = Turquoise)) {
        Column(modifier.fillMaxSize().background(Background).safeContentPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("P O R T A L", color = Turquoise, fontSize = 24.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.height(6.dp))
                    Text("01 / THE FIRST MAZE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
                Text(if (active) "EXPLORE" else "PAUSED", color = if (active) Turquoise else Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(20.dp))
            MazeCanvas(maze, sphere, visibility, active, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(18.dp))
            Text("Lit · visible    Faint · explored    Dark · unknown", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(controls, color = Muted, fontSize = 12.sp)
        }
    }
}
