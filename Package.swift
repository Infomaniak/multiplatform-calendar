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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.20.0/MultiplatformCalendar.xcframework.zip",
            checksum: "c605f8a89d649fd2f18c806a9138e780042af11f3ba5b560a2aac7ffe68ac9a7"
        ),
    ]
)
