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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.2.0/MultiplatformCalendar.xcframework.zip",
            checksum: "e42180d056b8789c1c43a6f6fac332a9d0f94e1e2eae7cf16d9d7965579026fa"
        ),
    ]
)
