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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.9.0/MultiplatformCalendar.xcframework.zip",
            checksum: "205c4bceff5314ce71b044cd0c437f1a4de719efb168b575924eebbf54ec8d19"
        ),
    ]
)
