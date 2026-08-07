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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.4.0/MultiplatformCalendar.xcframework.zip",
            checksum: "61a98a7a192af9d1003b4cd3e888d886e67650d2fdb0b64ccf2ff44e60b5852f"
        ),
    ]
)
