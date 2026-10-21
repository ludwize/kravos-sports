package net.kravos.sports.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.sports.data.Event
import net.kravos.sports.data.Ranking
import net.kravos.sports.data.Sport
import net.kravos.sports.data.Tournament
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.LocalApp
import net.kravos.sports.ui.LocalNav
import net.kravos.sports.ui.Route
import net.kravos.sports.ui.components.Divider
import net.kravos.sports.ui.components.Empty
import net.kravos.sports.ui.components.ErrorBox
import net.kravos.sports.ui.components.EventRow
import net.kravos.sports.ui.components.LeagueLogo
import net.kravos.sports.ui.components.LiveDot
import net.kravos.sports.ui.components.Loading
import net.kravos.sports.ui.components.Pill
import net.kravos.sports.ui.rememberLoad
import net.kravos.sports.ui.theme.K
import net.kravos.sports.ui.theme.LocalPalette
import java.time.LocalDate

private sealed class Row_ {
    data class Header(val t: Tournament?, val key: String, val count: Int, val liveCount: Int, val collapsed: Boolean, val fav: Boolean = false) : Row_()
    data class Match(val e: Event, val first: Boolean, val last: Boolean, val fav: Boolean = false) : Row_()
    data class Title(val text: String) : Row_()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoresScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val sports by app.prefs.sports.collectAsState()
    val favTeams by app.prefs.teams.collectAsState()
    val favLeagues by app.prefs.leagues.collectAsState()

    var sport by rememberSaveable { mutableStateOf(app.prefs.lastSport.takeIf { it in sports } ?: sports.first()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var liveOnly by rememberSaveable { mutableStateOf(false) }
    val day = LocalDate.parse(date)
    val expanded = remember(sport, date) { mutableStateMapOf<String, Boolean>() }


    val load = rememberLoad(sport, date, liveOnly, refreshMs = { evs ->
        when {
            liveOnly -> 15_000L
            day == LocalDate.now() && evs.orEmpty().any { it.status.live } -> 15_000L
            day == LocalDate.now() -> 60_000L
            else -> 0L
        }
    }) {
        if (liveOnly) app.repo.live(sport)
        else app.repo.day(sport, day).filter { it.race != null || Fmt.localDate(it.start) == day }
    }

    // --- build flat rows ---
    val favTeamIds = favTeams.map { it.key }.toSet()
    val favLeagueIds = favLeagues.map { it.key }.toSet()
    val rows = remember(load.data, favTeamIds, favLeagueIds, expanded.toMap()) {
        val evs = load.data.orEmpty().sortedWith(compareBy<Event> { !it.status.live }.thenBy { it.start })
        val out = mutableListOf<Row_>()
        val mine = evs.filter { it.home.key in favTeamIds || it.away.key in favTeamIds }
        if (mine.isNotEmpty()) {
            out += Row_.Header(null, "fav", mine.size, mine.count { it.status.live }, false, fav = true)
            mine.forEachIndexed { i, e -> out += Row_.Match(e, false, i == mine.lastIndex, fav = true) }
        }
        val groups = evs.groupBy { it.tournament?.groupKey }.mapKeys { (_, v) -> v.first().tournament }
        Ranking.sortGroups(groups, favLeagueIds).forEach { (t, list) ->
            val key = t?.groupKey ?: "none"
            val tier = Ranking.tier(t, favLeagueIds)
            val anyLive = list.any { it.status.live }
            val collapsed = !(expanded[key] ?: (tier <= 5 || anyLive || liveOnly))
            out += Row_.Header(t, key, list.size, list.count { it.status.live }, collapsed)
            if (!collapsed) list.forEachIndexed { i, e -> out += Row_.Match(e, false, i == list.lastIndex) }
        }
        out
    }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        // header
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (liveOnly) "Uživo" else Fmt.dayLabel(day), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val totalLive = if (liveOnly || day == LocalDate.now()) load.data.orEmpty().count { it.status.live } else 0
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (liveOnly) K.Live else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { liveOnly = !liveOnly }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!liveOnly) { LiveDot(7.dp); Spacer(Modifier.width(6.dp)) }
                Text("UŽIVO" + if (totalLive > 0) " $totalLive" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (liveOnly) Color.White else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { nav.go(Route.Settings) }) { Icon(Icons.Filled.Settings, "Podešavanja") }
        }
        // sport bar
        val sportState = rememberLazyListState()
        LazyRow(state = sportState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(sports, key = { it.slug }) { s ->
                Pill(s.label, s == sport, onClick = {
                    sport = s; app.prefs.lastSport = s
                }, leading = { Icon(s.icon, null, Modifier.size(16.dp), tint = if (s == sport) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) })
            }
        }
        // date strip
        if (!liveOnly) DateStrip(day) { date = it.toString() }

