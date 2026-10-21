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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import net.kravos.sports.data.BoxScore
import net.kravos.sports.data.Event
import net.kravos.sports.data.FotMob
import net.kravos.sports.data.Incident
import net.kravos.sports.data.LineupPlayer
import net.kravos.sports.data.MatchDetail
import net.kravos.sports.data.Shot
import net.kravos.sports.data.Sport
import net.kravos.sports.data.Src
import net.kravos.sports.data.periodSr
import net.kravos.sports.data.statGroupSr
import net.kravos.sports.data.statSr
import net.kravos.sports.data.statusSr
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
import net.kravos.sports.ui.components.HeatmapPitch
import net.kravos.sports.ui.components.KCard
import net.kravos.sports.ui.components.LeagueLogo
import net.kravos.sports.ui.components.LineupPitch
import net.kravos.sports.ui.components.LiveDot
import net.kravos.sports.ui.components.Loading
import net.kravos.sports.ui.components.MomentumGraph
import net.kravos.sports.ui.components.NetImage
import net.kravos.sports.ui.components.Pill
import net.kravos.sports.ui.components.RatingBadge
import net.kravos.sports.ui.components.ShotMap
import net.kravos.sports.ui.components.StatBar
import net.kravos.sports.ui.components.TeamLogo
import net.kravos.sports.ui.rememberLoad
import net.kravos.sports.ui.theme.K
import net.kravos.sports.ui.theme.LocalPalette

private enum class MTab(val label: String) {
    OVERVIEW("Pregled"), STATS("Statistika"), LINEUPS("Postave"), PLAYERS("Igrači"), HEAT("Heatmapa"),
    SHOTS("Šutevi"), RACE("Rezultati"), TABLE("Tabela"), H2H("Međusobni")
}

private fun parseColor(hex: String?, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor("#" + hex!!.removePrefix("#")))
}.getOrDefault(fallback)

