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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.21.0-202611031207-41631712588-1/MultiplatformCalendar.xcframework.zip",
            checksum: "81d14803b077af21221511251b21e1fa04e9087fd8bd6c799f41542834d0bc5e"
        ),
    ]
)
