import { useState, useEffect, useRef, useCallback } from "react";

// ================================================================ ДАНІ

const PALETTES = [
  { name: "NEON",   cost: 0,   bg: "#0e1024", ring: "#2b3060", player: "#00e5ff", gem: "#ffd54a", spike: "#ff3d68" },
  { name: "SUNSET", cost: 150, bg: "#241220", ring: "#542a44", player: "#ff9e4d", gem: "#ffe08a", spike: "#ff4d6d" },
  { name: "TOXIC",  cost: 150, bg: "#0c1a10", ring: "#235c31", player: "#7dff5e", gem: "#eaff5e", spike: "#ff5e5e" },
  { name: "ICE",    cost: 150, bg: "#0e1622", ring: "#2b4a6f", player: "#bfe9ff", gem: "#6ec6ff", spike: "#ff7b9c" },
  { name: "SYNTH",  cost: 250, bg: "#160222", ring: "#4a1566", player: "#ff3ec8", gem: "#19f7e2", spike: "#b44dff" },
  { name: "MAGMA",  cost: 250, bg: "#190805", ring: "#58221a", player: "#ff8b2e", gem: "#ffd76b", spike: "#ff2e55" },
  { name: "VOID",   cost: 400, bg: "#070310", ring: "#2a1b52", player: "#a86bff", gem: "#64f0c8", spike: "#ff4d8d" },
  { name: "GOLD",   cost: 600, bg: "#14100a", ring: "#5c4a22", player: "#ffe27a", gem: "#7ae0ff", spike: "#ff5470" },
];

const UPGRADES = [
  { id: "magnet", name: "MAGNET",       desc: "Wider gem pickup",      max: 5,  base: 25, curve: 1.9 },
  { id: "shield", name: "SHIELD",       desc: "Start with charges",    max: 3,  base: 60, curve: 1.9 },
  { id: "slow",   name: "CALM START",   desc: "Slower start speed",    max: 5,  base: 20, curve: 1.9 },
  { id: "bdur",   name: "BOOST TIME",   desc: "+10% booster duration", max: 5,  base: 30, curve: 1.9 },
  { id: "bfreq",  name: "BOOST RATE",   desc: "Boosters spawn faster", max: 5,  base: 35, curve: 1.9 },
  { id: "keeper", name: "COMBO KEEPER", desc: "Combo decays slower",   max: 5,  base: 30, curve: 1.9 },
  { id: "value",  name: "GEM VALUE",    desc: "+5% gems per pickup",   max: 10, base: 15, curve: 2.2 },
];

const upCost = (u, lvl) => Math.round(u.base * Math.pow(u.curve, lvl));
const ORBIT3_COST = 500;

const BOOSTS = {
  shield: { label: "SHIELD",  ch: "S", color: "#4dd9ff", dur: 0 },
  magnet: { label: "MAGNET",  ch: "M", color: "#c07bff", dur: 8 },
  frenzy: { label: "GEM x2",  ch: "2", color: "#ffd54a", dur: 10 },
  slow:   { label: "SLOW-MO", ch: "≈", color: "#ffffff", dur: 4 },
  pulse:  { label: "PULSE",   ch: "P", color: "#ff9f2e", dur: 0 },
};

const MISSION_TYPES = {
  gems:  { name: (g) => `Collect ${g} gems`,     goals: [40, 80],   rew: [20, 35] },
  near:  { name: (g) => `${g} near-misses`,      goals: [6, 12],    rew: [25, 40] },
  boost: { name: (g) => `Use ${g} boosters`,     goals: [3, 6],     rew: [20, 35] },
  runs:  { name: (g) => `Play ${g} runs`,        goals: [3, 6],     rew: [15, 25] },
  score: { name: (g) => `Score ${g} in one run`, goals: [400, 800], rew: [25, 45] },
};
const STREAK_REW = [10, 15, 20, 30, 40, 60, 100];

const DEFAULT_SAVE = {
  gems: 0, best: 0, runs: 0, palette: 0, owned: [0], orbit3: false, noAds: false,
  pid: null, name: null,
  upgrades: { magnet: 0, shield: 0, slow: 0, bdur: 0, bfreq: 0, keeper: 0, value: 0 },
  daily: null,
};

const W = 720, H = 1280, CX = 360, CY = 610, D = Math.PI / 180;
const LAYOUT2 = [190, 320, 320];
const LAYOUT3 = [130, 225, 320];

const MENU_STARS = [
  { x: 12, y: 14, s: 3, d: 2.4 }, { x: 78, y: 9, s: 2, d: 3.1 }, { x: 90, y: 30, s: 3, d: 2.0 },
  { x: 8, y: 44, s: 2, d: 3.6 }, { x: 68, y: 52, s: 2, d: 2.7 }, { x: 22, y: 66, s: 3, d: 2.2 },
  { x: 88, y: 72, s: 2, d: 3.3 }, { x: 45, y: 6, s: 2, d: 2.9 }, { x: 55, y: 88, s: 3, d: 2.5 },
  { x: 10, y: 90, s: 2, d: 3.0 }, { x: 33, y: 28, s: 2, d: 3.8 }, { x: 95, y: 55, s: 2, d: 2.3 },
];

