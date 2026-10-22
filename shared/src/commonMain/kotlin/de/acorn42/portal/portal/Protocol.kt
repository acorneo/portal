package de.acorn42.portal.portal

import de.acorn42.portal.phone.PhoneRoom
import de.acorn42.portal.phone.PhoneState
import de.acorn42.portal.world.Sphere
import de.acorn42.portal.world.Vec2
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val PORTAL_PROTOCOL_VERSION = 1
const val PORTAL_PORT = 8765
const val PORTAL_ROOM = "pocket-room-1"
const val MAX_PACKET_BYTES = 16_384

@Serializable enum class ClientKind { Hello, Acknowledge, Progress, Foreground }
@Serializable enum class ServerKind { Welcome, Snapshot, Error }
@Serializable enum class TransferPhase { Desktop, OutboundPending, Phone, ReturnPending }
@Serializable enum class ActiveDevice { Desktop, Phone, Neither }

@Serializable
data class PhoneCheckpoint(val x: Double, val y: Double, val vx: Double = 0.0, val vy: Double = 0.0, val completed: Boolean = false) {
    fun valid(): Boolean {
        if (!listOf(x, y, vx, vy).all { it.isFinite() }) return false
        if (Vec2(vx, vy).length > 4.000001) return false
        val sphere = Sphere(Vec2(x, y))
        return PhoneRoom.maze.canOccupy(sphere) &&
            (!completed || (sphere.position - PhoneRoom.exit).length <= PhoneRoom.exitRadius)
    }

    fun toState() = PhoneState(Sphere(Vec2(x, y)), Vec2(vx, vy), completed)

    companion object {
        fun from(state: PhoneState) = PhoneCheckpoint(
            state.sphere.position.x, state.sphere.position.y, state.velocity.x, state.velocity.y, state.completed,
        )
        fun initial() = from(PhoneState(Sphere(PhoneRoom.maze.start)))
    }
}

@Serializable
data class SessionSnapshot(
    val revision: Long = 0,
    val sessionId: String,
    val clientId: String? = null,
    val generation: Long = 0,
    val connected: Boolean = false,
    val hostForeground: Boolean = true,
    val phoneForeground: Boolean = false,
    val phase: TransferPhase = TransferPhase.Desktop,
    val owner: ActiveDevice = ActiveDevice.Desktop,
    val transferId: String? = null,
    val returnId: String? = null,
    val roomId: String = PORTAL_ROOM,
    val phone: PhoneCheckpoint = PhoneCheckpoint.initial(),
    val acknowledgedPhoneSequence: Long = 0,
    val gateUnlocked: Boolean = false,
) {
    val ready: Boolean get() = connected && hostForeground && phoneForeground
    val desktopCanMove: Boolean get() = phase == TransferPhase.Desktop && hostForeground && (clientId == null || ready)
    val phoneCanMove: Boolean get() = phase == TransferPhase.Phone && ready
}

@Serializable
data class ClientPacket(
    val kind: ClientKind,
    val version: Int = PORTAL_PROTOCOL_VERSION,
    val sessionId: String = "",
    val clientId: String,
    val generation: Long = 0,
    val pairCode: String = "",
    val resumeToken: String = "",
    val transferId: String = "",
    val roomId: String = PORTAL_ROOM,
    val sequence: Long = 0,
    val phone: PhoneCheckpoint? = null,
    val foreground: Boolean = true,
)

@Serializable
data class ServerPacket(
    val kind: ServerKind,
    val version: Int = PORTAL_PROTOCOL_VERSION,
    val snapshot: SessionSnapshot? = null,
    val resumeToken: String = "",
    val error: String = "",
)

object PortalCodec {
    private val json = Json { encodeDefaults = true; explicitNulls = false }
    fun encode(packet: ClientPacket): String = json.encodeToString(packet)
    fun encode(packet: ServerPacket): String = json.encodeToString(packet)
    fun client(text: String): ClientPacket { checkSize(text); return json.decodeFromString(text) }
    fun server(text: String): ServerPacket { checkSize(text); return json.decodeFromString(text) }
    private fun checkSize(text: String) { require(text.encodeToByteArray().size <= MAX_PACKET_BYTES) { "Packet too large" } }
}
