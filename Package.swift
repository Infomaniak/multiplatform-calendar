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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.8.0/MultiplatformCalendar.xcframework.zip",
            checksum: "51feecc074679e717fa4aed40dc11f0f370c86b611099b4c2c20801c8cca021d"
        ),
    ]
)