        PullToRefreshBox(
            isRefreshing = load.value.loading && load.data != null,
            onRefresh = { load.reload() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                load.data == null && load.value.loading -> Loading()
                load.data == null -> ErrorBox(load.value.error) { load.reload() }
                rows.isEmpty() -> Empty(if (liveOnly) "Trenutno nema utakmica uživo u ovom sportu" else "Nema utakmica za ovaj dan")
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 16.dp)) {
                    itemsIndexed(rows, key = { i, r ->
                        when (r) {
                            is Row_.Header -> "h-${r.key}"
                            is Row_.Match -> "m-${r.e.key}-${if (r.fav) "f" else ""}"
                            is Row_.Title -> "t-$i"
                        }
                    }, contentType = { _, r -> r.javaClass.simpleName }) { _, r ->
                        when (r) {
                            is Row_.Header -> GroupHeader(r,
                                onOpen = { r.t?.let { t -> t.key?.let { nav.go(Route.League(it, t.name)) } } },
                                onToggle = { expanded[r.key] = r.collapsed })
                            is Row_.Match -> Box(Modifier.padding(horizontal = 12.dp).cardSegment(false, r.last)) {
                                Column {
                                    EventRow(r.e, onClick = { nav.go(Route.Match(r.e.key)) })
                                    if (!r.last) Divider(start = 64.dp)
                                }
                            }
                            is Row_.Title -> Text(r.text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.cardSegment(top: Boolean, bottom: Boolean): Modifier {
    val r = 16.dp
    val shape = RoundedCornerShape(topStart = if (top) r else 0.dp, topEnd = if (top) r else 0.dp, bottomStart = if (bottom) r else 0.dp, bottomEnd = if (bottom) r else 0.dp)
    return this.fillMaxWidth().clip(shape).background(LocalPalette.current.card)
}

@Composable
private fun GroupHeader(h: Row_.Header, onOpen: () -> Unit, onToggle: () -> Unit) {
    val pal = LocalPalette.current
    Column(Modifier.padding(top = 10.dp)) {
        Box(Modifier.padding(horizontal = 12.dp).cardSegment(true, h.collapsed)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = if (h.fav || h.t?.key == null) onToggle else onOpen).padding(start = 14.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (h.fav) {
                    Text("★", color = K.Yellow, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("Moji timovi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                } else {
                    LeagueLogo(h.t, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.t?.name ?: "Ostalo", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        h.t?.categoryName?.let { Text(it, fontSize = 11.sp, color = pal.dim, maxLines = 1) }
                    }
                }
                if (h.liveCount > 0) {
                    LiveDot(6.dp); Spacer(Modifier.width(4.dp))
                    Text("${h.liveCount}", color = K.Live, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                } else if (h.collapsed) {
                    Text("${h.count}", color = pal.dim, fontSize = 12.sp)
                }
                if (!h.fav) IconButton(onClick = onToggle) {
                    Icon(if (h.collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, null, tint = pal.dim)
                } else Spacer(Modifier.height(48.dp))
            }
        }
        if (!h.collapsed) Box(Modifier.padding(horizontal = 12.dp).cardSegment(false, false)) { Divider(0.dp) }
    }
}

@Composable
private fun DateStrip(selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = remember(today) { (-14..14).map { today.plusDays(it.toLong()) } }
    val st = rememberLazyListState(initialFirstVisibleItemIndex = (days.indexOf(selected) - 2).coerceAtLeast(0))
    LaunchedEffect(selected) {
        val i = days.indexOf(selected)
        if (i >= 0 && (i < st.firstVisibleItemIndex || i > st.firstVisibleItemIndex + 5)) st.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    val pal = LocalPalette.current
    LazyRow(state = st, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        items(days, key = { it.toString() }) { d ->
            val sel = d == selected
            Column(
                Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onPick(d) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (d == today) "DANAS" else Fmt.dayShort(d).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else pal.dim)
                Text("${d.dayOfMonth}.${d.monthValue}.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Suppress("unused") private val unusedSport = Sport.FOOTBALL
