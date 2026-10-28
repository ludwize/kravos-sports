#!/usr/bin/env python3
"""Does FotMob matchDetails carry stats when called like a browser on sports.kravos.net?"""
import json, os, sys, urllib.request, urllib.error, datetime
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "index.txt"), "w")
F = "https://www.fotmob.com/api/data"
DESK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
MOB = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
def get(url, h):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=25) as r:
            return r.status, dict(r.headers), r.read()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read()
d = datetime.date.today()
ids = []
for off in (0, -1):
    dd = (d + datetime.timedelta(days=off)).strftime("%Y%m%d")
    s, _, b = get(f"{F}/matches?date={dd}&timezone=Europe%2FBelgrade&ccode3=SRB", {"User-Agent": DESK})
    j = json.loads(b)
    for lg in j.get("leagues", []):
        if (lg.get("primaryId") or lg.get("id")) in (47, 87, 55, 54, 53, 42, 73, 182):
            for m in lg["matches"]:
                st = m.get("status", {})
                if st.get("finished") or st.get("ongoing"):
                    ids.append((lg["name"], m["id"]))
log.write(f"candidates {ids[:6]}\n")
for name, mid in ids[:3]:
    for label, h in (("desk+origin", {"User-Agent": DESK, "Origin": "https://sports.kravos.net", "Referer": "https://sports.kravos.net/", "Accept": "*/*"}),
                     ("mobile", {"User-Agent": MOB, "Accept": "application/json, */*"})):
        s, hd, b = get(f"{F}/matchDetails?matchId={mid}", h)
        try:
            j = json.loads(b); c = j.get("content") or {}
            st = c.get("stats")
            per = (st or {}).get("Periods") if isinstance(st, dict) else None
            info = f"keys={sorted(c.keys())[:14]} stats={type(st).__name__} periods={list(per.keys()) if per else None}"
            if per and per.get("All"): info += f" groups={len(per['All'].get('stats', []))}"
        except Exception as e:
            info = f"not json {b[:120]!r}"
        log.write(f"{name} {mid} {label}: {s} acao={hd.get('Access-Control-Allow-Origin') or hd.get('access-control-allow-origin')} len={len(b)} {info}\n")
        if label == "desk+origin":
            open(os.path.join(OUT, f"md_{mid}.json"), "wb").write(b)
log.close(); print(open(os.path.join(OUT, "index.txt")).read())
