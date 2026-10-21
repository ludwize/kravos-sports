package net.kravos.sports.data

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * SofaScore client (unofficial API, personal use).
 * Transport: direct HTTPS first; if blocked (403/429), switches to the WebView bridge for the rest of the session.
 * Caching: in-memory TTL cache + in-flight de-duplication + small disk cache so screens open instantly.
 */
class Sofa(context: Context) {
    private val bridge = WebBridge(context.applicationContext)
    private val disk = File(context.cacheDir, "api").apply { mkdirs() }
    private val mem = LruCache<String, Pair<Long, String>>(300)
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val gate = Semaphore(6)

    private val sp = context.getSharedPreferences("transport", Context.MODE_PRIVATE)

    /** Once direct access was blocked, go straight through the WebView bridge on later launches too. */
    @Volatile var useBridge = sp.getBoolean("bridge2", false)
        private set
    val bridgeStatus get() = bridge.status

    /** Start loading the bridge page early so the first request is fast. */
    fun warmUp() { if (useBridge) bridge.start() }
    @Volatile var lastError: String? = null
        private set

    private val API = "https://api.sofascore.com/api/v1/"

    /** Raw GET with TTL (seconds). Returns cached body if fresh enough. */
    suspend fun raw(path: String, ttl: Int, allowStale: Boolean = false): String {
        val now = System.currentTimeMillis()
        mem.get(path)?.let { (t, body) -> if (now - t < ttl * 1000L) return body }
        inflight[path]?.let { return it.await() }
        val d = CompletableDeferred<String>()
        val prev = inflight.putIfAbsent(path, d)
        if (prev != null) return prev.await()
        try {
            val body = gate.withPermit { fetch(path) }
            mem.put(path, now to body)
            if (ttl >= 60) writeDisk(path, body)
            lastError = null
            d.complete(body)
            return body
        } catch (e: Exception) {
            lastError = e.message
            val stale = mem.get(path)?.second ?: if (allowStale) readDisk(path) else null
            if (stale != null) {
                d.complete(stale)
                return stale
            }
            d.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(path)
        }
    }

    /** Last body we have on disk (for instant first paint), or null. */
    fun cachedOnDisk(path: String): String? = mem.get(path)?.second ?: readDisk(path)

    private suspend fun fetch(path: String): String {
        if (!useBridge) {
            try {
                return Http.get(API + path)
            } catch (e: HttpException) {
                if (e.code != 403 && e.code != 429) throw e
                Log.w(Http.TAG, "direct API blocked (${e.code}); switching to WebView bridge")
                useBridge = true
                sp.edit().putBoolean("bridge2", true).apply()
            }
        }
        return bridge.get(path)
    }

    private fun key(path: String) = File(disk, path.replace(Regex("[^A-Za-z0-9._-]"), "_").take(180))
    private fun writeDisk(path: String, body: String) = runCatching { key(path).writeText(body) }
    private fun readDisk(path: String): String? = runCatching { key(path).takeIf { it.exists() }?.readText() }.getOrNull()

    // ---------- endpoints ----------

    suspend fun scheduled(sport: Sport, date: LocalDate, live: Boolean): List<Event> {
        val today = date == LocalDate.now()
        val ttl = when {
            today || live -> 15
            date.isBefore(LocalDate.now()) -> 600
            else -> 120
        }
        val body = raw("sport/${sport.slug}/scheduled-events/$date", ttl, allowStale = true)
        return withContext(Dispatchers.Default) { parseEvents(body, sport.slug) }
    }

    suspend fun live(sport: Sport): List<Event> {
        val body = raw("sport/${sport.slug}/events/live", 10, allowStale = true)
        return withContext(Dispatchers.Default) { parseEvents(body, sport.slug) }
    }

