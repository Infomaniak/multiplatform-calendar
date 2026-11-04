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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.20.0-fix-duplicate-calendar-privileges-202611041205-41823324457-1/MultiplatformCalendar.xcframework.zip",
            checksum: "889da9ab77ca2aa30a47d1e34c34bfb28a1f2538bb5f5ed212dbeb9a933c20f8"
        ),
    ]
)
