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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.4.0-202607300918/MultiplatformCalendar.xcframework.zip",
            checksum: "2e37cfa76d4859ed18943554e1298fc9c25210f147fe1f51f52bdd0f0f264139"
        ),
    ]
)
