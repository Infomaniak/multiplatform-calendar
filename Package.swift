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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.2.0-date-conversion-202607201149/MultiplatformCalendar.xcframework.zip",
            checksum: "770d3063200274a9a1384870a16bff83c4b8d5c594056be1c9b49b704e200fd3"
        ),
    ]
)
