package de.acorn42.portal.world

/** No rendering, clocks, or platform input APIs live in the simulation. */
class WorldSimulation(
    val maze: Maze,
    initialSphere: Sphere = Sphere(maze.start),
    val movementSpeed: Double = 3.0,
) {
    var sphere: Sphere = initialSphere
        private set

    init {
        require(maze.canOccupy(initialSphere))
        require(movementSpeed.isFinite() && movementSpeed >= 0)
    }

    fun step(input: MoveInput, seconds: Double) {
        require(seconds.isFinite() && seconds >= 0)
        sphere = maze.move(sphere, input.direction * (movementSpeed * seconds))
    }
}

/** Accumulates frame time into fixed simulation ticks. Stalls drop excess time
 * instead of creating an unbounded backlog (or a jump after switching apps).
 */
class FixedStepClock(val stepSeconds: Double = 1.0 / 120.0, val maxStepsPerFrame: Int = 8) {
    private var accumulator = 0.0

    init {
        require(stepSeconds.isFinite() && stepSeconds > 0)
        require(maxStepsPerFrame > 0)
    }

    fun advance(elapsedSeconds: Double, step: (Double) -> Unit): Int {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0)
        accumulator += elapsedSeconds.coerceAtMost(stepSeconds * maxStepsPerFrame)
        var steps = 0
        while (steps < maxStepsPerFrame && accumulator + stepSeconds * 1e-9 >= stepSeconds) {
            step(stepSeconds)
            accumulator = (accumulator - stepSeconds).coerceAtLeast(0.0)
            steps++
        }
        return steps
    }

    fun reset() {
        accumulator = 0.0
    }
}
