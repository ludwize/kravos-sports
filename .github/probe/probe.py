#!/usr/bin/env python3
"""CORS probe: can a static page on github.io call these APIs straight from the browser?"""
import os, sys, urllib.request, urllib.error, datetime
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
ORIGIN = "https://ludwize.github.io"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/129 Safari/537.36"
d = datetime.date.today().strftime("%Y%m%d")
F = "https://www.fotmob.com/api/data"
L = "https://prod-cdn-public-api.livescore.com/v1/api/app"
E = "https://site.api.espn.com/apis/site/v2/sports"
URLS = {
    "fotmob_matches": f"{F}/matches?date={d}&timezone=Europe%2FBelgrade&ccode3=SRB",
    "fotmob_img": "https://images.fotmob.com/image_resources/logo/teamlogo/7998_small.png",
    "fotmob_data": "https://data.fotmob.com/tables.ext.943230.fot.gz",
    "livescore_date": f"{L}/date/soccer/{d}/2?countryCode=RS&locale=en&MD=1",
    "livescore_img": "https://lsm-static-prod.livescore.com/medium/enet/58542.png",
    "espn_nba": f"{E}/basketball/nba/scoreboard",
    "espn_nhl": f"{E}/hockey/nhl/scoreboard",
    "euroleague_v2": "https://api-live.euroleague.net/v2/competitions/E/seasons/E2026/games",
    "euroleague_live": "https://live.euroleague.net/api/Header?gamecode=1&seasoncode=E2026",
}
log = open(os.path.join(OUT, "index.txt"), "w")
for name, url in URLS.items():
    out = {}
    for method in ("GET", "OPTIONS"):
        h = {"User-Agent": UA, "Origin": ORIGIN, "Accept": "application/json"}
        if method == "OPTIONS":
            h["Access-Control-Request-Method"] = "GET"
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=h, method=method), timeout=25) as r:
                out[method] = (r.status, r.headers.get("access-control-allow-origin"), len(r.read()))
        except urllib.error.HTTPError as e:
            out[method] = (e.code, e.headers.get("access-control-allow-origin"), len(e.read()))
        except Exception as e:
            out[method] = ("ERR", str(e)[:60], 0)
    log.write(f"{name:16} GET {out['GET']}  OPTIONS {out['OPTIONS']}\n  {url}\n")
log.close()
print(open(os.path.join(OUT, "index.txt")).read())
