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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.5.0-202608111212/MultiplatformCalendar.xcframework.zip",
            checksum: "c21610d19c278f61831ea68dd4a0a758d37d3f3dffa759d6b45f69fea02850bd"
        ),
    ]
)
