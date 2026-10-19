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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.16.0/MultiplatformCalendar.xcframework.zip",
            checksum: "65b28967b92376c2f2438683f8bd8c2b3153795506f60b68e0d3bc7697f4b3a1"
        ),
    ]
)
