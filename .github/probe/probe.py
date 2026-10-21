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
yd = (datetime.date.today() - datetime.timedelta(days=1)).strftime("%Y%m%d")
get("fm_match", f"{F}/matchDetails?matchId=6106333")
get("fm_match_srb", f"{F}/matchDetails?matchId=" + str(json.load(open('/dev/null')) if False else "0"))
# find a Serbian league match from league page
lg = get("fm_league182", f"{F}/leagues?id=182")
mid = None
try:
    for m in lg["fixtures"]["allMatches"]:
        if m.get("status", {}).get("finished"):
            mid = m["id"]
    get("fm_match_srb", f"{F}/matchDetails?matchId={mid}")
except Exception as e:
    log.write(f"no srb match {e}\n")
get("fm_team", f"{F}/teams?id=8695")
get("fm_team_partizan_search", f"{F}/search/suggest?term=partizan&lang=en")
get("fm_search2", f"https://apigw.fotmob.com/searchapi/suggest?term=partizan&lang=en")
get("fm_player", f"{F}/playerData?id=961995")
get("fm_alllegues", f"{F}/allLeagues?locale=en&country=SRB")
get("fm_live", f"{F}/matches?date={yd}&timezone=Europe%2FBelgrade&ccode3=SRB&includeNextDayLateNight=true")
# livescore details
for sp in ["basketball", "tennis", "hockey", "cricket", "handball", "volleyball"]:
    get(f"ls_date_{sp}", f"{L}/date/{sp}/{yd}/2?countryCode=RS&locale=en&MD=1")
get("ls_live_basket", f"{L}/live/basketball/2?countryCode=RS&locale=en&MD=1")
get("ls_sb_basket", f"{L}/scoreboard/basketball/1867553?locale=en")
get("ls_inc_basket", f"{L}/incidents/basketball/1867553?locale=en")
get("ls_stat_basket", f"{L}/statistics/basketball/1867553?locale=en")
get("ls_lineups_basket", f"{L}/lineups/basketball/1867553?locale=en")
get("ls_table_nba", f"{L}/leaguetable/basketball/26128?locale=en")
get("ls_stage_nba", f"{L}/stage/basketball/usa/nba/2?countryCode=RS&locale=en&MD=1")
log.close()
print(open(os.path.join(OUT, "index.txt")).read())
