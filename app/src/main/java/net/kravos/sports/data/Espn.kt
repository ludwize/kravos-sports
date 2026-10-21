package net.kravos.sports.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** ESPN public site API — F1, UFC, NFL, MLB. */
class Espn(private val net: Net) {
    private val S = "https://site.api.espn.com/apis/site/v2/sports"
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")

    suspend fun day(sport: Sport, date: LocalDate, today: Boolean): List<Event> {
        val path = sport.espnPath ?: return emptyList()
        // races and fight cards span several days: ask for a window and keep what touches the day
        val wide = sport == Sport.F1 || sport == Sport.MMA
        val q = if (wide) "${date.minusDays(3).format(YMD)}-${date.plusDays(3).format(YMD)}" else date.format(YMD)
        val body = net.get("$S/$path/scoreboard?dates=$q", if (today) 20 else 300)
        return withContext(Dispatchers.Default) { parse(JSONObject(body), sport, path) }
            .filter { e -> if (!wide) true else net.kravos.sports.ui.Fmt.localDate(e.start) == date }
    }

    private fun status(s: JSONObject?): Pair<Status, String?> {
        val t = s?.obj("type")
        return when (t?.str("state")) {
            "post" -> (if (t.str("name")?.contains("CANCEL") == true || t.str("name")?.contains("POSTPONED") == true) Status.cancelled(t.str("description") ?: "") else Status.FT) to null
            "in" -> Status.live(t.str("shortDetail") ?: "") to (t.str("shortDetail") ?: s.str("displayClock"))
            else -> Status.NS to null
        }
    }

    private fun parse(root: JSONObject, sport: Sport, path: String): List<Event> {
        val league = root.arr("leagues").optJSONObject(0)
        val lname = league?.str("name") ?: sport.label
        val t = Tournament(null, "${Src.ESPN}:$path", lname, null, if (sport == Sport.F1 || sport == Sport.MMA) "INT" else "USA",
            league?.arr("logos")?.optJSONObject(0)?.str("href"))
        return root.arr("events").mapObj { ev ->
            val comps = ev.arr("competitions")
            if (sport == Sport.F1) {
                // one row per session that falls in the window; the race carries the classification
                return@mapObj comps.mapObj { c ->
                    val abbr = c.obj("type")?.str("abbreviation") ?: ""
                    val (st, clock) = status(c.obj("status"))
                    val res = c.arr("competitors").mapObj { r ->
                        val a = r.obj("athlete")
                        RaceResult(r.optInt("order"), a?.str("displayName") ?: "", r.obj("team")?.str("displayName"), null, a?.obj("flag")?.str("href"))
                    }.sortedBy { it.pos }
                    val name = "${ev.str("shortName") ?: ev.str("name")} · ${sessionSr(abbr)}"
                    Event(
                        key = "${Src.ESPN}:$path:${ev.str("id")}:${c.str("id") ?: abbr}", sport = sport.slug, tournament = t.copy(name = ev.str("name") ?: lname, groupKey = "${Src.ESPN}:${ev.str("id")}"),
                        home = Team("x:1", name), away = Team("x:2", ev.obj("circuit")?.str("fullName") ?: ""),
                        homeScore = Score(null), awayScore = Score(null), status = st, start = isoToEpoch(c.str("date")?.let { if (it.length == 17) it.replace("Z", ":00Z") else it }),
                        clock = clock, race = res,
                    )
                }
            }
            comps.mapObj { c ->
                val cs = c.arr("competitors")
                fun side(home: Boolean): JSONObject? = (0 until cs.length()).mapNotNull { cs.optJSONObject(it) }.let { l ->
                    l.firstOrNull { it.str("homeAway") == (if (home) "home" else "away") } ?: l.getOrNull(if (home) 0 else 1)
                }
                val h = side(true) ?: return@mapObj null
                val a = side(false) ?: return@mapObj null
                fun team(o: JSONObject): Team {
                    val tm = o.obj("team"); val at = o.obj("athlete")
                    return if (tm != null) Team("${Src.ESPN}:$path:${tm.str("id")}", tm.str("displayName") ?: "", tm.str("shortDisplayName") ?: tm.str("displayName") ?: "", tm.str("abbreviation"), tm.str("logo"), sport = sport.slug)
                    else Team("${Src.ESPN}:$path:a${o.str("id")}", at?.str("displayName") ?: "", at?.str("shortName") ?: at?.str("displayName") ?: "", null, at?.obj("flag")?.str("href"), sport = sport.slug)
                }
                val (st, clock) = status(c.obj("status"))
                fun score(o: JSONObject) = Score(
                    if (st.notStarted || sport == Sport.MMA) null else o.int("score"),
                    if (st.notStarted) emptyList() else o.arr("linescores").mapObj { it.int("value") ?: it.dbl("value")?.toInt() },
                )
                val hs = score(h); val asc = score(a)
                val winner = when { h.bool("winner") -> 1; a.bool("winner") -> 2; else -> null }
                Event(
                    key = "${Src.ESPN}:$path:${c.str("id") ?: ev.str("id")}", sport = sport.slug,
                    tournament = if (sport == Sport.MMA) t.copy(name = ev.str("name") ?: lname, groupKey = "${Src.ESPN}:${ev.str("id")}") else t,
                    home = team(h), away = team(a), homeScore = hs, awayScore = asc, status = st,
                    start = isoToEpoch(c.str("date")?.let { if (it.length == 17) it.replace("Z", ":00Z") else it }),
                    winner = winner, clock = clock,
                    note = if (sport == Sport.MMA) c.obj("type")?.str("abbreviation") else null,
                )
            }
        }.flatten()
    }

