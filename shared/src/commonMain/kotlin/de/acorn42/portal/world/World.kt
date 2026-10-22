package de.acorn42.portal.world

import kotlin.math.sqrt

/** World coordinates use maze tiles, with positive Y pointing down. */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vec2(x * scale, y * scale)
    fun dot(other: Vec2) = x * other.x + y * other.y
    val length: Double get() = sqrt(dot(this))

    companion object {
        val Zero = Vec2(0.0, 0.0)
    }
}

data class Wall(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left < right && top < bottom)
    }

    fun distanceTo(point: Vec2): Double =
        (point - Vec2(point.x.coerceIn(left, right), point.y.coerceIn(top, bottom))).length
}

data class Sphere(val position: Vec2, val radius: Double = 0.24) {
    init {
        require(position.x.isFinite() && position.y.isFinite())
        require(radius.isFinite() && radius > 0.0)
    }
}

class Maze(val width: Int, val height: Int, walls: List<Wall>, val start: Vec2) {
    val walls: List<Wall> = walls.toList()

    init {
        require(width > 0 && height > 0)
        require(this.walls.all { it.left >= 0 && it.top >= 0 && it.right <= width && it.bottom <= height })
        require(canOccupy(Sphere(start))) { "The start must have room for the sphere" }
    }

    /** Bounds are solid even for worlds whose wall list does not enclose the map. */
    fun canOccupy(sphere: Sphere, tolerance: Double = 1e-8): Boolean = with(sphere) {
        position.x >= radius - tolerance && position.y >= radius - tolerance &&
            position.x <= width - radius + tolerance && position.y <= height - radius + tolerance &&
            walls.all { it.distanceTo(position) >= radius - tolerance }
    }
}

/** Platform-neutral directional intent, independent of keyboard or touch APIs. */
data class MoveInput(val horizontal: Double = 0.0, val vertical: Double = 0.0) {
    init {
        require(horizontal in -1.0..1.0 && vertical in -1.0..1.0)
    }

    val direction: Vec2 get() {
        val vector = Vec2(horizontal, vertical)
        return vector * (1.0 / vector.length.coerceAtLeast(1.0))
    }
}
