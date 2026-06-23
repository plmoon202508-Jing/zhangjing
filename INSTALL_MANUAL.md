# 亚信卫星时刻 — 安装与备份手册

本手册覆盖两个交付程序：

| 编号 | 程序 | 说明 | 代码位置 |
|------|------|------|---------|
| ① | **Android APP** | 亚信卫星时刻客户端（星座全景 / AR卫星 / 分享 / 命名） | `android/` |
| ② | **云端服务** | 分享图存储 + 二维码下载页 + APK下载页 + 星座命名大屏 | `server/` |

> 仓库本身即代码备份（GitHub）。运行期产生的图片/APK/命名数据需另行备份，见第 4 节。

---

## 0. 系统组成与数据流

```
   ┌─────────────── Android APP（①）────────────────┐
   │  星座全景：用户点击卫星 → 命名                    │
   │     └── POST /name ───────────────┐             │
   │  AR卫星：捕捉卫星 → 生成分享图       │             │
   │     ├── 本地用 zxing 生成二维码      │             │
   │     └── POST /upload 上传PNG ──────┤             │
   └────────────────────────────────────┼────────────┘
                                         ▼
   ┌─────────────── 云端服务（②）sat_share_server.py ──────────────┐
   │  存储:  DATA_DIR/<id>.png   DATA_DIR/<id>.apk                  │
   │         DATA_DIR/namings.jsonl （命名记录，持久化）             │
   │  页面:  /p/<id> 图片查看下载页    /app APK下载页                │
   │         /screen 星座大屏（浅色, 实时标注用户命名）              │
   │  接口:  /upload /upload-apk /name /api/namings /health         │
   └───────────────────────────────────────────────────────────────┘
                                         ▲
                          大屏(浏览器) 每3秒轮询 /api/namings
```

二维码生成有两处：
- **APP 端（zxing）**：分享图右下角二维码、分享完成弹窗二维码，均由 `com.google.zxing:core:3.5.3` 在本地生成，内容为云端返回的 `/p/<id>` 下载页地址。
- **云端**：`/app` 下载页里的"扫码下载APP"二维码当前指向 `/app` 自身（H5 页面内 `<img>`）。

---

## 1. 云端服务（②）安装

### 1.1 环境要求
- Linux 服务器（已部署在腾讯云 `101.35.112.92`）
- Python 3.6+（**纯标准库，无需 pip 依赖**）
- 开放入站端口 **8090**（安全组 + 防火墙）

### 1.2 目录约定
| 用途 | 路径（可改） | 环境变量 |
|------|-------------|---------|
| 代码 | `/opt/sat-share/app`（含 `server/sat_share_server.py`） | — |
| 数据 | `/opt/sat-share/data`（图片/APK/命名记录） | `DATA_DIR` |
| 端口 | `8090` | `PORT` |
| 外网地址 | `http://101.35.112.92:8090` | `PUBLIC_BASE` |

### 1.3 部署步骤

```bash
# 1) 获取代码（首次）
sudo mkdir -p /opt/sat-share
sudo git clone <仓库地址> /opt/sat-share/app
# 已有则更新：
cd /opt/sat-share/app && sudo git pull

# 2) 一键启动/重启（推荐）
cd /opt/sat-share/app/server
sudo APP_DIR=/opt/sat-share/app DATA_DIR=/opt/sat-share/data \
     PORT=8090 PUBLIC_BASE=http://101.35.112.92:8090 \
     bash deploy.sh
```

### 1.4 设为开机自启（systemd，推荐生产使用）

```bash
# 1) 安装服务单元（先按需修改 WorkingDirectory / Environment 路径）
sudo cp /opt/sat-share/app/server/sat-share.service /etc/systemd/system/sat-share.service

# 2) 启用并启动
sudo systemctl daemon-reload
sudo systemctl enable sat-share
sudo systemctl restart sat-share

# 3) 查看状态与日志
sudo systemctl status sat-share
sudo journalctl -u sat-share -f
```

### 1.5 验证

```bash
curl http://101.35.112.92:8090/health        # {"ok": true}
# 浏览器打开大屏：
#   http://101.35.112.92:8090/screen
```

