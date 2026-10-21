package net.kravos.sports.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** EuroLeague (E) and EuroCup (U) official API: schedule, live header, box score, standings. */
class Euroleague(private val net: Net) {
    private val V2 = "https://api-live.euroleague.net/v2/competitions"
    private val LIVE = "https://live.euroleague.net/api"

    companion object {
        val COMPS = listOf("E" to "Evroliga", "U" to "Evrokup")
        fun seasonYear(d: LocalDate = LocalDate.now()) = if (d.monthValue >= 8) d.year else d.year - 1
        fun compName(c: String) = COMPS.firstOrNull { it.first == c }?.second ?: c
    }

    private fun season(comp: String, year: Int = seasonYear()) = "$comp$year"

    private fun tour(comp: String) = Tournament(
        key = "${Src.EL}:$comp", groupKey = "${Src.EL}:$comp", name = compName(comp),
        categoryName = "Evropa", country = "EUR", priority = if (comp == "E") 100 else 90,
    )

    private fun club(c: JSONObject?): Team {
        val code = c?.str("code") ?: "?"
        return Team(
            key = "${Src.EL}:$code",
            name = c?.str("editorialName") ?: c?.str("abbreviatedName") ?: code,
            shortName = c?.str("abbreviatedName") ?: c?.str("editorialName") ?: code,
            code = c?.str("tvCode") ?: code,
            logo = c?.obj("images")?.str("crest"),
            country = c?.obj("country")?.str("name"),
            sport = "basketball",
        )
    }

    private data class Raw(val comp: String, val code: Int, val o: JSONObject)

    private suspend fun games(comp: String, ttl: Int = 300): List<Raw> {
        val body = net.get("$V2/$comp/seasons/${season(comp)}/games", ttl)
        return withContext(Dispatchers.Default) {
            JSONObject(body).arr("data").mapObj { g -> g.int("gameCode")?.let { Raw(comp, it, g) } }
        }
    }

    private fun eventOf(r: Raw): Event {
        val g = r.o
        val played = g.bool("played")
        val l = g.obj("local"); val rd = g.obj("road")
        fun score(s: JSONObject?): Score {
            val p = s?.obj("partials")
            val per = (1..4).map { p?.int("partials$it") }
            val ex = p?.obj("extraPeriods")?.let { e -> e.keys().asSequence().mapNotNull { e.int(it) }.sum().takeIf { it > 0 } }
            return Score(if (played) s?.int("score") else null, if (played) per else emptyList(), overtime = ex)
        }
        val hs = score(l); val asc = score(rd)
        return Event(
            key = "${Src.EL}:${r.comp}:${r.code}",
            sport = "basketball",
            tournament = tour(r.comp),
            home = club(l?.obj("club")), away = club(rd?.obj("club")),
            homeScore = hs, awayScore = asc,
            status = if (played) Status.FT else Status.NS,
            start = isoToEpoch(g.str("utcDate")),
            winner = if (played) { val a = hs.current ?: 0; val b = asc.current ?: 0; if (a > b) 1 else if (b > a) 2 else null } else null,
            roundName = g.str("roundName")?.replace("Round", "Kolo") ?: g.str("phaseType"),
        )
    }

    /** Live state from the live feed (score, quarter, clock). */
    private suspend fun withLive(e: Event, ttl: Int = 10): Event {
        val (comp, code) = e.key.split(':').let { it[1] to it[2] }
        val h = runCatching { JSONObject(net.get("$LIVE/Header?gamecode=$code&seasoncode=${season(comp)}", ttl, allowStale = false)) }.getOrNull() ?: return e
        val a = h.int("ScoreA"); val b = h.int("ScoreB")
        val live = h.bool("Live")
        val q = (1..4).map { i -> h.int("ScoreQuarter${i}A") to h.int("ScoreQuarter${i}B") }
        // header gives cumulative scores per quarter -> convert to per-quarter
        fun per(sel: (Pair<Int?, Int?>) -> Int?): List<Int?> {
            var prev = 0
            val out = mutableListOf<Int?>()
            for (v in q.map { sel(it) }) {
                if (v == null || v <= 0) break
                out += v - prev
                prev = v
            }
            return out
        }
        val now = System.currentTimeMillis() / 1000
        val finished = !live && (a ?: 0) > 0 && now - e.start > 90 * 60
        val quarter = h.str("Quarter")?.trim()
        val clock = if (live) listOfNotNull(quarter?.let { if (it.all(Char::isDigit)) "Q$it" else it }, h.str("RemainingPartialTime")?.trim()).joinToString(" ") else null
        return when {
            live -> e.copy(status = Status.live(quarter ?: ""), clock = clock, homeScore = Score(a, per { it.first }), awayScore = Score(b, per { it.second }))
            finished -> e.copy(status = Status.FT, homeScore = Score(a, per { it.first }), awayScore = Score(b, per { it.second }),
                winner = if ((a ?: 0) > (b ?: 0)) 1 else 2)
            else -> e
        }
    }

