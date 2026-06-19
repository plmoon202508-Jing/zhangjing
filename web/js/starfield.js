/* ===========================================================
 * starfield.js — 全局粒子星空背景
 * =========================================================== */
(function () {
  'use strict';
  const canvas = document.getElementById('starfield');
  const ctx = canvas.getContext('2d');
  let w, h, stars, shooting = [];

  function resize() {
    w = canvas.width = window.innerWidth;
    h = canvas.height = window.innerHeight;
    const count = Math.min(220, Math.floor((w * h) / 7000));
    stars = Array.from({ length: count }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      z: Math.random(),                       // 深度 -> 大小/亮度
      tw: Math.random() * Math.PI * 2,        // 闪烁相位
      tws: 0.5 + Math.random() * 1.5          // 闪烁速度
    }));
  }

  function spawnShooting() {
    if (Math.random() < 0.012 && shooting.length < 2) {
      const startX = Math.random() * w;
      shooting.push({ x: startX, y: -20, vx: -2 - Math.random() * 2, vy: 4 + Math.random() * 4, life: 1 });
    }
  }

  const COLORS = ['#2de2ff', '#a05bff', '#6ef0ff', '#ffffff'];

  function draw(t) {
    ctx.clearRect(0, 0, w, h);
    // 星点
    for (const s of stars) {
      const a = 0.35 + 0.45 * Math.sin(t * 0.001 * s.tws + s.tw);
      const r = s.z * 1.6 + 0.3;
      ctx.beginPath();
      ctx.fillStyle = `rgba(180,220,255,${a * (0.4 + s.z * 0.6)})`;
      ctx.arc(s.x, s.y, r, 0, Math.PI * 2);
      ctx.fill();
    }
    // 流星
    spawnShooting();
    for (let i = shooting.length - 1; i >= 0; i--) {
      const m = shooting[i];
      m.x += m.vx; m.y += m.vy; m.life -= 0.012;
      const grad = ctx.createLinearGradient(m.x, m.y, m.x - m.vx * 8, m.y - m.vy * 8);
      grad.addColorStop(0, `rgba(110,240,255,${m.life})`);
      grad.addColorStop(1, 'rgba(110,240,255,0)');
      ctx.strokeStyle = grad; ctx.lineWidth = 2;
      ctx.beginPath(); ctx.moveTo(m.x, m.y); ctx.lineTo(m.x - m.vx * 8, m.y - m.vy * 8); ctx.stroke();
      if (m.life <= 0 || m.y > h) shooting.splice(i, 1);
    }
    requestAnimationFrame(draw);
  }

  window.addEventListener('resize', resize);
  resize();
  requestAnimationFrame(draw);
})();
