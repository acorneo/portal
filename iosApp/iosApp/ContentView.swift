import Shared
import SwiftUI
import UIKit

struct ComposeView: UIViewControllerRepresentable {
    let controller: UIViewController
    func makeUIViewController(context: Self.Context) -> UIViewController { controller }
    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    var body: some View { NearbyView().preferredColorScheme(.dark) }
}
