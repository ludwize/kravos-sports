package net.kravos.sports.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.sports.data.Event
import net.kravos.sports.data.Season
import net.kravos.sports.data.Sofa
import net.kravos.sports.data.StandingTable
import net.kravos.sports.data.TOP_STAT_SR
import net.kravos.sports.ui.LocalApp
import net.kravos.sports.ui.LocalNav
import net.kravos.sports.ui.Route
import net.kravos.sports.ui.components.CardTitle
import net.kravos.sports.ui.components.Divider
import net.kravos.sports.ui.components.Empty
import net.kravos.sports.ui.components.ErrorBox
import net.kravos.sports.ui.components.EventRow
import net.kravos.sports.ui.components.KCard
import net.kravos.sports.ui.components.Loading
import net.kravos.sports.ui.components.NetImage
import net.kravos.sports.ui.components.Pill
import net.kravos.sports.ui.components.StarButton
import net.kravos.sports.ui.components.TeamLogo
import net.kravos.sports.ui.rememberLoad
import net.kravos.sports.ui.theme.K
import net.kravos.sports.ui.theme.LocalPalette

private enum class LTab(val label: String) { TABLE("Tabela"), RESULTS("Rezultati"), FIXTURES("Raspored"), PLAYERS("Igrači") }

@Composable
fun LeagueScreen(r: Route.League, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val favs by app.prefs.leagues.collectAsState()
    val info = rememberLoad(r.utId, "ut") { app.sofa.uniqueTournament(r.utId) }
    val seasons = rememberLoad(r.utId, "seasons") { app.sofa.seasons(r.utId) }
    var seasonId by rememberSaveable(r.utId) { mutableStateOf(r.seasonId) }
    val sid = seasonId ?: seasons.data?.firstOrNull()?.id
    var tab by rememberSaveable(r.utId) { mutableStateOf(LTab.TABLE.name) }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
            NetImage(Sofa.leagueLogo(r.utId), 30.dp, r.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.data?.name ?: r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                info.data?.categoryName?.let { Text(it, fontSize = 12.sp, color = LocalPalette.current.dim) }
            }
            StarButton(favs.any { it.utId == r.utId }) {
                app.prefs.toggleLeague(r.utId, info.data?.name ?: r.name, info.data?.sport ?: "football", info.data?.alpha2)
            }
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SeasonPicker(seasons.data.orEmpty(), sid) { seasonId = it }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
            LTab.entries.forEach { t -> Pill(t.label, t.name == tab, { tab = t.name }) }
        }
        if (sid == null) {
            if (seasons.value.loading) Loading() else ErrorBox(seasons.value.error) { seasons.reload() }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            when (LTab.valueOf(tab)) {
                LTab.TABLE -> item { Column { StandingsBlock(r.utId, sid, emptySet()) } }
                LTab.RESULTS -> item { Column { EventsBlock(r.utId, sid, next = false) } }
                LTab.FIXTURES -> item { Column { EventsBlock(r.utId, sid, next = true) } }
                LTab.PLAYERS -> item { Column { TopPlayersBlock(r.utId, sid) } }
            }
        }
    }
}

@Composable
private fun SeasonPicker(list: List<Season>, sel: Int?, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Sezona " + (list.firstOrNull { it.id == sel }?.year ?: list.firstOrNull { it.id == sel }?.name ?: ""))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            list.take(15).forEach { s ->
                DropdownMenuItem(text = { Text(s.year ?: s.name) }, onClick = { open = false; onPick(s.id) })
            }
        }
    }
}

/** Standings for a league season; highlights [highlight] team ids. Used by league, team and match screens. */
@Composable
fun StandingsBlock(utId: Int, seasonId: Int, highlight: Set<Int>) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val st = rememberLoad(utId, seasonId, "standings") { app.sofa.standings(utId, seasonId) }
    when {
        st.data == null && st.value.loading -> Loading()
        st.data == null -> ErrorBox(st.value.error) { st.reload() }
        st.data!!.isEmpty() -> Empty("Ovo takmičenje nema tabelu (kup / eliminacije)")
        else -> st.data!!.forEach { table -> StandingTableCard(table, highlight, pal.dim) { id, name -> nav.go(Route.TeamPage(id, name)) } }
    }
}

private val ZONE_COLORS = listOf(Color(0xFF1E88E5), Color(0xFF00A86B), Color(0xFF26C6DA), Color(0xFFF2A100), Color(0xFFE5484D), Color(0xFF9C6ADE))

