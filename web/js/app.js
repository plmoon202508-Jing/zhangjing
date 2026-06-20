/* ===========================================================
 * app.js — 应用主控制：导航 / 详情面板 / 改名 / 分享 / 登记
 * =========================================================== */
(function () {
  'use strict';

  const views = document.querySelectorAll('.view');
  let current = 'home';
  let earthReady = false;
  let arReady = false;
  let activeSat = null;

  /* ---------- 视图导航 ---------- */
  function navigate(name) {
    if (name === current) return;
    views.forEach(v => v.classList.toggle('is-active', v.dataset.view === name));

    // 离开旧页面
    if (current === 'constellation') Earth.stop();
    if (current === 'ar') AR.stop();

    current = name;

    if (name === 'constellation') enterConstellation();
    if (name === 'ar') enterAR();
    if (name === 'share') renderShare();
  }

  document.querySelectorAll('[data-nav]').forEach(el => {
    el.addEventListener('click', () => navigate(el.dataset.nav));
  });

  /* ---------- 星座全景 ---------- */
  function enterConstellation() {
    console.log('[App] enterConstellation', { earthReady, THREE: !!window.THREE });
    const canvas = document.getElementById('earth-canvas');
    const loading = document.getElementById('earth-loading');
    console.log('[App] canvas and loading', { canvas: !!canvas, loading: !!loading });
    if (!earthReady) {
      setTimeout(() => {
        console.log('[App] calling Earth.init');
        Earth.init(canvas);          // 先用内置兜底数据即时渲染
        Earth.onSelect(openDetail);
        updateCounts();
        loading.classList.add('is-hidden');
        earthReady = true;
        Earth.start();
        loadRealData();              // 异步拉取 CelesTrak 实时数据
      }, 400);
    } else {
      Earth.start();
      updateCounts();
    }
  }

  function updateCounts() {
    document.getElementById('sat-count').textContent = Earth.count();
    const c = SatData.counts();
    document.getElementById('gw-count').textContent = c.GW || 0;
    document.getElementById('g60-count').textContent = c.G60 || 0;
    document.getElementById('tq-count').textContent = c.TQ || 0;
  }

  function loadRealData() {
    const loadingMsg = document.querySelector('#earth-loading span');
    SatData.load().then((res) => {
      if (res.source === 'online' || res.source === 'cache') {
        Earth.reload();
        updateCounts();
        const label = res.source === 'online' ? 'CelesTrak 实时数据' : '本地缓存数据';
        toast(`已加载${label} · 共 ${res.count} 颗`);
      } else {
        toast('网络不可用，使用内置演示数据');
      }
    }).catch((e) => {
      console.error('[App] loadRealData error', e);
      toast('数据加载失败，使用内置演示数据');
    });
  }

  document.querySelectorAll('#filter-chips .chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.querySelectorAll('#filter-chips .chip').forEach(c => c.classList.remove('is-active'));
      chip.classList.add('is-active');
      Earth.setFilter(chip.dataset.group);
      document.getElementById('sat-count').textContent = Earth.count();
    });
  });

  document.getElementById('toggle-orbits').addEventListener('click', () => {
    const on = Earth.toggleOrbits();
    toast(on ? '已显示轨道' : '已隐藏轨道');
  });

  /* ---------- AR 卫星 ---------- */
  function enterAR() {
    const els = {
      canvas: document.getElementById('ar-overlay'),
      video: document.getElementById('ar-camera'),
      fallback: document.getElementById('ar-fallback'),
      compassDeg: document.getElementById('ar-deg'),
      hint: document.getElementById('ar-hint')
    };
    if (!arReady) { AR.init(els); AR.onSelect(openDetail); arReady = true; }
    AR.start();
  }
  document.getElementById('ar-recenter').addEventListener('click', () => { AR.recenter(); toast('已校准视角'); });

  /* ---------- 卫星详情面板 ---------- */
  const sheet = document.getElementById('detail-sheet');
  const backdrop = document.getElementById('sheet-backdrop');

  let detailTimer = null;
  function renderDetailLive() {
    if (!activeSat) return;
    const pt = SatData.geodetic(activeSat, new Date());
    if (!pt) return;
    document.getElementById('d-alt').textContent = pt.alt.toFixed(0) + ' km';
    document.getElementById('d-lon').textContent = pt.lon.toFixed(2) + '°';
    document.getElementById('d-lat').textContent = pt.lat.toFixed(2) + '°';
    document.getElementById('d-vel').textContent = pt.velocity.toFixed(2) + ' km/s';
  }
  function openDetail(sat) {
    activeSat = sat;
    document.getElementById('d-name').textContent = sat.displayName;
    document.getElementById('d-tag').textContent = sat.groupLabel;
    document.getElementById('d-norad').textContent = sat.norad;
    document.getElementById('d-inc').textContent = sat.inc.toFixed(1) + '°';
    document.getElementById('d-custom').value = sat.customName || '';
    renderDetailLive();
    clearInterval(detailTimer);
    detailTimer = setInterval(renderDetailLive, 1000);   // 实时刷新位置
    sheet.classList.add('is-open');
    backdrop.classList.add('is-open');
  }
  function closeDetail() { clearInterval(detailTimer); sheet.classList.remove('is-open'); backdrop.classList.remove('is-open'); }
  document.getElementById('detail-close').addEventListener('click', closeDetail);
  backdrop.addEventListener('click', closeDetail);

  document.getElementById('d-save').addEventListener('click', () => {
    if (!activeSat) return;
    const val = document.getElementById('d-custom').value.trim();
    SatData.saveCustomName(activeSat.norad, val);
    activeSat.customName = val || null;
    activeSat.displayName = val || activeSat.name;
    document.getElementById('d-name').textContent = activeSat.displayName;
    // 重建场景以刷新标签
    if (earthReady) { Earth.refreshNames(); }
    toast(val ? '已重命名为「' + val + '」' : '已恢复原名');
    closeDetail();
  });

  /* ---------- 分享 / 二维码 ---------- */
  function renderShare() {
    const code = 'AISM-' + Math.floor(100000 + Math.random() * 900000);
    document.getElementById('invite-code').textContent = '邀请码: ' + code;
    const payload = JSON.stringify({ app: 'AsiaInfo Satellite Moment', action: 'invite', code });
    QR.render(document.getElementById('qr-canvas'), payload);
  }

  /* ---------- 用户登记 ---------- */
  document.getElementById('reg-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    if (!data.name || !data.phone) { toast('请填写姓名和手机号'); return; }
    // 原型：本地保存，正式版提交到后端
    localStorage.setItem('aism_user', JSON.stringify(Object.assign(data, { at: Date.now() })));
    toast('登记成功，正在进入体验...');
    setTimeout(() => { e.target.reset(); navigate('constellation'); }, 1200);
  });

  /* ---------- Toast ---------- */
  let toastTimer = null;
  function toast(msg) {
    const el = document.getElementById('toast');
    el.textContent = msg; el.classList.add('is-show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('is-show'), 2200);
  }

  // 暴露给调试
  window.__nav = navigate;

  // 支持 URL hash 深链接 (#constellation / #ar / #share / #form)
  function applyHash() {
    const h = (location.hash || '').replace('#', '');
    if (h && document.querySelector(`.view[data-view="${h}"]`)) navigate(h);
  }
  window.addEventListener('hashchange', applyHash);
  applyHash();
})();
