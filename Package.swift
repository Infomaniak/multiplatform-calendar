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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.3.0/MultiplatformCalendar.xcframework.zip",
            checksum: "97a9028e8c734f155c400e49a896e0a8efa63b517e9fb92cc3006539ef504d69"
        ),
    ]
)
