import Foundation
import Network

public struct NearbyFoundPeer: Codable, Equatable {
    public let id: String
    public let name: String
}

public struct NearbyStatus: Codable, Equatable {
    public var phase = "stopped"
    public var message = "Find your nearby Mac to play PORTAL"
    public var localName = ""
    public var localID = ""
    public var peerName = ""
    public var peerID = ""
    public var fingerprint = ""
    public var requestID = ""
    public var acknowledgements = 0
    public var receivedPings = 0
    public var pendingPing = false
    public var peers: [NearbyFoundPeer] = []
    public init() {}
}

/// All methods/callbacks run on the main queue. No motion/game state is owned here.
public final class NearbyNetwork {
    public let identity: NearbyIdentity
    public private(set) var status = NearbyStatus()
    public var receivedGame: ((String) -> Void)?
    public var changed: ((NearbyStatus) -> Void)?
    private var listener: NWListener?
    private var browser: NWBrowser?
    private var endpoints: [String: NWEndpoint] = [:]
    private var connection: NWConnection?
    private var machine: NearbyMachine?
    private var framer = NearbyFramer()
    private var deadline: DispatchWorkItem?
    private var heartbeat: DispatchSourceTimer?
    private var pingDeadline: DispatchWorkItem?
    private var requestID = ""
    private var runningHost = false
    private var generation = 0

