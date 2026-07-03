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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.1.0/MultiplatformCalendar.xcframework.zip",
            checksum: "9458cd66c240919897e919f076114c0c25c0ebee7f9c0686e2c6dd12f8c31c6b"
        ),
    ]
)
