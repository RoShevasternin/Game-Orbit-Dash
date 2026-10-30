// ─────────────────────────────────────────────────────────────────────────────
// «Знайомся: Бліп» — живий Бліп на сайті. Малює його САМ КОД МУЛЬТИКА (копія в site/src/blip/; мультик — у git-історії, було market/content/cartoon/ep01-first-light/js:
// util.js + draw.js → assets/js/blip-art.mjs, без змін), тож це той самий герой, що в серіях, а не перемальований.
//   Спокій: сидить на дузі орбіти, серце-світло б'ється в такт колисковій (104 BPM, як FIRST LIGHT), кліпає, стежить очима
//   за курсором. Тап — стрибок, «бліп!» (голос — синтез на сторінці, без файлів) і ноти. Настрої — моменти гри з BIBLE.md:
//   комбо (супутники, як на м'ячі гри), шип попереду, корона, ой (шип — і холод), пауза (не Тиша — він тримає подих).
// [HALLOWEEN] у сезон (клас .halloween на <html>, вікно 29.10–30.11 — те саме, що декор сторінки) Бліп у костюмі: капелюх відьми
//   (фігура гри HAT_D, як на «O» в заголовку), друг-кажанчик ширяє поруч, гарбуз-ліхтар на орбіті кидає теплий свічковий відсвіт.
//   Лише костюм: той самий Бліп, ті самі обличчя й настрої (BIBLE.md). Усе — на полотні (жодного DOM щокадру); «менше руху» — стоїть.
//   Поза сезоном — без змін.
// page.js вантажить модуль, коли до розділу догорнули; рух — лише поки видно; «менше руху» — без стрибків і погойдування.
// ─────────────────────────────────────────────────────────────────────────────
import { blip, pose, mixPose, spike, gem, crown, twinkle, glow, ring } from "./blip-art.mjs";

const BPM = 104, BEAT = 60 / BPM;
const MOODS = {
  joy: { face: "joy", rim: "#FFD27A" },
  focus: { face: "focus", rim: "#FF4D6D" },
  wow: { face: "starry", rim: "#FFD27A" },
  ouch: { face: "ouch", rim: "#FF4D6D" },
  calm: { face: "sleep", rim: "#9FD3FF" },
};
const clamp = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x));
// [HALLOWEEN] фігури сезону — ті самі, що в грі й на сторінці (prototype/haunt.mjs → OD.haunt)
function costume() {
  const H = window.OD && window.OD.haunt;
  if (!H || !document.documentElement.classList.contains("halloween") || typeof Path2D === "undefined") return null;
  const P = (d) => new Path2D(d);
  return { hat: P(H.hat), band: P(H.band), batB: P(H.batB), batW: P(H.batW), pump: P(H.pump), face: P(H.face), stem: P(H.stem), ribs: P(H.ribs) };
}
const flick = (t) => 0.78 + 0.12 * Math.sin(t * 9.3) + 0.07 * Math.sin(t * 23.1 + 1.3) + 0.03 * Math.sin(t * 41.7);   // полум'я свічки
const ease = (k) => 1 - Math.pow(1 - clamp(k), 3);

