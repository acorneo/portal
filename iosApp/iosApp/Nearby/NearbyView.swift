import SwiftUI
import Shared
import NearbyCore
import UIKit

@MainActor
final class NearbyModel: ObservableObject {
    @Published var status = NearbyStatus()
    @Published var roomVisible = false
    @Published var gameMessage = "Choose your nearby Mac"
    let network = NearbyNetwork(name: UIDevice.current.model)
    lazy var game = NearbyPhoneGame(clientId: network.identity.id)
    lazy var controller = MainViewControllerKt.MainViewController(game: game)
    private var connectionID = ""
    private var pairedMac = ""
    private var timer: Timer?

    init() {
        network.changed = { [weak self] status in self?.changed(status) }
        network.receivedGame = { [weak self] payload in
            guard let self else { return }
            _ = self.game.receive(payload: payload)
            self.refresh()
        }
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self else { return }
                if !self.connectionID.isEmpty {
                    for packet in self.game.outgoing() {
                        self.network.sendGame(packet, request: self.connectionID)
                    }
                }
                self.refresh()
            }
        }
    }
    private func changed(_ value: NearbyStatus) {
        status = value
        if value.phase == "connected", connectionID != value.requestID {
            guard pairedMac.isEmpty || pairedMac == value.peerID else {
                network.disconnect(message: "Choose your original Mac to resume this game")
                return
            }
            pairedMac = value.peerID
            connectionID = value.requestID
            network.sendGame(game.transportConnected(), request: connectionID)
        } else if value.phase != "connected", !connectionID.isEmpty {
            connectionID = ""
            game.transportDisconnected()
        }
        refresh()
    }
    private func refresh() {
        roomVisible = game.isRoomActive
        gameMessage = game.message
    }
    func start() { game.foreground(value: true); if status.phase == "stopped" { network.browse() }; refresh() }
    func retry() { game.foreground(value: true); network.browse() }
    func background() {
        game.foreground(value: false)
        network.stop(message: "Connection paused · return and choose your Mac to resume")
        refresh()
    }
    func select(_ id: String) { network.select(id) }
    var peers: [NearbyFoundPeer] { status.peers.filter { pairedMac.isEmpty || $0.id == pairedMac } }
}

struct NearbyView: View {
    @StateObject private var model = NearbyModel()
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("P O R T A L").font(.headline).foregroundStyle(.mint)
                Spacer()
                if model.status.phase == "connected" {
                    Text(model.status.peerName).font(.caption).lineLimit(1)
                    Button("Disconnect") { model.network.disconnect() }.font(.caption)
                }
            }.padding(.horizontal, 20).padding(.vertical, 10)
            if model.roomVisible {
                ComposeView(controller: model.controller).ignoresSafeArea(edges: .bottom)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        if model.status.phase == "connected" {
                            Text(model.gameMessage).font(.title3)
                            Text("Explore the maze on your Mac. The violet portal brings the sphere here.")
                        } else {
                            Text("PLAY WITH YOUR MAC").font(.headline)
                            Text(model.status.message).foregroundStyle(.secondary)
                            if model.status.phase == "browsing" {
                                if model.peers.isEmpty { Text("Looking for your Mac… Open PORTAL on your Mac, with Wi-Fi enabled on both devices.") }
                                ForEach(model.peers, id: \.id) { peer in
                                    Button { model.select(peer.id) } label: {
                                        VStack(alignment: .leading) {
                                            Text(peer.name)
                                            Text(String(peer.id.prefix(8))).font(.caption)
                                        }
                                    }.buttonStyle(.borderedProminent)
                                }
                            }
                            if !model.status.peerID.isEmpty {
                                Text("\(model.status.peerName) · \(model.status.peerID.prefix(8))")
                                Text("Compare on your Mac: \(model.status.fingerprint)").foregroundStyle(.purple).font(.headline)
                                Text("Accept this iPhone on your Mac to connect.")
                            }
                            if model.status.phase != "browsing" {
                                Button("Find my Mac") { model.retry() }.buttonStyle(.borderedProminent)
                            }
                            Text(model.gameMessage).foregroundStyle(.secondary)
                        }
                        Text("Keep both apps open and Wi-Fi enabled. A shared router or hotspot is not required. Your game stays in memory during reconnects; reopening either app starts a new session.").font(.footnote).foregroundStyle(.secondary)
                        Text("Allow Local Network access when asked. If denied, enable PORTAL in Settings › Privacy & Security › Local Network, then find your Mac again.").font(.footnote).foregroundStyle(.secondary)
                    }.padding(20)
                }
            }
        }
        .onAppear { model.start() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.start() }
            else { model.background() }
        }
    }
}
