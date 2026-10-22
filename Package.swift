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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.17.0/MultiplatformCalendar.xcframework.zip",
            checksum: "b5c423037414aeaae40a2748ac684acdb6b76a84107db3d174e5056195caf6f7"
        ),
    ]
)
