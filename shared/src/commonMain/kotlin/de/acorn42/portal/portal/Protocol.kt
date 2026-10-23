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
@Serializable enum class ChapterObjective {
    ExploreFirstRoom, RollFirstPocketRoom, ReachGreenExit, RollSecondPocketRoom, Complete,
}

@Serializable
data class DesktopCheckpoint(val room: Int = 1, val x: Double, val y: Double) {
    fun valid(): Boolean = room in 1..2 &&
        PortalLevel.room(room).maze.canOccupy(Sphere(Vec2(x, y)))
    fun position() = Vec2(x, y)
    companion object {
        fun initial(room: Int = 1) = DesktopCheckpoint(room, PortalLevel.room(room).start.x, PortalLevel.room(room).start.y)
        fun from(room: Int, position: Vec2) = DesktopCheckpoint(room, position.x, position.y)
    }
}

@Serializable
data class PhoneCheckpoint(
    val x: Double,
    val y: Double,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val completed: Boolean = false,
    val roomVariant: Int = 1,
) {
    fun valid(): Boolean {
        if (!listOf(x, y, vx, vy).all { it.isFinite() }) return false
        if (Vec2(vx, vy).length > 4.000001) return false
        val sphere = Sphere(Vec2(x, y))
        if (roomVariant !in 1..2) return false
        val maze = PhoneRoom.mazeFor(roomVariant)
        val exit = PhoneRoom.exitFor(roomVariant)
        return maze.canOccupy(sphere) &&
            (!completed || (sphere.position - exit).length <= PhoneRoom.exitRadius)
    }

    fun toState() = PhoneState(Sphere(Vec2(x, y)), Vec2(vx, vy), completed)

    companion object {
        fun from(state: PhoneState, roomVariant: Int = 1) = PhoneCheckpoint(
            state.sphere.position.x, state.sphere.position.y, state.velocity.x, state.velocity.y, state.completed, roomVariant,
        )
        fun initial(roomVariant: Int = 1) = from(
            PhoneState(Sphere(PhoneRoom.mazeFor(roomVariant).start)), roomVariant,
        )
    }
}

@Serializable
data class SessionSnapshot(
    val revision: Long = 0,
    val sessionId: String,
    val clientId: String? = null,
    val runId: String = "$sessionId-run-1",
    val generation: Long = 0,
    val connected: Boolean = false,
    val hostForeground: Boolean = true,
    val phoneForeground: Boolean = false,
    val phase: TransferPhase = TransferPhase.Desktop,
    val owner: ActiveDevice = ActiveDevice.Desktop,
    val transferId: String? = null,
    val returnId: String? = null,
    val roomId: String = PORTAL_ROOM,
    val chapter: Int = 1,
    val desktopRoom: Int = 1,
    val phoneRoom: Int = 1,
    val objective: ChapterObjective = ChapterObjective.ExploreFirstRoom,
    val completed: Boolean = false,
    val desktop: DesktopCheckpoint = DesktopCheckpoint.initial(),
    val phone: PhoneCheckpoint = PhoneCheckpoint.initial(1),
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
    val runId: String = "",
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
