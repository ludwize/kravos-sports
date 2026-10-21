package net.kravos.sports.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

fun isoToEpoch(s: String?): Long {
    if (s.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(s).epochSecond }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(s).toEpochSecond() }.getOrNull()
        ?: runCatching { Instant.parse(s.trim().replace(" ", "T") + (if (s.endsWith("Z")) "" else "Z")).epochSecond }.getOrNull()
        ?: 0L
}

/** FotMob — football worldwide, incl. Serbian SuperLiga. */
class FotMob(private val net: Net) {
    private val F = "https://www.fotmob.com/api/data"
    private val TZ = "Europe%2FBelgrade"

    companion object {
        fun teamLogo(id: String) = "https://images.fotmob.com/image_resources/logo/teamlogo/${id}_small.png"
        fun leagueLogo(id: String) = "https://images.fotmob.com/image_resources/logo/leaguelogo/$id.png"
        fun playerPhoto(id: String) = "https://images.fotmob.com/image_resources/playerimages/$id.png"
        private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    private fun team(id: Any?, name: String?, short: String? = null): Team {
        val i = id?.toString() ?: "0"
        val n = name ?: "?"
        return Team("${Src.FM}:$i", n, short ?: n, null, teamLogo(i), sport = "football")
    }

    /** Parses the compact status object used on lists and pages. */
    private fun status(st: JSONObject?): Triple<Status, String?, Long> {
        st ?: return Triple(Status.NS, null, 0)
        val start = isoToEpoch(st.str("utcTime"))
        val reason = st.obj("reason")
        val short = reason?.str("short")
        val s = when {
            st.bool("cancelled") -> Status.cancelled(reason?.str("long") ?: "Cancelled")
            st.bool("finished") -> Status("finished", when (short) { "AET" -> "AET"; "Pen" -> "AP"; else -> "Ended" })
            st.bool("ongoing") || (st.bool("started") && !st.bool("finished")) -> Status.live(short ?: "")
            else -> Status.NS
        }
        val clock = st.obj("liveTime")?.str("short")?.replace("‎", "")?.replace("’", "'")
            ?: if (s.live && short == "HT") "Poluvreme" else null
        return Triple(s, clock, start)
    }

    private fun scoreFromStr(s: String?): Pair<Int?, Int?> {
        val p = s?.split("-")?.map { it.trim().toIntOrNull() } ?: return null to null
        return p.getOrNull(0) to p.getOrNull(1)
    }

    private fun winnerOf(h: Int?, a: Int?, finished: Boolean): Int? =
        if (!finished || h == null || a == null) null else if (h > a) 1 else if (a > h) 2 else 3

    // ---------- day / live ----------

    suspend fun day(date: LocalDate, today: Boolean): List<Event> {
        val body = net.get("$F/matches?date=${date.format(YMD)}&timezone=$TZ&ccode3=SRB", if (today) 15 else if (date.isBefore(LocalDate.now())) 600 else 120)
        return withContext(Dispatchers.Default) {
            val root = JSONObject(body)
            root.arr("leagues").mapObj { lg ->
                val primary = lg.int("primaryId") ?: lg.int("id") ?: 0
                val t = Tournament(
                    key = "${Src.FM}:$primary",
                    groupKey = "${Src.FM}:${lg.int("id")}",
                    name = lg.str("name") ?: "",
                    categoryName = countryName(lg.str("ccode")),
                    country = lg.str("ccode"),
                    logo = leagueLogo(primary.toString()),
                )
                lg.arr("matches").mapObj { m -> listMatch(m, t) }
            }.flatten()
        }
    }

    private fun listMatch(m: JSONObject, t: Tournament?): Event? {
        val id = m.str("id") ?: return null
        val h = m.obj("home") ?: return null
        val a = m.obj("away") ?: return null
        val (st, clock, start0) = status(m.obj("status"))
        val start = if (start0 > 0) start0 else (m.long("timeTS") ?: 0L) / 1000
        val hs = h.int("score"); val asc = a.int("score")
        val (sh, sa) = scoreFromStr(m.obj("status")?.str("scoreStr"))
        val showScore = !st.notStarted
        val hS = if (showScore) sh ?: hs else null
        val aS = if (showScore) sa ?: asc else null
        return Event(
            key = "${Src.FM}:$id",
            sport = "football",
            tournament = t,
            home = team(h.opt("id"), h.str("longName") ?: h.str("name"), h.str("shortName") ?: h.str("name")),
            away = team(a.opt("id"), a.str("longName") ?: a.str("name"), a.str("shortName") ?: a.str("name")),
            homeScore = Score(hS, penalties = h.int("penScore")),
            awayScore = Score(aS, penalties = a.int("penScore")),
            status = st,
            start = start,
            winner = winnerOf(hS, aS, st.finished),
            clock = clock,
            homeRed = h.int("redCards") ?: 0,
            awayRed = a.int("redCards") ?: 0,
        )
    }

    // ---------- match ----------

    suspend fun match(id: String, live: Boolean): MatchDetail {
        val body = net.get("$F/matchDetails?matchId=$id", if (live) 10 else 300)
        return withContext(Dispatchers.Default) { parseMatch(id, JSONObject(body)) }
    }

    private fun parseMatch(id: String, root: JSONObject): MatchDetail {
        val g = root.obj("general") ?: JSONObject()
        val hd = root.obj("header") ?: JSONObject()
        val c = root.obj("content") ?: JSONObject()
        val teams = hd.arr("teams")
        val ht = teams.optJSONObject(0) ?: JSONObject()
        val at = teams.optJSONObject(1) ?: JSONObject()
        val homeId = ht.str("id") ?: g.obj("homeTeam")?.str("id") ?: "0"
        val awayId = at.str("id") ?: g.obj("awayTeam")?.str("id") ?: "0"
        val st = hd.obj("status")
        val (status, clock, start) = status(st)
        val showScore = !status.notStarted
        val hS = if (showScore) ht.int("score") else null
        val aS = if (showScore) at.int("score") else null
        val parent = g.str("parentLeagueId") ?: g.str("leagueId")
        val tour = Tournament(
            key = parent?.let { "${Src.FM}:$it" },
            groupKey = "${Src.FM}:${g.str("leagueId")}",
            name = g.str("leagueName") ?: "",
            categoryName = countryName(g.str("countryCode")),
            country = g.str("countryCode"),
            logo = parent?.let { leagueLogo(it) },
        )
        val round = g.str("leagueRoundName")?.let { r -> if (r.all { it.isDigit() }) "$r. kolo" else r }
        val pen = st?.optJSONArray("penaltyShootoutScore")
        val event = Event(
            key = "${Src.FM}:$id",
            sport = "football",
            tournament = tour,
            home = team(homeId, ht.str("name"), null),
            away = team(awayId, at.str("name"), null),
            homeScore = Score(hS, penalties = pen?.optInt(0)),
            awayScore = Score(aS, penalties = pen?.optInt(1)),
            status = status,
            start = if (start > 0) start else isoToEpoch(g.str("matchTimeUTCDate")),
            winner = winnerOf(hS, aS, status.finished).let { w ->
                if (w == 3 && pen != null) (if (pen.optInt(0) > pen.optInt(1)) 1 else 2) else w
            },
            clock = clock,
            roundName = round,
            homeRed = st?.int("numberOfHomeRedCards") ?: 0,
            awayRed = st?.int("numberOfAwayRedCards") ?: 0,
        )

        // incidents
        val incidents = c.obj("matchFacts")?.obj("events")?.arr("events")?.mapObj { e -> incident(e) }.orEmpty()

        // stats
        val stats = c.obj("stats")?.obj("Periods")?.let { periods ->
            listOf("All", "FirstHalf", "SecondHalf").mapNotNull { pk ->
                val p = periods.obj(pk) ?: return@mapNotNull null
                val groups = p.arr("stats").mapObj { gr ->
                    val items = gr.arr("stats").mapObj { s ->
                        if (s.str("type") == "title") return@mapObj null
                        val vals = s.optJSONArray("stats") ?: return@mapObj null
                        val hv = vals.opt(0); val av = vals.opt(1)
                        if (hv == null || hv == JSONObject.NULL) return@mapObj null
                        StatItem(
                            name = s.str("title") ?: "",
                            home = hv.toString(), away = av?.toString() ?: "",
                            homeValue = leadingNum(hv.toString()), awayValue = leadingNum(av?.toString()),
                        )
                    }
                    if (items.isEmpty()) null else StatGroup(gr.str("title") ?: "", items)
                }
                if (groups.isEmpty()) null else StatPeriod(when (pk) { "All" -> "ALL"; "FirstHalf" -> "1ST"; else -> "2ND" }, groups)
            }
        }.orEmpty()

        // lineups
        val lu = c.obj("lineup")
        val lineups = lu?.let {
            val h = lineupSide(it.obj("homeTeam"))
            val a = lineupSide(it.obj("awayTeam"))
            if (h == null && a == null) null else Lineups(it.str("lineupType") != "predicted", h, a)
        }

        // shots
        val shots = c.obj("shotmap")?.arr("shots")?.mapObj { s ->
            val isHome = s.str("teamId") == homeId
            val type = when (s.str("eventType")) {
                "Goal" -> "goal"
                "AttemptSaved" -> if (s.bool("isBlocked")) "block" else "save"
                "Post" -> "post"
                else -> "miss"
            }
            Shot(
                isHome = isHome,
                player = s.str("playerName") ?: "",
                type = if (s.bool("isOwnGoal")) "goal" else type,
                situation = s.str("situation"),
                bodyPart = s.str("shotType"),
                x = (105.0 - (s.dbl("x") ?: 90.0)).toFloat().coerceIn(0f, 105f),
                y = ((s.dbl("y") ?: 34.0) / 68.0 * 100).toFloat(),
                xg = s.dbl("expectedGoals"),
                xgot = s.dbl("expectedGoalsOnTarget"),
                minute = s.int("min"),
            )
        }.orEmpty()

        val momentum = c.obj("momentum")?.obj("main")?.arr("data")?.mapObj { MomentumPoint(it.optDouble("minute"), it.optInt("value")) }.orEmpty()

        // player stats (popup)
        val pStats = mutableMapOf<String, List<Pair<String, String>>>()
        val ratings = mutableMapOf<String, Double>()
        c.obj("playerStats")?.let { ps ->
            ps.keys().forEach { pid ->
                val p = ps.optJSONObject(pid) ?: return@forEach
                val rows = mutableListOf<Pair<String, String>>()
                val groups = p.arr("stats")
                for (gi in 0 until groups.length()) {
                    val so = groups.optJSONObject(gi)?.obj("stats") ?: continue
                    for (label in so.keys()) {
                        val stat = so.optJSONObject(label)?.obj("stat") ?: continue
                        if (stat.str("type") == "boolean") continue
                        val v = stat.opt("value")?.takeIf { it != JSONObject.NULL } ?: continue
                        val total = stat.opt("total")?.takeIf { it != JSONObject.NULL }
                        val txt = if (total != null) {
                            val vt = v.toString().toDoubleOrNull() ?: 0.0
                            val tt = total.toString().toDoubleOrNull() ?: 0.0
                            "${num(v)}/${num(total)}" + if (tt > 0) " (${(vt / tt * 100).toInt()}%)" else ""
                        } else num(v)
                        if (rows.none { it.first == label }) rows.add(label to txt)
                        if (label == "FotMob rating") v.toString().toDoubleOrNull()?.let { ratings[pid] = it }
                    }
                }
                if (rows.isNotEmpty()) pStats[pid] = rows
            }
        }

        // h2h
        val h2h = c.obj("h2h")?.let { h ->
            val sum = h.optJSONArray("summary")
            val evs = h.arr("matches").mapObj { m ->
                val lg = m.obj("league")
                val tt = Tournament(lg?.str("id")?.let { "${Src.FM}:$it" }, "h2h", lg?.str("name") ?: "", logo = lg?.str("id")?.let { leagueLogo(it) })
                val mid = m.str("matchUrl")?.substringAfterLast('#') ?: return@mapObj null
                val st2 = m.obj("status")
                val (s2, _, t2) = status(st2)
                val (a1, a2) = scoreFromStr(st2?.str("scoreStr"))
                Event(
                    key = "${Src.FM}:$mid", sport = "football", tournament = tt,
                    home = team(m.obj("home")?.str("id"), m.obj("home")?.str("name")),
                    away = team(m.obj("away")?.str("id"), m.obj("away")?.str("name")),
                    homeScore = Score(a1), awayScore = Score(a2), status = s2,
                    start = if (t2 > 0) t2 else isoToEpoch(m.obj("time")?.str("utcTime")),
                    winner = winnerOf(a1, a2, s2.finished),
                )
            }
            if (sum == null && evs.isEmpty()) null
            else H2H(sum?.optInt(0) ?: 0, sum?.optInt(1) ?: 0, sum?.optInt(2) ?: 0, evs.sortedByDescending { it.start }.take(10))
        }

        // form
        val tf = c.obj("matchFacts")?.optJSONArray("teamForm")
        val form = tf?.let { arr ->
            fun side(i: Int) = arr.optJSONArray(i)?.let { a -> TeamForm(a.mapObj { it.str("resultString") }) }
            PregameForm(side(0), side(1))
        }

        // info
        val info = mutableListOf<InfoLine>()
        c.obj("matchFacts")?.obj("infoBox")?.let { ib ->
            ib.obj("Stadium")?.let { s -> info += InfoLine("Stadion", listOfNotNull(s.str("name"), s.str("city")).joinToString(", ")) }
            ib.obj("Referee")?.str("text")?.let { info += InfoLine("Sudija", it) }
            ib.int("Attendance")?.let { info += InfoLine("Gledalaca", "%,d".format(it).replace(',', '.')) }
        }
        val potm = c.obj("matchFacts")?.obj("playerOfTheMatch")
        potm?.str("name")?.let { n -> info.add(0, InfoLine("Igrač utakmice", n + (potm.obj("rating")?.str("num")?.let { " ($it)" } ?: ""))) }

        val colors = g.obj("teamColors")?.obj("darkMode")
        // apply ratings from playerStats where lineup lacks them
        val lineups2 = lineups?.let { l ->
            fun fix(t: TeamLineup?) = t?.copy(players = t.players.map { p -> if (p.rating == null && ratings[p.id] != null) p.copy(rating = ratings[p.id]) else p })
            l.copy(home = fix(l.home), away = fix(l.away))
        }
        return MatchDetail(
            event = event,
            incidents = incidents,
            stats = stats,
            lineups = lineups2,
            shots = shots,
            momentum = momentum,
            playerStats = pStats,
            h2h = h2h,
            form = form,
            info = info,
            tableLeague = c.obj("table")?.str("parentLeagueId")?.let { "${Src.FM}:$it" } ?: tour.key,
            homeColor = colors?.str("home"),
            awayColor = colors?.str("away"),
        ).also { c.str("heatmapUrl")?.let { u -> heatUrls[id] = u } }
    }

    private val heatUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Player heatmaps for a match: player id -> points (0..100, attacking to the right). */
    suspend fun heatmaps(id: String, live: Boolean): Map<String, List<HeatPoint>> {
        val path = heatUrls[id] ?: run { match(id, live); heatUrls[id] } ?: return emptyMap()
        val body = net.get("https://www.fotmob.com$path", if (live) 120 else 3600)
        return withContext(Dispatchers.Default) {
            val players = JSONObject(body).obj("players") ?: return@withContext emptyMap()
            val re = Regex("""cx="([\d.]+)"\s+cy="([\d.]+)"""")
            val out = mutableMapOf<String, List<HeatPoint>>()
            players.keys().forEach { k ->
                val svg = players.optString(k)
                val pts = re.findAll(svg).map { m ->
                    HeatPoint((m.groupValues[1].toFloat() / 105f * 100f), (m.groupValues[2].toFloat() / 68f * 100f))
                }.toList()
                if (pts.isNotEmpty()) out[k.removePrefix("p")] = pts
            }
            out
        }
    }

    private fun incident(e: JSONObject): Incident? {
        val time = e.int("time")
        val added = e.int("overloadTime")?.takeIf { it > 0 }
        val home = if (e.has("isHome")) e.bool("isHome") else null
        return when (e.str("type")) {
            "Goal" -> {
                val ns = e.optJSONArray("newScore")
                Incident(
                    type = "goal",
                    cls = when { e.bool("ownGoal") -> "ownGoal"; e.str("suffix")?.contains("pen", true) == true || e.str("goalDescription")?.contains("Penalty", true) == true -> "penalty"; else -> "regular" },
                    time = time, addedTime = added, isHome = home,
                    player = e.str("nameStr") ?: e.obj("player")?.str("name"),
                    assist = e.str("assistStr")?.removePrefix("assist by ")?.trim(),
                    homeScore = ns?.optInt(0), awayScore = ns?.optInt(1),
                )
            }
            "Card" -> Incident(
                type = "card",
                cls = when (e.str("card")) { "Red" -> "red"; "YellowRed" -> "yellowRed"; else -> "yellow" },
                time = time, addedTime = added, isHome = home,
                player = e.str("nameStr") ?: e.obj("player")?.str("name"),
                text = e.str("description"),
            )
            "Substitution" -> {
                val sw = e.optJSONArray("swap")
                Incident("substitution", time = time, addedTime = added, isHome = home,
                    playerIn = sw?.optJSONObject(0)?.str("name"), playerOut = sw?.optJSONObject(1)?.str("name"))
            }
            "Half" -> Incident("period", time = time, text = when (e.str("halfStrShort")) {
                "HT" -> "Poluvreme"; "FT" -> "Kraj"; "AET" -> "Posle produžetaka"; "Pen" -> "Penali"; else -> e.str("halfStrShort")
            }, homeScore = e.int("homeScore"), awayScore = e.int("awayScore"))
            "AddedTime" -> Incident("injuryTime", time = time, text = e.str("minutesAddedStr")?.filter { it.isDigit() })
            "MissedPenalty" -> Incident("missedPenalty", time = time, addedTime = added, isHome = home, player = e.str("nameStr"))
            "VAR" -> Incident("var", time = time, addedTime = added, isHome = home, player = e.str("nameStr"),
                text = e.obj("VAR")?.str("decision")?.let { it } ?: e.str("varDecision"))
            else -> null
        }
    }

    private fun lineupSide(o: JSONObject?): TeamLineup? {
        o ?: return null
        fun player(p: JSONObject, sub: Boolean): LineupPlayer? {
            val id = p.str("id") ?: return null
            val vl = p.obj("verticalLayout")
            val perf = p.obj("performance")
            val evs = perf?.arr("events")?.mapObj { it.str("type") }.orEmpty() +
                perf?.arr("substitutionEvents")?.mapObj { it.str("type") }.orEmpty()
            val name = p.str("name") ?: ""
            return LineupPlayer(
                id = id, name = name,
                shortName = p.str("lastName")?.takeIf { it.isNotBlank() } ?: name,
                number = p.str("shirtNumber"),
                position = posName(p.int("usualPlayingPositionId")),
                substitute = sub,
                captain = p.bool("isCaptain"),
                rating = perf?.dbl("rating"),
                country = p.str("countryCode"),
                px = vl?.dbl("x")?.toFloat(), py = vl?.dbl("y")?.toFloat(),
                events = evs,
            )
        }
        val starters = o.arr("starters").mapObj { player(it, false) }
        val subs = o.arr("subs").mapObj { player(it, true) }
        if (starters.isEmpty() && subs.isEmpty()) return null
        return TeamLineup(
            formation = o.str("formation"),
            players = starters + subs,
            missing = o.arr("unavailable").mapObj { u ->
                val n = u.str("name") ?: return@mapObj null
                val un = u.obj("unavailability")
                n to listOfNotNull(when (un?.str("type")) { "injury" -> "povreda"; "suspension" -> "suspenzija"; "international" -> "reprezentacija"; else -> un?.str("type") }, un?.str("expectedReturn")?.let { r -> if (r == "Doubtful") "upitan" else r }).joinToString(" · ")
            },
            coach = o.obj("coach")?.str("name"),
            rating = o.dbl("rating"),
        )
    }

    private fun posName(id: Int?) = when (id) { 0 -> "G"; 1 -> "D"; 2 -> "M"; 3 -> "F"; else -> null }

    // ---------- league ----------

    suspend fun league(id: String, season: String?): LeaguePage {
        val url = "$F/leagues?id=$id" + (season?.let { "&season=" + URLEncoder.encode(it, "UTF-8") } ?: "")
        val body = net.get(url, 300)
        return withContext(Dispatchers.Default) {
            val r = JSONObject(body)
            val d = r.obj("details") ?: JSONObject()
            val tables = r.optJSONArray("table")?.let { tablesFrom(it) }.orEmpty()
            val all = r.obj("fixtures")?.arr("allMatches")?.mapObj { m -> fixtureMatch(m, null) }.orEmpty()
            val tTour = Tournament("${Src.FM}:$id", "${Src.FM}:$id", d.str("name") ?: "", countryName(d.str("country")), d.str("country"), leagueLogo(id))
            val withT = all.map { it.copy(tournament = tTour) }
            val top = r.obj("stats")?.arr("players")?.mapObj { cat ->
                val title = cat.str("header") ?: return@mapObj null
                val players = cat.arr("topThree").mapObj { p ->
                    TopPlayer(p.str("id")?.let { "${Src.FM}:$it" }, p.str("name") ?: "", p.str("teamName"), num(p.opt("value")))
                }
                if (players.isEmpty()) null else TopCategory(statTitleSr(title), players)
            }.orEmpty()
            LeaguePage(
                key = "${Src.FM}:$id",
                name = d.str("name") ?: "",
                country = countryName(d.str("country")),
                logo = leagueLogo(id),
                sport = "football",
                seasons = r.arr("allAvailableSeasons").strings(),
                season = d.str("selectedSeason"),
                tables = tables,
                results = withT.filter { it.status.finished }.sortedByDescending { it.start },
                fixtures = withT.filter { !it.status.finished }.sortedBy { it.start },
                top = top,
            )
        }
    }

    private fun tablesFrom(arr: JSONArray): List<StandingTable> = arr.mapObj { t ->
        val data = t.obj("data") ?: return@mapObj null
        val subs = data.optJSONArray("tables")
        if (subs != null) subs.mapObj { st -> tableOf(st, st.str("leagueName") ?: data.str("leagueName")) }
        else listOfNotNull(tableOf(data, data.str("leagueName")))
    }.flatten()

    private fun tableOf(d: JSONObject, name: String?): StandingTable? {
        val rows = d.obj("table")?.arr("all") ?: return null
        val legend = d.arr("legend").mapObj { l -> (l.str("title") ?: "") to (l.str("color") ?: "#888888") }
        val idxZone = mutableMapOf<Int, Pair<String, String>>()
        val lg = d.arr("legend")
        for (li in 0 until lg.length()) {
            val l = lg.optJSONObject(li) ?: continue
            val ix = l.optJSONArray("indices") ?: continue
            for (i in 0 until ix.length()) idxZone[ix.optInt(i)] = (l.str("title") ?: "") to (l.str("color") ?: "#888888")
        }
        val list = rows.mapObj { r ->
            val (sf, sa) = r.str("scoresStr")?.split("-")?.map { it.trim().toIntOrNull() ?: 0 }.let { (it?.getOrNull(0) ?: 0) to (it?.getOrNull(1) ?: 0) }
            val idx = r.int("idx") ?: 0
            StandingRow(
                team = team(r.opt("id"), r.str("name"), r.str("shortName")),
                position = idx,
                played = r.int("played") ?: 0, wins = r.int("wins") ?: 0, draws = r.int("draws") ?: 0, losses = r.int("losses") ?: 0,
                scoresFor = sf, scoresAgainst = sa, points = r.int("pts"),
                zone = idxZone[idx - 1]?.first, zoneColor = r.str("qualColor") ?: idxZone[idx - 1]?.second,
            )
        }
        return StandingTable(name ?: "", list, legend.map { (t, c) -> zoneSr(t) to c })
    }

    private fun fixtureMatch(m: JSONObject, t: Tournament?): Event? {
        val id = m.str("id") ?: return null
        val h = m.obj("home") ?: return null
        val a = m.obj("away") ?: return null
        val (st, clock, start) = status(m.obj("status"))
        val (hs, asc) = if (st.notStarted) null to null else scoreFromStr(m.obj("status")?.str("scoreStr"))
        val rn = m.str("roundName") ?: m.str("round")
        return Event(
            key = "${Src.FM}:$id", sport = "football", tournament = t,
            home = team(h.opt("id"), h.str("name"), h.str("shortName")),
            away = team(a.opt("id"), a.str("name"), a.str("shortName")),
            homeScore = Score(hs ?: h.int("score")?.takeIf { !st.notStarted }), awayScore = Score(asc ?: a.int("score")?.takeIf { !st.notStarted }),
            status = st, start = start, winner = winnerOf(hs, asc, st.finished), clock = clock,
            roundName = rn?.let { r -> if (r.all { it.isDigit() }) "$r. kolo" else r },
        )
    }

    // ---------- team ----------

    suspend fun team(id: String): TeamPage {
        val body = net.get("$F/teams?id=$id", 120)
        return withContext(Dispatchers.Default) {
            val r = JSONObject(body)
            val d = r.obj("details") ?: JSONObject()
            val tm = Team("${Src.FM}:$id", d.str("name") ?: "", d.str("shortName") ?: d.str("name") ?: "", null, teamLogo(id), countryName(d.str("country")), "football")
            val fx = r.obj("fixtures")?.obj("allFixtures")?.arr("fixtures")?.mapObj { m ->
                val trn = m.obj("tournament")
                val t = trn?.let { Tournament(it.str("leagueId")?.let { l -> "${Src.FM}:$l" }, "x", it.str("name") ?: "", logo = it.str("leagueId")?.let { l -> leagueLogo(l) }) }
                fixtureMatch(m, t)
            }.orEmpty()
            val tables = r.optJSONArray("table")?.mapObj { t ->
                val data = t.obj("data") ?: return@mapObj null
                val lk = data.str("leagueId")?.let { "${Src.FM}:$it" } ?: return@mapObj null
                tablesFrom(JSONArray().put(t)).firstOrNull()?.let { lk to it.copy(name = data.str("leagueName") ?: it.name) }
            }.orEmpty()
            val info = mutableListOf<InfoLine>()
            r.obj("overview")?.obj("venue")?.let { v ->
                v.obj("widget")?.let { w -> info += InfoLine("Stadion", listOfNotNull(w.str("name"), w.str("city")).joinToString(", ")) }
                v.arr("statPairs").let { sp -> for (i in 0 until sp.length()) sp.optJSONArray(i)?.let { p -> if (p.optString(0) == "Capacity") info += InfoLine("Kapacitet", p.optString(1)) } }
            }
            r.obj("overview")?.optJSONArray("coachHistory")?.optJSONObject(0)?.str("name")?.let { info += InfoLine("Trener", it) }
            TeamPage(
                team = tm,
                upcoming = fx.filter { !it.status.finished && !it.status.cancelled }.sortedBy { it.start },
                past = fx.filter { it.status.finished }.sortedByDescending { it.start },
                tables = tables,
                leagueKey = d.str("primaryLeagueId")?.let { "${Src.FM}:$it" },
                info = info,
            )
        }
    }

    // ---------- player ----------

    suspend fun player(id: String): PlayerPage {
        val body = net.get("$F/playerData?id=$id", 3600)
        return withContext(Dispatchers.Default) {
            val p = JSONObject(body)
            val pt = p.obj("primaryTeam")
            val team = pt?.str("teamId")?.let { team(it, pt.str("teamName")) }
            val info = mutableListOf<InfoLine>()
            p.obj("positionDescription")?.obj("primaryPosition")?.str("label")?.let { info += InfoLine("Pozicija", posSr(it)) }
            info += p.arr("playerInformation").mapObj { pi ->
                val title = pi.str("title") ?: return@mapObj null
                val v = pi.obj("value")?.let { it.str("fallback") ?: it.opt("numberValue")?.toString() } ?: return@mapObj null
                val label = when (title) { "Height" -> "Visina"; "Shirt" -> "Broj"; "Age" -> "Godine"; "Preferred foot" -> "Noga"; "Country" -> "Država"; "Market value" -> "Vrednost"; else -> null } ?: return@mapObj null
                InfoLine(label, when (v) { "Left" -> "Leva"; "Right" -> "Desna"; "Both" -> "Obe"; else -> v })
            }
            p.obj("injuryInformation")?.str("name")?.let { info += InfoLine("Povreda", it) }
            val ml = p.obj("mainLeague")
            val season = ml?.arr("stats")?.mapObj { s -> (statTitleSr(s.str("title") ?: "")) to num(s.opt("value")) }.orEmpty()
                .let { if (it.isNotEmpty()) listOf("Sezona" to "${ml?.str("leagueName")} ${ml?.str("season") ?: ""}") + it else it }
            val recent = p.arr("recentMatches").mapObj { m ->
                val home = m.bool("isHomeTeam")
                val my = team(m.opt("teamId"), m.str("teamName")); val op = team(m.opt("opponentTeamId"), m.str("opponentTeamName"))
                val hs = m.int("homeScore"); val asc = m.int("awayScore")
                Event(
                    key = "${Src.FM}:${m.str("id")}", sport = "football",
                    tournament = Tournament(m.str("leagueId")?.let { "${Src.FM}:$it" }, "x", m.str("leagueName") ?: "", logo = m.str("leagueId")?.let { leagueLogo(it) }),
                    home = if (home) my else op, away = if (home) op else my,
                    homeScore = Score(hs), awayScore = Score(asc), status = Status.FT,
                    start = isoToEpoch(m.obj("matchDate")?.str("utcTime")), winner = winnerOf(hs, asc, true),
                    note = listOfNotNull(m.int("minutesPlayed")?.let { "$it'" }, m.int("goals")?.takeIf { it > 0 }?.let { "⚽×$it" },
                        m.int("assists")?.takeIf { it > 0 }?.let { "A×$it" }, m.obj("ratingProps")?.str("rating")?.let { "★ $it" }).joinToString("  "),
                )
            }
            PlayerPage("${Src.FM}:$id", p.str("name") ?: "", team, playerPhoto(id), info, season, recent)
        }
    }

    // ---------- search & catalog ----------

    suspend fun search(q: String): List<SearchHit> {
        val body = net.get("$F/search/suggest?term=${URLEncoder.encode(q, "UTF-8")}&lang=en", 300)
        val out = mutableListOf<SearchHit>()
        val groups = JSONArray(body)
        for (gi in 0 until groups.length()) {
            val sug = groups.optJSONObject(gi)?.arr("suggestions") ?: continue
            for (si in 0 until sug.length()) {
                val s = sug.optJSONObject(si) ?: continue
                val id = s.str("id") ?: continue
                when (s.str("type")) {
                    "team" -> out += SearchHit.TeamHit(Team("${Src.FM}:$id", s.str("name") ?: "", logo = teamLogo(id), country = s.str("leagueName"), sport = "football"))
                    "player" -> out += SearchHit.PlayerHit("${Src.FM}:$id", s.str("name") ?: "", s.str("teamName"), playerPhoto(id))
                    "league" -> out += SearchHit.LeagueHit("${Src.FM}:$id", s.str("name") ?: "", countryName(s.str("ccode")), "football", leagueLogo(id))
                    "match" -> out += SearchHit.MatchHit("${Src.FM}:$id", "${s.str("homeTeamName")} – ${s.str("awayTeamName")}",
                        listOfNotNull(s.str("leagueName"), s.obj("status")?.str("scoreStr")).joinToString(" · "))
                }
            }
        }
        return out.distinctBy { when (it) { is SearchHit.TeamHit -> it.team.key; is SearchHit.PlayerHit -> it.key; is SearchHit.LeagueHit -> it.key; is SearchHit.MatchHit -> it.key } }
    }

    suspend fun catalog(): List<LeagueGroup> {
        val r = JSONObject(net.get("$F/allLeagues?locale=en&country=SRB", 24 * 3600))
        fun refs(a: JSONArray) = a.mapObj { l -> l.str("id")?.let { LeagueRef("${Src.FM}:$it", l.str("localizedName") ?: l.str("name") ?: "", countryName(l.str("ccode")), leagueLogo(it)) } }
        val out = mutableListOf<LeagueGroup>()
        out += LeagueGroup("Popularno", refs(r.arr("popular")))
        out += r.arr("international").mapObj { g -> LeagueGroup(g.str("localizedName") ?: g.str("name") ?: "Međunarodno", refs(g.arr("leagues"))) }
        val countries = r.arr("countries").mapObj { g -> LeagueGroup(countryName(g.str("ccode")) ?: g.str("localizedName") ?: "", refs(g.arr("leagues"))) }
        out += countries.sortedWith(compareBy<LeagueGroup> { if (it.name == "Srbija") 0 else 1 }.thenBy { it.name })
        return out
    }
}

private fun leadingNum(s: String?): Double = s?.let { Regex("""-?\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() } ?: 0.0

fun num(v: Any?): String {
    val d = v?.toString()?.toDoubleOrNull() ?: return v?.toString() ?: ""
    return if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')
}

private fun posSr(p: String) = when (p) {
    "Keeper", "Goalkeeper" -> "Golman"; "Right Winger" -> "Desno krilo"; "Left Winger" -> "Levo krilo"; "Striker" -> "Napadač"
    "Attacking Midfielder" -> "Ofanzivni vezni"; "Central Midfielder" -> "Centralni vezni"; "Defensive Midfielder" -> "Defanzivni vezni"
    "Center Back" -> "Štoper"; "Right Back" -> "Desni bek"; "Left Back" -> "Levi bek"; "Right Midfielder" -> "Desni vezni"; "Left Midfielder" -> "Levi vezni"
    "Right Wing-Back" -> "Desni krilni bek"; "Left Wing-Back" -> "Levi krilni bek"; else -> p
}