    private fun sessionSr(a: String) = when (a) { "FP1" -> "1. trening"; "FP2" -> "2. trening"; "FP3" -> "3. trening"; "Qual" -> "Kvalifikacije"; "Race" -> "Trka"; "Sprint" -> "Sprint"; "SQ", "SS" -> "Sprint kvalifikacije"; else -> a }

    suspend fun match(key: String, live: Boolean): MatchDetail {
        // key: espn:<sport>/<league>:<compId>[:session]
        val parts = key.split(':')
        val path = parts[1]
        val id = parts[2]
        val sport = Sport.entries.firstOrNull { it.espnPath == path } ?: Sport.AMERICAN_FOOTBALL
        if (sport == Sport.F1 || sport == Sport.MMA) {
            // these come straight from the scoreboard; reuse the cached list around the event date
            val body = net.get("$S/$path/scoreboard", if (live) 20 else 300)
            val evs = parse(JSONObject(body), sport, path)
            val e = evs.firstOrNull { it.key == key } ?: error("Događaj nije pronađen")
            return MatchDetail(event = e)
        }
        val body = net.get("$S/$path/summary?event=$id", if (live) 15 else 300)
        val r = JSONObject(body)
        val hdr = r.obj("header")?.arr("competitions")?.optJSONObject(0)
        val wrap = JSONObject().put("leagues", org.json.JSONArray())
            .put("events", org.json.JSONArray().put(JSONObject().put("id", id).put("competitions", org.json.JSONArray().put(hdr ?: JSONObject()))))
        val ev = parse(wrap, sport, path).firstOrNull() ?: error("Utakmica nije pronađena")
        val teams = r.obj("boxscore")?.arr("teams")
        val stats = if (teams != null && teams.length() >= 2) {
            val h = teams.optJSONObject(0); val a = teams.optJSONObject(1)
            val hs = h?.arr("statistics")?.mapObj { s -> (s.str("label") ?: s.str("name") ?: "") to (s.str("displayValue") ?: "") }.orEmpty()
            val am = a?.arr("statistics")?.mapObj { s -> (s.str("label") ?: s.str("name") ?: "") to (s.str("displayValue") ?: "") }?.toMap().orEmpty()
            val items = hs.mapNotNull { (k, v) -> am[k]?.let { av -> StatItem(k, v, av, leading(v), leading(av)) } }
            // ESPN lists away team first in boxscore — align to home/away of the event
            val homeFirst = h?.obj("team")?.str("id")?.let { ev.home.key.endsWith(":$it") } ?: true
            val fixed = if (homeFirst) items else items.map { it.copy(home = it.away, away = it.home, homeValue = it.awayValue, awayValue = it.homeValue) }
            if (fixed.isEmpty()) emptyList() else listOf(StatPeriod("ALL", listOf(StatGroup("Pregled", fixed))))
        } else emptyList()
        val info = listOfNotNull(r.obj("gameInfo")?.obj("venue")?.str("fullName")?.let { InfoLine("Stadion", it) })
        return MatchDetail(event = ev.copy(tournament = ev.tournament ?: Tournament(null, path, sport.label)), stats = stats, info = info)
    }

    private fun leading(s: String) = Regex("""-?\d+(\.\d+)?""").find(s)?.value?.toDoubleOrNull() ?: 0.0
}