    /** Live event counts per sport — keyed by slug. */
    suspend fun liveCounts(): Map<String, Int> = runCatching {
        val o = JSONObject(raw("sport/0/event-count", 30))
        o.keys().asSequence().associateWith { o.optJSONObject(it)?.optInt("live") ?: 0 }
    }.getOrDefault(emptyMap())

    suspend fun event(id: Int, live: Boolean): Event? {
        val body = raw("event/$id", if (live) 8 else 120)
        return JSONObject(body).optJSONObject("event")?.let { Event.parse(it) }
    }

    suspend fun statistics(id: Int, live: Boolean) = parseStatistics(raw("event/$id/statistics", if (live) 20 else 600))
    suspend fun incidents(id: Int, live: Boolean) = parseIncidents(raw("event/$id/incidents", if (live) 10 else 600))
    suspend fun lineups(id: Int, live: Boolean) = parseLineups(raw("event/$id/lineups", if (live) 60 else 900))
    suspend fun shotmap(id: Int, live: Boolean) = parseShotmap(raw("event/$id/shotmap", if (live) 30 else 900))
    suspend fun graph(id: Int, live: Boolean) = parseGraph(raw("event/$id/graph", if (live) 30 else 900))
    suspend fun averagePositions(id: Int) = parseAveragePositions(raw("event/$id/average-positions", 600))
    suspend fun pregame(id: Int) = parsePregame(raw("event/$id/pregame-form", 3600))
    suspend fun playerHeatmap(eventId: Int, playerId: Int, live: Boolean) =
        parseHeatmap(raw("event/$eventId/player/$playerId/heatmap", if (live) 60 else 3600))

