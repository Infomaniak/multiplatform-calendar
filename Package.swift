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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-2.3.1-group-slices-202611231352-45186833667-1/MultiplatformCalendar.xcframework.zip",
            checksum: "e02ce9c6aae45b606807cd4ce637fff2096ad3f1632674959ce475a95a5ec977"
        ),
    ]
)
