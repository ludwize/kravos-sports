#!/usr/bin/env python3
"""Kravos Sports for the web: snapshot of data the browser cannot fetch itself.

The EuroLeague v2 API sends no CORS header, so the runner downloads the game lists
(Evroliga + Evrokup) and the page reads them from el.json. Live scores come straight
from the browser (live.euroleague.net allows it). Runs on GitHub every ~6 minutes (self-chaining web.yml).

usage: build.py <out-dir>
"""
import json, os, sys, time, urllib.request
from datetime import date

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/129 Safari/537.36", "Accept": "application/json"}
V2 = "https://api-live.euroleague.net/v2/competitions"
COMPS = [("E", "Evroliga"), ("U", "Evrokup")]


def get(url, tries=3):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
                return json.loads(r.read())
        except Exception as e:
            err = e
            time.sleep(2 * (i + 1))
    print("FAILED", url, err, file=sys.stderr)
    return None


def club(c):
    c = c or {}
    code = c.get("code") or "?"
    return {"code": code, "name": c.get("editorialName") or c.get("abbreviatedName") or code,
            "short": c.get("abbreviatedName") or code, "logo": (c.get("images") or {}).get("crest")}


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    today = date.today()
    year = today.year if today.month >= 8 else today.year - 1
    games, failed = [], []
    for comp, name in COMPS:
        d = get(f"{V2}/{comp}/seasons/{comp}{year}/games")
        if not d:
            failed.append(comp)
            continue
        for g in d.get("data", []):
            if g.get("gameCode") is None:
                continue
            loc, rd = g.get("local") or {}, g.get("road") or {}
            games.append({
                "c": comp, "code": g["gameCode"], "utc": g.get("utcDate"), "played": bool(g.get("played")),
                "round": (g.get("roundName") or g.get("phaseType") or "").replace("Round", "Kolo"),
                "h": club(loc.get("club")), "a": club(rd.get("club")),
                "hs": loc.get("score"), "as": rd.get("score"),
                "hp": [(loc.get("partials") or {}).get(f"partials{i}") for i in range(1, 5)],
                "ap": [(rd.get("partials") or {}).get(f"partials{i}") for i in range(1, 5)],
            })
    json.dump({"built": int(time.time()), "season": year, "comps": dict(COMPS), "games": games, "failed": failed},
              open(os.path.join(out, "el.json"), "w"), ensure_ascii=False, separators=(",", ":"))
    print(f"{len(games)} euroleague games; failed: {failed}")


if __name__ == "__main__":
    main()
