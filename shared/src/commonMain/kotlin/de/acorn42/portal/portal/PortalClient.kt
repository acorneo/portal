package de.acorn42.portal.portal

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

internal expect fun portalHttpClient(): HttpClient

data class PhoneConnection(val connected: Boolean = false, val snapshot: SessionSnapshot? = null, val message: String = "Connect to your Mac")

/** Confined to the caller's main scope, like the existing native PhoneHost. */
class PortalClient(private val scope: CoroutineScope) {
    val replica = PhoneReplica(Random.nextLong().toString(16) + Random.nextLong().toString(16))
    private val http = portalHttpClient()
    private val mutableState = MutableStateFlow(PhoneConnection())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var attempt = 0
    private var address = ""
    private var code = ""
    private var foreground = true

    fun connect(address: String, code: String) {
        this.address = address.trim()
        this.code = code.trim()
        job?.cancel()
        val current = ++attempt
        job = scope.launch {
            do {
                try {
                    mutableState.value = mutableState.value.copy(connected = false, message = "Connecting…")
                    val url = Url("ws://${this@PortalClient.address}/portal/v1")
                    require(url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty())
                    val socket = withTimeout(7_000) { http.webSocketSession(urlString = url.toString()) }
                    try {
                        coroutineScope {
                            val connectionScope = this
                            with(socket) {
                                send(PortalCodec.encode(replica.hello(this@PortalClient.code, foreground)))
                                val first = withTimeout(7_000) { incoming.receive() } as? Frame.Text ?: error("Invalid server response")
                                val welcome = PortalCodec.server(first.readText())
                                require(welcome.kind == ServerKind.Welcome && replica.accept(welcome)) { welcome.error.ifBlank { "Pairing failed" } }
                                publish(true, "Paired")
                                // Keep replica/outbox access on the caller's main scope.
                                val sender = connectionScope.launch {
                                    while (isActive) {
                                        replica.outgoing(foreground).forEach { send(PortalCodec.encode(it)) }
                                        delay(100)
                                    }
                                }
                                try {
                                    while (isActive) {
                                        val frame = withTimeout(7_000) { incoming.receive() } as? Frame.Text ?: continue
                                        val packet = PortalCodec.server(frame.readText())
                                        if (packet.kind == ServerKind.Error) publish(true, packet.error)
                                        else if (replica.accept(packet)) publish(true, if (replica.snapshot?.ready == true) "Paired" else "Paused · keep both apps foreground")
                                    }
                                } finally { sender.cancel() }
                            }
                        }
                    } finally { socket.cancel() }
                } catch (_: TimeoutCancellationException) {
                    if (current == attempt) publish(false, "Connection timed out · progress retained")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (current == attempt) publish(false, if (replica.resumable) "Disconnected · reconnecting with progress retained" else "Cannot pair: ${error.message?.take(100).orEmpty()}")
                }
                if (current != attempt || !replica.resumable || !foreground) break
                delay(1_500)
            } while (isActive)
        }
    }

    fun reconnect() = connect(address, code)

    fun foreground(value: Boolean) {
        foreground = value
        if (value && replica.resumable && job?.isActive != true) connect(address, code)
    }

    fun disconnect() {
        attempt++
        job?.cancel()
        publish(false, "Disconnected · progress retained; tap Reconnect")
    }

    fun close() { disconnect(); http.close() }
    private fun publish(connected: Boolean, message: String) {
        mutableState.value = PhoneConnection(connected, replica.snapshot, message)
    }
}
