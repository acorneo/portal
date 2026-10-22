import XCTest
@testable import NearbyCore

final class NearbyProtocolTests: XCTestCase {
    private func pair() throws -> (NearbyMachine, NearbyMachine) {
        let mac = NearbyIdentity(name: "Mac")
        var host = NearbyMachine(host: true, identity: mac, session: mac.id)
        var phone = NearbyMachine(host: false, identity: NearbyIdentity(name: "Phone"), session: mac.id)
        let offer = try host.receive(phone.hello()).first!
        _ = try phone.receive(offer)
        XCTAssertEqual(host.fingerprint, phone.fingerprint)
        return (host, phone)
    }
    func testFragmentedAndCoalescedFramesRoundTrip() throws {
        let message = NearbyMessage("ping", id: UUID().uuidString)
        let frame = try NearbyFramer.encode(message)
        var framer = NearbyFramer()
        XCTAssertTrue(try framer.append(frame.prefix(2)).isEmpty)
        XCTAssertTrue(try framer.append(frame.dropFirst(2).prefix(3)).isEmpty)
        XCTAssertEqual(try framer.append(frame.dropFirst(5) + frame), [message, message])
    }
    func testOversizedMalformedAndVersionFramesAreRejected() throws {
        var framer = NearbyFramer()
        XCTAssertThrowsError(try framer.append(Data([0, 1, 0, 0])))
        framer = NearbyFramer()
        XCTAssertThrowsError(try framer.append(Data([0, 0, 0, 1, 123])))
        var message = NearbyMessage("hello"); message.version = 9
        XCTAssertThrowsError(try NearbyFramer.encode(message))
        message = NearbyMessage("unknown")
        XCTAssertThrowsError(try NearbyFramer.encode(message))
    }
    func testConsentRequiredAndOnlyMacCanDecide() throws {
        var (host, phone) = try pair()
        XCTAssertEqual(host.phase, .awaitingApproval)
        XCTAssertEqual(phone.phase, .awaitingAcceptance)
        XCTAssertThrowsError(try host.ping())
        XCTAssertThrowsError(try phone.ping())
        XCTAssertThrowsError(try phone.decide(accept: true))
        let approval = try host.decide(accept: true)
        _ = try phone.receive(approval)
        XCTAssertEqual(host.phase, .connected)
        XCTAssertEqual(phone.phase, .connected)
        XCTAssertThrowsError(try host.decide(accept: true))
    }
    func testRejectClosesBothAndDoesNotPermitPayloads() throws {
        var (host, phone) = try pair()
        _ = try phone.receive(host.decide(accept: false))
        XCTAssertEqual(phone.phase, .closed)
        XCTAssertThrowsError(try host.ping())
        XCTAssertThrowsError(try phone.ping())
    }
    func testWrongSelectedSessionAndWrongPeerCannotBeAccepted() throws {
        let mac = NearbyIdentity(name: "Mac")
        var host = NearbyMachine(host: true, identity: mac, session: mac.id)
        let phone = NearbyMachine(host: false, identity: NearbyIdentity(name: "Phone"), session: UUID().uuidString)
        XCTAssertThrowsError(try host.receive(phone.hello()))
        var (pairedHost, pairedPhone) = try pair()
        var offer = try pairedHost.decide(accept: true)
        offer.peerID = UUID().uuidString
        XCTAssertThrowsError(try pairedPhone.receive(offer))
    }
    func testUniquePingAcknowledgementAndReplayDoNotDoubleCount() throws {
        var (host, phone) = try pair()
        _ = try phone.receive(host.decide(accept: true))
        let ping = try phone.ping()
        XCTAssertThrowsError(try phone.ping())
        let pong = try host.receive(ping).first!
        _ = try host.receive(ping)
        XCTAssertEqual(host.receivedPings, 1)
        var unrelated = pong; unrelated.id = UUID().uuidString
        _ = try phone.receive(unrelated)
        XCTAssertNotNil(phone.pendingPing)
        _ = try phone.receive(pong); _ = try phone.receive(pong)
        XCTAssertEqual(phone.acknowledgements, 1)
        XCTAssertNil(phone.pendingPing)
    }
    func testFreshConnectionRejectsOldApprovalAndPayload() throws {
        var (host, phone) = try pair()
        let oldApproval = try host.decide(accept: true)
        _ = try phone.receive(oldApproval)
        let oldPing = try host.ping()
        var freshPhone = NearbyMachine(host: false, identity: phone.identity, session: phone.session)
        var freshHost = NearbyMachine(host: true, identity: host.identity, session: host.session)
        let offer = try freshHost.receive(freshPhone.hello()).first!
        _ = try freshPhone.receive(offer)
        XCTAssertThrowsError(try freshPhone.receive(oldApproval))
        XCTAssertThrowsError(try freshPhone.receive(oldPing))
    }
    func testNativeParametersExplicitlyEnablePeerToPeer() {
        XCTAssertTrue(NearbyNetwork.parameters().includePeerToPeer)
    }
    func testStopEmitsOneStableStateAndIsIdempotent() {
        let network = NearbyNetwork(name: "Mac")
        var phases: [String] = []
        network.changed = { phases.append($0.phase) }
        network.stop(); network.stop()
        XCTAssertEqual(phases, ["stopped", "stopped"])
        XCTAssertTrue(network.status.peerID.isEmpty)
        XCTAssertFalse(network.status.pendingPing)
        XCTAssertEqual(network.status.localID, network.identity.id)
    }
    func testNamesAreBoundedWithoutBreakingUnicode() throws {
        let identity = NearbyIdentity(name: String(repeating: "🌀", count: 100) + "\n")
        XCTAssertLessThanOrEqual(identity.name.utf8.count, 80)
        XCTAssertFalse(identity.name.contains("\n"))
        try NearbyMessage("hello", name: identity.name).validate()
    }
}
