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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import net.kravos.sports.data.LeaguePage
import net.kravos.sports.data.StandingTable
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
    var season by rememberSaveable(r.key) { mutableStateOf(r.season) }
    val load = rememberLoad(r.key, season) { app.repo.league(r.key, season) }
    val pg = load.data
    var tab by rememberSaveable(r.key) { mutableStateOf(LTab.TABLE.name) }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
            NetImage(pg?.logo, 30.dp, r.name)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pg?.name ?: r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                pg?.country?.let { Text(it, fontSize = 12.sp, color = LocalPalette.current.dim) }
            }
            StarButton(favs.any { it.key == r.key }) {
                app.prefs.toggleLeague(r.key, pg?.name ?: r.name, pg?.sport ?: "football", pg?.logo)
            }
        }
        if (pg != null && pg.seasons.size > 1) Row(Modifier.padding(horizontal = 10.dp)) {
            SeasonPicker(pg.seasons, pg.season) { season = it }
        }
        val tabs = LTab.entries.filter { it != LTab.PLAYERS || pg?.top?.isNotEmpty() == true }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
            tabs.forEach { t -> Pill(t.label, t.name == tab, { tab = t.name }) }
        }
        when {
            pg == null && load.value.loading -> { Loading(); return@Column }
            pg == null -> { ErrorBox(load.value.error) { load.reload() }; return@Column }
        }
        pg!!
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            when (LTab.valueOf(tab)) {
                LTab.TABLE -> item { Column { TablesView(pg.tables, emptySet()) } }
                LTab.RESULTS -> item { Column { EventsByRound(pg.results, "Nema odigranih utakmica") } }
                LTab.FIXTURES -> item { Column { EventsByRound(pg.fixtures, "Nema zakazanih utakmica") } }
                LTab.PLAYERS -> item { Column { TopPlayers(pg) } }
            }
        }
    }
}

@Composable
private fun SeasonPicker(list: List<String>, sel: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text("Sezona ${sel ?: list.firstOrNull() ?: ""}")
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            list.take(15).forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { open = false; onPick(s) }) }
        }
    }
}

/** Standings for a league (by key); highlights [highlight] team keys. Used by match and team screens. */
@Composable
fun StandingsBlock(leagueKey: String, season: String?, highlight: Set<String>) {
    val app = LocalApp.current
    val st = rememberLoad(leagueKey, season, "standings") { app.repo.league(leagueKey, season).tables }
    when {
        st.data == null && st.value.loading -> Loading()
        st.data == null -> ErrorBox(st.value.error) { st.reload() }
        else -> TablesView(st.data!!, highlight)
    }
}

@Composable
fun TablesView(tables: List<StandingTable>, highlight: Set<String>) {
    val nav = LocalNav.current
    if (tables.isEmpty()) { Empty("Ovo takmičenje nema tabelu (kup / eliminacije)"); return }
    tables.forEach { t -> StandingTableCard(t, highlight) { key, name -> nav.go(Route.TeamPage(key, name)) } }
}

@Composable
private fun StandingTableCard(t: StandingTable, highlight: Set<String>, onTeam: (String, String) -> Unit) {
    val dim = LocalPalette.current.dim
    val hasDraws = t.rows.any { it.draws > 0 }
    val hasPoints = t.rows.any { it.points != null }
    KCard {
        if (t.name.isNotBlank()) CardTitle(t.name)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("#", Modifier.width(28.dp), fontSize = 11.sp, color = dim)
            Text("Tim", Modifier.weight(1f), fontSize = 11.sp, color = dim)
            listOfNotNull("U", "P", if (hasDraws) "N" else null, "I", if (hasPoints) "Gol" else "Koš", if (hasPoints) "Bod" else null).forEach {
                Text(it, Modifier.width(if (it == "Gol" || it == "Koš") 58.dp else 26.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = dim)
            }
        }
        t.rows.forEach { row ->
            val hl = row.team.key in highlight
            Row(
                Modifier.fillMaxWidth()
                    .background(if (hl) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(enabled = row.team.hasPage) { onTeam(row.team.key, row.team.name) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(28.dp)) {
                    val zc = row.zoneColor?.let { c -> runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrNull() }
                    if (zc != null) Box(Modifier.size(3.dp, 18.dp).clip(RoundedCornerShape(2.dp)).background(zc).align(Alignment.CenterStart))
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
                Text("${row.scoresFor}:${row.scoresAgainst}", Modifier.width(58.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = dim)
                if (hasPoints) Text("${row.points ?: 0}", Modifier.width(26.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (t.legend.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                t.legend.forEach { (z, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrDefault(Color.Gray)))
                        Spacer(Modifier.width(8.dp))
                        Text(z, fontSize = 11.sp, color = dim)
                    }
                }
            }
        } else Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Cell(s: String) = Text(s, Modifier.width(26.dp), textAlign = TextAlign.Center, fontSize = 13.sp)

@Composable
private fun EventsByRound(evs: List<Event>, empty: String) {
    val nav = LocalNav.current
    var shown by remember(evs) { mutableStateOf(40) }
    if (evs.isEmpty()) { Empty(empty); return }
    val byRound = evs.take(shown).groupBy { it.roundName ?: "" }
    byRound.forEach { (round, list) ->
        KCard {
            if (round.isNotBlank()) CardTitle(round)
            list.forEachIndexed { i, e ->
                EventRow(e, { nav.go(Route.Match(e.key)) }, showDate = true)
                if (i < list.lastIndex) Divider(64.dp)
            }
        }
    }
    if (evs.size > shown) TextButton(onClick = { shown += 40 }, modifier = Modifier.fillMaxWidth()) { Text("Prikaži još") }
}

@Composable
private fun TopPlayers(pg: LeaguePage) {
    val nav = LocalNav.current
    if (pg.top.isEmpty()) { Empty("Statistika igrača nije dostupna"); return }
    pg.top.forEach { c ->
        KCard {
            CardTitle(c.title)
            c.players.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = p.playerKey != null) { p.playerKey?.let { nav.go(Route.PlayerPage(it, p.name)) } }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", Modifier.width(22.dp), fontSize = 13.sp, color = LocalPalette.current.dim)
                    NetImage(p.playerKey?.substringAfter(':')?.let { net.kravos.sports.data.FotMob.playerPhoto(it) }, 30.dp, p.name, circle = true)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 14.sp, maxLines = 1)
                        p.team?.let { Text(it, fontSize = 11.sp, color = LocalPalette.current.dim, maxLines = 1) }
                    }
                    Text(p.value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (i == 0) K.Accent else MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
