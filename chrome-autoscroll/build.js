// Builds the bookmarklet version (for phones / any browser) from content.js.
// Usage: node build.js   ->  writes bookmarklet/bookmarklet.txt and bookmarklet/install.html
const fs = require("fs");
const path = require("path");

const src = fs.readFileSync(path.join(__dirname, "content.js"), "utf8");
const code = src
  .split("\n")
  .filter((l) => !/^\s*\/\//.test(l))   // drop comment-only lines
  .map((l) => l.trim())
  .filter(Boolean)
  .join("\n");
const bookmarklet = "javascript:" + encodeURIComponent(code);

const outDir = path.join(__dirname, "bookmarklet");
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, "bookmarklet.txt"), bookmarklet + "\n");

const tpl = fs.readFileSync(path.join(outDir, "install.template.html"), "utf8");
const esc = (s) => s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
fs.writeFileSync(path.join(outDir, "install.html"),
  tpl.replace("{{HREF}}", esc(bookmarklet)).replace("{{CODE}}", esc(bookmarklet)));
console.log(`bookmarklet: ${bookmarklet.length} chars`);
