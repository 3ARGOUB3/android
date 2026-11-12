#!/usr/bin/env python3
"""Splits the quran-json package (npm quran-json 3.1.2, CC BY-SA 4.0) into per-surah assets.
Usage: build_quran.py <path to the package's dist folder>"""
import json, os, sys

dist = sys.argv[1]
out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/assets/quran")
arabic = json.load(open(os.path.join(dist, "quran.json"), encoding="utf-8"))
translations = {lang: json.load(open(os.path.join(dist, f"quran_{lang}.json"), encoding="utf-8")) for lang in ("fr", "en", "es")}
index = []
for surah in arabic:
    sid = surah["id"]
    names = {lang: translations[lang][sid - 1]["translation"] for lang in translations}
    index.append({"id": sid, "name": surah["name"], "transliteration": surah["transliteration"],
                  "type": surah["type"], "count": surah["total_verses"], "names": names})
    os.makedirs(os.path.join(out, "ar"), exist_ok=True)
    with open(os.path.join(out, "ar", f"{sid}.json"), "w", encoding="utf-8") as f:
        json.dump([v["text"] for v in surah["verses"]], f, ensure_ascii=False, separators=(",", ":"))
    for lang, data in translations.items():
        t = data[sid - 1]
        assert t["id"] == sid and len(t["verses"]) == len(surah["verses"])
        os.makedirs(os.path.join(out, lang), exist_ok=True)
        with open(os.path.join(out, lang, f"{sid}.json"), "w", encoding="utf-8") as f:
            json.dump([v["translation"] for v in t["verses"]], f, ensure_ascii=False, separators=(",", ":"))
with open(os.path.join(out, "index.json"), "w", encoding="utf-8") as f:
    json.dump(index, f, ensure_ascii=False, separators=(",", ":"))
print(len(index), "surahs,", sum(s["count"] for s in index), "verses")
