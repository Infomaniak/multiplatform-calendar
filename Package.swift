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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-3.0.0-month-layout-202611261646-45860561390-1/MultiplatformCalendar.xcframework.zip",
            checksum: "739b6984b671742926d02ea974d20657a15d17b19aae1ab22335de8a342e18bd"
        ),
    ]
)
