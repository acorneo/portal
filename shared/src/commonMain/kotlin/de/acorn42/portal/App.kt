package de.acorn42.portal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.acorn42.portal.world.FirstMaze
import de.acorn42.portal.world.Maze
import de.acorn42.portal.world.Sphere

private val Background = Color(0xFF080F1B)
private val Turquoise = Color(0xFF6FFFE2)
private val Muted = Color(0xFF9AAEC4)

/** Shared presentation only. The host owns simulation, frame timing, and input. */
@Composable
fun App(
    maze: Maze = FirstMaze.world,
    sphere: Sphere = Sphere(maze.start),
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
            MazeCanvas(maze, sphere, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(18.dp))
            Text(controls, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MazeCanvas(maze: Maze, sphere: Sphere, modifier: Modifier) {
    Canvas(modifier) {
        val scale = minOf(size.width / maze.width, size.height / maze.height)
        val origin = Offset((size.width - maze.width * scale) / 2, (size.height - maze.height * scale) / 2)
        translate(origin.x, origin.y) {
            drawRect(Color(0xFF101E2E), size = Size(maze.width * scale, maze.height * scale))
            // A faint floor grid makes the corridor width easy to read.
            for (x in 0..maze.width) {
                drawLine(Color(0xFF17283A), Offset(x * scale, 0f), Offset(x * scale, maze.height * scale))
            }
            for (y in 0..maze.height) {
                drawLine(Color(0xFF17283A), Offset(0f, y * scale), Offset(maze.width * scale, y * scale))
            }
            for (wall in maze.walls) {
                val topLeft = Offset(wall.left.toFloat() * scale, wall.top.toFloat() * scale)
                val wallSize = Size((wall.right - wall.left).toFloat() * scale, (wall.bottom - wall.top).toFloat() * scale)
                drawRect(Color(0xFF2C4258), topLeft, wallSize)
                drawLine(Color(0xFF47617B), topLeft, topLeft + Offset(wallSize.width, 0f), 1f)
            }
            val center = Offset(sphere.position.x.toFloat() * scale, sphere.position.y.toFloat() * scale)
            val radius = sphere.radius.toFloat() * scale
            drawCircle(
                Brush.radialGradient(listOf(Turquoise.copy(alpha = 0.24f), Color.Transparent), center, radius * 3f),
                radius * 3f,
                center,
            )
            drawCircle(Turquoise, radius, center)
            drawCircle(Color(0xFFE5FFF9), radius * 0.4f, center - Offset(radius * 0.22f, radius * 0.22f))
        }
    }
}
