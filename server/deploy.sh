#!/usr/bin/env bash
#
# 亚信卫星时刻 - 云服务一键部署/重启脚本
# 用法（在云主机上以 root 或具备 sudo 的用户执行）：
#   bash deploy.sh
#
# 该脚本做三件事：
#   1) 确保数据目录存在（图片/APK/命名记录的持久化目录，重启不丢）
#   2) 优雅停掉旧进程
#   3) 后台启动最新的 sat_share_server.py
#
set -euo pipefail

# ===== 可按实际环境修改 =====
APP_DIR="${APP_DIR:-/opt/sat-share/app}"          # 代码目录（含 server/sat_share_server.py）
DATA_DIR="${DATA_DIR:-/opt/sat-share/data}"        # 数据目录（图片/APK/命名记录）
PORT="${PORT:-8090}"
PUBLIC_BASE="${PUBLIC_BASE:-http://101.35.112.92:8090}"
LOG_FILE="${LOG_FILE:-/opt/sat-share/server.log}"
PY="${PY:-python3}"
# ============================

SERVER_PY="$APP_DIR/server/sat_share_server.py"

echo "[1/4] 检查代码与数据目录…"
if [ ! -f "$SERVER_PY" ]; then
  echo "  ✗ 找不到 $SERVER_PY，请确认 APP_DIR 是否正确（当前=$APP_DIR）"
  exit 1
fi
mkdir -p "$DATA_DIR"
mkdir -p "$(dirname "$LOG_FILE")"
echo "  ✓ 代码: $SERVER_PY"
echo "  ✓ 数据: $DATA_DIR"

echo "[2/4] 停止旧进程…"
pkill -f sat_share_server.py 2>/dev/null && echo "  ✓ 已停止旧进程" || echo "  · 无运行中的旧进程"
sleep 1

echo "[3/4] 启动新进程…"
PORT="$PORT" PUBLIC_BASE="$PUBLIC_BASE" DATA_DIR="$DATA_DIR" \
  nohup "$PY" "$SERVER_PY" > "$LOG_FILE" 2>&1 &
sleep 2

echo "[4/4] 健康检查…"
if curl -fs -m 5 "http://127.0.0.1:$PORT/health" >/dev/null; then
  echo "  ✓ 服务已启动"
  echo ""
  echo "  大屏:     $PUBLIC_BASE/screen"
  echo "  下载页:   $PUBLIC_BASE/app"
  echo "  命名数据: $PUBLIC_BASE/api/namings"
  echo "  日志:     $LOG_FILE"
else
  echo "  ✗ 健康检查失败，请查看日志: tail -n 100 $LOG_FILE"
  exit 1
fi
