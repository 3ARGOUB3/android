// Auto Scroll core. Runs as the extension's content script, and is also built
// into a bookmarklet (see build.js), so every chrome.* call is optional.
(() => {
  const EXT = typeof chrome !== "undefined" && !!(chrome.runtime && chrome.runtime.id);
  if (window.__autoScroll) {
    if (!EXT) window.__autoScroll.toggle(); // bookmarklet tapped again
    return;
  }

  const DEFAULTS = { speed: 60, direction: 1, atEnd: "stop", pauseOnInteract: true };
  const MIN_SPEED = 5, MAX_SPEED = 2000;
  const LS_KEY = "__autoScrollSettings";

  const state = {
    running: false, paused: false, settings: { ...DEFAULTS },
    target: null, carry: 0, lastTs: 0, rafId: 0, resumeTimer: 0, bar: null,
  };

  // Find what actually scrolls: the page itself (WIN) or, in many web apps, an inner panel.
  const WIN = "window";
  function pageRoom() {
    const d = document.documentElement, b = document.body;
    const h = Math.max(d.scrollHeight, b ? b.scrollHeight : 0);
    return h - window.innerHeight;
  }
  function findScroller() {
    if (pageRoom() > 50) return WIN;
    let best = null, bestArea = 0;
    for (const el of document.querySelectorAll("body *")) {
      if (el.scrollHeight - el.clientHeight < 50) continue;
      const oy = getComputedStyle(el).overflowY;
      if (oy !== "auto" && oy !== "scroll" && oy !== "overlay") continue;
      const r = el.getBoundingClientRect();
      const area = r.width * r.height;
      if (area > bestArea) { best = el; bestArea = area; }
    }
    return best || WIN;
  }
  const getPos = (t) => (t === WIN ? window.scrollY : t.scrollTop);
  const setPos = (t, y) => (t === WIN ? window.scrollTo(window.scrollX, y) : (t.scrollTop = y));
  const maxPos = (t) => (t === WIN ? pageRoom() : t.scrollHeight - t.clientHeight);

  function step(ts) {
    if (!state.running) return;
    state.rafId = requestAnimationFrame(step);
    if (state.paused || !state.lastTs) { state.lastTs = ts; return; }
    const dt = Math.min((ts - state.lastTs) / 1000, 0.1); // clamp after tab switch
    state.lastTs = ts;

    const el = state.target;
    if (!el || (el !== WIN && !el.isConnected)) { state.target = findScroller(); return; }

    // Accumulate sub-pixel movement so slow speeds stay smooth.
    state.carry += state.settings.speed * dt * state.settings.direction;
    const px = Math.trunc(state.carry);
    if (px === 0) return;
    state.carry -= px;

    const before = getPos(el);
    setPos(el, before + px);
    if (getPos(el) === before) handleEdge(el);
  }

  function handleEdge(el) {
    const pos = getPos(el);
    const atBottom = pos >= maxPos(el) - 1;
    const atTop = pos <= 0;
    const dir = state.settings.direction;
    if ((dir > 0 && !atBottom) || (dir < 0 && !atTop)) return; // blocked by something else
    switch (state.settings.atEnd) {
      case "loop": setPos(el, dir > 0 ? 0 : maxPos(el)); break;
      case "bounce": setSetting({ direction: -dir }); break;
      case "wait": break; // keep trying: infinite-scroll feeds load more content
      default: stop();
    }
  }

  function start() {
    if (state.running) return;
    state.target = findScroller();
    state.running = true;
    state.paused = false;
    state.lastTs = 0;
    state.carry = 0;
    state.rafId = requestAnimationFrame(step);
    showBar();
    notify();
  }

  function stop() {
    state.running = false;
    state.paused = false;
    cancelAnimationFrame(state.rafId);
    clearTimeout(state.resumeTimer);
    if (state.bar) state.bar.remove();
    notify();
  }

  function togglePause() {
    state.paused = !state.paused;
    clearTimeout(state.resumeTimer);
    if (!state.paused) state.target = findScroller();
    updateBar();
    notify();
  }

  function setSpeed(v) {
    setSetting({ speed: Math.max(MIN_SPEED, Math.min(MAX_SPEED, Math.round(v))) });
  }

  function setSetting(obj) {
    Object.assign(state.settings, obj);
    persist(obj);
    updateBar();
    notify();
  }

  function persist(obj) {
    try {
      if (EXT) chrome.storage.local.set(obj);
      else localStorage.setItem(LS_KEY, JSON.stringify(state.settings));
    } catch (_) {}
  }

  function notify() {
    if (!EXT) return;
    try { chrome.runtime.sendMessage({ type: "status", status: status() }).catch(() => {}); } catch (_) {}
  }

  function status() {
    return { running: state.running, paused: state.paused, ...state.settings };
  }

  // ---- On-page control bar (touch friendly, works on phones) ----
  const BTN = "all:unset;box-sizing:border-box;min-width:40px;height:40px;padding:0 10px;" +
    "display:inline-flex;align-items:center;justify-content:center;border-radius:999px;" +
    "cursor:pointer;font:600 15px/1 system-ui,sans-serif;color:#fff;background:rgba(255,255,255,.12);" +
    "-webkit-tap-highlight-color:transparent;touch-action:manipulation;";

  function showBar() {
    if (!state.bar) {
      const bar = document.createElement("div");
      bar.setAttribute("data-autoscroll-bar", "");
      bar.style.cssText = "all:initial;position:fixed;right:12px;bottom:max(12px,env(safe-area-inset-bottom));" +
        "z-index:2147483647;display:flex;align-items:center;gap:6px;padding:6px;border-radius:999px;" +
        "background:rgba(20,20,28,.88);box-shadow:0 4px 14px rgba(0,0,0,.35);user-select:none;" +
        "-webkit-user-select:none;font:600 13px/1 system-ui,sans-serif;color:#fff;";
      const mk = (label, title, fn) => {
        const b = document.createElement("button");
        b.type = "button"; b.style.cssText = BTN; b.textContent = label; b.title = title;
        b.setAttribute("aria-label", title);
        b.addEventListener("click", (e) => { e.preventDefault(); e.stopPropagation(); fn(); });
        bar.appendChild(b);
        return b;
      };
      state.ui = {
        dir: mk("▼", "Change direction", () => setSetting({ direction: -state.settings.direction })),
        slower: mk("−", "Slower", () => setSpeed(state.settings.speed / 1.25)),
        speed: document.createElement("span"),
        faster: null, pause: null,
      };
      state.ui.speed.style.cssText = "all:initial;font:600 13px/1 system-ui,sans-serif;color:#fff;" +
        "min-width:34px;text-align:center;";
      bar.appendChild(state.ui.speed);
      state.ui.faster = mk("+", "Faster", () => setSpeed(state.settings.speed * 1.25));
      state.ui.pause = mk("❚❚", "Pause / resume", togglePause);
      mk("✕", "Stop auto scroll", stop);
      state.bar = bar;
    }
    updateBar();
    (document.body || document.documentElement).appendChild(state.bar);
  }

  function updateBar() {
    if (!state.bar) return;
    state.ui.dir.textContent = state.settings.direction > 0 ? "▼" : "▲";
    state.ui.speed.textContent = String(state.settings.speed);
    state.ui.speed.title = state.settings.speed + " px/s";
    state.ui.pause.textContent = state.paused ? "▶" : "❚❚";
    state.bar.style.opacity = state.paused ? "0.7" : "1";
  }

  // ---- Pause on user interaction (ignores taps on the control bar) ----
  function interact(e) {
    if (!state.running || state.paused || !state.settings.pauseOnInteract) return;
    if (state.bar && state.bar.contains(e.target)) return;
    state.paused = true;
    updateBar();
    clearTimeout(state.resumeTimer);
    state.resumeTimer = setTimeout(() => {
      state.paused = false;
      state.target = findScroller();
      updateBar();
      notify();
    }, 1500);
  }
  window.addEventListener("wheel", interact, { passive: true, capture: true });
  window.addEventListener("touchmove", interact, { passive: true, capture: true });

  // ---- Keys while running ----
  window.addEventListener("keydown", (e) => {
    if (!state.running) return;
    const t = e.target;
    if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return;
    if (e.key === "Escape") stop();
    else if (e.key === " " && !e.ctrlKey && !e.metaKey) togglePause();
    else if (e.key === "+" || e.key === "=") setSpeed(state.settings.speed * 1.25);
    else if (e.key === "-" || e.key === "_") setSpeed(state.settings.speed / 1.25);
    else return;
    e.preventDefault();
    e.stopPropagation();
  }, true);

  const api = { state, start, stop, toggle: () => (state.running ? stop() : start()) };
  window.__autoScroll = api;

  if (EXT) {
    chrome.runtime.onMessage.addListener((msg, _sender, reply) => {
      switch (msg.type) {
        case "toggle": api.toggle(); break;
        case "start": start(); break;
        case "stop": stop(); break;
        case "speedUp": setSpeed(state.settings.speed * 1.25); break;
        case "speedDown": setSpeed(state.settings.speed / 1.25); break;
        case "settings":
          state.fresh = { ...state.fresh, ...msg.settings };
          Object.assign(state.settings, msg.settings); updateBar(); break;
      }
      reply(status());
    });
    // Saved settings may arrive after the popup already changed one; the newer value wins.
    chrome.storage.local.get(DEFAULTS, (s) => {
      Object.assign(state.settings, s, state.fresh);
      updateBar();
    });
  } else {
    // Bookmarklet: load saved settings for this site and start right away.
    try { Object.assign(state.settings, JSON.parse(localStorage.getItem(LS_KEY)) || {}); } catch (_) {}
    start();
  }
})();
