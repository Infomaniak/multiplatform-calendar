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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.18.0-fix-event-response-202610271023-40486896631-1/MultiplatformCalendar.xcframework.zip",
            checksum: "eca0553544f220a0031aa5e03c0d082be5f3081f43a36859a867fef21bab0bf6"
        ),
    ]
)