### 1.6 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/screen` | **星座大屏**（浅色，旋转地球，地球上实时标注用户命名） |
| GET | `/api/namings?limit=120` | 返回最近命名记录 JSON |
| POST | `/name` | 命名上报（JSON: customName,user,satName,satId,constellation,lat,lon,alt）|
| POST | `/upload[?id=]` | 上传分享PNG，返回 `{id,img,page}` |
| GET | `/i/<id>.png` | 取分享图片 |
| GET | `/p/<id>` | 图片查看/下载 H5 页 |
| POST | `/upload-apk[?id=]` | 上传APK，返回 `{id,apk,page}` |
| GET | `/a/<id>.apk` | 取APK |
| GET | `/app` | APP 下载页（取最新APK） |

---

## 2. Android APP（①）构建安装

### 2.1 关键配置（`android/app/build.gradle.kts`）
- `applicationId = com.asiainfo.satellite`
- `versionName = 1.0.0`，`versionCode = 1`
- `minSdk = 24`（Android 7.0），`targetSdk = 34`，`compileSdk = 34`
- 二维码依赖：`com.google.zxing:core:3.5.3`
- 云服务地址：`CloudUploader.BASE = http://101.35.112.92:8090`（如换服务器在此修改）

### 2.2 方式 A：GitHub Actions 云构建（推荐，无需本地环境）
推送到 `main` 后，Actions 自动构建，在 Actions 运行页面的 **Artifacts** 下载 APK。
工作流文件：`.github/workflows/build-android.yml`

### 2.3 方式 B：本地构建
```bash
cd android
./gradlew assembleRelease     # 产物: app/build/outputs/apk/release/
# 或调试包:
./gradlew assembleDebug       # 产物: app/build/outputs/apk/debug/
```
需要：JDK 17、Android SDK（API 34）。

### 2.4 发布到下载页
把构建好的 APK 上传到云端，用户即可在 `/app` 页扫码下载：
```bash
curl -X POST --data-binary @app-release.apk \
  -H "Content-Type: application/vnd.android.package-archive" \
  http://101.35.112.92:8090/upload-apk
```

---

## 3. 服务器是否需要重启？

**需要。** 每次更新 `server/sat_share_server.py` 后，必须重启进程才能生效：
- 用 deploy.sh：再次执行 `bash deploy.sh` 即可（会自动停旧进程再启动）
- 用 systemd：`sudo systemctl restart sat-share`

> 注意：`/screen`、`/api/namings`、`/name` 为新增接口，老进程没有，重启前访问会返回 404。

---

## 4. 备份方案

### 4.1 代码备份
代码以 Git 仓库为准（GitHub 即异地备份）。每次改动通过 `git commit` + `git push` 留存历史，可随时回滚。

### 4.2 运行数据备份（重要）
图片/APK/命名记录都在 `DATA_DIR`，**不在 Git 里**，需单独备份：

```bash
# 手动备份一次（产物: /opt/sat-share/backups/satshare_data_<时间>.tar.gz）
sudo DATA_DIR=/opt/sat-share/data bash /opt/sat-share/app/server/backup.sh

# 每日 03:00 自动备份（加入 crontab）
sudo crontab -e
# 追加一行：
0 3 * * * bash /opt/sat-share/app/server/backup.sh >> /opt/sat-share/backup.log 2>&1
```

### 4.3 恢复数据
```bash
sudo systemctl stop sat-share          # 或 pkill -f sat_share_server.py
sudo tar -xzf /opt/sat-share/backups/satshare_data_<时间>.tar.gz -C /opt/sat-share/
sudo systemctl start sat-share
```

### 4.4 备份清单速查
| 资产 | 位置 | 备份方式 |
|------|------|---------|
| ① APP 源码 | `android/` | Git |
| ② 服务端源码 | `server/sat_share_server.py` | Git |
| 部署脚本/服务单元 | `server/deploy.sh` `server/backup.sh` `server/sat-share.service` | Git |
| 分享图片 | `DATA_DIR/*.png` | backup.sh |
| APK 安装包 | `DATA_DIR/*.apk` | backup.sh |
| 命名记录 | `DATA_DIR/namings.jsonl` | backup.sh |

---

## 5. 常见问题

- **/screen 打不开（404）**：服务器跑的是旧代码，按第 3 节重启。
- **大屏没有命名显示**：确认 APP 端能联网、命名时 `POST /name` 成功；`curl http://101.35.112.92:8090/api/namings` 应能看到记录。
- **/app 返回 404**：还没上传过 APK，按 2.4 上传一个即可。
- **换服务器/域名**：改 `PUBLIC_BASE`（服务端）与 `CloudUploader.BASE`（APP 端）两处，重新部署与构建。
