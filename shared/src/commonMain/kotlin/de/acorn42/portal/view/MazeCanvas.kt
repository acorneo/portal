package de.acorn42.portal.view

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.acorn42.portal.world.*

private val Floor = Color(0xFF132638)
private val ExploredFloor = Color(0xFF0E1825)
private val WallColor = Color(0xFF38546B)
private val ExploredWall = Color(0xFF1B2A3D)
private val WallEdge = Color(0xFF57798F)
private val ExploredEdge = Color(0xFF24364B)
private val Turquoise = Color(0xFF6FFFE2)

private data class CellGeometry(val cell: Cell, val walls: List<Wall>)

/** Camera and light effects are presentation state. Exploration is read-only here. */
@Composable
internal fun MazeCanvas(maze: Maze, sphere: Sphere, visibility: VisibilityState, active: Boolean, modifier: Modifier) {
    val pixelsPerTile = with(LocalDensity.current) { 64.dp.toPx().toDouble() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = if (canvasSize.width > 0 && canvasSize.height > 0) {
        Viewport(canvasSize.width.toDouble(), canvasSize.height.toDouble(), pixelsPerTile)
    } else null
    val worldSize = remember(maze) { Vec2(maze.width.toDouble(), maze.height.toDouble()) }
    // Reframe immediately on resize so a shrinking viewport cannot strand the
    // sphere offscreen. Ordinary motion and focus pauses retain the smooth camera.
    var camera by remember(maze, viewport) {
        mutableStateOf(Camera(sphere.position).let { if (viewport != null) it.bounded(viewport, worldSize) else it })
    }
    val currentSphere by rememberUpdatedState(sphere)
    val geometry = remember(maze) {
        buildList {
            for (y in 0 until maze.height) for (x in 0 until maze.width) {
                add(CellGeometry(Cell(x, y), maze.walls.filter {
                    it.left < x + 1 && it.right > x && it.top < y + 1 && it.bottom > y
                }))
            }
        }
    }

    LaunchedEffect(maze, viewport, active) {
        val view = viewport ?: return@LaunchedEffect
        camera = camera.bounded(view, worldSize)
        if (active) {
            var previous = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val seconds = (now - previous).coerceAtLeast(0L) / 1_000_000_000.0
                    previous = now
                    camera = camera.follow(currentSphere.position, seconds, view, worldSize)
                }
            }
        }
    }

    Canvas(modifier.onSizeChanged { canvasSize = it }) {
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val view = Viewport(size.width.toDouble(), size.height.toDouble(), pixelsPerTile)
        val frameCamera = camera.bounded(view, worldSize)
        fun screen(point: Vec2): Offset = frameCamera.worldToScreen(point, view).let { Offset(it.x.toFloat(), it.y.toFloat()) }
        val scale = pixelsPerTile.toFloat()
        val lightCenter = screen(visibility.observer)
        val lightRadius = (visibility.radius * pixelsPerTile).toFloat()
        fun light(normal: Color, faint: Color): Brush = Brush.radialGradient(
            0f to normal, 0.6f to normal, 1f to faint,
            center = lightCenter, radius = lightRadius,
        )
        val floorLight = light(Floor, ExploredFloor)
        val wallLight = light(WallColor, ExploredWall)
        val edgeLight = light(WallEdge, ExploredEdge)
        val visibleArea = Path()

        // Nothing, including the grid, is drawn for unexplored cells. Clipping
        // each known cell also prevents full wall rectangles exposing hidden map.
        clipRect {
            for ((cell, walls) in geometry) {
                val state = visibility.at(cell)
                if (state == Visibility.Unexplored) continue
                val topLeft = screen(Vec2(cell.x.toDouble(), cell.y.toDouble()))
                if (topLeft.x + scale < 0 || topLeft.y + scale < 0 || topLeft.x > size.width || topLeft.y > size.height) continue
                val visible = state == Visibility.Visible
                val rect = Rect(topLeft, Size(scale, scale))
                if (visible) visibleArea.addRect(rect)
                val floorBrush = if (visible) floorLight else SolidColor(ExploredFloor)
                val wallBrush = if (visible) wallLight else SolidColor(ExploredWall)
                val edgeBrush = if (visible) edgeLight else SolidColor(ExploredEdge)
                clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                    drawRect(floorBrush, topLeft, Size(scale, scale))
                    drawLine(edgeBrush, topLeft, topLeft + Offset(scale, 0f), 0.5f)
                    drawLine(edgeBrush, topLeft, topLeft + Offset(0f, scale), 0.5f)
                    for (wall in walls) {
                        val wallTop = screen(Vec2(wall.left, wall.top))
                        val wallSize = Size(((wall.right - wall.left) * scale).toFloat(), ((wall.bottom - wall.top) * scale).toFloat())
                        drawRect(wallBrush, wallTop, wallSize)
                        drawLine(edgeBrush, wallTop, wallTop + Offset(wallSize.width, 0f), 1f)
                    }
                }
            }

            // The sphere is our only dynamic entity. Future entities must use
            // this exact visibility gate, never the explored-geometry memory.
            if (visibility.isVisible(sphere.position)) {
                val center = screen(sphere.position)
                val radius = sphere.radius.toFloat() * scale
                clipPath(visibleArea) {
                    drawCircle(
                        Brush.radialGradient(listOf(Turquoise.copy(alpha = 0.2f), Color.Transparent), center, radius * 2.7f),
                        radius * 2.7f, center,
                    )
                }
                drawCircle(Turquoise, radius, center)
                drawCircle(Color(0xFFE5FFF9), radius * 0.4f, center - Offset(radius * 0.22f, radius * 0.22f))
            }
        }
    }
}
