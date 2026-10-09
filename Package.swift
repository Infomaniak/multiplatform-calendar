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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.14.0-recurrence-description-202610091357-37939199227-1/MultiplatformCalendar.xcframework.zip",
            checksum: "9de5f47205c4861486a1bdc868b9eec898e7af34340756fb4f5a94e53e2c8803"
        ),
    ]
)
