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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.6.0/MultiplatformCalendar.xcframework.zip",
            checksum: "fdfaf5e7da3df4b3e85e85b36cf00d46dd5a9b1f3261071eb1ad8b01945ba137"
        ),
    ]
)
