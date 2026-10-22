import Foundation
import CryptoKit

public let nearbyService = "_portalnear._tcp"
public let nearbyVersion = 1
public let nearbyMaxFrame = 65536
public let nearbyMaxGamePayload = 16384

public enum NearbyFailure: Error { case invalidFrame, invalidMessage, unexpectedMessage }

public struct NearbyMessage: Codable, Equatable {
    public var version = nearbyVersion
    public var gameVersion: Int? = 1
    public var payload: String? = nil
    public var kind: String
    public var session = ""
    public var peerID = ""
    public var name = ""
    public var nonce = ""
    public var echo = ""
    public var id = ""
    public init(_ kind: String, session: String = "", peerID: String = "", name: String = "", nonce: String = "", echo: String = "", id: String = "") {
        self.kind = kind; self.session = session; self.peerID = peerID; self.name = name
        self.nonce = nonce; self.echo = echo; self.id = id
    }
    public func validate() throws {
        guard version == nearbyVersion, ["hello", "offer", "accept", "reject", "ping", "pong", "bye", "game"].contains(kind),
              [session, peerID, nonce, echo, id].allSatisfy({ $0.utf8.count <= 80 }), name.utf8.count <= 96,
              name.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }),
              payload == nil || payload!.utf8.count <= nearbyMaxGamePayload,
              gameVersion == nil || gameVersion == 1 else { throw NearbyFailure.invalidMessage }
    }
}

/// UInt32 big-endian byte length followed by one strict, bounded JSON message.
public struct NearbyFramer {
    private var buffer = Data()
    public init() {}
    public mutating func append(_ bytes: Data) throws -> [NearbyMessage] {
        buffer.append(bytes)
        var messages: [NearbyMessage] = []
        while buffer.count >= 4 {
            let count = buffer.prefix(4).reduce(0) { ($0 << 8) | Int($1) }
            guard count > 0 && count <= nearbyMaxFrame else { throw NearbyFailure.invalidFrame }
            guard buffer.count >= count + 4 else { break }
            let message = try JSONDecoder().decode(NearbyMessage.self, from: Data(buffer.dropFirst(4).prefix(count)))
            try message.validate()
            messages.append(message)
            buffer.removeFirst(count + 4)
        }
        guard buffer.count <= nearbyMaxFrame + 4 else { throw NearbyFailure.invalidFrame }
        return messages
    }
    public static func encode(_ message: NearbyMessage) throws -> Data {
        try message.validate()
        let payload = try JSONEncoder().encode(message)
        guard !payload.isEmpty && payload.count <= nearbyMaxFrame else { throw NearbyFailure.invalidFrame }
        let n = UInt32(payload.count)
        return Data([UInt8((n >> 24) & 255), UInt8((n >> 16) & 255), UInt8((n >> 8) & 255), UInt8(n & 255)]) + payload
    }
}

public func nearbyName(_ value: String, limit: Int = 80) -> String {
    var result = ""
    for scalar in value.unicodeScalars where !CharacterSet.controlCharacters.contains(scalar) {
        if result.utf8.count + String(scalar).utf8.count > limit { break }
        result.unicodeScalars.append(scalar)
    }
    return result.isEmpty ? "PORTAL peer" : result
}

public struct NearbyIdentity: Codable, Equatable {
    public let id: String
    public let name: String
    public init(id: String = UUID().uuidString, name: String) {
        self.id = id
        self.name = nearbyName(name)
    }
}

public enum NearbyPhase: String { case idle, awaitingHello, awaitingOffer, awaitingApproval, awaitingAcceptance, connected, closed }

/// Pure consent/session state. Every reconnect creates a fresh machine/nonces.
/// Fingerprint correlates the two screens; plaintext POC is not authenticated TLS.
public struct NearbyMachine {
    public let host: Bool
    public let identity: NearbyIdentity
    public let session: String
    public let nonce: String
    public private(set) var peer: NearbyIdentity?
    public private(set) var phase: NearbyPhase
    public private(set) var fingerprint = ""
    public private(set) var acknowledgements = 0
    public private(set) var receivedPings = 0
    public private(set) var pendingPing: String?
    private var seenPings: [String] = []
    private var peerNonce = ""

