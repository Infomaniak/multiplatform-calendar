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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/2.2.0/MultiplatformCalendar.xcframework.zip",
            checksum: "6ba535c0a1f3293e667c77ddf7757bc16f5d5c29bedbfa42d7be0a72965cb4a6"
        ),
    ]
)
