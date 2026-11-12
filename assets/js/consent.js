/* ─────────────────────────────────────────────────────────────────────────────
   Orbit Dash — Google Analytics 4 лише за згодою (сайт гри: сторінка, /play/, політика).

   Правило сайту «жодних сторонніх запитів» тримається для кожного, хто не сказав «Allow»: до вибору й після «Decline»
   ні gtag.js, ні жодного пінгу (і без «cookieless»-режиму Consent Mode — gtag просто не вантажиться). «Allow» — тоді
   й лише тоді вантажиться googletagmanager.com/gtag/js і йдуть події на google-analytics.com.

   Налаштування — window.__odConsent (ставить site/build.py перед цим скриптом):
     id     — measurementId вебпотоку «Orbit Dash Web» (G-…; site.json → ga4)
     mode   — "page" (сторінка сайту: page_view + store_click), "play" (/play/: ще події гри через window.__orbitTrack),
              "privacy" (політика: лише показати й змінити вибір — gtag там не вантажиться ніколи)
     S, rtl — лише /play/: тексти банера всіма мовами (з site/src/strings.json) і мови справа наліво; мову бере з <html lang>,
              який ставить сама гра (сторінка сайту перекладає банер своїм page.js — data-t)
   play/embed.html (жива гра в герої й на сайті студії) цього скрипта НЕ має: там аналітики немає взагалі.

   Вибір — localStorage "lewydo:od-analytics" = {"v":"granted"|"denied","t":мс}; через 395 днів (13 місяців) питаємо знову.
   Змінити — [data-odc-open] (підвал сторінки, кнопка в політиці) і PRIVACY OPTIONS у SETTINGS гри (window.__orbitPrivacy).

   ЄДИНИЙ виняток з «нічого назовні до Allow» (власник 12.11.2026): НАШ анонімний лічильник відвідувань stats.lewydo.com (Cloudflare Worker
   lewydo-site-stats → Workers Analytics Engine). Працює для всіх, і до вибору, і після «Decline», бо нічого особистого не зберігає: без кук,
   без id, без IP, без адреси й реферера — лише ці 5 полів (контракт — server/site-stats, privacy.html#visit-counter):
     s   "od" — сайт гри            p   "home" | "play" | "privacy" | "press" | "other" (зокрема «Музика» /music/)
     e   "view" | "allow" | "decline"   c   вибір ДО події: "granted" | "denied" | "none"
     src мітка джерела, рахується тут, на пристрої (власник 12.11.2026), — за пріоритетом:
         1) utm_source (^[a-z0-9_.-]{1,32}$);
         2) категорія ХОСТА реферера: свій сайт → "self"; lewydo.com / www.lewydo.com → "studio"; orbitdash.lewydo.com → "orbitdash";
            cubepix.lewydo.com → "cubepix" (інший *.lewydo.com → "studio"); google, bing, duckduckgo, tiktok, youtube, instagram, facebook,
            x, reddit, discord, telegram; інший хост → "other";
         3) реферера немає — вбудований браузер застосунку з navigator.userAgent: TikTok → "tiktok", Instagram → "instagram",
            Facebook → "facebook" (біо TikTok власника — гола https://lewydo.com/, а браузер TikTok часто не шле реферер).
            Сам user agent нікуди не йде й ніде не лежить — лише одна з трьох міток;
         4) інакше "none".
   Лише navigator.sendBeacon (рядок → text/plain, без preflight); немає sendBeacon — нічого. Нічого не пишемо в сховище; читаємо лише вибір
   Allow / Decline, який смуга вже зберегла (load(), ключ KEY), — щоб c показав, який вибір був.
   Адреса — window.__odConsent.stats (site.json → stats; тести підміняють). Google Analytics — як і було, лише після «Allow».
   ───────────────────────────────────────────────────────────────────────────── */
