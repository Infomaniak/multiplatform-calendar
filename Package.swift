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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.4.1/MultiplatformCalendar.xcframework.zip",
            checksum: "7a9f14639858d0a913224de2b8a4a72b864d2947b874c68aaa087f291ecdc8d3"
        ),
    ]
)
