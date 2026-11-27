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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/3.0.1/MultiplatformCalendar.xcframework.zip",
            checksum: "7f10418176fe67c3c9964546def3fc2f1fd364d67915a8d7f1476d1eb4c894a2"
        ),
    ]
)
