// Handles keyboard shortcuts by injecting the content script on demand.
async function sendToActiveTab(type) {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id) return;
  try {
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["content.js"] });
    await chrome.tabs.sendMessage(tab.id, { type });
  } catch (_) {
    // Restricted page (chrome://, Web Store, etc.) — nothing we can do.
  }
}

chrome.commands.onCommand.addListener((command) => {
  const map = { "toggle-scroll": "toggle", "speed-up": "speedUp", "speed-down": "speedDown" };
  if (map[command]) sendToActiveTab(map[command]);
});
