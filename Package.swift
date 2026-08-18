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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.5.0/MultiplatformCalendar.xcframework.zip",
            checksum: "911c4314f3db71fc733eb9481aff25449d633f144dad3f3d0d1cc17b9cacca4f"
        ),
    ]
)
