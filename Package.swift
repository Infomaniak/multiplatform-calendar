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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/3.0.0/MultiplatformCalendar.xcframework.zip",
            checksum: "3edbbfb465cc2d90726e7af3338c9b7ffe2008ccbb5ef2c9d45a9d36904a6e5e"
        ),
    ]
)
