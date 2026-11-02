#!/usr/bin/env python3
"""Rebuild common/src/main/assets/hackertube/videos.jsonl from HackerTube's export.

Usage: python3 tools/hackertube/build_assets.py [path/to/videos.jsonl]
Downloads https://hackertube.org/download/videos.jsonl when no path is given.
Only the fields the app shows are kept; DATA.md documents the full schema.
"""
import json
import sys
import urllib.request

SRC = sys.argv[1] if len(sys.argv) > 1 else "https://hackertube.org/download/videos.jsonl"
OUT = "common/src/main/assets/hackertube/videos.jsonl"


def keep(record):
    hn = record.get("hn") or {}
    areas = []
    for category in record.get("categories") or []:
        if category.get("area") and category["area"] not in areas:
            areas.append(category["area"])
    return {
        "vid": record["vid"],
        "title": record["title"],
        "channel": record.get("channel"),
        "summary": record.get("summary"),
        "why": record.get("why_hn"),
        "type": record.get("content_type"),
        "shelf": record.get("shelf_life"),
        "score": record.get("score"),
        "rec": hn.get("recommenders"),
        "first": hn.get("first_year"),
        "last": hn.get("last_year"),
        "areas": areas,
    }


def lines():
    if SRC.startswith("http"):
        with urllib.request.urlopen(SRC) as response:
            yield from response
    else:
        with open(SRC, "rb") as source:
            yield from source


records = [keep(json.loads(line)) for line in lines() if line.strip()]
payload = "\n".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) for r in records)

# Plain (not gzipped): AGP serves .gz assets decompressed anyway, and the APK deflates them.
with open(OUT, "w", encoding="utf-8") as out:
    out.write(payload + "\n")

print(f"{len(records)} videos -> {OUT}")