    public init(host: Bool, identity: NearbyIdentity, session: String, nonce: String = UUID().uuidString) {
        self.host = host; self.identity = identity; self.session = session; self.nonce = nonce
        phase = host ? .awaitingHello : .awaitingOffer
    }
    public func hello() -> NearbyMessage {
        NearbyMessage("hello", session: session, peerID: identity.id, name: identity.name, nonce: nonce)
    }
    private func packet(_ kind: String, id: String = "") -> NearbyMessage {
        NearbyMessage(kind, session: session, peerID: identity.id, name: identity.name, nonce: nonce, echo: peerNonce, id: id)
    }
    private mutating func correlate() {
        let ordered = host ? [nonce, peerNonce] : [peerNonce, nonce]
        fingerprint = SHA256.hash(data: Data((["portal-nearby-v1", session] + ordered).joined(separator: "|").utf8))
            .prefix(3).map { String(format: "%02X", $0) }.joined(separator: " ")
    }
    public mutating func receive(_ message: NearbyMessage) throws -> [NearbyMessage] {
        try message.validate()
        guard message.session == session, UUID(uuidString: session) != nil,
              UUID(uuidString: message.peerID) != nil, UUID(uuidString: message.nonce) != nil else { throw NearbyFailure.invalidMessage }
        switch (host, phase, message.kind) {
        case (true, .awaitingHello, "hello"):
            guard message.gameVersion == 1 else { throw NearbyFailure.invalidMessage }
            peer = NearbyIdentity(id: message.peerID, name: message.name); peerNonce = message.nonce
            correlate(); phase = .awaitingApproval
            return [packet("offer")]
        case (false, .awaitingOffer, "offer"):
            guard message.echo == nonce, message.peerID == session, message.gameVersion == 1 else { throw NearbyFailure.invalidMessage }
            peer = NearbyIdentity(id: message.peerID, name: message.name); peerNonce = message.nonce
            correlate(); phase = .awaitingAcceptance
            return []
        case (false, .awaitingAcceptance, "accept"):
            try verify(message); phase = .connected
            return []
        case (_, _, "reject"), (_, _, "bye"):
            try verify(message); phase = .closed; pendingPing = nil
            return []
        case (_, .connected, "game"):
            try verify(message)
            guard message.payload != nil else { throw NearbyFailure.invalidMessage }
            return []
        case (_, .connected, "ping"):
            try verify(message)
            guard UUID(uuidString: message.id) != nil else { throw NearbyFailure.invalidMessage }
            if !seenPings.contains(message.id) {
                receivedPings += 1; seenPings.append(message.id)
                if seenPings.count > 64 { seenPings.removeFirst() }
            }
            return [packet("pong", id: message.id)]
        case (_, .connected, "pong"):
            try verify(message)
            if message.id == pendingPing { acknowledgements += 1; pendingPing = nil }
            return []
        default: throw NearbyFailure.unexpectedMessage
        }
    }
    private func verify(_ message: NearbyMessage) throws {
        guard peer?.id == message.peerID, message.nonce == peerNonce, message.echo == nonce else { throw NearbyFailure.invalidMessage }
    }
    public mutating func decide(accept: Bool) throws -> NearbyMessage {
        guard host && phase == .awaitingApproval else { throw NearbyFailure.unexpectedMessage }
        phase = accept ? .connected : .closed
        return packet(accept ? "accept" : "reject")
    }
    public func game(_ payload: String) throws -> NearbyMessage {
        guard phase == .connected, payload.utf8.count <= nearbyMaxGamePayload else { throw NearbyFailure.unexpectedMessage }
        var message = packet("game"); message.payload = payload
        return message
    }
    public mutating func ping(id: String = UUID().uuidString) throws -> NearbyMessage {
        guard phase == .connected, pendingPing == nil, UUID(uuidString: id) != nil else { throw NearbyFailure.unexpectedMessage }
        pendingPing = id
        return packet("ping", id: id)
    }
}
