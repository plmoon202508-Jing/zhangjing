#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
亚信卫星时刻 — 分享图片云服务（标准库实现，无需 pip 依赖）

接口：
  POST /upload[?id=xxx]   上传 PNG（请求体为原始图片字节，Content-Type: image/png）
                          返回 JSON: {"id","img","page"}
  POST /upload-apk[?id=xxx] 上传 APK（请求体为原始APK字节，Content-Type: application/vnd.android.package-archive）
                          返回 JSON: {"id","apk","page"}
  GET  /i/<id>.png        获取图片
  GET  /a/<id>.apk        获取APK
  GET  /p/<id>            H5 页面：展示图片 + 下载按钮（用于二维码扫码后查看/下载/微信分享）
  GET  /app               APP下载页面：提供APK下载
  POST /name              星座全景命名上报（JSON: customName,user,satName,satId,constellation,lat,lon,alt）
  GET  /api/namings[?limit=120]  返回最近命名记录 JSON: {"items":[...]}
  GET  /screen            云端大屏：浅色星座全景 + 地球上实时标注用户命名
  GET  /health            健康检查
"""
import json
import os
import re
import sys
import socketserver
import threading
from collections import deque
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime
import random
import string

PORT = int(os.environ.get("PORT", "8090"))
PUBLIC_BASE = os.environ.get("PUBLIC_BASE", "http://101.35.112.92:%d" % PORT)
DATA_DIR = os.environ.get("DATA_DIR", "/opt/sat-share/data")
MAX_BYTES = 20 * 1024 * 1024  # 20MB
APK_MAX_BYTES = 100 * 1024 * 1024  # 100MB for APK
NAME_MAX_BYTES = 8 * 1024  # 8KB，命名事件 JSON 上限
NAMINGS_KEEP = 300  # 大屏保留的最近命名条数

os.makedirs(DATA_DIR, exist_ok=True)

ID_RE = re.compile(r"^[A-Za-z0-9_-]{4,40}$")

# ===== 星座命名同步（app 星座全景 → 云端大屏） =====
NAMINGS_FILE = os.path.join(DATA_DIR, "namings.jsonl")
_namings_lock = threading.Lock()
_namings = deque(maxlen=NAMINGS_KEEP)


def _load_namings():
    """启动时从磁盘恢复最近的命名记录。"""
    if not os.path.exists(NAMINGS_FILE):
        return
    try:
        with open(NAMINGS_FILE, "r", encoding="utf-8") as f:
            lines = f.readlines()[-NAMINGS_KEEP:]
        for ln in lines:
            ln = ln.strip()
            if not ln:
                continue
            try:
                _namings.append(json.loads(ln))
            except Exception:
                pass
    except Exception as e:
        sys.stderr.write("load namings failed: %s\n" % e)


def _append_naming(item):
    """追加一条命名记录（内存 + 落盘）。"""
    with _namings_lock:
        _namings.append(item)
        try:
            with open(NAMINGS_FILE, "a", encoding="utf-8") as f:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        except Exception as e:
            sys.stderr.write("append naming failed: %s\n" % e)


def _recent_namings(limit=120):
    with _namings_lock:
        items = list(_namings)
    return items[-limit:]


def _clean_str(v, maxlen):
    if not isinstance(v, str):
        return ""
    return v.strip()[:maxlen]


def _num(v, default=0.0):
    try:
        return float(v)
    except Exception:
        return default


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

APP_DOWNLOAD_HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>亚信卫星时刻 · APP下载</title>
<style>
  body{{margin:0;background:#03040a;color:#eaf6ff;font-family:-apple-system,"PingFang SC","Noto Sans SC",sans-serif;text-align:center;}}
  .wrap{{max-width:560px;margin:0 auto;padding:18px;}}
  h1{{font-size:22px;letter-spacing:2px;margin:20px 0;color:#2de2ff;}}
  .logo{{width:120px;height:120px;margin:20px auto;border-radius:24px;background:linear-gradient(135deg,#1e4d7c,#0a2b4a);display:flex;align-items:center;justify-content:center;box-shadow:0 10px 30px rgba(45,226,255,0.3);}}
  .logo-icon{{font-size:48px;color:#2de2ff;}}
  .desc{{color:#8aa0c0;font-size:15px;line-height:1.8;margin:20px 0;padding:0 16px;}}
  .btn{{display:inline-block;margin:20px 8px;padding:16px 32px;border-radius:28px;background:#2de2ff;color:#03040a;font-weight:700;text-decoration:none;font-size:16px;transition:all 0.3s;}}
  .btn:hover{{background:#4de2ff;transform:scale(1.05);}}
  .features{{display:flex;justify-content:center;gap:20px;margin:30px 0;}}
  .feature{{background:rgba(255,255,255,0.05);padding:16px;border-radius:12px;flex:1;}}
  .feature-icon{{font-size:24px;margin-bottom:8px;}}
  .feature-text{{color:#9fe8ff;font-size:13px;}}
  .foot{{color:#54657f;font-size:11px;margin:30px 0;}}
  .qr-section{{margin:30px 0;padding:20px;background:rgba(255,255,255,0.03);border-radius:16px;}}
  .qr-img{{width:180px;height:180px;margin:10px auto;border:8px solid white;border-radius:8px;}}
</style>
</head>
<body>
  <div class="wrap">
    <h1>亚信卫星时刻</h1>
    <div class="logo">
      <span class="logo-icon">🌍</span>
    </div>
    <div class="desc">
      探索浩瀚星空，实时观测卫星星座<br/>
      AR实景增强，沉浸式卫星追踪体验
    </div>
    
    <div class="features">
      <div class="feature">
        <div class="feature-icon">🛰️</div>
        <div class="feature-text">星座全景</div>
      </div>
      <div class="feature">
        <div class="feature-icon">📱</div>
        <div class="feature-text">AR卫星</div>
      </div>
      <div class="feature">
        <div class="feature-icon">🔗</div>
        <div class="feature-text">云端分享</div>
      </div>
    </div>
    
    <div>
      <a class="btn" href="{apk_url}" download="asiainfo-satellite.apk">下载 Android 版</a>
    </div>
    
    <div class="qr-section">
      <div style="color:#8aa0c0;font-size:14px;margin-bottom:10px;">扫码下载APP</div>
      <img class="qr-img" src="{qr_url}" alt="下载二维码"/>
    </div>
    
    <div class="tip" style="color:#6b7c99;font-size:12px;margin-top:20px;">
      支持 Android 7.0 及以上版本<br/>
      iOS 版本即将推出
    </div>
    
    <div class="foot">ASIAINFO · SATELLITE MOMENT</div>
  </div>
</body>
</html>"""

