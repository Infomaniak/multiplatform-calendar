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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-caldav-interceptor-202608210904-32465573347-1/MultiplatformCalendar.xcframework.zip",
            checksum: "baaff0e90a626416fc0cdd71e119a4c659c6972861fa5c2aea101852e3762d44"
        ),
    ]
)
