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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/0.15.0/MultiplatformCalendar.xcframework.zip",
            checksum: "bb6e7efd8796758d28698e544ca0c464028e45d33ebc31f85bcd90ceacef8110"
        ),
    ]
)
