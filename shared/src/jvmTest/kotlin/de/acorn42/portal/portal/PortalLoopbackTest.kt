package de.acorn42.portal.portal

import de.acorn42.portal.phone.PhonePuzzle
import de.acorn42.portal.phone.PhoneRoom
import de.acorn42.portal.world.Vec2
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.*

class PortalLoopbackTest {
    private suspend fun awaitClient(client: PortalClient, predicate: (PhoneConnection) -> Boolean): PhoneConnection =
        try { withTimeout(4_000) { client.state.first(predicate) } }
        catch (timeout: TimeoutCancellationException) {
            error("Client wait failed: ${client.state.value.message}; snapshot=${client.state.value.snapshot?.let { "${it.phase}/${it.connected}/${it.ready}/${it.revision}" }}")
        }

    @Test fun realWebSocketPairsTransfersExistingPuzzleAndReturnsOnce() = runBlocking {
        withTimeout(20_000) {
            val host = DesktopPortalHost()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val client = PortalClient(scope)
            try {
                val port = host.start(0, "127.0.0.1")
                client.connect("127.0.0.1:$port", host.info.value.pairCode)
                awaitClient(client) { it.connected }
                host.enterPortal()
                val outbound = awaitClient(client) { it.snapshot?.phase == TransferPhase.OutboundPending }.snapshot!!
                client.replica.acknowledge(outbound.transferId!!)
                awaitClient(client) { it.snapshot?.phase == TransferPhase.Phone }
                val puzzle = PhonePuzzle()
                for (target in listOf(Vec2(7.4, 2.5), Vec2(7.4, 6.4), Vec2(1.5, 6.4), Vec2(1.5, 10.8), PhoneRoom.exit)) {
                    var ticks = 0
                    while ((puzzle.state.sphere.position - target).length > 0.18 && !puzzle.state.completed && ticks++ < 4_000) {
                        puzzle.step((target - puzzle.state.sphere.position) * 0.6 - puzzle.state.velocity * 0.45, 1.0 / 120)
                    }
                    assertTrue(ticks < 4_000)
                }
                assertTrue(puzzle.state.completed)
                client.replica.progress(PhoneCheckpoint.from(puzzle.state))
                val returning = awaitClient(client) { it.snapshot?.phase == TransferPhase.ReturnPending }.snapshot!!
                assertFalse(returning.gateUnlocked)
                client.replica.acknowledge(returning.returnId!!)
                val done = awaitClient(client) { it.snapshot?.gateUnlocked == true }.snapshot!!
                assertEquals(TransferPhase.Desktop, done.phase)
                assertEquals(ActiveDevice.Desktop, done.owner)
                assertEquals(returning.returnId, done.returnId)
                assertTrue(done.phone.completed)
            } finally { client.close(); scope.cancel(); host.close() }
        }
    }

    @Test fun realDisconnectResumesUnacknowledgedPhoneProgress() = runBlocking {
        withTimeout(20_000) {
            val host = DesktopPortalHost()
            val scope = CoroutineScope(coroutineContext + SupervisorJob())
            val client = PortalClient(scope)
            try {
                val port = host.start(0, "127.0.0.1")
                val address = "127.0.0.1:$port"
                client.connect(address, host.info.value.pairCode)
                awaitClient(client) { it.connected }
                host.enterPortal()
                val outbound = awaitClient(client) { it.snapshot?.phase == TransferPhase.OutboundPending }.snapshot!!
                client.replica.acknowledge(outbound.transferId!!)
                awaitClient(client) { it.snapshot?.phase == TransferPhase.Phone }
                client.disconnect()
                host.state.first { !it.connected }
                val checkpoint = PhoneCheckpoint(2.5, 2.0)
                client.replica.progress(checkpoint)
                client.connect(address, "not-the-code") // Resume uses the retained credential, never the consumed code.
                val resumed = awaitClient(client) { it.connected && it.snapshot?.acknowledgedPhoneSequence == 1L }.snapshot!!
                assertEquals(checkpoint, resumed.phone)
                assertEquals(outbound.transferId, resumed.transferId)
                assertFalse(resumed.gateUnlocked)
                client.foreground(false)
                host.state.first { !it.phoneForeground }
                assertFalse(host.state.value.phoneCanMove)
            } finally { client.close(); scope.cancel(); host.close() }
        }
    }
}
