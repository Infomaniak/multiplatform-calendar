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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.12.0-202609241318-36003303556-1/MultiplatformCalendar.xcframework.zip",
            checksum: "aa5ac21da9eed884dbce62ee3cced6249786711267a473ae123b5e77079b35f0"
        ),
    ]
)
