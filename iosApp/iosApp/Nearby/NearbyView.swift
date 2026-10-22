import SwiftUI
import NearbyCore
import UIKit

@MainActor
final class NearbyModel: ObservableObject {
    @Published var status = NearbyStatus()
    let network = NearbyNetwork(name: UIDevice.current.model)
    init() { network.changed = { [weak self] in self?.status = $0 } }
}

struct NearbyView: View {
    @StateObject private var model = NearbyModel()
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("NEARBY CONNECTION POC").font(.headline).foregroundStyle(.mint)
                Text("Wi-Fi on · both apps foreground\nNo shared router or hotspot required").font(.subheadline)
                Text("This iPhone: \(model.status.localName) · \(model.status.localID.prefix(8))")
                Text(model.status.message).foregroundStyle(.secondary)
                Text("Phase: \(model.status.phase)").font(.caption).foregroundStyle(.secondary)
                if model.status.phase == "browsing" {
                    if model.status.peers.isEmpty { Text("Looking for Macs… Open Nearby POC on your Mac and choose Advertise.") }
                    ForEach(model.status.peers, id: \.id) { peer in
                        Button { model.network.select(peer.id) } label: {
                            VStack(alignment: .leading) { Text(peer.name); Text(String(peer.id.prefix(8))).font(.caption) }
                        }.buttonStyle(.borderedProminent)
                    }
                }
                if !model.status.peerID.isEmpty {
                    Text("Mac: \(model.status.peerName) · \(model.status.peerID.prefix(8))")
                    Text("Compare on both screens: \(model.status.fingerprint)").foregroundStyle(.purple).font(.headline)
                }
                if model.status.phase == "connected" {
                    Button("Send ping") { model.network.ping() }.buttonStyle(.borderedProminent).disabled(model.status.pendingPing)
                }
                Text("Acknowledged pings: \(model.status.acknowledgements) · received: \(model.status.receivedPings)")
                HStack {
                    Button("Retry discovery") { model.network.browse() }
                    Button("Disconnect / stop") { model.network.stop() }
                }.buttonStyle(.bordered)
                Text("Tap one Mac, compare the fingerprint, and accept on that Mac. Every retry requires fresh selection and approval. Game transport is unchanged.").font(.footnote)
                Text("Names are self-reported. This plaintext POC only tests consent and ping/pong; it does not authenticate devices cryptographically.").font(.footnote).foregroundStyle(.secondary)
                Text("If discovery is denied, allow PORTAL in Settings > Privacy & Security > Local Network, then Retry. No automatic permission retry. Simulator results cannot prove router-free connectivity.").font(.footnote).foregroundStyle(.secondary)
            }.padding(20)
        }
        .onAppear { model.network.browse() }
        .onDisappear { model.network.stop() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { model.network.stop(message: "Paused · foreground this app and Retry discovery") }
        }
    }
}
