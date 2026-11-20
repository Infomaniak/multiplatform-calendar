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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/2.3.0/MultiplatformCalendar.xcframework.zip",
            checksum: "f668f422233648ae9c70b6ce82de4b0346e6b0b891474a45ac11c7dc803b6766"
        ),
    ]
)
