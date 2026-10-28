#!/usr/bin/env python3
"""CORS check for the EuroLeague live boxscore."""
import os, sys, urllib.request, urllib.error
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "index.txt"), "w")
for url in ["https://live.euroleague.net/api/Boxscore?gamecode=1&seasoncode=E2026",
            "https://live.euroleague.net/api/Header?gamecode=1&seasoncode=E2026"]:
    for method in ("GET", "OPTIONS"):
        h = {"User-Agent": "Mozilla/5.0 Chrome/130", "Origin": "https://sports.kravos.net"}
        if method == "OPTIONS": h["Access-Control-Request-Method"] = "GET"
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=h, method=method), timeout=25) as r:
                log.write(f"{method} {r.status} acao={r.headers.get('access-control-allow-origin')} len={len(r.read())} {url}\n")
        except urllib.error.HTTPError as e:
            log.write(f"{method} {e.code} acao={e.headers.get('access-control-allow-origin')} {url}\n")
log.close(); print(open(os.path.join(OUT, "index.txt")).read())
