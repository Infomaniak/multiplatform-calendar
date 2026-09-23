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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.10.1/MultiplatformCalendar.xcframework.zip",
            checksum: "b0c02ea97e5e5aca21024d8474a7eda394f2ebdd2eefcdaee1dde2595b75baf6"
        ),
    ]
)
