// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "AsiainfoSatellite",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "AsiainfoSatellite",
            targets: ["AsiainfoSatellite"]
        ),
    ],
    dependencies: [
        // 添加Swift包依赖
        // .package(url: "https://github.com/example/package.git", from: "1.0.0"),
    ],
    targets: [
        .target(
            name: "AsiainfoSatellite",
            dependencies: []
        ),
    ]
)