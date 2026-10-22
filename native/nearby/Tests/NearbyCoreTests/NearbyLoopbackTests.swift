import XCTest
import Network
@testable import NearbyCore

final class NearbyLoopbackTests: XCTestCase {
    @MainActor
    func testNativeConsentPingBusyPeerRefusalAndDisconnect() async throws {
        let host = NearbyNetwork(name: "Loopback Mac")
        let phone = NearbyNetwork(name: "Loopback Phone")
        let other = NearbyNetwork(name: "Unselected Phone")
        defer { host.stop(); phone.stop(); other.stop() }
        let listening = expectation(description: "native listener ready")
        var ready = false
        host.changed = { status in
            if status.phase == "advertising" && !ready { ready = true; listening.fulfill() }
        }
        host.listen(advertiseService: false) // No discovery/radio/privacy result is implied.
        await fulfillment(of: [listening], timeout: 4)
        let port = try XCTUnwrap(host.listenerPort)
        let endpoint = NWEndpoint.hostPort(host: "127.0.0.1", port: port)
        let request = expectation(description: "Mac requires consent")
        let offer = expectation(description: "selected phone receives matching offer")
        var requested = false; var offered = false
        host.changed = { status in
            if status.phase == "awaitingApproval" && !requested { requested = true; request.fulfill() }
        }
        phone.changed = { status in
            if status.phase == "awaitingAcceptance" && !offered { offered = true; offer.fulfill() }
        }
        phone.connectSelected(endpoint: endpoint, sessionID: host.identity.id)
        await fulfillment(of: [request, offer], timeout: 4)
        XCTAssertEqual(host.status.fingerprint, phone.status.fingerprint)
        XCTAssertNotEqual(host.status.phase, "connected")
        let connected = expectation(description: "Mac explicitly approves")
        var approved = false
        phone.changed = { status in
            if status.phase == "connected" && !approved { approved = true; connected.fulfill() }
        }
        host.decide(request: "stale-ui-request", accept: true)
        XCTAssertEqual(host.status.phase, "awaitingApproval")
        host.decide(request: host.status.requestID, accept: true)
        await fulfillment(of: [connected], timeout: 4)
        let gameAtHost = expectation(description: "gameplay reaches consented Mac")
        let gameAtPhone = expectation(description: "snapshot reaches selected phone")
        var hostPayloads: [String] = []
        host.receivedGame = { payload in
            hostPayloads.append(payload)
            gameAtHost.fulfill()
            host.sendGame("{\"snapshot\":1}", request: host.status.requestID)
        }
        phone.receivedGame = { payload in
            XCTAssertEqual(payload, "{\"snapshot\":1}")
            gameAtPhone.fulfill()
        }
        phone.sendGame("stale request must not send", request: "stale")
        phone.sendGame("{\"transfer\":\"unique-1\"}", request: phone.status.requestID)
        await fulfillment(of: [gameAtHost, gameAtPhone], timeout: 4)
        XCTAssertEqual(hostPayloads, ["{\"transfer\":\"unique-1\"}"])
        let pong = expectation(description: "real framed ping/pong acknowledged")
        var acknowledged = false
        phone.changed = { status in
            if status.acknowledgements == 1 && !acknowledged { acknowledged = true; pong.fulfill() }
        }
        phone.ping()
        await fulfillment(of: [pong], timeout: 4)
        XCTAssertEqual(host.status.receivedPings, 1)
        XCTAssertFalse(phone.status.pendingPing)
        let refused = expectation(description: "second peer cannot replace approved phone")
        var denied = false
        other.changed = { status in
            if status.phase == "disconnected" && !denied { denied = true; refused.fulfill() }
        }
        other.connectSelected(endpoint: endpoint, sessionID: host.identity.id)
        await fulfillment(of: [refused], timeout: 4)
        XCTAssertEqual(host.status.peerID, phone.identity.id)
        XCTAssertEqual(host.status.phase, "connected")
        let disconnected = expectation(description: "phone stop releases host connection")
        var closed = false
        host.changed = { status in
            if status.phase == "advertising" && status.peerID.isEmpty && !closed { closed = true; disconnected.fulfill() }
        }
        phone.stop()
        await fulfillment(of: [disconnected], timeout: 4)
        host.stop()
        XCTAssertEqual(host.status.phase, "stopped")
    }
}
