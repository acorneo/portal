package de.acorn42.portal.portal

import de.acorn42.portal.phone.PhonePuzzle
import de.acorn42.portal.phone.PhoneRoom
import de.acorn42.portal.world.Sphere
import de.acorn42.portal.world.Vec2
import de.acorn42.portal.world.WorldSimulation
import kotlin.test.*

class PortalSessionTest {
    private var id = 0
    private fun host() = PortalSession("session", "123456", 0, "secret-resume") { "transfer-${++id}" }
    private fun hello() = ClientPacket(ClientKind.Hello, clientId = "phone", pairCode = "123456")
    private fun command(host: PortalSession, kind: ClientKind) = ClientPacket(
        kind, sessionId = "session", clientId = "phone", generation = host.snapshot.generation,
        runId = host.snapshot.runId, transferId = host.snapshot.transferId.orEmpty(),
    )
    private fun phone(host: PortalSession) {
        assertEquals(ServerKind.Welcome, host.hello(hello(), 1).kind)
        assertTrue(host.enterPortal())
        host.receive(command(host, ClientKind.Acknowledge))
        assertEquals(TransferPhase.Phone, host.snapshot.phase)
    }
    private fun completed() = PhoneCheckpoint(PhoneRoom.exit.x, PhoneRoom.exit.y, completed = true)

    private fun completed(roomVariant: Int) = PhoneCheckpoint.initial(roomVariant).copy(
        x = PhoneRoom.exitFor(roomVariant).x,
        y = PhoneRoom.exitFor(roomVariant).y,
        completed = true,
    )

    @Test fun protocolRoundTripsAndRejectsMalformedOversizeFrames() {
        val packet = hello()
        assertEquals(packet, PortalCodec.client(PortalCodec.encode(packet)))
        val snapshot = host().state()
        assertEquals(snapshot, PortalCodec.server(PortalCodec.encode(snapshot)))
        assertFails { PortalCodec.client("{" ) }
        assertFails { PortalCodec.client(" ".repeat(MAX_PACKET_BYTES + 1)) }
        assertFails { PortalCodec.client("{\"kind\":\"FutureCommand\"}") }
    }

    @Test fun pairingExpiresIsSingleClientAndRequiresResumeSecret() {
        val host = host()
        assertEquals(ServerKind.Error, host.hello(hello(), 300_000).kind)
        assertTrue(host.refreshCode("654321", 300_001))
        assertEquals(ServerKind.Welcome, host.hello(hello().copy(pairCode = "654321"), 300_002).kind)
        assertFalse(host.refreshCode("111111", 300_003))
        assertEquals(ServerKind.Error, host.hello(hello(), 300_003).kind)
        assertEquals(ServerKind.Error, host.hello(hello().copy(sessionId = "session", resumeToken = "wrong"), 300_003).kind)
        val resumed = host.hello(hello().copy(sessionId = "session", resumeToken = "secret-resume"), 300_004)
        assertEquals(ServerKind.Welcome, resumed.kind)
        assertEquals(2, host.snapshot.generation)
    }

    @Test fun versionsWrongSessionAndRateLimitedCodesCannotPair() {
        val host = host()
        assertEquals(ServerKind.Error, host.hello(hello().copy(version = 2), 1).kind)
        assertEquals(ServerKind.Error, host.hello(hello().copy(sessionId = "other"), 1).kind)
        repeat(5) { assertEquals(ServerKind.Error, host.hello(hello().copy(pairCode = "wrong"), 1).kind) }
        assertEquals(ServerKind.Error, host.hello(hello(), 2).kind)
        assertEquals(ServerKind.Welcome, host.hello(hello(), 60_001).kind)
    }

