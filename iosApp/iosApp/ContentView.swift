import Shared
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    let controller: UIViewController
    func makeUIViewController(context: Self.Context) -> UIViewController {
        controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

@MainActor
final class GameController: ObservableObject {
    lazy var controller = MainViewControllerKt.MainViewController()
}

struct ContentView: View {
    @StateObject private var game = GameController()
    @State private var nearby = false
    var body: some View {
        VStack(spacing: 0) {
            Picker("PORTAL demo mode", selection: $nearby) {
                Text("Game / LAN portal").tag(false)
                Text("Nearby POC").tag(true)
            }.pickerStyle(.segmented).padding(10)
            if nearby { NearbyView() }
            else { ComposeView(controller: game.controller).ignoresSafeArea(edges: .bottom) }
        }.preferredColorScheme(.dark)
    }
}
