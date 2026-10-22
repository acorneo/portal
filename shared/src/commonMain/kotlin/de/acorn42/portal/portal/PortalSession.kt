package de.acorn42.portal.portal

/** Pure single-session authority. The platform host serializes access and supplies
 * monotonic time plus cryptographically generated session/token/transfer IDs.
 * No client message can choose desktop placement, ownership, or unlock state.
 */
class PortalSession(
    sessionId: String,
    private var pairCode: String,
    private var codeIssuedAt: Long,
    private val resumeToken: String,
    private val nextId: () -> String,
) {
    var snapshot = SessionSnapshot(sessionId = sessionId)
        private set
    private var failedAttempts = 0
    private var attemptWindow = codeIssuedAt

    fun refreshCode(code: String, now: Long): Boolean {
        if (snapshot.clientId != null) return false
        pairCode = code
        codeIssuedAt = now
        return true
    }

    fun hello(packet: ClientPacket, now: Long): ServerPacket {
        if (packet.kind != ClientKind.Hello || packet.version != PORTAL_PROTOCOL_VERSION) return error("Protocol version mismatch")
        if (packet.clientId.isBlank() || packet.clientId.length > 100) return error("Invalid client")
        if (packet.sessionId.isNotEmpty() && packet.sessionId != snapshot.sessionId) return error("Wrong session")
        if (snapshot.clientId == null) {
            if (now - attemptWindow >= 60_000) { failedAttempts = 0; attemptWindow = now }
            if (failedAttempts >= 5) return error("Pairing rate limit; retry later")
            if (now < codeIssuedAt || now - codeIssuedAt >= 300_000 || packet.pairCode != pairCode) {
                failedAttempts++
                return error("Incorrect or expired pairing code")
            }
        } else if (packet.sessionId != snapshot.sessionId || packet.clientId != snapshot.clientId ||
            packet.resumeToken != resumeToken
        ) return error("Session already paired; use its resume connection")
        change(snapshot.copy(
            clientId = packet.clientId, generation = snapshot.generation + 1,
            connected = true, phoneForeground = packet.foreground,
        ))
        return ServerPacket(ServerKind.Welcome, snapshot = snapshot, resumeToken = resumeToken)
    }

    fun receive(packet: ClientPacket): ServerPacket {
        if (packet.version != PORTAL_PROTOCOL_VERSION || packet.sessionId != snapshot.sessionId ||
            packet.clientId != snapshot.clientId || packet.generation != snapshot.generation || !snapshot.connected
        ) return error("Stale or unauthorized session")
        when (packet.kind) {
            ClientKind.Hello -> return error("Unexpected hello")
            ClientKind.Foreground -> {
                if (snapshot.phoneForeground != packet.foreground) change(snapshot.copy(phoneForeground = packet.foreground))
            }
            ClientKind.Acknowledge -> when {
                packet.transferId == snapshot.transferId && snapshot.phase == TransferPhase.OutboundPending -> {
                    if (!snapshot.ready) return error("Both devices must be foreground")
                    change(snapshot.copy(phase = TransferPhase.Phone, owner = ActiveDevice.Phone))
                }
                packet.transferId == snapshot.returnId && snapshot.phase == TransferPhase.ReturnPending -> {
                    if (!snapshot.ready) return error("Both devices must be foreground")
                    change(snapshot.copy(phase = TransferPhase.Desktop, owner = ActiveDevice.Desktop, gateUnlocked = true))
                }
                packet.transferId != snapshot.transferId && packet.transferId != snapshot.returnId -> return error("Stale transfer")
                // Duplicate acknowledgements for a known transfer return canonical state.
            }
            ClientKind.Progress -> {
                if (packet.transferId != snapshot.transferId || packet.roomId != snapshot.roomId) return error("Stale transfer or wrong room")
                if (packet.sequence <= snapshot.acknowledgedPhoneSequence) return state()
                if (!snapshot.phoneCanMove) return error("Phone does not own active gameplay")
                val phone = packet.phone ?: return error("Missing checkpoint")
                if (!phone.valid()) return error("Invalid phone checkpoint")
                val next = snapshot.copy(phone = phone, acknowledgedPhoneSequence = packet.sequence)
                if (phone.completed) change(next.copy(
                    phase = TransferPhase.ReturnPending, owner = ActiveDevice.Neither, returnId = nextId(),
                )) else change(next)
            }
        }
        return state()
    }

    fun enterPortal(): Boolean {
        if (!snapshot.ready || snapshot.phase != TransferPhase.Desktop || snapshot.gateUnlocked) return false
        change(snapshot.copy(
            phase = TransferPhase.OutboundPending, owner = ActiveDevice.Neither,
            transferId = nextId(), phone = PhoneCheckpoint.initial(), acknowledgedPhoneSequence = 0,
        ))
        return true
    }

    fun hostForeground(foreground: Boolean) {
        if (snapshot.hostForeground != foreground) change(snapshot.copy(hostForeground = foreground))
    }

    fun disconnect(generation: Long) {
        if (generation == snapshot.generation && snapshot.connected) change(snapshot.copy(connected = false, phoneForeground = false))
    }

    fun state() = ServerPacket(ServerKind.Snapshot, snapshot = snapshot)
    private fun error(message: String) = ServerPacket(ServerKind.Error, error = message)
    private fun change(next: SessionSnapshot) { snapshot = next.copy(revision = snapshot.revision + 1) }
}
