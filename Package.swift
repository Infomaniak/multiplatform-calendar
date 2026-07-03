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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.0.2/MultiplatformCalendar.xcframework.zip",
            checksum: "f69af879289469241cd474cfa4064ac7816ee6e8e050567b8c0b59dfce65383c"
        ),
    ]
)
