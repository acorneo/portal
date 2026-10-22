package de.acorn42.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.acorn42.portal.portal.*
import de.acorn42.portal.nearby.NearbyPanel
import de.acorn42.portal.world.FixedStepClock
import de.acorn42.portal.world.Sphere
import de.acorn42.portal.world.WorldSimulation
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener

fun main(args: Array<String>) = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PORTAL — Beyond the Screen",
        state = rememberWindowState(width = 1080.dp, height = 780.dp),
    ) {
        var nearbyMode by remember { mutableStateOf("--nearby-poc" in args) }
        val input = remember { DesktopInput() }
        var simulation by remember { mutableStateOf(WorldSimulation(PortalLevel.closedMaze)) }
        val host = remember { DesktopPortalHost() }
        val session by host.state.collectAsState()
        val hostInfo by host.info.collectAsState()
        val scope = rememberCoroutineScope()
        val addresses = remember { host.addresses() }
        var starting by remember { mutableStateOf(false) }
        var appliedReturn by remember { mutableStateOf<String?>(null) }
        val clock = remember { FixedStepClock() }
        val focusRequester = remember { FocusRequester() }
        var sphere by remember { mutableStateOf(simulation.sphere) }
        var visibility by remember { mutableStateOf(simulation.exploration.state) }
        var windowFocused by remember { mutableStateOf(window.isFocused) }
        var contentFocused by remember { mutableStateOf(false) }
        val active = windowFocused && contentFocused && session.desktopCanMove && !nearbyMode

        DisposableEffect(window) {
            window.minimumSize = Dimension(600, 620)
            val listener = object : WindowFocusListener {
                override fun windowGainedFocus(event: WindowEvent) {
                    input.clear()
                    windowFocused = true
                }

                override fun windowLostFocus(event: WindowEvent) {
                    input.clear()
                    windowFocused = false
                }
            }
            window.addWindowFocusListener(listener)
            onDispose {
                window.removeWindowFocusListener(listener)
                input.clear()
                host.close()
            }
        }

        LaunchedEffect(windowFocused, nearbyMode) {
            host.hostForeground(windowFocused && !nearbyMode)
            if (windowFocused && !nearbyMode) focusRequester.requestFocus()
        }

        LaunchedEffect(session.gateUnlocked, session.returnId) {
            if (session.gateUnlocked && session.returnId != appliedReturn) {
                simulation = WorldSimulation(PortalLevel.openMaze, Sphere(PortalLevel.exit), initialExplored = visibility.exploredCells)
                sphere = simulation.sphere
                visibility = simulation.exploration.state
                appliedReturn = session.returnId
            }
        }

        LaunchedEffect(active, simulation) {
            input.clear()
            clock.reset()
            if (active) {
                var previousFrame = withFrameNanos { it }
                while (true) {
                    withFrameNanos { now ->
                        val elapsed = (now - previousFrame).coerceAtLeast(0L) / 1_000_000_000.0
                        previousFrame = now
                        clock.advance(elapsed) { simulation.step(input.movement(), it) }
                        sphere = simulation.sphere
                        visibility = simulation.exploration.state
                        if (session.ready && session.phase == TransferPhase.Desktop && !session.gateUnlocked && PortalLevel.atEntrance(sphere.position)) host.enterPortal()
                    }
                }
            }
        }

        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6FFFE2))) {
            Column(Modifier.fillMaxSize().background(Color(0xFF080F1B))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { nearbyMode = !nearbyMode }) {
                        Text(if (nearbyMode) "Back to game / LAN portal" else "Nearby connection POC")
                    }
                    if (nearbyMode) Text("Game / LAN paused", color = Color(0xFF9AAEC4), fontSize = 11.sp)
                }
                if (nearbyMode) NearbyPanel(windowFocused)
                else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (!hostInfo.started) Button(
                            enabled = !starting,
                            onClick = {
                                starting = true
                                scope.launch {
                                    try { host.start() } catch (_: Exception) { /* HostInfo contains the actionable startup error. */ }
                                    finally { starting = false; focusRequester.requestFocus() }
                                }
                            },
                        ) { Text(if (starting) "Starting…" else "Start phone session") }
                        Column(Modifier.weight(1f)) {
                            if (hostInfo.started) {
                                Text("MAC  ${addresses.ifBlank { "No Wi-Fi IPv4 address" }} · port ${hostInfo.port}", color = Color.White, fontSize = 12.sp)
                                Text(if (session.clientId == null) "PAIR CODE  ${hostInfo.pairCode} · expires after 5 minutes" else "iPhone paired · keep both apps foreground", color = Color(0xFFBD91FF), fontSize = 12.sp)
                            }
                            Text(if (session.clientId != null) "Session progress retained while both processes stay open" else hostInfo.message, color = Color(0xFF9AAEC4), fontSize = 11.sp)
                        }
                        if (hostInfo.started && session.clientId == null) TextButton(onClick = { scope.launch { host.refreshCode() }; focusRequester.requestFocus() }) { Text("New code") }
                    }
                    App(
                        maze = simulation.maze,
                        sphere = sphere,
                        visibility = visibility,
                        controls = when {
                            session.gateUnlocked -> "Gate open · Sphere returned · WASD / Arrow keys to explore"
                            session.clientId != null && !session.ready -> "Paused · reconnect your iPhone and keep both apps foreground"
                            session.phase == TransferPhase.Phone -> "Continue on your iPhone · tilt through the pocket room to its exit"
                            session.phase == TransferPhase.OutboundPending -> "Sending sphere to your iPhone…"
                            session.phase == TransferPhase.ReturnPending -> "Returning sphere · waiting for iPhone acknowledgement…"
                            session.ready -> "WASD / Arrows · enter the violet ring to travel to your iPhone"
                            else -> "WASD / Arrows · start a session and pair an iPhone to activate the violet portal"
                        },
                        active = active,
                        sphereVisible = session.owner == ActiveDevice.Desktop,
                        portals = true,
                        gateUnlocked = session.gateUnlocked,
                        modifier = Modifier.weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                contentFocused = it.hasFocus
                                if (!it.hasFocus) input.clear()
                            }
                            .onPreviewKeyEvent { if (windowFocused) input.handle(it) else false }
                            .focusable()
                            .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } },
                    )
                }
            }
        }
    }
}
