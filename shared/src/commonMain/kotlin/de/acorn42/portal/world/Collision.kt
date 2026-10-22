package de.acorn42.portal.world

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Continuous circle vs rectangle sweep: flat faces plus the four rounded corners.
 * A rectangular expansion alone would incorrectly block the open space near corners.
 */
internal fun Maze.move(sphere: Sphere, displacement: Vec2): Sphere {
    var position = sphere.position
    var remaining = displacement
    val radius = sphere.radius
    // Each contact removes inward motion. If a pathological arrangement exhausts the
    // budget, stop at the last safe position rather than applying unchecked movement.
    repeat(8) {
        if (remaining.length < 1e-10) return sphere.copy(position = position)
        var firstTime = Double.POSITIVE_INFINITY
        val normals = mutableListOf<Vec2>()

        fun contact(time: Double, normal: Vec2) {
            if (time < -1e-10 || time > 1.0 || remaining.dot(normal) >= -1e-10) return
            val clamped = time.coerceAtLeast(0.0)
            if (clamped < firstTime - 1e-10) {
                firstTime = clamped
                normals.clear()
            }
            if (abs(clamped - firstTime) <= 1e-10) normals.add(normal)
        }

        // World edges are planes, so escaping an unenclosed map is impossible.
        if (remaining.x < 0) contact((radius - position.x) / remaining.x, Vec2(1.0, 0.0))
        if (remaining.x > 0) contact((width - radius - position.x) / remaining.x, Vec2(-1.0, 0.0))
        if (remaining.y < 0) contact((radius - position.y) / remaining.y, Vec2(0.0, 1.0))
        if (remaining.y > 0) contact((height - radius - position.y) / remaining.y, Vec2(0.0, -1.0))

        for (wall in walls) {
            // Reject walls outside the swept circle's bounding box.
            if (max(position.x, position.x + remaining.x) + radius < wall.left ||
                minOf(position.x, position.x + remaining.x) - radius > wall.right ||
                max(position.y, position.y + remaining.y) + radius < wall.top ||
                minOf(position.y, position.y + remaining.y) - radius > wall.bottom
            ) continue

            if (remaining.x != 0.0) {
                for ((face, normal) in listOf(
                    wall.left - radius to Vec2(-1.0, 0.0),
                    wall.right + radius to Vec2(1.0, 0.0),
                )) {
                    val time = (face - position.x) / remaining.x
                    val y = position.y + remaining.y * time
                    if (y in wall.top..wall.bottom) contact(time, normal)
                }
            }
            if (remaining.y != 0.0) {
                for ((face, normal) in listOf(
                    wall.top - radius to Vec2(0.0, -1.0),
                    wall.bottom + radius to Vec2(0.0, 1.0),
                )) {
                    val time = (face - position.y) / remaining.y
                    val x = position.x + remaining.x * time
                    if (x in wall.left..wall.right) contact(time, normal)
                }
            }

            for (cornerX in listOf(wall.left, wall.right)) {
                for (cornerY in listOf(wall.top, wall.bottom)) {
                    val offset = position - Vec2(cornerX, cornerY)
                    val a = remaining.dot(remaining)
                    val b = offset.dot(remaining)
                    val c = offset.dot(offset) - radius * radius
                    val discriminant = b * b - a * c
                    if (b >= 0 || discriminant < 0) continue
                    // Equivalent to (-b - sqrt(discriminant)) / a, but avoids
                    // cancellation when the sphere starts very close to contact.
                    val time = c / (-b + sqrt(discriminant))
                    val hitOffset = offset + remaining * time
                    val onCornerX = if (cornerX == wall.left) hitOffset.x <= 0 else hitOffset.x >= 0
                    val onCornerY = if (cornerY == wall.top) hitOffset.y <= 0 else hitOffset.y >= 0
                    if (onCornerX && onCornerY) contact(time, hitOffset * (1.0 / radius))
                }
            }
        }

        if (normals.isEmpty()) return sphere.copy(position = position + remaining)
        // Leave a tiny separation to keep roundoff from putting a resting sphere inside a wall.
        val safeTime = (firstTime - 1e-8 / remaining.length).coerceAtLeast(0.0)
        position += remaining * safeTime
        remaining *= 1.0 - firstTime
        for (normal in normals) {
            val inward = remaining.dot(normal)
            if (inward < 0) remaining -= normal * inward
        }
    }
    return sphere.copy(position = position)
}