    @Test fun handoffsAreAcknowledgedAndReturnUnlockOccursExactlyOnce() {
        val host = host()
        host.hello(hello(), 1)
        assertTrue(host.enterPortal())
        val outbound = host.snapshot.transferId
        assertEquals(ActiveDevice.Neither, host.snapshot.owner)
        assertFalse(host.snapshot.desktopCanMove)
        assertFalse(host.enterPortal())
        host.receive(command(host, ClientKind.Acknowledge))
        val activeRevision = host.snapshot.revision
        host.receive(command(host, ClientKind.Acknowledge))
        assertEquals(activeRevision, host.snapshot.revision)
        val completion = command(host, ClientKind.Progress).copy(sequence = 1, phone = completed())
        host.receive(completion)
        assertEquals(TransferPhase.ReturnPending, host.snapshot.phase)
        assertFalse(host.snapshot.gateUnlocked)
        assertEquals(ActiveDevice.Neither, host.snapshot.owner)
        assertNotEquals(outbound, host.snapshot.returnId)
        val pending = host.snapshot
        host.receive(completion)
        assertEquals(pending, host.snapshot)
        val ack = command(host, ClientKind.Acknowledge).copy(transferId = host.snapshot.returnId!!)
        host.receive(ack)
        assertTrue(host.snapshot.gateUnlocked)
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)
        val returned = host.snapshot
        repeat(4) { host.receive(ack); host.receive(completion) }
        assertEquals(returned, host.snapshot)
        assertFalse(host.enterPortal())
    }

    @Test fun authoredChapterAdvancesThroughTwoPhoneVariantsAndCompletesOnMac() {
        val host = host()
        host.hello(hello(), 1)

        assertTrue(host.enterPortal())
        host.receive(command(host, ClientKind.Acknowledge))
        assertEquals(1, host.snapshot.phoneRoom)
        host.receive(command(host, ClientKind.Progress).copy(
            runId = host.snapshot.runId,
            sequence = 1,
            phone = completed(1),
        ))
        host.receive(command(host, ClientKind.Acknowledge).copy(transferId = host.snapshot.returnId!!))
        assertEquals(ChapterObjective.ReachGreenExit, host.snapshot.objective)
        assertTrue(host.snapshot.gateUnlocked)
        assertFalse(host.snapshot.completed)

        assertTrue(host.desktopCheckpoint(DesktopCheckpoint.from(1, PortalLevel.exit)))
        assertEquals(2, host.snapshot.chapter)
        assertEquals(2, host.snapshot.desktopRoom)
        assertEquals(ChapterObjective.RollSecondPocketRoom, host.snapshot.objective)
        assertTrue(host.enterPortal())
        host.receive(command(host, ClientKind.Acknowledge))
        assertEquals(2, host.snapshot.phoneRoom)
        assertEquals(PhoneRoom.secondMaze.start, Vec2(host.snapshot.phone.x, host.snapshot.phone.y))

        host.receive(command(host, ClientKind.Progress).copy(
            runId = host.snapshot.runId,
            sequence = 1,
            phone = completed(2),
        ))
        host.receive(command(host, ClientKind.Acknowledge).copy(transferId = host.snapshot.returnId!!))
        assertTrue(host.snapshot.completed)
        assertEquals(ChapterObjective.Complete, host.snapshot.objective)
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)
        assertFalse(host.enterPortal())
    }

    @Test fun newChapterKeepsPairingButRejectsProgressFromTheOldRun() {
        val host = host()
        host.hello(hello(), 1)
        host.enterPortal()
        host.receive(command(host, ClientKind.Acknowledge))
        val stale = command(host, ClientKind.Progress).copy(
            runId = host.snapshot.runId,
            sequence = 1,
            phone = PhoneCheckpoint(2.5, 2.0),
        )
        val oldRun = host.snapshot.runId
        val reset = host.newChapter()
        assertNotEquals(oldRun, reset.runId)
        assertEquals("phone", reset.clientId)
        assertTrue(reset.connected)
        assertEquals(ChapterObjective.ExploreFirstRoom, reset.objective)
        assertEquals(1, reset.desktopRoom)
        assertEquals(ServerKind.Error, host.receive(stale).kind)
        assertEquals(ChapterObjective.ExploreFirstRoom, host.snapshot.objective)
        assertFalse(host.snapshot.completed)
    }

    @Test fun desktopCheckpointSurvivesReconnectWithoutChangingRoomOwnership() {
        val host = host()
        host.hello(hello(), 1)
        val checkpoint = DesktopCheckpoint.from(1, Vec2(2.5, 1.5))
        assertTrue(host.desktopCheckpoint(checkpoint))
        assertEquals(checkpoint, host.snapshot.desktop)
        val generation = host.snapshot.generation
        host.disconnect(generation)
        assertFalse(host.snapshot.connected)
        assertEquals(checkpoint, host.snapshot.desktop)

        val resumed = host.hello(
            hello().copy(sessionId = "session", resumeToken = "secret-resume"),
            2,
        )
        assertEquals(ServerKind.Welcome, resumed.kind)
        assertEquals(checkpoint, host.snapshot.desktop)
        assertEquals(TransferPhase.Desktop, host.snapshot.phase)
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)
    }

    @Test fun staleAndWrongSessionInputsNeverChangeOwnershipOrProgress() {
        val host = host(); phone(host)
        val valid = command(host, ClientKind.Progress).copy(sequence = 1, phone = PhoneCheckpoint(2.5, 2.0))
        for (bad in listOf(valid.copy(sessionId = "other"), valid.copy(clientId = "other"),
            valid.copy(generation = 0), valid.copy(transferId = "old"), valid.copy(roomId = "other"))) {
            val before = host.snapshot
            assertEquals(ServerKind.Error, host.receive(bad).kind)
            assertEquals(before, host.snapshot)
        }
        host.receive(valid)
        val accepted = host.snapshot
        host.receive(valid.copy(sequence = 0, phone = completed()))
        assertEquals(accepted, host.snapshot)
    }

    @Test fun invalidPhysicsAndFalseCompletionAreRejected() {
        val host = host(); phone(host)
        for (point in listOf(PhoneCheckpoint(Double.NaN, 2.0), PhoneCheckpoint(0.0, 0.0),
            PhoneCheckpoint(2.0, 2.0, vx = 20.0), PhoneCheckpoint(2.0, 2.0, completed = true))) {
            val before = host.snapshot
            assertEquals(ServerKind.Error, host.receive(command(host, ClientKind.Progress).copy(sequence = 1, phone = point)).kind)
            assertEquals(before, host.snapshot)
        }
    }

    @Test fun disconnectAtEveryTransferBoundaryResumesWithoutReapplyingEffects() {
        for (boundary in 0..3) {
            val host = host(); host.hello(hello(), 1); host.enterPortal()
            if (boundary >= 1) host.receive(command(host, ClientKind.Acknowledge))
            if (boundary >= 2) host.receive(command(host, ClientKind.Progress).copy(sequence = 1, phone = completed()))
            if (boundary >= 3) host.receive(command(host, ClientKind.Acknowledge).copy(transferId = host.snapshot.returnId!!))
            val before = host.snapshot
            val oldGeneration = before.generation
            host.disconnect(oldGeneration)
            assertFalse(host.snapshot.desktopCanMove)
            assertFalse(host.snapshot.phoneCanMove)
            host.hello(hello().copy(sessionId = "session", resumeToken = "secret-resume"), 10)
            assertEquals(before.phase, host.snapshot.phase)
            assertEquals(before.transferId, host.snapshot.transferId)
            assertEquals(before.returnId, host.snapshot.returnId)
            assertEquals(before.phone, host.snapshot.phone)
            assertEquals(before.gateUnlocked, host.snapshot.gateUnlocked)
            host.disconnect(oldGeneration)
            assertTrue(host.snapshot.connected, "Old connection cleanup cannot disconnect a resumed phone")
        }
    }

    @Test fun foregroundLossPausesBothOwnersWithoutLosingProgress() {
        val host = host(); phone(host)
        host.hostForeground(false)
        assertFalse(host.snapshot.phoneCanMove)
        host.hostForeground(true)
        host.receive(command(host, ClientKind.Foreground).copy(foreground = false))
        assertFalse(host.snapshot.phoneCanMove)
        host.receive(command(host, ClientKind.Foreground))
        assertTrue(host.snapshot.phoneCanMove)
    }

    @Test fun replicaRetainsUnacknowledgedProgressAndRejectsStaleSnapshots() {
        val host = host()
        val replica = PhoneReplica("phone")
        assertTrue(replica.accept(host.hello(replica.hello("123456", true), 1)))
        host.enterPortal(); replica.accept(host.state())
        replica.acknowledge(host.snapshot.transferId!!)
        replica.outgoing(true).forEach { replica.accept(host.receive(it)) }
        assertEquals(TransferPhase.Phone, replica.snapshot?.phase)
        val checkpoint = PhoneCheckpoint(2.5, 2.0)
        replica.progress(checkpoint)
        host.disconnect(host.snapshot.generation)
        assertTrue(replica.accept(host.hello(replica.hello("ignored", true), 10)))
        assertEquals(checkpoint, replica.phone)
        replica.outgoing(true).forEach { replica.accept(host.receive(it)) }
        assertEquals(checkpoint, host.snapshot.phone)
        val accepted = replica.snapshot
        assertFalse(replica.accept(ServerPacket(ServerKind.Snapshot, snapshot = accepted!!.copy(sessionId = "wrong"))))
        assertFalse(replica.accept(ServerPacket(ServerKind.Snapshot, snapshot = accepted.copy(revision = 0))))
        assertEquals(accepted, replica.snapshot)
    }

    @Test fun replicaRetriesLostReturnAcknowledgementAfterReconnect() {
        val host = host()
        val replica = PhoneReplica("phone")
        replica.accept(host.hello(replica.hello("123456", true), 1))
        host.enterPortal(); replica.accept(host.state())
        replica.acknowledge(host.snapshot.transferId!!)
        replica.outgoing(true).forEach { replica.accept(host.receive(it)) }
        replica.progress(completed())
        replica.outgoing(true).forEach { replica.accept(host.receive(it)) }
        val returnId = host.snapshot.returnId!!
        replica.acknowledge(returnId) // Socket closes before delivery.
        host.disconnect(host.snapshot.generation)
        replica.accept(host.hello(replica.hello("ignored", true), 10))
        val replay = replica.outgoing(true)
        replay.forEach { replica.accept(host.receive(it)) }
        val returned = host.snapshot
        assertTrue(returned.gateUnlocked)
        assertEquals(returnId, returned.returnId)
        replay.forEach { replica.accept(host.receive(it)) }
        assertEquals(returned, host.snapshot)
    }

    @Test fun restoringCheckpointPreservesPhoneProgressAndRejectsInvalidState() {
        val puzzle = PhonePuzzle()
        val retained = PhoneCheckpoint(2.5, 2.0, vx = 0.5).toState()
        puzzle.restore(retained)
        assertEquals(retained, puzzle.state)
        puzzle.stop()
        assertEquals(retained.sphere, puzzle.state.sphere)
        assertFails { puzzle.restore(PhoneCheckpoint(0.0, 0.0).toState()) }
        assertFails { puzzle.restore(PhoneCheckpoint(2.5, 2.0, vx = Double.NaN).toState()) }
        assertFails { puzzle.restore(PhoneCheckpoint(2.5, 2.0, completed = true).toState()) }
    }

    @Test fun openingGateAndReturningRetainsExplorationMemory() {
        val before = WorldSimulation(PortalLevel.closedMaze)
        val known = before.exploration.state.exploredCells
        val returned = WorldSimulation(PortalLevel.openMaze, Sphere(PortalLevel.exit), initialExplored = known)
        assertTrue(returned.exploration.state.exploredCells.containsAll(known))
        assertEquals(PortalLevel.exit, returned.sphere.position)
    }

    @Test fun gateAndReturnPointHaveValidGeometry() {
        assertTrue(PortalLevel.closedMaze.canOccupy(Sphere(PortalLevel.entrance)))
        assertTrue(PortalLevel.openMaze.canOccupy(Sphere(PortalLevel.exit)))
        assertFalse(PortalLevel.closedMaze.canOccupy(Sphere(Vec2(5.5, 2.5))))
        assertTrue(PortalLevel.openMaze.canOccupy(Sphere(Vec2(5.5, 2.5))))
    }
}
