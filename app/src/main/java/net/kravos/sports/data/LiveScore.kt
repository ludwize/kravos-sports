package net.kravos.sports.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** LiveScore public CDN API — basketball (all leagues), tennis, ice hockey, cricket. */
class LiveScore(private val net: Net) {
    private val L = "https://prod-cdn-public-api.livescore.com/v1/api/app"
    private val YMD = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val ESD = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    private fun offsetHours(): Int = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 3600

    /** Competitions also covered by a richer source are skipped here. */
    private val SKIP = setOf("euroleague", "eurocup")

    fun logo(img: String?) = img?.let { "https://lsm-static-prod.livescore.com/medium/$it" }

    suspend fun day(sport: Sport, date: LocalDate, today: Boolean): List<Event> {
        val off = offsetHours()
        val url = "$L/date/${sport.lsSlug}/${date.format(YMD)}/$off?countryCode=RS&locale=en&MD=1"
        val body = net.get(url, if (today) 15 else if (date.isBefore(LocalDate.now())) 600 else 120)
        return withContext(Dispatchers.Default) { parseStages(JSONObject(body), sport, off) }
    }

    suspend fun live(sport: Sport): List<Event> {
        val off = offsetHours()
        val body = net.get("$L/live/${sport.lsSlug}/$off?countryCode=RS&locale=en&MD=1", 10)
        return withContext(Dispatchers.Default) { parseStages(JSONObject(body), sport, off) }
    }

    private fun parseStages(root: JSONObject, sport: Sport, off: Int): List<Event> =
        root.arr("Stages").mapObj { st ->
            val comp = st.str("CompN") ?: st.str("Cnm") ?: ""
            if (comp.lowercase().replace(" ", "") in SKIP) return@mapObj null
            val stageName = st.str("Snm")
            val country = st.str("CompD") ?: st.str("Cnm")
            val t = Tournament(
                key = null,
                groupKey = "${Src.LS}:${st.str("CompId") ?: st.str("Sid")}",
                name = if (sport == Sport.TENNIS) listOfNotNull(st.str("Cnm"), stageName).joinToString(" – ") else comp,
                categoryName = if (sport == Sport.TENNIS) null else listOfNotNull(country?.takeIf { it != comp }, stageName?.takeIf { it != "Regular season" }).joinToString(" · ").ifBlank { null },
                country = lsCountry(country, st.str("CompCnmt")),
                logo = st.str("badgeUrl")?.let { "https://lsm-static-prod.livescore.com/high/enet/badges/$it" },
            )
            st.arr("Events").mapObj { e -> event(e, sport, t, off) }
        }.flatten()

