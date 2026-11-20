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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-2.2.0-color-list-202611201143-44728744829-1/MultiplatformCalendar.xcframework.zip",
            checksum: "39cc3093a61cddaa9d071d75871a2f6812557c658c1a56d057de8eb11e998f97"
        ),
    ]
)
