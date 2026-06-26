// swift-tools-version:5.10
import PackageDescription

let package = Package(
    name: "KmpCalendar",
    platforms: [
        .iOS(.v14),
        .macOS(.v12),
    ],
    products: [
        .library(name: "KmpCalendar", targets: ["KmpCalendar"])
    ],
    targets: [
        .binaryTarget(
            name: "KmpCalendar",
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.0.1/KmpCalendar.xcframework.zip",
            checksum: "0000000000000000000000000000000000000000000000000000000000000000"
        ),
    ]
)
