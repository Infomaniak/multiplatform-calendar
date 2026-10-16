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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.15.0-rollfix-202610160914-38868269616-2/MultiplatformCalendar.xcframework.zip",
            checksum: "a812d067023285a244c35f72676c50fc89c545b00cfe7597eb1fab818102da32"
        ),
    ]
)
