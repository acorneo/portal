package de.acorn42.portal.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstMazeTest {
    @Test
    fun startIsValidAndAllCorridorsAreNavigable() {
        val maze = FirstMaze.world
        val layout = FirstMaze.layout
        assertTrue(maze.canOccupy(Sphere(maze.start)))
        assertEquals(1, layout.sumOf { row -> row.count { it == 'S' } })
        assertTrue(layout.first().all { it == '#' } && layout.last().all { it == '#' })
        assertTrue(layout.all { it.first() == '#' && it.last() == '#' })
        val start = maze.start.x.toInt() to maze.start.y.toInt()
        val visited = mutableSetOf(start)
        val pending = ArrayDeque<Pair<Int, Int>>()
        pending.add(start)
        while (pending.isNotEmpty()) {
            val (x, y) = pending.removeFirst()
            val center = Vec2(x + 0.5, y + 0.5)
            for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val next = x + dx to y + dy
                if (layout.getOrNull(next.second)?.getOrNull(next.first) in listOf('.', 'S')) {
                    // Validate actual sphere clearance between adjacent corridor centers.
                    val result = maze.move(Sphere(center), Vec2(dx.toDouble(), dy.toDouble()))
                    assertPosition(Vec2(next.first + 0.5, next.second + 0.5), result.position)
                    if (visited.add(next)) pending.add(next)
                }
            }
        }
        assertEquals(layout.sumOf { row -> row.count { it != '#' } }, visited.size, "All floor tiles must be reachable")
    }
}
