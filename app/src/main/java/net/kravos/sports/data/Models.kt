package net.kravos.sports.data

import org.json.JSONArray
import org.json.JSONObject

// ---------- small JSON helpers ----------

fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null
fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
fun JSONObject.dbl(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null
fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
inline fun <T> JSONArray.mapObj(f: (JSONObject) -> T?): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { o -> f(o)?.let(out::add) }
    return out
}
fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

// ---------- entities ----------

data class Team(
    val id: Int,
    val name: String,
    val shortName: String,
    val code: String?,
    val sport: String?,
    val country: String?,
) {
    companion object {
        fun parse(o: JSONObject?): Team? {
            o ?: return null
            val id = o.int("id") ?: return null
            val name = o.str("name") ?: return null
            return Team(
                id = id,
                name = name,
                shortName = o.str("shortName") ?: name,
                code = o.str("nameCode"),
                sport = o.obj("sport")?.str("slug"),
                country = o.obj("country")?.str("name"),
            )
        }
    }
}

data class Tournament(
    val id: Int,
    val utId: Int?,
    val name: String,
    val categoryId: Int?,
    val categoryName: String?,
    val alpha2: String?,
    val categorySlug: String?,
    val priority: Int,
    val sport: String?,
) {
    val key get() = utId?.let { "u$it" } ?: "t$id"
    companion object {
        fun parse(o: JSONObject?): Tournament? {
            o ?: return null
            val ut = o.obj("uniqueTournament")
            val cat = o.obj("category") ?: ut?.obj("category")
            return Tournament(
                id = o.int("id") ?: ut?.int("id") ?: return null,
                utId = ut?.int("id"),
                name = ut?.str("name") ?: o.str("name") ?: "",
                categoryId = cat?.int("id"),
                categoryName = cat?.str("name"),
                alpha2 = cat?.str("alpha2") ?: cat?.obj("country")?.str("alpha2"),
                categorySlug = cat?.str("slug"),
                priority = o.int("priority") ?: 0,
                sport = cat?.obj("sport")?.str("slug"),
            )
        }
    }
}

data class Score(
    val current: Int?,
    val display: Int?,
    val periods: List<Int?>,     // period1..period7
    val overtime: Int?,
    val penalties: Int?,
    val point: String?,          // tennis game point (0/15/30/40/A)
    val periodTiebreaks: List<Int?>,
) {
    companion object {
        val EMPTY = Score(null, null, emptyList(), null, null, null, emptyList())
        fun parse(o: JSONObject?): Score {
            o ?: return EMPTY
            val periods = (1..7).map { o.int("period$it") }
            val last = periods.indexOfLast { it != null }
            val tb = (1..7).map { o.int("period${it}TieBreak") }
            return Score(
                current = o.int("current"),
                display = o.int("display"),
                periods = if (last >= 0) periods.subList(0, last + 1) else emptyList(),
                overtime = o.int("overtime"),
                penalties = o.int("penalties"),
                point = o.str("point"),
                periodTiebreaks = if (last >= 0) tb.subList(0, last + 1) else emptyList(),
            )
        }
    }
}

data class Status(val code: Int, val type: String, val description: String) {
    val live get() = type == "inprogress"
    val finished get() = type == "finished"
    val notStarted get() = type == "notstarted"
    val cancelled get() = type == "canceled" || type == "postponed" || type == "interrupted" || type == "suspended"
}

data class MatchTime(
    val periodStart: Long?,
    val initial: Int?,
    val max: Int?,
    val extra: Int?,
    val injury1: Int?,
    val injury2: Int?,
    val played: Int?,
    val periodLength: Int?,
)

