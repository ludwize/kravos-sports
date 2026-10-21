package net.kravos.sports.data

import org.json.JSONArray
import org.json.JSONObject

// ---------- small JSON helpers ----------

fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() && it != "null" } else null
fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) opt(key).let { (it as? Number)?.toInt() ?: it.toString().trim().toIntOrNull() } else null
fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) opt(key).let { (it as? Number)?.toLong() ?: it.toString().trim().toLongOrNull() } else null
fun JSONObject.dbl(key: String): Double? = if (has(key) && !isNull(key)) opt(key).let { (it as? Number)?.toDouble() ?: it.toString().trim().toDoubleOrNull() }?.takeIf { !it.isNaN() } else null
fun JSONObject.bool(key: String): Boolean = optBoolean(key, false)
fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()
inline fun <T> JSONArray.mapObj(f: (JSONObject) -> T?): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { o -> f(o)?.let(out::add) }
    return out
}
fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

/** Data sources. The prefix of every entity key tells which client handles it. */
object Src {
    const val FM = "fm"       // FotMob (football)
    const val EL = "el"       // EuroLeague / EuroCup official API
    const val LS = "ls"       // LiveScore (basketball, tennis, hockey, cricket)
    const val ESPN = "espn"   // ESPN (NBA, NFL, MLB, F1, UFC)
}

// ---------- entities ----------

data class Team(
    val key: String,          // e.g. "fm:7998", "el:PAR", "espn:basketball/nba:13"
    val name: String,
    val shortName: String = name,
    val code: String? = null,
    val logo: String? = null,
    val country: String? = null,
    val sport: String? = null,
) {
    val src get() = key.substringBefore(':')
    /** Only some sources have team pages. */
    val hasPage get() = src == Src.FM || src == Src.EL
}

data class Tournament(
    val key: String?,         // league page key ("fm:182", "el:E") or null if no page
    val groupKey: String,     // grouping key on the scores list
    val name: String,
    val categoryName: String? = null,
    val country: String? = null,   // ISO-3 or ISO-2 country code, or "INT"
    val logo: String? = null,
    val priority: Int = 0,
)

data class Score(
    val current: Int?,
    val periods: List<Int?> = emptyList(),
    val periodTiebreaks: List<Int?> = emptyList(),
    val overtime: Int? = null,
    val penalties: Int? = null,
    val point: String? = null,
    val display: Int? = current,
)

data class Status(val type: String, val description: String) {
    val live get() = type == "inprogress"
    val finished get() = type == "finished"
    val notStarted get() = type == "notstarted"
    val cancelled get() = type == "canceled"

    companion object {
        val NS = Status("notstarted", "Not started")
        val FT = Status("finished", "Ended")
        fun live(d: String) = Status("inprogress", d)
        fun cancelled(d: String) = Status("canceled", d)
    }
}

data class Event(
    val key: String,
    val sport: String,
    val tournament: Tournament?,
    val home: Team,
    val away: Team,
    val homeScore: Score,
    val awayScore: Score,
    val status: Status,
    val start: Long,          // epoch seconds
    val winner: Int? = null,  // 1 home, 2 away, 3 draw
    val clock: String? = null,      // live clock text ("67'", "Q3 4:12")
    val roundName: String? = null,
    val homeRed: Int = 0,
    val awayRed: Int = 0,
    val serving: Int? = null,
    val note: String? = null,
    val aggregateHome: Int? = null,
    val aggregateAway: Int? = null,
    /** For races (F1): ordered results instead of home/away. */
    val race: List<RaceResult>? = null,
) {
    val src get() = key.substringBefore(':')
}

data class RaceResult(val pos: Int, val name: String, val team: String?, val detail: String?, val flag: String?)

// ---------- match detail ----------

data class StatItem(
    val name: String,
    val home: String,
    val away: String,
    val homeValue: Double,
    val awayValue: Double,
    val lowerIsBetter: Boolean = false,
)

data class StatGroup(val name: String, val items: List<StatItem>)
data class StatPeriod(val period: String, val groups: List<StatGroup>)

data class Incident(
    val type: String,            // goal, card, substitution, period, injuryTime, var, missedPenalty
    val cls: String? = null,     // ownGoal, penalty, yellow, red, yellowRed
    val time: Int? = null,
    val addedTime: Int? = null,
    val isHome: Boolean? = null,
    val player: String? = null,
    val assist: String? = null,
    val playerIn: String? = null,
    val playerOut: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val text: String? = null,
)

