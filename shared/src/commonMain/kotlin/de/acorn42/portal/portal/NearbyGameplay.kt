package de.acorn42.portal.portal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Native consent replaces manual admission, not gameplay identity/generation checks.
 * Platform callbacks must serialize this authority on the UI thread.
 */
class NearbyPortalAuthority(
    sessionId: String,
    private val admission: String,
    private val resume: String,
    nextId: () -> String,
) {
    private val session = PortalSession(sessionId, admission, 0, resume, nextId)
    val snapshot: SessionSnapshot get() = session.snapshot
    private var connection = ""
    private var peer = ""
    private var hello: ClientPacket? = null
    private var welcome: ServerPacket? = null

    fun attach(connectionId: String, acceptedPeer: String): Boolean {
        if (connectionId.isBlank() || acceptedPeer.isBlank()) return false
        if (connection.isNotEmpty()) return connection == connectionId && peer == acceptedPeer
        if (snapshot.clientId != null && snapshot.clientId != acceptedPeer) return false
        connection = connectionId; peer = acceptedPeer; hello = null; welcome = null
        return true
    }
    fun detach(connectionId: String) {
        if (connectionId != connection) return
        session.disconnect(snapshot.generation)
        connection = ""; peer = ""; hello = null; welcome = null
    }
    fun receive(connectionId: String, acceptedPeer: String, payload: String, now: Long): ServerPacket {
        fun reject(message: String) = ServerPacket(ServerKind.Error, error = message)
        if (connectionId != connection || acceptedPeer != peer || connection.isEmpty()) return reject("Stale native connection")
        val packet = runCatching { PortalCodec.client(payload) }.getOrElse { return reject("Invalid gameplay packet") }
        if (packet.clientId != peer) return reject("Gameplay identity differs from accepted phone")
        if (packet.kind != ClientKind.Hello) return session.receive(packet)
        if (hello != null) return if (packet == hello) welcome!!.copy(snapshot = snapshot) else reject("Unexpected hello")
        var admitted = packet
        if (snapshot.clientId == null) {
            session.refreshCode(admission, now)
            admitted = packet.copy(pairCode = admission)
        } else if (packet.resumeToken.isEmpty() && snapshot.transferId == null &&
            snapshot.phase == TransferPhase.Desktop && packet.sessionId.isEmpty()
        ) {
            // Recover a lost first Welcome before any gameplay transfer. Fresh
            // native consent for the same peer is still required; never bypass
            // resume validation once progress has begun.
            admitted = packet.copy(sessionId = snapshot.sessionId, resumeToken = resume)
        }
        return session.hello(admitted, now).also {
            if (it.kind == ServerKind.Welcome) { hello = packet; welcome = it }
        }
    }
    fun enterPortal() = session.enterPortal()
    fun newChapter() = session.newChapter()
    fun desktopCheckpoint(checkpoint: DesktopCheckpoint) = session.desktopCheckpoint(checkpoint)
    fun hostAvailable(value: Boolean) = session.hostForeground(value)
    fun state() = session.state()
}

data class NearbyPhoneState(
    val connected: Boolean = false,
    val foreground: Boolean = true,
    val snapshot: SessionSnapshot? = null,
    val message: String = "Choose your nearby Mac",
)

/** In-memory phone session retained independently of native connection/room views. */
class NearbyPhoneGame(clientId: String) {
    val replica = PhoneReplica(clientId)
    private val mutableState = MutableStateFlow(NearbyPhoneState())
    val state = mutableState.asStateFlow()
    private var foreground = true
    private var synchronized = false
    val isRoomActive: Boolean get() = synchronized && foreground && replica.snapshot?.phase == TransferPhase.Phone
    val message: String get() = mutableState.value.message

    fun transportConnected(): String {
        synchronized = false
        publish("Restoring your session…")
        return PortalCodec.encode(replica.hello("", foreground))
    }
    fun transportDisconnected() { synchronized = false; publish("Connection paused · choose your Mac again to resume") }
    fun foreground(value: Boolean) { foreground = value; publish() }
    fun receive(payload: String): Boolean {
        val packet = runCatching { PortalCodec.server(payload) }.getOrElse { publish("Invalid game update · reconnect to your Mac"); return false }
        if (packet.kind == ServerKind.Error) { publish(packet.error); return false }
        if (!replica.accept(packet)) return false
        if (packet.kind == ServerKind.Welcome) synchronized = true
        val snapshot = replica.snapshot!!
        when (snapshot.phase) {
            TransferPhase.OutboundPending -> replica.acknowledge(snapshot.transferId!!)
            TransferPhase.ReturnPending -> replica.acknowledge(snapshot.returnId!!)
            else -> Unit
        }
        publish()
        return true
    }
    fun outgoing(): List<String> = if (synchronized) replica.outgoing(foreground).map(PortalCodec::encode) else emptyList()
    fun progress(checkpoint: PhoneCheckpoint) = replica.progress(checkpoint)
    private fun publish(override: String? = null) {
        val snapshot = replica.snapshot
        val message = override ?: when {
            !synchronized -> "Connection paused · choose your Mac again to resume"
            !foreground || snapshot?.ready != true -> "Paused · return both apps to continue"
            snapshot.gateUnlocked -> "Sphere returned to your Mac · the gate is open"
            snapshot.phase == TransferPhase.Desktop -> "Connected · enter the violet portal on your Mac"
            snapshot.phase == TransferPhase.OutboundPending -> "Preparing your pocket room…"
            snapshot.phase == TransferPhase.ReturnPending -> "Returning the sphere to your Mac…"
            else -> "Roll to the violet exit to return to your Mac"
        }
        mutableState.value = NearbyPhoneState(synchronized, foreground, snapshot, message)
    }
}