    public init(name: String, id: String = UUID().uuidString) {
        identity = NearbyIdentity(id: id, name: name)
        status.localName = identity.name; status.localID = identity.id
    }
    public static func parameters() -> NWParameters {
        let parameters = NWParameters.tcp
        parameters.includePeerToPeer = true
        return parameters
    }
    private func publish(_ message: String? = nil) {
        if let machine {
            status.phase = machine.phase.rawValue
            status.peerName = machine.peer?.name ?? ""; status.peerID = machine.peer?.id ?? ""
            status.fingerprint = machine.fingerprint
            status.acknowledgements = machine.acknowledgements; status.receivedPings = machine.receivedPings
            status.pendingPing = machine.pendingPing != nil
        }
        if let message { status.message = message }
        status.requestID = requestID
        changed?(status)
    }
    public func advertise() { listen(advertiseService: true) }
    // Internal seam also permits real loopback transport tests without Bonjour.
    var listenerPort: NWEndpoint.Port? { listener?.port }
    func listen(advertiseService: Bool) {
        stop(); runningHost = true
        do {
            let listener = try NWListener(using: Self.parameters())
            self.listener = listener
            if advertiseService { listener.service = NWListener.Service(name: "PORTAL \(nearbyName(identity.name, limit: 24)) · \(identity.id.prefix(4))", type: nearbyService, txtRecord: NWTXTRecord(["v": "1", "game": "1", "id": identity.id])) }
            listener.stateUpdateHandler = { [weak self, weak listener] state in
                guard let self, let listener, self.listener === listener else { return }
                switch state {
                case .ready: self.status.phase = "advertising"; self.publish("Nearby session available · waiting for an iPhone")
                case .waiting(let error): self.publish("Nearby unavailable: \(Self.explain(error)). Stop and retry after checking Wi-Fi/privacy.")
                case .failed(let error): self.stop(message: Self.explain(error))
                default: break
                }
            }
            listener.newConnectionHandler = { [weak self] incoming in
                guard let self else { incoming.cancel(); return }
                self.acceptCandidate(incoming)
            }
            listener.start(queue: .main)
            status.phase = "starting"; publish("Starting peer-to-peer Wi-Fi advertisement…")
        } catch { stop(message: "Cannot advertise: \(error.localizedDescription)") }
    }
    public func browse() {
        stop()
        let browser = NWBrowser(for: .bonjourWithTXTRecord(type: nearbyService, domain: nil), using: Self.parameters())
        self.browser = browser
        browser.stateUpdateHandler = { [weak self, weak browser] state in
            guard let self, let browser, self.browser === browser else { return }
            switch state {
            case .ready: self.status.phase = "browsing"; self.publish("Looking for nearby Macs · no shared router required")
            case .waiting(let error): self.publish("Discovery unavailable: \(Self.explain(error)). Check Local Network access and Wi-Fi, then Retry.")
            case .failed(let error): self.stop(message: Self.explain(error))
            default: break
            }
        }
        browser.browseResultsChangedHandler = { [weak self, weak browser] results, _ in
            guard let self, let browser, self.browser === browser, self.connection == nil else { return }
            var peers: [NearbyFoundPeer] = []; var endpoints: [String: NWEndpoint] = [:]
            for result in results.prefix(32) {
                guard case .bonjour(let txt) = result.metadata,
                      txt["v"] == "1", txt["game"] == "1", let id = txt["id"], UUID(uuidString: id) != nil,
                      case .service(let name, _, _, _) = result.endpoint else { continue }
                if endpoints[id] == nil { peers.append(NearbyFoundPeer(id: id, name: name)); endpoints[id] = result.endpoint }
            }
            self.endpoints = endpoints
            self.status.peers = peers.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
            self.publish()
        }
        status.phase = "browsing"; publish("Discovering nearby Macs…")
        browser.start(queue: .main)
    }
    public func select(_ id: String) {
        guard connection == nil, let endpoint = endpoints[id], status.peers.contains(where: { $0.id == id }) else { return }
        connectSelected(endpoint: endpoint, sessionID: id)
    }
    func connectSelected(endpoint: NWEndpoint, sessionID: String) {
        guard connection == nil, UUID(uuidString: sessionID) != nil else { return }
        machine = NearbyMachine(host: false, identity: identity, session: sessionID)
        browser?.cancel(); browser = nil; endpoints.removeAll(); status.peers = []
        begin(NWConnection(to: endpoint, using: Self.parameters()), host: false)
    }
    private func acceptCandidate(_ incoming: NWConnection) {
        // One request/approved peer at a time. No automatic selection or approval.
        guard runningHost, connection == nil else { incoming.cancel(); return }
        machine = NearbyMachine(host: true, identity: identity, session: identity.id)
        begin(incoming, host: true)
    }
    private func begin(_ socket: NWConnection, host: Bool) {
        generation += 1
        let token = generation
        connection = socket; framer = NearbyFramer(); requestID = UUID().uuidString
        publish(host ? "Receiving a nearby pairing request…" : "Connecting to selected Mac…")
        armDeadline(seconds: 25, generation: token, message: "Pairing timed out · select again and approve on Mac")
        socket.stateUpdateHandler = { [weak self, weak socket] state in
            guard let self, let socket, self.connection === socket, self.generation == token else { return }
            switch state {
            case .ready:
                if !host, let hello = self.machine?.hello() { self.send(hello) }
                self.receive(socket, generation: token)
            case .waiting(let error): self.publish("Connection waiting: \(Self.explain(error))")
            case .failed(let error): self.disconnect(message: Self.explain(error))
            case .cancelled: self.disconnect(message: "Peer disconnected · retry requires fresh selection/approval")
            default: break
            }
        }
        socket.start(queue: .main)
    }
    private func receive(_ socket: NWConnection, generation token: Int) {
        socket.receive(minimumIncompleteLength: 1, maximumLength: nearbyMaxFrame) { [weak self, weak socket] data, _, complete, error in
            guard let self, let socket, self.connection === socket, self.generation == token else { return }
            do {
                if let data {
                    for message in try self.framer.append(data) {
                        guard var machine = self.machine else { throw NearbyFailure.unexpectedMessage }
                        let responses = try machine.receive(message)
                        self.machine = machine
                        if message.kind == "game", let payload = message.payload { self.receivedGame?(payload) }
                        responses.forEach { self.send($0) }
                        switch machine.phase {
                        case .awaitingApproval: self.publish("Compare fingerprint with the iPhone, then explicitly Accept or Reject")
                        case .awaitingAcceptance: self.publish("Compare fingerprint · waiting for Mac approval")
                        case .connected:
                            self.deadline?.cancel()
                            if machine.pendingPing == nil { self.pingDeadline?.cancel() }
                            self.startHeartbeat()
                            self.publish("Connected to \(machine.peer?.name ?? "peer")")
                        case .closed: self.disconnect(message: "Peer rejected or ended this connection · Retry to select again"); return
                        default: self.publish()
                        }
                    }
                }
                if let error { self.disconnect(message: Self.explain(error)) }
                else if complete { self.disconnect(message: "Peer disconnected · Retry to select again") }
                else { self.receive(socket, generation: token) }
            } catch { self.disconnect(message: "Rejected invalid, stale or unapproved peer message") }
        }
    }
    public func decide(request: String, accept: Bool) {
        guard request == requestID, var machine else { return }
        do {
            let response = try machine.decide(accept: accept)
            self.machine = machine
            send(response) { [weak self] in if !accept { self?.disconnect(message: "Pairing rejected · waiting for a new selection") } }
            deadline?.cancel()
            if accept { startHeartbeat() }
            publish(accept ? "Connected to \(machine.peer?.name ?? "iPhone")" : "Rejecting request…")
        } catch { /* Stale UI actions cannot accept another request. */ }
    }
    public func ping() {
        guard var machine else { return }
        do {
            let message = try machine.ping(); self.machine = machine
            send(message); publish()
            let token = generation
            let timeout = DispatchWorkItem { [weak self] in
                guard let self, self.generation == token, self.machine?.pendingPing != nil else { return }
                self.disconnect(message: "Connection timed out · reconnect to resume")
            }
            pingDeadline?.cancel(); pingDeadline = timeout
            DispatchQueue.main.asyncAfter(deadline: .now() + 5, execute: timeout)
        } catch { }
    }
    public func sendGame(_ payload: String, request: String) {
        guard request == requestID, let message = try? machine?.game(payload) else { return }
        send(message)
    }
    private func startHeartbeat() {
        guard heartbeat == nil else { return }
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now() + 1, repeating: 2)
        timer.setEventHandler { [weak self] in
            guard let self, self.machine?.phase == .connected, self.machine?.pendingPing == nil else { return }
            self.ping()
        }
        heartbeat = timer; timer.resume()
    }
    private func send(_ message: NearbyMessage, completion: (() -> Void)? = nil) {
        guard let socket = connection, let data = try? NearbyFramer.encode(message) else { return }
        let token = generation
        socket.send(content: data, completion: .contentProcessed { [weak self, weak socket] error in
            guard let self, let socket, self.connection === socket, self.generation == token else { return }
            if let error { self.disconnect(message: Self.explain(error)) } else { completion?() }
        })
    }
    private func armDeadline(seconds: Double, generation token: Int, message: String) {
        deadline?.cancel()
        let item = DispatchWorkItem { [weak self] in
            guard let self, self.generation == token else { return }
            self.disconnect(message: message)
        }
        deadline = item
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: item)
    }
    private func clearConnection() {
        generation += 1; deadline?.cancel(); pingDeadline?.cancel(); heartbeat?.cancel(); heartbeat = nil
        connection?.stateUpdateHandler = nil; connection?.cancel(); connection = nil
        machine = nil; framer = NearbyFramer(); requestID = ""
        status.peerName = ""; status.peerID = ""; status.fingerprint = ""; status.requestID = ""
        status.pendingPing = false
    }
    public func disconnect(message: String = "Disconnected · Retry requires fresh selection and Mac approval") {
        clearConnection()
        status.phase = runningHost ? "advertising" : "disconnected"
        publish(message)
    }
    public func stop(message: String = "Nearby connection stopped") {
        runningHost = false
        listener?.stateUpdateHandler = nil; listener?.cancel(); listener = nil
        browser?.stateUpdateHandler = nil; browser?.cancel(); browser = nil
        endpoints.removeAll(); status.peers = []
        clearConnection(); status.phase = "stopped"; publish(message)
    }
    private static func explain(_ error: NWError) -> String {
        if case .dns(let code) = error, code == -65570 {
            return "Local Network permission denied · enable PORTAL in system Privacy & Security > Local Network"
        }
        return "\(error) · verify Wi-Fi and Local Network access; keep firewall enabled and allow PORTAL if prompted"
    }
}
