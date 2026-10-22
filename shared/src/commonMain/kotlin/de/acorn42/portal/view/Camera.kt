package de.acorn42.portal.view

import de.acorn42.portal.world.Vec2
import kotlin.math.expm1

/** Pixel dimensions and scale are supplied by the renderer, never the game engine. */
data class Viewport(val width: Double, val height: Double, val pixelsPerTile: Double) {
    init {
        require(width.isFinite() && width > 0)
        require(height.isFinite() && height > 0)
        require(pixelsPerTile.isFinite() && pixelsPerTile > 0)
    }

    val worldSize: Vec2 get() = Vec2(width / pixelsPerTile, height / pixelsPerTile)
}

/** The previous target lets us integrate a linearly moving target analytically,
 * rather than using a per-frame lerp that changes its lag with the display rate.
 */
data class Camera(val center: Vec2, val target: Vec2 = center) {
    init {
        require(listOf(center.x, center.y, target.x, target.y).all { it.isFinite() })
    }

    fun bounded(viewport: Viewport, worldSize: Vec2): Camera {
        require(worldSize.x.isFinite() && worldSize.y.isFinite() && worldSize.x > 0 && worldSize.y > 0)
        fun constrain(point: Vec2): Vec2 {
            fun axis(value: Double, world: Double, view: Double): Double =
                if (view >= world) world / 2 else value.coerceIn(view / 2, world - view / 2)
            return Vec2(axis(point.x, worldSize.x, viewport.worldSize.x), axis(point.y, worldSize.y, viewport.worldSize.y))
        }
        return Camera(constrain(center), constrain(target))
    }

    fun follow(
        nextTarget: Vec2,
        seconds: Double,
        viewport: Viewport,
        worldSize: Vec2,
        response: Double = 8.0,
    ): Camera {
        require(seconds.isFinite() && seconds >= 0)
        require(response.isFinite() && response > 0)
        require(nextTarget.x.isFinite() && nextTarget.y.isFinite())
        val current = bounded(viewport, worldSize)
        val next = Camera(nextTarget).bounded(viewport, worldSize).center
        if (seconds == 0.0) return current.copy(target = next)
        val time = response * seconds
        val alpha = -expm1(-time)
        // The series avoids cancellation in 1 - alpha/time for tiny frame times.
        val movingWeight = if (time < 1e-5) time / 2 - time * time / 6 else 1 - alpha / time
        val position = current.center + (current.target - current.center) * alpha + (next - current.target) * movingWeight
        return Camera(position, next).bounded(viewport, worldSize)
    }

    fun worldToScreen(point: Vec2, viewport: Viewport): Vec2 =
        (point - center) * viewport.pixelsPerTile + Vec2(viewport.width / 2, viewport.height / 2)

    fun screenToWorld(point: Vec2, viewport: Viewport): Vec2 =
        (point - Vec2(viewport.width / 2, viewport.height / 2)) * (1 / viewport.pixelsPerTile) + center
}