@Composable
private fun StandingTableCard(t: StandingTable, highlight: Set<Int>, dim: Color, onTeam: (Int, String) -> Unit) {
    val zones = t.rows.mapNotNull { it.promotion }.distinct()
    val relegation = zones.filter { it.lowercase().let { z -> z.contains("relegation") || z.contains("ispadanje") } }
    fun zoneColor(z: String?): Color? {
        z ?: return null
        if (z in relegation) return Color(0xFFE5484D)
        val i = zones.filter { it !in relegation }.indexOf(z)
        return ZONE_COLORS.getOrNull(i.coerceAtLeast(0)) ?: Color.Gray
    }
    val hasDraws = t.rows.any { it.draws > 0 }
    val hasPoints = t.rows.any { it.points != null }
    KCard {
        if (t.name.isNotBlank()) CardTitle(t.name)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("#", Modifier.width(26.dp), fontSize = 11.sp, color = dim)
            Text("Tim", Modifier.weight(1f), fontSize = 11.sp, color = dim)
            listOfNotNull("U", "P", if (hasDraws) "N" else null, "I", "Gol", if (hasPoints) "Bod" else "%").forEach {
                Text(it, Modifier.width(if (it == "Gol") 50.dp else 28.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = dim)
            }
        }
        t.rows.forEach { row ->
            val hl = row.team.id in highlight
            Row(
                Modifier.fillMaxWidth()
                    .background(if (hl) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { onTeam(row.team.id, row.team.name) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(26.dp)) {
                    val zc = zoneColor(row.promotion)
                    if (zc != null) Box(Modifier.size(3.dp, 18.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(zc).align(Alignment.CenterStart))
                    Text("${row.position}", Modifier.padding(start = 7.dp), fontSize = 13.sp, color = if (zc != null) MaterialTheme.colorScheme.onSurface else dim)
                }
                TeamLogo(row.team, 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(row.team.shortName, Modifier.weight(1f), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (hl) FontWeight.Bold else FontWeight.Normal)
                Cell("${row.played}")
                Cell("${row.wins}")
                if (hasDraws) Cell("${row.draws}")
                Cell("${row.losses}")
                Text("${row.scoresFor}:${row.scoresAgainst}", Modifier.width(50.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = dim)
                Text(
                    if (hasPoints) "${row.points ?: 0}" else row.percentage?.let { String.format(java.util.Locale.US, "%.3f", it).removePrefix("0") } ?: "-",
                    Modifier.width(28.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        if (zones.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                zones.forEach { z ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(zoneColor(z) ?: Color.Gray))
                        Spacer(Modifier.width(8.dp))
                        Text(zoneSr(z), fontSize = 11.sp, color = dim)
                    }
                }
            }
        } else Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Cell(s: String) = Text(s, Modifier.width(28.dp), textAlign = TextAlign.Center, fontSize = 13.sp)

private fun zoneSr(z: String): String = z
    .replace("Champions League", "Liga šampiona")
    .replace("Europa League", "Liga Evrope")
    .replace("Conference League", "Liga konferencija")
    .replace("Relegation", "Ispadanje")
    .replace("Promotion", "Plasman")
    .replace("Playoffs", "Plej-of")
    .replace("Play-offs", "Plej-of")
    .replace("Qualification", "kvalifikacije")
    .replace("qualification", "kvalifikacije")

@Composable
private fun EventsBlock(utId: Int, seasonId: Int, next: Boolean) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var pages by remember(utId, seasonId, next) { mutableStateOf(1) }
    val load = rememberLoad(utId, seasonId, next, pages) {
        (0 until pages).flatMap { app.sofa.tournamentEvents(utId, seasonId, next, it) }
    }
    val evs = load.data
    when {
        evs == null && load.value.loading -> Loading()
        evs.isNullOrEmpty() -> Empty(if (next) "Nema zakazanih utakmica" else "Nema odigranih utakmica")
        else -> {
            val sorted = if (next) evs.sortedBy { it.start } else evs.sortedByDescending { it.start }
            val byRound = sorted.groupBy { it.roundName ?: it.round?.let { r -> "$r. kolo" } ?: "" }
            byRound.forEach { (round, list) ->
                KCard {
                    if (round.isNotBlank()) CardTitle(round)
                    list.forEachIndexed { i, e: Event ->
                        EventRow(e, { nav.go(Route.Match(e.id)) }, showDate = true)
                        if (i < list.lastIndex) Divider(64.dp)
                    }
                }
            }
            TextButton(onClick = { pages++ }, modifier = Modifier.fillMaxWidth()) { Text("Učitaj još") }
        }
    }
}

@Composable
private fun TopPlayersBlock(utId: Int, seasonId: Int) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val load = rememberLoad(utId, seasonId, "top") { app.sofa.topPlayers(utId, seasonId) }
    val cats = load.data
    when {
        cats == null && load.value.loading -> Loading()
        cats.isNullOrEmpty() -> Empty("Statistika igrača nije dostupna za ovo takmičenje")
        else -> {
            val preferred = listOf("rating", "goals", "assists", "goalsAssistsSum", "expectedGoals", "points", "rebounds", "pir", "bigChancesCreated",
                "keyPasses", "successfulDribbles", "tackles", "interceptions", "saves", "cleanSheet", "yellowCards", "redCards")
            val ordered = cats.sortedBy { c -> preferred.indexOf(c.key).let { if (it < 0) 999 else it } }
            ordered.forEach { c ->
                KCard {
                    CardTitle(TOP_STAT_SR[c.key] ?: c.key)
                    c.players.take(5).forEachIndexed { i, p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { nav.go(Route.PlayerPage(p.playerId, p.name)) }.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", Modifier.width(22.dp), fontSize = 13.sp, color = LocalPalette.current.dim)
                            NetImage(Sofa.playerPhoto(p.playerId), 30.dp, p.name, circle = true)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 14.sp, maxLines = 1)
                                p.team?.let { Text(it.shortName, fontSize = 11.sp, color = LocalPalette.current.dim, maxLines = 1) }
                            }
                            Text(p.value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (i == 0) K.Accent else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