# ===== 云端大屏：星座全景（浅色背景）+ 地球上实时标注用户命名 =====
SCREEN_HTML = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>亚信卫星时刻 · 星座全景大屏</title>
<style>
  *{box-sizing:border-box;}
  html,body{margin:0;height:100%;}
  body{
    background:linear-gradient(160deg,#eaf2fc 0%,#dbe8f7 45%,#cfe0f4 100%);
    color:#0b2545;font-family:-apple-system,"PingFang SC","Noto Sans SC",Segoe UI,sans-serif;
    overflow:hidden;
  }
  .banner{
    display:flex;align-items:center;gap:16px;
    padding:18px 34px;
    background:linear-gradient(90deg,#1f5fb0,#2e86d6);
    color:#fff;box-shadow:0 6px 24px rgba(31,95,176,.25);
  }
  .banner .logo{width:42px;height:42px;border-radius:12px;background:rgba(255,255,255,.18);
    display:flex;align-items:center;justify-content:center;font-size:24px;}
  .banner h1{margin:0;font-size:30px;font-weight:800;letter-spacing:6px;}
  .banner .en{margin-left:6px;font-size:13px;letter-spacing:3px;opacity:.8;align-self:flex-end;padding-bottom:5px;}
  .banner .live{margin-left:auto;display:flex;align-items:center;gap:10px;font-size:15px;}
  .dot{width:10px;height:10px;border-radius:50%;background:#5dff9b;box-shadow:0 0 10px #5dff9b;animation:pulse 1.4s infinite;}
  @keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}
  .stage{position:relative;width:100%;height:calc(100% - 78px);}
  #globe{position:absolute;inset:0;width:100%;height:100%;}
  .side{
    position:absolute;top:22px;right:26px;width:300px;max-height:calc(100% - 44px);
    background:rgba(255,255,255,.72);backdrop-filter:blur(8px);
    border:1px solid rgba(31,95,176,.18);border-radius:16px;
    box-shadow:0 10px 30px rgba(31,95,176,.15);padding:16px 16px 8px;overflow:hidden;
    display:flex;flex-direction:column;
  }
  .side h2{margin:0 0 4px;font-size:16px;color:#1f5fb0;letter-spacing:1px;}
  .side .cnt{font-size:13px;color:#5b6b85;margin-bottom:10px;}
  .side .cnt b{color:#e8511d;font-size:18px;}
  .list{overflow:auto;flex:1;}
  .row{display:flex;align-items:center;gap:10px;padding:9px 6px;border-bottom:1px dashed rgba(31,95,176,.14);}
  .row .nm{font-weight:700;color:#0b2545;font-size:15px;}
  .row .meta{font-size:12px;color:#6b7c99;}
  .row .tag{margin-left:auto;font-size:11px;padding:2px 8px;border-radius:10px;color:#fff;white-space:nowrap;}
  .foot{position:absolute;left:26px;bottom:16px;color:#5b6b85;font-size:12px;letter-spacing:2px;}
</style>
</head>
<body>
  <div class="banner">
    <div class="logo">🛰️</div>
    <h1>亚信卫星时刻</h1>
    <span class="en">ASIAINFO · SATELLITE MOMENT</span>
    <div class="live"><span class="dot"></span>星座全景大屏 · 实时同步</div>
  </div>
  <div class="stage">
    <canvas id="globe"></canvas>
    <div class="side">
      <h2>实时命名</h2>
      <div class="cnt">累计命名 <b id="total">0</b> 颗卫星</div>
      <div class="list" id="list"></div>
    </div>
    <div class="foot">数据来源 CelesTrak · 命名由「亚信卫星时刻」APP 用户实时贡献</div>
  </div>
<script>
const CONST_COLORS = { "千帆G60":"#e8511d", "G60":"#e8511d", "星网GW":"#1f5fb0", "GW":"#1f5fb0", "天启":"#1f9d6b", "TQ":"#1f9d6b" };
function colorOf(c){ return CONST_COLORS[c] || "#1f5fb0"; }

const canvas = document.getElementById('globe');
const ctx = canvas.getContext('2d');
let DPR = window.devicePixelRatio || 1;
let W=0, H=0, CX=0, CY=0, R=0;
function resize(){
  const r = canvas.getBoundingClientRect();
  W = r.width; H = r.height;
  canvas.width = W*DPR; canvas.height = H*DPR;
  ctx.setTransform(DPR,0,0,DPR,0,0);
  CX = W/2; CY = H/2; R = Math.min(W,H)*0.38;
}
window.addEventListener('resize', resize); resize();

// 简化大陆点（与 APP 一致的轮廓概念）
const CONTINENTS = [
  [-110,50],[-100,45],[-90,40],[-80,35],[-70,30],[-60,25],[-50,20],[-40,15],[-30,10],
  [-75,-5],[-65,-10],[-55,-15],[-45,-20],[-35,-25],
  [0,55],[10,50],[20,45],[30,40],[40,35],
  [10,35],[20,30],[30,25],[40,20],[50,15],
  [60,55],[70,50],[80,45],[90,40],[100,35],[110,30],[120,25],[130,20],[140,15],
  [115,-20],[125,-25],[135,-30],[145,-35]
];

function project(lat, lon, rr, spin, tilt){
  const latR=lat*Math.PI/180, lonR=(lon+spin)*Math.PI/180;
  const cl=Math.cos(latR);
  const x=cl*Math.sin(lonR), y=Math.sin(latR), z=cl*Math.cos(lonR);
  const t=tilt*Math.PI/180;
  const y2=y*Math.cos(t)-z*Math.sin(t);
  const z2=y*Math.sin(t)+z*Math.cos(t);
  return { x: CX + R*rr*x, y: CY - R*rr*y2, depth: z2 };
}

let spin = 0; const tilt = -18;
let namings = [];

function drawGlobe(){
  ctx.clearRect(0,0,W,H);
  // 大气辉光
  const glow = ctx.createRadialGradient(CX,CY,R*0.6, CX,CY,R*1.35);
  glow.addColorStop(0,'rgba(120,170,230,0.25)');
  glow.addColorStop(1,'rgba(120,170,230,0)');
  ctx.fillStyle = glow;
  ctx.beginPath(); ctx.arc(CX,CY,R*1.35,0,Math.PI*2); ctx.fill();
  // 海洋
  const ocean = ctx.createRadialGradient(CX-R*0.3,CY-R*0.3,R*0.1, CX,CY,R*1.2);
  ocean.addColorStop(0,'#bcd9f6');
  ocean.addColorStop(0.6,'#9cc4ee');
  ocean.addColorStop(1,'#7fb0e4');
  ctx.fillStyle = ocean;
  ctx.beginPath(); ctx.arc(CX,CY,R,0,Math.PI*2); ctx.fill();
  // 经纬网格
  ctx.strokeStyle='rgba(31,95,176,0.18)'; ctx.lineWidth=1;
  for(let lat=-60; lat<=60; lat+=30){
    let prev=null;
    for(let lon=0; lon<=360; lon+=6){
      const p=project(lat,lon,1,spin,tilt);
      if(prev && prev.depth>=0 && p.depth>=0){ ctx.beginPath(); ctx.moveTo(prev.x,prev.y); ctx.lineTo(p.x,p.y); ctx.stroke(); }
      prev=p;
    }
  }
  for(let lon=0; lon<360; lon+=30){
    let prev=null;
    for(let lat=-90; lat<=90; lat+=6){
      const p=project(lat,lon,1,spin,tilt);
      if(prev && prev.depth>=0 && p.depth>=0){ ctx.beginPath(); ctx.moveTo(prev.x,prev.y); ctx.lineTo(p.x,p.y); ctx.stroke(); }
      prev=p;
    }
  }
  // 大陆点
  ctx.fillStyle='rgba(46,134,86,0.85)';
  for(const [lon,lat] of CONTINENTS){
    const p=project(lat,lon,1,spin,tilt);
    if(p.depth>=0){ ctx.beginPath(); ctx.arc(p.x,p.y,4,0,Math.PI*2); ctx.fill(); }
  }
  // 边缘描边
  ctx.strokeStyle='rgba(31,95,176,0.4)'; ctx.lineWidth=1.6;
  ctx.beginPath(); ctx.arc(CX,CY,R,0,Math.PI*2); ctx.stroke();

  // 命名标注（在卫星位置）
  for(const n of namings){
    const rr = 1 + (n.alt||550)/6371;
    const p = project(n.lat||0, n.lon||0, rr, spin, tilt);
    if(p.depth < 0) continue;
    const col = colorOf(n.constellation);
    // 连接线到地表
    const ground = project(n.lat||0, n.lon||0, 1, spin, tilt);
    ctx.strokeStyle = col+'66'; ctx.lineWidth=1;
    ctx.beginPath(); ctx.moveTo(ground.x,ground.y); ctx.lineTo(p.x,p.y); ctx.stroke();
    // 光晕 + 卫星点
    ctx.fillStyle = col+'40'; ctx.beginPath(); ctx.arc(p.x,p.y,9,0,Math.PI*2); ctx.fill();
    ctx.fillStyle = col; ctx.beginPath(); ctx.arc(p.x,p.y,4.5,0,Math.PI*2); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(p.x,p.y,1.8,0,Math.PI*2); ctx.fill();
    // 标签
    const label = n.customName || n.satName || '';
    const sub = n.user ? ('— '+n.user) : '';
    ctx.font = '700 14px -apple-system,"PingFang SC",sans-serif';
    const tw = ctx.measureText(label).width;
    const bx = p.x + 12, by = p.y - 18, pad = 7, bh = sub ? 34 : 22;
    const bw = Math.max(tw, ctx.measureText(sub).width) + pad*2;
    ctx.fillStyle = 'rgba(255,255,255,0.86)';
    roundRect(bx, by, bw, bh, 7); ctx.fill();
    ctx.strokeStyle = col+'88'; ctx.lineWidth=1; roundRect(bx,by,bw,bh,7); ctx.stroke();
    ctx.fillStyle = '#0b2545'; ctx.textBaseline='top';
    ctx.fillText(label, bx+pad, by+4);
    if(sub){ ctx.font='12px -apple-system,"PingFang SC",sans-serif'; ctx.fillStyle=col; ctx.fillText(sub, bx+pad, by+19); }
  }
}
function roundRect(x,y,w,h,r){
  ctx.beginPath();
  ctx.moveTo(x+r,y); ctx.arcTo(x+w,y,x+w,y+h,r); ctx.arcTo(x+w,y+h,x,y+h,r);
  ctx.arcTo(x,y+h,x,y,r); ctx.arcTo(x,y,x+w,y,r); ctx.closePath();
}

function tick(){ spin = (spin + 0.08) % 360; drawGlobe(); requestAnimationFrame(tick); }
tick();

function renderList(){
  const list = document.getElementById('list');
  document.getElementById('total').textContent = namings.length;
  const recent = namings.slice().reverse().slice(0, 40);
  list.innerHTML = recent.map(n => {
    const col = colorOf(n.constellation);
    const nm = (n.customName||n.satName||'').replace(/</g,'&lt;');
    const user = (n.user||'匿名').replace(/</g,'&lt;');
    const c = (n.constellation||'').replace(/</g,'&lt;');
    return '<div class="row"><div><div class="nm">'+nm+'</div><div class="meta">'+user+' · '+(n.satName||'').replace(/</g,'&lt;')+'</div></div><span class="tag" style="background:'+col+'">'+c+'</span></div>';
  }).join('');
}

async function poll(){
  try{
    const r = await fetch('/api/namings?limit=120', {cache:'no-store'});
    const j = await r.json();
    if(Array.isArray(j.items)){ namings = j.items; renderList(); }
  }catch(e){}
}
poll(); setInterval(poll, 3000);
</script>
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
        if path == "/screen":
            # 星座全景大屏（浅色背景 + 地球上实时标注命名）
            return self._send(200, SCREEN_HTML, "text/html; charset=utf-8")
        if path == "/api/namings":
            qs = parse_qs(parsed.query)
            try:
                limit = int(qs.get("limit", ["120"])[0])
            except ValueError:
                limit = 120
            limit = max(1, min(limit, NAMINGS_KEEP))
            items = _recent_namings(limit)
            return self._send(200, json.dumps({"ok": True, "items": items}, ensure_ascii=False))
        m = re.match(r"^/i/([A-Za-z0-9_-]+)\.png$", path)
        if m:
            return self._serve_image(m.group(1))
        m = re.match(r"^/a/([A-Za-z0-9_-]+)\.apk$", path)
        if m:
            return self._serve_apk(m.group(1))
        m = re.match(r"^/p/([A-Za-z0-9_-]+)$", path)
        if m:
            sid = m.group(1)
            fp = os.path.join(DATA_DIR, sid + ".png")
            if not os.path.exists(fp):
                return self._send(404, "not found", "text/plain; charset=utf-8")
            img = "%s/i/%s.png" % (PUBLIC_BASE, sid)
            return self._send(200, PAGE_HTML.format(img=img), "text/html; charset=utf-8")
        if path == "/app":
            # APP下载页面
            # 使用最新的APK文件
            apk_files = [f for f in os.listdir(DATA_DIR) if f.endswith('.apk')]
            if apk_files:
                latest_apk = max(apk_files)
                apk_url = "%s/a/%s.apk" % (PUBLIC_BASE, latest_apk.replace('.apk', ''))
                # 生成下载二维码
                qr_url = "%s/app" % PUBLIC_BASE
                return self._send(200, APP_DOWNLOAD_HTML.format(apk_url=apk_url, qr_url=qr_url), "text/html; charset=utf-8")
            else:
                return self._send(404, "APK not available", "text/plain; charset=utf-8")
        return self._send(404, json.dumps({"error": "not found"}))

    def _serve_image(self, sid):
        fp = os.path.join(DATA_DIR, sid + ".png")
        if not os.path.exists(fp):
            return self._send(404, "not found", "text/plain; charset=utf-8")
        with open(fp, "rb") as f:
            data = f.read()
        self._send(200, data, "image/png", extra={"Cache-Control": "public, max-age=86400"})

    def _serve_apk(self, sid):
        fp = os.path.join(DATA_DIR, sid + ".apk")
        if not os.path.exists(fp):
            return self._send(404, "not found", "text/plain; charset=utf-8")
        with open(fp, "rb") as f:
            data = f.read()
        self._send(200, data, "application/vnd.android.package-archive", extra={"Cache-Control": "public, max-age=86400"})

    def do_POST(self):
        parsed = urlparse(self.path)
        if parsed.path == "/name":
            # 星座全景命名上报：请求体为 JSON
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                length = 0
            if length <= 0 or length > NAME_MAX_BYTES:
                return self._send(400, json.dumps({"error": "bad length"}))
            raw = self.rfile.read(length)
            try:
                body = json.loads(raw.decode("utf-8"))
            except Exception:
                return self._send(400, json.dumps({"error": "bad json"}))
            custom_name = _clean_str(body.get("customName"), 40)
            if not custom_name:
                return self._send(400, json.dumps({"error": "customName required"}))
            item = {
                "satId": _clean_str(body.get("satId"), 20),
                "satName": _clean_str(body.get("satName"), 60),
                "customName": custom_name,
                "user": _clean_str(body.get("user"), 24),
                "constellation": _clean_str(body.get("constellation"), 20),
                "lat": round(_num(body.get("lat")), 4),
                "lon": round(_num(body.get("lon")), 4),
                "alt": round(_num(body.get("alt"), 550.0), 1),
                "ts": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            }
            _append_naming(item)
            return self._send(200, json.dumps({"ok": True, "item": item}, ensure_ascii=False))
        if parsed.path == "/upload-apk":
            # APK上传
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
            if length <= 0 or length > APK_MAX_BYTES:
                return self._send(400, json.dumps({"error": "bad length"}))
            data = self.rfile.read(length)
            fp = os.path.join(DATA_DIR, sid + ".apk")
            with open(fp, "wb") as f:
                f.write(data)
            resp = {
                "id": sid,
                "apk": "%s/a/%s.apk" % (PUBLIC_BASE, sid),
                "page": "%s/app" % PUBLIC_BASE,
            }
            return self._send(200, json.dumps(resp))
        elif parsed.path == "/upload":
            # 图片上传
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
        else:
            return self._send(404, json.dumps({"error": "not found"}))

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


class ThreadingHTTPServer(socketserver.ThreadingMixIn, HTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    _load_namings()
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    sys.stderr.write("sat-share listening on 0.0.0.0:%d, base=%s, data=%s\n" % (PORT, PUBLIC_BASE, DATA_DIR))
    sys.stderr.write("  大屏: %s/screen   命名上报: POST %s/name\n" % (PUBLIC_BASE, PUBLIC_BASE))
    httpd.serve_forever()
