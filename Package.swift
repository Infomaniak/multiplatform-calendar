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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.14.0/MultiplatformCalendar.xcframework.zip",
            checksum: "b6679bea8c3f7a5f864018dadb21179557e45324d7e359f7004dfc26541bf1b2"
        ),
    ]
)
