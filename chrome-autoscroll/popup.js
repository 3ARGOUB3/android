const DEFAULTS = { speed: 60, direction: 1, atEnd: "stop", pauseOnInteract: true };
const $ = (id) => document.getElementById(id);
let tabId = null;
let settings = { ...DEFAULTS };

async function ensureInjected() {
  if (tabId == null) return false;
  try {
    await chrome.scripting.executeScript({ target: { tabId }, files: ["content.js"] });
    return true;
  } catch (_) {
    $("error").hidden = false;
    $("toggle").disabled = true;
    return false;
  }
}

async function send(msg) {
  if (!(await ensureInjected())) return null;
  try { return await chrome.tabs.sendMessage(tabId, msg); } catch (_) { return null; }
}

function render(status) {
  const s = status || { running: false, paused: false, ...settings };
  Object.assign(settings, {
    speed: s.speed, direction: s.direction, atEnd: s.atEnd, pauseOnInteract: s.pauseOnInteract,
  });
  $("toggle").textContent = s.running ? "Stop" : "Start";
  $("toggle").classList.toggle("stop", s.running);
  const pill = $("state");
  pill.textContent = s.running ? (s.paused ? "Paused" : "Scrolling") : "Stopped";
  pill.className = "pill" + (s.running ? (s.paused ? " paused" : " on") : "");
  $("speed").value = Math.min(s.speed, 1000);
  $("speedOut").textContent = s.speed;
  $("down").classList.toggle("active", s.direction > 0);
  $("up").classList.toggle("active", s.direction < 0);
  $("atEnd").value = s.atEnd;
  $("pause").checked = s.pauseOnInteract;
}

async function update(partial) {
  Object.assign(settings, partial);
  await chrome.storage.local.set(partial);
  render(await send({ type: "settings", settings: partial }));
}

$("toggle").addEventListener("click", async () => render(await send({ type: "toggle" })));
$("speed").addEventListener("input", (e) => {
  $("speedOut").textContent = e.target.value;
  update({ speed: Number(e.target.value) });
});
document.querySelectorAll("[data-speed]").forEach((b) =>
  b.addEventListener("click", () => update({ speed: Number(b.dataset.speed) })));
document.querySelectorAll("[data-dir]").forEach((b) =>
  b.addEventListener("click", () => update({ direction: Number(b.dataset.dir) })));
$("atEnd").addEventListener("change", (e) => update({ atEnd: e.target.value }));
$("pause").addEventListener("change", (e) => update({ pauseOnInteract: e.target.checked }));

// Live status from the page (e.g. it stopped at the bottom, or speed changed by keys).
chrome.runtime.onMessage.addListener((msg, sender) => {
  if (msg.type === "status" && sender.tab?.id === tabId) render(msg.status);
});

(async () => {
  settings = await chrome.storage.local.get(DEFAULTS);
  render(null);
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  tabId = tab?.id ?? null;
  const status = await send({ type: "status" });
  if (status) render(status);
})();