data class LineupPlayer(
    val id: String,
    val name: String,
    val shortName: String,
    val number: String?,
    val position: String?,
    val substitute: Boolean,
    val captain: Boolean = false,
    val rating: Double? = null,
    val stats: Map<String, Double> = emptyMap(),
    val country: String? = null,
    /** Position on a vertical pitch (0..1), when the source provides it. */
    val px: Float? = null,
    val py: Float? = null,
    val events: List<String> = emptyList(),   // goal, yellowCard, redCard, subOut, subIn, assist
)

data class TeamLineup(
    val formation: String?,
    val players: List<LineupPlayer>,
    val missing: List<Pair<String, String>> = emptyList(),
    val coach: String? = null,
    val rating: Double? = null,
)

data class Lineups(val confirmed: Boolean, val home: TeamLineup?, val away: TeamLineup?)

data class HeatPoint(val x: Float, val y: Float, val w: Float = 1f)

data class Shot(
    val isHome: Boolean,
    val player: String,
    val type: String,          // goal, save, miss, block, post
    val situation: String?,
    val bodyPart: String?,
    val x: Float,              // 0..100 along the pitch towards the goal
    val y: Float,              // 0..100 across
    val xg: Double?,
    val xgot: Double?,
    val minute: Int?,
)

data class MomentumPoint(val minute: Double, val value: Int)
data class TeamForm(val form: List<String>, val position: Int? = null, val value: String? = null)
data class PregameForm(val home: TeamForm?, val away: TeamForm?, val label: String? = null)
data class H2H(val homeWins: Int, val draws: Int, val awayWins: Int, val events: List<Event>)

/** Box score table (basketball etc.): column headers + rows per player. */
data class BoxScore(val columns: List<String>, val home: List<BoxRow>, val away: List<BoxRow>)
data class BoxRow(val name: String, val number: String?, val starter: Boolean, val values: List<String>, val playerKey: String? = null)

data class InfoLine(val label: String, val value: String)

data class MatchDetail(
    val event: Event,
    val incidents: List<Incident> = emptyList(),
    val stats: List<StatPeriod> = emptyList(),
    val lineups: Lineups? = null,
    val shots: List<Shot> = emptyList(),
    val momentum: List<MomentumPoint> = emptyList(),
    /** player id -> heat points (0..100 coordinates, attacking right). */
    val heat: Map<String, List<HeatPoint>> = emptyMap(),
    val playerStats: Map<String, List<Pair<String, String>>> = emptyMap(),
    val h2h: H2H? = null,
    val form: PregameForm? = null,
    val box: BoxScore? = null,
    val info: List<InfoLine> = emptyList(),
    val tableLeague: String? = null,   // league key for the "Tabela" tab
    val tableSeason: String? = null,
    val homeColor: String? = null,
    val awayColor: String? = null,
)

// ---------- league / team / player pages ----------

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
    val zone: String? = null,
    val zoneColor: String? = null,
)

data class StandingTable(val name: String, val rows: List<StandingRow>, val legend: List<Pair<String, String>> = emptyList())

data class TopPlayer(val playerKey: String?, val name: String, val team: String?, val value: String)
data class TopCategory(val title: String, val players: List<TopPlayer>)

data class LeaguePage(
    val key: String,
    val name: String,
    val country: String?,
    val logo: String?,
    val sport: String,
    val seasons: List<String>,
    val season: String?,
    val tables: List<StandingTable>,
    val results: List<Event>,
    val fixtures: List<Event>,
    val top: List<TopCategory>,
)

data class TeamPage(
    val team: Team,
    val upcoming: List<Event>,
    val past: List<Event>,
    val tables: List<Pair<String, StandingTable>>,   // league key/name -> table
    val leagueKey: String?,
    val info: List<InfoLine> = emptyList(),
)

data class PlayerPage(
    val key: String,
    val name: String,
    val team: Team?,
    val photo: String?,
    val info: List<InfoLine>,
    val seasonStats: List<Pair<String, String>>,
    val recent: List<Event>,
)

// ---------- search ----------

sealed class SearchHit {
    data class TeamHit(val team: Team) : SearchHit()
    data class LeagueHit(val key: String, val name: String, val category: String?, val sport: String, val logo: String?) : SearchHit()
    data class PlayerHit(val key: String, val name: String, val team: String?, val photo: String?) : SearchHit()
    data class MatchHit(val key: String, val title: String, val sub: String) : SearchHit()
}

data class LeagueRef(val key: String, val name: String, val country: String?, val logo: String?)
data class LeagueGroup(val name: String, val leagues: List<LeagueRef>)
