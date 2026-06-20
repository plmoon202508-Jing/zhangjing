/* ===========================================================
 * earth.js — 星座全景：全息地球 + 真实星历卫星 (Three.js + SGP4)
 * 卫星位置由 satellite.js 按真实时间 SGP4 传播，逐帧实时更新。
 * 坐标系：ECI(惯性系) -> 场景；地球按 GMST 自转对齐。
 * 1 场景单位 = 1 地球半径 = 6371 km。
 * =========================================================== */
(function (global) {
  'use strict';

  const R_EARTH = 1.0;
  const RE_KM = 6371;            // 1 单位 = 6371 km

  let renderer, scene, camera, raf = null;
  let root, globe, clouds, satGroup, orbitGroup;
  let satellites = [];
  let satMeshes = [];
  let selectCb = null;
  let filter = 'all';
  let rotation = { x: -0.3, y: 0 };
  let autoSpin = true, showOrbits = true;
  let camDist = 3.2;
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  let lastPos = 0;
  const POS_INTERVAL = 200;      // 位置更新节流(ms)，5Hz 实时

  const GROUP_COLOR = {
    GW: 0x2de2ff,    // 星网 GW 国网 — 青
    G60: 0xff4ecd,   // 垣信 G60 千帆 — 品红
    TQ: 0x4dffb8     // 国电高科 天启 — 翠绿
  };

  // 太阳方向（场景/世界坐标，昼夜着色与云层光照共用）
  const SUN_DIR = new THREE.Vector3(5, 2.5, 4).normalize();

  /* ---------- ECI(km) -> 场景坐标 (Y 轴朝北) ---------- */
  function eciToScene(p) {
    return new THREE.Vector3(p.x / RE_KM, p.z / RE_KM, -p.y / RE_KM);
  }
  /* ---------- 轨道圆点(惯性系) -> 场景 ---------- */
  function orbitPoint(aScene, i, Om, theta) {
    const p = aScene * Math.cos(theta), q = aScene * Math.sin(theta);
    const ci = Math.cos(i), si = Math.sin(i), cO = Math.cos(Om), sO = Math.sin(Om);
    const ex = p * cO - q * ci * sO;
    const ey = p * sO + q * ci * cO;
    const ez = q * si;
    return new THREE.Vector3(ex, ez, -ey);
  }

  function buildGlobe() {
    const g = new THREE.Group();
    const loader = new THREE.TextureLoader();

    // 真实地球贴图：昼面 + 夜面(城市灯光)
    const dayMap = loader.load('textures/ssc_2k_earth_daymap.jpg');
    const nightMap = loader.load('textures/ssc_2k_earth_nightmap.jpg');
    if ('encoding' in dayMap) dayMap.encoding = THREE.sRGBEncoding;
    if ('encoding' in nightMap) nightMap.encoding = THREE.sRGBEncoding;

    // 昼夜混合着色器：白天显示昼面，夜面显示城市灯光，按太阳方向过渡
    const earth = new THREE.Mesh(
      new THREE.SphereGeometry(R_EARTH, 64, 48),
      new THREE.ShaderMaterial({
        uniforms: {
          dayTex: { value: dayMap },
          nightTex: { value: nightMap },
          sunDir: { value: SUN_DIR.clone() }
        },
        vertexShader: `varying vec2 vUv; varying vec3 vNW;
          void main(){ vUv = uv; vNW = normalize(mat3(modelMatrix) * normal);
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`,
        fragmentShader: `uniform sampler2D dayTex, nightTex; uniform vec3 sunDir;
          varying vec2 vUv; varying vec3 vNW;
          void main(){ float l = dot(normalize(vNW), sunDir);
            float m = smoothstep(-0.12, 0.28, l);
            vec3 day = texture2D(dayTex, vUv).rgb;
            vec3 night = texture2D(nightTex, vUv).rgb * 1.6;
            gl_FragColor = vec4(mix(night, day, m), 1.0); }`
      })
    );
    g.add(earth);

    // 云层
    const cloudMap = loader.load('textures/ssc_2k_earth_clouds.jpg');
    clouds = new THREE.Mesh(
      new THREE.SphereGeometry(R_EARTH * 1.012, 64, 48),
      new THREE.MeshPhongMaterial({ map: cloudMap, transparent: true, opacity: 0.38, depthWrite: false })
    );
    g.add(clouds);

    // 大气辉光（保留蓝色边缘光）
    g.add(new THREE.Mesh(
      new THREE.SphereGeometry(R_EARTH * 1.18, 48, 48),
      new THREE.ShaderMaterial({
        transparent: true, side: THREE.BackSide, blending: THREE.AdditiveBlending,
        vertexShader: `varying vec3 vN; void main(){ vN = normalize(normalMatrix*normal); gl_Position = projectionMatrix*modelViewMatrix*vec4(position,1.0);}`,
        fragmentShader: `varying vec3 vN; void main(){ float i = pow(0.72 - dot(vN, vec3(0,0,1.0)), 3.0); gl_FragColor = vec4(0.22,0.62,1.0,1.0)*i; }`
      })
    ));
    return g;
  }

  function buildSatellites() {
    satGroup = new THREE.Group();
    orbitGroup = new THREE.Group();
    satMeshes = [];

    satellites.forEach((sat) => {
      const color = GROUP_COLOR[sat.group] || 0xffffff;
      const i = sat.inc * Math.PI / 180;
      const Om = sat.raan * Math.PI / 180;
      const aScene = sat.aEr || (R_EARTH + sat.alt / RE_KM);

      // 轨道环（惯性系圆近似）
      const segs = 96, opos = [];
      for (let k = 0; k <= segs; k++) {
        const v = orbitPoint(aScene, i, Om, (k / segs) * Math.PI * 2);
        opos.push(v.x, v.y, v.z);
      }
      const orbitGeo = new THREE.BufferGeometry();
      orbitGeo.setAttribute('position', new THREE.Float32BufferAttribute(opos, 3));
      const orbit = new THREE.Line(orbitGeo, new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.18 }));
      orbit.userData.group = sat.group;
      orbitGroup.add(orbit);

      // 卫星辉光精灵
      const sprite = new THREE.Sprite(new THREE.SpriteMaterial({
        map: glowTexture(color), transparent: true, blending: THREE.AdditiveBlending, depthWrite: false
      }));
      sprite.scale.set(0.1, 0.1, 0.1);
      sprite.userData = { sat, aScene, inc: i, raan: Om, filtered: true, label: null };

      // 自定义命名 → 在地球上呈现该名称
      if (sat.customName) {
        const label = makeTextSprite(sat.customName, color);
        label.userData.isLabel = true;
        satGroup.add(label);
        sprite.userData.label = label;
      }

      satGroup.add(sprite);
      satMeshes.push(sprite);
    });

    positionSatellites(new Date());
    root.add(orbitGroup);
    root.add(satGroup);
  }

  function positionSatellites(now) {
    const sgp4 = global.satellite;
    const tSec = now.getTime() / 1000;
    satMeshes.forEach((m) => {
      const sat = m.userData.sat;
      if (sat.mode === 'sgp4' && sgp4 && sat.satrec) {
        const pv = sgp4.propagate(sat.satrec, now);
        if (!pv || !pv.position) { m.visible = false; if (m.userData.label) m.userData.label.visible = false; return; }
        m.position.copy(eciToScene(pv.position));
        m.visible = m.userData.filtered;
      } else {
        const mm = (2 * Math.PI) / (sat.periodMin * 60);
        m.position.copy(orbitPoint(m.userData.aScene, m.userData.inc, m.userData.raan, sat.phase + mm * tSec));
      }
      // 同步名称标签位置（略微上移，避免遮挡光点）
      if (m.userData.label) {
        m.userData.label.position.set(m.position.x, m.position.y + 0.07, m.position.z);
        m.userData.label.visible = m.visible;
      }
    });
  }

  // 径向辉光贴图
  const texCache = {};
  function glowTexture(color) {
    if (texCache[color]) return texCache[color];
    const c = document.createElement('canvas'); c.width = c.height = 64;
    const x = c.getContext('2d');
    const hex = '#' + color.toString(16).padStart(6, '0');
    const g = x.createRadialGradient(32, 32, 0, 32, 32, 32);
    g.addColorStop(0, '#ffffff'); g.addColorStop(0.25, hex); g.addColorStop(1, 'rgba(0,0,0,0)');
    x.fillStyle = g; x.fillRect(0, 0, 64, 64);
    const t = new THREE.CanvasTexture(c); texCache[color] = t; return t;
  }

  // 文本标签精灵（用于在地球上呈现卫星自定义名称）
  function makeTextSprite(text, color) {
    const pad = 12, fontSize = 40;
    const measure = document.createElement('canvas').getContext('2d');
    measure.font = `bold ${fontSize}px "Noto Sans SC", sans-serif`;
    const w = Math.ceil(measure.measureText(text).width) + pad * 2;
    const h = fontSize + pad * 2;
    const c = document.createElement('canvas'); c.width = w; c.height = h;
    const x = c.getContext('2d');
    const hex = '#' + color.toString(16).padStart(6, '0');
    // 背景胶囊
    x.fillStyle = 'rgba(6,12,28,0.6)';
    if (x.roundRect) { x.beginPath(); x.roundRect(0, 0, w, h, 14); x.fill(); }
    else x.fillRect(0, 0, w, h);
    // 文本
    x.font = `bold ${fontSize}px "Noto Sans SC", sans-serif`;
    x.textAlign = 'center'; x.textBaseline = 'middle';
    x.shadowColor = hex; x.shadowBlur = 12;
    x.fillStyle = '#ffffff';
    x.fillText(text, w / 2, h / 2);
    const tex = new THREE.CanvasTexture(c);
    if ('encoding' in tex) tex.encoding = THREE.sRGBEncoding;
    const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false }));
    const scale = 0.0016;
    sprite.scale.set(w * scale, h * scale, 1);
    return sprite;
  }

  function applyFilter() {
    satMeshes.forEach((m) => {
      const ok = (filter === 'all' || m.userData.sat.group === filter);
      m.userData.filtered = ok;
      m.visible = ok;
    });
    orbitGroup.children.forEach((o) => {
      o.visible = showOrbits && (filter === 'all' || o.userData.group === filter);
    });
  }

  function render() {
    const now = new Date();
    if (now.getTime() - lastPos >= POS_INTERVAL) { positionSatellites(now); lastPos = now.getTime(); }
    if (autoSpin) rotation.y += 0.0012;
    root.rotation.x = rotation.x;
    root.rotation.y = rotation.y;
    // 地球按真实自转角(GMST)对齐
    if (global.satellite) globe.rotation.y = global.satellite.gstime(now);
    if (clouds) clouds.rotation.y += 0.0004;   // 云层缓慢漂移
    camera.position.z = camDist;
    renderer.render(scene, camera);
    raf = requestAnimationFrame(render);
  }

  /* ---------- 交互 ---------- */
  function bindInteraction(canvas) {
    let dragging = false, lx = 0, ly = 0, moved = 0;
    const down = (x, y) => { dragging = true; lx = x; ly = y; moved = 0; autoSpin = false; };
    const move = (x, y) => {
      if (!dragging) return;
      const dx = x - lx, dy = y - ly; lx = x; ly = y; moved += Math.abs(dx) + Math.abs(dy);
      rotation.y += dx * 0.005;
      rotation.x = Math.max(-1.4, Math.min(1.4, rotation.x + dy * 0.005));
    };
    const up = (x, y) => {
      if (dragging && moved < 6) pick(x, y);
      dragging = false;
      setTimeout(() => { autoSpin = true; }, 2500);
    };
    canvas.addEventListener('mousedown', e => down(e.clientX, e.clientY));
    window.addEventListener('mousemove', e => move(e.clientX, e.clientY));
    window.addEventListener('mouseup', e => up(e.clientX, e.clientY));
    canvas.addEventListener('wheel', e => { e.preventDefault(); camDist = Math.max(1.4, Math.min(6, camDist + e.deltaY * 0.002)); }, { passive: false });
    let pinch = 0;
    canvas.addEventListener('touchstart', e => {
      if (e.touches.length === 1) down(e.touches[0].clientX, e.touches[0].clientY);
      else if (e.touches.length === 2) pinch = dist2(e);
    }, { passive: true });
    canvas.addEventListener('touchmove', e => {
      if (e.touches.length === 1) move(e.touches[0].clientX, e.touches[0].clientY);
      else if (e.touches.length === 2) { const d = dist2(e); if (pinch) camDist = Math.max(1.4, Math.min(6, camDist - (d - pinch) * 0.005)); pinch = d; }
    }, { passive: true });
    canvas.addEventListener('touchend', e => { const t = e.changedTouches[0]; up(t.clientX, t.clientY); pinch = 0; });
  }
  function dist2(e) { const a = e.touches[0], b = e.touches[1]; return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY); }

  function pick(clientX, clientY) {
    const rect = renderer.domElement.getBoundingClientRect();
    pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    raycaster.params.Sprite = { threshold: 0.1 };
    const hits = raycaster.intersectObjects(satMeshes.filter(m => m.visible));
    if (hits.length && selectCb) selectCb(hits[0].object.userData.sat);
  }

  function disposeGroup(group) {
    group.traverse((obj) => {
      if (obj.geometry) obj.geometry.dispose();
      if (obj.material) { if (obj.material.map) obj.material.map.dispose(); obj.material.dispose(); }
    });
  }

  /* ---------- 公共 API ---------- */
  const Earth = {
    init(canvas) {
      renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
      renderer.setPixelRatio(Math.min(2, window.devicePixelRatio));
      if ('outputEncoding' in renderer) renderer.outputEncoding = THREE.sRGBEncoding;
      resize();
      scene = new THREE.Scene();
      camera = new THREE.PerspectiveCamera(45, canvas.clientWidth / canvas.clientHeight, 0.1, 100);
      camera.position.z = camDist;

      // 光照（用于云层 Phong）：太阳方向与昼夜着色器一致
      scene.add(new THREE.AmbientLight(0x6a86a0, 0.45));
      const sun = new THREE.DirectionalLight(0xffffff, 1.15);
      sun.position.copy(SUN_DIR);
      scene.add(sun);

      root = new THREE.Group();
      scene.add(root);
      globe = buildGlobe();
      root.add(globe);

      satellites = SatData.build();
      buildSatellites();
      applyFilter();
      bindInteraction(canvas);
      window.addEventListener('resize', resize);
      return satellites.length;
    },
    start() { if (!raf) render(); },
    stop() { if (raf) { cancelAnimationFrame(raf); raf = null; } },
    setFilter(g) { filter = g; applyFilter(); },
    toggleOrbits() { showOrbits = !showOrbits; applyFilter(); return showOrbits; },
    onSelect(cb) { selectCb = cb; },
    // 重命名后重建场景，使名称标签即时显示/更新
    refreshNames() {
      if (satGroup) { root.remove(satGroup); disposeGroup(satGroup); }
      if (orbitGroup) { root.remove(orbitGroup); disposeGroup(orbitGroup); }
      satellites = SatData.build();
      buildSatellites();
      applyFilter();
    },
    // 数据更新后重建（切换到 CelesTrak 实时数据）
    reload() {
      if (satGroup) { root.remove(satGroup); disposeGroup(satGroup); }
      if (orbitGroup) { root.remove(orbitGroup); disposeGroup(orbitGroup); }
      satellites = SatData.build();
      buildSatellites();
      applyFilter();
      return satellites.length;
    },
    count() { return satellites.filter(s => filter === 'all' || s.group === filter).length; }
  };

  function resize() {
    const canvas = renderer.domElement;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    renderer.setSize(w, h, false);
    if (camera) { camera.aspect = w / h; camera.updateProjectionMatrix(); }
  }

  global.Earth = Earth;
})(window);
