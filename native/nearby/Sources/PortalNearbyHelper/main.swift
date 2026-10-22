import Foundation
import NearbyCore
import Darwin

struct Command: Decodable {
    let kind: String
    var requestID: String?
    var payload: String?
}

signal(SIGPIPE, SIG_IGN)
let network = NearbyNetwork(name: Host.current().localizedName ?? "PORTAL Mac")
network.changed = { state in
    guard let data = try? JSONEncoder().encode(state) else { return }
    try? FileHandle.standardOutput.write(contentsOf: data + Data([10]))
}
network.receivedGame = { payload in
    let event: [String: String] = ["event": "game", "connectionID": network.status.requestID, "peerID": network.status.peerID, "payload": payload]
    if let data = try? JSONSerialization.data(withJSONObject: event) {
        try? FileHandle.standardOutput.write(contentsOf: data + Data([10]))
    }
}
network.stop(message: "Nearby connection ready")
Thread.detachNewThread {
    while let line = readLine(strippingNewline: true) {
        guard line.utf8.count <= nearbyMaxFrame, let data = line.data(using: .utf8), let command = try? JSONDecoder().decode(Command.self, from: data) else {
            DispatchQueue.main.async { network.stop(message: "Invalid JVM helper command") }
            continue
        }
        DispatchQueue.main.async {
            switch command.kind {
            case "advertise": network.advertise()
            case "accept": network.decide(request: command.requestID ?? "", accept: true)
            case "reject": network.decide(request: command.requestID ?? "", accept: false)
            case "ping": network.ping()
            case "game": network.sendGame(command.payload ?? "", request: command.requestID ?? "")
            case "disconnect": network.disconnect()
            case "stop": network.stop()
            case "quit": network.changed = nil; network.stop(); exit(0)
            default: network.stop(message: "Unknown JVM helper command")
            }
        }
    }
    DispatchQueue.main.async { network.changed = nil; network.stop(); exit(0) } // Parent exit/pipe closure cannot orphan the helper.
}
dispatchMain()
