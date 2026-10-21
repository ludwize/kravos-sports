#!/usr/bin/env python3
"""Fetches sample responses from free sports APIs and stores them for offline inspection."""
import json, os, sys, urllib.request, datetime, traceback
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/129 Mobile Safari/537.36", "Accept": "application/json"}
log = open(os.path.join(OUT, "index.txt"), "w")

def get(name, url):
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=25) as r:
            body = r.read()
            code = r.status
    except urllib.error.HTTPError as e:
        body = e.read(); code = e.code
    except Exception as e:
        log.write(f"ERR  {name} {url} {e}\n"); return None
    open(os.path.join(OUT, name + ".json"), "wb").write(body)
    log.write(f"{code}  {len(body):>8}  {name}  {url}\n")
    try:
        return json.loads(body)
    except Exception:
        return None

S = "https://site.api.espn.com/apis/site/v2/sports"
today = datetime.date.today()
yd = (today - datetime.timedelta(days=1)).strftime("%Y%m%d")
td = today.strftime("%Y%m%d")

# --- aggregated "all leagues" scoreboards
get("header_soccer", f"https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=soccer&dates={yd}")
get("header_all", f"https://site.web.api.espn.com/apis/v2/scoreboard/header?dates={td}")
get("soccer_all_scoreboard", f"{S}/soccer/all/scoreboard?dates={yd}")

# --- league catalogs
get("core_soccer_leagues", "https://sports.core.api.espn.com/v2/sports/soccer/leagues?limit=400")
get("core_basketball_leagues", "https://sports.core.api.espn.com/v2/sports/basketball/leagues?limit=200")

# --- soccer
for lg in ["uefa.champions", "eng.1", "srb.1", "esp.1", "uefa.europa", "uefa.europa.conf", "fifa.world"]:
    sb = get(f"sb_{lg}", f"{S}/soccer/{lg}/scoreboard?dates={yd}")
cl = get("sb_cl_range", f"{S}/soccer/uefa.champions/scoreboard?dates={(today - datetime.timedelta(days=14)).strftime('%Y%m%d')}-{td}")
ev = None
for src in [cl] + [None]:
    if src:
        for e in src.get("events", []):
            if e.get("status", {}).get("type", {}).get("completed"):
                ev = e["id"]; break
if ev:
    get("summary_soccer", f"{S}/soccer/uefa.champions/summary?event={ev}")
get("standings_eng1", "https://site.api.espn.com/apis/v2/sports/soccer/eng.1/standings")
get("standings_srb1", "https://site.api.espn.com/apis/v2/sports/soccer/srb.1/standings")
get("teams_eng1", f"{S}/soccer/eng.1/teams")
get("team_sched", f"{S}/soccer/eng.1/teams/359/schedule")
get("search", "https://site.web.api.espn.com/apis/search/v2?query=partizan&limit=10")
get("leaders_eng1", "https://site.web.api.espn.com/apis/site/v3/sports/soccer/eng.1/leaders")

# --- basketball
nba = get("sb_nba", f"{S}/basketball/nba/scoreboard?dates={yd}")
if nba and nba.get("events"):
    get("summary_nba", f"{S}/basketball/nba/summary?event={nba['events'][0]['id']}")
for lg in ["euroleague", "mens-euroleague", "fiba", "eurocup"]:
    get(f"sb_bb_{lg}", f"{S}/basketball/{lg}/scoreboard?dates={yd}")

# --- euroleague official
get("el_games", "https://api-live.euroleague.net/v2/competitions/E/seasons/E2026/games")
get("el_standings", "https://api-live.euroleague.net/v2/competitions/E/seasons/E2026/rounds/1/basicstandings")
get("el_header", "https://live.euroleague.net/api/Header?gamecode=1&seasoncode=E2026")
get("el_boxscore", "https://live.euroleague.net/api/Boxscore?gamecode=1&seasoncode=E2026")
get("el_pbp", "https://live.euroleague.net/api/PlayByPlay?gamecode=1&seasoncode=E2026")
get("aba_test", "https://www.aba-liga.com/calendar.php?lang=en")

# --- other sports
for sp, lg in [("tennis", "atp"), ("tennis", "wta"), ("hockey", "nhl"), ("racing", "f1"), ("mma", "ufc"),
               ("football", "nfl"), ("baseball", "mlb"), ("volleyball", "fivb"), ("handball", "ehf"), ("rugby", "164205"),
               ("cricket", "8676")]:
    d = get(f"sb_{sp}_{lg}", f"{S}/{sp}/{lg}/scoreboard")
tn = None
try:
    tn = json.load(open(os.path.join(OUT, "sb_tennis_atp.json")))
except Exception:
    pass
get("sb_f1_range", f"{S}/racing/f1/scoreboard?dates=20261001-20261031")
get("core_sports", "https://sports.core.api.espn.com/v2/sports?limit=100")
log.close()
print(open(os.path.join(OUT, "index.txt")).read())
