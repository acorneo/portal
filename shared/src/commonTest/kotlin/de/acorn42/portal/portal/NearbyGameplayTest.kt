package de.acorn42.portal.portal

import de.acorn42.portal.phone.PhonePuzzle
import de.acorn42.portal.phone.PhoneRoom
import de.acorn42.portal.world.MoveInput
import de.acorn42.portal.world.Sphere
import de.acorn42.portal.world.Vec2
import de.acorn42.portal.world.WorldSimulation
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

    private fun moveDesktop(roomId: Int, target: Vec2): Vec2 {
        val room = PortalLevel.room(roomId)
        val maze = if (roomId == 1 && host.snapshot.gateUnlocked) PortalLevel.openMaze else room.maze
        val simulation = WorldSimulation(maze, Sphere(host.snapshot.desktop.position()))
        val waypoints = if (roomId == 2) listOf(
            Vec2(2.0, 10.5), Vec2(6.0, 10.5), Vec2(6.0, 2.0),
            Vec2(10.0, 2.0), Vec2(10.0, 10.5), target,
        ) else listOf(target)
        for (waypoint in waypoints) {
            var ticks = 0
            while ((simulation.sphere.position - waypoint).length > 0.18 && ticks++ < 12_000) {
                val delta = waypoint - simulation.sphere.position
                val input = delta * 0.7
                simulation.step(MoveInput(input.x.coerceIn(-1.0, 1.0), input.y.coerceIn(-1.0, 1.0)), 1.0 / 120.0)
            }
            assertTrue(ticks < 12_000, "Could not physically reach waypoint $waypoint in room $roomId; stopped at ${simulation.sphere.position}")
        }
        assertTrue(maze.canOccupy(simulation.sphere))
        return simulation.sphere.position
    }

    private fun solvePhoneVariant(variant: Int) {
        val maze = PhoneRoom.mazeFor(variant)
        val exit = PhoneRoom.exitFor(variant)
        val puzzle = PhonePuzzle(maze, exit)
        val targets = if (variant == 1) {
            listOf(Vec2(7.4, 2.5), Vec2(7.4, 6.4), Vec2(1.5, 6.4), Vec2(1.5, 10.8), exit)
        } else {
            // Reach and steer around each alternating horizontal wall via its actual gap.
            listOf(Vec2(5.5, 2.0), Vec2(5.5, 6.2), Vec2(3.5, 6.2), Vec2(3.5, 10.5), exit)
        }
        for (target in targets) {
            var ticks = 0
            while ((puzzle.state.sphere.position - target).length > 0.18 && !puzzle.state.completed && ticks++ < 8_000) {
                val delta = target - puzzle.state.sphere.position
                puzzle.step(delta * 0.6 - puzzle.state.velocity * 0.45, 1.0 / 120.0)
            }
            assertTrue(ticks < 8_000, "Phone variant $variant could not reach $target: ${puzzle.state}")
        }
        assertTrue(puzzle.state.completed, "Phone variant $variant must complete")
        phone.progress(PhoneCheckpoint.from(puzzle.state, variant))
        val progress = phone.outgoing().first { PortalCodec.client(it).kind == ClientKind.Progress }
        assertEquals(ServerKind.Snapshot, receive(progress).kind)
        phone.receive(PortalCodec.encode(host.state()))
        assertEquals(TransferPhase.ReturnPending, host.snapshot.phase)
        val returnId = host.snapshot.returnId!!
        phone.receive(PortalCodec.encode(host.state()))
        val ack = phone.outgoing().first { PortalCodec.client(it).kind == ClientKind.Acknowledge }
        assertEquals(returnId, PortalCodec.client(ack).transferId)
        assertEquals(ServerKind.Snapshot, receive(ack).kind)
        phone.receive(PortalCodec.encode(host.state()))
    }

    @Test fun twoStagePhysicalTriggersTransferBothPhoneVariantsAndCompleteChapter() {
        connect()
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)
        assertTrue(PortalLevel.room(1).maze.canOccupy(Sphere(PortalLevel.room(1).phonePortal)))
        assertTrue(PortalLevel.room(1).maze.canOccupy(Sphere(PortalLevel.exit)))

        // Reach actual purple handoff by collision-aware movement and transfer to phone variant 1.
        val purple1 = moveDesktop(1, PortalLevel.room(1).phonePortal)
        assertTrue(PortalLevel.atPhonePortal(1, purple1))
        assertTrue(host.desktopCheckpoint(DesktopCheckpoint.from(1, purple1)))
        assertTrue(host.enterPortal())
        phone.receive(PortalCodec.encode(host.state()))
        pump()
        assertTrue(phone.isRoomActive)
        assertEquals(1, host.snapshot.phoneRoom)
        solvePhoneVariant(1)
        assertTrue(host.snapshot.gateUnlocked)
        assertEquals(ChapterObjective.ReachGreenExit, host.snapshot.objective)
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)

        // The green marker lies in reachable floor; entering it immediately advances the stage.
        val green1 = moveDesktop(1, PortalLevel.room(1).chapterExit!!)
        assertTrue(PortalLevel.atChapterExit(1, green1))
        assertTrue(host.desktopCheckpoint(DesktopCheckpoint.from(1, green1)))
        phone.receive(PortalCodec.encode(host.state()))
        assertEquals(2, host.snapshot.chapter)
        assertEquals(2, host.snapshot.desktopRoom)
        assertEquals(ChapterObjective.RollSecondPocketRoom, host.snapshot.objective)
        assertEquals(PortalLevel.room(2).start, host.snapshot.desktop.position())

        // Room 2's purple trigger must be in connected floor reachable by its collision geometry.
        val room2 = PortalLevel.room(2)
        assertTrue(room2.maze.canOccupy(Sphere(room2.phonePortal)))
        val purple2 = moveDesktop(2, room2.phonePortal)
        assertTrue(PortalLevel.atPhonePortal(2, purple2))
        assertTrue(host.desktopCheckpoint(DesktopCheckpoint.from(2, purple2)))
        assertTrue(host.enterPortal())
        phone.receive(PortalCodec.encode(host.state()))
        pump()
        assertTrue(phone.isRoomActive)
        assertEquals(2, host.snapshot.phoneRoom)
        assertEquals(PhoneRoom.secondMaze.start, Vec2(host.snapshot.phone.x, host.snapshot.phone.y))
        solvePhoneVariant(2)
        assertTrue(host.snapshot.completed)
        assertEquals(ChapterObjective.Complete, host.snapshot.objective)
        assertEquals(ActiveDevice.Desktop, host.snapshot.owner)
        assertFalse(host.enterPortal())
    }

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
