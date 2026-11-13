#!/usr/bin/env python3
"""Converts content-src/<lang>.md into app/src/main/assets/content/<lang>.json.

Format:
  # id | category | Title      starts an article
  > summary                    one-line summary
  ## Heading                   starts a section
  - bullet                     bullet in the current section
  kw: a, b, c                  search keywords
  any other line               paragraph in the current section
  @lab CODE | Title            starts a lab explanation, followed by what:/high:/low:/tips: lines
  @tip text                    a tip of the day
"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANGS = ["ar", "fr", "en", "es"]
LAB_CODES = ["UREA", "CREAT", "K", "PHOS", "CA", "HB", "FERRITIN", "ALB", "PTH", "CRP", "TSAT"]

def parse(path):
    articles, labs, tips = [], [], []
    article = section = lab = None
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.strip()
        if not line or line.startswith("<!--"):
            continue
        if line.startswith("# "):
            parts = [p.strip() for p in line[2:].split("|")]
            if len(parts) != 3:
                sys.exit(f"{path}:{n}: expected '# id | category | title'")
            article = {"id": parts[0], "cat": parts[1], "title": parts[2], "summary": "", "s": [], "kw": []}
            articles.append(article); section = None; lab = None
        elif line.startswith("@lab "):
            code, title = [p.strip() for p in line[5:].split("|")]
            lab = {"code": code, "title": title, "what": "", "high": "", "low": "", "tips": ""}
            labs.append(lab); article = None
        elif line.startswith("@tip "):
            tips.append(line[5:].strip())
        elif lab is not None and line.split(":", 1)[0] in ("what", "high", "low", "tips"):
            key, value = line.split(":", 1)
            lab[key] = (lab[key] + " " + value.strip()).strip()
        elif article is None:
            sys.exit(f"{path}:{n}: text outside an article: {line[:40]}")
        elif line.startswith("> "):
            article["summary"] = line[2:].strip()
        elif line.startswith("## "):
            section = {"h": line[3:].strip(), "p": [], "b": []}
            article["s"].append(section)
        elif line.startswith("kw:"):
            article["kw"] = [k.strip() for k in line[3:].split(",") if k.strip()]
        else:
            if section is None:
                section = {"p": [], "b": []}
                article["s"].append(section)
            if line.startswith("- "):
                section["b"].append(line[2:].strip())
            else:
                section["p"].append(line)
    for a in articles:
        for s in a["s"]:
            if not s["p"]: del s["p"]
            if not s["b"]: del s["b"]
    return {"articles": articles, "labs": labs, "tips": tips}

def main():
    packs = {}
    for lang in LANGS:
        src = os.path.join(ROOT, "content-src", f"{lang}.md")
        packs[lang] = parse(src)
    ref = packs["en"]
    ref_ids = [(a["id"], a["cat"]) for a in ref["articles"]]
    for lang, pack in packs.items():
        ids = [(a["id"], a["cat"]) for a in pack["articles"]]
        if ids != ref_ids:
            missing = set(ref_ids) - set(ids); extra = set(ids) - set(ref_ids)
            sys.exit(f"{lang}: articles differ from English. missing={sorted(missing)} extra={sorted(extra)} (order must match)")
        codes = [l["code"] for l in pack["labs"]]
        if codes != LAB_CODES:
            sys.exit(f"{lang}: lab codes {codes}")
        for l in pack["labs"]:
            for key in ("what", "high", "low"):
                if not l[key]:
                    sys.exit(f"{lang}: lab {l['code']} has no '{key}'")
        if len(pack["tips"]) != len(ref["tips"]):
            sys.exit(f"{lang}: {len(pack['tips'])} tips, English has {len(ref['tips'])}")
        for a in pack["articles"]:
            if not a["s"]:
                sys.exit(f"{lang}: article {a['id']} is empty")
        out = os.path.join(ROOT, "app/src/main/assets/content")
        os.makedirs(out, exist_ok=True)
        with open(os.path.join(out, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump(pack, f, ensure_ascii=False, separators=(",", ":"))
        words = sum(len(json.dumps(a, ensure_ascii=False).split()) for a in pack["articles"])
        print(f"{lang}: {len(pack['articles'])} articles, {len(pack['labs'])} labs, {len(pack['tips'])} tips, ~{words} words")

if __name__ == "__main__":
    main()
