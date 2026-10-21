package net.kravos.sports.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kravos.sports.BuildConfig
import net.kravos.sports.data.Event
import net.kravos.sports.data.SearchHit
import net.kravos.sports.data.Sofa
import net.kravos.sports.data.Sport
import net.kravos.sports.data.ThemeMode
import net.kravos.sports.data.Tournament
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.LocalApp
import net.kravos.sports.ui.LocalNav
import net.kravos.sports.ui.Route
import net.kravos.sports.ui.components.CardTitle
import net.kravos.sports.ui.components.Divider
import net.kravos.sports.ui.components.Empty
import net.kravos.sports.ui.components.ErrorBox
import net.kravos.sports.ui.components.EventRow
import net.kravos.sports.ui.components.KCard
import net.kravos.sports.ui.components.LeagueLogo
import net.kravos.sports.ui.components.Loading
import net.kravos.sports.ui.components.NetImage
import net.kravos.sports.ui.components.Pill
import net.kravos.sports.ui.components.StarButton
import net.kravos.sports.ui.components.TeamLogo
import net.kravos.sports.ui.rememberLoad
import net.kravos.sports.ui.theme.LocalPalette
import net.kravos.sports.update.Release
import net.kravos.sports.update.Updater

@Composable
private fun TopBar(title: String, sub: String? = null, logo: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
        if (logo != null) { logo(); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 12.sp, color = LocalPalette.current.dim, maxLines = 1)
        }
        actions()
    }
}

@Composable
private fun LargeTitle(text: String, trailing: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }

// ======================= TEAM =======================

