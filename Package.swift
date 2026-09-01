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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.7.0-agp9-202609010734-33481961973-1/MultiplatformCalendar.xcframework.zip",
            checksum: "439b51ae2f6be9aa1f454f15d63e7d1e49af519b214c36d6e0fdf52e72efb64d"
        ),
    ]
)
