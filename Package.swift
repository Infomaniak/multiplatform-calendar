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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.10.0/MultiplatformCalendar.xcframework.zip",
            checksum: "9ce154ac236a4e81ddb06f6d7d1a548169f4868759f03b4c595f3ed6dbcf5da2"
        ),
    ]
)
