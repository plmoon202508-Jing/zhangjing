/* ===========================================================
 * ar.js — AR 卫星预览（相机 + 卫星方位叠加）
 * 桌面无相机/无传感器时自动回退到模拟天空 + 自动扫描
 * =========================================================== */
(function (global) {
  'use strict';

  let canvas, ctx, video, fallback, compassDeg, hintEl;
  let raf = null, running = false;
  let heading = 0;          // 方位角(度)
  let pitch = 30;           // 俯仰角(度)
  let autoSweep = true;     // 无传感器时自动扫描
  let sats = [];
  let selectCb = null;
  const FOV = 60;           // 视场角(度)
  let hitTargets = [];      // 屏幕上的卫星命中区

  // 给每颗卫星分配一个"天空方位"(演示用固定方位)
  function assignSky() {
    sats = SatData.build().map((s) => {
      // 用 raan/inc 派生一个稳定的方位/仰角
      const az = (s.raan * 1.7 + s.norad) % 360;
      const el = 10 + (Math.abs(s.inc) % 70);
      return Object.assign({}, s, { az, el });
    });
  }

  async function initCamera() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' }, audio: false
      });
      video.srcObject = stream;
      fallback.style.display = 'none';
      return true;
    } catch (e) {
      fallback.style.display = 'block';   // 回退到模拟天空
      return false;
    }
  }

  function initSensors() {
    if (typeof DeviceOrientationEvent !== 'undefined') {
      // iOS 需要授权
      if (typeof DeviceOrientationEvent.requestPermission === 'function') {
        DeviceOrientationEvent.requestPermission().then(p => {
          if (p === 'granted') listen();
        }).catch(() => {});
      } else {
        listen();
      }
    }
    function listen() {
      window.addEventListener('deviceorientation', (e) => {
        if (e.alpha != null) {
          autoSweep = false;
          heading = (e.webkitCompassHeading != null) ? e.webkitCompassHeading : (360 - e.alpha);
          if (e.beta != null) pitch = Math.max(-10, Math.min(85, e.beta - 30));
        }
      });
    }
  }

  function angDiff(a, b) {
    let d = ((a - b + 540) % 360) - 180;
    return d;
  }

  function draw() {
    const w = canvas.width = canvas.clientWidth;
    const h = canvas.height = canvas.clientHeight;
    ctx.clearRect(0, 0, w, h);

    if (autoSweep) heading = (heading + 0.15) % 360;
    compassDeg.textContent = Math.round(heading) + '°';
    document.querySelector('.ar-compass__ring').style.transform = `rotate(${-heading}deg)`;

    hitTargets = [];
    const halfFovX = FOV / 2;
    const halfFovY = (FOV / 2) * (h / w);

    for (const s of sats) {
      const dAz = angDiff(s.az, heading);
      const dEl = s.el - pitch;
      if (Math.abs(dAz) > halfFovX || Math.abs(dEl) > halfFovY + 10) continue;

      const x = w / 2 + (dAz / halfFovX) * (w / 2);
      const y = h / 2 - (dEl / halfFovY) * (h / 2);
      const color = colorFor(s.group);

      // 光晕
      const grad = ctx.createRadialGradient(x, y, 0, x, y, 26);
      grad.addColorStop(0, color);
      grad.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.globalAlpha = 0.85; ctx.fillStyle = grad;
      ctx.beginPath(); ctx.arc(x, y, 26, 0, Math.PI * 2); ctx.fill();

      // 核心点
      ctx.globalAlpha = 1; ctx.fillStyle = '#fff';
      ctx.beginPath(); ctx.arc(x, y, 3.5, 0, Math.PI * 2); ctx.fill();

      // 标签
      ctx.globalAlpha = 0.95;
      ctx.font = '12px "Noto Sans SC", sans-serif';
      ctx.fillStyle = color; ctx.textAlign = 'left';
      const label = s.displayName;
      ctx.fillText(label, x + 16, y - 6);
      ctx.globalAlpha = 0.6; ctx.fillStyle = '#8aa0c0';
      ctx.font = '10px "Noto Sans SC", sans-serif';
      ctx.fillText(s.alt + ' km', x + 16, y + 9);

      hitTargets.push({ x, y, r: 28, sat: s });
    }
    ctx.globalAlpha = 1;
    raf = requestAnimationFrame(draw);
  }

  function colorFor(g) {
    return ({ GW: '#2de2ff', G60: '#ff4ecd', TQ: '#4dffb8' })[g] || '#fff';
  }

  function onTap(e) {
    const rect = canvas.getBoundingClientRect();
    const px = (e.touches ? e.changedTouches[0].clientX : e.clientX) - rect.left;
    const py = (e.touches ? e.changedTouches[0].clientY : e.clientY) - rect.top;
    let best = null, bestD = 1e9;
    for (const t of hitTargets) {
      const d = Math.hypot(t.x - px, t.y - py);
      if (d < t.r && d < bestD) { bestD = d; best = t.sat; }
    }
    if (best && selectCb) selectCb(best);
  }

  const AR = {
    init(els) {
      canvas = els.canvas; ctx = canvas.getContext('2d');
      video = els.video; fallback = els.fallback;
      compassDeg = els.compassDeg; hintEl = els.hint;
      assignSky();
      canvas.addEventListener('click', onTap);
      canvas.addEventListener('touchend', onTap);
    },
    async start() {
      if (running) return; running = true;
      assignSky();                       // 先用现有数据即时呈现
      await initCamera();
      initSensors();
      draw();
      // 后台拉取/刷新 CelesTrak 实时数据
      SatData.load().then((res) => {
        if (res.source === 'online' || res.source === 'cache') assignSky();
      }).catch(() => {});
    },
    stop() {
      running = false;
      if (raf) { cancelAnimationFrame(raf); raf = null; }
      if (video && video.srcObject) {
        video.srcObject.getTracks().forEach(t => t.stop());
        video.srcObject = null;
      }
    },
    recenter() { heading = 0; pitch = 30; },
    onSelect(cb) { selectCb = cb; }
  };

  global.AR = AR;
})(window);
