/* ===========================================================
 * qrcode.js — 二维码绘制
 * 原型阶段：绘制具备定位/校正图形的"拟真"二维码（由邀请码确定性生成）。
 * 正式 Android 版将用 ZXing 生成真实可扫描二维码。
 * =========================================================== */
(function (global) {
  'use strict';

  // 简单确定性伪随机
  function seeded(seed) {
    let s = 0;
    for (let i = 0; i < seed.length; i++) s = (s * 31 + seed.charCodeAt(i)) >>> 0;
    return function () { s = (s * 1103515245 + 12345) & 0x7fffffff; return s / 0x7fffffff; };
  }

  function drawFinder(grid, n, r, c) {
    for (let i = 0; i < 7; i++) for (let j = 0; j < 7; j++) {
      const edge = i === 0 || i === 6 || j === 0 || j === 6;
      const inner = i >= 2 && i <= 4 && j >= 2 && j <= 4;
      grid[r + i][c + j] = (edge || inner) ? 1 : 0;
    }
  }

  const QR = {
    render(canvas, text) {
      const ctx = canvas.getContext('2d');
      const N = 33;                          // 模块数
      const grid = Array.from({ length: N }, () => Array(N).fill(0));
      const rnd = seeded(text || 'AISM');

      // 数据区随机填充
      for (let i = 0; i < N; i++) for (let j = 0; j < N; j++) {
        grid[i][j] = rnd() > 0.5 ? 1 : 0;
      }
      // 三个定位图形
      drawFinder(grid, N, 0, 0);
      drawFinder(grid, N, 0, N - 7);
      drawFinder(grid, N, N - 7, 0);
      // 定位图形周边留白
      const clear = (r, c) => { for (let i = -1; i <= 7; i++) for (let j = -1; j <= 7; j++) { const y = r + i, x = c + j; if (y >= 0 && y < N && x >= 0 && x < N && (i < 0 || i > 6 || j < 0 || j > 6)) grid[y][x] = 0; } };
      clear(0, 0); clear(0, N - 7); clear(N - 7, 0);
      // 校正图形(右下)
      for (let i = 0; i < 5; i++) for (let j = 0; j < 5; j++) {
        const edge = i === 0 || i === 4 || j === 0 || j === 4;
        const center = i === 2 && j === 2;
        grid[N - 9 + i][N - 9 + j] = (edge || center) ? 1 : 0;
      }
      // 时序图形
      for (let i = 8; i < N - 8; i++) { grid[6][i] = i % 2 === 0 ? 1 : 0; grid[i][6] = i % 2 === 0 ? 1 : 0; }

      // 绘制
      const size = canvas.width;
      const cell = size / N;
      ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, size, size);
      ctx.fillStyle = '#06122a';
      for (let i = 0; i < N; i++) for (let j = 0; j < N; j++) {
        if (grid[i][j]) {
          // 圆角点更有科技感
          const x = j * cell, y = i * cell;
          ctx.beginPath();
          ctx.roundRect ? ctx.roundRect(x + cell * 0.08, y + cell * 0.08, cell * 0.84, cell * 0.84, cell * 0.25)
                        : ctx.rect(x, y, cell, cell);
          ctx.fill();
        }
      }
    }
  };

  global.QR = QR;
})(window);
