package net.kravos.sports.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import net.kravos.sports.data.Event
import net.kravos.sports.data.Sofa
import net.kravos.sports.data.Sport
import net.kravos.sports.data.Team
import net.kravos.sports.data.Tournament
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.theme.K
import net.kravos.sports.ui.theme.LocalPalette

// ---------- images ----------

@Composable
fun NetImage(url: String?, size: Dp, fallback: String, modifier: Modifier = Modifier, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(4.dp)
    val fb: @Composable () -> Unit = {
        Box(
            modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fallback.take(2).uppercase(),
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    if (url == null) { fb(); return }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).clip(shape),
        loading = { Box(Modifier.size(size)) },
        error = { fb() },
    )
}

@Composable
fun TeamLogo(team: Team, size: Dp = 22.dp, modifier: Modifier = Modifier) =
    NetImage(Sofa.teamLogo(team.id), size, team.code ?: team.shortName, modifier)

@Composable
fun LeagueLogo(t: Tournament?, size: Dp = 20.dp) {
    when {
        t?.utId != null -> NetImage(Sofa.leagueLogo(t.utId), size, t.name)
        Sofa.flag(t?.alpha2) != null -> NetImage(Sofa.flag(t?.alpha2), size, t?.alpha2 ?: "")
        else -> Icon(Icons.Filled.Public, null, Modifier.size(size), tint = LocalPalette.current.dim)
    }
}

// ---------- containers ----------

@Composable
fun KCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(LocalPalette.current.card),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalPalette.current.dim, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Divider(start: Dp = 14.dp) = HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = LocalPalette.current.divider)

@Composable
fun Loading(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }

@Composable
fun ErrorBox(msg: String?, onRetry: () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CloudOff, null, tint = LocalPalette.current.dim, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(8.dp))
        Text("Podaci trenutno nisu dostupni", style = MaterialTheme.typography.titleSmall)
        if (!msg.isNullOrBlank()) Text(msg.take(140), style = MaterialTheme.typography.bodySmall, color = LocalPalette.current.dim, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Pokušaj ponovo") }
    }

@Composable
fun Empty(text: String) =
    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(text, color = LocalPalette.current.dim, textAlign = TextAlign.Center)
    }

@Composable
fun LiveDot(size: Dp = 7.dp) {
    val tr = rememberInfiniteTransition(label = "live")
    val a by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).alpha(a).clip(CircleShape).background(K.Live))
}

@Composable
fun StarButton(on: Boolean, onClick: () -> Unit) =
    IconButton(onClick = onClick) {
        Icon(if (on) Icons.Filled.Star else Icons.Outlined.StarOutline, "Omiljeno", tint = if (on) K.Yellow else LocalPalette.current.dim)
    }

@Composable
fun FormBadge(r: String) {
    val (c, t) = when (r.uppercase()) {
        "W" -> K.Win to "P"
        "D" -> K.Draw to "N"
        "L" -> K.Loss to "I"
        else -> K.Draw to r
    }
    Box(Modifier.padding(1.5.dp).size(20.dp).clip(RoundedCornerShape(5.dp)).background(c), contentAlignment = Alignment.Center) {
        Text(t, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- tournament header ----------

@Composable
fun TournamentHeader(t: Tournament?, fav: Boolean, onClick: (() -> Unit)?, onStar: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeagueLogo(t, 18.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t?.name ?: "Ostalo", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!t?.categoryName.isNullOrBlank()) Text(t!!.categoryName!!, fontSize = 11.sp, color = LocalPalette.current.dim, maxLines = 1)
        }
        if (onStar != null) StarButton(fav, onStar) else Spacer(Modifier.height(48.dp))
    }
}

// ---------- the score row ----------

@Composable
fun EventRow(e: Event, onClick: () -> Unit, showDate: Boolean = false, highlightTeam: Int? = null) {
    val pal = LocalPalette.current
    val sport = Sport.of(e.sport)
    val live = e.status.live
    val fin = e.status.finished
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // status column
        Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showDate && !live) Text(Fmt.date(e.start), fontSize = 11.sp, color = pal.dim)
            if (live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(6.dp); Spacer(Modifier.width(3.dp))
                    Text(Fmt.liveClock(e), color = K.Live, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            } else {
                Text(
                    Fmt.statusShort(e),
                    fontSize = 12.sp,
                    color = if (e.status.cancelled) K.Loss else pal.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // teams
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TeamLine(e.home, e.winner == 1, fin, e.homeRed, sport, e.firstToServe == 1 && live, highlightTeam == e.home.id)
            TeamLine(e.away, e.winner == 2, fin, e.awayRed, sport, e.firstToServe == 2 && live, highlightTeam == e.away.id)
        }
        // periods (set-based sports)
        if (sport.setBased && (live || fin)) {
            val n = maxOf(e.homeScore.periods.size, e.awayScore.periods.size)
            for (i in 0 until n) {
                Column(Modifier.widthIn(min = 18.dp).padding(start = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val h = e.homeScore.periods.getOrNull(i); val a = e.awayScore.periods.getOrNull(i)
                    val cur = live && i == n - 1
                    PeriodCell(h, cur, (h ?: 0) > (a ?: 0))
                    PeriodCell(a, cur, (a ?: 0) > (h ?: 0))
                }
            }
            if (live && (e.homeScore.point != null || e.awayScore.point != null)) {
                Column(Modifier.padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(e.homeScore.point ?: "", color = K.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(e.awayScore.point ?: "", color = K.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // main score
        if (live || fin || (e.homeScore.current != null && !e.status.notStarted)) {
            Column(Modifier.widthIn(min = 26.dp).padding(start = 8.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScoreCell(e.homeScore.display ?: e.homeScore.current, live, fin && e.winner == 2)
                ScoreCell(e.awayScore.display ?: e.awayScore.current, live, fin && e.winner == 1)
            }
        }
    }
}

@Composable
private fun TeamLine(t: Team, winner: Boolean, fin: Boolean, red: Int, sport: Sport, serving: Boolean, highlight: Boolean) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TeamLogo(t, 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            t.shortName,
            fontSize = 14.5.sp,
            fontWeight = if (winner || highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (fin && !winner) (if (pal.dark) Color(0xFFB0B4BA) else Color(0xFF6B6B70)) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (red > 0) {
            Spacer(Modifier.width(5.dp))
            repeat(red.coerceAtMost(3)) { Box(Modifier.padding(end = 2.dp).size(7.dp, 10.dp).clip(RoundedCornerShape(1.5.dp)).background(K.Loss)) }
        }
        if (serving) {
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(K.Yellow))
        }
    }
}

@Composable
private fun PeriodCell(v: Int?, current: Boolean, won: Boolean) =
    Text(
        v?.toString() ?: "",
        fontSize = 13.sp,
        fontWeight = if (won) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) K.Accent else if (won) MaterialTheme.colorScheme.onSurface else LocalPalette.current.dim,
    )

@Composable
private fun ScoreCell(v: Int?, live: Boolean, loser: Boolean) =
    Text(
        v?.toString() ?: "-",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = when {
            live -> K.Live
            loser -> LocalPalette.current.dim
            else -> MaterialTheme.colorScheme.onSurface
        },
    )

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, leading: @Composable (() -> Unit)? = null) {
    val bg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun FillBox(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }
