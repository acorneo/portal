// swift-tools-version: 5.9
import PackageDescription
import Foundation

let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().path
let package = Package(
    name: "PortalNearby",
    platforms: [.macOS(.v13), .iOS("18.2")],
    products: [.library(name: "NearbyCore", targets: ["NearbyCore"]), .executable(name: "PortalNearbyHelper", targets: ["PortalNearbyHelper"])],
    targets: [
        .target(name: "NearbyCore"),
        .executableTarget(name: "PortalNearbyHelper", dependencies: ["NearbyCore"], linkerSettings: [
            .unsafeFlags(["-Xlinker", "-sectcreate", "-Xlinker", "__TEXT", "-Xlinker", "__info_plist", "-Xlinker", root + "/HelperInfo.plist"], .when(platforms: [.macOS]))
        ]),
        .testTarget(name: "NearbyCoreTests", dependencies: ["NearbyCore"])
    ]
)
