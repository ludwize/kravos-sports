#!/usr/bin/env python3
"""Kravos Sports for the web: snapshot of data the browser cannot fetch itself.

The EuroLeague v2 API sends no CORS header, so the runner downloads the game lists
(Evroliga + Evrokup) and the page reads them from el.json. Live scores come straight
from the browser (live.euroleague.net allows it). Runs on GitHub every ~6 minutes (self-chaining web.yml).

usage: build.py <out-dir>
"""
import json, os, sys, time, urllib.request
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

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


LS = "https://prod-cdn-public-api.livescore.com/v1/api/app"
# LiveScore refuses browser requests (403 when an Origin header is present), so basketball
# outside the EuroLeague/NBA (ABA, national leagues, FIBA cups...) is snapshotted here.
LS_SKIP = {"euroleague", "eurocup", "nba"}
LS_FIN = {"FT", "AET", "AP", "AOT", "Ret.", "W.O.", "AW", "Fin"}


def ls_status(eps):
    if eps == "NS":
        return {"k": "ns"}
    if eps in LS_FIN:
        return {"k": "ft", "label": "Prod." if eps in ("AOT", "AET") else "Kraj"}
    if eps.startswith(("Canc", "Postp", "Aband")) or eps == "Susp.":
        return {"k": "x", "label": {"Canc.": "Otkazano", "Postp.": "Odloženo"}.get(eps, eps)}
    if len(eps) == 2 and "Q" in eps and any(c.isdigit() for c in eps):
        return {"k": "live", "label": f"{next(c for c in eps if c.isdigit())}. četv."}
    return {"k": "live", "label": {"HT": "Poluvreme", "OT": "Produžetak", "Pause": "Pauza"}.get(eps, "Pauza" if eps.startswith("Break") else eps)}


def ls_basketball(out):
    tz = ZoneInfo("Europe/Belgrade")
    now = datetime.now(tz)
    off = int(now.utcoffset().total_seconds() // 3600)
    events, failed = [], []
    for delta in (-1, 0, 1):
        d = (now + timedelta(days=delta)).strftime("%Y%m%d")
        j = get(f"{LS}/date/basketball/{d}/{off}?countryCode=RS&locale=en&MD=1")
        if not j:
            failed.append(d)
            continue
        for st in j.get("Stages", []):
            cnm = st.get("Cnm") or ""
            comp = st.get("CompN") or ""
            snm = st.get("Snm") or ""
            if (comp or cnm).lower().replace(" ", "") in LS_SKIP:
                continue
            if cnm.lower() == "adriatic" or "aba league" in snm.lower():
                name, sub, country = "ABA liga", snm.split(":", 1)[1].strip().replace("Group", "Grupa") if ":" in snm else "", "ABA"
            else:
                name, sub, country = comp or cnm, "" if snm in ("Regular season", comp) else snm, cnm
            for e in st.get("Events", []):
                eps = e.get("Eps") or "NS"
                stt = ls_status(eps)
                try:
                    start = datetime.strptime(str(e.get("Esd")), "%Y%m%d%H%M%S").replace(tzinfo=timezone(timedelta(hours=off)))
                except ValueError:
                    continue
                t1, t2 = (e.get("T1") or [{}])[0], (e.get("T2") or [{}])[0]
                shown = stt["k"] != "ns"
                q = lambda side: [num(e.get(f"Tr{side}Q{i}")) for i in range(1, 5) if e.get(f"Tr{side}Q{i}") is not None] if shown else []
                img = lambda t: f"https://lsm-static-prod.livescore.com/medium/{t['Img']}" if t.get("Img") else None
                events.append({
                    "id": e.get("Eid"), "lg": name, "sub": sub, "cc": country, "utc": start.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "st": stt, "h": {"name": t1.get("Nm", "?"), "logo": img(t1)}, "a": {"name": t2.get("Nm", "?"), "logo": img(t2)},
                    "hs": num(e.get("Tr1")) if shown else None, "as": num(e.get("Tr2")) if shown else None, "hp": q(1), "ap": q(2),
                })
    uniq = {ev["id"]: ev for ev in events}
    json.dump({"built": int(time.time()), "events": list(uniq.values()), "failed": failed},
              open(os.path.join(out, "ls.json"), "w"), ensure_ascii=False, separators=(",", ":"))
    print(f"{len(uniq)} livescore basketball events; failed: {failed}")


def num(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return None


def club(c):
    c = c or {}
    code = c.get("code") or "?"
    return {"code": code, "name": c.get("name") or c.get("abbreviatedName") or c.get("editorialName") or code,
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
    ls_basketball(out)


if __name__ == "__main__":
    main()
