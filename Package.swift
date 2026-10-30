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
            url: "https://github.com/Infomaniak/multiplatform-calendar/releases/download/ios-snapshot-0.20.0-api-environment-202610301400-41065958406-1/MultiplatformCalendar.xcframework.zip",
            checksum: "9ed30ef5dc24f2d327db6dd2587caee43fe26a4ce586585db24960b60ad423df"
        ),
    ]
)
