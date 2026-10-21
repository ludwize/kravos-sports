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
S = "https://site.api.espn.com/apis/site/v2/sports"
yd = (datetime.date.today() - datetime.timedelta(days=1)).strftime("%Y%m%d")
sat = "20261018"
get("espn_partizan_sched", f"{S}/soccer/all/teams/541/schedule")
get("espn_partizan_team", f"{S}/soccer/all/teams/541")
get("espn_all_sat", f"{S}/soccer/all/scoreboard?dates={sat}")
get("espn_bb_header_sat", f"https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=basketball&dates={sat}")
# fotmob
get("fotmob_matches", f"https://www.fotmob.com/api/data/matches?date={yd}&timezone=Europe%2FBelgrade&ccode3=SRB")
get("fotmob_matches_old", f"https://www.fotmob.com/api/matches?date={yd}")
get("fotmob_league_srb", "https://www.fotmob.com/api/leagues?id=182")
get("fotmob_league_srb2", "https://www.fotmob.com/api/data/leagues?id=182")
# thesportsdb (free public key 3)
get("tsdb_srb_search", "https://www.thesportsdb.com/api/v1/json/3/search_all_leagues.php?c=Serbia")
get("tsdb_day", f"https://www.thesportsdb.com/api/v1/json/3/eventsday.php?d={datetime.date.today()-datetime.timedelta(days=1)}&s=Soccer")
# euroleague
get("el_v2_standings", "https://api-live.euroleague.net/v2/competitions/E/seasons/E2026/rounds/4/standings")
get("el_v3_standings", "https://api-live.euroleague.net/v3/competitions/E/seasons/E2026/rounds/4/basicstandings")
get("el_v1_standings", "https://api-live.euroleague.net/v1/standings?seasonCode=E2026&roundNumber=4")
get("el_v2_clubs", "https://api-live.euroleague.net/v2/competitions/E/seasons/E2026/clubs")
get("eurocup_games", "https://api-live.euroleague.net/v2/competitions/U/seasons/U2026/games")
# ABA league
get("aba_api", "https://www.aba-liga.com/api/game/schedule")
# livescore.com (cdn api)
get("lsc_soccer", f"https://prod-cdn-public-api.livescore.com/v1/api/app/date/soccer/{yd}/2?countryCode=RS&locale=en&MD=1")
get("lsc_basket", f"https://prod-cdn-public-api.livescore.com/v1/api/app/date/basketball/{yd}/2?countryCode=RS&locale=en&MD=1")
log.close()
print(open(os.path.join(OUT, "index.txt")).read())
