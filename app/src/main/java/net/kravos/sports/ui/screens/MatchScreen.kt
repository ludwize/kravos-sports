package net.kravos.sports.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import net.kravos.sports.data.Event
import net.kravos.sports.data.Incident
import net.kravos.sports.data.LineupPlayer
import net.kravos.sports.data.Lineups
import net.kravos.sports.data.Shot
import net.kravos.sports.data.Sport
import net.kravos.sports.data.StatPeriod
import net.kravos.sports.data.TOP_STAT_SR
import net.kravos.sports.data.periodSr
import net.kravos.sports.data.statGroupSr
import net.kravos.sports.data.statusSr
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.LocalApp
import net.kravos.sports.ui.LocalNav
import net.kravos.sports.ui.Route
import net.kravos.sports.ui.components.AveragePositionsPitch
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
import net.kravos.sports.data.Sofa

private enum class MTab(val label: String) { OVERVIEW("Pregled"), STATS("Statistika"), LINEUPS("Postave"), PLAYERS("Igrači"), HEAT("Heatmapa"), TABLE("Tabela"), H2H("Međusobni") }

private fun parseColor(hex: String?, fallback: Color): Color = runCatching {
    val h = hex!!.removePrefix("#")
    Color(android.graphics.Color.parseColor("#$h"))
}.getOrDefault(fallback)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(id: Int, pad: PaddingValues) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val sofa = app.sofa

    val ev = rememberLoad(id, refreshMs = { e -> if (e?.status?.live == true) 10_000L else if (e?.status?.notStarted == true) 60_000L else 0L }) {
        sofa.event(id, live = true) ?: error("Utakmica nije pronađena")
    }
    val e = ev.data
    val live = e?.status?.live == true
    val sport = Sport.of(e?.sport)

    val lineups = rememberLoad(id, e != null, refreshMs = { if (live) 60_000L else 0L }) {
        if (e == null) null else runCatching { sofa.lineups(id, live) }.getOrNull()
    }
    val hasLineups = lineups.data?.let { (it.home?.players?.isNotEmpty() == true) } == true

    val tabs = buildList {
        add(MTab.OVERVIEW)
        if (e != null && !e.status.notStarted) add(MTab.STATS)
        if (hasLineups) add(if (sport.hasPitch) MTab.LINEUPS else MTab.PLAYERS)
        if (hasLineups && sport.hasPitch && e?.status?.notStarted == false) add(MTab.HEAT)
        if (e?.tournament?.utId != null && e.seasonId != null) add(MTab.TABLE)
        add(MTab.H2H)
    }
    var tabName by rememberSaveable(id) { mutableStateOf(MTab.OVERVIEW.name) }
    val tab = tabs.firstOrNull { it.name == tabName } ?: MTab.OVERVIEW

    var sheetPlayer by remember { mutableStateOf<Pair<LineupPlayer, Boolean>?>(null) }

    val homeColor = parseColor(lineups.data?.home?.color, K.Home).let { if (it.luminance() > 0.85f) K.Home else it }
    val awayColor = parseColor(lineups.data?.away?.color, K.Away).let { c ->
        if (c.luminance() > 0.85f || colorDist(c, homeColor) < 0.18f) K.Away else c
    }

    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazad") }
            if (e != null) {
                Row(
                    Modifier.weight(1f).clickable { e.tournament?.let { t -> t.utId?.let { nav.go(Route.League(it, t.name, e.seasonId)) } } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeagueLogo(e.tournament, 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        listOfNotNull(e.tournament?.name, e.roundName ?: e.round?.let { "$it. kolo" }).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        when {
            e == null && ev.value.loading -> { Loading(); return@Column }
            e == null -> { ErrorBox(ev.value.error) { ev.reload() }; return@Column }
        }
        e!!

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 24.dp)) {
            item { ScoreHeader(e, homeColor, awayColor) }
            stickyTabs(tabs, tab) { tabName = it.name }
            when (tab) {
                MTab.OVERVIEW -> overview(e, live, sport, homeColor, awayColor, lineups.data)
                MTab.STATS -> statsTab(e, live, homeColor, awayColor)
                MTab.LINEUPS -> lineupsTab(e, lineups.data, homeColor, awayColor) { p, h -> sheetPlayer = p to h }
                MTab.PLAYERS -> playersTab(e, lineups.data) { p, h -> sheetPlayer = p to h }
                MTab.HEAT -> heatTab(e, live, lineups.data, homeColor, awayColor) { p, h -> sheetPlayer = p to h }
                MTab.TABLE -> item { Column { StandingsBlock(e.tournament!!.utId!!, e.seasonId!!, setOf(e.home.id, e.away.id)) } }
                MTab.H2H -> h2hTab(e)
            }
        }
    }

    sheetPlayer?.let { (p, isHome) ->
        val st = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheetPlayer = null }, sheetState = st) {
            PlayerMatchSheet(e!!, p, isHome, sport, live) {
                sheetPlayer = null
                nav.go(Route.PlayerPage(p.id, p.name))
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
private fun colorDist(a: Color, b: Color): Float = kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)

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
private fun ScoreHeader(e: Event, homeColor: Color, awayColor: Color) {
    val nav = LocalNav.current
    val app = LocalApp.current
    val pal = LocalPalette.current
    val sport = Sport.of(e.sport)
    KCard {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            TeamBlock(e.home.name, Modifier.weight(1f), { TeamLogo(e.home, 56.dp) }) { nav.go(Route.TeamPage(e.home.id, e.home.name)) }
            Column(Modifier.widthIn(min = 120.dp).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.status.notStarted || e.status.cancelled) {
                    Text(Fmt.time(e.start), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(Fmt.dayLabel(Fmt.localDate(e.start)), color = pal.dim, fontSize = 13.sp)
                    if (e.status.cancelled) Text(statusSr(e.status.description), color = K.Loss, fontSize = 13.sp)
                } else {
                    Text(
                        "${e.homeScore.display ?: e.homeScore.current ?: 0}  –  ${e.awayScore.display ?: e.awayScore.current ?: 0}",
                        fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        color = if (e.status.live) K.Live else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (e.status.live) { LiveDot(); Spacer(Modifier.width(5.dp)) }
                        Text(
                            if (e.status.live) Fmt.liveClock(e) else statusSr(e.status.description),
                            color = if (e.status.live) K.Live else pal.dim, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                    if (e.homeScore.penalties != null) Text("Penali ${e.homeScore.penalties}:${e.awayScore.penalties}", fontSize = 12.sp, color = pal.dim)
                    if (e.aggregateHome != null) Text("Ukupno ${e.aggregateHome}:${e.aggregateAway}", fontSize = 12.sp, color = pal.dim)
                    if (e.status.live && (e.homeScore.point != null)) Text("${e.homeScore.point} : ${e.awayScore.point}", color = K.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            TeamBlock(e.away.name, Modifier.weight(1f), { TeamLogo(e.away, 56.dp) }) { nav.go(Route.TeamPage(e.away.id, e.away.name)) }
        }
        // goal scorers (football-like) — compact under the score
        val inc = rememberLoad(e.id, e.status.live, refreshMs = { if (e.status.live) 20_000L else 0L }) {
            if (e.status.notStarted) emptyList() else runCatching { app.sofa.incidents(e.id, e.status.live) }.getOrDefault(emptyList())
        }
        val goals = inc.data.orEmpty().filter { it.type == "goal" }.sortedBy { it.time ?: 0 }
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
        if (sport != Sport.FOOTBALL && sport != Sport.FUTSAL && (e.homeScore.periods.isNotEmpty())) PeriodTable(e)
        Spacer(Modifier.height(12.dp))
        if (e.note != null) Text(e.note, fontSize = 12.sp, color = pal.dim, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        @Suppress("UNUSED_VARIABLE") val unused = homeColor to awayColor
    }
}

private fun scorerText(g: Incident): String {
    val t = g.time?.let { if (g.addedTime != null) "$it+${g.addedTime}'" else "$it'" } ?: ""
    val suf = when (g.cls) { "ownGoal" -> " (ag)"; "penalty" -> " (p)"; else -> "" }
    return "${g.player ?: ""}$suf $t".trim()
}

@Composable
private fun TeamBlock(name: String, modifier: Modifier, logo: @Composable () -> Unit, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                Text(label, Modifier.width(70.dp), fontSize = 12.sp, color = if (bold) MaterialTheme.colorScheme.onSurface else pal.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                cells.forEach { Text(it, Modifier.width(34.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = if (bold) MaterialTheme.colorScheme.onSurface else pal.dim) }
                Text(total, Modifier.width(38.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        val head = (1..n).map { if (sport.setBased) "S$it" else "$it" } + listOfNotNull(e.homeScore.overtime?.let { "OT" })
        line("", head, if (sport.setBased) "Set" else "Uk.", false)
        fun cells(s: net.kravos.sports.data.Score) = (0 until n).map { i ->
            val v = s.periods.getOrNull(i)?.toString() ?: "-"
            val tb = s.periodTiebreaks.getOrNull(i)
            if (tb != null) "$v⁽$tb⁾" else v
        } + listOfNotNull(s.overtime?.toString())
        line(e.home.code ?: e.home.shortName, cells(e.homeScore), (e.homeScore.display ?: e.homeScore.current)?.toString() ?: "", true)
        line(e.away.code ?: e.away.shortName, cells(e.awayScore), (e.awayScore.display ?: e.awayScore.current)?.toString() ?: "", true)
    }
}

// ---------- overview ----------

private fun LazyListScope.overview(e: Event, live: Boolean, sport: Sport, homeColor: Color, awayColor: Color, lu: Lineups?) {
    item(key = "ov-inc") { IncidentsCard(e, live, homeColor, awayColor) }
    if (sport == Sport.FOOTBALL && !e.status.notStarted) item(key = "ov-mom") { MomentumCard(e, live, homeColor, awayColor) }
    if (!e.status.notStarted) item(key = "ov-keystats") { KeyStatsCard(e, live, homeColor, awayColor) }
    if (lu != null && sport.hasPitch) item(key = "ov-best") { BestPlayersCard(lu) }
    item(key = "ov-form") { FormCard(e) }
    item(key = "ov-info") { InfoCard(e) }
}

@Composable
private fun IncidentsCard(e: Event, live: Boolean, homeColor: Color, awayColor: Color) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    if (e.status.notStarted) return
    val inc = rememberLoad(e.id, "inc", refreshMs = { if (live) 15_000L else 0L }) { app.sofa.incidents(e.id, live) }
    val list = inc.data ?: return
    val shown = list.filter { it.type in setOf("goal", "card", "substitution", "period", "varDecision", "injuryTime", "inGamePenalty") }
    if (shown.isEmpty() || shown.size > 90) return
    KCard {
        CardTitle("Tok utakmice")
        shown.forEach { i ->
            when (i.type) {
                "period" -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(statusSr(i.text ?: ""), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = pal.dim)
                    if (i.homeScore != null) Text("${i.homeScore} – ${i.awayScore}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                "injuryTime" -> Text("+${i.length ?: 0} min nadoknade", Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = pal.dim)
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
            "goal" -> Text(if (i.cls == "ownGoal") "⚽︎" else "⚽", fontSize = 15.sp)
            "card" -> Box(Modifier.size(10.dp, 14.dp).clip(RoundedCornerShape(2.dp)).background(
                when (i.cls) { "red" -> K.Loss; "yellowRed" -> Color(0xFFFF7A00); else -> K.Yellow }))
            "substitution" -> Text("⇅", fontSize = 15.sp, color = K.Accent)
            "varDecision" -> Text("VAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = pal.dim)
            "inGamePenalty" -> Text("✕", fontSize = 13.sp, color = K.Loss)
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
                "varDecision" -> Text("VAR: ${i.player ?: ""} ${i.cls ?: ""}", fontSize = 12.sp, maxLines = 2)
                "inGamePenalty" -> Text("Promašen penal – ${i.player ?: ""}", fontSize = 12.sp, maxLines = 1)
                else -> {
                    Text(i.player ?: "", fontSize = 13.sp, maxLines = 1)
                    i.reason?.let { Text(it, fontSize = 11.sp, color = pal.dim, maxLines = 1) }
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
private fun MomentumCard(e: Event, live: Boolean, homeColor: Color, awayColor: Color) {
    val app = LocalApp.current
    val g = rememberLoad(e.id, "graph", refreshMs = { if (live) 30_000L else 0L }) { runCatching { app.sofa.graph(e.id, live) }.getOrDefault(emptyList()) }
    val inc = rememberLoad(e.id, "incg") { runCatching { app.sofa.incidents(e.id, live) }.getOrDefault(emptyList()) }
    val pts = g.data ?: return
    if (pts.isEmpty()) return
    val goals = inc.data.orEmpty().filter { it.type == "goal" && it.time != null }.map { (it.time!! + (it.addedTime ?: 0) * 0.0) to (it.isHome == true) }
    KCard {
        CardTitle("Momentum napada")
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(e.home, 14.dp); Spacer(Modifier.width(6.dp)); Text(e.home.shortName, fontSize = 11.sp, color = LocalPalette.current.dim)
        }
        MomentumGraph(pts, homeColor, awayColor, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), goals)
        Row(Modifier.padding(horizontal = 14.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(e.away, 14.dp); Spacer(Modifier.width(6.dp)); Text(e.away.shortName, fontSize = 11.sp, color = LocalPalette.current.dim)
        }
    }
}

@Composable
private fun KeyStatsCard(e: Event, live: Boolean, homeColor: Color, awayColor: Color) {
    val app = LocalApp.current
    val st = rememberLoad(e.id, "stats-key", refreshMs = { if (live) 30_000L else 0L }) { runCatching { app.sofa.statistics(e.id, live) }.getOrDefault(emptyList()) }
    val all = st.data?.firstOrNull { it.period == "ALL" } ?: st.data?.firstOrNull() ?: return
    val items = all.groups.firstOrNull()?.items?.take(8).orEmpty()
    if (items.isEmpty()) return
    KCard {
        CardTitle("Ključna statistika")
        TeamsLegend(e)
        items.forEach { StatBar(it, homeColor, awayColor) }
        Spacer(Modifier.height(8.dp))
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
private fun BestPlayersCard(lu: Lineups) {
    val all = (lu.home?.players.orEmpty().map { it to true } + lu.away?.players.orEmpty().map { it to false })
        .filter { it.first.rating != null }.sortedByDescending { it.first.rating }.take(3)
    if (all.isEmpty()) return
    KCard {
        CardTitle("Najbolji igrači")
        all.forEachIndexed { i, (p, _) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(Sofa.playerPhoto(p.id), 34.dp, p.shortName, circle = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                    val g = p.stats["goals"]?.toInt() ?: 0; val a = p.stats["goalAssist"]?.toInt() ?: 0
                    Text(listOfNotNull(if (g > 0) "$g gol" else null, if (a > 0) "$a asist." else null).joinToString(" · ").ifEmpty { p.position ?: "" },
                        fontSize = 12.sp, color = LocalPalette.current.dim)
                }
                RatingBadge(p.rating)
            }
            if (i < all.lastIndex) Divider(58.dp)
        }
    }
}

@Composable
private fun FormCard(e: Event) {
    val app = LocalApp.current
    val f = rememberLoad(e.id, "form") { runCatching { app.sofa.pregame(e.id) }.getOrNull() }
    val pf = f.data ?: return
    if (pf.home == null && pf.away == null) return
    KCard {
        CardTitle("Forma pred meč")
        listOf(e.home to pf.home, e.away to pf.away).forEach { (t, tf) ->
            if (tf == null) return@forEach
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(t, 22.dp); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.shortName, fontSize = 14.sp, maxLines = 1)
                    val sub = listOfNotNull(tf.position?.let { "$it. mesto" }, tf.value?.let { "${pf.label ?: "Bod."}: $it" }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = LocalPalette.current.dim)
                }
                tf.form.takeLast(5).forEach { FormBadge(it) }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun InfoCard(e: Event) {
    val pal = LocalPalette.current
    KCard {
        CardTitle("Informacije")
        InfoLine("Početak", "${Fmt.fullDate(e.start)} u ${Fmt.time(e.start)}")
        e.tournament?.let { InfoLine("Takmičenje", listOfNotNull(it.categoryName, it.name).joinToString(" – ")) }
        e.roundName?.let { InfoLine("Faza", it) } ?: e.round?.let { InfoLine("Kolo", "$it.") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun InfoLine(k: String, v: String) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
        Text(k, Modifier.width(100.dp), fontSize = 13.sp, color = LocalPalette.current.dim)
        Text(v, fontSize = 13.sp)
    }

// ---------- statistics tab ----------

private fun LazyListScope.statsTab(e: Event, live: Boolean, homeColor: Color, awayColor: Color) {
    item(key = "stats") { Column { StatsBlock(e, live, homeColor, awayColor) } }
}

@Composable
private fun StatsBlock(e: Event, live: Boolean, homeColor: Color, awayColor: Color) {
    val app = LocalApp.current
    val st = rememberLoad(e.id, "stats", refreshMs = { if (live) 30_000L else 0L }) { app.sofa.statistics(e.id, live) }
    var period by rememberSaveable(e.id) { mutableStateOf("ALL") }
    when {
        st.data == null && st.value.loading -> Loading()
        st.data.isNullOrEmpty() -> Empty("Statistika još nije dostupna")
        else -> {
            val periods: List<StatPeriod> = st.data!!
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
                periods.forEach { p -> Pill(periodSr(p.period), p.period == period, { period = p.period }) }
            }
            val cur = periods.firstOrNull { it.period == period } ?: periods.first()
            cur.groups.forEach { g ->
                KCard {
                    CardTitle(statGroupSr(g.name))
                    g.items.forEach { StatBar(it, homeColor, awayColor) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ---------- lineups ----------

private fun LazyListScope.lineupsTab(e: Event, lu: Lineups?, homeColor: Color, awayColor: Color, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    val h = lu?.home; val a = lu?.away
    if (h == null || a == null) { item { Empty("Postave još nisu objavljene") }; return }
    item(key = "lu-head") {
        KCard {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(e.home, 20.dp); Spacer(Modifier.width(8.dp))
                Text(h.formation ?: "", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (lu?.confirmed == false) Text("Očekivane postave", fontSize = 11.sp, color = LocalPalette.current.dim)
                Spacer(Modifier.weight(1f))
                Text(a.formation ?: "", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); TeamLogo(e.away, 20.dp)
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
            CardTitle("Klupa")
            val hs = h.players.filter { it.substitute }; val asub = a.players.filter { it.substitute }
            for (i in 0 until maxOf(hs.size, asub.size)) {
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { hs.getOrNull(i)?.let { SubRow(it) { onPlayer(it, true) } } }
                    Box(Modifier.weight(1f)) { asub.getOrNull(i)?.let { SubRow(it) { onPlayer(it, false) } } }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
    if (h.missing.isNotEmpty() || a.missing.isNotEmpty()) item(key = "lu-miss") {
        KCard {
            CardTitle("Ne igraju")
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                Column(Modifier.weight(1f)) { h.missing.forEach { (n, r) -> MissingLine(n, r) } }
                Column(Modifier.weight(1f)) { a.missing.forEach { (n, r) -> MissingLine(n, r) } }
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
        if (p.rating != null) RatingBadge(p.rating)
    }

// ---------- players (box score, non-football) ----------

private val BOX_COLUMNS = listOf(
    "secondsPlayed" to "MIN", "points" to "PTS", "rebounds" to "REB", "assists" to "AST", "steals" to "STL",
    "blocks" to "BLK", "turnovers" to "TO", "fieldGoalsMade" to "FG", "threePointsMade" to "3P", "freeThrowsMade" to "FT",
    "plusMinus" to "+/-", "pir" to "PIR", "goals" to "G", "assists" to "A", "shots" to "SH", "saves" to "SV",
)

private fun LazyListScope.playersTab(e: Event, lu: Lineups?, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    if (lu == null) { item { Empty("Nema podataka o igračima") }; return }
    listOf(e.home to lu.home, e.away to lu.away).forEachIndexed { idx, (team, side) ->
        if (side == null) return@forEachIndexed
        item(key = "box-$idx") {
            val keys = side.players.flatMap { it.stats.keys }.toSet()
            val cols = BOX_COLUMNS.filter { it.first in keys }.distinctBy { it.second }.take(9)
            KCard {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(team, 20.dp); Spacer(Modifier.width(8.dp)); Text(team.name, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Column {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Text("Igrač", Modifier.width(130.dp), fontSize = 11.sp, color = LocalPalette.current.dim)
                            cols.forEach { Text(it.second, Modifier.width(40.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = LocalPalette.current.dim) }
                        }
                        side.players.sortedWith(compareBy<LineupPlayer> { it.substitute }.thenByDescending { it.stats["points"] ?: it.stats["goals"] ?: 0.0 }).forEach { p ->
                            Row(Modifier.clickable { onPlayer(p, idx == 0) }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text((p.number?.let { "$it " } ?: "") + p.shortName, Modifier.width(130.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (p.substitute) FontWeight.Normal else FontWeight.SemiBold)
                                cols.forEach { (k, _) ->
                                    val v = p.stats[k]
                                    val s = when {
                                        v == null -> "-"
                                        k == "secondsPlayed" -> "${(v / 60).toInt()}"
                                        else -> Fmt.num(v)
                                    }
                                    Text(s, Modifier.width(40.dp), textAlign = TextAlign.Center, fontSize = 13.sp)
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

// ---------- heatmap tab ----------

private fun LazyListScope.heatTab(e: Event, live: Boolean, lu: Lineups?, homeColor: Color, awayColor: Color, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    item(key = "heat") { Column { HeatBlock(e, live, lu, homeColor, awayColor, onPlayer) } }
}

@Composable
private fun HeatBlock(e: Event, live: Boolean, lu: Lineups?, homeColor: Color, awayColor: Color, onPlayer: (LineupPlayer, Boolean) -> Unit) {
    val app = LocalApp.current
    var home by rememberSaveable(e.id) { mutableStateOf(true) }
    val side = if (home) lu?.home else lu?.away
    val starters = side?.players?.filter { !it.substitute || (it.stats["minutesPlayed"] ?: 0.0) > 0 }.orEmpty()
    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Pill(e.home.shortName, home, { home = true }, leading = { TeamLogo(e.home, 16.dp) })
        Pill(e.away.shortName, !home, { home = false }, leading = { TeamLogo(e.away, 16.dp) })
    }
    // team heatmap = merge of player heatmaps
    val heat = rememberLoad(e.id, home, starters.size) { app.sofa.teamHeatmap(e.id, starters, live) }
    KCard {
        CardTitle("Heatmapa tima")
        Box(Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
            if (heat.data == null) Loading() else HeatmapPitch(heat.data!!, attackRightLabel = "smer napada")
        }
    }
    // average positions
    val avg = rememberLoad(e.id, "avg") { runCatching { app.sofa.averagePositions(e.id) }.getOrDefault(emptyList()) }
    val pos = avg.data.orEmpty().filter { it.isHome == home }
    if (pos.isNotEmpty()) KCard {
        CardTitle("Prosečne pozicije")
        Box(Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp)) {
            AveragePositionsPitch(pos, if (home) homeColor else awayColor) { pid ->
                side?.players?.firstOrNull { it.id == pid }?.let { onPlayer(it, home) }
            }
        }
    }
    // shotmap
    val shots = rememberLoad(e.id, "shots", refreshMs = { if (live) 60_000L else 0L }) { runCatching { app.sofa.shotmap(e.id, live) }.getOrDefault(emptyList()) }
    val all = shots.data.orEmpty()
    if (all.isNotEmpty()) {
        var sel by remember { mutableStateOf<Shot?>(null) }
        val mine = all.filter { it.isHome == home }
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
                        s.xgot?.let { Text("xGOT ${String.format(java.util.Locale.US, "%.2f", it)}", fontSize = 12.sp, color = LocalPalette.current.dim) }
                    }
                }
            } else Spacer(Modifier.height(10.dp))
        }
    }
    // per-player heatmaps list
    KCard {
        CardTitle("Heatmapa igrača")
        starters.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { onPlayer(p, home) }.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.number ?: "", Modifier.width(26.dp), fontSize = 12.sp, color = LocalPalette.current.dim)
                Text(p.name, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                RatingBadge(p.rating)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

private fun shotTypeSr(t: String) = when (t) { "goal" -> "Gol"; "save" -> "Odbranjen"; "miss" -> "Promašaj"; "block" -> "Blokiran"; "post" -> "Stativa"; else -> t }
private fun bodySr(b: String) = when (b) { "right-foot" -> "desna noga"; "left-foot" -> "leva noga"; "head" -> "glava"; else -> b }
private fun situationSr(s: String) = when (s) {
    "regular", "assisted" -> "iz igre"; "fast-break" -> "kontra"; "corner" -> "korner"; "set-piece" -> "prekid"
    "free-kick" -> "slobodan udarac"; "penalty" -> "penal"; "throw-in-set-piece" -> "aut"; else -> s
}

// ---------- player sheet ----------

private val SHEET_STATS = listOf(
    "minutesPlayed" to "Minuti", "goals" to "Golovi", "goalAssist" to "Asistencije", "expectedGoals" to "xG", "expectedAssists" to "xA",
    "totalShots" to "Šutevi", "onTargetScoringAttempt" to "Šutevi u okvir", "keyPass" to "Ključni pasovi",
    "accuratePass" to "Precizni pasovi", "totalPass" to "Ukupno pasova", "totalLongBalls" to "Duge lopte",
    "wonContest" to "Uspešni driblinzi", "duelWon" to "Dobijeni dueli", "duelLost" to "Izgubljeni dueli",
    "aerialWon" to "Dueli u vazduhu (dob.)", "totalTackle" to "Startovi", "interceptionWon" to "Presečene lopte",
    "totalClearance" to "Izbijene lopte", "possessionLostCtrl" to "Izgubljeni posedi", "fouls" to "Faulovi", "wasFouled" to "Fauliran",
    "saves" to "Odbrane", "goalsPrevented" to "Sprečeni golovi", "touches" to "Dodiri",
    "points" to "Poeni", "rebounds" to "Skokovi", "assists" to "Asistencije", "steals" to "Ukradene", "blocks" to "Blokade",
    "turnovers" to "Izgubljene lopte", "fieldGoalsMade" to "Šut iz igre (pog.)", "fieldGoalAttempts" to "Šut iz igre (pok.)",
    "threePointsMade" to "Trojke (pog.)", "threePointAttempts" to "Trojke (pok.)", "freeThrowsMade" to "Slobodna (pog.)",
    "freeThrowAttempts" to "Slobodna (pok.)", "plusMinus" to "+/-", "pir" to "PIR",
)

@Composable
private fun PlayerMatchSheet(e: Event, p: LineupPlayer, isHome: Boolean, sport: Sport, live: Boolean, onProfile: () -> Unit) {
    val app = LocalApp.current
    val pal = LocalPalette.current
    val heat = rememberLoad(e.id, p.id, "ph") { if (sport.hasPitch) runCatching { app.sofa.playerHeatmap(e.id, p.id, live) }.getOrDefault(emptyList()) else emptyList() }
    val stats = rememberLoad(e.id, p.id, "ps") { app.sofa.playerEventStats(e.id, p.id).ifEmpty { p.stats } }
    LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(Sofa.playerPhoto(p.id), 52.dp, p.shortName, circle = true)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamLogo(if (isHome) e.home else e.away, 14.dp); Spacer(Modifier.width(5.dp))
                        Text(listOfNotNull(p.number?.let { "#$it" }, p.position).joinToString(" · "), fontSize = 12.sp, color = pal.dim)
                    }
                }
                RatingBadge(stats.data?.get("rating") ?: p.rating)
            }
        }
        if (sport.hasPitch) item {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (heat.data == null) Loading() else HeatmapPitch(heat.data!!, attackRightLabel = "smer napada")
            }
        }
        val st = stats.data.orEmpty()
        val rows = SHEET_STATS.filter { st.containsKey(it.first) }.distinctBy { it.second }
        items(rows) { (k, label) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text(label, Modifier.weight(1f), fontSize = 14.sp, color = pal.dim)
                Text(Fmt.num(st[k] ?: 0.0), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            TextButton(onClick = onProfile, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Profil igrača →") }
        }
    }
}

// ---------- H2H ----------

private fun LazyListScope.h2hTab(e: Event) {
    item(key = "h2h") { Column { H2HBlock(e) } }
}

@Composable
private fun H2HBlock(e: Event) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val h = rememberLoad(e.id, "h2h") { app.sofa.h2h(e) }
    val lastHome = rememberLoad(e.home.id, "lastH") { app.sofa.teamEvents(e.home.id, next = false) }
    val lastAway = rememberLoad(e.away.id, "lastA") { app.sofa.teamEvents(e.away.id, next = false) }
    val d = h.data
    if (h.value.loading && d == null) { Loading(); return }
    if (d != null) {
        KCard {
            CardTitle("Međusobni duel")
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                H2HCount(d.homeWins, "Pobede", K.Home, Modifier.weight(1f))
                H2HCount(d.draws, "Nerešeno", K.Draw, Modifier.weight(1f))
                H2HCount(d.awayWins, "Pobede", K.Away, Modifier.weight(1f))
            }
            d.events.forEach { ev -> EventRow(ev, { nav.go(Route.Match(ev.id)) }, showDate = true) }
            Spacer(Modifier.height(6.dp))
        }
    }
    listOf(e.home to lastHome.data, e.away to lastAway.data).forEach { (t, list) ->
        val l = list.orEmpty().filter { it.status.finished }.sortedByDescending { it.start }.take(5)
        if (l.isEmpty()) return@forEach
        KCard {
            CardTitle("Poslednje utakmice – ${t.shortName}")
            l.forEach { ev -> EventRow(ev, { nav.go(Route.Match(ev.id)) }, showDate = true, highlightTeam = t.id) }
        }
    }
}

@Composable
private fun H2HCount(n: Int, label: String, c: Color, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$n", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c)
        Text(label, fontSize = 12.sp, color = LocalPalette.current.dim)
    }

@Suppress("unused") private val keep = TOP_STAT_SR
@Suppress("unused") private val keepArr = Arrangement.Center
