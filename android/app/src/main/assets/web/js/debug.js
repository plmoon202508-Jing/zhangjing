/* ===========================================================
 * debug.js — 屏幕内置调试日志面板（用于在 WebView / 真机上抓取日志）
 * 必须最先加载，以便捕获后续脚本的所有 console 输出与运行时错误。
 * 功能：捕获 console.log/info/warn/error、window.onerror、未处理 Promise 异常；
 *      浮动「日志」按钮可展开面板，支持复制全部日志、清空。
 * =========================================================== */
(function (global) {
  'use strict';

  var MAX = 400;
  var buffer = [];
  var panel, list, badge;
  var errorCount = 0;

  function ts() {
    var d = new Date();
    function p(n, l) { return String(n).padStart(l || 2, '0'); }
    return p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) + '.' + p(d.getMilliseconds(), 3);
  }

  function stringify(args) {
    return Array.prototype.map.call(args, function (a) {
      if (a instanceof Error) return a.message + (a.stack ? '\n' + a.stack : '');
      if (typeof a === 'object') {
        try { return JSON.stringify(a); } catch (e) { return String(a); }
      }
      return String(a);
    }).join(' ');
  }

  function push(level, text) {
    var entry = { t: ts(), level: level, text: text };
    buffer.push(entry);
    if (buffer.length > MAX) buffer.shift();
    if (level === 'error') errorCount++;
    render(entry);
    updateBadge();
  }

  function updateBadge() {
    if (!badge) return;
    badge.textContent = errorCount > 0 ? ('日志 ⚠' + errorCount) : ('日志 ' + buffer.length);
    badge.style.background = errorCount > 0 ? 'rgba(255,78,78,.92)' : 'rgba(45,226,255,.9)';
  }

  var COLOR = { log: '#cfe6ff', info: '#7ad4ff', warn: '#ffd24e', error: '#ff7a7a' };
  function render(entry) {
    if (!list) return;
    var row = document.createElement('div');
    row.style.cssText = 'padding:3px 8px;border-bottom:1px solid rgba(255,255,255,.06);' +
      'font:11px/1.4 ui-monospace,Menlo,Consolas,monospace;white-space:pre-wrap;word-break:break-word;color:' + (COLOR[entry.level] || '#cfe6ff');
    row.textContent = '[' + entry.t + '] ' + entry.text;
    list.appendChild(row);
    list.scrollTop = list.scrollHeight;
  }

  function buildUI() {
    badge = document.createElement('button');
    badge.id = 'dbg-badge';
    badge.style.cssText = 'position:fixed;right:12px;bottom:14px;z-index:99999;' +
      'border:none;border-radius:14px;padding:7px 12px;font:600 12px/1 sans-serif;' +
      'color:#03040a;background:rgba(45,226,255,.9);box-shadow:0 4px 14px rgba(0,0,0,.4);';
    badge.textContent = '日志';
    badge.onclick = function () { panel.style.display = (panel.style.display === 'flex') ? 'none' : 'flex'; };

    panel = document.createElement('div');
    panel.id = 'dbg-panel';
    panel.style.cssText = 'position:fixed;left:0;right:0;bottom:0;height:46vh;z-index:99998;display:none;' +
      'flex-direction:column;background:rgba(4,7,16,.96);border-top:1px solid rgba(45,226,255,.4);' +
      'backdrop-filter:blur(4px);';

    var bar = document.createElement('div');
    bar.style.cssText = 'display:flex;gap:8px;align-items:center;padding:8px 10px;border-bottom:1px solid rgba(255,255,255,.1);';
    var title = document.createElement('span');
    title.textContent = '运行日志';
    title.style.cssText = 'flex:1;color:#eaf6ff;font:600 13px sans-serif;';
    bar.appendChild(title);
    bar.appendChild(mkBtn('复制', copyAll));
    bar.appendChild(mkBtn('清空', clearAll));
    bar.appendChild(mkBtn('关闭', function () { panel.style.display = 'none'; }));

    list = document.createElement('div');
    list.id = 'dbg-list';
    list.style.cssText = 'flex:1;overflow-y:auto;overflow-x:hidden;';

    panel.appendChild(bar);
    panel.appendChild(list);
    document.body.appendChild(panel);
    document.body.appendChild(badge);

    // 回放缓冲（UI 构建前产生的日志）
    buffer.forEach(render);
    updateBadge();
  }

  function mkBtn(label, fn) {
    var b = document.createElement('button');
    b.textContent = label;
    b.style.cssText = 'border:1px solid rgba(45,226,255,.4);background:rgba(45,226,255,.12);color:#9fe6ff;' +
      'border-radius:8px;padding:5px 10px;font:12px sans-serif;';
    b.onclick = fn;
    return b;
  }

  function copyAll() {
    var text = buffer.map(function (e) { return '[' + e.t + '][' + e.level + '] ' + e.text; }).join('\n');
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(function () { flash('已复制'); }, function () { fallbackCopy(text); });
    } else { fallbackCopy(text); }
  }
  function fallbackCopy(text) {
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.cssText = 'position:fixed;left:-9999px;';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); flash('已复制'); } catch (e) { flash('复制失败'); }
    document.body.removeChild(ta);
  }
  function clearAll() { buffer = []; errorCount = 0; if (list) list.innerHTML = ''; updateBadge(); }
  function flash(msg) {
    var f = document.createElement('div');
    f.textContent = msg;
    f.style.cssText = 'position:fixed;left:50%;top:20px;transform:translateX(-50%);z-index:100000;' +
      'background:rgba(45,226,255,.95);color:#03040a;padding:8px 16px;border-radius:10px;font:600 13px sans-serif;';
    document.body.appendChild(f);
    setTimeout(function () { document.body.removeChild(f); }, 1200);
  }

  // ---- 劫持 console ----
  ['log', 'info', 'warn', 'error'].forEach(function (lvl) {
    var orig = console[lvl] ? console[lvl].bind(console) : function () {};
    console[lvl] = function () { orig.apply(null, arguments); push(lvl, stringify(arguments)); };
  });

  // ---- 全局错误 ----
  global.addEventListener('error', function (e) {
    if (e && e.message) push('error', 'window.onerror: ' + e.message + ' @ ' + (e.filename || '') + ':' + (e.lineno || ''));
  });
  global.addEventListener('unhandledrejection', function (e) {
    var r = e && e.reason;
    push('error', 'unhandledrejection: ' + (r && r.message ? r.message : stringify([r])));
  });

  // 暴露 API，供其他模块主动写日志/显示错误
  global.DebugLog = {
    push: push,
    show: function () { if (panel) panel.style.display = 'flex'; },
    info: function (m) { push('info', m); }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', buildUI);
  } else {
    buildUI();
  }

  push('info', 'debug.js loaded · UA=' + navigator.userAgent);
})(window);
