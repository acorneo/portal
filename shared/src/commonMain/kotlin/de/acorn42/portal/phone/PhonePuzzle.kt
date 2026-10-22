package de.acorn42.portal.phone

import de.acorn42.portal.world.*
import kotlin.math.exp

/** Portrait screen axes: +x right, +y down. Values are gravity fractions, never normalized. */
class TiltCalibration {
    private var neutral: Vec2? = null
    val calibrated get() = neutral != null

    fun calibrate(gravity: Vec2): Boolean {
        // Leave at least 0.35 g of headroom on each screen axis. An upright
        // neutral pose would make one direction impossible with subtraction.
        if (!gravity.valid() || gravity.length > 0.65) {
            clear()
            return false
        }
        neutral = gravity
        return true
    }

    fun clear() { neutral = null }

    fun input(gravity: Vec2): Vec2 {
        val origin = neutral ?: return Vec2.Zero
        if (!gravity.valid()) return Vec2.Zero
        val delta = gravity - origin
        return boundedTilt(delta)
    }
}

private fun Vec2.valid() = x.isFinite() && y.isFinite() && x in -1.0..1.0 && y in -1.0..1.0

internal fun boundedTilt(value: Vec2): Vec2 {
    if (!value.x.isFinite() || !value.y.isFinite()) return Vec2.Zero
    val capped = value * (0.75 / value.length.coerceAtLeast(0.75))
    return if (capped.length < 0.025) Vec2.Zero else capped
}

object PhoneRoom {
    val maze = Maze(9, 13, listOf(
        Wall(0.0, 0.0, 9.0, 0.5), Wall(0.0, 12.5, 9.0, 13.0),
        Wall(0.0, 0.5, 0.5, 12.5), Wall(8.5, 0.5, 9.0, 12.5),
        Wall(0.5, 4.0, 6.4, 4.5), Wall(2.6, 8.0, 8.5, 8.5),
    ), Vec2(1.5, 2.0))
    val exit = Vec2(7.3, 11.0)
    const val exitRadius = 0.65
}

data class PhoneState(val sphere: Sphere, val velocity: Vec2 = Vec2.Zero, val completed: Boolean = false)
data class PhoneEvents(val impactSpeed: Double = 0.0, val exited: Boolean = false)

/** Offline room physics. The host supplies fixed ticks and owns all native effects. */
class PhonePuzzle(
    val maze: Maze = PhoneRoom.maze,
    val exit: Vec2 = PhoneRoom.exit,
    initialSphere: Sphere = Sphere(maze.start),
) {
    private val initial = initialSphere
    var state = PhoneState(initial)
        private set

    init { require(maze.canOccupy(initial)) }

    fun reset() { state = PhoneState(initial) }
    fun stop() { state = state.copy(velocity = Vec2.Zero) }

    /** Restore a validated in-memory session checkpoint; offline reset is unchanged. */
    fun restore(checkpoint: PhoneState) {
        require(maze.canOccupy(checkpoint.sphere) && checkpoint.sphere.radius == initial.radius)
        require(checkpoint.velocity.x.isFinite() && checkpoint.velocity.y.isFinite() && checkpoint.velocity.length <= 4.000001)
        require(!checkpoint.completed || (checkpoint.sphere.position - exit).length <= PhoneRoom.exitRadius)
        state = checkpoint
    }

    fun step(input: Vec2, seconds: Double): PhoneEvents {
        require(seconds.isFinite() && seconds > 0 && seconds <= 1.0 / 30.0)
        if (state.completed) return PhoneEvents()
        val force = boundedTilt(input)
        var velocity = (state.velocity + force * (12.0 * seconds)) * exp(-2.2 * seconds)
        velocity *= 4.0 / velocity.length.coerceAtLeast(4.0)
        val requested = velocity * seconds
        val next = maze.move(state.sphere, requested)
        val removed = requested - (next.position - state.sphere.position)
        var impact = 0.0
        if (removed.length > 1e-7) {
            val normal = removed * (1.0 / removed.length)
            impact = velocity.dot(normal).coerceAtLeast(0.0)
            velocity -= normal * impact
        }
        val completed = (next.position - exit).length <= PhoneRoom.exitRadius
        state = PhoneState(next, if (completed) Vec2.Zero else velocity, completed)
        return PhoneEvents(impact, completed)
    }
}
