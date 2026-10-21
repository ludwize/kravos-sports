#!/usr/bin/env python3
import json, os, sys, urllib.request, datetime
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/129 Mobile Safari/537.36", "Accept": "application/json"}
log = open(os.path.join(OUT, "index.txt"), "w")
def get(name, url, headers=None):
    h = dict(UA); h.update(headers or {})
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=25) as r:
            body = r.read(); code = r.status
    except urllib.error.HTTPError as e:
        body = e.read(); code = e.code
    except Exception as e:
        log.write(f"ERR  {name} {url} {e}\n"); return None
    open(os.path.join(OUT, name + ".json"), "wb").write(body)
    log.write(f"{code}  {len(body):>8}  {name}  {url}\n")
    try: return json.loads(body)
    except Exception: return None
F = "https://www.fotmob.com/api/data"
L = "https://prod-cdn-public-api.livescore.com/v1/api/app"
get("fm_heat", f"{F}/heatmap/match/6106333/heatmaps?heatmapUrl=https%3A%2F%2Fpub.fotmob.com%2Fprod%2Fdb%2Fapi%2Fheatmap%2Fmatch%2F6106333")
get("fm_heat_pub", "https://pub.fotmob.com/prod/db/api/heatmap/match/6106333")
get("fm_table", "https://data.fotmob.com/tables.ext.943230.fot.gz")
get("fm_today", f"{F}/matches?date={datetime.date.today().strftime('%Y%m%d')}&timezone=Europe%2FBelgrade&ccode3=SRB")
get("fm_stats_goals", "https://data.fotmob.com/stats/182/season/1000000451/goals.json")
get("fm_league182_2025", f"{F}/leagues?id=182&season=2025%2F2026")
get("ls_sb_tennis", f"{L}/date/tennis/20261020/2?countryCode=RS&locale=en&MD=1")
get("ls_live_all_soccer", f"{L}/live/soccer/2?countryCode=RS&locale=en&MD=1")
get("logo_fm", "https://images.fotmob.com/image_resources/logo/teamlogo/7998_small.png")
get("logo_fm_league", "https://images.fotmob.com/image_resources/logo/leaguelogo/182.png")
get("logo_ls", "https://lsm-static-prod.livescore.com/medium/enet/58542.png")
get("logo_el", "https://media-cdn.incrowdsports.com/23610a1b-1c2e-4d2a-8fe4-ac2f8e400632.png")
log.close()
print(open(os.path.join(OUT, "index.txt")).read())
