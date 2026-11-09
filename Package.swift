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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-1.0.0-room-202611090805-42581488586-2/MultiplatformCalendar.xcframework.zip",
            checksum: "a91127e489f4aeac8de86ff3afc909b4cc7a2a4560b72c0368071b830e7450cf"
        ),
    ]
)
