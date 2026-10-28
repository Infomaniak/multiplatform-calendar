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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.19.0/MultiplatformCalendar.xcframework.zip",
            checksum: "486728b8710de680eef4e8e06adb08f6b388e1bd25574c5e7e3875bdc4ab1df6"
        ),
    ]
)