data class Event(
    val id: Int,
    val customId: String?,
    val slug: String?,
    val sport: String,
    val tournament: Tournament?,
    val seasonId: Int?,
    val round: Int?,
    val roundName: String?,
    val home: Team,
    val away: Team,
    val homeScore: Score,
    val awayScore: Score,
    val status: Status,
    val start: Long,           // epoch seconds
    val winner: Int?,          // 1 home, 2 away, 3 draw
    val time: MatchTime?,
    val homeRed: Int,
    val awayRed: Int,
    val firstToServe: Int?,
    val hasStats: Boolean,
    val hasHeatmap: Boolean,
    val hasXg: Boolean,
    val aggregateHome: Int?,
    val aggregateAway: Int?,
    val note: String?,
) {
    companion object {
        fun parse(o: JSONObject, sportHint: String? = null): Event? {
            val home = Team.parse(o.obj("homeTeam")) ?: return null
            val away = Team.parse(o.obj("awayTeam")) ?: return null
            val st = o.obj("status")
            val t = o.obj("time")
            val tour = Tournament.parse(o.obj("tournament"))
            val ri = o.obj("roundInfo")
            val agg = o.obj("homeScore")?.int("aggregated") to o.obj("awayScore")?.int("aggregated")
            return Event(
                id = o.int("id") ?: return null,
                customId = o.str("customId"),
                slug = o.str("slug"),
                sport = tour?.sport ?: home.sport ?: sportHint ?: "football",
                tournament = tour,
                seasonId = o.obj("season")?.int("id"),
                round = ri?.int("round"),
                roundName = ri?.str("name"),
                home = home,
                away = away,
                homeScore = Score.parse(o.obj("homeScore")),
                awayScore = Score.parse(o.obj("awayScore")),
                status = Status(st?.int("code") ?: 0, st?.str("type") ?: "notstarted", st?.str("description") ?: ""),
                start = o.long("startTimestamp") ?: 0L,
                winner = o.int("winnerCode"),
                time = t?.let {
                    MatchTime(
                        periodStart = it.long("currentPeriodStartTimestamp"),
                        initial = it.int("initial"),
                        max = it.int("max"),
                        extra = it.int("extra"),
                        injury1 = it.int("injuryTime1"),
                        injury2 = it.int("injuryTime2"),
                        played = it.int("played"),
                        periodLength = it.int("periodLength"),
                    )
                },
                homeRed = o.int("homeRedCards") ?: 0,
                awayRed = o.int("awayRedCards") ?: 0,
                firstToServe = o.int("firstToServe"),
                hasStats = o.optBoolean("hasEventPlayerStatistics", false),
                hasHeatmap = o.optBoolean("hasEventPlayerHeatMap", false),
                hasXg = o.optBoolean("hasXg", false),
                aggregateHome = agg.first,
                aggregateAway = agg.second,
                note = o.str("note"),
            )
        }
    }
}

// ---------- match detail pieces ----------

data class StatItem(
    val name: String,
    val home: String,
    val away: String,
    val homeValue: Double,
    val awayValue: Double,
    val compareCode: Int?,        // 1 home better, 2 away better, 3 equal
    val lowerIsBetter: Boolean,
)

data class StatGroup(val name: String, val items: List<StatItem>)
data class StatPeriod(val period: String, val groups: List<StatGroup>)

fun parseStatistics(json: String): List<StatPeriod> {
    val root = JSONObject(json)
    return root.arr("statistics").mapObj { p ->
        StatPeriod(
            period = p.str("period") ?: "ALL",
            groups = p.arr("groups").mapObj { g ->
                StatGroup(
                    name = g.str("groupName") ?: "",
                    items = g.arr("statisticsItems").mapObj { s ->
                        StatItem(
                            name = s.str("name") ?: return@mapObj null,
                            home = s.str("home") ?: "",
                            away = s.str("away") ?: "",
                            homeValue = s.dbl("homeValue") ?: s.str("home")?.filter { c -> c.isDigit() || c == '.' }?.toDoubleOrNull() ?: 0.0,
                            awayValue = s.dbl("awayValue") ?: s.str("away")?.filter { c -> c.isDigit() || c == '.' }?.toDoubleOrNull() ?: 0.0,
                            compareCode = s.int("compareCode"),
                            lowerIsBetter = s.str("renderType")?.contains("lower") == true,
                        )
                    },
                )
            },
        )
    }
}

