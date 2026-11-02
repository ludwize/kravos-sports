#!/usr/bin/env python3
"""Is ABA (Partizan) in LiveScore basketball today, and can the runner fetch it?"""
import json, os, sys, urllib.request, urllib.error, datetime
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "index.txt"), "w")
L = "https://prod-cdn-public-api.livescore.com/v1/api/app"
MOB = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"
DESK = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
d = datetime.date.today().strftime("%Y%m%d")
for label, ua, extra in (("mobile", MOB, {}), ("desk", DESK, {}), ("desk+origin", DESK, {"Origin": "https://sports.kravos.net", "Referer": "https://sports.kravos.net/"}), ("bare", "python-urllib", {})):
    for kind, url in (("date", f"{L}/date/basketball/{d}/1?countryCode=RS&locale=en&MD=1"), ("live", f"{L}/live/basketball/1?countryCode=RS&locale=en&MD=1")):
        h = {"User-Agent": ua, "Accept": "application/json, */*"}; h.update(extra)
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=25) as r:
                b = r.read(); code = r.status; acao = r.headers.get("access-control-allow-origin")
        except urllib.error.HTTPError as e:
            b = e.read(); code = e.code; acao = e.headers.get("access-control-allow-origin")
        line = f"{label:12} {kind:5} {code} acao={acao} len={len(b)}"
        if code == 200:
            j = json.loads(b)
            comps = [(s.get("CompN") or s.get("Cnm"), s.get("Cnm"), len(s.get("Events", []))) for s in j.get("Stages", [])]
            aba = [s for s in j.get("Stages", []) if "aba" in (s.get("CompN", "") + s.get("Snm", "") + s.get("Cnm", "")).lower() or "adriat" in (s.get("CompN", "") + s.get("Cnm", "")).lower()]
            line += f" stages={len(comps)} aba={[ (s.get('CompN'), s.get('Cnm'), s.get('Snm')) for s in aba]}"
            for s in j.get("Stages", []):
                for e in s.get("Events", []):
                    names = (e.get("T1") or [{}])[0].get("Nm", "") + " - " + (e.get("T2") or [{}])[0].get("Nm", "")
                    if "partizan" in names.lower():
                        line += f"\n    PARTIZAN: {s.get('CompN')}|{s.get('Cnm')} {names} Eps={e.get('Eps')} {e.get('Tr1')}:{e.get('Tr2')} Esd={e.get('Esd')}"
            if label == "mobile" and kind == "date":
                open(os.path.join(OUT, "ls_bb_today.json"), "wb").write(b)
                line += "\n    comps: " + "; ".join(f"{a}/{c}({n})" for a, c, n in comps[:40])
        log.write(line + "\n")
log.close(); print(open(os.path.join(OUT, "index.txt")).read())
