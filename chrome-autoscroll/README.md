# Auto Scroll

Hands-free, smooth auto scrolling for any web page, on a computer or a phone.

| Where | Use |
|---|---|
| Chrome, Edge, Brave on a computer | The extension (full popup and shortcuts) |
| Firefox on a computer or Android | The extension (it is Firefox-compatible) |
| Chrome / Samsung Internet / Safari on a phone | The bookmarklet |

Chrome for Android and Safari on iPhone don't support extensions, so phones use the bookmarklet. It's the same code with the same on-screen controls.

## Phone: bookmarklet
Open `bookmarklet/install.html` and follow the steps. In short: bookmark any page, rename the bookmark to `autoscroll`, and replace its URL with the contents of `bookmarklet/bookmarklet.txt`. Then on any page, type `autoscroll` in the address bar and tap the bookmark. Run it again to stop.

After changing `content.js`, rebuild the bookmarklet with `node build.js`.

## Computer: install the extension (developer mode)
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
- An on-page control bar (big enough to tap on a phone): direction, slower / faster, pause, stop.
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
