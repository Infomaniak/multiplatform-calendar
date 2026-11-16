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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/2.1.0/MultiplatformCalendar.xcframework.zip",
            checksum: "709b3a9795c5522e66bf888fc4f912ab165a69b302ae9d01218af00bff618ebe"
        ),
    ]
)
