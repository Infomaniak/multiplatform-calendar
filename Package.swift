// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "MultiplatformCalendar",
    platforms: [
        .iOS(.v14),
        .macOS(.v12),
    ],
    products: [
        .library(name: "MultiplatformCalendar", targets: ["MultiplatformCalendar"])
    ],
    targets: [
        .binaryTarget(
            name: "MultiplatformCalendar",
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.11.0/MultiplatformCalendar.xcframework.zip",
            checksum: "87894f875e607ee4e1e7d457881cffd50df9847ee6e5d4a7448e6f0509999a95"
        ),
    ]
)