data class Incident(
    val type: String,            // goal, card, substitution, period, injuryTime, varDecision, inGamePenalty, ...
    val cls: String?,            // incidentClass: regular, ownGoal, penalty, yellow, red, yellowRed, ...
    val time: Int?,
    val addedTime: Int?,
    val isHome: Boolean?,
    val player: String?,
    val playerId: Int?,
    val assist: String?,
    val playerIn: String?,
    val playerOut: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val text: String?,
    val reason: String?,
    val length: Int?,
)

fun parseIncidents(json: String): List<Incident> =
    JSONObject(json).arr("incidents").mapObj { o ->
        Incident(
            type = o.str("incidentType") ?: return@mapObj null,
            cls = o.str("incidentClass"),
            time = o.int("time"),
            addedTime = o.int("addedTime")?.takeIf { it != 999 },
            isHome = if (o.has("isHome")) o.optBoolean("isHome") else null,
            player = o.obj("player")?.let { it.str("shortName") ?: it.str("name") } ?: o.str("playerName"),
            playerId = o.obj("player")?.int("id"),
            assist = o.obj("assist1")?.let { it.str("shortName") ?: it.str("name") },
            playerIn = o.obj("playerIn")?.let { it.str("shortName") ?: it.str("name") },
            playerOut = o.obj("playerOut")?.let { it.str("shortName") ?: it.str("name") },
            homeScore = o.int("homeScore"),
            awayScore = o.int("awayScore"),
            text = o.str("text"),
            reason = o.str("reason") ?: o.str("description"),
            length = o.int("length"),
        )
    }

data class LineupPlayer(
    val id: Int,
    val name: String,
    val shortName: String,
    val number: String?,
    val position: String?,     // G D M F or basketball positions
    val substitute: Boolean,
    val captain: Boolean,
    val rating: Double?,
    val stats: Map<String, Double>,
    val country: String?,
)

data class TeamLineup(
    val formation: String?,
    val players: List<LineupPlayer>,
    val missing: List<Pair<String, String>>,   // name, reason
    val color: String?,
    val gkColor: String?,
)

data class Lineups(val confirmed: Boolean, val home: TeamLineup?, val away: TeamLineup?)

fun parseLineups(json: String): Lineups {
    val root = JSONObject(json)
    fun side(o: JSONObject?): TeamLineup? {
        o ?: return null
        val players = o.arr("players").mapObj { p ->
            val pl = p.obj("player") ?: return@mapObj null
            val st = p.obj("statistics")
            val stats = mutableMapOf<String, Double>()
            st?.keys()?.forEach { k -> st.dbl(k)?.let { stats[k] = it } }
            LineupPlayer(
                id = pl.int("id") ?: return@mapObj null,
                name = pl.str("name") ?: "",
                shortName = pl.str("shortName") ?: pl.str("name") ?: "",
                number = p.str("shirtNumber") ?: p.str("jerseyNumber") ?: pl.str("jerseyNumber"),
                position = p.str("position") ?: pl.str("position"),
                substitute = p.optBoolean("substitute", false),
                captain = p.optBoolean("captain", false),
                rating = st?.dbl("rating") ?: p.dbl("avgRating"),
                stats = stats,
                country = pl.obj("country")?.str("alpha2"),
            )
        }
        val missing = o.arr("missingPlayers").mapObj { m ->
            val n = m.obj("player")?.let { it.str("shortName") ?: it.str("name") } ?: return@mapObj null
            n to (m.str("reason")?.let { r -> if (r == "1") "povreda" else if (r == "3") "suspenzija" else r } ?: m.str("type") ?: "")
        }
        return TeamLineup(
            formation = o.str("formation"),
            players = players,
            missing = missing,
            color = o.obj("playerColor")?.str("primary"),
            gkColor = o.obj("goalkeeperColor")?.str("primary"),
        )
    }
    return Lineups(root.optBoolean("confirmed", false), side(root.obj("home")), side(root.obj("away")))
}

