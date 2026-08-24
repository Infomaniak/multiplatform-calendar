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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.7.0-202608241320-32731235980-1/MultiplatformCalendar.xcframework.zip",
            checksum: "6b69f725995f7cb9cd56af15ba9f29cc73d6ea99fe028f2e73fc68ab948c9409"
        ),
    ]
)
