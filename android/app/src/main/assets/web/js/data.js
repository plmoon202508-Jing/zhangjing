/* ===========================================================
 * data.js — 卫星数据模块（真实星历）
 * 从 CelesTrak 拉取真实 TLE 两行根数，使用 satellite.js (SGP4)
 * 按当前真实时间推算每颗卫星位置，实时更新。
 * 星座：GW 星网国网 / G60 千帆 / TQ 国电高科天启
 * 三级回退：在线 → 本地缓存(6h) → 内置壳层兜底(Kepler 近似)
 * =========================================================== */
(function (global) {
  'use strict';

  const GROUP_LABELS = { GW: '星网 GW 国网', G60: '垣信 G60 千帆', TQ: '国电高科 天启' };
  const MU = 398600.4418, RE = 6371;

  // CelesTrak 查询（按名称，TLE 格式）；CORS 已开放
  // 国网 GW 在 CelesTrak 主要以「HULIANWANG（互联网低轨/技术试验）」命名，
  //   另有少量「GUOWANG」测试星；两者合并即完整 GW LEO 星座(约 180+ 颗)。
  // filter：剔除同名不同任务卫星（TIANQIN 天琴 ≠ TIANQI 天启）
  const ENDPOINTS = [
    { group: 'GW',  name: 'HULIANWANG' },   // 互联网低轨 DIGUI + 技术试验 JISHU
    { group: 'GW',  name: 'GUOWANG' },      // 国网测试星 / OBJECT
    { group: 'G60', name: 'QIANFAN' },      // 垣信 G60 千帆
    { group: 'TQ',  name: 'TIANQI', filter: (n) => /^TIANQI-/i.test(n) }  // 天启(剔除天琴)
  ];
  const API = (name) =>
    `https://celestrak.org/NORAD/elements/gp.php?NAME=${encodeURIComponent(name)}&FORMAT=tle`;

  const CACHE_KEY = 'aism_tle_v3';
  const CACHE_TS = 'aism_tle_v3_time';
  const CACHE_TTL = 6 * 3600 * 1000;
  const STORE_KEY = 'aism_custom_names';

  /* ---------- 内置壳层兜底（离线 Kepler 近似）---------- */
  const FALLBACK_DEF = [
    { group: 'GW',  prefix: 'GUOWANG', base: 62000, shells: [
      { tag: 'A59', alt: 590,  inc: 85.0, planes: 5, perPlane: 4 },
      { tag: 'GW2', alt: 1145, inc: 50.0, planes: 3, perPlane: 3 } ] },
    { group: 'G60', prefix: 'QIANFAN', base: 60379, shells: [
      { tag: 'P1', alt: 1070, inc: 89.0, planes: 6, perPlane: 4 } ] },
    { group: 'TQ',  prefix: 'TIANQI', base: 44314, shells: [
      { tag: 'I35', alt: 600, inc: 35.0, planes: 3, perPlane: 3 },
      { tag: 'I45', alt: 850, inc: 45.0, planes: 2, perPlane: 2 } ] }
  ];
  function genFallback() {
    const out = [];
    FALLBACK_DEF.forEach((c) => {
      let idx = 0;
      c.shells.forEach((shell) => {
        for (let p = 0; p < shell.planes; p++) {
          const raan = Math.round((p / shell.planes) * 360);
          for (let s = 0; s < shell.perPlane; s++) {
            idx++;
            out.push({
              norad: c.base + out.length, name: `${c.prefix}-${shell.tag}-${String(idx).padStart(2, '0')}`,
              group: c.group, inc: shell.inc, alt: shell.alt, raan, ecc: 0.0001,
              _phase: (s / shell.perPlane) * Math.PI * 2 + p * 0.4
            });
          }
        }
      });
    });
    return out;
  }

  let dataset = genFallback();   // 当前数据集（默认兜底）
  let source = 'fallback';

  /* ---------- TLE 解析 ---------- */
  function parseTLE(text, group, filterFn) {
    const lines = text.split(/\r?\n/);
    const out = [];
    for (let i = 0; i + 2 < lines.length; i++) {
      if (/^1 /.test(lines[i + 1]) && /^2 /.test(lines[i + 2])) {
        const name = lines[i].trim();
        if (filterFn && !filterFn(name)) { i += 2; continue; }
        out.push({
          norad: parseInt(lines[i + 1].substring(2, 7), 10),
          name, group, line1: lines[i + 1], line2: lines[i + 2]
        });
        i += 2;
      }
    }
    return out;
  }

  /* ---------- 在线加载（含缓存）---------- */
  async function load(force) {
    if (!force) {
      try {
        const ts = +localStorage.getItem(CACHE_TS) || 0;
        const cached = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
        if (cached && cached.length && Date.now() - ts < CACHE_TTL) {
          dataset = cached; source = 'cache';
          return { source, count: cached.length };
        }
      } catch (e) { /* ignore */ }
    }
    try {
      const results = await Promise.all(
        ENDPOINTS.map((e) =>
          fetch(API(e.name))
            .then((r) => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.text(); })
            .then((txt) => parseTLE(txt, e.group, e.filter))
        )
      );
      // 合并并按 NORAD 去重（不同名称查询可能命中同一卫星）
      const seen = new Set();
      const merged = results.flat().filter((s) => {
        if (seen.has(s.norad)) return false;
        seen.add(s.norad); return true;
      });
      if (!merged.length) throw new Error('空数据');
      dataset = merged; source = 'online';
      try {
        localStorage.setItem(CACHE_KEY, JSON.stringify(merged));
        localStorage.setItem(CACHE_TS, String(Date.now()));
      } catch (e) { /* 配额超限忽略 */ }
      return { source, count: merged.length };
    } catch (e) {
      try {
        const cached = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null');
        if (cached && cached.length) { dataset = cached; source = 'cache'; return { source, count: cached.length, error: e.message }; }
      } catch (_) {}
      dataset = genFallback(); source = 'fallback';
      return { source, count: dataset.length, error: e.message };
    }
  }

  /* ---------- SGP4 satrec（懒加载并缓存到 raw 上）---------- */
  function makeSatrec(raw) {
    if ('_satrec' in raw) return raw._satrec;
    try {
      raw._satrec = (global.satellite && raw.line1)
        ? global.satellite.twoline2satrec(raw.line1, raw.line2) : null;
    } catch (e) { raw._satrec = null; }
    return raw._satrec;
  }
  function elementsFromSatrec(rec) {
    const aEr = rec.a;                          // 半长轴(地球半径)
    return {
      inc: rec.inclo * 180 / Math.PI,
      raan: rec.nodeo * 180 / Math.PI,
      ecc: rec.ecco,
      alt: Math.round((aEr - 1) * RE),
      aEr,
      periodMin: (2 * Math.PI) / rec.no        // rec.no: rad/min
    };
  }

  /* ---------- 自定义名称（有效期 1 天）---------- */
  const NAME_TTL = 24 * 3600 * 1000;   // 命名有效期 1 天
  // 返回 { norad: name } 的纯净映射，并自动剔除过期项
  function loadCustomNames() {
    let raw;
    try { raw = JSON.parse(localStorage.getItem(STORE_KEY) || '{}'); } catch (e) { return {}; }
    const now = Date.now();
    const clean = {};
    let changed = false;
    Object.keys(raw).forEach((k) => {
      const v = raw[k];
      if (v && typeof v === 'object') {
        if (v.name && (now - (v.ts || 0) < NAME_TTL)) clean[k] = v.name;
        else changed = true;                 // 过期，丢弃
      } else if (typeof v === 'string') {
        // 兼容旧格式（无时间戳）：升级为带时间戳，立即生效
        clean[k] = v; changed = true;
      }
    });
    if (changed) {
      const store = {};
      Object.keys(clean).forEach((k) => { store[k] = { name: clean[k], ts: now }; });
      try { localStorage.setItem(STORE_KEY, JSON.stringify(store)); } catch (e) {}
    }
    return clean;
  }
  function saveCustomName(norad, name) {
    let store;
    try { store = JSON.parse(localStorage.getItem(STORE_KEY) || '{}'); } catch (e) { store = {}; }
    if (name && name.trim()) store[norad] = { name: name.trim(), ts: Date.now() };
    else delete store[norad];
    try { localStorage.setItem(STORE_KEY, JSON.stringify(store)); } catch (e) {}
  }

  /* ---------- 构建派生列表 ---------- */
  function build() {
    const custom = loadCustomNames();
    const out = [];
    dataset.forEach((s, i) => {
      let mode, inc, raan, ecc, alt, periodMin, aEr, satrec = null;
      if (s.line1) {
        satrec = makeSatrec(s);
        if (!satrec || satrec.error !== 0) return;     // 跳过无效 TLE
        const el = elementsFromSatrec(satrec);
        mode = 'sgp4'; inc = el.inc; raan = el.raan; ecc = el.ecc; alt = el.alt; aEr = el.aEr; periodMin = el.periodMin;
      } else {
        mode = 'kepler'; inc = s.inc; raan = s.raan; ecc = s.ecc || 0; alt = s.alt;
        aEr = 1 + alt / RE; periodMin = orbitalPeriod(alt);
      }
      out.push(Object.assign({}, s, {
        id: s.norad + '_' + i, mode, satrec, inc, raan, ecc, alt, aEr, periodMin,
        velocity: orbitalVelocity(alt),
        customName: custom[s.norad] || null,
        displayName: custom[s.norad] || s.name,
        groupLabel: GROUP_LABELS[s.group] || s.group,
        phase: (s._phase != null) ? s._phase : Math.random() * Math.PI * 2
      }));
    });
    return out;
  }

  function orbitalVelocity(altKm) { return Math.sqrt(MU / (RE + altKm)); }
  function orbitalPeriod(altKm) { const a = RE + altKm; return (2 * Math.PI * Math.sqrt(a * a * a / MU)) / 60; }

  /* ---------- 实时星下点（详情面板用）---------- */
  function geodetic(sat, date) {
    date = date || new Date();
    if (sat.mode === 'sgp4' && sat.satrec && global.satellite) {
      const pv = global.satellite.propagate(sat.satrec, date);
      if (!pv || !pv.position) return null;
      const gmst = global.satellite.gstime(date);
      const gd = global.satellite.eciToGeodetic(pv.position, gmst);
      const v = pv.velocity;
      return {
        lat: global.satellite.degreesLat(gd.latitude),
        lon: global.satellite.degreesLong(gd.longitude),
        alt: gd.height,
        velocity: v ? Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) : sat.velocity
      };
    }
    // Kepler 兜底
    const tSec = date.getTime() / 1000;
    const meanMotion = (2 * Math.PI) / (sat.periodMin * 60);
    const u = sat.phase + meanMotion * tSec;
    const inc = sat.inc * Math.PI / 180;
    const lat = Math.asin(Math.sin(inc) * Math.sin(u)) * 180 / Math.PI;
    let lon = (sat.raan + (u * 180 / Math.PI) - (tSec * 360 / 86400)) % 360;
    if (lon > 180) lon -= 360; if (lon < -180) lon += 360;
    return { lat, lon, alt: sat.alt, velocity: sat.velocity };
  }

  function counts() {
    const r = {};
    dataset.forEach((s) => { r[s.group] = (r[s.group] || 0) + 1; });
    return r;
  }

  global.SatData = {
    GROUP_LABELS, RE,
    load, build, geodetic, counts,
    saveCustomName, loadCustomNames,
    source: () => source
  };
})(window);
