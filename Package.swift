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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.16.0-participation-status-202610210935-39576587847-2/MultiplatformCalendar.xcframework.zip",
            checksum: "f96bce57ba9a3162cd8f9bf64916c98ae14fcd05f0071a0170f6472ac67ed978"
        ),
    ]
)
