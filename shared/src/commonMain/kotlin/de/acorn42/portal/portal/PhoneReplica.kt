package de.acorn42.portal.portal

/** In-memory recovery state, independent of sockets/UI. Never clears an outbox
 * just because sending succeeded; only an authoritative acknowledgement clears it.
 */
class PhoneReplica(val clientId: String) {
    var snapshot: SessionSnapshot? = null
        private set
    private var token = ""
    private var sequence = 0L
    private var pendingPhone: Pair<Long, PhoneCheckpoint>? = null
    private var pendingAck: String? = null
    val resumable: Boolean get() = token.isNotEmpty()
    val phone: PhoneCheckpoint? get() = pendingPhone?.second ?: snapshot?.phone

    fun hello(code: String, foreground: Boolean) = ClientPacket(
        ClientKind.Hello, clientId = clientId, sessionId = snapshot?.sessionId.orEmpty(),
        resumeToken = token, pairCode = if (resumable) "" else code, foreground = foreground,
    )

    fun accept(packet: ServerPacket): Boolean {
        if (packet.version != PORTAL_PROTOCOL_VERSION || packet.kind == ServerKind.Error) return false
        val next = packet.snapshot ?: return false
        if (next.clientId != clientId || next.roomId != PORTAL_ROOM) return false
        val old = snapshot
        if (old != null && (old.sessionId != next.sessionId || next.revision < old.revision)) return false
        if (packet.kind == ServerKind.Welcome) {
            if (packet.resumeToken.isEmpty()) return false
            token = packet.resumeToken
        } else if (old == null || old.generation != next.generation) return false
        if (old?.transferId != next.transferId) {
            pendingPhone = null
            pendingAck = null
            sequence = 0
        }
        sequence = maxOf(sequence, next.acknowledgedPhoneSequence)
        if ((pendingPhone?.first ?: Long.MAX_VALUE) <= next.acknowledgedPhoneSequence) pendingPhone = null
        snapshot = next
        return true
    }

    fun progress(phone: PhoneCheckpoint) {
        if (snapshot?.phase != TransferPhase.Phone || !phone.valid()) return
        if (pendingPhone?.second?.completed == true || pendingPhone?.second == phone) return
        pendingPhone = ++sequence to phone
    }

    fun acknowledge(id: String) {
        val state = snapshot ?: return
        if (id == state.transferId && state.phase == TransferPhase.OutboundPending ||
            id == state.returnId && state.phase == TransferPhase.ReturnPending
        ) pendingAck = id
    }

    fun outgoing(foreground: Boolean): List<ClientPacket> {
        val state = snapshot ?: return emptyList()
        fun packet(kind: ClientKind) = ClientPacket(
            kind, sessionId = state.sessionId, clientId = clientId,
            generation = state.generation, runId = state.runId, foreground = foreground,
        )
        return buildList {
            add(packet(ClientKind.Foreground))
            if (!state.ready || !foreground) return@buildList
            val id = pendingAck
            if (id != null && (state.phase == TransferPhase.OutboundPending && id == state.transferId ||
                    state.phase == TransferPhase.ReturnPending && id == state.returnId)
            ) add(packet(ClientKind.Acknowledge).copy(transferId = id))
            if (state.phase == TransferPhase.Phone) pendingPhone?.let { (sequence, phone) ->
                add(packet(ClientKind.Progress).copy(transferId = state.transferId.orEmpty(), sequence = sequence, phone = phone))
            }
        }
    }
}
