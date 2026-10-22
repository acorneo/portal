package de.acorn42.portal.portal

import de.acorn42.portal.phone.PhonePuzzle
import de.acorn42.portal.phone.PhoneRoom
import de.acorn42.portal.world.Vec2
import kotlin.test.*

class NearbyGameplayTest {
    private var ids = 0
    private val host = NearbyPortalAuthority("session", "private-admission", "private-resume") { "transfer-${++ids}" }
    private val phone = NearbyPhoneGame("phone")
    private var connection = "native-1"
    private fun receive(payload: String) = host.receive(connection, "phone", payload, 400_000)
    private fun connect() {
        assertTrue(host.attach(connection, "phone"))
        assertTrue(phone.receive(PortalCodec.encode(receive(phone.transportConnected()))))
    }
    private fun pump() {
        phone.outgoing().forEach { phone.receive(PortalCodec.encode(receive(it))) }
        phone.receive(PortalCodec.encode(host.state()))
    }
    private fun enter() { assertTrue(host.enterPortal()); phone.receive(PortalCodec.encode(host.state())); pump() }

    @Test fun nativeConsentCarriesExistingPuzzleAndReturnsOwnershipExactlyOnce() {
        connect(); enter()
        assertTrue(phone.isRoomActive)
        assertEquals(ActiveDevice.Phone, host.snapshot.owner)
        val puzzle = PhonePuzzle()
        for (target in listOf(Vec2(7.4, 2.5), Vec2(7.4, 6.4), Vec2(1.5, 6.4), Vec2(1.5, 10.8), PhoneRoom.exit)) {
            var ticks = 0
            while ((puzzle.state.sphere.position - target).length > 0.18 && !puzzle.state.completed && ticks++ < 4_000)
                puzzle.step((target - puzzle.state.sphere.position) * 0.6 - puzzle.state.velocity * 0.45, 1.0 / 120)
            assertTrue(ticks < 4_000)
        }
        assertTrue(puzzle.state.completed)
        phone.progress(PhoneCheckpoint.from(puzzle.state))
        val progress = phone.outgoing().first { PortalCodec.client(it).kind == ClientKind.Progress }
        phone.receive(PortalCodec.encode(receive(progress)))
        assertEquals(TransferPhase.ReturnPending, host.snapshot.phase)
        assertFalse(host.snapshot.gateUnlocked)
        pump()
        assertTrue(host.snapshot.gateUnlocked)
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)
        assertFalse(phone.isRoomActive)
        val revision = host.snapshot.revision
        assertEquals(ServerKind.Snapshot, receive(progress).kind)
        assertEquals(revision, host.snapshot.revision)
        assertFalse(host.enterPortal())
        assertEquals(2, ids)
    }

    @Test fun reconnectKeepsOutboxAndTransferAndRejectsOldSocketGenerationOrWrongPeer() {
        connect(); enter()
        val transfer = host.snapshot.transferId
        val checkpoint = PhoneCheckpoint(2.5, 2.0)
        phone.progress(checkpoint)
        val stale = phone.outgoing().first { PortalCodec.client(it).kind == ClientKind.Progress }
        host.detach(connection); phone.transportDisconnected()
        assertFalse(host.snapshot.phoneCanMove)
        assertTrue(phone.outgoing().isEmpty())
        assertFalse(host.attach("impostor", "another-phone"))
        connection = "native-2"
        connect()
        assertEquals(ServerKind.Error, host.receive("native-1", "phone", stale, 400_001).kind)
        assertEquals(ServerKind.Error, receive(stale).kind)
        host.detach("native-1") // Delayed old teardown cannot pause new connection.
        assertTrue(host.snapshot.connected)
        pump()
        assertEquals(checkpoint, host.snapshot.phone)
        assertEquals(transfer, host.snapshot.transferId)
        assertEquals(1, host.snapshot.acknowledgedPhoneSequence)
        phone.foreground(false); pump()
        assertFalse(host.snapshot.phoneCanMove)
        phone.foreground(true); pump()
        assertTrue(host.snapshot.phoneCanMove)
        host.hostAvailable(false); phone.receive(PortalCodec.encode(host.state()))
        assertFalse(host.snapshot.phoneCanMove)
        host.hostAvailable(true); pump()
        assertTrue(host.snapshot.phoneCanMove)
    }

    @Test fun lostWelcomeAndAcknowledgementsRecoverWithoutDuplicateTransfers() {
        assertTrue(host.attach(connection, "phone"))
        val hello = phone.transportConnected()
        val welcome = receive(hello) // Lose first Welcome.
        assertEquals(welcome, receive(hello))
        assertTrue(phone.outgoing().isEmpty(), "No stale outbox before resume Welcome")
        host.detach(connection); connection = "native-2"
        connect() // Same consented identity can bootstrap before progress began.
        assertTrue(host.enterPortal())
        phone.receive(PortalCodec.encode(host.state()))
        val ack = phone.outgoing().first { PortalCodec.client(it).kind == ClientKind.Acknowledge }
        receive(ack) // Lose acknowledgement response, then disconnect.
        host.detach(connection); phone.transportDisconnected(); connection = "native-3"
        connect(); pump()
        assertTrue(phone.isRoomActive)
        assertEquals(1, ids)
        assertEquals(ActiveDevice.Phone, host.snapshot.owner)
        assertEquals(ServerKind.Error, host.receive(connection, "other", ack, 400_001).kind)
    }
}