export function mount({ root, reduced }) {
  const box = root.querySelector("#blipCv"), cv = box.querySelector("canvas"), ctx = cv.getContext("2d");
  const hit = root.querySelector("#blipHit"), moods = [...root.querySelectorAll(".mood")];
  const st = { mood: null, face: pose("smile"), from: pose("smile"), faceAt: 0, look: [0, 0], want: [0, 0], jumpAt: -9, notes: [], blinkAt: 2, vis: true,
    t0: performance.now(), lastPointer: -9, shake: 0 };
  let ac = null;
  const HW = costume();
  const now = () => (performance.now() - st.t0) / 1000;
  function setFace(name) { st.from = curFace(now()); st.face = pose(name); st.faceAt = now(); }
  function curFace(t) { const k = clamp((t - st.faceAt) / 0.22); return k >= 1 ? st.face : mixPose(st.from, st.face, k * k * (3 - 2 * k)); }

  // голос Бліпа — «бліп!»: два короткі синусові біпи вгору (як beep(f, dur, wave, vol, slide) гри), лише з дотику
  function beep() {
    try {
      ac = ac || new (window.AudioContext || window.webkitAudioContext)();
      if (ac.state === "suspended") ac.resume();
      [[880, 1500, 0], [1320, 1980, 0.1]].forEach(([f0, f1, d]) => {
        const o = ac.createOscillator(), g = ac.createGain(), t = ac.currentTime + d;
        o.type = "sine"; o.frequency.setValueAtTime(f0, t); o.frequency.exponentialRampToValueAtTime(f1, t + 0.07);
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.12, t + 0.012); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.11);
        o.connect(g).connect(ac.destination); o.start(t); o.stop(t + 0.13);
      });
    } catch (e) { /* без звуку — і так гарно */ }
  }
  function note(ctx, x, y, s, a) {                                   // восьма нота — свій шлях (у шрифті ♪ немає)
    ctx.save(); ctx.globalAlpha = a; ctx.fillStyle = "#EFFFFF"; ctx.strokeStyle = "#EFFFFF"; ctx.lineWidth = s * 0.14;
    ctx.beginPath(); ctx.ellipse(x, y, s * 0.36, s * 0.26, -0.4, 0, Math.PI * 2); ctx.fill();
    ctx.beginPath(); ctx.moveTo(x + s * 0.32, y - s * 0.06); ctx.lineTo(x + s * 0.32, y - s * 1.1); ctx.quadraticCurveTo(x + s * 0.7, y - s * 0.85, x + s * 0.72, y - s * 0.45); ctx.stroke();
    ctx.restore();
    glow(ctx, x, y, s * 1.6, "#6FE7FA", 0.35 * a);
  }
  function tap() {
    const t = now();
    if (!reduced) st.jumpAt = t;
    beep();
    for (let i = 0; i < 3; i++) st.notes.push({ t: t + i * 0.08, x: (i - 1) * 0.5 + (Math.random() - 0.5) * 0.3, s: 0.8 + Math.random() * 0.4 });
    if (!st.mood) { setFace("joy"); setTimeout(() => { if (!st.mood) setFace("smile"); }, 900); }
    if (reduced) draw(now());
  }
  hit.addEventListener("click", tap);
  moods.forEach((b) => b.addEventListener("click", () => {
    const m = st.mood === b.dataset.mood ? null : b.dataset.mood;
    st.mood = m;
    moods.forEach((x) => x.setAttribute("aria-pressed", String(x.dataset.mood === m)));
    setFace(m ? MOODS[m].face : "smile");
    if (m === "ouch") st.shake = now();
    if (reduced) draw(now());
  }));
  box.addEventListener("pointermove", (e) => {
    const r = box.getBoundingClientRect();
    st.want = [clamp(((e.clientX - r.left) / r.width - 0.5) * 2.4, -1, 1), clamp(((e.clientY - r.top) / r.height - 0.5) * 2.4, -1, 1)];
    st.lastPointer = now();
  });
  addEventListener("pointermove", (e) => {                         // курсор будь-де на сторінці — Бліп дивиться на нього
    const r = box.getBoundingClientRect();
    if (e.clientY < r.top - 400 || e.clientY > r.bottom + 400) return;
    st.want = [clamp((e.clientX - (r.left + r.width / 2)) / (r.width * 0.8), -1, 1), clamp((e.clientY - (r.top + r.height / 2)) / (r.height * 0.8), -1, 1)];
    st.lastPointer = now();
  }, { passive: true });

  // [HALLOWEEN] гарбуз-ліхтар стоїть на дузі орбіти (a — кут дуги): світиться обличчя, полум'я тремтить
  function lantern(ctx, px, py, a, w, fl) {
    const k = w / 64;
    glow(ctx, px, py - w * 0.35, w * 2.6, "#FF8A1F", 0.32 * fl);
    glow(ctx, px, py - w * 0.4, w * 0.9, "#FFD27A", 0.4 * fl);
    ctx.save(); ctx.translate(px, py); ctx.rotate(a); ctx.scale(k, k); ctx.translate(-32, -55);
    ctx.fillStyle = `rgba(255,${Math.round(190 + 40 * fl)},110,1)`; ctx.fill(HW.face);
    const g = ctx.createRadialGradient(26, 24, 2, 32, 30, 40);
    g.addColorStop(0, "#ff9a3a"); g.addColorStop(0.7, "#d9530f"); g.addColorStop(1, "#7a2a06");
    ctx.fillStyle = g; ctx.fill(HW.pump, "evenodd");
    ctx.save(); ctx.clip(HW.pump, "evenodd"); ctx.strokeStyle = "rgba(80,18,0,.45)"; ctx.lineWidth = 1.3; ctx.stroke(HW.ribs); ctx.restore();
    ctx.fillStyle = "#3f6212"; ctx.fill(HW.stem);
    ctx.restore();
  }
  // [HALLOWEEN] капелюх відьми — сидить на маківці набакир, тисне й тягнеться разом з Бліпом; у стрибку на мить підлітає
  function hat(ctx, x, y, r, sx, sy, jumpK, t) {
    const lift = jumpK >= 0 ? Math.max(0, Math.sin(clamp((jumpK - 0.1) / 0.8) * Math.PI)) * r * 0.16 : 0;
    const k = r * 1.38 / 48;
    glow(ctx, x + r * 0.12, y - r * 1.25 - lift, r * 1.1, "#8B5CF6", 0.22);
    ctx.save();
    ctx.translate(x + r * 0.08 * sx, y - r * 0.72 * sy - lift); ctx.scale(sx, sy);
    ctx.rotate(0.16 + (reduced ? 0 : Math.sin(t * 1.3) * 0.03) + (lift ? -0.08 * lift / (r * 0.16) : 0));
    ctx.scale(k, k); ctx.translate(-24, -35);
    ctx.fillStyle = "#1a0b2e"; ctx.fill(HW.hat);
    ctx.strokeStyle = "#8b5cf6"; ctx.lineWidth = 1.3; ctx.lineJoin = "round"; ctx.stroke(HW.hat);
    ctx.fillStyle = "#ff8a1f"; ctx.fill(HW.band);
    // пряжка на стрічці й світло Бліпа знизу на крисах
    ctx.fillStyle = "#ffd27a"; ctx.fillRect(21.6, 27, 4.4, 3.4); ctx.fillStyle = "#ff8a1f"; ctx.fillRect(22.8, 28, 2, 1.4);
    ctx.strokeStyle = "rgba(111,231,250,.55)"; ctx.lineWidth = 0.9; ctx.beginPath(); ctx.moveTo(8, 37.4); ctx.quadraticCurveTo(24, 39.6, 40, 37.4); ctx.stroke();
    ctx.restore();
  }
  // [HALLOWEEN] друг-кажанчик: ширяє праворуч від Бліпа, махає крилами; від тапу — злітає дугою й повертається; у паузі — ледь ворушиться
  function bat(ctx, x, y, r, t, m, j) {
    const slow = m === "calm" ? 0.35 : 1, still = reduced;
    let bx = x + r * 1.62 + (still ? 0 : Math.sin(t * 1.3 * slow) * r * 0.22), by = y - r * 1.25 + (still ? 0 : Math.sin(t * 2.1 * slow + 0.6) * r * 0.18);
    if (!still && j >= 0 && j < 1) { const q = j; bx += Math.sin(q * Math.PI * 2) * r * 0.5; by -= Math.sin(q * Math.PI) * r * 0.55; }
    const k = r * 1.08 / 48, flap = still ? 0.5 : Math.sin(t * (m === "calm" ? 6 : 15)) * 0.5 + 0.5;
    glow(ctx, bx, by, r * 0.75, "#A87BFF", 0.35);
    ctx.save(); ctx.translate(bx, by); ctx.rotate(still ? -0.08 : Math.sin(t * 1.7) * 0.1 - 0.06); ctx.scale(k, k);
    ctx.fillStyle = "#2b1650"; ctx.strokeStyle = "rgba(196,160,255,.85)"; ctx.lineWidth = 1; ctx.lineJoin = "round";
    for (const side of [1, -1]) {                                     // крила — від кореня (y 2.4) махають угору-вниз
      ctx.save(); ctx.scale(side, 1); ctx.translate(0, 2.4); ctx.scale(1, 1 - flap * 1.45); ctx.translate(0, -2.4);
      ctx.fill(HW.batW); ctx.stroke(HW.batW); ctx.restore();
    }
    ctx.fill(HW.batB); ctx.stroke(HW.batB);
    // очі-вогники й усмішка — щоб друг, а не страшко
    ctx.fillStyle = "#FFD27A"; ctx.beginPath(); ctx.arc(-1.9, -1.2, 1.15, 0, Math.PI * 2); ctx.arc(1.9, -1.2, 1.15, 0, Math.PI * 2); ctx.fill();
    ctx.strokeStyle = "#FFD27A"; ctx.lineWidth = 0.6; ctx.beginPath(); ctx.arc(0, 1.2, 1.3, 0.25 * Math.PI, 0.75 * Math.PI); ctx.stroke();
    ctx.restore();
  }
  function draw(t) {
    const dpr = Math.min(2, window.devicePixelRatio || 1), W = box.clientWidth, H = box.clientHeight;
    if (cv.width !== Math.round(W * dpr) || cv.height !== Math.round(H * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);
    const S = Math.min(W, H), cx = W / 2, r = S * (HW ? 0.155 : 0.17), R = S * 1.15, ocy = H * 0.66 + R;   // дуга орбіти: коло R, верх — під Бліпом
    // небо: світло м'яча знизу, туман Тиші — фіалкове по краях
    const sky = ctx.createRadialGradient(cx, H * 0.6, 0, cx, H * 0.6, S * 0.75);
    sky.addColorStop(0, "rgba(0,229,255,0.16)"); sky.addColorStop(0.55, "rgba(46,50,95,0.10)"); sky.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
    for (let i = 0; i < 14; i++) {
      const x = ((i * 0.618 + 0.13) % 1) * W, y = ((i * 0.377 + 0.07) % 0.6) * H, tw = 0.5 + 0.5 * Math.sin(t * (0.6 + (i % 5) * 0.21) + i);
      twinkle(ctx, x, y, S * 0.004 * (1 + (i % 3)), 0, "#BFE9FF", 0.25 + 0.5 * tw);
    }
    const beatK = ((t / BEAT) % 1), pulse = reduced ? 0.3 : Math.exp(-beatK * 6);
    const lit = (phi) => { const d = Math.abs(((phi - 270 + 540) % 360) - 180); return Math.max(0, 1 - d / 22) * (0.4 + 0.6 * pulse); };
    ring(ctx, cx, ocy, R, { color: "#6FE7FA", alpha: 0.9, wz: S / 1080 * 1.6, dim: 0.55, lit, litColor: "#9FF6FF" });
    const fl = HW ? (reduced ? 0.85 : flick(t)) : 0;
    if (HW) lantern(ctx, cx + Math.sin(-0.34) * R, ocy - Math.cos(-0.34) * R, -0.34, S * 0.085, fl);
    // настрій: що навколо
    const m = st.mood;
    if (m === "focus" || m === "ouch") {
      const a = m === "ouch" ? -0.22 : 0.34, sx = cx + Math.sin(a) * R, sy = ocy - Math.cos(a) * R;
      spike(ctx, sx, sy, S * 0.12, t * (m === "ouch" ? 0.4 : 1.4), { color: "#FF3D68" });
    }
    const ga = HW ? 0.3 : -0.3;                                      // у сезон ліворуч стоїть гарбуз — кристал праворуч
    if (m === "joy") gem(ctx, cx + Math.sin(ga) * R, ocy - Math.cos(ga) * R, S * 0.055, t * 1.2, "#FFD54A", 1);
    // Бліп: стрибок (тап), погойдування в такт, стиск на приземленні
    const j = t - st.jumpAt, jumpK = j >= 0 && j < 0.55 ? j / 0.55 : -1;
    let y = H * 0.66 - r * 1.02, sx = 1, sy = 1;
    if (jumpK >= 0) { y -= Math.sin(jumpK * Math.PI) * r * 1.25; const sq = jumpK < 0.12 ? jumpK / 0.12 : jumpK > 0.88 ? (1 - jumpK) / 0.12 : 0; sx = 1 + 0.12 * sq; sy = 1 - 0.12 * sq; if (jumpK > 0.15 && jumpK < 0.85) { sx = 0.94; sy = 1.07; } }
    else if (!reduced) { const breath = m === "calm" ? Math.sin(t * 1.1) * 0.025 : pulse * 0.035; sx = 1 + breath; sy = 1 - breath; y += breath * r * 0.9; }
    let x = cx;
    if (m === "ouch" && !reduced) { const k = t - st.shake; if (k < 0.5) x += Math.sin(k * 60) * r * 0.08 * (1 - k / 0.5); }
    // очі: курсор, а без нього — повільно блукають
    if (t - st.lastPointer > 3) st.want = [Math.sin(t * 0.35) * 0.5, Math.sin(t * 0.23) * 0.3];
    st.look[0] += (st.want[0] - st.look[0]) * 0.12; st.look[1] += (st.want[1] - st.look[1]) * 0.12;
    if (t > st.blinkAt + 0.14) st.blinkAt = t + 2.2 + Math.random() * 2.8;
    const blink = m === "calm" ? 0 : t >= st.blinkAt && t < st.blinkAt + 0.14 ? Math.sin((t - st.blinkAt) / 0.14 * Math.PI) : 0;
    const look = m === "focus" ? [0.9, -0.1] : m === "ouch" ? [-0.7, 0] : st.look;
    if (m === "joy" && !reduced) for (let i = 0; i < 4; i++) {                // супутники комбо (x5 — чотири), як на м'ячі гри
      const a = t * 2.4 + i * Math.PI / 2;
      glow(ctx, x + Math.cos(a) * r * 1.45, y + Math.sin(a) * r * 1.45, r * 0.22, "#FFFFFF", 0.6);
      ctx.fillStyle = "#FFFFFF"; ctx.beginPath(); ctx.arc(x + Math.cos(a) * r * 1.45, y + Math.sin(a) * r * 1.45, r * 0.07, 0, Math.PI * 2); ctx.fill();
    }
    blip(ctx, x, y, r, { face: curFace(t), look, blink, sx, sy, heart: 0.25 + 0.6 * pulse, t, dim: m === "ouch" ? 0.18 : 0,
      rim: HW && !m ? [150, "#FF9A3A", 0.75 * fl] : [-40, m ? MOODS[m].rim : "#FFD27A", 0.6] });
    if (HW && m !== "wow") hat(ctx, x, y, r, sx, sy, jumpK, t);       // корона — сама «капелюх» зорі: у «wow» капелюх знято
    if (m === "wow") crown(ctx, x + r * 0.08, y - r * 1.22 + (reduced ? 0 : Math.sin(t * 2) * r * 0.04), r * 0.95, -0.12, 1, (t * 0.5) % 1);
    if (m === "calm") {                                              // пауза — не Тиша: дві риски паузи над ним, дихають
      ctx.save(); ctx.globalAlpha = 0.35 + 0.25 * Math.sin(t * 1.1); ctx.fillStyle = "#BFE9FF";
      const pw = r * 0.14, ph = r * 0.5, px = HW ? x - r * 1.45 : x, py = HW ? y - r * 1.4 : y - r * 1.75;   // у капелюсі — збоку, не на ньому
      ctx.fillRect(px - pw * 1.6, py, pw, ph); ctx.fillRect(px + pw * 0.6, py, pw, ph); ctx.restore();
    }
    if (HW) bat(ctx, x, H * 0.66 - r * 1.02, r, t, m, j);              // кажан — від місця спокою, не від стрибка (не вилітає з кадру)
    // ноти після тапу — пливуть угору й гаснуть
    st.notes = st.notes.filter((n) => t - n.t < 1.4);
    for (const n of st.notes) { const k = (t - n.t) / 1.4; if (k < 0) continue; note(ctx, x + n.x * r * 1.6 + Math.sin(k * 6 + n.x) * r * 0.1, y - r * (1.1 + k * 1.6), r * 0.32 * n.s, 1 - ease(k)); }
  }
  if ("IntersectionObserver" in window) new IntersectionObserver((es) => { st.vis = es[0].isIntersecting; }, { threshold: 0 }).observe(box);
  const loop = () => { if (st.vis && !document.hidden) draw(now()); requestAnimationFrame(loop); };
  draw(now());
  if (!reduced) requestAnimationFrame(loop);
  else addEventListener("resize", () => draw(now()));
  return null;
}
