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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.11.1-optim-202609301309-36715380352-2/MultiplatformCalendar.xcframework.zip",
            checksum: "33d30a0e5e435de5ce3a8041c3e464b895a2d04c5d08516fe2c32f56da2a8462"
        ),
    ]
)
