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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.5.0-dyn-202608191242-32253678078-1/MultiplatformCalendar.xcframework.zip",
            checksum: "42653be05b5cdc9c9c29e991dda89d501cad88322f612129413886d73b1d9915"
        ),
    ]
)