data class HeatPoint(val x: Float, val y: Float, val w: Float = 1f)

fun parseHeatmap(json: String): List<HeatPoint> {
    val root = JSONObject(json)
    val arr = root.optJSONArray("heatmap") ?: root.optJSONArray("points") ?: JSONArray()
    return arr.mapObj { HeatPoint(it.optDouble("x").toFloat(), it.optDouble("y").toFloat(), (it.dbl("count") ?: 1.0).toFloat()) }
}

data class Shot(
    val isHome: Boolean,
    val player: String,
    val type: String,          // goal, save, miss, block, post
    val situation: String?,
    val bodyPart: String?,
    val x: Float,
    val y: Float,
    val xg: Double?,
    val xgot: Double?,
    val minute: Int?,
)

fun parseShotmap(json: String): List<Shot> =
    JSONObject(json).arr("shotmap").mapObj { o ->
        val c = o.obj("playerCoordinates") ?: return@mapObj null
        Shot(
            isHome = o.optBoolean("isHome"),
            player = o.obj("player")?.let { it.str("shortName") ?: it.str("name") } ?: "",
            type = o.str("shotType") ?: "miss",
            situation = o.str("situation"),
            bodyPart = o.str("bodyPart"),
            x = c.optDouble("x").toFloat(),
            y = c.optDouble("y").toFloat(),
            xg = o.dbl("xg"),
            xgot = o.dbl("xgot"),
            minute = o.int("time"),
        )
    }

data class MomentumPoint(val minute: Double, val value: Int)

fun parseGraph(json: String): List<MomentumPoint> =
    JSONObject(json).arr("graphPoints").mapObj { MomentumPoint(it.optDouble("minute"), it.optInt("value")) }

data class AvgPosition(val playerId: Int, val name: String, val number: String?, val x: Float, val y: Float, val isHome: Boolean)

fun parseAveragePositions(json: String): List<AvgPosition> {
    val root = JSONObject(json)
    fun side(key: String, home: Boolean) = root.arr(key).mapObj { o ->
        val p = o.obj("player") ?: return@mapObj null
        AvgPosition(
            playerId = p.int("id") ?: return@mapObj null,
            name = p.str("shortName") ?: p.str("name") ?: "",
            number = p.str("jerseyNumber") ?: o.str("jerseyNumber"),
            x = o.optDouble("averageX").toFloat(),
            y = o.optDouble("averageY").toFloat(),
            isHome = home,
        )
    }
    return side("home", true) + side("away", false)
}

data class TeamForm(val form: List<String>, val position: Int?, val value: String?)
data class PregameForm(val home: TeamForm?, val away: TeamForm?, val label: String?)

fun parsePregame(json: String): PregameForm {
    val r = JSONObject(json)
    fun t(o: JSONObject?) = o?.let { TeamForm(it.arr("form").strings(), it.int("position"), it.str("value")) }
    return PregameForm(t(r.obj("homeTeam")), t(r.obj("awayTeam")), r.str("label"))
}

data class H2H(val homeWins: Int, val draws: Int, val awayWins: Int, val events: List<Event>)

// ---------- standings ----------

data class StandingRow(
    val team: Team,
    val position: Int,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val scoresFor: Int,
    val scoresAgainst: Int,
    val points: Int?,
    val percentage: Double?,
    val promotion: String?,
    val promotionId: Int?,
)

data class StandingTable(val name: String, val rows: List<StandingRow>)

