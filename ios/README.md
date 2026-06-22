# 亚信卫星时刻 iOS 版本

## 项目结构

```
ios/
├── AsiainfoSatellite/          # 主应用代码
│   ├── AsiainfoSatelliteApp.swift  # 应用入口
│   ├── MainView.swift              # 主视图
│   ├── Info.plist                  # 应用配置
│   └── Assets/                     # 资源文件
├── Package.swift                   # Swift Package Manager 配置
└── README.md                       # 说明文档
```

## 功能模块

### 1. 首页 (HomeView)
- 显示应用Logo和品牌信息
- 提供星座全景和AR卫星功能入口
- 显示APP下载二维码

### 2. 星座全景 (ConstellationView)
- 原生2D旋转地球渲染
- 真实卫星位置投影
- 支持点阵/真实地球模式切换
- 卫星轨道显示
- 点击卫星查看详情

### 3. AR卫星 (ARView)
- 相机实景叠加
- 陀螺仪方向检测
- GPS定位
- 实时卫星方位计算
- 体验人姓名输入
- 云端分享功能

## 技术栈

- **UI框架**: SwiftUI
- **最低版本**: iOS 15.0+
- **语言**: Swift 5.9+
- **包管理**: Swift Package Manager

## 权限要求

- 相机权限 (NSCameraUsageDescription)
- 位置权限 (NSLocationWhenInUseUsageDescription)

## 构建说明

### 使用 Xcode
1. 打开 Xcode
2. 创建新的 iOS App 项目
3. 将代码文件复制到项目中
4. 配置 Info.plist 和资源文件
5. 构建并运行

### 使用命令行
```bash
cd ios
xcodebuild -project AsiainfoSatellite.xcodeproj -scheme AsiainfoSatellite -configuration Release
```

## 待实现功能

- [ ] 完整的星座全景功能
- [ ] AR卫星核心功能
- [ ] 卫星数据获取和SGP4计算
- [ ] 云端分享功能
- [ ] 推送通知
- [ ] 数据持久化

## 注意事项

1. iOS版本需要移植Android的SGP4算法
2. AR功能需要使用ARKit或SceneKit
3. 相机和传感器权限需要在Info.plist中正确配置
4. 需要适配不同屏幕尺寸