    private fun team(arr: org.json.JSONArray?, sport: Sport): Team {
        val o = arr?.optJSONObject(0)
        val names = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it)?.str("Nm") }
        val name = names.joinToString(" / ").ifBlank { "?" }
        return Team(
            key = "${Src.LS}:${o?.str("ID") ?: name}",
            name = name, shortName = name,
            code = o?.str("Abr"), logo = logo(o?.str("Img")), sport = sport.slug,
        )
    }

    private fun event(e: JSONObject, sport: Sport, t: Tournament, off: Int): Event? {
        val id = e.str("Eid") ?: return null
        val eps = e.str("Eps") ?: "NS"
        val status = when {
            eps == "NS" -> Status.NS
            eps in setOf("FT", "AET", "AP", "AOT", "Ret.", "W.O.", "AW", "Fin") -> Status("finished", when (eps) { "AOT", "AET" -> "AET"; "AP" -> "AP"; "Ret." -> "retired"; "W.O." -> "walkover"; else -> "Ended" })
            eps.startsWith("Canc") || eps.startsWith("Postp") || eps.startsWith("Aband") || eps == "Susp." -> Status.cancelled(eps)
            else -> Status.live(eps)
        }
        val start = runCatching { LocalDateTime.parse(e.optLong("Esd").toString(), ESD).toEpochSecond(ZoneOffset.ofHours(off)) }.getOrDefault(0L)
        fun periods(side: Int): List<Int?> {
            val keys = when (sport) {
                Sport.TENNIS -> (1..5).map { "Tr${side}S$it" }
                Sport.ICE_HOCKEY -> (1..3).map { "Tr${side}Pe$it" }
                else -> (1..4).map { "Tr${side}Q$it" }
            }
            return keys.map { e.int(it) }.let { l -> l.subList(0, (l.indexOfLast { it != null } + 1)) }
        }
        fun tb(side: Int) = if (sport == Sport.TENNIS) (1..5).map { e.int("Tr${side}S${it}T") } else emptyList()
        val shown = !status.notStarted
        val h = Score(if (shown) e.int("Tr1") else null, if (shown) periods(1) else emptyList(), tb(1), overtime = e.int("Tr1OT"), point = e.str("Tr1G"))
        val a = Score(if (shown) e.int("Tr2") else null, if (shown) periods(2) else emptyList(), tb(2), overtime = e.int("Tr2OT"), point = e.str("Tr2G"))
        val winner = e.int("Ewt").takeIf { status.finished && it in 1..2 }
            ?: if (status.finished && h.current != null && a.current != null) (if (h.current > a.current) 1 else if (a.current > h.current) 2 else 3) else null
        return Event(
            key = "${Src.LS}:${sport.lsSlug}:$id",
            sport = sport.slug, tournament = t,
            home = team(e.optJSONArray("T1"), sport), away = team(e.optJSONArray("T2"), sport),
            homeScore = h, awayScore = a, status = status, start = start, winner = winner,
            clock = if (status.live) lsClock(eps, sport) else null,
            serving = e.int("Esrv")?.takeIf { status.live },
        )
    }

    private fun lsClock(eps: String, sport: Sport): String = when {
        eps.matches(Regex("Q\\d")) -> "${eps.drop(1)}. četv."
        eps == "HT" -> "Poluvreme"
        eps == "OT" -> "Produžetak"
        eps.matches(Regex("S\\d")) -> "${eps.drop(1)}. set"
        eps.matches(Regex("P\\d")) -> "${eps.drop(1)}. tr."
        eps.startsWith("Break") || eps == "Pause" -> "Pauza"
        else -> eps
    }

    suspend fun match(sportSlug: String, id: String, live: Boolean): MatchDetail {
        val sport = Sport.entries.firstOrNull { it.lsSlug == sportSlug } ?: Sport.BASKETBALL
        val off = offsetHours()
        val body = net.get("$L/scoreboard/$sportSlug/$id?locale=en", if (live) 10 else 300)
        val o = JSONObject(body)
        val stg = o.obj("Stg")
        val t = Tournament(null, "ls", stg?.str("CompN") ?: stg?.str("Cnm") ?: "", stg?.str("CompD") ?: stg?.str("Snm"))
        val ev = event(o, sport, t, off) ?: error("Utakmica nije pronađena")
        // lineups (names only)
        val box = runCatching {
            val lu = JSONObject(net.get("$L/lineups/$sportSlug/$id?locale=en", 600))
            val sides = lu.arr("Lu")
            fun rows(tnb: Int) = (0 until sides.length()).mapNotNull { sides.optJSONObject(it) }.firstOrNull { it.optInt("Tnb") == tnb }
                ?.arr("Ps")?.mapObj { p -> BoxRow(p.str("Snm") ?: "${p.str("Fn") ?: ""} ${p.str("Ln") ?: ""}".trim(), p.str("Snu"), p.str("Pon") == "STARTER", emptyList()) }.orEmpty()
            val h = rows(1); val a = rows(2)
            if (h.isEmpty() && a.isEmpty()) null else BoxScore(emptyList(), h, a)
        }.getOrNull()
        return MatchDetail(event = ev, box = box)
    }

    private fun lsCountry(name: String?, cont: String?): String? = when (name?.lowercase()) {
        null -> null
        "serbia" -> "SRB"; "europe", "uefa", "fiba europe" -> "EUR"; "usa" -> "USA"; "international", "world" -> "INT"
        "spain" -> "ESP"; "italy" -> "ITA"; "germany" -> "GER"; "france" -> "FRA"; "greece" -> "GRE"; "turkey", "türkiye", "turkiye" -> "TUR"
        "slovenia" -> "SVN"; "croatia" -> "CRO"; "bosnia and herzegovina" -> "BIH"; "montenegro" -> "MNE"; "adriatic", "aba league" -> "ABA"
        "lithuania" -> "LTU"; "israel" -> "ISR"; "england" -> "ENG"
        else -> if (cont == "europe") "EU?" else name.take(3).uppercase()
    }
}