private fun Color.lum(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
private fun dist(a: Color, b: Color): Float = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(key: String, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    var wasLive by remember(key) { mutableStateOf(false) }
    val load = rememberLoad(key, refreshMs = { d ->
        when {
            d?.event?.status?.live == true -> 15_000L
            d?.event?.status?.notStarted == true -> 60_000L
            else -> 0L
        }
    }) { app.repo.match(key, wasLive).also { wasLive = it.event.status.live } }
    val d = load.data
    val e = d?.event
    val sport = Sport.of(e?.sport)
    val live = e?.status?.live == true

    val lu = d?.lineups
    val hasLineups = lu?.home?.players?.isNotEmpty() == true || lu?.away?.players?.isNotEmpty() == true
    val tabs = buildList {
        add(if (e?.race != null) MTab.RACE else MTab.OVERVIEW)
        if (d?.stats?.isNotEmpty() == true) add(MTab.STATS)
        if (hasLineups) add(MTab.LINEUPS)
        if (d?.box != null && d.box.columns.isNotEmpty()) add(MTab.PLAYERS)
        if (d?.box != null && d.box.columns.isEmpty() && !hasLineups) add(MTab.LINEUPS)
        if (key.startsWith("${Src.FM}:") && hasLineups && e?.status?.notStarted == false) add(MTab.HEAT)
        if (d?.shots?.isNotEmpty() == true) add(MTab.SHOTS)
        if (d?.tableLeague != null) add(MTab.TABLE)
        if (d?.h2h != null) add(MTab.H2H)
    }
    var tabName by rememberSaveable(key) { mutableStateOf(MTab.OVERVIEW.name) }
    val tab = tabs.firstOrNull { it.name == tabName } ?: tabs.first()

    var sheet by remember { mutableStateOf<Pair<LineupPlayer, Boolean>?>(null) }

    val homeColor = parseColor(d?.homeColor, K.Home).let { if (it.lum() > 0.92f || it.lum() < 0.06f) K.Home else it }
    val awayColor = parseColor(d?.awayColor, K.Away).let { c -> if (c.lum() > 0.92f || c.lum() < 0.06f || dist(c, homeColor) < 0.25f) K.Away else c }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
            if (e != null) {
                Row(
                    Modifier.weight(1f).clickable(enabled = e.tournament?.key != null) {
                        e.tournament?.key?.let { nav.go(Route.League(it, e.tournament.name)) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeagueLogo(e.tournament, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOfNotNull(e.tournament?.name, e.roundName).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            d == null && load.value.loading -> { Loading(); return@Column }
            d == null -> { ErrorBox(load.value.error) { load.reload() }; return@Column }
        }
        d!!; e!!

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item { if (e.race != null) RaceHeader(e) else ScoreHeader(d, homeColor, awayColor) }
            if (tabs.size > 1) stickyTabs(tabs, tab) { tabName = it.name }
            when (tab) {
                MTab.OVERVIEW -> overview(d, sport, homeColor, awayColor) { p, h -> sheet = p to h }
                MTab.RACE -> raceTab(e)
                MTab.STATS -> item { Column { StatsBlock(d, homeColor, awayColor) } }
                MTab.LINEUPS -> lineupsTab(d, homeColor, awayColor, sport) { p, h -> sheet = p to h }
                MTab.PLAYERS -> boxTab(e, d.box!!)
                MTab.HEAT -> item { Column { HeatBlock(d, live, homeColor, awayColor) { p, h -> sheet = p to h } } }
                MTab.SHOTS -> item { Column { ShotsBlock(d, homeColor, awayColor) } }
                MTab.TABLE -> item { Column { StandingsBlock(d.tableLeague!!, d.tableSeason, setOf(e.home.key, e.away.key)) } }
                MTab.H2H -> item { Column { H2HBlock(d) } }
            }
        }
    }

    sheet?.let { (p, isHome) ->
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheet = null }, sheetState = st) {
            PlayerSheet(d!!, p, isHome, live) {
                sheet = null
                nav.go(Route.PlayerPage("${Src.FM}:${p.id}", p.name))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LazyListScope.stickyTabs(tabs: List<MTab>, sel: MTab, onSel: (MTab) -> Unit) {
    stickyHeader {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(sel).coerceAtLeast(0),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                divider = {},
            ) {
                tabs.forEach { t ->
                    Tab(selected = t == sel, onClick = { onSel(t) }, text = { Text(t.label, fontWeight = if (t == sel) FontWeight.SemiBold else FontWeight.Normal) })
                }
            }
        }
    }
}

// ---------- header ----------

@Composable
private fun ScoreHeader(d: MatchDetail, homeColor: Color, awayColor: Color) {
    val e = d.event
    val nav = LocalNav.current
    val pal = LocalPalette.current
    val sport = Sport.of(e.sport)
    KCard {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            TeamBlock(e.home.name, Modifier.weight(1f), { TeamLogo(e.home, 56.dp) }, e.home.hasPage) { nav.go(Route.TeamPage(e.home.key, e.home.name)) }
            Column(Modifier.widthIn(min = 120.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.status.notStarted || (e.status.cancelled && e.homeScore.current == null)) {
                    Text(Fmt.time(e.start), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(Fmt.dayLabel(Fmt.localDate(e.start)), color = pal.dim, fontSize = 13.sp)
                    if (e.status.cancelled) Text(statusSr(e.status.description), color = K.Loss, fontSize = 13.sp)
                } else {
                    Text(
                        "${e.homeScore.display ?: 0}  –  ${e.awayScore.display ?: 0}",
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = if (e.status.live) K.Live else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (e.status.live) { LiveDot(); Spacer(Modifier.width(5.dp)) }
                        Text(
                            if (e.status.live) Fmt.liveClock(e) else Fmt.statusShort(e).let { if (it == "Kraj") "Kraj utakmice" else it },
                            color = if (e.status.live) K.Live else pal.dim, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                    if (e.homeScore.penalties != null && e.awayScore.penalties != null) Text("Penali ${e.homeScore.penalties}:${e.awayScore.penalties}", fontSize = 12.sp, color = pal.dim)
                    if (e.status.live && e.homeScore.point != null) Text("${e.homeScore.point} : ${e.awayScore.point}", color = K.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            TeamBlock(e.away.name, Modifier.weight(1f), { TeamLogo(e.away, 56.dp) }, e.away.hasPage) { nav.go(Route.TeamPage(e.away.key, e.away.name)) }
        }
        val goals = d.incidents.filter { it.type == "goal" }
        if (goals.isNotEmpty() && goals.size <= 14) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp)) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    goals.filter { it.isHome == true }.forEach { g -> Text(scorerText(g), fontSize = 12.sp, color = pal.dim, maxLines = 1) }
                }
                Text(" ⚽ ", fontSize = 11.sp, color = pal.dim)
                Column(Modifier.weight(1f)) {
                    goals.filter { it.isHome == false }.forEach { g -> Text(scorerText(g), fontSize = 12.sp, color = pal.dim, maxLines = 1) }
                }
            }
        }
        if (sport != Sport.FOOTBALL && (e.homeScore.periods.isNotEmpty() || e.awayScore.periods.isNotEmpty())) PeriodTable(e)
        Spacer(Modifier.height(12.dp))
        @Suppress("UNUSED_VARIABLE") val keep = homeColor to awayColor
    }
}

private fun scorerText(g: Incident): String {
    val t = g.time?.let { if (g.addedTime != null) "$it+${g.addedTime}'" else "$it'" } ?: ""
    val suf = when (g.cls) { "ownGoal" -> " (ag)"; "penalty" -> " (p)"; else -> "" }
    return "${g.player ?: ""}$suf $t".trim()
}

@Composable
private fun TeamBlock(name: String, modifier: Modifier, logo: @Composable () -> Unit, clickable: Boolean, onClick: () -> Unit) {
    Column(modifier.clickable(enabled = clickable, onClick = onClick).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        logo()
        Spacer(Modifier.height(8.dp))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PeriodTable(e: Event) {
    val pal = LocalPalette.current
    val sport = Sport.of(e.sport)
    val n = maxOf(e.homeScore.periods.size, e.awayScore.periods.size)
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).horizontalScroll(rememberScrollState())) {
        @Composable
        fun line(label: String, cells: List<String>, total: String, bold: Boolean) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(90.dp), fontSize = 12.sp, color = if (bold) MaterialTheme.colorScheme.onSurface else pal.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                cells.forEach { Text(it, Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = if (bold) MaterialTheme.colorScheme.onSurface else pal.dim) }
                Text(total, Modifier.width(40.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        val head = (1..n).map { if (sport.setBased) "S$it" else "$it" } + listOfNotNull(e.homeScore.overtime?.let { "OT" })
        line("", head, if (sport.setBased) "Set" else "Uk.", false)
        fun cells(s: net.kravos.sports.data.Score) = (0 until n).map { i ->
            val v = s.periods.getOrNull(i)?.toString() ?: "-"
            val tb = s.periodTiebreaks.getOrNull(i)
            if (tb != null) "$v($tb)" else v
        } + listOfNotNull(s.overtime?.toString())
        line(e.home.code ?: e.home.shortName, cells(e.homeScore), e.homeScore.display?.toString() ?: "", true)
        line(e.away.code ?: e.away.shortName, cells(e.awayScore), e.awayScore.display?.toString() ?: "", true)
    }
}

@Composable
private fun RaceHeader(e: Event) {
    val pal = LocalPalette.current
    KCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(e.tournament?.name ?: "", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(e.home.name.substringAfter(" · ", e.home.name), fontSize = 14.sp, color = pal.dim)
            if (e.away.name.isNotBlank()) Text(e.away.name, fontSize = 12.sp, color = pal.dim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.status.live) { LiveDot(); Spacer(Modifier.width(5.dp)) }
                Text(if (e.status.notStarted) "${Fmt.dayLabel(Fmt.localDate(e.start))} u ${Fmt.time(e.start)}" else if (e.status.live) "Uživo" else "Završeno",
                    color = if (e.status.live) K.Live else pal.dim, fontSize = 14.sp)
            }
        }
    }
}

private fun LazyListScope.raceTab(e: Event) {
    val res = e.race.orEmpty()
    if (res.isEmpty()) { item { Empty("Rezultati još nisu dostupni") }; return }
    item {
        KCard {
            CardTitle(if (e.status.notStarted) "Učesnici" else "Plasman")
            res.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (e.status.notStarted) "" else "${r.pos}.", Modifier.width(30.dp), fontWeight = FontWeight.Bold,
                        color = when (r.pos) { 1 -> Color(0xFFFFD60A); 2 -> Color(0xFFC0C0C0); 3 -> Color(0xFFCD7F32); else -> MaterialTheme.colorScheme.onSurface })
                    if (r.flag != null) { NetImage(r.flag, 16.dp, ""); Spacer(Modifier.width(8.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 14.sp)
                        r.team?.let { Text(it, fontSize = 11.sp, color = LocalPalette.current.dim) }
                    }
                    r.detail?.let { Text(it, fontSize = 12.sp, color = LocalPalette.current.dim) }
                }
                if (i < res.lastIndex) Divider(44.dp)
            }
        }
    }
}

// ---------- overview ----------

private fun LazyListScope.overview(d: MatchDetail, sport: Sport, homeColor: Color, awayColor: Color, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    if (d.incidents.isNotEmpty()) item(key = "ov-inc") { IncidentsCard(d, homeColor, awayColor) }
    if (d.momentum.isNotEmpty()) item(key = "ov-mom") { MomentumCard(d, homeColor, awayColor) }
    val top = d.stats.firstOrNull { it.period == "ALL" }?.groups?.firstOrNull()?.items?.take(8).orEmpty()
    if (top.isNotEmpty()) item(key = "ov-stats") {
        KCard {
            CardTitle("Ključna statistika")
            TeamsLegend(d.event)
            top.forEach { StatBar(it, homeColor, awayColor) }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (d.lineups != null && sport.hasPitch) item(key = "ov-best") { BestPlayersCard(d, onPlayer) }
    if (d.form?.home != null || d.form?.away != null) item(key = "ov-form") { FormCard(d) }
    item(key = "ov-info") { InfoCard(d) }
}

@Composable
private fun IncidentsCard(d: MatchDetail, homeColor: Color, awayColor: Color) {
    val pal = LocalPalette.current
    val shown = d.incidents.filter { it.type != "injuryTime" || it.text != null }
    if (shown.isEmpty()) return
    // newest first while live, chronological once finished
    val list = if (d.event.status.live) shown.asReversed() else shown
    KCard {
        CardTitle("Tok utakmice")
        list.forEach { i ->
            when (i.type) {
                "period" -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(i.text ?: "", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pal.dim)
                    if (i.homeScore != null) Text("${i.homeScore} – ${i.awayScore}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                "injuryTime" -> Text("+${i.text} min nadoknade", Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = pal.dim)
                else -> IncidentRow(i, homeColor, awayColor)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun IncidentRow(i: Incident, homeColor: Color, awayColor: Color) {
    val pal = LocalPalette.current
    val home = i.isHome != false
    val minute = i.time?.let { if (i.addedTime != null) "$it+${i.addedTime}'" else "$it'" } ?: ""
    val icon: @Composable () -> Unit = {
        when (i.type) {
            "goal" -> Text("⚽", fontSize = 15.sp)
            "card" -> Box(Modifier.size(10.dp, 14.dp).clip(RoundedCornerShape(2.dp)).background(
                when (i.cls) { "red" -> K.Loss; "yellowRed" -> Color(0xFFFF7A00); else -> K.Yellow }))
            "substitution" -> Text("⇅", fontSize = 15.sp, color = K.Accent)
            "var" -> Text("VAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.dim)
            "missedPenalty" -> Text("✕", fontSize = 13.sp, color = K.Loss)
            else -> Text("•")
        }
    }
    val text: @Composable (Alignment.Horizontal) -> Unit = { al ->
        Column(horizontalAlignment = al) {
            when (i.type) {
                "goal" -> {
                    Text((i.player ?: "") + when (i.cls) { "ownGoal" -> " (autogol)"; "penalty" -> " (penal)"; else -> "" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    i.assist?.let { Text("asist. $it", fontSize = 11.sp, color = pal.dim, maxLines = 1) }
                }
                "substitution" -> {
                    Text("↑ ${i.playerIn ?: ""}", fontSize = 13.sp, color = K.Win, maxLines = 1)
                    Text("↓ ${i.playerOut ?: ""}", fontSize = 11.sp, color = pal.dim, maxLines = 1)
                }
                "var" -> Text("VAR: ${listOfNotNull(i.player, i.text).joinToString(" – ")}", fontSize = 12.sp, maxLines = 2)
                "missedPenalty" -> Text("Promašen penal – ${i.player ?: ""}", fontSize = 12.sp, maxLines = 1)
                else -> {
                    Text(i.player ?: "", fontSize = 13.sp, maxLines = 1)
                    i.text?.let { Text(it, fontSize = 11.sp, color = pal.dim, maxLines = 1) }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (home) Row(verticalAlignment = Alignment.CenterVertically) { text(Alignment.End); Spacer(Modifier.width(8.dp)); icon() }
        }
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(minute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pal.dim)
            if (i.type == "goal" && i.homeScore != null) Text("${i.homeScore}-${i.awayScore}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = if (home) homeColor else awayColor)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (!home) Row(verticalAlignment = Alignment.CenterVertically) { icon(); Spacer(Modifier.width(8.dp)); text(Alignment.Start) }
        }
    }
}

@Composable
private fun MomentumCard(d: MatchDetail, homeColor: Color, awayColor: Color) {
    val e = d.event
    val goals = d.incidents.filter { it.type == "goal" && it.time != null }.map { it.time!!.toDouble() to (it.isHome == true) }
    KCard {
        CardTitle("Momentum napada")
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(e.home, 14.dp); Spacer(Modifier.width(6.dp)); Text(e.home.shortName, fontSize = 11.sp, color = LocalPalette.current.dim)
        }
        MomentumGraph(d.momentum, homeColor, awayColor, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), goals)
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(e.away, 14.dp); Spacer(Modifier.width(6.dp)); Text(e.away.shortName, fontSize = 11.sp, color = LocalPalette.current.dim)
        }
    }
}

@Composable
private fun TeamsLegend(e: Event) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(e.home, 18.dp)
        Spacer(Modifier.weight(1f))
        TeamLogo(e.away, 18.dp)
    }
}

@Composable
private fun BestPlayersCard(d: MatchDetail, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    val lu = d.lineups ?: return
    val all = (lu.home?.players.orEmpty().map { it to true } + lu.away?.players.orEmpty().map { it to false })
        .filter { it.first.rating != null }.sortedByDescending { it.first.rating }.take(3)
    if (all.isEmpty()) return
    KCard {
        CardTitle("Najbolji igrači")
        all.forEachIndexed { i, (p, h) ->
            Row(Modifier.fillMaxWidth().clickable { onPlayer(p, h) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(FotMob.playerPhoto(p.id), 34.dp, p.shortName, circle = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    TeamLogo(if (h) d.event.home else d.event.away, 14.dp)
                }
                RatingBadge(p.rating)
            }
            if (i < all.lastIndex) Divider(58.dp)
        }
    }
}

@Composable
private fun FormCard(d: MatchDetail) {
    val pf = d.form ?: return
    KCard {
        CardTitle("Forma pred meč")
        listOf(d.event.home to pf.home, d.event.away to pf.away).forEach { (t, tf) ->
            if (tf == null || tf.form.isEmpty()) return@forEach
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(t, 22.dp); Spacer(Modifier.width(10.dp))
                Text(t.shortName, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                tf.form.takeLast(5).forEach { FormBadge(it) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun InfoCard(d: MatchDetail) {
    val e = d.event
    KCard {
        CardTitle("Informacije")
        InfoRow("Početak", "${Fmt.fullDate(e.start)} u ${Fmt.time(e.start)}")
        e.tournament?.let { InfoRow("Takmičenje", listOfNotNull(it.categoryName, it.name).distinct().joinToString(" – ")) }
        e.roundName?.let { InfoRow("Faza", it) }
        d.info.forEach { InfoRow(it.label, it.value) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoRow(k: String, v: String) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
        Text(k, Modifier.width(110.dp), fontSize = 13.sp, color = LocalPalette.current.dim)
        Text(v, fontSize = 13.sp)
    }

// ---------- statistics ----------

@Composable
private fun StatsBlock(d: MatchDetail, homeColor: Color, awayColor: Color) {
    var period by rememberSaveable(d.event.key) { mutableStateOf("ALL") }
    val periods = d.stats
    if (periods.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
        periods.forEach { p -> Pill(periodSr(p.period), p.period == period, { period = p.period }) }
    }
    val cur = periods.firstOrNull { it.period == period } ?: periods.first()
    cur.groups.forEach { g ->
        KCard {
            CardTitle(statGroupSr(g.name))
            if (g == cur.groups.first()) TeamsLegend(d.event)
            g.items.forEach { StatBar(it, homeColor, awayColor) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- lineups ----------

private fun LazyListScope.lineupsTab(d: MatchDetail, homeColor: Color, awayColor: Color, sport: Sport, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    val e = d.event
    val lu = d.lineups
    if (lu == null) {
        // names-only lineups (box without columns)
        val b = d.box ?: return
        item {
            KCard {
                CardTitle("Igrači")
                for (i in 0 until maxOf(b.home.size, b.away.size)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                        Text(b.home.getOrNull(i)?.let { (it.number?.let { n -> "$n " } ?: "") + it.name } ?: "", Modifier.weight(1f), fontSize = 13.sp,
                            fontWeight = if (b.home.getOrNull(i)?.starter == true) FontWeight.SemiBold else FontWeight.Normal)
                        Text(b.away.getOrNull(i)?.let { (it.number?.let { n -> "$n " } ?: "") + it.name } ?: "", Modifier.weight(1f), fontSize = 13.sp,
                            fontWeight = if (b.away.getOrNull(i)?.starter == true) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        return
    }
    val h = lu.home; val a = lu.away
    if (sport.hasPitch && h != null && a != null) item(key = "lu-head") {
        KCard {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(e.home, 20.dp); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.formation ?: "", fontWeight = FontWeight.SemiBold)
                    h.rating?.let { Text("ocena tima ${String.format(java.util.Locale.US, "%.1f", it)}", fontSize = 11.sp, color = LocalPalette.current.dim) }
                }
                if (!lu.confirmed) Text("Očekivane", fontSize = 11.sp, color = LocalPalette.current.dim)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(a.formation ?: "", fontWeight = FontWeight.SemiBold)
                    a.rating?.let { Text("ocena tima ${String.format(java.util.Locale.US, "%.1f", it)}", fontSize = 11.sp, color = LocalPalette.current.dim) }
                }
                Spacer(Modifier.width(8.dp)); TeamLogo(e.away, 20.dp)
            }
            Box(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
                LineupPitch(
                    h.players.filter { !it.substitute }, h.formation, homeColor,
                    a.players.filter { !it.substitute }, a.formation, awayColor,
                    onPlayer,
                )
            }
        }
    }
    item(key = "lu-subs") {
        KCard {
            CardTitle(if (sport.hasPitch) "Klupa" else "Igrači")
            val hs = h?.players.orEmpty().filter { it.substitute || !sport.hasPitch }
            val asub = a?.players.orEmpty().filter { it.substitute || !sport.hasPitch }
            for (i in 0 until maxOf(hs.size, asub.size)) {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { hs.getOrNull(i)?.let { SubRow(it) { onPlayer(it, true) } } }
                    Box(Modifier.weight(1f)) { asub.getOrNull(i)?.let { SubRow(it) { onPlayer(it, false) } } }
                }
            }
            val coaches = listOfNotNull(h?.coach, a?.coach)
            if (coaches.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text("Trener: ${h?.coach ?: "-"}", Modifier.weight(1f), fontSize = 12.sp, color = LocalPalette.current.dim)
                    Text("Trener: ${a?.coach ?: "-"}", Modifier.weight(1f), fontSize = 12.sp, color = LocalPalette.current.dim)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    if (h?.missing?.isNotEmpty() == true || a?.missing?.isNotEmpty() == true) item(key = "lu-miss") {
        KCard {
            CardTitle("Ne igraju")
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                Column(Modifier.weight(1f)) { h?.missing?.forEach { (n, r) -> MissingLine(n, r) } }
                Column(Modifier.weight(1f)) { a?.missing?.forEach { (n, r) -> MissingLine(n, r) } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MissingLine(n: String, r: String) = Column(Modifier.padding(vertical = 3.dp)) {
    Text(n, fontSize = 13.sp, maxLines = 1)
    Text(r, fontSize = 11.sp, color = K.Loss, maxLines = 1)
}

@Composable
private fun SubRow(p: LineupPlayer, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.number ?: "", Modifier.width(24.dp), fontSize = 12.sp, color = LocalPalette.current.dim)
        Text(p.shortName, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (p.events.contains("subIn")) Text("↑ ", color = K.Win, fontSize = 12.sp)
        if (p.rating != null) RatingBadge(p.rating)
    }

// ---------- box score ----------

private fun LazyListScope.boxTab(e: Event, b: BoxScore) {
    listOf(e.home to b.home, e.away to b.away).forEachIndexed { idx, (team, rows) ->
        if (rows.isEmpty()) return@forEachIndexed
        item(key = "box-$idx") {
            KCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(team, 20.dp); Spacer(Modifier.width(8.dp)); Text(team.name, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Column {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Text("Igrač", Modifier.width(140.dp), fontSize = 11.sp, color = LocalPalette.current.dim)
                            b.columns.forEach { Text(it, Modifier.width(if (it.length > 3 || it.contains("P")) 46.dp else 38.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = LocalPalette.current.dim) }
                        }
                        rows.forEach { r ->
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text((r.number?.let { "$it " } ?: "") + r.name, Modifier.width(140.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (r.starter) FontWeight.SemiBold else FontWeight.Normal)
                                r.values.forEachIndexed { i, v ->
                                    val col = b.columns.getOrNull(i) ?: ""
                                    Text(v, Modifier.width(if (col.length > 3 || col.contains("P")) 46.dp else 38.dp), textAlign = TextAlign.Center, fontSize = 13.sp,
                                        fontWeight = if (col == "PTS") FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------- heatmaps ----------

@Composable
private fun HeatBlock(d: MatchDetail, live: Boolean, homeColor: Color, awayColor: Color, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    val app = LocalApp.current
    val e = d.event
    var home by rememberSaveable(e.key) { mutableStateOf(true) }
    val side = if (home) d.lineups?.home else d.lineups?.away
    val played = side?.players?.filter { !it.substitute || it.events.contains("subIn") }.orEmpty()
    val heat = rememberLoad(e.key, "heat", refreshMs = { if (live) 120_000L else 0L }) { app.repo.heatmaps(e.key, live) }
    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Pill(e.home.shortName, home, { home = true }, leading = { TeamLogo(e.home, 16.dp) })
        Pill(e.away.shortName, !home, { home = false }, leading = { TeamLogo(e.away, 16.dp) })
    }
    val map = heat.data
    when {
        map == null && heat.value.loading -> Loading()
        map.isNullOrEmpty() -> Empty("Heatmape nisu dostupne za ovu utakmicu.\nFotMob ih objavljuje za veće lige (Liga šampiona, top 5 lige…).")
        else -> {
            val pts = played.flatMap { map[it.id].orEmpty() }
            KCard {
                CardTitle("Heatmapa tima")
                Box(Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp)) { HeatmapPitch(pts, attackRightLabel = "smer napada") }
            }
            KCard {
                CardTitle("Heatmapa igrača")
                played.filter { map.containsKey(it.id) }.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPlayer(p, home) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.number ?: "", Modifier.width(26.dp), fontSize = 12.sp, color = LocalPalette.current.dim)
                        Text(p.name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                        RatingBadge(p.rating)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val keep = homeColor to awayColor
}

// ---------- shots ----------

@Composable
private fun ShotsBlock(d: MatchDetail, homeColor: Color, awayColor: Color) {
    val e = d.event
    var home by rememberSaveable(e.key) { mutableStateOf(true) }
    var sel by remember { mutableStateOf<Shot?>(null) }
    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Pill(e.home.shortName, home, { home = true; sel = null }, leading = { TeamLogo(e.home, 16.dp) })
        Pill(e.away.shortName, !home, { home = false; sel = null }, leading = { TeamLogo(e.away, 16.dp) })
    }
    val mine = d.shots.filter { it.isHome == home }
    KCard {
        CardTitle("Mapa šuteva") {
            val xg = mine.sumOf { it.xg ?: 0.0 }
            Text("${mine.size} šuteva · xG ${String.format(java.util.Locale.US, "%.2f", xg)}", fontSize = 12.sp, color = LocalPalette.current.dim, modifier = Modifier.padding(end = 10.dp))
        }
        Box(Modifier.padding(horizontal = 10.dp)) { ShotMap(mine, homeColor, awayColor, sel) { sel = it } }
        val s = sel ?: mine.firstOrNull { it.type == "goal" }
        if (s != null) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.player}  ${s.minute?.let { "$it'" } ?: ""}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(listOfNotNull(shotTypeSr(s.type), s.bodyPart?.let(::bodySr), s.situation?.let(::situationSr)).joinToString(" · "),
                        fontSize = 12.sp, color = LocalPalette.current.dim)
                }
                Column(horizontalAlignment = Alignment.End) {
                    s.xg?.let { Text("xG ${String.format(java.util.Locale.US, "%.2f", it)}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    s.xgot?.takeIf { it > 0 }?.let { Text("xGOT ${String.format(java.util.Locale.US, "%.2f", it)}", fontSize = 12.sp, color = LocalPalette.current.dim) }
                }
            }
        } else Spacer(Modifier.height(10.dp))
    }
    KCard {
        CardTitle("Svi šutevi")
        mine.sortedBy { it.minute ?: 0 }.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable { sel = s }.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.minute?.let { "$it'" } ?: "", Modifier.width(40.dp), fontSize = 12.sp, color = LocalPalette.current.dim)
                Text(s.player, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, fontWeight = if (s.type == "goal") FontWeight.Bold else FontWeight.Normal)
                Text(shotTypeSr(s.type), fontSize = 12.sp, color = if (s.type == "goal") K.Win else LocalPalette.current.dim)
                Spacer(Modifier.width(10.dp))
                Text(s.xg?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

private fun shotTypeSr(t: String) = when (t) { "goal" -> "Gol"; "save" -> "Odbranjen"; "miss" -> "Promašaj"; "block" -> "Blokiran"; "post" -> "Stativa"; else -> t }
private fun bodySr(b: String) = when (b) { "RightFoot" -> "desna noga"; "LeftFoot" -> "leva noga"; "Header" -> "glava"; else -> b }
private fun situationSr(s: String) = when (s) {
    "RegularPlay" -> "iz igre"; "FastBreak" -> "kontra"; "FromCorner" -> "korner"; "SetPiece" -> "prekid"
    "FreeKick" -> "slobodan udarac"; "Penalty" -> "penal"; "ThrowInSetPiece" -> "aut"; "IndividualPlay" -> "solo akcija"; else -> s
}

// ---------- player sheet ----------

@Composable
private fun PlayerSheet(d: MatchDetail, p: LineupPlayer, isHome: Boolean, live: Boolean, onProfile: () -> Unit) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val e = d.event
    val isFm = e.key.startsWith("${Src.FM}:")
    val heat = rememberLoad(e.key, "heat") { if (isFm) app.repo.heatmaps(e.key, live) else emptyMap() }
    val stats = d.playerStats[p.id].orEmpty()
    LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(if (isFm) FotMob.playerPhoto(p.id) else null, 52.dp, p.shortName, circle = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamLogo(if (isHome) e.home else e.away, 14.dp); Spacer(Modifier.width(5.dp))
                        Text(listOfNotNull(p.number?.let { "#$it" }, p.country).joinToString(" · "), fontSize = 12.sp, color = pal.dim)
                    }
                }
                RatingBadge(p.rating)
            }
        }
        val pts = heat.data?.get(p.id)
        if (isFm) item {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                when {
                    heat.data == null -> Loading()
                    pts.isNullOrEmpty() -> Text("Heatmapa nije dostupna", color = pal.dim, fontSize = 13.sp)
                    else -> HeatmapPitch(pts, attackRightLabel = "smer napada")
                }
            }
        }
        items(stats) { (label, v) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(statSr(label), Modifier.weight(1f), fontSize = 14.sp, color = pal.dim)
                Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (isFm) item { TextButton(onClick = onProfile, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Profil igrača →") } }
    }
}

// ---------- H2H ----------

@Composable
private fun H2HBlock(d: MatchDetail) {
    val nav = LocalNav.current
    val h = d.h2h ?: return
    val e = d.event
    KCard {
        CardTitle("Međusobni duel")
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            H2HCount(h.homeWins, e.home.shortName, K.Home, Modifier.weight(1f))
            H2HCount(h.draws, "Nerešeno", K.Draw, Modifier.weight(1f))
            H2HCount(h.awayWins, e.away.shortName, K.Away, Modifier.weight(1f))
        }
        h.events.forEachIndexed { i, ev ->
            Column {
                Row(Modifier.padding(start = 14.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    LeagueLogo(ev.tournament, 12.dp); Spacer(Modifier.width(6.dp))
                    Text(ev.tournament?.name ?: "", fontSize = 11.sp, color = LocalPalette.current.dim, maxLines = 1)
                }
                EventRow(ev, { nav.go(Route.Match(ev.key)) }, showDate = true)
            }
            if (i < h.events.lastIndex) Divider(64.dp)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun H2HCount(n: Int, label: String, c: Color, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c)
        Text(label, fontSize = 12.sp, color = LocalPalette.current.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
