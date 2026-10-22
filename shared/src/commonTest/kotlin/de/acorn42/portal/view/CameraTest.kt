package de.acorn42.portal.view

import de.acorn42.portal.world.Vec2
import de.acorn42.portal.world.assertPosition
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CameraTest {
    private val viewport = Viewport(640.0, 384.0, 64.0)
    private val world = Vec2(100.0, 100.0)

    @Test
    fun stationaryTargetHasTheSameResultAtDifferentFrameRates() {
        val start = Vec2(20.0, 20.0)
        val target = Vec2(30.0, 35.0)
        val expected = target + (start - target) * exp(-8.0)
        for (frames in listOf(1, 30, 60, 144)) {
            var camera = Camera(start, target)
            repeat(frames) { camera = camera.follow(target, 1.0 / frames, viewport, world) }
            assertPosition(expected, camera.center, 1e-10)
        }
    }

    @Test
    fun movingTargetHasTheSameLagAtDifferentFrameRates() {
        val start = Vec2(30.0, 30.0)
        val velocity = Vec2(4.0, 2.0)
        val expected = start + velocity - velocity * ((1 - exp(-8.0)) / 8.0)
        for (frames in listOf(1, 30, 60, 144)) {
            var camera = Camera(start)
            repeat(frames) { index ->
                val target = start + velocity * ((index + 1.0) / frames)
                camera = camera.follow(target, 1.0 / frames, viewport, world)
            }
            assertPosition(expected, camera.center, 1e-10)
        }
    }

    @Test
    fun boundsStopAtAllFourWorldEdges() {
        assertPosition(Vec2(5.0, 3.0), Camera(Vec2(-100.0, -100.0)).bounded(viewport, world).center)
        assertPosition(Vec2(95.0, 97.0), Camera(Vec2(200.0, 200.0)).bounded(viewport, world).center)
        var camera = Camera(Vec2(50.0, 50.0))
        repeat(100) {
            camera = camera.follow(Vec2(200.0, -100.0), 0.02, viewport, world)
            assertTrue(camera.center.x in 5.0..95.0 && camera.center.y in 3.0..97.0)
        }
    }

    @Test
    fun worldsSmallerThanViewportAreCenteredIndependentlyPerAxis() {
        assertPosition(Vec2(2.0, 1.5), Camera(Vec2(50.0, 50.0)).bounded(viewport, Vec2(4.0, 3.0)).center)
        assertPosition(Vec2(2.0, 20.0), Camera(Vec2(50.0, 20.0)).bounded(viewport, Vec2(4.0, 40.0)).center)
        assertPosition(Vec2(20.0, 2.0), Camera(Vec2(20.0, 50.0)).bounded(viewport, Vec2(40.0, 4.0)).center)
        assertPosition(Vec2(5.0, 3.0), Camera(Vec2(20.0, 50.0)).bounded(viewport, Vec2(10.0, 6.0)).center)
    }

    @Test
    fun resizeClampsImmediatelyAndCanThenFollowAgain() {
        val small = Viewport(256.0, 256.0, 64.0)
        val large = Viewport(1280.0, 1024.0, 64.0)
        val world = Vec2(16.0, 12.0)
        val nearEdge = Camera(Vec2(14.0, 10.0)).bounded(small, world)
        val resized = nearEdge.bounded(large, world)
        assertPosition(Vec2(8.0, 6.0), resized.center)
        val following = resized.follow(Vec2(14.0, 10.0), 0.1, small, world)
        assertTrue(following.center.x > 8.0 && following.center.x <= 14.0)
        assertTrue(following.center.y > 6.0 && following.center.y <= 10.0)
    }

    @Test
    fun transformsUseTheCameraAndRoundTripAfterResize() {
        for (view in listOf(viewport, Viewport(1200.0, 500.0, 80.0))) {
            val camera = Camera(Vec2(30.0, 40.0)).bounded(view, world)
            assertPosition(Vec2(view.width / 2, view.height / 2), camera.worldToScreen(camera.center, view))
            val point = Vec2(32.25, 37.75)
            assertPosition(point, camera.screenToWorld(camera.worldToScreen(point, view), view))
            val next = camera.worldToScreen(camera.center + Vec2(1.0, 0.0), view)
            assertEquals(view.width / 2 + view.pixelsPerTile, next.x, 1e-9)
        }
    }

    @Test
    fun zeroTinyAndLongFramesStayFiniteAndDoNotOvershoot() {
        val target = Vec2(40.0, 40.0)
        val camera = Camera(Vec2(30.0, 30.0), target)
        assertPosition(camera.center, camera.follow(target, 0.0, viewport, world).center)
        val tiny = camera.follow(target, 1e-12, viewport, world)
        assertTrue(tiny.center.x > 30.0 && tiny.center.x < 40.0)
        assertPosition(target, camera.follow(target, 60.0, viewport, world).center)
    }

    @Test
    fun invalidCameraAndViewportInputsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Viewport(0.0, 100.0, 64.0) }
        assertFailsWith<IllegalArgumentException> { Viewport(100.0, 100.0, Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Camera(Vec2(Double.NaN, 0.0)) }
        assertFailsWith<IllegalArgumentException> { Camera(Vec2.Zero).follow(Vec2.Zero, -1.0, viewport, world) }
        assertFailsWith<IllegalArgumentException> { Camera(Vec2.Zero).bounded(viewport, Vec2(0.0, 1.0)) }
    }
}
