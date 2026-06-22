#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
亚信卫星时刻 — 分享图片云服务（标准库实现，无需 pip 依赖）

接口：
  POST /upload[?id=xxx]   上传 PNG（请求体为原始图片字节，Content-Type: image/png）
                          返回 JSON: {"id","img","page"}
  GET  /i/<id>.png        获取图片
  GET  /p/<id>            H5 页面：展示图片 + 下载按钮（用于二维码扫码后查看/下载/微信分享）
  GET  /health            健康检查
"""
import json
import os
import re
import sys
import socketserver
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime
import random
import string

PORT = int(os.environ.get("PORT", "8090"))
PUBLIC_BASE = os.environ.get("PUBLIC_BASE", "http://101.35.112.92:%d" % PORT)
DATA_DIR = os.environ.get("DATA_DIR", "/opt/sat-share/data")
MAX_BYTES = 20 * 1024 * 1024  # 20MB

os.makedirs(DATA_DIR, exist_ok=True)

ID_RE = re.compile(r"^[A-Za-z0-9_-]{4,40}$")


def gen_id():
    ts = datetime.now().strftime("%Y%m%d%H%M%S")
    rnd = "".join(random.choices(string.ascii_lowercase + string.digits, k=6))
    return "sat%s%s" % (ts, rnd)


PAGE_HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>亚信卫星时刻 · 我的卫星</title>
<style>
  body{{margin:0;background:#03040a;color:#eaf6ff;font-family:-apple-system,"PingFang SC","Noto Sans SC",sans-serif;text-align:center;}}
  .wrap{{max-width:560px;margin:0 auto;padding:18px;}}
  h1{{font-size:18px;letter-spacing:2px;margin:14px 0;color:#2de2ff;}}
  img{{width:100%;border-radius:14px;box-shadow:0 10px 40px rgba(0,0,0,.6);}}
  .btn{{display:inline-block;margin:18px 8px 6px;padding:13px 26px;border-radius:26px;background:#2de2ff;color:#03040a;font-weight:700;text-decoration:none;font-size:15px;}}
  .tip{{color:#8aa0c0;font-size:13px;line-height:1.7;margin-top:10px;padding:0 10px;}}
  .foot{{color:#54657f;font-size:11px;margin:22px 0;}}
</style>
</head>
<body>
  <div class="wrap">
    <h1>亚信卫星时刻</h1>
    <img id="pic" src="{img}" alt="卫星分享图"/>
    <div>
      <a class="btn" href="{img}" download="asiainfo-satellite.png">下载图片</a>
    </div>
    <div class="tip">长按图片可保存到相册；点击右上角「···」可分享到微信朋友圈。</div>
    <div class="foot">ASIAINFO · SATELLITE MOMENT</div>
  </div>
</body>
</html>"""


class Handler(BaseHTTPRequestHandler):
    server_version = "SatShare/1.0"

    def _send(self, code, body, ctype="application/json; charset=utf-8", extra=None):
        if isinstance(body, str):
            body = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        if extra:
            for k, v in extra.items():
                self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        if path == "/health":
            return self._send(200, json.dumps({"ok": True}))
        m = re.match(r"^/i/([A-Za-z0-9_-]+)\.png$", path)
        if m:
            return self._serve_image(m.group(1))
        m = re.match(r"^/p/([A-Za-z0-9_-]+)$", path)
        if m:
            sid = m.group(1)
            fp = os.path.join(DATA_DIR, sid + ".png")
            if not os.path.exists(fp):
                return self._send(404, "not found", "text/plain; charset=utf-8")
            img = "%s/i/%s.png" % (PUBLIC_BASE, sid)
            return self._send(200, PAGE_HTML.format(img=img), "text/html; charset=utf-8")
        return self._send(404, json.dumps({"error": "not found"}))

    def _serve_image(self, sid):
        fp = os.path.join(DATA_DIR, sid + ".png")
        if not os.path.exists(fp):
            return self._send(404, "not found", "text/plain; charset=utf-8")
        with open(fp, "rb") as f:
            data = f.read()
        self._send(200, data, "image/png", extra={"Cache-Control": "public, max-age=86400"})

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path != "/upload":
            return self._send(404, json.dumps({"error": "not found"}))
        qs = parse_qs(parsed.query)
        sid = (qs.get("id", [None])[0])
        if sid and not ID_RE.match(sid):
            return self._send(400, json.dumps({"error": "bad id"}))
        if not sid:
            sid = gen_id()
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BYTES:
            return self._send(400, json.dumps({"error": "bad length"}))
        data = self.rfile.read(length)
        fp = os.path.join(DATA_DIR, sid + ".png")
        with open(fp, "wb") as f:
            f.write(data)
        resp = {
            "id": sid,
            "img": "%s/i/%s.png" % (PUBLIC_BASE, sid),
            "page": "%s/p/%s" % (PUBLIC_BASE, sid),
        }
        return self._send(200, json.dumps(resp))

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


class ThreadingHTTPServer(socketserver.ThreadingMixIn, HTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    sys.stderr.write("sat-share listening on 0.0.0.0:%d, base=%s, data=%s\n" % (PORT, PUBLIC_BASE, DATA_DIR))
    httpd.serve_forever()
