# iOS Xcode项目设置指南

## 📱 项目结构

```
ios/AsiainfoSatellite/
├── AsiainfoSatellite/
│   ├── AsiainfoSatelliteApp.swift      # 应用入口
│   ├── MainView.swift                  # 主视图（TabView）
│   ├── Info.plist                      # 应用配置
│   ├── Models/
│   │   └── Satellite.swift            # 数据模型
│   ├── Services/
│   │   ├── SGP4.swift                 # SGP4算法
│   │   ├── SatelliteService.swift     # 卫星数据服务
│   │   ├── LocationService.swift      # 位置服务
│   │   └── OrientationService.swift   # 传感器服务
│   └── Views/
│       ├── ConstellationView.swift     # 星座全景
│       └── ARView.swift               # AR卫星
└── AsiainfoSatellite.xcodeproj/       # Xcode项目文件
```

## 🛠️ Xcode项目创建步骤

### 方法1：使用Xcode创建新项目（推荐）

1. **打开Xcode** → File → New → Project
2. **选择模板**：iOS → App
3. **项目配置**：
   - Product Name: `AsiainfoSatellite`
   - Team: 选择你的开发团队
   - Organization Identifier: `com.asiainfo`
   - Interface: SwiftUI
   - Language: Swift
   - Storage: None
4. **保存位置**：选择 `ios/AsiainfoSatellite/` 目录

### 方法2：手动配置现有文件

1. **复制文件**：将现有Swift文件复制到Xcode项目中
2. **添加到Xcode**：
   - 右键项目 → Add Files to "AsiainfoSatellite"
   - 选择所有Swift文件
   - 确保勾选"Copy items if needed"
3. **配置Info.plist**：
   - 将提供的Info.plist内容复制到项目
   - 或在Xcode中手动添加权限描述

## 🔧 必要的配置

### 1. 权限配置
在Info.plist中添加以下权限描述：
- `NSCameraUsageDescription`: 相机权限
- `NSLocationWhenInUseUsageDescription`: 位置权限
- `NSMotionUsageDescription`: 运动传感器权限

### 2. 依赖管理
项目使用系统框架，无需额外依赖：
- `Foundation`
- `SwiftUI`
- `CoreLocation`
- `CoreMotion`
- `AVFoundation`

### 3. 构建设置
在Xcode中配置：
- Deployment Target: iOS 15.0+
- Swift Language Version: Swift 5.0
- Bundle Identifier: com.asiainfo.satellite

## 🚀 构建和运行

### 在Xcode中构建
1. 选择目标设备或模拟器
2. 点击Run按钮（⌘R）
3. 等待构建完成

### 命令行构建
```bash
cd ios/AsiainfoSatellite
xcodebuild -project AsiainfoSatellite.xcodeproj \
  -scheme AsiainfoSatellite \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 15'
```

## 📦 功能实现状态

### ✅ 已实现功能
- **数据层**：卫星模型、SGP4算法、服务层
- **星座全景**：三种渲染模式、卫星显示、交互
- **AR卫星**：相机预览、传感器、卫星叠加
- **UI/UX**：TabView导航、权限处理、错误处理

### ⚠️ 注意事项
1. **SGP4算法**：当前为简化实现，生产环境建议使用成熟库
2. **AR功能**：使用AVFoundation相机预览，可升级到ARKit
3. **权限处理**：需要在真机上测试权限请求流程
4. **网络请求**：卫星数据从Celestrak获取，需网络连接

## 🎯 测试建议

### 模拟器测试
- UI布局和交互
- 数据加载和显示
- 权限处理流程

### 真机测试
- 相机功能
- 传感器数据
- GPS定位
- 实际卫星观测

## 🐛 常见问题

### 构建错误
1. **Swift版本不匹配**：检查Swift Language Version设置
2. **框架缺失**：确保所有必需框架已添加
3. **权限配置**：检查Info.plist中的权限描述

### 运行时错误
1. **权限被拒绝**：检查系统设置中的应用权限
2. **网络请求失败**：检查网络连接和URL配置
3. **传感器数据异常**：在真机上测试传感器功能

## 📝 下一步优化

1. **SGP4算法**：集成成熟的SGP4库提高精度
2. **ARKit集成**：使用ARKit实现真正的AR体验
3. **数据缓存**：添加本地缓存减少网络请求
4. **错误处理**：完善错误处理和用户提示
5. **性能优化**：优化渲染和计算性能

## 🎨 UI定制

### 主题颜色
```swift
let primaryColor = Color(red: 0.2, green: 0.9, blue: 1.0) // 青色
let secondaryColor = Color(red: 1.0, green: 0.3, blue: 0.8) // 粉色
let backgroundColor = Color(red: 0.04, green: 0.07, blue: 0.2) // 深蓝色
```

### 图标和资源
- 将Android图标转换为iOS格式
- 添加AppIcon到Assets.xcassets
- 添加启动屏幕

## 🔄 持续集成

### GitHub Actions
项目已配置iOS构建工作流，需要：
1. 配置Apple Developer账号
2. 设置证书和Provisioning Profile
3. 配置TestFlight分发

---

**注意**：当前代码提供了完整的iOS应用框架，需要在Xcode中创建项目并导入这些文件才能构建和运行。