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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.9.1-datetime-202609181044-35335417161-1/MultiplatformCalendar.xcframework.zip",
            checksum: "aa124d6ee530e4670cce212f0334bb10a9b1cc0abb23ca7bf5f2be7216f68294"
        ),
    ]
)