@Composable
fun TeamScreen(r: Route.TeamPage, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val favs by app.prefs.teams.collectAsState()
    val team = rememberLoad(r.id, "team") { app.sofa.team(r.id) }
    val next = rememberLoad(r.id, "next", refreshMs = { l -> if (l.orEmpty().any { it.status.live }) 20_000L else 0L }) { app.sofa.teamEvents(r.id, true) }
    val last = rememberLoad(r.id, "last") { app.sofa.teamEvents(r.id, false) }
    val seasons = rememberLoad(r.id, "ts") { app.sofa.teamStandingsSeasons(r.id) }
    var tab by rememberSaveable(r.id) { mutableStateOf(0) }
    val t = team.data

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        TopBar(t?.name ?: r.name, t?.country, logo = { NetImage(Sofa.teamLogo(r.id), 32.dp, r.name) }) {
            StarButton(favs.any { it.id == r.id }) { t?.let { app.prefs.toggleTeam(it, it.sport ?: "football") } }
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            listOf("Utakmice", "Tabela").forEachIndexed { i, s -> Pill(s, tab == i, { tab = i }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            if (tab == 0) {
                val upcoming = next.data.orEmpty().sortedBy { it.start }.take(6)
                val past = last.data.orEmpty().filter { !it.status.notStarted }.sortedByDescending { it.start }.take(15)
                if (next.data == null && last.data == null) item { Loading() }
                if (upcoming.isNotEmpty()) item { EventsCard("Sledeće", upcoming, r.id) }
                if (past.isNotEmpty()) item {
                    Column {
                        FormStrip(past.take(10), r.id)
                        EventsCard("Poslednje", past, r.id)
                    }
                }
            } else {
                val list = seasons.data.orEmpty()
                if (seasons.data == null) item { Loading() }
                else if (list.isEmpty()) item { Empty("Nema tabele za ovaj tim") }
                list.filter { it.first.utId != null }.take(3).forEach { (tour, s) ->
                    item {
                        Column {
                            Row(Modifier.fillMaxWidth().clickable { nav.go(Route.League(tour.utId!!, tour.name, s.id)) }.padding(horizontal = 18.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                LeagueLogo(tour, 18.dp); Spacer(Modifier.width(8.dp))
                                Text(tour.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ChevronRight, null, tint = LocalPalette.current.dim)
                            }
                            StandingsBlock(tour.utId!!, s.id, setOf(r.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormStrip(past: List<Event>, teamId: Int) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Forma ", fontSize = 12.sp, color = LocalPalette.current.dim)
        past.filter { it.status.finished }.take(10).reversed().forEach { e ->
            val home = e.home.id == teamId
            val r = when (e.winner) { 3 -> "D"; 1 -> if (home) "W" else "L"; 2 -> if (home) "L" else "W"; else -> "?" }
            net.kravos.sports.ui.components.FormBadge(r)
        }
    }
}

@Composable
private fun EventsCard(title: String, list: List<Event>, highlight: Int? = null) {
    val nav = LocalNav.current
    KCard {
        CardTitle(title)
        list.forEachIndexed { i, e ->
            Column {
                Row(Modifier.padding(start = 14.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LeagueLogo(e.tournament, 12.dp); Spacer(Modifier.width(6.dp))
                    Text(e.tournament?.name ?: "", fontSize = 11.sp, color = LocalPalette.current.dim, maxLines = 1)
                }
                EventRow(e, { nav.go(Route.Match(e.id)) }, showDate = true, highlightTeam = highlight)
            }
            if (i < list.lastIndex) Divider(64.dp)
        }
    }
}

// ======================= PLAYER =======================

@Composable
fun PlayerScreen(r: Route.PlayerPage, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val p = rememberLoad(r.id, "p") { app.sofa.player(r.id) }
    val evs = rememberLoad(r.id, "pe") { app.sofa.playerEvents(r.id) }
    val pl = p.data
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        TopBar(pl?.name ?: r.name, pl?.team?.name)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                KCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetImage(Sofa.playerPhoto(r.id), 72.dp, r.name, circle = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl?.name ?: r.name, style = MaterialTheme.typography.titleMedium)
                            pl?.team?.let { t ->
                                Row(Modifier.clickable { nav.go(Route.TeamPage(t.id, t.name)) }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TeamLogo(t, 16.dp); Spacer(Modifier.width(6.dp)); Text(t.name, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    if (pl != null) {
                        val facts = listOfNotNull(
                            pl.country?.let { "Država" to it },
                            pl.birth?.let { "Godine" to "${Fmt.age(it)} (${Fmt.fullDate(it)})" },
                            pl.height?.let { "Visina" to "$it cm" },
                            pl.position?.let { "Pozicija" to posSr(it) },
                            pl.number?.let { "Broj" to it },
                            pl.foot?.let { "Noga" to footSr(it) },
                            pl.marketValue?.let { "Vrednost" to Fmt.money(it, pl.currency) },
                        )
                        facts.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                                Text(k, Modifier.width(100.dp), fontSize = 13.sp, color = LocalPalette.current.dim)
                                Text(v, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            val list = evs.data.orEmpty().sortedByDescending { it.start }.take(15)
            if (list.isNotEmpty()) item { EventsCard("Poslednje utakmice", list, pl?.team?.id) }
        }
    }
}

private fun posSr(p: String) = when (p) { "G" -> "Golman"; "D" -> "Odbrana"; "M" -> "Vezni red"; "F" -> "Napad"; else -> p }
private fun footSr(f: String) = when (f.lowercase()) { "right" -> "Desna"; "left" -> "Leva"; "both" -> "Obe"; else -> f }

// ======================= FAVORITES =======================

@Composable
fun FavoritesScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val teams by app.prefs.teams.collectAsState()
    val leagues by app.prefs.leagues.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        LargeTitle("Omiljeno")
        if (teams.isEmpty() && leagues.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Dodaj timove i lige zvezdicom ★", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text("Njihove utakmice biće na vrhu rezultata i na widgetu.", color = LocalPalette.current.dim, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick = { nav.tab(net.kravos.sports.ui.Tab.SEARCH) }) { Text("Pronađi tim") }
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            if (leagues.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    leagues.forEach { l ->
                        Pill(l.name, false, { nav.go(Route.League(l.utId, l.name)) }, leading = { NetImage(Sofa.leagueLogo(l.utId), 16.dp, l.name) })
                    }
                }
            }
            items(teams, key = { it.id }) { ft -> FavTeamCard(ft.id, ft.name) }
        }
    }
}

@Composable
private fun FavTeamCard(id: Int, name: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val next = rememberLoad(id, "fn", refreshMs = { l -> if (l.orEmpty().any { it.status.live }) 20_000L else 120_000L }) { app.sofa.teamEvents(id, true) }
    val last = rememberLoad(id, "fl") { app.sofa.teamEvents(id, false) }
    val liveOrNext = next.data.orEmpty().sortedBy { it.start }.firstOrNull()
    val lastGame = last.data.orEmpty().filter { it.status.finished }.maxByOrNull { it.start }
    KCard {
        Row(Modifier.fillMaxWidth().clickable { nav.go(Route.TeamPage(id, name)) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NetImage(Sofa.teamLogo(id), 26.dp, name)
            Spacer(Modifier.width(10.dp))
            Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = LocalPalette.current.dim)
        }
        Divider(0.dp)
        if (next.data == null && last.data == null) Loading()
        lastGame?.let { EventRow(it, { nav.go(Route.Match(it.id)) }, showDate = true, highlightTeam = id) }
        if (lastGame != null && liveOrNext != null) Divider(64.dp)
        liveOrNext?.let { EventRow(it, { nav.go(Route.Match(it.id)) }, showDate = true, highlightTeam = id) }
    }
}

// ======================= LEAGUES BROWSER =======================

private val POPULAR = mapOf(
    Sport.FOOTBALL to listOf(7 to "UEFA Liga šampiona", 679 to "UEFA Liga Evrope", 17015 to "UEFA Liga konferencija", 210 to "Superliga Srbije",
        17 to "Premijer liga", 8 to "La Liga", 23 to "Serija A", 35 to "Bundesliga", 34 to "Liga 1", 16 to "Svetsko prvenstvo", 1 to "Evropsko prvenstvo", 10783 to "Liga nacija"),
    Sport.BASKETBALL to listOf(138 to "Evroliga", 141 to "Evrokup", 235 to "ABA liga", 132 to "NBA"),
)

@Composable
fun LeaguesScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val sports by app.prefs.sports.collectAsState()
    val favs by app.prefs.leagues.collectAsState()
    var sport by rememberSaveable { mutableStateOf(sports.first()) }
    val cats = rememberLoad(sport, "cats") { app.sofa.categories(sport) }
    val open = remember(sport) { mutableStateMapOf<Int, Boolean>() }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        LargeTitle("Lige")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
            sports.forEach { s -> Pill(s.label, s == sport, { sport = s }, leading = {
                Icon(s.icon, null, Modifier.size(16.dp), tint = if (s == sport) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
            }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            val myLeagues = favs.filter { it.sport == sport.slug }
            if (myLeagues.isNotEmpty()) item {
                KCard {
                    CardTitle("Moje lige")
                    myLeagues.forEach { l -> LeagueLine(l.name, null, l.utId) { nav.go(Route.League(l.utId, l.name)) } }
                }
            }
            POPULAR[sport]?.let { pop ->
                item {
                    KCard {
                        CardTitle("Popularno")
                        pop.forEach { (id, name) -> LeagueLine(name, null, id) { nav.go(Route.League(id, name)) } }
                    }
                }
            }
            val list = cats.data
            when {
                list == null && cats.value.loading -> item { Loading() }
                list == null -> item { ErrorBox(cats.value.error) { cats.reload() } }
                else -> {
                    val sorted = list.sortedWith(compareBy<Pair<Int, String>> { catRank(it.second) }.thenBy { it.second })
                    item { CardTitle("Sve zemlje i takmičenja") }
                    items(sorted, key = { it.first }) { (cid, cname) ->
                        Column {
                            Row(Modifier.fillMaxWidth().clickable { open[cid] = !(open[cid] ?: false) }.padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(cname, Modifier.weight(1f), fontSize = 15.sp)
                                Icon(if (open[cid] == true) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = LocalPalette.current.dim)
                            }
                            if (open[cid] == true) CategoryTournaments(cid)
                            Divider(18.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun catRank(name: String): Int = when (name.lowercase()) {
    "serbia" -> 0
    "europe" -> 1
    "world", "international" -> 2
    "england", "spain", "italy", "germany", "france" -> 3
    else -> 4
}

@Composable
private fun CategoryTournaments(cid: Int) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val ts = rememberLoad(cid, "ct") { app.sofa.categoryTournaments(cid) }
    val list = ts.data
    if (list == null) { Loading(); return }
    if (list.isEmpty()) { Text("Nema takmičenja", Modifier.padding(18.dp), color = LocalPalette.current.dim); return }
    list.forEach { t: Tournament -> LeagueLine(t.name, t, t.utId ?: t.id, indent = true) { nav.go(Route.League(t.utId ?: t.id, t.name)) } }
}

@Composable
private fun LeagueLine(name: String, t: Tournament?, utId: Int, indent: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (indent) 30.dp else 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (t != null) LeagueLogo(t, 20.dp) else NetImage(Sofa.leagueLogo(utId), 20.dp, name)
        Spacer(Modifier.width(12.dp))
        Text(name, Modifier.weight(1f), fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Filled.ChevronRight, null, tint = LocalPalette.current.dim)
    }
}

// ======================= SEARCH =======================

@Composable
fun SearchScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val favs by app.prefs.teams.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(q) { delay(350); debounced = q.trim() }
    val res = rememberLoad(debounced) { if (debounced.length < 2) emptyList() else app.sofa.search(debounced) }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        LargeTitle("Pretraga")
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            placeholder = { Text("Tim, liga ili igrač…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Obriši") } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        val hits = res.data.orEmpty()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
            if (debounced.length >= 2 && res.value.loading && res.data.isNullOrEmpty()) item { Loading() }
            if (res.value.error != null && hits.isEmpty()) item { ErrorBox(res.value.error) { res.reload() } }
            if (debounced.length >= 2 && !res.value.loading && hits.isEmpty() && res.value.error == null) item { Empty("Nema rezultata") }
            items(hits.size) { i ->
                when (val h = hits[i]) {
                    is SearchHit.TeamHit -> Row(Modifier.fillMaxWidth().clickable { nav.go(Route.TeamPage(h.team.id, h.team.name)) }.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        TeamLogo(h.team, 30.dp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.team.name, fontSize = 15.sp, maxLines = 1)
                            Text(listOfNotNull(Sport.of(h.team.sport).label, h.team.country).joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.dim)
                        }
                        StarButton(favs.any { it.id == h.team.id }) { app.prefs.toggleTeam(h.team, h.team.sport ?: "football") }
                    }
                    is SearchHit.LeagueHit -> Row(Modifier.fillMaxWidth().clickable { nav.go(Route.League(h.id, h.name)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NetImage(Sofa.leagueLogo(h.id), 30.dp, h.name); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = 15.sp, maxLines = 1)
                            Text(listOfNotNull("Takmičenje", Sport.of(h.sport).label, h.category).joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.dim)
                        }
                    }
                    is SearchHit.PlayerHit -> Row(Modifier.fillMaxWidth().clickable { nav.go(Route.PlayerPage(h.id, h.name)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NetImage(Sofa.playerPhoto(h.id), 30.dp, h.name, circle = true); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = 15.sp, maxLines = 1)
                            Text(listOfNotNull("Igrač", h.team?.name).joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.dim)
                        }
                    }
                }
            }
        }
    }
}

// ======================= SETTINGS =======================

@Composable
fun SettingsScreen(pad: PaddingValues, release: Release?, onCheckUpdate: () -> Unit) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme by app.prefs.theme.collectAsState()
    val enabled by app.prefs.sports.collectAsState()
    var progress by remember { mutableStateOf<Float?>(null) }
    var updMsg by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(app.prefs.autoUpdateCheck) }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        TopBar("Podešavanja")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                KCard {
                    CardTitle("Ažuriranje")
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Verzija ${BuildConfig.VERSION_NAME}", fontSize = 15.sp)
                            Text(
                                when {
                                    release == null -> "Nije provereno"
                                    Updater.isNewer(release) -> "Dostupna je verzija 1.0.${release.build}"
                                    else -> "Imaš najnoviju verziju"
                                }, fontSize = 12.sp, color = LocalPalette.current.dim,
                            )
                        }
                        if (release != null && Updater.isNewer(release)) {
                            Button(enabled = progress == null, onClick = {
                                scope.launch {
                                    try {
                                        progress = 0f
                                        val f = Updater.download(ctx, release) { progress = it }
                                        progress = null
                                        if (!Updater.install(ctx, f)) updMsg = "Dozvoli instalaciju iz ove aplikacije, pa ponovo pritisni Instaliraj."
                                    } catch (e: Exception) {
                                        progress = null; updMsg = "Greška: ${e.message}"
                                    }
                                }
                            }) { Text("Instaliraj") }
                        } else TextButton(onClick = onCheckUpdate) { Text("Proveri") }
                    }
                    progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(14.dp)) }
                    updMsg?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp)) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Automatski proveri pri pokretanju", Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = auto, onCheckedChange = { auto = it; app.prefs.autoUpdateCheck = it })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                KCard {
                    CardTitle("Tema")
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        listOf(ThemeMode.DARK to "Tamna", ThemeMode.LIGHT to "Svetla", ThemeMode.SYSTEM to "Sistem").forEach { (m, l) ->
                            Pill(l, theme == m, { app.prefs.setTheme(m) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            item {
                KCard {
                    CardTitle("Sportovi (redosled i prikaz)")
                    val all = enabled + Sport.entries.filter { it !in enabled }
                    all.forEach { s ->
                        val on = s in enabled
                        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = { c ->
                                app.prefs.setSports(if (c) enabled + s else enabled - s)
                            })
                            Icon(s.icon, null, Modifier.size(18.dp), tint = LocalPalette.current.dim)
                            Spacer(Modifier.width(8.dp))
                            Text(s.label, Modifier.weight(1f), fontSize = 14.sp, color = if (on) MaterialTheme.colorScheme.onSurface else LocalPalette.current.dim)
                            if (on) {
                                val i = enabled.indexOf(s)
                                IconButton(enabled = i > 0, onClick = { app.prefs.setSports(enabled.toMutableList().apply { add(i - 1, removeAt(i)) }) }) { Icon(Icons.Filled.ArrowUpward, "Gore", Modifier.size(18.dp)) }
                                IconButton(enabled = i < enabled.lastIndex, onClick = { app.prefs.setSports(enabled.toMutableList().apply { add(i + 1, removeAt(i)) }) }) { Icon(Icons.Filled.ArrowDownward, "Dole", Modifier.size(18.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            item {
                KCard {
                    CardTitle("Podaci")
                    Text(
                        "Izvor: SofaScore (nezvanični API, samo za ličnu upotrebu). Veza: " +
                            (if (app.sofa.useBridge) "preko ugrađenog pregledača — ${app.sofa.bridgeStatus}" else "direktna") +
                            (app.sofa.lastError?.let { "\nPoslednja greška: ${it.take(120)}" } ?: ""),
                        fontSize = 12.sp, color = LocalPalette.current.dim, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Suppress("unused") private val keepBox: @Composable () -> Unit = { Box {} }
