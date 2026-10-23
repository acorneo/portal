package de.acorn42.portal.nearby

import de.acorn42.portal.portal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/** All gameplay mutation is confined to the caller's Compose main scope. */
internal class NearbyGameHost(scope: CoroutineScope) : AutoCloseable {
    val bridge = NearbyBridge(NearbyBridge.packagedHelper())
    private val authority = NearbyPortalAuthority(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()) { UUID.randomUUID().toString() }
    private val mutableState = MutableStateFlow(authority.snapshot)
    val state = mutableState.asStateFlow()
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    private var connection = ""
    private var peer = ""
    private val reader = scope.launch {
        bridge.events.collect { event ->
            when (event) {
                is NearbyBridgeEvent.Status -> {
                    val status = event.value
                    if (status.phase == "connected") {
                        if (connection != status.requestID) {
                            if (authority.attach(status.requestID, status.peerID)) {
                                connection = status.requestID; peer = status.peerID; mutableMessage.value = ""
                            } else {
                                mutableMessage.value = "Reconnect the same iPhone to preserve your session"
                                bridge.command("disconnect")
                            }
                        }
                    } else if (connection.isNotEmpty()) {
                        authority.detach(connection); connection = ""; peer = ""; publish()
                    }
                }
                is NearbyBridgeEvent.Game -> {
                    val response = authority.receive(event.connectionID, event.peerID, event.payload, System.nanoTime() / 1_000_000)
                    if (event.connectionID == connection && event.peerID == peer) bridge.sendGame(connection, PortalCodec.encode(response))
                    publish()
                }
            }
        }
    }
    private val ticker = scope.launch {
        while (isActive) { delay(250); if (authority.snapshot.connected) broadcast() }
    }
    init { bridge.command("advertise") }
    private fun publish() { mutableState.value = authority.snapshot }
    private fun broadcast() { if (connection.isNotEmpty()) bridge.sendGame(connection, PortalCodec.encode(authority.state())) }
    fun hostAvailable(value: Boolean) { authority.hostAvailable(value); publish(); broadcast() }
    fun enterPortal() { if (authority.enterPortal()) { publish(); broadcast() } }
    fun desktopCheckpoint(checkpoint: DesktopCheckpoint) {
        if (authority.desktopCheckpoint(checkpoint)) { publish(); broadcast() }
    }
    fun newChapter() { authority.newChapter(); publish(); broadcast() }
    fun snapshot() = authority.snapshot
    override fun close() { reader.cancel(); ticker.cancel(); bridge.close() }
}

/** Focus only affects keyboard input. A visible, unminimized Mac can host while
 * another window has keyboard focus and the phone owns gameplay. */
internal data class DesktopAvailability(val focused: Boolean, val visible: Boolean, val minimized: Boolean, val applicationHidden: Boolean = false) {
    val hostAvailable: Boolean get() = visible && !minimized && !applicationHidden
    val keyboardAvailable: Boolean get() = focused && hostAvailable
}
