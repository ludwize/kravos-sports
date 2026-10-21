package net.kravos.sports.data

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/** One entry point for the UI; routes each request to the right source by key prefix / sport. */
class Repo(context: Context) {
    val net = Net(context)
    val fm = FotMob(net)
    val el = Euroleague(net)
    val ls = LiveScore(net)
    val espn = Espn(net)

    val lastError get() = net.lastError

    suspend fun day(sport: Sport, date: LocalDate): List<Event> = coroutineScope {
        val today = date == LocalDate.now()
        when (sport) {
            Sport.FOOTBALL -> fm.day(date, today)
            Sport.BASKETBALL -> {
                val a = async { runCatching { el.day(date, today) }.getOrDefault(emptyList()) }
                val b = async { ls.day(sport, date, today) }
                a.await() + b.await()
            }
            Sport.TENNIS, Sport.ICE_HOCKEY, Sport.CRICKET -> ls.day(sport, date, today)
            else -> espn.day(sport, date, today)
        }
    }

    suspend fun live(sport: Sport): List<Event> = coroutineScope {
        when (sport) {
            Sport.FOOTBALL -> fm.day(LocalDate.now(), true).filter { it.status.live }
            Sport.BASKETBALL -> {
                val a = async { runCatching { el.day(LocalDate.now(), true) }.getOrDefault(emptyList()).filter { it.status.live } }
                val b = async { ls.live(sport) }
                a.await() + b.await()
            }
            Sport.TENNIS, Sport.ICE_HOCKEY, Sport.CRICKET -> ls.live(sport)
            else -> espn.day(sport, LocalDate.now(), true).filter { it.status.live }
        }
    }

    suspend fun match(key: String, live: Boolean): MatchDetail {
        val p = key.split(':')
        return when (p[0]) {
            Src.FM -> fm.match(p[1], live)
            Src.EL -> el.match(p[1], p[2], live)
            Src.LS -> ls.match(p[1], p[2], live)
            Src.ESPN -> espn.match(key, live)
            else -> error("Nepoznat izvor")
        }
    }

    suspend fun heatmaps(key: String, live: Boolean): Map<String, List<HeatPoint>> =
        if (key.startsWith("${Src.FM}:")) fm.heatmaps(key.substringAfter(':'), live) else emptyMap()

    suspend fun league(key: String, season: String?): LeaguePage {
        val p = key.split(':')
        return when (p[0]) {
            Src.FM -> fm.league(p[1], season)
            Src.EL -> el.league(p[1])
            else -> error("Ova liga nema stranicu")
        }
    }

    suspend fun team(key: String): TeamPage {
        val p = key.split(':')
        return when (p[0]) {
            Src.FM -> fm.team(p[1])
            Src.EL -> el.team(p[1])
            else -> error("Ovaj tim nema stranicu")
        }
    }

    suspend fun player(key: String): PlayerPage {
        val p = key.split(':')
        return when (p[0]) {
            Src.FM -> fm.player(p[1])
            else -> error("Profil nije dostupan")
        }
    }

    suspend fun search(q: String): List<SearchHit> = coroutineScope {
        val clubs = async {
            runCatching {
                val n = q.lowercase()
                el.clubs().filter { it.name.lowercase().contains(n) || it.shortName.lowercase().contains(n) }.map { SearchHit.TeamHit(it) }
            }.getOrDefault(emptyList())
        }
        val f = async { runCatching { fm.search(q) }.getOrDefault(emptyList()) }
        val c = clubs.await()
        val leagues = Euroleague.COMPS.filter { it.second.lowercase().contains(q.lowercase()) || (it.first == "E" && "euroleague".contains(q.lowercase())) }
            .map { SearchHit.LeagueHit("${Src.EL}:${it.first}", it.second, "Evropa", "basketball", null) }
        c + leagues + f.await()
    }

    suspend fun catalog(sport: Sport): List<LeagueGroup> = when (sport) {
        Sport.FOOTBALL -> fm.catalog()
        Sport.BASKETBALL -> listOf(LeagueGroup("Evropa", Euroleague.COMPS.map { LeagueRef("${Src.EL}:${it.first}", it.second, "Evropa", null) }))
        else -> emptyList()
    }

    /** Recent + upcoming matches for a favourite team (widget, favourites screen). */
    suspend fun teamEvents(key: String): List<Event> = runCatching { team(key).let { it.past.take(3) + it.upcoming.take(3) } }.getOrDefault(emptyList())
}