    suspend fun day(date: LocalDate, today: Boolean): List<Event> = coroutineScope {
        val zone = ZoneId.systemDefault()
        COMPS.map { (c, _) -> async { runCatching { games(c, if (today) 120 else 600) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
            .map { eventOf(it) }
            .filter { java.time.Instant.ofEpochSecond(it.start).atZone(zone).toLocalDate() == date }
            .map { e ->
                async {
                    val now = System.currentTimeMillis() / 1000
                    if (!e.status.finished && e.start <= now + 60) withLive(e) else e
                }
            }.awaitAll()
    }

    // ---------- match ----------

    suspend fun match(comp: String, code: String, live: Boolean): MatchDetail = coroutineScope {
        val ttl = if (live) 10 else 300
        val all = async { games(comp) }
        val box = async { runCatching { JSONObject(net.get("$LIVE/Boxscore?gamecode=$code&seasoncode=${season(comp)}", ttl)) }.getOrNull() }
        val base = all.await().firstOrNull { it.code.toString() == code }?.let { eventOf(it) } ?: error("Utakmica nije pronađena")
        val ev = if (base.status.finished) base else withLive(base, ttl)
        val b = box.await()
        val stats = b?.arr("Stats")
        val cols = listOf("MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "2P", "3P", "FT", "PIR", "+/-")
        fun rows(i: Int): List<BoxRow> = stats?.optJSONObject(i)?.arr("PlayersStats")?.mapObj { p ->
            val name = p.str("Player")?.let { prettyName(it) } ?: return@mapObj null
            BoxRow(
                name = name, number = p.str("Dorsal"), starter = p.optInt("IsStarter") == 1,
                values = listOf(
                    p.str("Minutes")?.substringBefore(':') ?: "-", p.optInt("Points").toString(), p.optInt("TotalRebounds").toString(),
                    p.optInt("Assistances").toString(), p.optInt("Steals").toString(), p.optInt("BlocksFavour").toString(), p.optInt("Turnovers").toString(),
                    "${p.optInt("FieldGoalsMade2")}/${p.optInt("FieldGoalsAttempted2")}", "${p.optInt("FieldGoalsMade3")}/${p.optInt("FieldGoalsAttempted3")}",
                    "${p.optInt("FreeThrowsMade")}/${p.optInt("FreeThrowsAttempted")}", p.optInt("Valuation").toString(), p.optInt("Plusminus").toString(),
                ),
            )
        }.orEmpty()
        val box2 = if (stats != null && stats.length() >= 2) BoxScore(cols, rows(0), rows(1)) else null
        val teamStats = if (stats != null && stats.length() >= 2) {
            val h = stats.optJSONObject(0)?.obj("totr"); val a = stats.optJSONObject(1)?.obj("totr")
            if (h != null && a != null) listOf(StatPeriod("ALL", listOf(StatGroup("Pregled", basketballTeamStats(h, a))))) else emptyList()
        } else emptyList()
        val info = listOfNotNull(
            b?.str("Attendance")?.takeIf { it != "0" }?.let { InfoLine("Gledalaca", it) },
            b?.str("Referees")?.let { InfoLine("Sudije", it) },
        )
        MatchDetail(
            event = ev, stats = teamStats, box = box2, info = info,
            tableLeague = "${Src.EL}:$comp",
        )
    }

    private fun basketballTeamStats(h: JSONObject, a: JSONObject): List<StatItem> {
        fun pct(m: String, at: String, o: JSONObject): Pair<String, Double> {
            val mm = o.optInt(m); val aa = o.optInt(at)
            val p = if (aa > 0) mm * 100.0 / aa else 0.0
            return "$mm/$aa (${p.toInt()}%)" to p
        }
        fun pair(name: String, m: String, at: String): StatItem {
            val (hs, hv) = pct(m, at, h); val (as_, av) = pct(m, at, a)
            return StatItem(name, hs, as_, hv, av)
        }
        fun simple(name: String, k: String, lower: Boolean = false) =
            StatItem(name, h.optInt(k).toString(), a.optInt(k).toString(), h.optInt(k).toDouble(), a.optInt(k).toDouble(), lower)
        return listOf(
            pair("Šut za 2", "FieldGoalsMade2", "FieldGoalsAttempted2"),
            pair("Šut za 3", "FieldGoalsMade3", "FieldGoalsAttempted3"),
            pair("Slobodna bacanja", "FreeThrowsMade", "FreeThrowsAttempted"),
            simple("Skokovi", "TotalRebounds"), simple("Skokovi u napadu", "OffensiveRebounds"), simple("Skokovi u odbrani", "DefensiveRebounds"),
            simple("Asistencije", "Assistances"), simple("Ukradene lopte", "Steals"), simple("Blokade", "BlocksFavour"),
            simple("Izgubljene lopte", "Turnovers", true), simple("Faulovi", "FoulsCommited", true), simple("PIR", "Valuation"),
        )
    }

    private fun prettyName(s: String): String {
        // "JONES, CHRIS" -> "Chris Jones"
        val parts = s.split(',').map { it.trim() }
        val cap = { x: String -> x.lowercase().split(' ', '-').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
        return if (parts.size == 2) "${cap(parts[1])} ${cap(parts[0])}" else cap(s)
    }

    // ---------- league / team ----------

    suspend fun league(comp: String): LeaguePage = coroutineScope {
        val all = games(comp).map { eventOf(it) }
        val lastRound = games(comp).filter { it.o.bool("played") }.maxOfOrNull { it.o.optInt("round") } ?: 1
        val tables = runCatching {
            JSONArray(net.get("$V2/$comp/seasons/${season(comp)}/rounds/$lastRound/standings", 600)).mapObj { g ->
                val rows = g.arr("standings").mapObj { s ->
                    val d = s.obj("data") ?: return@mapObj null
                    StandingRow(
                        team = club(s.obj("club")), position = d.optInt("position"), played = d.optInt("gamesPlayed"),
                        wins = d.optInt("gamesWon"), draws = 0, losses = d.optInt("gamesLost"),
                        scoresFor = d.optInt("pointsFavour"), scoresAgainst = d.optInt("pointsAgainst"), points = null,
                    )
                }
                StandingTable(g.obj("group")?.str("rawName")?.let { if (it == "Regular Season") "" else it } ?: "", rows)
            }
        }.getOrDefault(emptyList())
        LeaguePage(
            key = "${Src.EL}:$comp", name = compName(comp), country = "Evropa", logo = null, sport = "basketball",
            seasons = emptyList(), season = "${seasonYear()}/${(seasonYear() + 1) % 100}",
            tables = tables,
            results = all.filter { it.status.finished }.sortedByDescending { it.start },
            fixtures = all.filter { !it.status.finished }.sortedBy { it.start },
            top = emptyList(),
        )
    }

    suspend fun team(code: String): TeamPage = coroutineScope {
        val evs = COMPS.map { (c, _) -> async { runCatching { games(c) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
            .map { eventOf(it) }.filter { it.home.key == "${Src.EL}:$code" || it.away.key == "${Src.EL}:$code" }
        val t = evs.firstOrNull()?.let { if (it.home.key.endsWith(":$code")) it.home else it.away } ?: Team("${Src.EL}:$code", code, sport = "basketball")
        val comp = evs.firstOrNull()?.key?.split(':')?.getOrNull(1) ?: "E"
        val table = runCatching { league(comp).tables.firstOrNull() }.getOrNull()
        TeamPage(
            team = t,
            upcoming = evs.filter { !it.status.finished }.sortedBy { it.start },
            past = evs.filter { it.status.finished }.sortedByDescending { it.start },
            tables = listOfNotNull(table?.let { "${Src.EL}:$comp" to it.copy(name = compName(comp)) }),
            leagueKey = "${Src.EL}:$comp",
        )
    }

    suspend fun clubs(): List<Team> = coroutineScope {
        COMPS.map { (c, _) ->
            async {
                runCatching {
                    JSONObject(net.get("$V2/$c/seasons/${season(c)}/clubs", 24 * 3600)).arr("data").mapObj { club(it) }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().distinctBy { it.key }
    }
}
