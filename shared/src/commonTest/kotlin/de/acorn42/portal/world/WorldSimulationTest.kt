package de.acorn42.portal.world

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorldSimulationTest {
    private fun openWorld() = Maze(100, 100, emptyList(), Vec2(10.0, 10.0))

    @Test
    fun movementUsesSecondsAndStopsWithoutInput() {
        val simulation = WorldSimulation(openWorld())
        simulation.step(MoveInput(1.0), 0.5)
        assertPosition(Vec2(11.5, 10.0), simulation.sphere.position)
        simulation.step(MoveInput(), 5.0)
        assertPosition(Vec2(11.5, 10.0), simulation.sphere.position)
        simulation.step(MoveInput(-1.0), 0.5)
        assertPosition(Vec2(10.0, 10.0), simulation.sphere.position)
    }

    @Test
    fun diagonalInputHasTheSameSpeedAsCardinalInput() {
        val simulation = WorldSimulation(openWorld())
        simulation.step(MoveInput(1.0, -1.0), 1.0)
        assertEquals(3.0, (simulation.sphere.position - simulation.maze.start).length, 1e-9)
        assertPosition(Vec2(10 + 3 / sqrt(2.0), 10 - 3 / sqrt(2.0)), simulation.sphere.position)
    }

    @Test
    fun framePartitionsProduceTheSameMovement() {
        fun run(frames: List<Double>): Vec2 {
            val simulation = WorldSimulation(openWorld())
            val clock = FixedStepClock()
            frames.forEach { elapsed -> clock.advance(elapsed) { simulation.step(MoveInput(0.0, 1.0), it) } }
            return simulation.sphere.position
        }
        val expected = Vec2(10.0, 13.0)
        assertPosition(expected, run(List(30) { 1.0 / 30 }))
        assertPosition(expected, run(List(60) { 1.0 / 60 }))
        assertPosition(expected, run(List(144) { 1.0 / 144 }))
        assertPosition(expected, run(List(20) { listOf(0.005, 0.015, 0.03) }.flatten()))
    }

    @Test
    fun invalidSimulationConfigurationAndTimeAreRejected() {
        assertFailsWith<IllegalArgumentException> { WorldSimulation(openWorld(), movementSpeed = Double.NaN) }
        assertFailsWith<IllegalArgumentException> { WorldSimulation(openWorld(), Sphere(Vec2(0.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { WorldSimulation(openWorld()).step(MoveInput(), -1.0) }
        assertFailsWith<IllegalArgumentException> { MoveInput(Double.NaN) }
    }
}

internal fun assertPosition(expected: Vec2, actual: Vec2, tolerance: Double = 1e-7) {
    assertEquals(expected.x, actual.x, tolerance, "x coordinate")
    assertEquals(expected.y, actual.y, tolerance, "y coordinate")
}
