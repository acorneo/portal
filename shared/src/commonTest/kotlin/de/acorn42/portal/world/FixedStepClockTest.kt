package de.acorn42.portal.world

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FixedStepClockTest {
    @Test
    fun partialFramesAccumulateAndEveryStepHasTheSameDuration() {
        val clock = FixedStepClock(stepSeconds = 0.01)
        val durations = mutableListOf<Double>()
        assertEquals(0, clock.advance(0.004) { durations.add(it) })
        assertEquals(2, clock.advance(0.021) { durations.add(it) })
        assertEquals(listOf(0.01, 0.01), durations)
        assertEquals(1, clock.advance(0.005) { durations.add(it) })
        assertEquals(0, clock.advance(0.0) { error("No pending step") })
    }

    @Test
    fun stallHasBoundedCatchUpAndDoesNotLeaveBacklog() {
        val clock = FixedStepClock(stepSeconds = 0.01, maxStepsPerFrame = 4)
        assertEquals(4, clock.advance(60.0) { assertEquals(0.01, it) })
        assertEquals(0, clock.advance(0.0) { error("Stale backlog") })
        assertEquals(1, clock.advance(0.01) {})
    }

    @Test
    fun stallPreservesThePreexistingFractionalRemainder() {
        val clock = FixedStepClock(stepSeconds = 0.01, maxStepsPerFrame = 4)
        clock.advance(0.005) { error("Partial step") }
        assertEquals(4, clock.advance(60.0) {})
        assertEquals(1, clock.advance(0.005) {})
    }

    @Test
    fun resetDiscardsPartialTimeOnFocusChanges() {
        val clock = FixedStepClock(stepSeconds = 0.01)
        clock.advance(0.009) { error("Partial step") }
        clock.reset()
        assertEquals(0, clock.advance(0.001) { error("Time before reset must not carry over") })
    }

    @Test
    fun invalidTimeAndConfigurationAreRejected() {
        assertFailsWith<IllegalArgumentException> { FixedStepClock(stepSeconds = 0.0) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock(maxStepsPerFrame = 0) }
        assertFailsWith<IllegalArgumentException> { FixedStepClock().advance(Double.NaN) {} }
        assertFailsWith<IllegalArgumentException> { FixedStepClock().advance(-0.1) {} }
    }
}
