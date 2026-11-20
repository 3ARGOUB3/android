// Auto Scroll content script. Injected on demand; guarded against double injection.
(() => {
  if (window.__autoScroll) return;

  const DEFAULTS = { speed: 60, direction: 1, atEnd: "stop", pauseOnInteract: true };
  const MIN_SPEED = 5, MAX_SPEED = 2000;

  const state = {
    running: false,
    paused: false,
    settings: { ...DEFAULTS },
    target: null,
    carry: 0,
    lastTs: 0,
    rafId: 0,
    resumeTimer: 0,
    badge: null,
  };

  // Find the element that actually scrolls (many web apps scroll an inner div, not the window).
  function findScroller() {
    const doc = document.scrollingElement || document.documentElement;
    if (doc.scrollHeight - doc.clientHeight > 50) return doc;
    let best = null, bestArea = 0;
    for (const el of document.querySelectorAll("body *")) {
      if (el.scrollHeight - el.clientHeight < 50) continue;
      const oy = getComputedStyle(el).overflowY;
      if (oy !== "auto" && oy !== "scroll" && oy !== "overlay") continue;
      const r = el.getBoundingClientRect();
      const area = r.width * r.height;
      if (area > bestArea) { best = el; bestArea = area; }
    }
    return best || doc;
  }

  function step(ts) {
    if (!state.running) return;
    state.rafId = requestAnimationFrame(step);
    if (state.paused) { state.lastTs = ts; return; }
    if (!state.lastTs) { state.lastTs = ts; return; }
    const dt = Math.min((ts - state.lastTs) / 1000, 0.1); // clamp after tab switch
    state.lastTs = ts;

    const el = state.target;
    if (!el || !el.isConnected) { state.target = findScroller(); return; }

    // Accumulate sub-pixel movement so slow speeds stay smooth.
    state.carry += state.settings.speed * dt * state.settings.direction;
    const px = Math.trunc(state.carry);
    if (px === 0) return;
    state.carry -= px;

    const before = el.scrollTop;
    el.scrollTop = before + px;
    if (el.scrollTop === before) handleEdge(el);
  }

  function handleEdge(el) {
    const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
    const atTop = el.scrollTop <= 0;
    const dir = state.settings.direction;
    if ((dir > 0 && !atBottom) || (dir < 0 && !atTop)) return; // blocked by something else
    switch (state.settings.atEnd) {
      case "loop":
        el.scrollTop = dir > 0 ? 0 : el.scrollHeight;
        break;
      case "bounce":
        state.settings.direction = -dir;
        persist({ direction: state.settings.direction });
        break;
      case "wait":
        break; // keep trying: handles infinite-scroll feeds that load more content
      default:
        stop();
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
    showBadge();
    notify();
  }

  function stop() {
    state.running = false;
    state.paused = false;
    cancelAnimationFrame(state.rafId);
    clearTimeout(state.resumeTimer);
    hideBadge();
    notify();
  }

  function setSpeed(v) {
    state.settings.speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, Math.round(v)));
    persist({ speed: state.settings.speed });
    updateBadge();
    notify();
  }

  function persist(obj) {
    try { chrome.storage.local.set(obj); } catch (_) {}
  }

  function notify() {
    try { chrome.runtime.sendMessage({ type: "status", status: status() }); } catch (_) {}
  }

  function status() {
    return { running: state.running, paused: state.paused, ...state.settings };
  }

  // ---- On-page indicator ----
  function showBadge() {
    if (!state.badge) {
      const b = document.createElement("div");
      b.setAttribute("data-autoscroll-badge", "");
      Object.assign(b.style, {
        position: "fixed", right: "16px", bottom: "16px", zIndex: 2147483647,
        font: "600 12px/1 system-ui, sans-serif", color: "#fff",
        background: "rgba(20,20,28,.82)", padding: "8px 10px", borderRadius: "999px",
        boxShadow: "0 2px 8px rgba(0,0,0,.3)", cursor: "pointer", userSelect: "none",
        transition: "opacity .2s",
      });
      b.title = "Click to stop auto scroll";
      b.addEventListener("click", (e) => { e.stopPropagation(); stop(); });
      state.badge = b;
    }
    updateBadge();
    (document.body || document.documentElement).appendChild(state.badge);
  }
  function updateBadge() {
    if (!state.badge) return;
    const arrow = state.settings.direction > 0 ? "▼" : "▲";
    state.badge.textContent = `${state.paused ? "❚❚" : arrow} ${state.settings.speed} px/s  ✕`;
    state.badge.style.opacity = state.paused ? "0.55" : "1";
  }
  function hideBadge() { state.badge?.remove(); }

  // ---- Pause on user interaction ----
  function interact() {
    if (!state.running || !state.settings.pauseOnInteract) return;
    state.paused = true;
    updateBadge();
    clearTimeout(state.resumeTimer);
    state.resumeTimer = setTimeout(() => {
      state.paused = false;
      state.target = findScroller();
      updateBadge();
    }, 1500);
  }
  window.addEventListener("wheel", interact, { passive: true, capture: true });
  window.addEventListener("touchstart", interact, { passive: true, capture: true });

  // ---- In-page keys while running ----
  window.addEventListener("keydown", (e) => {
    if (!state.running) return;
    const t = e.target;
    if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return;
    if (e.key === "Escape") { stop(); }
    else if (e.key === " " && !e.ctrlKey && !e.metaKey) {
      state.paused = !state.paused; clearTimeout(state.resumeTimer); updateBadge();
    }
    else if (e.key === "+" || e.key === "=") setSpeed(state.settings.speed * 1.25);
    else if (e.key === "-" || e.key === "_") setSpeed(state.settings.speed / 1.25);
    else return;
    e.preventDefault();
    e.stopPropagation();
  }, true);

  // ---- Messages from popup / background ----
  chrome.runtime.onMessage.addListener((msg, _sender, reply) => {
    switch (msg.type) {
      case "toggle": state.running ? stop() : start(); break;
      case "start": start(); break;
      case "stop": stop(); break;
      case "speedUp": setSpeed(state.settings.speed * 1.25); break;
      case "speedDown": setSpeed(state.settings.speed / 1.25); break;
      case "settings":
        Object.assign(state.settings, msg.settings);
        updateBadge();
        break;
    }
    reply(status());
  });

  chrome.storage.local.get(DEFAULTS, (s) => Object.assign(state.settings, s));

  window.__autoScroll = state;
})();