const hx = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const rgba = (hex, a) => { const c = hx(hex); return `rgba(${c[0]},${c[1]},${c[2]},${a})`; };
const brighten = (hex, f) => { const c = hx(hex).map((v) => Math.min(255, Math.round(v * f))); return `rgb(${c[0]},${c[1]},${c[2]})`; };
const norm = (a) => { let x = a % 360; if (x < 0) x += 360; return x; };
const angDiff = (a, b) => ((a - b) % 360 + 540) % 360 - 180;
const rnd = (a, b) => a + Math.random() * (b - a);
const semitone = (base, n) => base * Math.pow(2, n / 12);
const todayStr = () => { const d = new Date(); const p = (n) => String(n).padStart(2, "0"); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`; };
const isYesterday = (s) => { if (!s) return false; const d = new Date(s + "T12:00:00"); const y = new Date(); y.setDate(y.getDate() - 1); return d.getFullYear() === y.getFullYear() && d.getMonth() === y.getMonth() && d.getDate() === y.getDate(); };
const genPid = () => (Math.random().toString(36).slice(2, 8) + Date.now().toString(36).slice(-4)).toUpperCase();

const genMissions = () => {
  const keys = Object.keys(MISSION_TYPES).sort(() => Math.random() - 0.5).slice(0, 3);
  return keys.map((k) => {
    const t = MISSION_TYPES[k];
    const tier = Math.random() < 0.6 ? 0 : 1;
    return { type: k, goal: t.goals[tier], rew: t.rew[tier], prog: 0, claimed: false };
  });
};

// "NeonBaker": запечені glow-спрайти — canvas-еквівалент bloom
const glowCache = {};
const getGlow = (color) => {
  if (glowCache[color]) return glowCache[color];
  const s = 64, c = document.createElement("canvas");
  c.width = s; c.height = s;
  const g = c.getContext("2d");
  const grad = g.createRadialGradient(s / 2, s / 2, 0, s / 2, s / 2, s / 2);
  grad.addColorStop(0, rgba(color, 0.7));
  grad.addColorStop(0.35, rgba(color, 0.25));
  grad.addColorStop(1, rgba(color, 0));
  g.fillStyle = grad;
  g.fillRect(0, 0, s, s);
  glowCache[color] = c;
  return c;
};

// ================================================================ КОМПОНЕНТ

export default function OrbitDash() {
  const [save, setSave] = useState(DEFAULT_SAVE);
  const [screen, setScreen] = useState("menu"); // menu | game | shop | missions | ranks
  const [boot, setBoot] = useState({ phase: "loading", pct: 0 }); // loading | ready | menu
  const [runKey, setRunKey] = useState(0);
  const [deadInfo, setDeadInfo] = useState(null);
  const [x2done, setX2done] = useState(false);
  const [ad, setAd] = useState(null);
  const [adFill, setAdFill] = useState(false);
  const [toast, setToast] = useState(null);
  const [muted, setMuted] = useState(false);
  const [booted, setBooted] = useState(false);
  const [storageOk, setStorageOk] = useState(true);
  const [dev, setDev] = useState(false);
  const [devArm, setDevArm] = useState(false);
  const [lb, setLb] = useState({ loading: false, rows: null, err: null });
  const [lbBump, setLbBump] = useState(0);
  const [nameDraft, setNameDraft] = useState("");

  const canvasRef = useRef(null);
  const holderRef = useRef(null);
  const engineRef = useRef(null);
  const saveRef = useRef(save);
  const mutedRef = useRef(false);
  const loadedRef = useRef(false);
  const actxRef = useRef(null);
  const deathsRef = useRef(0);
  const lastAdRef = useRef(0);
  const quickDeathsRef = useRef(0);
  const pendingBoostRef = useRef(null);

  const pal = PALETTES[save.palette] || PALETTES[0];
  const reduced = typeof window !== "undefined" && window.matchMedia
    ? window.matchMedia("(prefers-reduced-motion: reduce)").matches : false;

  useEffect(() => { saveRef.current = save; }, [save]);
  useEffect(() => { mutedRef.current = muted; }, [muted]);

  // ---------------------------------------------------------- ЗВУК / ВІБРО

  const beep = useCallback((f, dur, type, vol, slide) => {
    if (mutedRef.current) return;
    try {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      actxRef.current = actxRef.current || new AC();
      const a = actxRef.current;
      if (a.state === "suspended") a.resume();
      const o = a.createOscillator(), g = a.createGain();
      o.type = type; o.frequency.setValueAtTime(f, a.currentTime);
      if (slide) o.frequency.exponentialRampToValueAtTime(Math.max(40, f + slide), a.currentTime + dur);
      g.gain.setValueAtTime(vol, a.currentTime);
      g.gain.exponentialRampToValueAtTime(0.001, a.currentTime + dur);
      o.connect(g); g.connect(a.destination);
      o.start(); o.stop(a.currentTime + dur);
    } catch (e) {}
  }, []);

  const buzz = (ms) => { try { if (navigator.vibrate) navigator.vibrate(ms); } catch (e) {} };

  // ---------------------------------------------------------- ЗБЕРЕЖЕННЯ

  useEffect(() => {
    (async () => {
      try {
        if (window.storage && window.storage.get) {
          const r = await window.storage.get("orbitdash-save");
          if (r && r.value) {
            const p = JSON.parse(r.value);
            setSave((s) => ({
              ...DEFAULT_SAVE, ...p,
              owned: Array.isArray(p.owned) && p.owned.length ? p.owned : [0],
              upgrades: { ...DEFAULT_SAVE.upgrades, ...(p.upgrades || {}) },
            }));
          }
        } else setStorageOk(false);
      } catch (e) {}
      loadedRef.current = true;
      setBooted(true);
    })();
  }, []);

  useEffect(() => {
    if (!booted) return;
    if (!save.pid) setSave((s) => ({ ...s, pid: genPid() }));
  }, [booted, save.pid]);

  // ---------------------------------------------------------- ЛОАДЕР (бут-послідовність)
  useEffect(() => {
    if (!booted || boot.phase !== "loading") return;
    let alive = true;
    let t = 0;
    const step = () => {
      if (!alive) return;
      setBoot((b) => {
        if (b.phase !== "loading") return b;
        const slow = b.pct > 72 ? 0.55 : 1;
        const np = Math.min(100, b.pct + rnd(5, 13) * slow);
        return { phase: np >= 100 ? "ready" : "loading", pct: np };
      });
      t = window.setTimeout(step, rnd(70, 150));
    };
    t = window.setTimeout(step, 250);
    return () => { alive = false; clearTimeout(t); };
  }, [booted, boot.phase]);

  useEffect(() => {
    if (boot.phase === "ready") beep(660, 0.14, "sine", 0.09, 260);
  }, [boot.phase, beep]);

  const goMenu = useCallback(() => {
    setBoot((b) => (b.phase === "ready" ? { phase: "menu", pct: 100 } : b));
    beep(520, 0.12, "sine", 0.1, 260);
  }, [beep]);

  useEffect(() => {
    if (!loadedRef.current) return;
    (async () => {
      try { if (window.storage && window.storage.set) await window.storage.set("orbitdash-save", JSON.stringify(save)); }
      catch (e) { setStorageOk(false); }
    })();
  }, [save]);

  // ---------------------------------------------------------- ЛІДЕРБОРД

  const submitScore = useCallback(async (score) => {
    const s = saveRef.current;
    if (!s.pid || score <= 0) return;
    try {
      if (window.storage && window.storage.set) {
        const n = (s.name || "PLAYER-" + s.pid.slice(0, 4)).slice(0, 12);
        await window.storage.set("lb:" + s.pid, JSON.stringify({ n, s: score, t: Date.now() }), true);
      }
    } catch (e) {}
  }, []);
  const submitRef = useRef(submitScore);
  useEffect(() => { submitRef.current = submitScore; }, [submitScore]);

  useEffect(() => {
    if (screen !== "ranks") return;
    let dead = false;
    (async () => {
      setLb({ loading: true, rows: null, err: null });
      try {
        if (!(window.storage && window.storage.list && window.storage.get)) throw new Error("nostorage");
        const r = await window.storage.list("lb:", true);
        const keys = (r && r.keys ? r.keys : []).slice(0, 64);
        const rows = [];
        for (let i = 0; i < keys.length; i += 8) {
          const chunk = keys.slice(i, i + 8);
          const res = await Promise.all(chunk.map((k) => window.storage.get(k, true).catch(() => null)));
          for (const it of res) {
            if (it && it.value) {
              try {
                const v = JSON.parse(it.value);
                if (v && typeof v.s === "number") rows.push({ pid: it.key.slice(3), n: String(v.n || "PLAYER").slice(0, 12), s: Math.floor(v.s) });
              } catch (e) {}
            }
          }
        }
        rows.sort((a, b) => b.s - a.s);
        if (!dead) setLb({ loading: false, rows: rows.slice(0, 20), err: null });
      } catch (e) {
        if (!dead) setLb({ loading: false, rows: null, err: true });
      }
    })();
    return () => { dead = true; };
  }, [screen, lbBump]);

  useEffect(() => {
    if (screen === "ranks") setNameDraft(save.name || "PLAYER-" + (save.pid || "").slice(0, 4));
  }, [screen]); // eslint-disable-line

  // ---------------------------------------------------------- ЩОДЕННІ МІСІЇ

  useEffect(() => {
    if (!booted || screen === "game") return;
    const today = todayStr();
    if (save.daily && save.daily.date === today) return;
    const prevDate = save.daily ? save.daily.date : null;
    const prevStreak = save.daily ? save.daily.streak : 0;
    const streak = isYesterday(prevDate) ? Math.min(7, prevStreak + 1) : 1;
    setSave((s) => ({ ...s, daily: { date: today, streak, streakClaimed: false, missions: genMissions() } }));
  }, [booted, screen, save.daily]);

  const claimable = save.daily && (
    (!save.daily.streakClaimed) ||
    save.daily.missions.some((m) => !m.claimed && m.prog >= m.goal)
  );

  const commitRun = useCallback((r) => {
    setSave((s) => {
      if (!s.daily) return s;
      const missions = s.daily.missions.map((m) => {
        if (m.claimed) return m;
        let prog = m.prog;
        if (m.type === "gems") prog += r.gems;
        if (m.type === "near") prog += r.near;
        if (m.type === "boost") prog += r.boost;
        if (m.type === "runs") prog += 1;
        if (m.type === "score") prog = Math.max(prog, r.score);
        return { ...m, prog: Math.min(prog, m.goal) };
      });
      return { ...s, daily: { ...s.daily, missions } };
    });
  }, []);

  // ---------------------------------------------------------- РЕКЛАМА (симуляція)

  const showRewarded = (label, onReward) => { beep(500, 0.05, "square", 0.06, 0); setAd({ kind: "rewarded", label, onDone: onReward }); };

  const maybeInterstitial = (cb) => {
    if (saveRef.current.noAds) return cb();
    const now = Date.now();
    if (deathsRef.current >= 3 && now - lastAdRef.current > 60000) {
      lastAdRef.current = now;
      setAd({ kind: "inter", label: "INTERSTITIAL", onDone: cb });
    } else cb();
  };

  useEffect(() => {
    if (!ad) return;
    setAdFill(false);
    const r = requestAnimationFrame(() => requestAnimationFrame(() => setAdFill(true)));
    const t = setTimeout(() => { const cb = ad.onDone; setAd(null); setAdFill(false); if (cb) cb(); }, 1550);
    return () => { cancelAnimationFrame(r); clearTimeout(t); };
  }, [ad]);

  const flash = (msg) => { setToast(msg); setTimeout(() => setToast(null), 1300); };

  // ================================================================ РУШІЙ

  useEffect(() => {
    if (screen !== "game") return;
    const canvas = canvasRef.current, holder = holderRef.current;
    if (!canvas || !holder) return;
    const ctx = canvas.getContext("2d");
    const sv = saveRef.current;
    const up = sv.upgrades;

    const mercy = quickDeathsRef.current >= 2;
    if (mercy) quickDeathsRef.current = 0;

    const st = {
      state: "run", time: 0, angle: -90, ringIndex: 0, dir: 1,
      ringCount: 2, ringR: [...LAYOUT2], target: LAYOUT2, r3a: 0,
      radius: LAYOUT2[0],
      baseSpeed: 100 * (1 - 0.06 * up.slow) * (mercy ? 0.9 : 1), v: 100,
      shield: up.shield, shieldMax: up.shield, pipFlash: 0,
      invuln: 0, shake: 0, freeze: 0, dieT: 0, flashT: 0,
      squash: 0, landed: true, dashOff: 0,
      gemsRun: 0, banked: false, revived: false, bonus: 0, score: 0,
      combo: 0, comboT: 0, comboWin: 4 + 0.5 * up.keeper,
      gemChain: 0, gemChainT: 0,
      magnetT: 0, frenzyT: 0, slowT: 0, boostTot: 1, announceT: 0,
      durMul: 1 + 0.1 * up.bdur,
      spawnT: 0.8, boostT: Math.max(7, rnd(12, 18) - up.bfreq),
      ents: [], parts: [], pops: [], trail: [], waves: [],
      stars: Array.from({ length: 46 }, () => ({
        x: rnd(10, 710), y: rnd(10, 1270), s: rnd(0.7, 2.1),
        tw: rnd(0, 6.28), sp: rnd(1.2, 3.2), al: rnd(0.25, 0.75),
      })),
      dust: Array.from({ length: 22 }, () => ({
        r: rnd(60, 350), a: rnd(0, 360), sp: rnd(2, 7) * (Math.random() < 0.5 ? -1 : 1),
        size: rnd(1.2, 2.6), al: rnd(0.06, 0.16),
      })),
      nebula: Array.from({ length: 3 }, () => ({
        x: rnd(120, 600), y: rnd(200, 1000), vx: rnd(-6, 6), vy: rnd(-5, 5), s: rnd(280, 420),
      })),
      scale: 1, tutRuns: sv.runs, tutBoostDone: sv.runs > 0,
      mercy, mercyDone: !mercy,
      mNear: 0, mBoost: 0,
    };

    const resize = () => {
      const r = holder.getBoundingClientRect();
      const dpr = Math.min(2, window.devicePixelRatio || 1);
      canvas.width = Math.max(1, Math.round(r.width * dpr));
      canvas.height = Math.max(1, Math.round(r.height * dpr));
      st.scale = canvas.width / W;
    };
    resize();
    let ro = null;
    if (window.ResizeObserver) { ro = new ResizeObserver(resize); ro.observe(holder); }

    const P = () => PALETTES[saveRef.current.palette] || PALETTES[0];
    const mult = () => Math.min(5, 1 + st.combo);
    const pxy = () => [CX + Math.cos(st.angle * D) * st.radius, CY + Math.sin(st.angle * D) * st.radius];
    const exy = (e) => [CX + Math.cos(e.a * D) * e.rr, CY + Math.sin(e.a * D) * e.rr];
    const glow = (x, y, size, color, alpha) => {
      ctx.globalAlpha = alpha === undefined ? 1 : alpha;
      ctx.drawImage(getGlow(color), x - size / 2, y - size / 2, size, size);
      ctx.globalAlpha = 1;
    };
    const rr = (x, y, w, h, r) => {
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(x, y, w, h, r);
      else {
        ctx.moveTo(x + r, y);
        ctx.arcTo(x + w, y, x + w, y + h, r);
        ctx.arcTo(x + w, y + h, x, y + h, r);
        ctx.arcTo(x, y + h, x, y, r);
        ctx.arcTo(x, y, x + w, y, r);
        ctx.closePath();
      }
    };

    const burst = (x, y, color, n, suck) => {
      for (let i = 0; i < n; i++) {
        const a = rnd(0, 360), sp = rnd(60, 420);
        st.parts.push({ x, y, vx: Math.cos(a * D) * sp, vy: Math.sin(a * D) * sp, life: rnd(0.4, 0.9), c: color, suck: !!suck });
      }
    };
    const pop = (x, y, txt, color) => st.pops.push({ x, y, txt, color, life: 1 });
    const wave = (x, y, r0, r1, life, color, width) => st.waves.push({ x, y, r0, r1, life, life0: life, color, width });

    const spawn = (forceGem, ringOpt, angOpt) => {
      const ring = ringOpt !== undefined ? ringOpt : Math.floor(Math.random() * st.ringCount);
      const a = angOpt !== undefined ? norm(angOpt) : norm(st.angle + rnd(120, 170));
      for (const e of st.ents) if (e.ring === ring && Math.abs(angDiff(e.a, a)) < 20) return;
      const spikeCh = Math.min(0.62, 0.42 + st.time * 0.004);
      const gem = forceGem || (st.frenzyT > 0 ? Math.random() < 0.85 : Math.random() >= spikeCh);
      st.ents.push({ ring, a, kind: gem ? "gem" : "spike", s: 0, rr: st.ringR[ring], prevRel: undefined });
    };

    const spawnBoost = (forceBt) => {
      const pool = ["shield", "magnet", "magnet", "frenzy", "frenzy", "slow", "pulse", "pulse"];
      const bt = forceBt || pool[Math.floor(Math.random() * pool.length)];
      const ring = forceBt ? st.ringIndex : Math.floor(Math.random() * st.ringCount);
      const a = norm(st.angle + rnd(130, 170));
      st.ents.push({ ring, a, kind: "boost", bt, s: 0, rr: st.ringR[ring], prevRel: undefined });
    };

    const applyBoost = (bt, x, y) => {
      const B = BOOSTS[bt];
      st.mBoost++;
      pop(x, y, B.label + "!", B.color);
      burst(x, y, B.color, 16);
      wave(x, y, 20, 90, 0.35, B.color, 4);
      if (bt === "shield") {
        st.shield = Math.min(3, st.shield + 1);
        st.shieldMax = Math.max(st.shieldMax, st.shield);
        beep(600, 0.15, "sine", 0.1, 200);
        return;
      }
      if (bt === "pulse") {
        st.shake = Math.max(st.shake, 0.5);
        const [px0, py0] = pxy();
        wave(px0, py0, 40, 460, 0.5, "#ff9f2e", 8);
        for (let i = st.ents.length - 1; i >= 0; i--) {
          const e = st.ents[i];
          if (e.kind !== "spike") continue;
          const rel = angDiff(e.a, st.angle);
          if (rel > -25 && rel < 140) {
            const [sx, sy] = exy(e);
            burst(sx, sy, P().spike, 10);
            st.bonus += 15;
            st.ents.splice(i, 1);
          }
        }
        beep(120, 0.35, "sawtooth", 0.16, -60); buzz(40);
        return;
      }
      const dur = B.dur * st.durMul;
      st.magnetT = 0; st.frenzyT = 0; st.slowT = 0;
      if (bt === "magnet") st.magnetT = dur;
      if (bt === "frenzy") st.frenzyT = dur;
      if (bt === "slow") st.slowT = dur;
      st.boostTot = dur;
      beep(700, 0.18, "sine", 0.11, 350);
    };

    const nearMiss = (x, y) => {
      st.combo = Math.min(10, st.combo + 1);
      st.comboT = st.comboWin;
      st.mNear++;
      st.freeze = Math.max(st.freeze, 0.03);
      const b = 5 * mult();
      st.bonus += b;
      pop(x, y, "CLOSE! +" + b, "#ffffff");
      burst(x, y, "#ffffff", 5);
      beep(semitone(950, Math.min(10, st.combo)), 0.07, "square", 0.09, 250);
      buzz(12);
    };

    const startDeath = () => {
      st.state = "dying";
      st.freeze = 0.08;
      st.dieT = 0;
      st.shake = 1;
      st.flashT = 0.2;
      const [x, y] = pxy();
      burst(x, y, P().player, 46, true);
      wave(x, y, 20, 380, 0.6, P().player, 7);
      wave(x, y, 30, 340, 0.5, "#ff4d6d", 4);
      wave(x, y, 26, 360, 0.55, "#4dd9ff", 4);
      beep(220, 0.5, "sawtooth", 0.15, -170);
      buzz(60);
    };

    const finalizeDeath = () => {
      st.state = "dead";
      deathsRef.current++;
      if (st.time < 15) quickDeathsRef.current++; else quickDeathsRef.current = 0;
      const gems = Math.floor(st.gemsRun);
      const newBest = st.score > saveRef.current.best;
      setSave((s) => ({ ...s, best: Math.max(s.best, st.score), runs: s.runs + 1 }));
      commitRun({ gems, near: st.mNear, boost: st.mBoost, score: st.score });
      if (newBest) submitRef.current(st.score);
      st.mNear = 0; st.mBoost = 0;
      setX2done(false);
      setDeadInfo({ score: st.score, gemsRun: gems, newBest, canRevive: !st.revived });
    };

    const update = (dt) => {
      const ts = st.slowT > 0 ? 0.6 : 1;
      const wdt = dt * ts;

      st.time += wdt;
      let v = st.baseSpeed + (300 - st.baseSpeed) * (1 - Math.exp(-st.time / 40));
      v *= 1 + 0.08 * Math.sin((Math.PI * 2 * st.time) / 20);
      st.v = v;
      st.angle = norm(st.angle + v * wdt);
      st.radius += (st.ringR[st.ringIndex] - st.radius) * Math.min(1, 14 * wdt);
      st.invuln = Math.max(0, st.invuln - dt);
      st.squash = Math.max(0, st.squash - dt);
      st.dashOff -= v * wdt * 0.9;

      if (!st.landed && Math.abs(st.radius - st.ringR[st.ringIndex]) < 6) {
        st.landed = true;
        const [lx, ly] = pxy();
        wave(lx, ly, 20, 48, 0.25, P().player, 3);
      }

      st.comboT -= dt;
      if (st.comboT <= 0) st.combo = 0;
      st.gemChainT = Math.max(0, st.gemChainT - dt);
      if (st.gemChainT <= 0) st.gemChain = 0;
      st.magnetT = Math.max(0, st.magnetT - dt);
      st.frenzyT = Math.max(0, st.frenzyT - dt);
      st.slowT = Math.max(0, st.slowT - dt);
      st.announceT = Math.max(0, st.announceT - dt);

      if (saveRef.current.orbit3 && st.ringCount === 2 && st.time >= 30) {
        st.ringCount = 3; st.target = LAYOUT3; st.announceT = 1.8; st.flashT = 0.3;
        wave(CX, CY, 60, 520, 0.7, P().gem, 9);
        beep(500, 0.4, "sine", 0.12, 500); beep(90, 0.5, "sine", 0.2, -30); buzz(30);
      }
      for (let i = 0; i < 3; i++) st.ringR[i] += (st.target[i] - st.ringR[i]) * Math.min(1, 2.5 * wdt);
      if (st.ringCount === 3) st.r3a = Math.min(1, st.r3a + 1.5 * dt);

      if (!st.mercyDone && st.time > 1) {
        st.mercyDone = true;
        for (let k = 0; k < 6; k++) spawn(true, 0, st.angle + 120 + k * 15);
      }
      if (!st.tutBoostDone && st.time >= 12) { st.tutBoostDone = true; spawnBoost("magnet"); }

      const [px, py] = pxy();
      st.trail.push({ x: px, y: py });
      if (st.trail.length > 24) st.trail.shift();

      st.spawnT -= wdt * (st.frenzyT > 0 ? 1.5 : 1);
      if (st.spawnT <= 0) { spawn(); st.spawnT = Math.max(0.5, 1.05 - st.time * 0.011); }
      st.boostT -= wdt;
      if (st.boostT <= 0) { spawnBoost(); st.boostT = Math.max(7, rnd(12, 18) - up.bfreq); }

      const arrived = Math.abs(st.radius - st.ringR[st.ringIndex]) < 45;
      for (let i = st.ents.length - 1; i >= 0; i--) {
        const e = st.ents[i];
        e.s = Math.min(1, e.s + 4 * wdt);

        if (st.magnetT > 0 && e.kind === "gem") {
          const rel0 = angDiff(st.angle, e.a);
          if (Math.abs(rel0) < 50) {
            e.a = norm(e.a + Math.sign(rel0) * Math.min(Math.abs(rel0), 260 * dt));
            e.rr += (st.radius - e.rr) * Math.min(1, 8 * dt);
            e.pulled = true;
            if (Math.random() < 0.25) {
              const [gx, gy] = exy(e);
              st.parts.push({ x: gx, y: gy, vx: rnd(-25, 25), vy: rnd(-25, 25), life: 0.35, c: P().gem });
            }
          }
        } else if (!e.pulled) {
          e.rr += (st.ringR[e.ring] - e.rr) * Math.min(1, 6 * wdt);
        }

        const rel = angDiff(e.a, st.angle);

        if (e.kind === "spike" && e.prevRel !== undefined && e.prevRel > 0 && rel <= 0 && st.state === "run") {
          const gap = Math.abs(st.radius - e.rr);
          if (gap > 26 && gap < 90) { const [nx, ny] = exy(e); nearMiss(nx, ny); }
        }
        e.prevRel = rel;

        if (rel < -60 && !e.pulled) { st.ents.splice(i, 1); continue; }

        const [ex, ey] = exy(e);
        const sd = Math.hypot(px - ex, py - ey);

        if (e.kind === "gem" && e.s > 0.5) {
          const er = 15 * (1 + 0.4 * up.magnet);
          const angularHit = e.ring === st.ringIndex && arrived && Math.abs(rel) < ((20 + er) / st.ringR[e.ring]) / D && Math.abs(st.radius - e.rr) < 45;
          if (angularHit || (e.pulled && sd < 30)) {
            const val = 1 * (e.ring === 2 ? 2 : 1) * (st.frenzyT > 0 ? 2 : 1) * mult() * (1 + 0.05 * up.value);
            st.gemsRun += val;
            if (st.combo > 0) st.comboT = st.comboWin;
            st.gemChain = st.gemChainT > 0 ? Math.min(12, st.gemChain + 1) : 0;
            st.gemChainT = 2;
            burst(ex, ey, P().gem, 8);
            if (val >= 2) pop(ex, ey, "+" + Math.round(val), P().gem);
            st.ents.splice(i, 1);
            beep(semitone(700, st.gemChain), 0.08, "sine", 0.1, 250);
            continue;
          }
        }

        if (e.kind === "boost" && e.s > 0.5 && e.ring === st.ringIndex && arrived) {
          if (Math.abs(rel) < (38 / st.ringR[e.ring]) / D) {
            applyBoost(e.bt, ex, ey);
            st.ents.splice(i, 1);
            continue;
          }
        }

        if (e.kind === "spike" && e.s > 0.5 && e.ring === st.ringIndex && arrived && st.invuln <= 0) {
          if (Math.abs(rel) < ((20 + 17) / st.ringR[e.ring]) / D) {
            if (st.shield > 0) {
              st.shield--; st.pipFlash = 0.15;
              st.invuln = 1.2; st.shake = 0.5; st.freeze = Math.max(st.freeze, 0.04);
              burst(ex, ey, P().spike, 14);
              wave(ex, ey, 16, 110, 0.35, "#4dd9ff", 5);
              st.ents.splice(i, 1);
              beep(360, 0.15, "square", 0.1, -120); buzz(30);
            } else { startDeath(); return; }
          }
        }
      }
      st.score = Math.floor(st.time * 12) + Math.floor(st.gemsRun) * 10 + st.bonus;
    };

    const updateFx = (dt) => {
      st.shake = Math.max(0, st.shake - dt * 1.6);
      st.flashT = Math.max(0, st.flashT - dt);
      st.pipFlash = Math.max(0, st.pipFlash - dt);
      for (let i = st.parts.length - 1; i >= 0; i--) {
        const p = st.parts[i]; p.life -= dt;
        if (p.life <= 0) { st.parts.splice(i, 1); continue; }
        if (p.suck) { p.vx += (CX - p.x) * 3.2 * dt; p.vy += (CY - p.y) * 3.2 * dt; }
        p.x += p.vx * dt; p.y += p.vy * dt; p.vx *= 0.98; p.vy *= 0.98;
      }
      for (let i = st.pops.length - 1; i >= 0; i--) {
        const p = st.pops[i]; p.life -= dt * 0.9; p.y -= 50 * dt;
        if (p.life <= 0) st.pops.splice(i, 1);
      }
      for (let i = st.waves.length - 1; i >= 0; i--) {
        const w = st.waves[i]; w.life -= dt;
        if (w.life <= 0) st.waves.splice(i, 1);
      }
      for (const d of st.dust) d.a = norm(d.a + d.sp * dt);
      for (const nb of st.nebula) {
        nb.x += nb.vx * dt; nb.y += nb.vy * dt;
        if (nb.x < 80 || nb.x > 640) nb.vx *= -1;
        if (nb.y < 140 || nb.y > 1100) nb.vy *= -1;
      }
    };

    const gemIcon = (x, y, r, color) => {
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.moveTo(x, y - r); ctx.lineTo(x + r * 0.75, y); ctx.lineTo(x, y + r); ctx.lineTo(x - r * 0.75, y);
      ctx.closePath(); ctx.fill();
    };

    const glint = (x, y, r, rot, color, alpha) => {
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(rot);
      ctx.fillStyle = rgba(color, Math.max(0, alpha));
      for (let k = 0; k < 2; k++) {
        ctx.beginPath();
        ctx.moveTo(0, -r); ctx.lineTo(r * 0.14, 0); ctx.lineTo(0, r); ctx.lineTo(-r * 0.14, 0);
        ctx.closePath(); ctx.fill();
        ctx.rotate(Math.PI / 2);
      }
      ctx.restore();
    };

    const hexIcon = (x, y, r, color, ch, s) => {
      ctx.strokeStyle = color; ctx.lineWidth = 3;
      ctx.beginPath();
      for (let k = 0; k < 6; k++) {
        const a = (k * 60 - 90) * D;
        const hxp = x + Math.cos(a) * r * s, hyp = y + Math.sin(a) * r * s;
        if (k === 0) ctx.moveTo(hxp, hyp); else ctx.lineTo(hxp, hyp);
      }
      ctx.closePath(); ctx.stroke();
      ctx.fillStyle = color;
      ctx.font = "700 " + Math.round(18 * s) + "px ui-monospace, monospace";
      ctx.textAlign = "center"; ctx.textBaseline = "middle";
      ctx.fillText(ch, x, y + 1);
    };

    const trailPath = (mul) => {
      const pts = st.trail;
      if (pts.length < 3) return false;
      const L = [], R = [];
      for (let i = 0; i < pts.length; i++) {
        const a = pts[Math.max(0, i - 1)], b = pts[Math.min(pts.length - 1, i + 1)];
        let dx = b.x - a.x, dy = b.y - a.y;
        const len = Math.hypot(dx, dy) || 1;
        dx /= len; dy /= len;
        const f = i / (pts.length - 1);
        const w = (1.5 + 12 * f) * mul;
        L.push([pts[i].x - dy * w, pts[i].y + dx * w]);
        R.push([pts[i].x + dy * w, pts[i].y - dx * w]);
      }
      ctx.beginPath();
      ctx.moveTo(L[0][0], L[0][1]);
      for (const q of L) ctx.lineTo(q[0], q[1]);
      for (let i = R.length - 1; i >= 0; i--) ctx.lineTo(R[i][0], R[i][1]);
      ctx.closePath();
      return true;
    };

    const render = () => {
      const p = P();
      ctx.setTransform(st.scale, 0, 0, st.scale, 0, 0);

      const sf = Math.max(0, Math.min(1, (st.v - 100) / 200));
      const bgGrad = ctx.createRadialGradient(CX, CY, 60, CX, CY, 760);
      bgGrad.addColorStop(0, brighten(p.bg, 1.7 + sf * 0.9 + 0.12 * Math.sin(st.time * 0.9)));
      bgGrad.addColorStop(1, brighten(p.bg, 0.7));
      ctx.fillStyle = bgGrad;
      ctx.fillRect(0, 0, W, H);

      for (const s of st.stars) {
        const a = s.al * (0.55 + 0.45 * Math.sin(st.time * s.sp + s.tw));
        ctx.fillStyle = `rgba(255,255,255,${a.toFixed(2)})`;
        ctx.fillRect(s.x, s.y, s.s, s.s);
      }

      ctx.save();
      ctx.translate(rnd(-1, 1) * st.shake * 14, rnd(-1, 1) * st.shake * 14);

      ctx.globalCompositeOperation = "lighter";
      for (let k = 0; k < 3; k++) {
        const a = (st.time * 4 + k * 120) * D;
        ctx.fillStyle = rgba(p.player, 0.035);
        ctx.beginPath();
        ctx.moveTo(CX, CY);
        ctx.lineTo(CX + Math.cos(a - 6 * D) * 820, CY + Math.sin(a - 6 * D) * 820);
        ctx.lineTo(CX + Math.cos(a + 6 * D) * 820, CY + Math.sin(a + 6 * D) * 820);
        ctx.closePath(); ctx.fill();
      }
      for (const nb of st.nebula) glow(nb.x, nb.y, nb.s, p.ring, 0.5);
      ctx.globalCompositeOperation = "source-over";

      for (const d of st.dust) {
        ctx.fillStyle = rgba("#ffffff", d.al);
        ctx.beginPath();
        ctx.arc(CX + Math.cos(d.a * D) * d.r, CY + Math.sin(d.a * D) * d.r, d.size, 0, Math.PI * 2);
        ctx.fill();
      }

      if (saveRef.current.orbit3 && st.ringCount === 2 && st.time >= 28 && st.time < 30) {
        const gf = (st.time - 28) / 2;
        ctx.strokeStyle = rgba(p.gem, 0.35 * gf * (0.6 + 0.4 * Math.sin(st.time * 10)));
        ctx.lineWidth = 2.5;
        ctx.setLineDash([14, 18]);
        ctx.beginPath(); ctx.arc(CX, CY, LAYOUT3[2], 0, Math.PI * 2); ctx.stroke();
        ctx.setLineDash([]);
      }

      for (let r = 0; r < st.ringCount; r++) {
        const active = st.ringIndex === r;
        const alpha = r === 2 ? st.r3a : 1;
        const pulse = 1 + 0.06 * Math.sin((Math.PI * 2 * st.time) / 20);
        if (active) {
          ctx.globalCompositeOperation = "lighter";
          ctx.globalAlpha = 0.30 * alpha;
          ctx.strokeStyle = p.player;
          ctx.lineWidth = 18 * pulse;
          ctx.beginPath(); ctx.arc(CX, CY, st.ringR[r], 0, Math.PI * 2); ctx.stroke();
          ctx.globalAlpha = 1;
          ctx.globalCompositeOperation = "source-over";
        }
        ctx.globalAlpha = alpha;
        ctx.strokeStyle = active ? brighten(p.ring, 2.2) : p.ring;
        ctx.lineWidth = (active ? 5.5 : 3) * pulse;
        ctx.beginPath(); ctx.arc(CX, CY, st.ringR[r], 0, Math.PI * 2); ctx.stroke();
        ctx.strokeStyle = rgba(active ? p.player : p.ring, active ? 0.35 : 0.3);
        ctx.lineWidth = 1;
        ctx.beginPath(); ctx.arc(CX, CY, st.ringR[r] - 8, 0, Math.PI * 2); ctx.stroke();
        if (active) {
          ctx.strokeStyle = rgba(p.player, 0.5);
          ctx.lineWidth = 2;
          ctx.setLineDash([16, 30]);
          ctx.lineDashOffset = st.dashOff;
          ctx.beginPath(); ctx.arc(CX, CY, st.ringR[r] + 9, 0, Math.PI * 2); ctx.stroke();
          ctx.setLineDash([]);
          ctx.lineDashOffset = 0;
        }
        if (r === 2 && st.r3a > 0.5) {
          ctx.fillStyle = rgba(p.gem, 0.55 * alpha);
          ctx.font = "700 18px ui-monospace, monospace";
          ctx.textAlign = "center";
          ctx.fillText("x2", CX, CY - st.ringR[2] - 16);
        }
        ctx.globalAlpha = 1;
      }

      ctx.globalCompositeOperation = "lighter";
      for (const w of st.waves) {
        const f = 1 - w.life / w.life0;
        const ease = 1 - Math.pow(1 - f, 2);
        ctx.strokeStyle = rgba(w.color, (1 - f) * 0.9);
        ctx.lineWidth = w.width * (1 - f) + 1.5;
        ctx.beginPath(); ctx.arc(w.x, w.y, w.r0 + (w.r1 - w.r0) * ease, 0, Math.PI * 2); ctx.stroke();
      }
      ctx.globalCompositeOperation = "source-over";

      if (st.state !== "dead" && st.trail.length > 2) {
        const head = st.trail[st.trail.length - 1], tail = st.trail[0];
        ctx.globalCompositeOperation = "lighter";
        if (trailPath(2.1)) { ctx.fillStyle = rgba(p.player, 0.14); ctx.fill(); }
        ctx.globalCompositeOperation = "source-over";
        if (trailPath(1)) {
          const tg = ctx.createLinearGradient(tail.x, tail.y, head.x, head.y);
          tg.addColorStop(0, rgba(p.player, 0));
          tg.addColorStop(0.55, rgba(p.player, 0.38));
          tg.addColorStop(1, rgba(p.player, 0.8));
          ctx.fillStyle = tg;
          ctx.fill();
        }
      }

      ctx.globalCompositeOperation = "lighter";
      for (const e of st.ents) {
        const [x, y] = exy(e);
        if (e.kind === "gem") glow(x, y, 48 * e.s, e.ring === 2 ? "#ffffff" : p.gem, 0.9);
        else if (e.kind === "spike") {
          const danger = e.ring === st.ringIndex && e.prevRel !== undefined && e.prevRel > 0 && e.prevRel < 70;
          const da = danger ? 0.55 + 0.35 * Math.sin(st.time * 12) : 0.5;
          glow(x, y, 56 * e.s * (danger ? 1.15 : 1), p.spike, da);
        } else glow(x, y, (64 + 8 * Math.sin(st.time * 6)) * e.s, BOOSTS[e.bt].color, 0.9);
      }
      ctx.globalCompositeOperation = "source-over";

      for (const e of st.ents) {
        const [x, y] = exy(e);
        if (e.kind === "gem") {
          const a = st.time * 120 * D;
          ctx.fillStyle = e.ring === 2 ? brighten(p.gem, 1.2) : p.gem;
          ctx.beginPath();
          ctx.moveTo(x + Math.cos(a) * 15 * e.s, y + Math.sin(a) * 15 * e.s);
          ctx.lineTo(x + Math.cos(a + Math.PI / 2) * 10 * e.s, y + Math.sin(a + Math.PI / 2) * 10 * e.s);
          ctx.lineTo(x + Math.cos(a + Math.PI) * 15 * e.s, y + Math.sin(a + Math.PI) * 15 * e.s);
          ctx.lineTo(x + Math.cos(a + Math.PI * 1.5) * 10 * e.s, y + Math.sin(a + Math.PI * 1.5) * 10 * e.s);
          ctx.closePath(); ctx.fill();
          ctx.fillStyle = "rgba(255,255,255,0.9)";
          ctx.beginPath(); ctx.arc(x, y, 3.5 * e.s, 0, Math.PI * 2); ctx.fill();
          glint(x, y, 24 * e.s, -st.time * 1.4, "#ffffff", 0.35 + 0.3 * Math.sin(st.time * 4 + x));
        } else if (e.kind === "spike") {
          ctx.fillStyle = p.spike;
          ctx.beginPath(); ctx.arc(x, y, 12 * e.s, 0, Math.PI * 2); ctx.fill();
          const rot = st.time * 40;
          for (let k = 0; k < 8; k++) {
            const a = (rot + k * 45) * D;
            ctx.beginPath();
            ctx.moveTo(x + Math.cos(a) * 24 * e.s, y + Math.sin(a) * 24 * e.s);
            ctx.lineTo(x + Math.cos(a + 16 * D) * 10 * e.s, y + Math.sin(a + 16 * D) * 10 * e.s);
            ctx.lineTo(x + Math.cos(a - 16 * D) * 10 * e.s, y + Math.sin(a - 16 * D) * 10 * e.s);
            ctx.closePath(); ctx.fill();
          }
          ctx.fillStyle = "rgba(0,0,0,0.4)";
          ctx.beginPath(); ctx.arc(x, y, 5.5 * e.s, 0, Math.PI * 2); ctx.fill();
        } else {
          const pulse = 1 + 0.12 * Math.sin(st.time * 6);
          hexIcon(x, y, 22 * pulse, BOOSTS[e.bt].color, BOOSTS[e.bt].ch, e.s);
        }
      }

      if (st.state === "run" && (st.invuln <= 0 || Math.floor(st.time * 10) % 2 === 0)) {
        const [px, py] = pxy();
        ctx.globalCompositeOperation = "lighter";
        glow(px, py, 88, p.player, 0.95);
        glow(px, py, 34, "#ffffff", 0.5);
        if (st.magnetT > 0) glow(px, py, 130 + 10 * Math.sin(st.time * 8), "#c07bff", 0.5);
        ctx.globalCompositeOperation = "source-over";

        if (st.shield > 0) {
          ctx.strokeStyle = rgba("#4dd9ff", 0.8);
          ctx.lineWidth = 2.5;
          ctx.beginPath(); ctx.arc(px, py, 30 + Math.sin(st.time * 5), 0, Math.PI * 2); ctx.stroke();
        }

        const k = st.squash / 0.12;
        ctx.save();
        ctx.translate(px, py);
        ctx.rotate(st.angle * D);
        ctx.scale(1 - 0.22 * k, 1 + 0.16 * k);
        ctx.fillStyle = p.player;
        ctx.beginPath(); ctx.arc(0, 0, 20, 0, Math.PI * 2); ctx.fill();
        ctx.fillStyle = "rgba(255,255,255,0.92)";
        ctx.beginPath(); ctx.arc(-6, -6, 5, 0, Math.PI * 2); ctx.fill();
        ctx.restore();

        const sats = st.combo > 0 ? Math.min(4, mult() - 1) : 0;
        for (let s = 0; s < sats; s++) {
          const sa = (st.time * 260 + s * (360 / sats)) * D;
          const sx = px + Math.cos(sa) * 31, sy = py + Math.sin(sa) * 31;
          ctx.globalCompositeOperation = "lighter";
          glow(sx, sy, 16, p.player, 0.8);
          ctx.globalCompositeOperation = "source-over";
          ctx.fillStyle = "#ffffff";
          ctx.beginPath(); ctx.arc(sx, sy, 2.6, 0, Math.PI * 2); ctx.fill();
        }

        if (st.combo > 0) {
          const frac = Math.max(0, st.comboT / st.comboWin);
          ctx.strokeStyle = rgba(p.player, 0.25);
          ctx.lineWidth = 4;
          ctx.beginPath(); ctx.arc(px, py, 40, 0, Math.PI * 2); ctx.stroke();
          ctx.strokeStyle = p.player;
          ctx.beginPath(); ctx.arc(px, py, 40, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * frac); ctx.stroke();
        }

        if (st.ringCount === 3) {
          const r1 = st.radius + st.dir * 30, r2 = st.radius + st.dir * 46;
          const tx = CX + Math.cos(st.angle * D) * r2, ty = CY + Math.sin(st.angle * D) * r2;
          const b1x = CX + Math.cos((st.angle + 5) * D) * r1, b1y = CY + Math.sin((st.angle + 5) * D) * r1;
          const b2x = CX + Math.cos((st.angle - 5) * D) * r1, b2y = CY + Math.sin((st.angle - 5) * D) * r1;
          ctx.fillStyle = "rgba(255,255,255,0.65)";
          ctx.beginPath(); ctx.moveTo(tx, ty); ctx.lineTo(b1x, b1y); ctx.lineTo(b2x, b2y); ctx.closePath(); ctx.fill();
        }
      }

      ctx.globalCompositeOperation = "lighter";
      for (const q of st.parts) {
        ctx.fillStyle = rgba(q.c, Math.min(1, q.life));
        ctx.beginPath(); ctx.arc(q.x, q.y, 3 + 5 * q.life, 0, Math.PI * 2); ctx.fill();
      }
      ctx.globalCompositeOperation = "source-over";

      for (const q of st.pops) {
        ctx.fillStyle = q.color;
        ctx.globalAlpha = Math.min(1, q.life);
        ctx.font = "700 26px system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.fillText(q.txt, q.x, q.y);
        ctx.globalAlpha = 1;
      }
      ctx.restore();

      const vig = ctx.createRadialGradient(CX, CY, 300, CX, CY, 800);
      vig.addColorStop(0, "rgba(0,0,0,0)");
      vig.addColorStop(1, "rgba(0,0,0,0.42)");
      ctx.fillStyle = vig;
      ctx.fillRect(0, 0, W, H);

      // ------------------------------------------------ HUD
      ctx.textAlign = "center"; ctx.textBaseline = "middle";
      ctx.fillStyle = "#ffffff";
      ctx.font = "700 52px ui-monospace, monospace";
      ctx.fillText(String(st.score), CX, 66);
      gemIcon(44, 64, 14, p.gem);
      ctx.fillStyle = p.gem;
      ctx.font = "700 30px ui-monospace, monospace";
      ctx.textAlign = "left";
      ctx.fillText(String(Math.floor(saveRef.current.gems + (st.banked ? 0 : st.gemsRun))), 68, 66);

      // Піпси щита (фінальний дизайн): 32×20 r6, праворуч, до 3 слотів
      if (st.shieldMax > 0) {
        const slots = Math.min(3, st.shieldMax);
        for (let i = 0; i < slots; i++) {
          const x = W - 36 - (slots - i) * 40, y = 54;
          const full = i < st.shield;
          const flashing = st.pipFlash > 0 && i === st.shield;
          if (full || flashing) {
            ctx.globalCompositeOperation = "lighter";
            glow(x + 16, y + 10, 46, flashing ? "#ffffff" : "#4dd9ff", 0.75);
            ctx.globalCompositeOperation = "source-over";
          }
          ctx.fillStyle = flashing ? "rgba(255,255,255,0.95)" : full ? "#4dd9ff" : "rgba(255,255,255,0.14)";
          rr(x, y, 32, 20, 6);
          ctx.fill();
        }
      }

      if (st.combo > 0) {
        ctx.textAlign = "center";
        ctx.fillStyle = p.player;
        ctx.font = "700 32px ui-monospace, monospace";
        ctx.fillText("COMBO x" + mult(), CX, 116);
      }

      const activeBoost = st.magnetT > 0 ? ["magnet", st.magnetT] : st.frenzyT > 0 ? ["frenzy", st.frenzyT] : st.slowT > 0 ? ["slow", st.slowT] : null;
      if (activeBoost) {
        const [bt, t] = activeBoost;
        const B = BOOSTS[bt];
        hexIcon(654, 120, 18, B.color, B.ch, 1);
        ctx.fillStyle = rgba("#ffffff", 0.2);
        ctx.fillRect(600, 148, 108, 5);
        ctx.fillStyle = B.color;
        ctx.fillRect(600, 148, 108 * Math.max(0, t / st.boostTot), 5);
      }

      if (st.slowT > 0) {
        const vg2 = ctx.createRadialGradient(CX, CY, 320, CX, CY, 780);
        vg2.addColorStop(0, "rgba(160,210,255,0)");
        vg2.addColorStop(1, "rgba(160,210,255,0.26)");
        ctx.fillStyle = vg2;
        ctx.fillRect(0, 0, W, H);
      }
      if (st.frenzyT > 0) {
        const vg3 = ctx.createRadialGradient(CX, CY, 340, CX, CY, 790);
        vg3.addColorStop(0, "rgba(255,213,74,0)");
        vg3.addColorStop(1, "rgba(255,213,74,0.16)");
        ctx.fillStyle = vg3;
        ctx.fillRect(0, 0, W, H);
      }
      if (st.flashT > 0) {
        ctx.fillStyle = `rgba(255,255,255,${(st.flashT * 1.6).toFixed(2)})`;
        ctx.fillRect(0, 0, W, H);
      }

      if (st.announceT > 0) {
        ctx.globalAlpha = Math.min(1, st.announceT);
        ctx.fillStyle = p.gem;
        ctx.font = "700 44px system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.fillText("ORBIT III ONLINE", CX, CY - 20);
        ctx.globalAlpha = 1;
      }

      if (st.state === "run" && st.time < 3.5 && st.tutRuns < 3) {
        const a = 0.55 + 0.45 * Math.sin(st.time * 6);
        ctx.fillStyle = `rgba(255,255,255,${a.toFixed(2)})`;
        ctx.font = "700 28px system-ui, sans-serif";
        ctx.textAlign = "center";
        ctx.fillText("TAP = SWITCH ORBIT", CX, CY + 330 + 58);
      }
    };

    let raf, last = 0;
    const frame = (t) => {
      const dt = Math.min(0.033, (t - last) / 1000 || 0.016);
      last = t;
      if (st.freeze > 0) {
        st.freeze -= dt;
      } else if (st.state === "run") {
        update(dt);
        updateFx(dt);
      } else if (st.state === "dying") {
        st.dieT += dt;
        updateFx(dt * 0.35);
        if (st.dieT > 0.8) finalizeDeath();
      } else {
        updateFx(dt);
      }
      render();
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
    beep(440, 0.1, "sine", 0.08, 220);

    if (pendingBoostRef.current) {
      applyBoost(pendingBoostRef.current, CX, CY);
      pendingBoostRef.current = null;
    }

    engineRef.current = {
      tap: () => {
        if (st.state !== "run" || st.freeze > 0) return;
        let next = st.ringIndex + st.dir;
        if (next > st.ringCount - 1 || next < 0) { st.dir = -st.dir; next = st.ringIndex + st.dir; }
        st.ringIndex = next;
        st.squash = 0.12;
        st.landed = false;
        const [sx, sy] = pxy();
        for (let i = 0; i < 4; i++) st.parts.push({ x: sx, y: sy, vx: rnd(-90, 90), vy: rnd(-90, 90), life: 0.3, c: P().player });
        beep(st.dir > 0 ? 300 : 340, 0.06, "square", 0.07, 200);
        buzz(8);
      },
      bank: (m) => {
        if (!st.banked) {
          const add = Math.round(st.gemsRun * m);
          if (add > 0) setSave((s) => ({ ...s, gems: s.gems + add }));
          st.banked = true;
        }
      },
      revive: () => {
        for (let i = st.ents.length - 1; i >= 0; i--) {
          if (st.ents[i].kind === "spike" && Math.abs(angDiff(st.ents[i].a, st.angle)) < 90) st.ents.splice(i, 1);
        }
        if (st.banked) { st.gemsRun = 0; st.banked = false; }
        st.state = "run"; st.revived = true; st.invuln = 2.2;
        setDeadInfo(null);
        beep(520, 0.25, "sine", 0.12, 420);
      },
    };

    return () => {
      cancelAnimationFrame(raf);
      if (ro) ro.disconnect();
      engineRef.current = null;
    };
  }, [screen, runKey, beep, commitRun]);

  // ---------------------------------------------------------- ДІЇ

  const startRun = () => { setDeadInfo(null); setX2done(false); setRunKey((k) => k + 1); setScreen("game"); };

  const doRestart = () => {
    if (engineRef.current) engineRef.current.bank(1);
    maybeInterstitial(() => { setDeadInfo(null); setX2done(false); setRunKey((k) => k + 1); });
  };

  const toMenu = () => {
    if (engineRef.current) engineRef.current.bank(1);
    maybeInterstitial(() => { setDeadInfo(null); setScreen("menu"); });
  };

  const buyUpgrade = (u) => {
    const lvl = save.upgrades[u.id] || 0;
    const cost = upCost(u, lvl);
    if (lvl >= u.max || save.gems < cost) return;
    beep(700, 0.12, "sine", 0.1, 250);
    setSave((s) => ({ ...s, gems: s.gems - cost, upgrades: { ...s.upgrades, [u.id]: lvl + 1 } }));
  };

  const tapSkin = (i) => {
    const p = PALETTES[i];
    if (save.owned.includes(i)) { setSave((s) => ({ ...s, palette: i })); beep(500, 0.05, "square", 0.06, 0); }
    else if (save.gems >= p.cost) {
      setSave((s) => ({ ...s, gems: s.gems - p.cost, owned: [...s.owned, i], palette: i }));
      flash(p.name);
      beep(700, 0.15, "sine", 0.1, 300);
    }
  };

  const claimMission = (idx) => {
    const m = save.daily.missions[idx];
    if (m.claimed || m.prog < m.goal) return;
    beep(760, 0.18, "sine", 0.12, 300);
    setSave((s) => ({
      ...s, gems: s.gems + m.rew,
      daily: { ...s.daily, missions: s.daily.missions.map((mm, i) => i === idx ? { ...mm, claimed: true } : mm) },
    }));
    flash("+" + m.rew);
  };

  const claimStreak = () => {
    if (!save.daily || save.daily.streakClaimed) return;
    const r = STREAK_REW[save.daily.streak - 1] || 10;
    beep(760, 0.18, "sine", 0.12, 300);
    setSave((s) => ({ ...s, gems: s.gems + r, daily: { ...s.daily, streakClaimed: true } }));
    flash("+" + r);
  };

  const saveName = () => {
    const n = nameDraft.trim().slice(0, 12) || "PLAYER";
    setSave((s) => ({ ...s, name: n }));
    beep(600, 0.08, "sine", 0.08, 150);
    setTimeout(() => {
      if (saveRef.current.best > 0) submitScore(saveRef.current.best).then(() => setLbBump((b) => b + 1));
    }, 50);
  };

  const devReset = () => {
    quickDeathsRef.current = 0; deathsRef.current = 0; lastAdRef.current = 0;
    setSave((s) => ({ ...DEFAULT_SAVE, pid: s.pid, name: s.name, daily: null }));
    setDevArm(false); setDev(false);
    setScreen("menu"); setBoot({ phase: "loading", pct: 0 });
    flash("RESET");
    beep(300, 0.2, "square", 0.08, -100);
  };

  useEffect(() => {
    const onKey = (e) => {
      if (e.code !== "Space" && e.code !== "ArrowUp" && e.code !== "Enter") return;
      if (ad || screen === "ranks") return;
      if (screen === "menu" && boot.phase === "ready") { goMenu(); e.preventDefault(); }
      else if (screen === "menu" && boot.phase === "loading") { e.preventDefault(); }
      else if (screen === "game" && !deadInfo) { engineRef.current && engineRef.current.tap(); e.preventDefault(); }
      else if (screen === "menu" && boot.phase === "menu" && !dev) { startRun(); e.preventDefault(); }
      else if (screen === "game" && deadInfo && e.code === "Enter") { doRestart(); e.preventDefault(); }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  });

  // ---------------------------------------------------------- UI

  const Btn = ({ onClick, children, kind, disabled, small }) => {
    const base = "w-full rounded-2xl font-black tracking-widest transition-transform active:scale-95 disabled:opacity-40 " + (small ? "py-3 px-3 text-sm" : "py-3 px-6 text-lg");
    let style = {};
    if (kind === "primary") style = { background: pal.player, color: pal.bg, boxShadow: `0 0 28px ${rgba(pal.player, 0.4)}` };
    else if (kind === "gem") style = { background: pal.gem, color: pal.bg, boxShadow: `0 0 22px ${rgba(pal.gem, 0.3)}` };
    else style = { background: "rgba(255,255,255,0.08)", color: "#ffffff", border: "1px solid rgba(255,255,255,0.18)" };
    return <button className={base} style={style} onClick={onClick} disabled={disabled}>{children}</button>;
  };

  const Gem = ({ size, color }) => (
    <span className="inline-block" style={{ width: size, height: size, background: color || pal.gem, transform: "rotate(45deg)", borderRadius: 2 }} />
  );

  const BannerSim = () => save.noAds ? null : (
    <div className="w-full flex items-center justify-center shrink-0" style={{ height: 52, background: "rgba(255,255,255,0.06)", borderTop: "1px solid rgba(255,255,255,0.1)" }}>
      <span className="font-mono text-xs" style={{ color: "rgba(255,255,255,0.35)" }}>BANNER 320×50 · симуляція</span>
    </div>
  );

  if (!booted) {
    return (
      <div className="w-full min-h-screen flex items-center justify-center" style={{ background: PALETTES[0].bg }}>
        <div className="text-white/40 font-mono tracking-widest text-sm">LOADING…</div>
      </div>
    );
  }

  const daily = save.daily;
  const myPid = save.pid;
  const menuBg = `radial-gradient(circle at 50% 40%, ${brighten(pal.bg, 2.1)} 0%, ${pal.bg} 52%, ${brighten(pal.bg, 0.6)} 100%)`;

  return (
    <div className="w-full min-h-screen flex items-center justify-center" style={{ background: pal.bg }}>
      <div
        ref={holderRef}
        className="relative w-full overflow-hidden select-none"
        style={{ aspectRatio: "9 / 16", maxHeight: "100vh", maxWidth: "min(100vw, calc(100vh * 0.5625))", touchAction: "none" }}
      >
        <button
          aria-label={muted ? "Увімкнути звук" : "Вимкнути звук"}
          onClick={() => setMuted((m) => !m)}
          className="absolute top-3 right-3 z-30 w-10 h-10 rounded-full flex items-center justify-center font-mono text-sm"
          style={{ background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.7)", border: "1px solid rgba(255,255,255,0.15)" }}
        >
          {muted ? "M" : "♪"}
        </button>

        {screen === "menu" && boot.phase === "menu" && (
          <button
            onClick={() => { setDev(true); setDevArm(false); }}
            className="absolute top-3 left-3 z-30 rounded-full px-3 py-2 font-mono text-xs"
            style={{ background: "rgba(255,255,255,0.06)", color: "rgba(255,255,255,0.45)", border: "1px dashed rgba(255,255,255,0.25)" }}
          >
            DEV
          </button>
        )}

        {/* ------------------------------------------------ ЛОАДЕР + МЕНЮ (морф) */}
        {screen === "menu" && (
          <div
            className="absolute inset-0 flex flex-col"
            style={{ background: menuBg, cursor: boot.phase === "ready" ? "pointer" : "default" }}
            onPointerDown={boot.phase === "ready" ? goMenu : undefined}
          >
            {MENU_STARS.map((s, i) => (
              <span
                key={i}
                className={"absolute rounded-full " + (reduced ? "" : "animate-pulse")}
                style={{ left: `${s.x}%`, top: `${s.y}%`, width: s.s, height: s.s, background: "#ffffff", opacity: 0.6, animationDuration: `${s.d}s` }}
              />
            ))}
            <div className="flex-1 relative overflow-y-auto flex flex-col items-center justify-between px-8 pt-8 pb-3">

              {/* Емблема + титули: у лоадері великі по центру → плавно зменшуються вгору */}
              <div
                className="flex flex-col items-center gap-4"
                style={{
                  transform: boot.phase === "menu" ? "translateY(0px) scale(1)" : "translateY(110px) scale(1.32)",
                  transformOrigin: "50% 0%",
                  transition: "transform 0.85s cubic-bezier(0.22, 1, 0.36, 1)",
                }}
              >
                <div className="relative" style={{ width: 150, height: 150, filter: `drop-shadow(0 0 22px ${rgba(pal.player, 0.55)})` }}>
                  <div className="absolute rounded-full" style={{ inset: 0, border: `4px solid ${pal.ring}` }} />
                  <div className="absolute rounded-full" style={{ inset: 30, border: `4px solid ${pal.ring}` }} />
                  <div className={"absolute inset-0 " + (reduced ? "" : "animate-spin")} style={{ animationDuration: "7s" }}>
                    <div className="absolute rounded-full" style={{ width: 22, height: 22, background: pal.player, top: -11, left: "50%", marginLeft: -11, boxShadow: `0 0 18px ${pal.player}` }} />
                  </div>
                  <div className={"absolute " + (reduced ? "" : "animate-spin")} style={{ inset: 30, animationDuration: "10s", animationDirection: "reverse" }}>
                    <div className="absolute" style={{ width: 15, height: 15, background: pal.gem, transform: "rotate(45deg)", borderRadius: 3, top: -8, left: "50%", marginLeft: -8, boxShadow: `0 0 14px ${pal.gem}` }} />
                  </div>
                </div>
                <div className="text-center leading-none">
                  <div className="font-black text-4xl" style={{ color: pal.player, letterSpacing: "0.3em", marginRight: "-0.3em", textShadow: `0 0 30px ${rgba(pal.player, 0.8)}, 0 0 8px ${rgba(pal.player, 0.6)}` }}>ORBIT</div>
                  <div className="font-black text-4xl mt-1" style={{ color: pal.gem, letterSpacing: "0.3em", marginRight: "-0.3em", textShadow: `0 0 30px ${rgba(pal.gem, 0.7)}, 0 0 8px ${rgba(pal.gem, 0.5)}` }}>DASH</div>
                </div>
              </div>

              {/* Меню: статистика + кнопки — випливають знизу після тапу */}
              <div
                className="w-full flex flex-col items-center gap-2 mt-4"
                style={{
                  maxWidth: 300,
                  opacity: boot.phase === "menu" ? 1 : 0,
                  transform: boot.phase === "menu" ? "translateY(0px)" : "translateY(70px)",
                  pointerEvents: boot.phase === "menu" ? "auto" : "none",
                  transition: "transform 0.7s cubic-bezier(0.22, 1, 0.36, 1) 0.12s, opacity 0.5s ease 0.12s",
                }}
              >
                <div className="flex items-center gap-5 font-mono text-sm mb-1" style={{ color: "rgba(255,255,255,0.65)" }}>
                  <span>BEST {save.best}</span>
                  <span className="flex items-center gap-2" style={{ color: pal.gem }}><Gem size={10} /> {save.gems}</span>
                  {daily && <span style={{ color: "rgba(255,255,255,0.45)" }}>DAY {daily.streak}</span>}
                </div>
                <Btn kind="primary" onClick={startRun}>PLAY</Btn>
                <Btn small onClick={() => showRewarded("MAGNET START", () => { pendingBoostRef.current = "magnet"; startRun(); })}>
                  PLAY + MAGNET · AD
                </Btn>
                <div className="w-full grid grid-cols-3 gap-2">
                  <Btn small onClick={() => setScreen("shop")}>SHOP</Btn>
                  <div className="relative w-full">
                    <Btn small onClick={() => setScreen("missions")}>DAILY</Btn>
                    {claimable && <span className="absolute rounded-full" style={{ width: 10, height: 10, background: pal.gem, top: 6, right: 6, boxShadow: `0 0 8px ${pal.gem}` }} />}
                  </div>
                  <Btn small onClick={() => setScreen("ranks")}>RANKS</Btn>
                </div>
                <Btn kind="gem" small onClick={() => showRewarded("+25 GEMS", () => { setSave((s) => ({ ...s, gems: s.gems + 25 })); flash("+25"); })}>
                  +25 GEMS · AD
                </Btn>
                <div className="text-center text-xs mt-1" style={{ color: "rgba(255,255,255,0.35)" }}>
                  Тап або пробіл — зміна орбіти
                  {!storageOk && <div className="mt-1">Збереження недоступне — прогрес лише в цій сесії</div>}
                </div>
              </div>

              {/* Слот лоадера: LOADING NN% → TAP TO START, гасне після входу в меню */}
              <div
                className="absolute left-0 right-0 flex flex-col items-center"
                style={{ bottom: 22, pointerEvents: "none", opacity: boot.phase === "menu" ? 0 : 1, transition: "opacity 0.4s ease" }}
              >
                <div className="relative w-full" style={{ height: 96 }}>
                  <div
                    className="absolute inset-0 flex flex-col items-center justify-center gap-3"
                    style={{ opacity: boot.phase === "loading" ? 1 : 0, transition: "opacity 0.35s ease" }}
                  >
                    <div className="font-mono tracking-widest" style={{ fontSize: 10, color: "rgba(255,255,255,0.35)", letterSpacing: 3 }}>LOADING</div>
                    <div className="font-mono font-black text-white" style={{ fontSize: 26, textShadow: `0 0 18px ${rgba(pal.player, 0.45)}` }}>{Math.floor(boot.pct)}%</div>
                    <div className="rounded-full overflow-hidden" style={{ width: 220, height: 4, background: "rgba(255,255,255,0.1)" }}>
                      <div className="h-full rounded-full" style={{ width: `${boot.pct}%`, background: `linear-gradient(90deg, ${pal.player}, ${pal.gem})`, boxShadow: `0 0 10px ${rgba(pal.player, 0.6)}`, transition: "width 0.12s linear" }} />
                    </div>
                  </div>
                  <div
                    className={"absolute inset-0 flex items-center justify-center " + (reduced ? "" : "animate-pulse")}
                    style={{ opacity: boot.phase === "ready" ? 1 : 0, transition: "opacity 0.45s ease 0.1s" }}
                  >
                    <span className="font-bold" style={{ fontSize: 14, color: "rgba(255,255,255,0.55)", letterSpacing: 4 }}>TAP TO START</span>
                  </div>
                </div>
                <div className="font-mono mt-1" style={{ fontSize: 10, color: "rgba(255,255,255,0.22)" }}>com.lewydo.orbitdash</div>
              </div>
            </div>

            {/* Банер під'їжджає разом з меню */}
            <div style={{ transform: boot.phase === "menu" ? "translateY(0px)" : "translateY(60px)", opacity: boot.phase === "menu" ? 1 : 0, transition: "transform 0.6s cubic-bezier(0.22, 1, 0.36, 1) 0.22s, opacity 0.4s ease 0.22s" }}>
              <BannerSim />
            </div>
          </div>
        )}

        {/* ------------------------------------------------ ГРА */}
        {screen === "game" && (
          <canvas
            ref={canvasRef}
            className="absolute inset-0 w-full h-full block"
            onPointerDown={(e) => { e.preventDefault(); engineRef.current && engineRef.current.tap(); }}
          />
        )}

        {screen === "game" && deadInfo && !ad && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-2 px-10 overflow-y-auto py-6 backdrop-blur-sm" style={{ background: "rgba(4,5,11,0.7)" }}>
            <div className="font-black text-3xl tracking-widest" style={{ color: pal.spike, textShadow: `0 0 26px ${rgba(pal.spike, 0.7)}` }}>GAME OVER</div>
            {deadInfo.newBest && (
              <div className="font-mono text-xs tracking-widest px-3 py-1 rounded-full" style={{ background: rgba(pal.gem, 0.15), color: pal.gem }}>NEW BEST → LEADERBOARD</div>
            )}
            <div className="font-mono font-bold text-white" style={{ fontSize: 48, lineHeight: 1, textShadow: "0 0 24px rgba(255,255,255,0.35)" }}>{deadInfo.score}</div>
            <div className="font-mono text-xs" style={{ color: "rgba(255,255,255,0.55)" }}>BEST {save.best}</div>
            <div className="flex items-center gap-2 font-mono mb-2" style={{ color: pal.gem }}>
              <Gem size={11} /> +{x2done ? deadInfo.gemsRun * 2 : deadInfo.gemsRun}{x2done ? " · x2" : ""}
            </div>
            <div className="w-full flex flex-col gap-2" style={{ maxWidth: 280 }}>
              {deadInfo.canRevive && (
                <Btn kind="primary" small onClick={() => showRewarded("REVIVE", () => engineRef.current && engineRef.current.revive())}>REVIVE · AD</Btn>
              )}
              {deadInfo.gemsRun > 0 && !x2done && (
                <Btn kind="gem" small onClick={() => showRewarded("x2 GEMS", () => { engineRef.current && engineRef.current.bank(2); setX2done(true); })}>x2 GEMS · AD</Btn>
              )}
              <Btn small onClick={doRestart}>RESTART</Btn>
              <Btn small onClick={toMenu}>MENU</Btn>
            </div>
            {!save.noAds && (
              <div className="w-full rounded-2xl flex flex-col items-center justify-center mt-3" style={{ maxWidth: 280, height: 110, background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.12)" }}>
                <span className="font-mono text-xs" style={{ color: "rgba(255,255,255,0.4)" }}>MREC 300×250 · симуляція</span>
                <span className="font-mono mt-1" style={{ color: "rgba(255,255,255,0.25)", fontSize: 10 }}>eCPM у 3–8 разів вище за банер</span>
              </div>
            )}
          </div>
        )}

        {/* ------------------------------------------------ МАГАЗИН */}
        {screen === "shop" && (
          <div className="absolute inset-0 flex flex-col">
            <div className="flex-1 overflow-y-auto px-5 py-5">
              <div className="flex items-center justify-between mb-4 mt-1">
                <button onClick={() => setScreen("menu")} className="rounded-xl px-4 py-2 font-bold tracking-widest text-sm" style={{ background: "rgba(255,255,255,0.08)", color: "#fff" }}>← BACK</button>
                <div className="font-black tracking-widest text-lg text-white">SHOP</div>
                <div className="flex items-center gap-2 font-mono" style={{ color: pal.gem }}><Gem size={11} /> {save.gems}</div>
              </div>

              <div className="rounded-2xl p-4 mb-3 flex items-center justify-between gap-3" style={{ background: rgba(pal.gem, 0.08), border: `1px solid ${rgba(pal.gem, 0.3)}` }}>
                <div>
                  <div className="font-black tracking-widest text-sm" style={{ color: pal.gem }}>ORBIT III</div>
                  <div className="text-xs mt-1" style={{ color: "rgba(255,255,255,0.55)" }}>Третє кільце з 30-ї секунди. Геми на ньому ×2.</div>
                </div>
                <button
                  onClick={() => { if (!save.orbit3 && save.gems >= ORBIT3_COST) { beep(760, 0.2, "sine", 0.12, 350); setSave((s) => ({ ...s, gems: s.gems - ORBIT3_COST, orbit3: true })); } }}
                  disabled={save.orbit3 || save.gems < ORBIT3_COST}
                  className="rounded-xl px-4 py-3 font-black tracking-wider text-sm shrink-0 transition-transform active:scale-95 disabled:opacity-40"
                  style={save.orbit3 ? { background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.5)" } : { background: pal.gem, color: pal.bg }}
                >
                  {save.orbit3 ? "OWNED" : `${ORBIT3_COST} ◆`}
                </button>
              </div>

              <div className="flex flex-col gap-3">
                {UPGRADES.map((u) => {
                  const lvl = save.upgrades[u.id] || 0;
                  const maxed = lvl >= u.max;
                  const cost = upCost(u, lvl);
                  const can = !maxed && save.gems >= cost;
                  return (
                    <div key={u.id} className="rounded-2xl p-4 flex items-center justify-between gap-3" style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)" }}>
                      <div>
                        <div className="font-black tracking-widest text-white text-sm">{u.name}</div>
                        <div className="text-xs mt-1" style={{ color: "rgba(255,255,255,0.45)" }}>{u.desc}</div>
                        <div className="flex gap-1 mt-2 flex-wrap">
                          {Array.from({ length: u.max }).map((_, i) => (
                            <span key={i} className="rounded-sm" style={{ width: 12, height: 7, background: i < lvl ? pal.player : "rgba(255,255,255,0.12)" }} />
                          ))}
                        </div>
                      </div>
                      <button
                        onClick={() => buyUpgrade(u)}
                        disabled={!can}
                        className="rounded-xl px-4 py-3 font-black tracking-wider text-sm shrink-0 transition-transform active:scale-95 disabled:opacity-40"
                        style={maxed ? { background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.5)" } : { background: pal.player, color: pal.bg }}
                      >
                        {maxed ? "MAX" : `${cost} ◆`}
                      </button>
                    </div>
                  );
                })}
              </div>

              <div className="font-black tracking-widest text-white text-sm mt-6 mb-3">SKINS</div>
              <div className="grid grid-cols-4 gap-3">
                {PALETTES.map((p, i) => {
                  const owned = save.owned.includes(i);
                  const active = save.palette === i;
                  return (
                    <button key={p.name} onClick={() => tapSkin(i)} className="flex flex-col items-center gap-1 transition-transform active:scale-95">
                      <div className="w-full aspect-square rounded-2xl flex items-center justify-center" style={{ background: p.bg, border: active ? "3px solid #ffffff" : `2px solid ${p.ring}`, boxShadow: active ? `0 0 18px ${rgba(p.player, 0.65)}` : "none" }}>
                        <div className="rounded-full flex items-start justify-center" style={{ width: 34, height: 34, border: `3px solid ${p.ring}` }}>
                          <div className="rounded-full" style={{ width: 11, height: 11, background: p.player, marginTop: -5, boxShadow: `0 0 10px ${p.player}` }} />
                        </div>
                      </div>
                      <div className="font-mono" style={{ fontSize: 10, color: "rgba(255,255,255,0.6)" }}>{p.name}</div>
                      <div className="font-mono text-xs" style={{ color: active ? "#ffffff" : "rgba(255,255,255,0.5)" }}>
                        {active ? "ON" : owned ? "USE" : `${p.cost} ◆`}
                      </div>
                    </button>
                  );
                })}
              </div>

              <div className="rounded-2xl p-4 mt-6 mb-2 flex items-center justify-between gap-3" style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)" }}>
                <div>
                  <div className="font-black tracking-widest text-white text-sm">REMOVE ADS</div>
                  <div className="text-xs mt-1" style={{ color: "rgba(255,255,255,0.45)" }}>Прибирає банер, MREC та interstitial. Rewarded лишається.</div>
                </div>
                <button
                  onClick={() => { if (!save.noAds) { beep(760, 0.2, "sine", 0.12, 350); setSave((s) => ({ ...s, noAds: true })); flash("ADS OFF"); } }}
                  disabled={save.noAds}
                  className="rounded-xl px-4 py-3 font-black tracking-wider text-sm shrink-0 transition-transform active:scale-95 disabled:opacity-40"
                  style={save.noAds ? { background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.5)" } : { background: "#ffffff", color: "#111" }}
                >
                  {save.noAds ? "OWNED" : "$3.99 · SIM"}
                </button>
              </div>
            </div>
            <BannerSim />
          </div>
        )}

        {/* ------------------------------------------------ МІСІЇ */}
        {screen === "missions" && daily && (
          <div className="absolute inset-0 flex flex-col px-5 py-5 overflow-y-auto">
            <div className="flex items-center justify-between mb-5 mt-1">
              <button onClick={() => setScreen("menu")} className="rounded-xl px-4 py-2 font-bold tracking-widest text-sm" style={{ background: "rgba(255,255,255,0.08)", color: "#fff" }}>← BACK</button>
              <div className="font-black tracking-widest text-lg text-white">MISSIONS</div>
              <div className="flex items-center gap-2 font-mono" style={{ color: pal.gem }}><Gem size={11} /> {save.gems}</div>
            </div>

            <div className="rounded-2xl p-4 mb-4" style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)" }}>
              <div className="font-black tracking-widest text-white text-sm mb-3">DAILY STREAK</div>
              <div className="flex items-center justify-between mb-3">
                {STREAK_REW.map((r, i) => {
                  const day = i + 1;
                  const reached = daily.streak >= day;
                  const isToday = daily.streak === day;
                  return (
                    <div key={i} className="flex flex-col items-center gap-1">
                      <div
                        className="rounded-full flex items-center justify-center font-mono text-xs"
                        style={{
                          width: 34, height: 34,
                          background: reached ? rgba(pal.gem, isToday ? 1 : 0.35) : "rgba(255,255,255,0.08)",
                          color: reached && isToday ? pal.bg : "rgba(255,255,255,0.6)",
                          border: isToday ? "2px solid #ffffff" : "2px solid transparent",
                        }}
                      >
                        {day}
                      </div>
                      <span className="font-mono" style={{ fontSize: 9, color: "rgba(255,255,255,0.4)" }}>+{r}</span>
                    </div>
                  );
                })}
              </div>
              <Btn kind="gem" small onClick={claimStreak} disabled={daily.streakClaimed}>
                {daily.streakClaimed ? "CLAIMED TODAY" : `CLAIM DAY ${daily.streak} · +${STREAK_REW[daily.streak - 1]} ◆`}
              </Btn>
            </div>

            <div className="flex flex-col gap-3">
              {daily.missions.map((m, i) => {
                const t = MISSION_TYPES[m.type];
                const done = m.prog >= m.goal;
                return (
                  <div key={i} className="rounded-2xl p-4" style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)" }}>
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex-1">
                        <div className="font-bold text-white text-sm">{t.name(m.goal)}</div>
                        <div className="w-full rounded-full mt-2 overflow-hidden" style={{ height: 6, background: "rgba(255,255,255,0.1)" }}>
                          <div className="h-full rounded-full" style={{ width: `${Math.min(100, (m.prog / m.goal) * 100)}%`, background: done ? pal.gem : pal.player }} />
                        </div>
                        <div className="font-mono text-xs mt-1" style={{ color: "rgba(255,255,255,0.45)" }}>{Math.min(m.prog, m.goal)} / {m.goal}</div>
                      </div>
                      <button
                        onClick={() => claimMission(i)}
                        disabled={!done || m.claimed}
                        className="rounded-xl px-4 py-3 font-black tracking-wider text-sm shrink-0 transition-transform active:scale-95 disabled:opacity-40"
                        style={m.claimed ? { background: "rgba(255,255,255,0.08)", color: "rgba(255,255,255,0.5)" } : { background: pal.gem, color: pal.bg }}
                      >
                        {m.claimed ? "DONE" : `+${m.rew} ◆`}
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="text-xs text-center mt-4" style={{ color: "rgba(255,255,255,0.35)" }}>Місії оновлюються щодня. Стрік скидається, якщо пропустити день.</div>
          </div>
        )}

        {/* ------------------------------------------------ ЛІДЕРБОРД */}
        {screen === "ranks" && (
          <div className="absolute inset-0 flex flex-col px-5 py-5 overflow-y-auto">
            <div className="flex items-center justify-between mb-4 mt-1">
              <button onClick={() => setScreen("menu")} className="rounded-xl px-4 py-2 font-bold tracking-widest text-sm" style={{ background: "rgba(255,255,255,0.08)", color: "#fff" }}>← BACK</button>
              <div className="font-black tracking-widest text-lg text-white">RANKS</div>
              <button onClick={() => setLbBump((b) => b + 1)} className="rounded-xl px-4 py-2 font-bold tracking-widest text-sm" style={{ background: "rgba(255,255,255,0.08)", color: "#fff" }}>↻</button>
            </div>

            <div className="rounded-2xl p-4 mb-4" style={{ background: "rgba(255,255,255,0.05)", border: "1px solid rgba(255,255,255,0.08)" }}>
              <div className="font-mono text-xs mb-2" style={{ color: "rgba(255,255,255,0.5)" }}>YOUR NAME · BEST {save.best}</div>
              <div className="flex gap-2">
                <input
                  value={nameDraft}
                  onChange={(e) => setNameDraft(e.target.value.slice(0, 12))}
                  maxLength={12}
                  className="flex-1 rounded-xl px-3 py-3 font-mono text-sm text-white outline-none"
                  style={{ background: "rgba(255,255,255,0.08)", border: "1px solid rgba(255,255,255,0.15)" }}
                />
                <button onClick={saveName} className="rounded-xl px-4 py-3 font-black tracking-wider text-sm" style={{ background: pal.player, color: pal.bg }}>SAVE</button>
              </div>
              <div className="text-xs mt-2" style={{ color: "rgba(255,255,255,0.35)" }}>Нік і рекорд видно всім гравцям цього прототипу. У релізі — Google Play Games.</div>
            </div>

            {lb.loading && <div className="text-center font-mono text-sm py-8" style={{ color: "rgba(255,255,255,0.4)" }}>LOADING…</div>}
            {lb.err && <div className="text-center font-mono text-sm py-8" style={{ color: "rgba(255,255,255,0.4)" }}>Лідерборд недоступний у цьому середовищі</div>}
            {lb.rows && lb.rows.length === 0 && <div className="text-center font-mono text-sm py-8" style={{ color: "rgba(255,255,255,0.4)" }}>Поки порожньо — зіграй ран і будь першим</div>}

            {lb.rows && lb.rows.length > 0 && (
              <div className="flex flex-col gap-2">
                {lb.rows.map((r, i) => {
                  const me = r.pid === myPid;
                  const medal = i === 0 ? pal.gem : i === 1 ? "#c8d6e5" : i === 2 ? "#cd7f32" : null;
                  return (
                    <div key={r.pid} className="rounded-xl px-4 py-3 flex items-center gap-3" style={{ background: me ? rgba(pal.player, 0.12) : "rgba(255,255,255,0.05)", border: me ? `1px solid ${rgba(pal.player, 0.5)}` : "1px solid rgba(255,255,255,0.08)" }}>
                      <div className="font-mono font-bold text-sm w-8 text-center" style={{ color: medal || "rgba(255,255,255,0.5)" }}>{i + 1}</div>
                      <div className="flex-1 font-bold text-white text-sm truncate">{r.n}{me ? " · YOU" : ""}</div>
                      <div className="font-mono font-bold" style={{ color: pal.gem }}>{r.s}</div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* ------------------------------------------------ DEV */}
        {dev && (
          <div className="absolute inset-0 z-40 flex flex-col items-center justify-center gap-3 px-10" style={{ background: "rgba(4,5,11,0.9)" }}>
            <div className="font-mono text-xs tracking-widest mb-2" style={{ color: "rgba(255,255,255,0.4)" }}>DEV PANEL · прибрати в релізі</div>
            <div className="w-full flex flex-col gap-2" style={{ maxWidth: 280 }}>
              <Btn kind="gem" small onClick={() => { setSave((s) => ({ ...s, gems: s.gems + 10000 })); flash("+10000"); beep(760, 0.15, "sine", 0.1, 300); }}>
                +10 000 ◆
              </Btn>
              <Btn small onClick={() => { setDev(false); setDevArm(false); setDeadInfo(null); setScreen("menu"); setBoot({ phase: "loading", pct: 0 }); beep(420, 0.1, "sine", 0.08, 180); }}>
                ⟳ RESTART APP · з лоадера
              </Btn>
              <button
                onClick={() => { if (devArm) devReset(); else setDevArm(true); }}
                className="w-full rounded-2xl py-3 px-4 font-black tracking-widest text-sm transition-transform active:scale-95"
                style={{ background: devArm ? pal.spike : "rgba(255,255,255,0.08)", color: devArm ? "#fff" : "rgba(255,255,255,0.8)", border: `1px solid ${devArm ? pal.spike : "rgba(255,255,255,0.2)"}` }}
              >
                {devArm ? "ТАП ЩЕ РАЗ — ПІДТВЕРДИТИ СКИДАННЯ" : "RESET SAVE (покупки, реклама, прогрес)"}
              </button>
              <Btn small onClick={() => { setDev(false); setDevArm(false); }}>CLOSE</Btn>
            </div>
          </div>
        )}

        {/* ------------------------------------------------ РЕКЛАМА (симуляція) */}
        {ad && (
          <div className="absolute inset-0 z-40 flex flex-col items-center justify-center gap-6 px-10" style={{ background: "#05060c" }}>
            <div className="font-mono text-xs tracking-widest" style={{ color: "rgba(255,255,255,0.4)" }}>SIMULATED AD</div>
            <div className="font-black text-2xl tracking-widest text-white text-center">{ad.label}</div>
            <div className="w-full rounded-full overflow-hidden" style={{ maxWidth: 260, height: 6, background: "rgba(255,255,255,0.1)" }}>
              <div className="h-full rounded-full" style={{ width: adFill ? "100%" : "0%", background: pal.gem, transition: "width 1.5s linear" }} />
            </div>
            <div className="text-xs text-center" style={{ color: "rgba(255,255,255,0.4)" }}>
              У релізі тут {ad.kind === "inter" ? "interstitial" : "rewarded-відео"} (AdMob / AppLovin MAX)
            </div>
          </div>
        )}

        {toast && (
          <div className="absolute left-0 right-0 z-30 flex justify-center" style={{ top: 88 }}>
            <div className="font-mono font-bold px-4 py-2 rounded-full flex items-center gap-2" style={{ background: rgba(pal.gem, 0.15), color: pal.gem }}>
              <Gem size={10} /> {toast}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}