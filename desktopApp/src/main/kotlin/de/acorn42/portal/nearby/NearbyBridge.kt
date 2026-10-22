package de.acorn42.portal.nearby

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import java.io.File
import java.io.Reader
import java.util.concurrent.TimeUnit

internal data class NearbyStatus(
    val phase: String = "starting",
    val message: String = "Preparing native nearby helper…",
    val localName: String = "",
    val localID: String = "",
    val peerName: String = "",
    val peerID: String = "",
    val fingerprint: String = "",
    val requestID: String = "",
    val acknowledgements: Int = 0,
    val receivedPings: Int = 0,
    val pendingPing: Boolean = false,
)

internal object NearbyBridgeCodec {
    private val phases = setOf("stopped", "starting", "advertising", "awaitingHello", "awaitingOffer", "awaitingApproval", "awaitingAcceptance", "connected", "closed", "disconnected")
    fun decode(line: String): NearbyStatus {
        require(line.toByteArray().size <= 32768)
        val json = Json.parseToJsonElement(line).jsonObject
        fun text(key: String): String = json.getValue(key).jsonPrimitive.let {
            require(it.isString && it.content.length <= 512); it.content
        }
        fun count(key: String): Int = json.getValue(key).jsonPrimitive.int.also { require(it >= 0) }
        val phase = text("phase").also { require(it in phases) }
        return NearbyStatus(phase, text("message"), text("localName"), text("localID"), text("peerName"), text("peerID"), text("fingerprint"), text("requestID"), count("acknowledgements"), count("receivedPings"), json.getValue("pendingPing").jsonPrimitive.boolean)
    }
    fun command(kind: String, requestID: String = ""): String {
        require(kind in setOf("advertise", "accept", "reject", "ping", "disconnect", "stop", "quit"))
        require(requestID.length <= 80)
        return buildJsonObject { put("kind", kind); put("requestID", requestID) }.toString() + "\n"
    }
    fun line(reader: Reader): String? {
        val result = StringBuilder()
        while (true) {
            val next = reader.read()
            if (next < 0) return if (result.isEmpty()) null else error("Truncated helper message")
            if (next == 10) return result.toString()
            require(result.length < 32768) { "Oversized helper message" }
            result.append(next.toChar())
        }
    }
}

/** Pipes only: networking is entirely owned by the native helper. */
internal class NearbyBridge(private val helper: File) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(NearbyStatus())
    val state = mutableState.asStateFlow()
    private val lock = Any()
    private val commands = Channel<String>(32)
    private var process: Process? = null
    @Volatile private var closed = false
    private val shutdown = Thread { synchronized(lock) { process?.destroy() } }

    init {
        Runtime.getRuntime().addShutdownHook(shutdown)
        scope.launch {
            var child: Process? = null
            try {
                require(helper.isFile && helper.canExecute()) { "Native helper missing · run :desktopApp:prepareNearbyHelper" }
                val launched = ProcessBuilder(helper.absolutePath).start()
                child = launched
                synchronized(lock) {
                    if (closed) launched.destroy() else process = launched
                }
                if (closed) return@launch
                val writer = launch {
                    for (line in commands) {
                        launched.outputStream.write(line.toByteArray())
                        launched.outputStream.flush()
                    }
                }
                val stderr = launch { launched.errorStream.bufferedReader().use { reader -> while (NearbyBridgeCodec.line(reader) != null) { /* Drain; do not mix native diagnostics with status JSON. */ } } }
                launched.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = NearbyBridgeCodec.line(reader) ?: break
                        val status = NearbyBridgeCodec.decode(line)
                        if (!closed) mutableState.value = status
                    }
                }
                writer.cancel()
                stderr.cancel()
                if (!closed) mutableState.value = NearbyStatus("failed", "Native helper exited · leave/reopen Nearby POC to retry")
            } catch (error: Exception) {
                if (!closed) mutableState.value = NearbyStatus("failed", "Nearby helper failed: ${error.message?.take(160)}")
            } finally {
                synchronized(lock) { process = null }
                child?.destroy()
            }
        }
    }
    fun command(kind: String, requestID: String = "") {
        val line = NearbyBridgeCodec.command(kind, requestID)
        if (!closed && !commands.trySend(line).isSuccess) {
            mutableState.value = NearbyStatus("failed", "Too many helper commands · leave/reopen Nearby POC")
            close()
        }
    }

    override fun close() {
        closed = true
        commands.close()
        val child = synchronized(lock) { process.also { process = null; runCatching { it?.outputStream?.close() } } }
        child?.onExit()?.orTimeout(1, TimeUnit.SECONDS)?.exceptionally { child.destroyForcibly(); null }
        scope.cancel()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdown) }
    }
    companion object {
        fun packagedHelper(): File {
            val resources = System.getProperty("compose.application.resources.dir")
            return File(resources ?: ".", "nearby/PortalNearbyHelper")
        }
    }
}
