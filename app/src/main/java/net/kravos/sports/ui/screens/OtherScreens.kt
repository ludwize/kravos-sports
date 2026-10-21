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
import net.kravos.sports.data.Sport
import net.kravos.sports.data.Team
import net.kravos.sports.data.ThemeMode
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.LocalApp
import net.kravos.sports.ui.LocalNav
import net.kravos.sports.ui.Route
import net.kravos.sports.ui.components.CardTitle
import net.kravos.sports.ui.components.Divider
import net.kravos.sports.ui.components.Empty
import net.kravos.sports.ui.components.ErrorBox
import net.kravos.sports.ui.components.EventRow
import net.kravos.sports.ui.components.FormBadge
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

@Composable
private fun InfoRows(rows: List<net.kravos.sports.data.InfoLine>) = rows.forEach { (k, v) ->
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        Text(k, Modifier.width(100.dp), fontSize = 13.sp, color = LocalPalette.current.dim)
        Text(v, fontSize = 13.sp)
    }
}

// ======================= TEAM =======================

@Composable
fun TeamScreen(r: Route.TeamPage, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val favs by app.prefs.teams.collectAsState()
    val load = rememberLoad(r.key, refreshMs = { p -> if (p?.upcoming?.any { it.status.live } == true) 30_000L else 0L }) { app.repo.team(r.key) }
    val pg = load.data
    var tab by rememberSaveable(r.key) { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        TopBar(pg?.team?.name ?: r.name, pg?.team?.country, logo = { NetImage(pg?.team?.logo, 32.dp, r.name) }) {
            StarButton(favs.any { it.key == r.key }) { pg?.team?.let { app.prefs.toggleTeam(it) } }
        }
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            listOf("Utakmice", "Tabela", "Info").forEachIndexed { i, s -> Pill(s, tab == i, { tab = i }) }
        }
        when {
            pg == null && load.value.loading -> { Loading(); return@Column }
            pg == null -> { ErrorBox(load.value.error) { load.reload() }; return@Column }
        }
        pg!!
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            when (tab) {
                0 -> {
                    val upcoming = pg.upcoming.take(8)
                    val past = pg.past.take(20)
                    if (upcoming.isEmpty() && past.isEmpty()) item { Empty("Nema utakmica") }
                    if (upcoming.isNotEmpty()) item { EventsCard("Sledeće", upcoming, r.key) }
                    if (past.isNotEmpty()) item {
                        Column {
                            FormStrip(past.take(10), r.key)
                            EventsCard("Poslednje", past, r.key)
                        }
                    }
                }
                1 -> {
                    if (pg.tables.isEmpty()) item { Empty("Nema tabele za ovaj tim") }
                    pg.tables.forEach { (lk, table) ->
                        item {
                            Column {
                                Row(Modifier.fillMaxWidth().clickable { nav.go(Route.League(lk, table.name)) }.padding(horizontal = 18.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(table.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Icon(Icons.Filled.ChevronRight, null, tint = LocalPalette.current.dim)
                                }
                                TablesView(listOf(table.copy(name = "")), setOf(r.key))
                            }
                        }
                    }
                }
                else -> item {
                    KCard {
                        CardTitle("O klubu")
                        InfoRows(listOfNotNull(pg.team.country?.let { net.kravos.sports.data.InfoLine("Država", it) }) + pg.info)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FormStrip(past: List<Event>, teamKey: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Forma ", fontSize = 12.sp, color = LocalPalette.current.dim)
        past.filter { it.status.finished }.take(10).reversed().forEach { e ->
            val home = e.home.key == teamKey
            val r = when (e.winner) { 3 -> "D"; 1 -> if (home) "W" else "L"; 2 -> if (home) "L" else "W"; else -> "?" }
            FormBadge(r)
        }
    }
}

@Composable
private fun EventsCard(title: String, list: List<Event>, highlight: String? = null) {
    val nav = LocalNav.current
    KCard {
        CardTitle(title)
        list.forEachIndexed { i, e ->
            Column {
                Row(Modifier.padding(start = 14.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LeagueLogo(e.tournament, 12.dp); Spacer(Modifier.width(6.dp))
                    Text(listOfNotNull(e.tournament?.name, e.note).joinToString("   "), fontSize = 11.sp, color = LocalPalette.current.dim, maxLines = 1)
                }
                EventRow(e, { nav.go(Route.Match(e.key)) }, showDate = true, highlightTeam = highlight)
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
    val load = rememberLoad(r.key) { app.repo.player(r.key) }
    val pl = load.data
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        TopBar(pl?.name ?: r.name, pl?.team?.name)
        when {
            pl == null && load.value.loading -> { Loading(); return@Column }
            pl == null -> { ErrorBox(load.value.error) { load.reload() }; return@Column }
        }
        pl!!
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item {
                KCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetImage(pl.photo, 72.dp, pl.name, circle = true)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.titleMedium)
                            pl.team?.let { t ->
                                Row(Modifier.clickable { nav.go(Route.TeamPage(t.key, t.name)) }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TeamLogo(t, 16.dp); Spacer(Modifier.width(6.dp)); Text(t.name, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    InfoRows(pl.info)
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (pl.seasonStats.isNotEmpty()) item {
                KCard {
                    CardTitle(pl.seasonStats.first().second)
                    pl.seasonStats.drop(1).forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(k, Modifier.weight(1f), fontSize = 14.sp, color = LocalPalette.current.dim)
                            Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (pl.recent.isNotEmpty()) item { EventsCard("Poslednje utakmice", pl.recent, pl.team?.key) }
        }
    }
}

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
                        Pill(l.name, false, { nav.go(Route.League(l.key, l.name)) }, leading = { NetImage(l.logo, 16.dp, l.name) })
                    }
                }
            }
            items(teams, key = { it.key }) { ft -> FavTeamCard(ft.key, ft.name, ft.logo) }
        }
    }
}

@Composable
private fun FavTeamCard(key: String, name: String, logo: String?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val load = rememberLoad(key, "fav", refreshMs = { p -> if (p?.upcoming?.any { it.status.live } == true) 30_000L else 300_000L }) { app.repo.team(key) }
    val pg = load.data
    val next = pg?.upcoming?.firstOrNull()
    val last = pg?.past?.firstOrNull()
    KCard {
        Row(Modifier.fillMaxWidth().clickable { nav.go(Route.TeamPage(key, name)) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NetImage(logo, 26.dp, name)
            Spacer(Modifier.width(10.dp))
            Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = LocalPalette.current.dim)
        }
        Divider(0.dp)
        if (pg == null && load.value.loading) Loading()
        last?.let { EventRow(it, { nav.go(Route.Match(it.key)) }, showDate = true, highlightTeam = key) }
        if (last != null && next != null) Divider(64.dp)
        next?.let { EventRow(it, { nav.go(Route.Match(it.key)) }, showDate = true, highlightTeam = key) }
    }
}

// ======================= LEAGUES BROWSER =======================

@Composable
fun LeaguesScreen(pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val favs by app.prefs.leagues.collectAsState()
    val browsable = listOf(Sport.FOOTBALL, Sport.BASKETBALL)
    var sport by rememberSaveable { mutableStateOf(Sport.FOOTBALL) }
    val cats = rememberLoad(sport, "cats") { app.repo.catalog(sport) }
    val open = remember(sport) { mutableStateMapOf<String, Boolean>() }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        LargeTitle("Lige")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp)) {
            browsable.forEach { s -> Pill(s.label, s == sport, { sport = s }, leading = {
                Icon(s.icon, null, Modifier.size(16.dp), tint = if (s == sport) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
            }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            val mine = favs.filter { it.sport == sport.slug }
            if (mine.isNotEmpty()) item {
                KCard {
                    CardTitle("Moje lige")
                    mine.forEach { l -> LeagueLine(l.name, l.logo) { nav.go(Route.League(l.key, l.name)) } }
                }
            }
            val list = cats.data
            when {
                list == null && cats.value.loading -> item { Loading() }
                list == null -> item { ErrorBox(cats.value.error) { cats.reload() } }
                else -> {
                    val (shownOpen, collapsible) = list.partition { it.name == "Popularno" || it.leagues.size <= 3 && list.size <= 3 }
                    shownOpen.forEach { g ->
                        item(key = "g-" + g.name) {
                            KCard {
                                CardTitle(g.name)
                                g.leagues.forEach { l -> LeagueLine(l.name, l.logo) { nav.go(Route.League(l.key, l.name)) } }
                            }
                        }
                    }
                    if (collapsible.isNotEmpty()) item { CardTitle("Sve zemlje i takmičenja") }
                    items(collapsible, key = { "c-" + it.name }) { g ->
                        Column {
                            Row(Modifier.fillMaxWidth().clickable { open[g.name] = !(open[g.name] ?: false) }.padding(horizontal = 18.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(g.name, Modifier.weight(1f), fontSize = 15.sp)
                                Text("${g.leagues.size}", fontSize = 12.sp, color = LocalPalette.current.dim)
                                Icon(if (open[g.name] == true) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = LocalPalette.current.dim)
                            }
                            if (open[g.name] == true) g.leagues.forEach { l -> LeagueLine(l.name, l.logo, indent = true) { nav.go(Route.League(l.key, l.name)) } }
                            Divider(18.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeagueLine(name: String, logo: String?, indent: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = if (indent) 30.dp else 14.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        NetImage(logo, 20.dp, name)
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
    val res = rememberLoad(debounced) { if (debounced.length < 2) emptyList() else app.repo.search(debounced) }

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
                    is SearchHit.TeamHit -> TeamHitRow(h.team, favs.any { it.key == h.team.key }, { nav.go(Route.TeamPage(h.team.key, h.team.name)) }) { app.prefs.toggleTeam(h.team) }
                    is SearchHit.LeagueHit -> Row(Modifier.fillMaxWidth().clickable { nav.go(Route.League(h.key, h.name)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NetImage(h.logo, 30.dp, h.name); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = 15.sp, maxLines = 1)
                            Text(listOfNotNull("Takmičenje", Sport.of(h.sport).label, h.category).joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.dim)
                        }
                    }
                    is SearchHit.PlayerHit -> Row(Modifier.fillMaxWidth().clickable { nav.go(Route.PlayerPage(h.key, h.name)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NetImage(h.photo, 30.dp, h.name, circle = true); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.name, fontSize = 15.sp, maxLines = 1)
                            Text(listOfNotNull("Igrač", h.team).joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.dim)
                        }
                    }
                    is SearchHit.MatchHit -> Row(Modifier.fillMaxWidth().clickable { nav.go(Route.Match(h.key)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(h.title, fontSize = 15.sp, maxLines = 1)
                            Text("Utakmica · ${h.sub}", fontSize = 12.sp, color = LocalPalette.current.dim, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamHitRow(t: Team, fav: Boolean, onOpen: () -> Unit, onStar: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(t, 30.dp); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, fontSize = 15.sp, maxLines = 1)
            Text(listOfNotNull(Sport.of(t.sport).label, t.country).joinToString(" · "), fontSize = 12.sp, color = LocalPalette.current.dim)
        }
        StarButton(fav, onStar)
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
                        "Fudbal: FotMob · Evroliga/Evrokup: zvanični API · ostala košarka, tenis, hokej, kriket: LiveScore · F1, UFC, NFL, MLB: ESPN.\nSamo za ličnu upotrebu." +
                            (app.repo.lastError?.let { "\nPoslednja greška: ${it.take(140)}" } ?: ""),
                        fontSize = 12.sp, color = LocalPalette.current.dim, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Suppress("unused") private val keepBox: @Composable () -> Unit = { Box {} }
