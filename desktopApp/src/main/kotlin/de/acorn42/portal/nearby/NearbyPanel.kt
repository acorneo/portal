package de.acorn42.portal.nearby

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun NearbyPanel(active: Boolean) {
    val bridge = remember { NearbyBridge(NearbyBridge.packagedHelper()) }
    val status by bridge.state.collectAsState()
    DisposableEffect(bridge) { onDispose { bridge.close() } }
    LaunchedEffect(active) { if (!active) bridge.command("stop") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("NEARBY CONNECTION POC", color = Color(0xFF6FFFE2))
        Text("Wi-Fi enabled · both apps foreground · no shared router or hotspot required", color = Color.White)
        Text("This Mac: ${status.localName} · ${status.localID.take(8)}", color = Color.White)
        Text(if (active) status.message else "Paused · focus this window, then Advertise again", color = Color(0xFF9AAEC4))
        Text("Phase: ${status.phase}", color = Color(0xFF9AAEC4))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { bridge.command("advertise") }, enabled = active && status.phase in setOf("stopped", "disconnected", "closed")) { Text("Advertise nearby Mac") }
            OutlinedButton(onClick = { bridge.command("stop") }, enabled = status.phase !in setOf("stopped", "failed")) { Text("Stop") }
        }
        if (status.peerID.isNotEmpty()) {
            Text("iPhone: ${status.peerName} · ${status.peerID.take(8)}", color = Color.White)
            Text("Compare on both screens: ${status.fingerprint}", color = Color(0xFFBD91FF))
        }
        if (status.phase == "awaitingApproval") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { bridge.command("accept", status.requestID) }, enabled = active) { Text("Accept iPhone") }
                OutlinedButton(onClick = { bridge.command("reject", status.requestID) }) { Text("Reject") }
            }
        }
        if (status.phase == "connected") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { bridge.command("ping") }, enabled = active && !status.pendingPing) { Text("Send ping") }
                OutlinedButton(onClick = { bridge.command("disconnect") }) { Text("Disconnect") }
            }
        }
        Text("Acknowledged pings: ${status.acknowledgements} · received: ${status.receivedPings}", color = Color.White)
        Text("Connection test only. Names are self-reported; the fingerprint correlates this request. This POC is plaintext and does not authenticate devices cryptographically. The existing game and LAN portal are separate modes.", color = Color(0xFF9AAEC4))
        Text("If access is denied, allow PORTAL in System Settings > Privacy & Security > Local Network, then Stop/Advertise. Keep firewall protections enabled; permit the app if prompted.", color = Color(0xFF9AAEC4))
    }
}
