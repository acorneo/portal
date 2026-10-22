package de.acorn42.portal.world

import kotlin.math.floor

data class Cell(val x: Int, val y: Int) {
    val center: Vec2 get() = Vec2(x + 0.5, y + 0.5)
}

enum class Visibility { Visible, Explored, Unexplored }

/** Immutable renderer-facing state. Explored cells store only static geometry;
 * dynamic objects must also pass isVisible, including exact point occlusion.
 */
class VisibilityState internal constructor(
    private val maze: Maze,
    val observer: Vec2,
    val radius: Double,
    visible: Set<Cell>,
    explored: Set<Cell>,
) {
    val visibleCells: Set<Cell> = visible.toSet()
    val exploredCells: Set<Cell> = explored.toSet()

    fun at(cell: Cell): Visibility = when (cell) {
        in visibleCells -> Visibility.Visible
        in exploredCells -> Visibility.Explored
        else -> Visibility.Unexplored
    }

    fun isVisible(point: Vec2): Boolean {
        if (!point.x.isFinite() || !point.y.isFinite() ||
            point.x < 0 || point.y < 0 || point.x >= maze.width || point.y >= maze.height
        ) return false
        return at(point.cell()) == Visibility.Visible && (point - observer).length <= radius &&
            maze.walls.none { it.rayEntry(observer, point) != null }
    }
}

/** Session memory advances from world positions, independently of camera and rendering. */
class Exploration(val maze: Maze, initialObserver: Vec2 = maze.start, val radius: Double = 4.5) {
    var state: VisibilityState
        private set

    init {
        require(radius.isFinite() && radius > 0)
        state = calculate(initialObserver, emptySet())
    }

    fun update(observer: Vec2) {
        if (observer != state.observer) state = calculate(observer, state.exploredCells)
    }

    private fun calculate(observer: Vec2, explored: Set<Cell>): VisibilityState {
        require(observer.x.isFinite() && observer.y.isFinite())
        require(observer.x >= 0 && observer.y >= 0 && observer.x < maze.width && observer.y < maze.height)
        val visible = buildSet {
            val minX = floor(observer.x - radius).toInt().coerceAtLeast(0)
            val maxX = floor(observer.x + radius).toInt().coerceAtMost(maze.width - 1)
            val minY = floor(observer.y - radius).toInt().coerceAtLeast(0)
            val maxY = floor(observer.y + radius).toInt().coerceAtMost(maze.height - 1)
            for (y in minY..maxY) for (x in minX..maxX) {
                val cell = Cell(x, y)
                val destination = cell.center
                if ((destination - observer).length > radius) continue
                var firstHit: Double? = null
                var firstWall: Wall? = null
                for (wall in maze.walls) {
                    val time = wall.rayEntry(observer, destination) ?: continue
                    if (firstHit == null || time < firstHit) {
                        firstHit = time
                        firstWall = wall
                    }
                }
                // A wall's front cell can be seen, but neither its back cells nor
                // cells beyond it. Tangent rays are blocked to seal diagonal gaps.
                val hit = firstHit?.let { observer + (destination - observer) * it }
                val wall = firstWall
                val wallCell = wall != null && destination.x in wall.left..wall.right &&
                    destination.y in wall.top..wall.bottom
                if (hit == null || wallCell && hit.x >= x - 1e-9 && hit.x <= x + 1 + 1e-9 &&
                    hit.y >= y - 1e-9 && hit.y <= y + 1 + 1e-9
                ) add(cell)
            }
        }
        return VisibilityState(maze, observer, radius, visible, explored + visible)
    }
}

private fun Vec2.cell() = Cell(floor(x).toInt(), floor(y).toInt())

/** Closed segment vs AABB, including thin walls and corner/edge tangencies. */
private fun Wall.rayEntry(start: Vec2, end: Vec2): Double? {
    var entry = 0.0
    var exit = 1.0
    fun clip(origin: Double, delta: Double, min: Double, max: Double): Boolean {
        if (delta == 0.0) return origin in min..max
        val a = (min - origin) / delta
        val b = (max - origin) / delta
        entry = maxOf(entry, minOf(a, b))
        exit = minOf(exit, maxOf(a, b))
        return entry <= exit
    }
    return if (clip(start.x, end.x - start.x, left, right) &&
        clip(start.y, end.y - start.y, top, bottom)
    ) entry else null
}
