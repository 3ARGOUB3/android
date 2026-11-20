# Auto Scroll: Chrome extension

Hands-free, smooth auto scrolling for any web page.

## Install (developer mode)
1. Open `chrome://extensions`.
2. Turn on **Developer mode** (top right).
3. Click **Load unpacked** and select this `chrome-autoscroll` folder.
4. Pin the extension and click its icon.

## Features
- Smooth scrolling from 5 to 2000 px/s, with Slow / Read / Skim / Fast presets.
- Scrolls up or down.
- At the end of the page it can stop, wait for more content (for infinite feeds), loop back to the top, or bounce.
- Pauses while you scroll with the mouse or touch, then resumes after 1.5 s (you can turn this off).
- Works on apps that scroll an inner panel instead of the whole page.
- A small on-page badge shows the speed. Click it to stop.
- Your settings are remembered.

## Keyboard
| Keys | Action |
|---|---|
| Alt+Shift+S | Start / stop |
| Alt+Shift+↑ / ↓ | Faster / slower |
| Space (while scrolling) | Pause / resume |
| + / − (while scrolling) | Faster / slower |
| Esc (while scrolling) | Stop |

You can change the global shortcuts at `chrome://extensions/shortcuts`.

## Permissions
Only `activeTab`, `scripting` and `storage`. The extension runs on a tab only when you click the icon or press a shortcut. It reads no browsing data. Chrome does not allow extensions on `chrome://` pages or the Chrome Web Store.
