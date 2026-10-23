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
   ───────────────────────────────────────────────────────────────────────────── */
(function () {
  "use strict";
  var C = window.__odConsent || {};
  var ID = C.id, MODE = C.mode || "page";
  var KEY = "lewydo:od-analytics", TTL = 395 * 864e5;
  var box = document.getElementById("odc");
  if (!box || !/^G-[A-Z0-9]+$/.test(ID || "")) return;
  var root = document.documentElement;
  // Кука _ga — лише для сайту гри (/Game-Orbit-Dash/…), не для всього roshevasternin.github.io (там і сайт студії)
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
      cookie_path: BASE, cookie_expires: 34128000,            // 395 днів — як і сама згода
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
    // відкликали — куки GA геть (на нашому шляху й, про всяк випадок, на корені)
    document.cookie.split(";").forEach(function (c) {
      var n = c.split("=")[0].trim();
      if (n !== "_ga" && n.indexOf("_ga_") !== 0) return;
      [BASE, "/"].forEach(function (p) { document.cookie = n + "=; Max-Age=0; path=" + p; document.cookie = n + "=; Max-Age=0; path=" + p + "; domain=" + location.hostname; });
    });
  }
  function send(name, params) {
    if (state !== "granted" || !loaded || sent >= 300) return;   // запобіжник: не більше 300 подій за захід
    sent++;
    gtag("event", name, params || {});
  }
  // «Хтось справді грав» — game_start раз за захід на сторінку; дозволили посеред гри — піде одразу після «Allow»
  function flushGame() { if (played && !gameSent && state === "granted") { gameSent = true; send("game_start", { mode: played }); } }

  /* ── банер ──────────────────────────────────────────────────────────────── */
  var open = false, forced = false, after = null;
  function show(force, done) {
    open = true; forced = !!force; after = done || null;
    [].forEach.call(box.querySelectorAll("[data-odc]"), function (b) { b.setAttribute("aria-pressed", String(b.getAttribute("data-odc") === state)); });
    box.hidden = false;
    root.classList.add("odc-open");
    layout();
    if (force) { var b = box.querySelector('[data-odc="' + (state || "granted") + '"]'); if (b) try { b.focus({ preventScroll: true }); } catch (e) { /* */ } }
  }
  function hide() {
    open = false;
    box.hidden = true;
    root.classList.remove("odc-open", "odc-flow");
    root.style.removeProperty("--od-bottom");
    box.classList.remove("odc-away");
    if (MODE === "play") window.dispatchEvent(new Event("resize"));
  }
  function choose(v) {
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

  /* Розкладка. Сторінка: картка внизу; якщо вона лягла б на «Грати», Google Play чи перемикач рівнів, які зараз на
     екрані, — ховається, доки вони не відʼїдуть (банер ніколи не накриває кнопки). /play/: широкий екран — картка в
     порожньому полі збоку від гри; вузький — смуга під грою, а сама гра стає на її висоту нижчою (нічого не накрито,
     тапи по грі працюють, вибір можна відкласти). */
  var PROTECT = "a[data-play],.btn,.lvc";
  function hit(r) {
    var els = document.querySelectorAll(PROTECT);
    for (var i = 0; i < els.length; i++) {
      if (box.contains(els[i])) continue;
      var q = els[i].getBoundingClientRect();
      if (!q.width || q.bottom < 0 || q.top > innerHeight) continue;
      if (q.left < r.right && q.right > r.left && q.top < r.bottom && q.bottom > r.top) return true;
    }
    return false;
  }
  function layout() {
    if (!open) return;
    if (MODE === "play") {
      var side = (innerWidth - innerHeight * 0.47) / 2;
      var corner = side >= 330;
      box.classList.toggle("odc-corner", corner);
      box.style.width = corner ? Math.min(360, side - 32) + "px" : "";
      root.classList.toggle("odc-flow", !corner);
      root.style.setProperty("--od-bottom", corner ? "0px" : box.offsetHeight + "px");   // гра (prototype) стає нижчою на смугу
      window.dispatchEvent(new Event("resize"));
      return;
    }
    box.classList.toggle("odc-away", !forced && hit(box.getBoundingClientRect()));
  }
  var raf = 0;
  function relayout() { if (!raf) raf = requestAnimationFrame(function () { raf = 0; layout(); }); }
  if (MODE !== "play") {
    addEventListener("scroll", relayout, { passive: true });
    addEventListener("load", relayout);
    try { new ResizeObserver(relayout).observe(document.body); } catch (e) { /* старий браузер — вистачить scroll / resize */ }
  }
  addEventListener("resize", function (e) { if (MODE !== "play" || e.isTrusted) relayout(); });

  /* /play/: тексти банера мовою гри (<html lang> ставить гра) */
  function translate() {
    var S = C.S || {}, L = root.lang || "en", P = S[L] || {}, E = S.en || {};
    [].forEach.call(box.querySelectorAll("[data-t]"), function (el) { var k = el.getAttribute("data-t"); var v = P[k] || E[k]; if (v) el.textContent = v; });
    var lab = P["consent.settings"] || E["consent.settings"];
    if (lab) box.setAttribute("aria-label", lab);
    box.dir = (C.rtl || []).indexOf(L) >= 0 ? "rtl" : "ltr";
    if (open) relayout();
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
    var h = a.href || "", store = /play\.google\.com/.test(h) ? "google_play" : /apps\.apple\.com|itms-apps:/.test(h) ? "app_store" : null;
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
      } else if (name === "run_end") send("run_end", { score_bucket: bucket(p.score) });
      else if (name === "level_complete") send("level_complete", { level_name: String(p.level_name || "") });
      else if (name === "ranks_open") send("ranks_open", { source: String(p.source || ""), state: String(p.state || "") });
      else if (name === "ranks_join" && p.step === 2) send("ranks_join", { outcome: String(p.outcome || "") });
    };
    // SETTINGS гри → PRIVACY OPTIONS: той самий банер; done — «✓ PRIVACY SAVED» на рядку гри
    window.__orbitPrivacy = function (done) { show(true, done); };
  }

  if (state === "granted") start();
  else if (state === null) show(false);
})();
