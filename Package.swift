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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.12.0/MultiplatformCalendar.xcframework.zip",
            checksum: "f7e3eee170da6080bf0351de6bc4e70d4a24ec295cf8799873dbf43109e24f5e"
        ),
    ]
)
