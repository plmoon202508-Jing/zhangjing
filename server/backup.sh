#!/usr/bin/env bash
#
# 亚信卫星时刻 - 云服务数据备份脚本
# 备份内容：DATA_DIR 下的全部图片(.png)、安装包(.apk)、命名记录(namings.jsonl)
#
# 用法：
#   bash backup.sh                 # 备份到 /opt/sat-share/backups/
#   BACKUP_DIR=/data/bak bash backup.sh
#
set -euo pipefail

DATA_DIR="${DATA_DIR:-/opt/sat-share/data}"
BACKUP_DIR="${BACKUP_DIR:-/opt/sat-share/backups}"
KEEP="${KEEP:-14}"   # 保留最近多少份备份

ts="$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"
out="$BACKUP_DIR/satshare_data_$ts.tar.gz"

if [ ! -d "$DATA_DIR" ]; then
  echo "✗ 数据目录不存在: $DATA_DIR"
  exit 1
fi

echo "打包 $DATA_DIR → $out"
tar -czf "$out" -C "$(dirname "$DATA_DIR")" "$(basename "$DATA_DIR")"
echo "✓ 备份完成: $out  ($(du -h "$out" | cut -f1))"

# 清理旧备份，仅保留最近 KEEP 份
ls -1t "$BACKUP_DIR"/satshare_data_*.tar.gz 2>/dev/null | tail -n +"$((KEEP+1))" | xargs -r rm -f
echo "✓ 仅保留最近 $KEEP 份备份"

# 建议加入 crontab 每日自动备份（示例）：
#   0 3 * * * bash /opt/sat-share/app/server/backup.sh >> /opt/sat-share/backup.log 2>&1
