package de.acorn42.portal.world

import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class CollisionTest {
    private val block = Wall(4.0, 4.0, 6.0, 6.0)

    @Test
    fun highSpeedCannotTunnelThroughAnyWallFace() {
        val cases = listOf(
            Triple(Vec2(2.0, 5.0), Vec2(100.0, 0.0), Vec2(3.76, 5.0)),
            Triple(Vec2(8.0, 5.0), Vec2(-100.0, 0.0), Vec2(6.24, 5.0)),
            Triple(Vec2(5.0, 2.0), Vec2(0.0, 100.0), Vec2(5.0, 3.76)),
            Triple(Vec2(5.0, 8.0), Vec2(0.0, -100.0), Vec2(5.0, 6.24)),
        )
        for ((start, displacement, expected) in cases) {
            val maze = Maze(10, 10, listOf(block), start)
            val sphere = maze.move(Sphere(start), displacement)
            assertPosition(expected, sphere.position)
            assertTrue(maze.canOccupy(sphere))
        }
    }

    @Test
    fun simulationCannotTunnelThroughAThinWallAtHighSpeed() {
        val maze = Maze(100, 100, listOf(Wall(4.0, 0.0, 4.01, 100.0)), Vec2(2.0, 5.0))
        val simulation = WorldSimulation(maze, movementSpeed = 10_000.0)
        simulation.step(MoveInput(1.0), 1.0 / 120)
        assertPosition(Vec2(3.76, 5.0), simulation.sphere.position)
    }

    @Test
    fun diagonalMovementSlidesAlongAFlatWall() {
        val maze = Maze(10, 10, listOf(Wall(4.0, 0.0, 5.0, 10.0)), Vec2(2.0, 2.0))
        val simulation = WorldSimulation(maze)
        simulation.step(MoveInput(1.0, 1.0), 1.0)
        assertPosition(Vec2(3.76, 2.0 + 3.0 / sqrt(2.0)), simulation.sphere.position)
        repeat(120) { simulation.step(MoveInput(1.0, 0.0), 1.0 / 120) }
        assertTrue(maze.canOccupy(simulation.sphere))
        simulation.step(MoveInput(-1.0), 0.1)
        assertTrue(simulation.sphere.position.x < 3.5, "Moving away from contact must work")
    }

    @Test
    fun allFourRoundedCornersStopADirectHighSpeedHit() {
        for (x in listOf(-1.0, 1.0)) {
            for (y in listOf(-1.0, 1.0)) {
                val start = Vec2(5.0 + x * 3.0, 5.0 + y * 3.0)
                val maze = Maze(10, 10, listOf(block), start)
                val result = maze.move(Sphere(start), Vec2(-x * 100, -y * 100))
                val corner = Vec2(5.0 + x, 5.0 + y)
                assertPosition(corner + Vec2(x, y) * (0.24 / sqrt(2.0)), result.position)
                assertTrue(maze.canOccupy(result))
            }
        }
    }

    @Test
    fun simultaneousContactsInAnInsideCornerDoNotPenetrate() {
        val maze = Maze(10, 10, listOf(Wall(4.0, 0.0, 5.0, 10.0), Wall(0.0, 4.0, 10.0, 5.0)), Vec2(2.0, 2.0))
        var sphere = maze.move(Sphere(maze.start), Vec2(100.0, 100.0))
        assertPosition(Vec2(3.76, 3.76), sphere.position)
        repeat(200) { sphere = maze.move(sphere, Vec2(1.0, 1.0)) }
        assertPosition(Vec2(3.76, 3.76), sphere.position)
        assertTrue(maze.canOccupy(sphere))
    }

    @Test
    fun spaceNearARoundedCornerIsNotTreatedAsASquare() {
        val maze = Maze(10, 10, listOf(block), Vec2(3.7, 3.8))
        val result = maze.move(Sphere(maze.start), Vec2(0.1, 0.0))
        assertPosition(Vec2(3.8, 3.8), result.position)
        assertTrue(maze.canOccupy(result))
    }

    @Test
    fun glancingCornerContactDeflectsWithoutPenetration() {
        val maze = Maze(10, 10, listOf(block), Vec2(2.0, 3.82))
        val result = maze.move(Sphere(maze.start), Vec2(6.0, 0.0))
        assertTrue(result.position.x > 4.0)
        assertTrue(result.position.y < 3.82)
        assertTrue(maze.canOccupy(result))
    }

    @Test
    fun tangentMovementAndTileSeamsDoNotStick() {
        val walls = (0..9).map { Wall(4.0, it.toDouble(), 5.0, it + 1.0) }
        val maze = Maze(10, 10, walls, Vec2(3.76, 1.0))
        val result = maze.move(Sphere(maze.start), Vec2(0.0, 8.0))
        assertPosition(Vec2(3.76, 9.0), result.position)
        assertTrue(maze.canOccupy(result))
    }

    @Test
    fun worldBoundsRemainSolidWithoutBoundaryWalls() {
        val maze = Maze(10, 10, emptyList(), Vec2(5.0, 5.0))
        assertPosition(Vec2(9.76, 9.76), maze.move(Sphere(maze.start), Vec2(100.0, 100.0)).position)
        assertPosition(Vec2(0.24, 0.24), maze.move(Sphere(maze.start), Vec2(-100.0, -100.0)).position)
    }

    @Test
    fun repeatedVariedMovesRemainOutsideEveryWall() {
        val maze = FirstMaze.world
        val random = Random(42)
        var sphere = Sphere(maze.start)
        repeat(2_000) { index ->
            val direction = MoveInput(random.nextInt(-1, 2).toDouble(), random.nextInt(-1, 2).toDouble()).direction
            sphere = maze.move(sphere, direction * if (index % 5 == 0) 100.0 else 0.15)
            assertTrue(maze.canOccupy(sphere), "Penetration at move $index: $sphere")
        }
    }
}
