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
import de.acorn42.portal.nearby.*
import de.acorn42.portal.world.FixedStepClock
import de.acorn42.portal.world.Sphere
import de.acorn42.portal.world.WorldSimulation
import java.awt.Dimension
import java.awt.Desktop
import java.awt.desktop.AppHiddenEvent
import java.awt.desktop.AppHiddenListener
import java.awt.event.*

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "PORTAL — Beyond the Screen",
        state = rememberWindowState(width = 1080.dp, height = 780.dp)) {
        val scope = rememberCoroutineScope()
        val host = remember { NearbyGameHost(scope) }
        val session by host.state.collectAsState()
        val currentSession by rememberUpdatedState(session)
        val nearby by host.bridge.state.collectAsState()
        val connectionMessage by host.message.collectAsState()
        val input = remember { DesktopInput() }
        var simulation by remember { mutableStateOf(WorldSimulation(PortalLevel.closedMaze)) }
        var sphere by remember { mutableStateOf(simulation.sphere) }
        var visibility by remember { mutableStateOf(simulation.exploration.state) }
        var appliedReturn by remember { mutableStateOf<String?>(null) }
        var appliedRun by remember { mutableStateOf(session.runId) }
        var appliedRoom by remember { mutableIntStateOf(1) }
        var checkpointFrame by remember { mutableIntStateOf(0) }
        val clock = remember { FixedStepClock() }
        val focusRequester = remember { FocusRequester() }
        var focused by remember { mutableStateOf(window.isFocused) }
        var visible by remember { mutableStateOf(window.isVisible) }
        var minimized by remember { mutableStateOf(false) }
        var appHidden by remember { mutableStateOf(false) }
        var contentFocused by remember { mutableStateOf(false) }
        val availability = DesktopAvailability(focused, visible, minimized, appHidden)
        val active = availability.keyboardAvailable && contentFocused && session.desktopCanMove

        DisposableEffect(window) {
            window.minimumSize = Dimension(600, 620)
            val focus = object : WindowFocusListener {
                override fun windowGainedFocus(event: WindowEvent) { input.clear(); focused = true }
                override fun windowLostFocus(event: WindowEvent) { input.clear(); focused = false }
            }
            val visibilityListener = object : ComponentAdapter() {
                override fun componentShown(event: ComponentEvent) { visible = true }
                override fun componentHidden(event: ComponentEvent) { input.clear(); visible = false }
            }
            val minimizedListener = object : WindowAdapter() {
                override fun windowIconified(event: WindowEvent) { input.clear(); minimized = true }
                override fun windowDeiconified(event: WindowEvent) { minimized = false }
            }
            val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop().takeIf { it.isSupported(Desktop.Action.APP_EVENT_HIDDEN) } else null
            val hiddenListener = object : AppHiddenListener {
                override fun appHidden(event: AppHiddenEvent) { input.clear(); appHidden = true }
                override fun appUnhidden(event: AppHiddenEvent) { appHidden = false }
            }
            desktop?.addAppEventListener(hiddenListener)
            window.addWindowFocusListener(focus); window.addComponentListener(visibilityListener); window.addWindowListener(minimizedListener)
            onDispose {
                window.removeWindowFocusListener(focus); window.removeComponentListener(visibilityListener); window.removeWindowListener(minimizedListener)
                desktop?.removeAppEventListener(hiddenListener)
                input.clear(); host.close()
            }
        }
        LaunchedEffect(availability.hostAvailable) { host.hostAvailable(availability.hostAvailable) }
        LaunchedEffect(focused) { if (focused) focusRequester.requestFocus() }
        LaunchedEffect(session.gateUnlocked, session.returnId, session.phoneRoom) {
            if (session.gateUnlocked && session.phoneRoom == 1 && session.returnId != appliedReturn) {
                simulation = WorldSimulation(PortalLevel.openMaze, Sphere(PortalLevel.exit), initialExplored = visibility.exploredCells)
                sphere = simulation.sphere; visibility = simulation.exploration.state; appliedReturn = session.returnId
            }
        }
        LaunchedEffect(session.runId, session.desktopRoom, session.desktop, session.chapter) {
            if (session.runId != appliedRun || session.desktopRoom != appliedRoom) {
                simulation = WorldSimulation(
                    PortalLevel.room(session.desktopRoom).maze,
                    Sphere(session.desktop.position()),
                )
                sphere = simulation.sphere
                visibility = simulation.exploration.state
                appliedRun = session.runId
                appliedRoom = session.desktopRoom
                appliedReturn = null
            } else if (!active && session.phase == TransferPhase.Desktop && session.desktopRoom == 1 &&
                session.desktop.position() != sphere.position
            ) {
                val restoredMaze = if (session.gateUnlocked) PortalLevel.openMaze else PortalLevel.closedMaze
                simulation = WorldSimulation(restoredMaze, Sphere(session.desktop.position()), initialExplored = visibility.exploredCells)
                sphere = simulation.sphere
                visibility = simulation.exploration.state
            }
        }
        LaunchedEffect(active, simulation) {
            input.clear(); clock.reset()
            if (active) {
                var previous = withFrameNanos { it }
                while (true) withFrameNanos { now ->
                    clock.advance((now - previous).coerceAtLeast(0L) / 1e9) { simulation.step(input.movement(), it) }
                    previous = now; sphere = simulation.sphere; visibility = simulation.exploration.state
                    if (++checkpointFrame % 6 == 0 && currentSession.phase == TransferPhase.Desktop) {
                        host.desktopCheckpoint(DesktopCheckpoint.from(currentSession.desktopRoom, sphere.position))
                    }
                    if (currentSession.ready && currentSession.phase == TransferPhase.Desktop && !currentSession.completed &&
                        currentSession.objective in setOf(ChapterObjective.ExploreFirstRoom, ChapterObjective.RollSecondPocketRoom) &&
                        PortalLevel.atPhonePortal(currentSession.desktopRoom, sphere.position)
                    ) host.enterPortal()
                }
            }
        }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6FFFE2))) {
            Column(Modifier.fillMaxSize().background(Color(0xFF080F1B))) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(if (nearby.peerName.isNotEmpty()) "iPhone · ${nearby.peerName}" else "Connect your nearby iPhone", color = Color.White, fontSize = 12.sp)
                        Text(connectionMessage.ifBlank { nearby.message }, color = Color(0xFF9AAEC4), fontSize = 11.sp)
                        if (nearby.phase == "awaitingApproval") Text("Compare on iPhone: ${nearby.fingerprint}", color = Color(0xFFBD91FF), fontSize = 12.sp)
                    }
                    TextButton(onClick = { host.newChapter(); simulation = WorldSimulation(PortalLevel.closedMaze); sphere = simulation.sphere; visibility = simulation.exploration.state; appliedRun = ""; appliedRoom = 1; focusRequester.requestFocus() }) { Text("New chapter") }
                    if (nearby.phase == "awaitingApproval") {
                        Button(onClick = { host.bridge.command("accept", nearby.requestID); focusRequester.requestFocus() }) { Text("Accept iPhone") }
                        TextButton(onClick = { host.bridge.command("reject", nearby.requestID); focusRequester.requestFocus() }) { Text("Reject") }
                    } else if (nearby.phase == "connected") {
                        TextButton(onClick = { host.bridge.command("disconnect"); focusRequester.requestFocus() }) { Text("Disconnect") }
                    } else if (nearby.phase == "stopped") {
                        Button(onClick = { host.bridge.command("advertise"); focusRequester.requestFocus() }) { Text("Find iPhone") }
                    }
                }
                App(simulation.maze, sphere, visibility,
                    controls = when {
                        session.completed -> "Chapter complete · New chapter starts a fresh run"
                        session.objective == ChapterObjective.ReachGreenExit -> "Gate open · reach the green exit to advance to room two"
                        session.objective == ChapterObjective.RollSecondPocketRoom -> "Room two · enter the violet portal to send the sphere to your iPhone"
                        session.gateUnlocked -> "Gate open · cross the amber gate · WASD / Arrow keys to explore"
                        session.clientId != null && !session.ready -> "Paused · reconnect the same iPhone to resume"
                        session.phase == TransferPhase.Phone -> "Continue on your iPhone · roll to its violet exit"
                        session.phase == TransferPhase.OutboundPending -> "Sending sphere to your iPhone…"
                        session.phase == TransferPhase.ReturnPending -> "Returning sphere to the maze…"
                        session.ready && session.desktopRoom == 1 -> "WASD / Arrows · enter the violet ring for your iPhone; return and reach green to advance"
                        session.ready -> "WASD / Arrows · enter the violet ring to send the sphere to your iPhone"
                        else -> "Choose this Mac on your iPhone, then accept its nearby connection"
                    }, chapterLabel = "${session.chapter.toString().padStart(2, '0')} / ${PortalLevel.room(session.desktopRoom).title.uppercase()}",
                    active = active, sphereVisible = session.owner == ActiveDevice.Desktop, portals = true,
                    gateUnlocked = session.gateUnlocked, portalRoomId = session.desktopRoom,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged {
                        contentFocused = it.hasFocus; if (!it.hasFocus) input.clear()
                    }.onPreviewKeyEvent { if (availability.keyboardAvailable && session.desktopCanMove) input.handle(it) else false }
                        .focusable().pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } })
            }
        }
    }
}
