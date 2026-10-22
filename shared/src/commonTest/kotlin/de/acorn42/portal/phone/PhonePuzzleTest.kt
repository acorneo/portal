package de.acorn42.portal.phone

import de.acorn42.portal.world.*
import kotlin.test.*

class PhonePuzzleTest {
    @Test fun calibrationPreservesMagnitudeAndRejectsInvalidInput() {
        val tilt = TiltCalibration()
        assertEquals(Vec2.Zero, tilt.input(Vec2(0.3, 0.2)))
        assertTrue(tilt.calibrate(Vec2(0.1, -0.3)))
        assertEquals(0.2, tilt.input(Vec2(0.3, -0.3)).x, 1e-10)
        assertEquals(0.4, tilt.input(Vec2(0.5, -0.3)).x, 1e-10)
        assertEquals(Vec2.Zero, tilt.input(Vec2(Double.NaN, 0.0)))
        assertFalse(tilt.calibrate(Vec2(2.0, 0.0)))
        assertEquals(Vec2.Zero, tilt.input(Vec2(0.11, -0.3)))
        assertEquals(0.75, tilt.input(Vec2(1.0, 1.0)).length, 1e-10)
        tilt.clear()
        assertFalse(tilt.calibrated)
    }

    @Test fun inertiaDampingAndMagnitude() {
        val low = PhonePuzzle()
        val high = PhonePuzzle()
        repeat(20) { low.step(Vec2(0.1, 0.0), dt); high.step(Vec2(0.3, 0.0), dt) }
        assertTrue(high.state.velocity.x > low.state.velocity.x * 2.9)
        val before = low.state
        low.step(Vec2.Zero, dt)
        assertTrue(low.state.sphere.position.x > before.sphere.position.x)
        assertTrue(low.state.velocity.x < before.velocity.x)
        repeat(600) { low.step(Vec2.Zero, dt) }
        assertTrue(low.state.velocity.length < 0.0001)
    }

    @Test fun wallsAndCornersStaySolidAndStopInwardVelocity() {
        val puzzle = PhonePuzzle()
        var impacts = 0
        repeat(2400) {
            if (puzzle.step(Vec2(-0.75, -0.4), dt).impactSpeed > 0.01) impacts++
            assertTrue(puzzle.maze.canOccupy(puzzle.state.sphere, 1e-6))
        }
        assertTrue(impacts > 0)
        assertTrue(puzzle.state.velocity.length < 0.1)
        puzzle.stop()
        assertEquals(Vec2.Zero, puzzle.state.velocity)
        puzzle.reset()
        assertEquals(PhoneState(Sphere(puzzle.maze.start)), puzzle.state)
    }

    @Test fun handmadeRoomCanBeSolvedAndExitFiresOnce() {
        val puzzle = PhonePuzzle()
        var exits = 0
        // Closed-loop steering through the right gap, left gap, then exit.
        for (target in listOf(Vec2(7.4, 2.5), Vec2(7.4, 6.4), Vec2(1.5, 6.4), Vec2(1.5, 10.8), PhoneRoom.exit)) {
            var ticks = 0
            while ((puzzle.state.sphere.position - target).length > 0.18 && !puzzle.state.completed && ticks++ < 4000) {
                val input = (target - puzzle.state.sphere.position) * 0.6 - puzzle.state.velocity * 0.45
                if (puzzle.step(input, dt).exited) exits++
                assertTrue(puzzle.maze.canOccupy(puzzle.state.sphere, 1e-6))
            }
            assertTrue(ticks < 4000, "Could not reach $target: ${puzzle.state}")
        }
        assertTrue(puzzle.state.completed)
        assertEquals(1, exits)
        val final = puzzle.state
        repeat(50) { assertFalse(puzzle.step(Vec2(0.75, 0.75), dt).exited) }
        assertEquals(final, puzzle.state)
        puzzle.reset()
        assertFalse(puzzle.state.completed)
    }

    @Test fun fixedClockProducesSamePhysicsAcrossFrameRatesAndDropsPauseBacklog() {
        fun run(hz: Int): PhoneState {
            val puzzle = PhonePuzzle()
            val clock = FixedStepClock()
            repeat(hz) { clock.advance(1.0 / hz) { puzzle.step(Vec2(0.2, 0.0), it) } }
            return puzzle.state
        }
        assertEquals(run(30), run(120))
        val clock = FixedStepClock()
        clock.advance(dt / 2) { fail("No full tick yet") }
        clock.reset()
        assertEquals(0, clock.advance(dt / 2) {})
        assertEquals(8, clock.advance(10.0) {})
    }

    companion object { const val dt = 1.0 / 120.0 }
}
