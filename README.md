# 亚信卫星时刻 (AsiaInfo Satellite Moment)

未来全息风格的卫星观测应用。本仓库包含两部分：

```
satellite-app/
├── web/        # 炫酷 Web 原型（可立即在浏览器预览全部页面）
├── android/    # 原生 Android 工程骨架（Kotlin + Jetpack Compose）
└── shots/      # 各页面渲染截图
```

## 一、Web 原型（推荐先看）

纯前端实现，无需安装任何依赖，包含全部主要页面：

- **主启动页**：粒子星空 + **亚信logo** + 全息发光按钮
- **星座全景**：Three.js 全息地球 + **CelesTrak 实时 TLE 数据**（星网 GW 国网 ~186 + 垣信 G60 千帆 200 + 国电高科 天启 29，当前约 415 颗），可拖拽旋转/缩放/点击/分类筛选
- **AR 卫星**：调用后置摄像头 + 陀螺仪方位叠加卫星光点（桌面自动回退到模拟天空）
- **卫星详情/改名**：底部抽屉面板，可修改卫星名称（本地保存）
- **二维码分享**：生成邀请二维码
- **体验登记**：扫码后预留用户信息表单

### 运行方式

```bash
cd web
python3 -m http.server 8765
# 浏览器打开 http://localhost:8765
```

> AR 的相机/陀螺仪需 **HTTPS 或 localhost** 且在 **手机浏览器** 上体验最佳。
> 支持深链接：`#constellation` `#ar` `#share` `#form`

### 技术要点
- `js/data.js`：**接入 CelesTrak 真实 TLE 两行根数**（CORS 已开放），按 NORAD 去重 + 三级回退（在线 → 本地缓存6h → 内置壳层兜底）：
  - 国网 GW：`NAME=HULIANWANG`（互联网低轨 DIGUI + 技术试验 JISHU，~173）+ `NAME=GUOWANG`（测试星，13）
  - 千帆 G60：`NAME=QIANFAN`（200）
  - 天启 TQ：`NAME=TIANQI`（按 `TIANQI-` 前缀过滤，剔除同名天琴 TIANQIN，29）
- `js/vendor/satellite.min.js`：**SGP4/SDP4 星历传播库**（satellite.js v5）
- `js/earth.js`：用 **SGP4 按当前真实时间推算每颗卫星的 ECI 位置，逐帧实时更新**（5Hz 节流）；地球按 GMST 真实自转角对齐；惯性系绘制轨道环；`satrec.a` 直接映射为场景单位（1 单位 = 1 地球半径 = 6371km）。点击卫星可在详情面板看到**每秒刷新的实时星下点经纬度/高度/速度**
- `textures/`：**真实地球贴图**（昼面图 + 夜面城市灯光图 + 云层，Solar System Scope CC BY 4.0）。星座页采用**写实昼夜着色器**：白天显示真实海陆、夜面显示城市灯光，按太阳方向平滑过渡 + 漂移云层 + 蓝色大气边缘辉光
- `earth-preview.html`：**地球风格对比页**（5 种风格实时切换，可拖拽/缩放），用于挑选；当前正式页采用「② 写实昼夜」
- `js/earth.js`：Three.js 全息地球（点阵大陆 + 大气辉光 + 轨道环 + 卫星辉光精灵 + 射线拾取）
- `js/ar.js`：方位/俯仰角 → 屏幕坐标投影，相机回退处理
- `js/qrcode.js`：原型用拟真二维码（Android 版用 ZXing 生成真实可扫码）

## 二、Android 工程

`android/` 是标准 Gradle + Compose 工程，已实现：

- ✅ 全息风**主启动页**（`MainScreen.kt`，含动画 Logo + 星空背景 + 发光按钮）
- ✅ 导航骨架（`Navigation.kt`）
- ✅ **二维码分享页**（`ShareScreen.kt`，ZXing 真实二维码）
- ✅ **体验登记表单**（`UserFormScreen.kt`）
- 🚧 星座全景 / AR 卫星为占位页，后续阶段接入（详见开发文档阶段4、5）

### 运行方式
用 **Android Studio**（Hedgehog 及以上）打开 `android/` 目录，IDE 会自动下载 Gradle 与 SDK 并完成同步，然后运行到设备/模拟器。

> 本机当前未安装 JDK / Android SDK，故无法在命令行编译；Android Studio 会自动处理这些依赖。

### 关键依赖
- Jetpack Compose (BOM 2024.02)
- Navigation Compose
- Retrofit + OkHttp + kotlinx.serialization（CelesTrak 数据）
- ZXing（二维码）
- Play Services Location（GPS）

## 三、配套文档
项目根目录（`/Users/luyan/`）下的设计文档：
- 亚信卫星时刻-技术需求文档.md
- 亚信卫星时刻-技术架构方案.md
- 亚信卫星时刻-卫星数据源方案.md
- 亚信卫星时刻-开发阶段规划.md