fun parseStandings(json: String): List<StandingTable> =
    JSONObject(json).arr("standings").mapObj { s ->
        StandingTable(
            name = s.str("name") ?: s.obj("tournament")?.str("name") ?: "",
            rows = s.arr("rows").mapObj { r ->
                StandingRow(
                    team = Team.parse(r.obj("team")) ?: return@mapObj null,
                    position = r.optInt("position"),
                    played = r.optInt("matches"),
                    wins = r.optInt("wins"),
                    draws = r.optInt("draws"),
                    losses = r.optInt("losses"),
                    scoresFor = r.optInt("scoresFor"),
                    scoresAgainst = r.optInt("scoresAgainst"),
                    points = r.int("points"),
                    percentage = r.dbl("percentage"),
                    promotion = r.obj("promotion")?.str("text"),
                    promotionId = r.obj("promotion")?.int("id"),
                )
            },
        )
    }

data class Season(val id: Int, val name: String, val year: String?)

data class TopPlayer(val playerId: Int, val name: String, val team: Team?, val value: String)
data class TopCategory(val key: String, val players: List<TopPlayer>)

fun parseTopPlayers(json: String): List<TopCategory> {
    val root = JSONObject(json).obj("topPlayers") ?: return emptyList()
    val out = mutableListOf<TopCategory>()
    root.keys().forEach { key ->
        val arr = root.optJSONArray(key) ?: return@forEach
        val players = arr.mapObj { o ->
            val p = o.obj("player") ?: return@mapObj null
            val st = o.obj("statistics")
            val v = st?.let { s -> s.opt(key)?.toString() } ?: ""
            TopPlayer(p.int("id") ?: 0, p.str("name") ?: "", Team.parse(o.obj("team")), formatStatValue(v))
        }
        if (players.isNotEmpty()) out += TopCategory(key, players)
    }
    return out
}

private fun formatStatValue(v: String): String {
    val d = v.toDoubleOrNull() ?: return v
    return if (d == Math.floor(d)) d.toLong().toString() else String.format(java.util.Locale.US, "%.2f", d)
}

// ---------- search ----------

sealed class SearchHit {
    data class TeamHit(val team: Team) : SearchHit()
    data class LeagueHit(val id: Int, val name: String, val category: String?, val sport: String?) : SearchHit()
    data class PlayerHit(val id: Int, val name: String, val team: Team?) : SearchHit()
}

fun parseSearch(json: String): List<SearchHit> =
    JSONObject(json).arr("results").mapObj { r ->
        val e = r.obj("entity") ?: return@mapObj null
        when (r.str("type")) {
            "team" -> Team.parse(e)?.let { SearchHit.TeamHit(it) }
            "uniqueTournament" -> SearchHit.LeagueHit(
                e.int("id") ?: return@mapObj null,
                e.str("name") ?: "",
                e.obj("category")?.str("name"),
                e.obj("category")?.obj("sport")?.str("slug"),
            )
            "player" -> SearchHit.PlayerHit(e.int("id") ?: return@mapObj null, e.str("name") ?: "", Team.parse(e.obj("team")))
            else -> null
        }
    }

fun parseEvents(json: String, sport: String? = null): List<Event> =
    JSONObject(json).arr("events").mapObj { Event.parse(it, sport) }

data class Player(
    val id: Int,
    val name: String,
    val team: Team?,
    val position: String?,
    val number: String?,
    val height: Int?,
    val foot: String?,
    val country: String?,
    val birth: Long?,
    val marketValue: Long?,
    val currency: String?,
)

fun parsePlayer(json: String): Player? {
    val p = JSONObject(json).obj("player") ?: return null
    return Player(
        id = p.int("id") ?: return null,
        name = p.str("name") ?: "",
        team = Team.parse(p.obj("team")),
        position = p.str("position"),
        number = p.str("jerseyNumber") ?: p.str("shirtNumber"),
        height = p.int("height"),
        foot = p.str("preferredFoot"),
        country = p.obj("country")?.str("name"),
        birth = p.long("dateOfBirthTimestamp"),
        marketValue = p.long("proposedMarketValue"),
        currency = p.obj("proposedMarketValueRaw")?.str("currency"),
    )
}