(function () {
  "use strict";
  var C = window.__odConsent || {};
  var ID = C.id, MODE = C.mode || "page";
  var KEY = "lewydo:od-analytics", TTL = 395 * 864e5;

  /* ── наш лічильник відвідувань (stats.lewydo.com) ─────────────────────────── */
  var STATS = typeof C.stats === "string" ? C.stats : "https://stats.lewydo.com/h";
  // категорія реферера — лише за хостом (повну адресу не шлемо ніколи); ланцюжок [мітка, регулярка хоста]
  var REF = [
    // youtube раніше за google: com.google.android.youtube — застосунок YouTube, а не пошук
    ["youtube", /(^|\.)youtube\.com$|(^|\.)youtu\.be$|^com\.google\.android\.youtube$/],
    ["google", /(^|\.)google\.[a-z.]+$|^com\.google\.android\./],
    ["bing", /(^|\.)bing\.com$/],
    ["duckduckgo", /(^|\.)duckduckgo\.com$/],
    ["tiktok", /(^|\.)tiktok\.com$|(^|\.)tiktokv\.com$|^com\.zhiliaoapp\.musically/],
    ["instagram", /(^|\.)instagram\.com$|^com\.instagram\./],
    ["facebook", /(^|\.)facebook\.com$|(^|\.)fb\.com$|(^|\.)messenger\.com$|^com\.facebook\./],
    ["x", /(^|\.)x\.com$|(^|\.)twitter\.com$|^t\.co$/],
    ["reddit", /(^|\.)reddit\.com$|(^|\.)redd\.it$/],
    ["discord", /(^|\.)discord\.com$|(^|\.)discord\.gg$|(^|\.)discordapp\.com$/],
    ["telegram", /^t\.me$|(^|\.)telegram\.org$|(^|\.)telegram\.me$|^org\.telegram\./],
  ];
  // вбудований браузер застосунку (коли реферера немає): user agent читаємо ЛИШЕ тут і лише щоб вибрати одну з трьох міток — сам він нікуди не йде
  var APP = [
    ["tiktok", /musical_ly|TikTok|Bytedance|trill_/i],
    ["instagram", /Instagram/],
    ["facebook", /FBAN|FBAV|FB_IAB|FBIOS/],
  ];
  function inApp() {
    var ua = String(navigator.userAgent || "");
    for (var i = 0; i < APP.length; i++) if (APP[i][1].test(ua)) return APP[i][0];
    return "none";
  }
  // категорія хоста реферера: свій сайт, сайти Lewydo, відомі сайти, інакше «other»
  function refKind(h) {
    if (h === location.hostname) return "self";                       // перехід усередині сайту
    if (h === "lewydo.com" || h === "www.lewydo.com") return "studio";
    if (h === "orbitdash.lewydo.com") return "orbitdash";
    if (h === "cubepix.lewydo.com") return "cubepix";
    if (/(^|\.)lewydo\.com$/.test(h)) return "studio";                // інший *.lewydo.com — теж студія
    for (var i = 0; i < REF.length; i++) if (REF[i][1].test(h)) return REF[i][0];
    return "other";
  }
  function source() {
    try {
      // мітка кампанії — малими літерами; схоже на ідентифікатор (довгий hex / цифри) — не беремо: мітка не має вказувати на людину
      var u = (new URLSearchParams(location.search).get("utm_source") || "").toLowerCase();
      if (u && /^[a-z0-9_.-]{1,32}$/.test(u) && !/^[0-9a-f]{12,}$|\d{6,}/.test(u)) return u;
      if (document.referrer) {
        var h = new URL(document.referrer).hostname.toLowerCase();
        if (h) return refKind(h);
      }
      return inApp();
    } catch (e) { return "none"; }
  }
  function page() {
    if (MODE === "play") return "play";
    if (MODE === "privacy") return "privacy";
    // [12.11.2026] сторінка «Музика» /music/ — «other» (білий список воркера: home / play / privacy / press / other)
    if (MODE === "page") return /\/press(\/|$)/.test(location.pathname) ? "press" : /\/music(\/|$)/.test(location.pathname) ? "other" : "home";
    return "other";
  }
  // одна точка на подію; c — вибір ДО неї. Лічильник ніколи не ламає сторінку: усе в try/catch
  function hit(e, before) {
    try {
      if (!STATS || !navigator.sendBeacon) return;
      navigator.sendBeacon(STATS, JSON.stringify({ s: "od", p: page(), e: e, c: before === "granted" || before === "denied" ? before : "none", src: source() }));
    } catch (err) { /* лічильник — не важливіше за сторінку */ }
  }
  // «view» — раз за завантаження сторінки (не з bfcache); сторінку, яку браузер лише передвантажив (prerender), — коли її справді відкрили
  (function () {
    var sentView = false;
    function view() { if (!sentView) { sentView = true; hit("view", load()); } }
    try {
      if (document.prerendering) document.addEventListener("prerenderingchange", view, { once: true });
      else view();
    } catch (e) { /* */ }
  })();

  var box = document.getElementById("odc");
  if (!box || !/^G-[A-Z0-9]+$/.test(ID || "")) return;
  var root = document.documentElement;
  // Кука _ga — лише для сайту гри. З 06.11.2026 він живе в корені власного хоста (orbitdash.lewydo.com), тож шлях — «/» (корінь сайту, рахуємо
  // від цього скрипта: assets/js/ → ../../). Домен — явно цей хост: типове «auto» gtag кладе _ga на найвищий домен (.lewydo.com), і кука
  // їздила б на сайт студії (lewydo.com) і CubePix (cubepix.lewydo.com). Хост сайту гри й нижчі (*.orbitdash.lewydo.com — їх немає), не вище
  var HOST = location.hostname;
  var BASE = (function () {
    try { return new URL("../../", (document.currentScript || {}).src || location.href).pathname; } catch (e) { return "/"; }
  })();

  function load() {
    try {
      var o = JSON.parse(localStorage.getItem(KEY) || "null");
      if (o && (o.v === "granted" || o.v === "denied") && Date.now() - (+o.t || 0) < TTL) return o.v;
    } catch (e) { /* приватне вікно, вимкнене сховище — питаємо щоразу */ }
    return null;
  }
  function save(v) { try { localStorage.setItem(KEY, JSON.stringify({ v: v, t: Date.now() })); } catch (e) { /* лише на цей захід */ } }

  var state = load(), loaded = false, sent = 0, played = null, gameSent = false;
  // [2.2.1] /play/ (власник 29.10): питаємо на вході, як і всюди, а хто не вибрав нічого — ще раз після першого кінця забігу / рівня,
  // коли людина вже задоволена: смуга, що вже стоїть, легко сяє лінією зверху (odc-nudge; «менше руху» — лише лінія), схована — з'являється знову. «Відхилити» — вибір: після нього не питаємо
  // (GDPR — відмову поважаємо, знову спитаємо лише через TTL). Раз за захід на сторінку.
  var ASK_END = 1200, nudged = false;
  function askAgain() {
    if (state !== null || nudged) return;
    nudged = true;
    setTimeout(function () {
      if (state !== null) return;
      if (!open) { show(false); return; }
      box.classList.remove("odc-nudge"); void box.offsetWidth; box.classList.add("odc-nudge");
    }, ASK_END);
  }

  /* ── Google Analytics ───────────────────────────────────────────────────── */
  function gtag() { window.dataLayer.push(arguments); }
  function start() {
    if (MODE === "privacy") return;
    window["ga-disable-" + ID] = false;
    if (loaded) { gtag("consent", "update", { analytics_storage: "granted" }); flushGame(); return; }
    loaded = true;
    window.dataLayer = window.dataLayer || [];
    gtag("js", new Date());
    gtag("consent", "default", { analytics_storage: "granted", ad_storage: "denied", ad_user_data: "denied", ad_personalization: "denied" });
    gtag("config", ID, {
      cookie_domain: HOST, cookie_path: BASE, cookie_expires: 34128000,   // лише хост сайту гри; 395 днів — як і сама згода
      allow_google_signals: false, allow_ad_personalization_signals: false,
    });
    var s = document.createElement("script");
    s.async = true;
    s.src = "https://www.googletagmanager.com/gtag/js?id=" + encodeURIComponent(ID);
    document.head.appendChild(s);
    flushGame();
  }
  function stop() {
    window["ga-disable-" + ID] = true;
    if (loaded) gtag("consent", "update", { analytics_storage: "denied" });
    // відкликали — куки GA геть (на нашому шляху й, про всяк випадок, на корені; на хості й без домену)
    document.cookie.split(";").forEach(function (c) {
      var n = c.split("=")[0].trim();
      if (n !== "_ga" && n.indexOf("_ga_") !== 0) return;
      [BASE, "/"].forEach(function (p) { document.cookie = n + "=; Max-Age=0; path=" + p; document.cookie = n + "=; Max-Age=0; path=" + p + "; domain=" + HOST; });
    });
  }
  function send(name, params) {
    if (state !== "granted" || !loaded || sent >= 300) return;   // запобіжник: не більше 300 подій за захід
    sent++;
    gtag("event", name, params || {});
  }
  // «Хтось справді грав» — game_start раз за захід на сторінку; дозволили посеред гри — піде одразу після «Allow»
  function flushGame() { if (played && !gameSent && state === "granted") { gameSent = true; send("game_start", { mode: played }); } }

  /* ── банер: смуга на всю ширину внизу (власник 06.11.2026, правило студії docs/ux/analytics-consent.md) ── */
  // До вибору смуга стоїть і не ховається (раніше картка в куті зникала, щойно лягала на «Грати», — на ПК її при вході не було
  // видно зовсім). Нічого не губиться під нею: сторінка (і політика) отримує знизу запас висоти смуги, а фокус клавіатури
  // прокручується над нею (scroll-padding); /play/ — гра стає на висоту смуги нижчою (--od-bottom у prototype), тапи по грі
  // працюють, вибір можна відкласти. Хрестика й «закрити без вибору» немає.
  var open = false, after = null, back = null;
  function fit() {
    if (!open) return;
    var h = box.offsetHeight + "px";
    if (MODE === "play") {
      if (root.style.getPropertyValue("--od-bottom") !== h) { root.style.setProperty("--od-bottom", h); window.dispatchEvent(new Event("resize")); }
      return;
    }
    document.body.style.paddingBottom = h;
    root.style.scrollPaddingBottom = h;
  }
  function show(force, done) {
    open = true; after = done || null;
    back = force && document.activeElement !== document.body ? document.activeElement : null;   // кнопка, що відкрила, — фокус повернеться туди
    [].forEach.call(box.querySelectorAll("[data-odc]"), function (b) { b.setAttribute("aria-pressed", String(b.getAttribute("data-odc") === state)); });
    box.hidden = false;
    root.classList.add("odc-shown");      // не «odc-open»: так у політиці зветься кнопка Cookie settings — <html> брав би її стиль
    fit();
    if (force) { var b = box.querySelector('[data-odc="' + (state || "granted") + '"]'); if (b) try { b.focus({ preventScroll: true }); } catch (e) { /* */ } }
  }
  function hide() {
    var inside = box.contains(document.activeElement);
    open = false;
    box.hidden = true;
    root.classList.remove("odc-shown");
    if (MODE === "play") { root.style.removeProperty("--od-bottom"); window.dispatchEvent(new Event("resize")); }
    else { document.body.style.paddingBottom = ""; root.style.scrollPaddingBottom = ""; }
    if (inside && back && back.isConnected) try { back.focus({ preventScroll: true }); } catch (e) { /* */ }
    back = null;
  }
  function choose(v) {
    hit(v === "granted" ? "allow" : "decline", state);   // наш лічильник: c — вибір ДО цього тапу
    state = v; save(v); hide();
    if (v === "granted") start(); else stop();
    var f = after; after = null;
    if (f) try { f(v); } catch (e) { /* */ }
  }
  [].forEach.call(box.querySelectorAll("[data-odc]"), function (b) { b.addEventListener("click", function () { choose(b.getAttribute("data-odc")); }); });
  document.addEventListener("click", function (e) {
    var a = e.target.closest && e.target.closest("[data-odc-open]");
    if (!a) return;
    e.preventDefault();
    show(true);
  });
  // висота смуги міняється з шириною екрана й мовою (сторінка перекладає її своїм page.js) — запас і гра йдуть за нею
  try { new ResizeObserver(fit).observe(box); } catch (e) { addEventListener("resize", function (ev) { if (MODE !== "play" || ev.isTrusted) fit(); }); }

  /* /play/: тексти банера мовою гри (<html lang> ставить гра) */
  function translate() {
    var S = C.S || {}, L = root.lang || "en", P = S[L] || {}, E = S.en || {};
    [].forEach.call(box.querySelectorAll("[data-t]"), function (el) { var k = el.getAttribute("data-t"); var v = P[k] || E[k]; if (v) el.textContent = v; });
    var lab = P["consent.settings"] || E["consent.settings"];
    if (lab) box.setAttribute("aria-label", lab);
    box.dir = (C.rtl || []).indexOf(L) >= 0 ? "rtl" : "ltr";
    fit();
  }
  if (MODE === "play" && C.S) {
    translate();
    try { new MutationObserver(translate).observe(root, { attributes: true, attributeFilter: ["lang"] }); } catch (e) { /* */ }
  }

  /* ── події ──────────────────────────────────────────────────────────────── */
  // Перехід у магазин — головна конверсія з вебу в застосунок: store_click { place, store }
  document.addEventListener("click", function (e) {
    var a = e.target.closest && e.target.closest("a[href]");
    if (!a) return;
    // за хостом, а не за всією адресою: «Грати» несе мітки відвідувача (play/?utm_source=play.google.com), і тап по ньому — не перехід у магазин
    var h = a.href || "", store = a.hostname === "play.google.com" ? "google_play" : a.hostname === "apps.apple.com" || a.protocol === "itms-apps:" ? "app_store" : null;
    if (!store) return;
    var place = a.getAttribute("data-place");
    if (!place && MODE === "play") place = a.hasAttribute("data-rk-appprizes") ? "ranks_prizes" : /\/dev\?|\/developer\//.test(h) ? "more_games" : "review_sheet";
    if (!place) {
      var sec = a.closest("section[id]");
      place = a.closest("footer") ? "footer" : a.closest("header") ? "header" : a.closest(".hero") ? "hero" : sec ? sec.id : "page";
    }
    send("store_click", { place: place, store: store });
  }, true);

  if (MODE === "play") {
    /* Гра кличе window.__orbitTrack(назва, параметри) (prototype: track / webTrack — лише в публічному режимі).
       Сюди доходять лише потрібні події, без особистих даних; сирий рахунок — лише бакетом. ≈ 1 подія на ран. */
    var bucket = function (s) { s = +s || 0; return s < 100 ? "0_99" : s < 500 ? "100_499" : s < 1000 ? "500_999" : s < 2000 ? "1000_1999" : "2000_plus"; };
    window.__orbitTrack = function (name, p) {
      p = p || {};
      if (name === "run_start" || name === "level_start") {
        if (!played) { played = name === "run_start" ? "endless" : "levels"; flushGame(); }
        if (name === "level_start") send("level_start", { level_name: String(p.level_name || ""), autoplay: String(!!p.autoplay) });
      } else if (name === "run_end") { send("run_end", { score_bucket: bucket(p.score) }); askAgain(); }
      else if (name === "level_complete") { send("level_complete", { level_name: String(p.level_name || "") }); askAgain(); }
      else if (name === "ranks_open") send("ranks_open", { source: String(p.source || ""), state: String(p.state || "") });
      else if (name === "ranks_join" && p.step === 2) send("ranks_join", { outcome: String(p.outcome || "") });
      else if (name === "share_game") send("share_game", { source: String(p.source || ""), method: String(p.method || "") });
      // [v162] встановлення вебверсії (prototype/install.mjs): action — shown · prompt · accepted · dismissed · steps · sheet · store · installed,
      // source — corner · settings · browser; обидва — уже зареєстровані виміри GA4 (нових немає: квота 50/50)
      else if (name === "web_install") send("web_install", { action: String(p.action || ""), source: String(p.source || "") });
      // [S2, патч 308] VERSUS (PORT-PLAN §12): лише вже зареєстровані виміри source / mode / method / state і лише значення з переліків —
      // без імен, кодів кімнат і id гравця (policy-facts розділ 14 звіряє ці параметри з VS_PARAM_OK)
      else if (name === "vs_open" && vsOk("source", p.source)) send("vs_open", { source: p.source });
      else if (name === "vs_room" && vsOk("mode", p.mode) && vsOk("method", p.method)) send("vs_room", { mode: p.mode, method: p.method });
      else if (name === "vs_race_end" && vsOk("mode", p.mode) && vsOk("method", p.method) && vsOk("state", p.state))
        send("vs_race_end", { mode: p.mode, method: p.method, state: p.state });
    };
    var VS_VAL = {
      source: ["menu", "tutorial", "link", "referrer", "code"],
      mode: ["endless", "levels"],
      method: ["friends", "random", "code", "link", "bots", "offline", "online"],
      state: ["win", "podium", "lost", "draw", "left"],
    };
    var vsOk = function (k, v) { return typeof v === "string" && VS_VAL[k].indexOf(v) >= 0; };
    // SETTINGS гри → PRIVACY OPTIONS: той самий банер; done — «✓ PRIVACY SAVED» на рядку гри
    window.__orbitPrivacy = function (done) { show(true, done); };
  }

  if (state === "granted") start();
  else if (state === null) show(false);                          // на вході; /play/ — ще раз після першого забігу (askAgain)
})();