    /** Team heatmap = merge of every player's heatmap on that side (fetched in parallel, max 6 at a time). */
    suspend fun teamHeatmap(eventId: Int, players: List<LineupPlayer>, live: Boolean): List<HeatPoint> = coroutineScope {
        players.map { p -> async { runCatching { playerHeatmap(eventId, p.id, live) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
    }

    suspend fun playerEventStats(eventId: Int, playerId: Int): Map<String, Double> = runCatching {
        val st = JSONObject(raw("event/$eventId/player/$playerId/statistics", 300)).optJSONObject("statistics") ?: return emptyMap()
        st.keys().asSequence().mapNotNull { k -> st.dbl(k)?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    suspend fun h2h(event: Event): H2H? {
        val duel = runCatching { JSONObject(raw("event/${event.id}/h2h", 3600)).optJSONObject("teamDuel") }.getOrNull()
        val evs = event.customId?.let { cid ->
            runCatching { parseEvents(raw("event/$cid/h2h/events", 3600)) }.getOrNull()
        }.orEmpty().filter { it.status.finished }.sortedByDescending { it.start }
        if (duel == null && evs.isEmpty()) return null
        return H2H(duel?.optInt("homeWins") ?: 0, duel?.optInt("draws") ?: 0, duel?.optInt("awayWins") ?: 0, evs.take(10))
    }

    suspend fun seasons(utId: Int): List<Season> =
        JSONObject(raw("unique-tournament/$utId/seasons", 6 * 3600)).arr("seasons").mapObj {
            Season(it.int("id") ?: return@mapObj null, it.str("name") ?: "", it.str("year"))
        }

    suspend fun standings(utId: Int, seasonId: Int): List<StandingTable> =
        parseStandings(raw("unique-tournament/$utId/season/$seasonId/standings/total", 300))

    suspend fun tournamentEvents(utId: Int, seasonId: Int, next: Boolean, page: Int = 0): List<Event> =
        runCatching {
            parseEvents(raw("unique-tournament/$utId/season/$seasonId/events/${if (next) "next" else "last"}/$page", 300))
        }.getOrDefault(emptyList())

    suspend fun topPlayers(utId: Int, seasonId: Int): List<TopCategory> =
        runCatching { parseTopPlayers(raw("unique-tournament/$utId/season/$seasonId/top-players/overall", 3600)) }
            .getOrDefault(emptyList())

    suspend fun uniqueTournament(utId: Int): Tournament? = runCatching {
        val o = JSONObject(raw("unique-tournament/$utId", 24 * 3600)).optJSONObject("uniqueTournament") ?: return null
        Tournament(
            id = utId, utId = utId, name = o.str("name") ?: "",
            categoryId = o.obj("category")?.int("id"), categoryName = o.obj("category")?.str("name"),
            alpha2 = o.obj("category")?.str("alpha2"), categorySlug = o.obj("category")?.str("slug"),
            priority = 0, sport = o.obj("category")?.obj("sport")?.str("slug"),
        )
    }.getOrNull()

    suspend fun team(id: Int): Team? = runCatching { Team.parse(JSONObject(raw("team/$id", 24 * 3600)).optJSONObject("team")) }.getOrNull()

    suspend fun teamEvents(id: Int, next: Boolean): List<Event> =
        runCatching { parseEvents(raw("team/$id/events/${if (next) "next" else "last"}/0", 120, allowStale = true)) }
            .getOrDefault(emptyList())

    /** The tournaments/seasons a team currently plays in (for standings on the team screen). */
    suspend fun teamStandingsSeasons(id: Int): List<Pair<Tournament, Season>> = runCatching {
        JSONObject(raw("team/$id/standings/seasons", 6 * 3600)).arr("tournamentSeasons").mapObj { ts ->
            val t = ts.obj("tournament")?.let { Tournament.parse(JSONObject().put("uniqueTournament", it).put("id", it.optInt("id"))) } ?: return@mapObj null
            val s = ts.arr("seasons").optJSONObject(0) ?: return@mapObj null
            t to Season(s.int("id") ?: return@mapObj null, s.str("name") ?: "", s.str("year"))
        }
    }.getOrDefault(emptyList())

    suspend fun player(id: Int): Player? = runCatching { parsePlayer(raw("player/$id", 24 * 3600)) }.getOrNull()

    suspend fun playerEvents(id: Int): List<Event> =
        runCatching { parseEvents(raw("player/$id/events/last/0", 600)) }.getOrDefault(emptyList())

    suspend fun search(q: String): List<SearchHit> =
        parseSearch(raw("search/all?q=${java.net.URLEncoder.encode(q, "UTF-8")}&page=0", 300))

    /** Popular tournaments for a sport, grouped by category (for the "Lige" browser). */
    suspend fun categories(sport: Sport): List<Pair<Int, String>> =
        JSONObject(raw("sport/${sport.slug}/categories", 24 * 3600)).arr("categories").mapObj {
            (it.int("id") ?: return@mapObj null) to (it.str("name") ?: "")
        }

    suspend fun categoryTournaments(categoryId: Int): List<Tournament> = runCatching {
        val root = JSONObject(raw("category/$categoryId/unique-tournaments", 24 * 3600))
        root.arr("groups").mapObj { g -> g.arr("uniqueTournaments").mapObj { ut ->
            Tournament.parse(JSONObject().put("uniqueTournament", ut).put("id", ut.optInt("id")))
        } }.flatten()
    }.getOrDefault(emptyList())

    companion object {
        const val IMG = "https://img.sofascore.com/api/v1/"
        fun teamLogo(id: Int) = "${IMG}team/$id/image"
        fun leagueLogo(utId: Int) = "${IMG}unique-tournament/$utId/image"
        fun playerPhoto(id: Int) = "${IMG}player/$id/image"
        fun flag(alpha2: String?): String? {
            val a = alpha2?.uppercase() ?: return null
            val code = when (a) {
                "EN" -> "gb-eng"; "SC" -> "gb-sct"; "WA" -> "gb-wls"; "NI" -> "gb-nir"
                else -> if (a.length == 2) a.lowercase() else return null
            }
            return "https://flagcdn.com/w40/$code.png"
        }
    }
}
