@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.acorn42.portal.phone

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.acorn42.portal.world.*
import kotlinx.cinterop.useContents
import platform.CoreMotion.*
import platform.Foundation.*
import platform.UIKit.*

/** Main-thread-only owner. Motion callbacks, Compose frames and lifecycle share the main queue. */
internal class PhoneHost {
    val puzzle = PhonePuzzle()
    val clock = FixedStepClock()
    private val calibration = TiltCalibration()
    private val motion = CMMotionManager()
    private val impact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val success = UINotificationFeedbackGenerator()
    private val selection = UISelectionFeedbackGenerator()
    private var generation = 0
    private var startedAt = 0.0
    private var sampleAt = 0.0
    private var sample: Vec2? = null
    private var touchInput = Vec2.Zero
    private var lastImpact = -1.0
    private var failed = false
    var active by mutableStateOf(false)
        private set
    var touchMode by mutableStateOf(!motion.deviceMotionAvailable)
        private set
    var ready by mutableStateOf(false)
        private set
    var state by mutableStateOf(puzzle.state)
        private set
    var message by mutableStateOf(if (touchMode) "Motion unavailable · use touch pad" else "Hold phone fairly flat, then calibrate")
        private set
    var hasSample by mutableStateOf(false)
        private set

    private fun now() = NSProcessInfo.processInfo.systemUptime

    fun start() {
        if (active) return
        active = true
        clock.reset()
        if (state.completed) return
        if (touchMode) {
            ready = true
            message = "Touch fallback · drag from the pad’s center"
            return
        }
        if (failed) {
            message = "Motion unavailable · choose touch fallback"
            return
        }
        if (!motion.deviceMotionAvailable) {
            failMotion("Motion unavailable · choose touch fallback")
            return
        }
        val token = ++generation
        startedAt = now()
        motion.deviceMotionUpdateInterval = 1.0 / 60.0
        motion.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { data, error ->
            if (token != generation || !active || touchMode) return@startDeviceMotionUpdatesToQueue
            if (error != null) {
                // No unrelated fitness/activity permission request. Denial is a terminal input failure.
                val denied = error.domain == CMErrorDomain &&
                    (error.code == CMErrorNotAuthorized.toLong() || error.code == CMErrorMotionActivityNotAuthorized.toLong())
                failMotion(if (denied) "Motion access denied · use touch fallback" else "Motion interrupted · use touch fallback")
            } else if (data != null && data.timestamp >= startedAt && now() - data.timestamp in 0.0..0.5) {
                val value = data.gravity.useContents { Vec2(x, -y) }
                if (value.x.isFinite() && value.y.isFinite() && value.x in -1.0..1.0 && value.y in -1.0..1.0) {
                    sample = value
                    sampleAt = data.timestamp
                    hasSample = true
                }
            }
        }
    }

    fun pause() {
        active = false
        stopInput()
        message = "Paused · calibrate again after returning"
    }

    private fun stopInput() {
        generation++ // Ignore callbacks queued before stop/background/disposal.
        motion.stopDeviceMotionUpdates()
        sample = null
        hasSample = false
        calibration.clear()
        touchInput = Vec2.Zero
        ready = false
        clock.reset()
        puzzle.stop()
        state = puzzle.state
    }

    private fun failMotion(reason: String) {
        failed = true
        stopInput()
        message = reason
    }

    fun useTouch() {
        stopInput()
        touchMode = true
        ready = active && !state.completed
        message = "Touch fallback · drag from the pad’s center"
    }

    fun calibrate() {
        val value = sample ?: return
        if (!active || touchMode || now() - sampleAt > 0.5) return
        puzzle.stop()
        state = puzzle.state
        clock.reset()
        ready = calibration.calibrate(value)
        message = if (ready) "Neutral set · tilt gently toward the exit"
            else "Hold phone flatter, then calibrate again"
        if (ready) selection.selectionChanged()
    }

    fun touch(value: Vec2) { touchInput = if (active && ready && touchMode) boundedTilt(value) else Vec2.Zero }

    fun reset() {
        stopInput()
        puzzle.reset()
        state = puzzle.state
        lastImpact = -1.0
        selection.selectionChanged()
        val wasActive = active
        active = false
        if (wasActive) start()
        if (!touchMode && failed) message = "Motion unavailable · choose touch fallback"
        else if (!touchMode) message = "Room reset · hold flatter and calibrate"
        else message = "Room reset · drag from the pad’s center"
    }

    fun restore(checkpoint: PhoneState) {
        pause()
        puzzle.restore(checkpoint.copy(velocity = Vec2.Zero))
        state = puzzle.state
        lastImpact = -1.0
    }

    fun frame(elapsed: Double) {
        if (!active || state.completed) return
        if (!touchMode && !failed && now() - (if (hasSample) sampleAt else startedAt) > 1.0) {
            failMotion("No fresh motion data · use touch fallback")
        }
        if (!ready) return
        val input = if (touchMode) touchInput else sample?.let(calibration::input) ?: Vec2.Zero
        clock.advance(elapsed) { seconds ->
            val events = puzzle.step(input, seconds)
            if (events.exited) {
                stopInput()
                message = "Exit reached · offline room complete"
                success.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            } else if (events.impactSpeed > 0.35 && now() - lastImpact > 0.12) {
                impact.impactOccurred()
                lastImpact = now()
            }
        }
        state = puzzle.state
    }
}

@Composable
internal fun rememberPhoneHost(enabled: Boolean = true): PhoneHost {
    val host = remember { PhoneHost() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(host, lifecycle, enabled) {
        val screen = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (enabled) host.start() else host.pause()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> host.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(screen)
        val center = NSNotificationCenter.defaultCenter
        val inactive = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { host.pause() }
        val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { host.pause() }
        val foreground = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
            if (enabled && lifecycle.currentState == Lifecycle.State.RESUMED) host.start()
        }
        if (enabled && lifecycle.currentState == Lifecycle.State.RESUMED && UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) host.start()
        else host.pause()
        onDispose {
            lifecycle.removeObserver(screen)
            center.removeObserver(inactive)
            center.removeObserver(background)
            center.removeObserver(foreground)
            host.pause()
        }
    }
    LaunchedEffect(host, host.active, host.state.completed) {
        if (host.active && !host.state.completed) {
            var previous = withFrameNanos { it }
            while (true) {
                val frame = withFrameNanos { it }
                host.frame(((frame - previous) / 1e9).coerceAtLeast(0.0))
                previous = frame
            }
        }
    }
    return host
}
