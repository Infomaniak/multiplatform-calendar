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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.13.0/MultiplatformCalendar.xcframework.zip",
            checksum: "24925057d3bca3b7f084dfb9064e6667b426d473c6851de6cd1d6b35426e7307"
        ),
    ]
)
