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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.18.0/MultiplatformCalendar.xcframework.zip",
            checksum: "6acb599ab4f06113c5bd9576e97c1cfcf68ebe9a8dde8a111a6110521392557e"
        ),
    ]
)
