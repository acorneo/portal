package de.acorn42.portal.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.acorn42.portal.phone.PhonePuzzleScreen
import de.acorn42.portal.phone.PhoneScreen
import de.acorn42.portal.phone.rememberPhoneHost
import kotlinx.coroutines.flow.collect
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.*

/** Offline mode remains the initial screen. Pairing is explicitly selected. */
@Composable
fun PortalPhoneScreen() {
    val scope = rememberCoroutineScope()
    val client = remember { PortalClient(scope) }
    var pairedMode by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) }
    DisposableEffect(client) {
        val center = NSNotificationCenter.defaultCenter
        val inactive = center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { foreground = false }
        val background = center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { foreground = false }
        val active = center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { foreground = true }
        onDispose {
            center.removeObserver(inactive); center.removeObserver(background); center.removeObserver(active)
            client.close()
        }
    }
    LaunchedEffect(pairedMode, foreground) { client.foreground(pairedMode && foreground) }
    if (!pairedMode) PhoneScreen(onPair = { pairedMode = true })
    else PairedRoom(client, foreground, onOffline = { client.disconnect(); pairedMode = false })
}

@Composable
private fun PairedRoom(client: PortalClient, foreground: Boolean, onOffline: () -> Unit) {
    val connection by client.state.collectAsState()
    val state = connection.snapshot
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var prepared by remember { mutableStateOf<String?>(null) }
    val enabled = foreground && connection.connected && state?.phoneCanMove == true && prepared == state.transferId
    val host = rememberPhoneHost(enabled)

    LaunchedEffect(state?.phase, state?.transferId, state?.returnId) {
        if (state != null && state.phase in listOf(TransferPhase.OutboundPending, TransferPhase.Phone) && prepared != state.transferId) {
            host.restore((client.replica.phone ?: state.phone).toState())
            prepared = state.transferId
        }
        if (state?.phase == TransferPhase.OutboundPending) client.replica.acknowledge(state.transferId!!)
        if (state?.phase == TransferPhase.ReturnPending) {
            host.pause()
            client.replica.acknowledge(state.returnId!!)
        }
    }
    LaunchedEffect(host) {
        snapshotFlow { host.state to prepared }.collect { (phone, transfer) ->
            if (transfer != null && transfer == client.replica.snapshot?.transferId)
                client.replica.progress(PhoneCheckpoint.from(phone))
        }
    }

    if (state?.phase == TransferPhase.Phone && prepared == state.transferId) {
        PhonePuzzleScreen(host, "PAIRED", onLeave = onOffline, leaveText = "Offline mode", connectionMessage = connection.message)
    } else {
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF6FFFE2))) {
            Column(Modifier.fillMaxSize().background(Color(0xFF080F1B)).safeContentPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("P O R T A L", color = Color(0xFF6FFFE2), fontSize = 24.sp)
                Text("CONNECT TO YOUR MAC", color = Color.White, fontSize = 13.sp)
                Text(connection.message, color = Color(0xFF9AAEC4))
                if (!client.replica.resumable) {
                    OutlinedTextField(address, { address = it }, label = { Text("Mac Wi-Fi address:port") }, placeholder = { Text("192.168.1.20:8765") }, singleLine = true)
                    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit pairing code") }, singleLine = true)
                }
                if (!connection.connected) Button(
                    onClick = { if (client.replica.resumable) client.reconnect() else client.connect(address, code) },
                    enabled = client.replica.resumable || address.isNotBlank() && code.length == 6,
                ) { Text(if (client.replica.resumable) "Reconnect" else "Pair") }
                Text(when {
                    state?.gateUnlocked == true -> "Sphere returned to Mac · the gate is open."
                    state?.phase == TransferPhase.ReturnPending -> "Returning the sphere to your Mac…"
                    state?.phase == TransferPhase.OutboundPending -> "Preparing the pocket room…"
                    connection.connected -> "Explore the maze on your Mac and enter the violet portal."
                    else -> "Keep both apps foreground on the same Wi-Fi. Allow local network access when asked."
                }, color = Color.White)
                TextButton(onClick = onOffline) { Text("Offline test room") }
            }
        }
    }
}
