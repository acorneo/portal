package de.acorn42.portal.portal

import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

data class HostInfo(val started: Boolean = false, val port: Int = PORTAL_PORT, val pairCode: String = "", val message: String = "Start a session to pair your iPhone")

/** Owns all host protocol state under one mutex. Ktor never mutates the game world. */
class DesktopPortalHost {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val random = SecureRandom()
    private fun now() = System.nanoTime() / 1_000_000
    private fun code() = random.nextInt(1_000_000).toString().padStart(6, '0')
    private val session = PortalSession(UUID.randomUUID().toString(), "", now(), UUID.randomUUID().toString()) { UUID.randomUUID().toString() }
    private val mutableState = MutableStateFlow(session.snapshot)
    val state = mutableState.asStateFlow()
    private val mutableInfo = MutableStateFlow(HostInfo())
    val info = mutableInfo.asStateFlow()
    private var server: EmbeddedServer<*, *>? = null
    private var socket: DefaultWebSocketServerSession? = null

    suspend fun start(port: Int = PORTAL_PORT, bindHost: String = "0.0.0.0"): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (server != null) return@withLock mutableInfo.value.port
            try {
                val engine = scope.embeddedServer(CIO, host = bindHost, port = port) {
                    install(WebSockets) { pingPeriod = 2.seconds; timeout = 5.seconds; maxFrameSize = MAX_PACKET_BYTES.toLong() }
                    routing { webSocket("/portal/v1") { serve() } }
                }
                server = engine
                withTimeout(7_000) { engine.startSuspend(wait = false) }
                val actualPort = withTimeout(7_000) { engine.engine.resolvedConnectors().first().port }
                val code = code()
                session.refreshCode(code, now())
                mutableInfo.value = HostInfo(true, actualPort, code, "Pairing code valid for 5 minutes · one iPhone")
                actualPort
            } catch (error: Exception) {
                server?.stop(0, 1_000)
                server = null
                mutableInfo.value = HostInfo(message = "Cannot host on port $port: ${error.message?.take(100)}")
                throw error
            }
        }
    }

    suspend fun refreshCode() = mutex.withLock {
        val code = code()
        if (session.refreshCode(code, now())) mutableInfo.value = mutableInfo.value.copy(pairCode = code, message = "New code · valid for 5 minutes")
    }

    fun hostForeground(value: Boolean) { scope.launch { mutex.withLock { session.hostForeground(value); publish() } } }
    fun enterPortal() { scope.launch { mutex.withLock { session.enterPortal(); publish() } } }

    private suspend fun DefaultWebSocketServerSession.serve() {
        var generation = -1L
        try {
            val frame = withTimeout(7_000) { incoming.receive() } as? Frame.Text ?: return
            val hello = PortalCodec.client(frame.readText())
            val welcome = mutex.withLock {
                session.hello(hello, now()).also {
                    if (it.kind == ServerKind.Welcome) {
                        generation = session.snapshot.generation
                        socket?.cancel("Replaced by authenticated resume")
                        socket = this@serve
                        publish()
                    }
                }
            }
            send(PortalCodec.encode(welcome))
            if (welcome.kind != ServerKind.Welcome) return
            val changes = launch { state.collect { send(PortalCodec.encode(ServerPacket(ServerKind.Snapshot, snapshot = it))) } }
            val heartbeat = launch { while (isActive) { delay(1_000); send(PortalCodec.encode(ServerPacket(ServerKind.Snapshot, snapshot = state.value))) } }
            try {
                for (item in incoming) {
                    val text = (item as? Frame.Text)?.readText() ?: continue
                    val response = mutex.withLock { session.receive(PortalCodec.client(text)).also { publish() } }
                    send(PortalCodec.encode(response))
                    if (response.error == "Stale or unauthorized session") break
                }
            } finally { changes.cancel(); heartbeat.cancel() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Do not log frame contents, pairing codes, or resume credentials.
        } finally {
            mutex.withLock {
                session.disconnect(generation)
                if (generation == session.snapshot.generation) socket = null
                publish()
            }
        }
    }

    private fun publish() { mutableState.value = session.snapshot }
    fun close() { server?.stop(0, 1_000); scope.cancel() }

    fun addresses(): String = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }.joinToString(" / ") { it.hostAddress }
}
