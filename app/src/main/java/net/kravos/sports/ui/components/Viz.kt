package net.kravos.sports.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kravos.sports.data.HeatPoint
import net.kravos.sports.data.LineupPlayer
import net.kravos.sports.data.MomentumPoint
import net.kravos.sports.data.Shot
import net.kravos.sports.data.StatItem
import net.kravos.sports.data.statSr
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.theme.K
import net.kravos.sports.ui.theme.LocalPalette
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// ---------- stat comparison bar ----------

@Composable
fun StatBar(s: StatItem, homeColor: Color = K.Home, awayColor: Color = K.Away) {
    val pal = LocalPalette.current
    val total = s.homeValue + s.awayValue
    val hf = if (total > 0) (s.homeValue / total).toFloat() else 0.5f
    val homeBetter = if (s.lowerIsBetter) s.homeValue < s.awayValue else s.homeValue > s.awayValue
    val awayBetter = if (s.lowerIsBetter) s.awayValue < s.homeValue else s.awayValue > s.homeValue
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ValueChip(s.home, homeBetter, homeColor)
            Text(statSr(s.name), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ValueChip(s.away, awayBetter, awayColor)
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.divider)) {
                Box(Modifier.fillMaxWidth(hf.coerceIn(0f, 1f)).height(5.dp).align(Alignment.CenterEnd).clip(RoundedCornerShape(3.dp))
                    .background(if (homeBetter) homeColor else homeColor.copy(alpha = 0.45f)))
            }
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(pal.divider)) {
                Box(Modifier.fillMaxWidth((1f - hf).coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (awayBetter) awayColor else awayColor.copy(alpha = 0.45f)))
            }
        }
    }
}

@Composable
private fun ValueChip(text: String, better: Boolean, c: Color) =
    Box(
        Modifier.widthIn(min = 40.dp).clip(RoundedCornerShape(7.dp)).background(if (better) c else Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (better) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }

// ---------- pitch ----------

fun DrawScope.drawPitchHorizontal(line: Color = K.PitchLine) {
    val w = size.width; val h = size.height
    val s = Stroke(width = 1.5.dp.toPx())
    drawRect(line, style = s)
    drawLine(line, Offset(w / 2, 0f), Offset(w / 2, h), s.width)
    drawCircle(line, radius = h * 0.135f, center = Offset(w / 2, h / 2), style = s)
    drawCircle(line, radius = 2.dp.toPx(), center = Offset(w / 2, h / 2))
    // penalty areas (16.5m of 105 x 40.3m of 68)
    val pw = w * 0.157f; val ph = h * 0.593f
    val gw = w * 0.052f; val gh = h * 0.269f
    drawRect(line, Offset(0f, (h - ph) / 2), Size(pw, ph), style = s)
    drawRect(line, Offset(w - pw, (h - ph) / 2), Size(pw, ph), style = s)
    drawRect(line, Offset(0f, (h - gh) / 2), Size(gw, gh), style = s)
    drawRect(line, Offset(w - gw, (h - gh) / 2), Size(gw, gh), style = s)
    drawCircle(line, radius = 2.dp.toPx(), center = Offset(w * 0.105f, h / 2))
    drawCircle(line, radius = 2.dp.toPx(), center = Offset(w * 0.895f, h / 2))
}

fun DrawScope.drawPitchVertical(line: Color = K.PitchLine) {
    val w = size.width; val h = size.height
    val s = Stroke(width = 1.5.dp.toPx())
    drawRect(line, style = s)
    drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), s.width)
    drawCircle(line, radius = w * 0.135f, center = Offset(w / 2, h / 2), style = s)
    val pw = w * 0.593f; val ph = h * 0.157f
    val gw = w * 0.269f; val gh = h * 0.052f
    drawRect(line, Offset((w - pw) / 2, 0f), Size(pw, ph), style = s)
    drawRect(line, Offset((w - pw) / 2, h - ph), Size(pw, ph), style = s)
    drawRect(line, Offset((w - gw) / 2, 0f), Size(gw, gh), style = s)
    drawRect(line, Offset((w - gw) / 2, h - gh), Size(gw, gh), style = s)
}

private fun pitchBrush() = Brush.verticalGradient(listOf(Color(0xFF1F5E36), Color(0xFF184D2C)))

// ---------- heatmap ----------

/** Renders a smooth heatmap bitmap (kernel density on a coarse grid, then colour-mapped). x,y in 0..100. */
private fun renderHeat(points: List<HeatPoint>, gw: Int = 105, gh: Int = 68): ImageBitmap {
    val grid = FloatArray(gw * gh)
    val sigma = 4.2f
    val r = (sigma * 3).toInt()
    for (p in points) {
        val cx = (p.x / 100f * gw).toInt(); val cy = (p.y / 100f * gh).toInt()
        for (dy in -r..r) {
            val yy = cy + dy; if (yy !in 0 until gh) continue
            for (dx in -r..r) {
                val xx = cx + dx; if (xx !in 0 until gw) continue
                grid[yy * gw + xx] += p.w * exp(-(dx * dx + dy * dy) / (2 * sigma * sigma))
            }
        }
    }
    val mx = grid.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val px = IntArray(gw * gh)
    for (i in grid.indices) {
        val v = (grid[i] / mx).coerceIn(0f, 1f)
        px[i] = heatColor(v)
    }
    val bmp = android.graphics.Bitmap.createBitmap(px, gw, gh, android.graphics.Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}

private fun heatColor(v: Float): Int {
    if (v < 0.06f) return 0
    // transparent green -> yellow -> orange -> red
    val stops = listOf(0.06f to 0x0034C759, 0.25f to 0x9934C759.toInt(), 0.5f to 0xCCFFD60A.toInt(), 0.75f to 0xDDFF9F0A.toInt(), 1f to 0xEEFF3B30.toInt())
    for (i in 1 until stops.size) {
        val (p1, c1) = stops[i]; val (p0, c0) = stops[i - 1]
        if (v <= p1) {
            val t = (v - p0) / (p1 - p0)
            fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
            fun mix(s: Int) = (ch(c0, s) + (ch(c1, s) - ch(c0, s)) * t).toInt()
            return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
    return stops.last().second
}

@Composable
fun HeatmapPitch(points: List<HeatPoint>, modifier: Modifier = Modifier, attackRightLabel: String? = null) {
    val img = remember(points) { if (points.isEmpty()) null else renderHeat(points) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(105f / 68f).clip(RoundedCornerShape(10.dp)).background(pitchBrush())) {
            Canvas(Modifier.matchParentSizeCompat()) {
                img?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = androidx.compose.ui.graphics.FilterQuality.High) }
                drawPitchHorizontal()
            }
            if (img == null) Text("Nema podataka", Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f))
        }
        if (attackRightLabel != null) Text("$attackRightLabel  →", fontSize = 11.sp, color = LocalPalette.current.dim, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
    }
}

private fun Modifier.matchParentSizeCompat() = this.then(Modifier.fillMaxWidth().aspectRatio(105f / 68f))

// ---------- shot map ----------

@Composable
fun ShotMap(shots: List<Shot>, homeColor: Color, awayColor: Color, selected: Shot?, onShot: (Shot) -> Unit) {
    // half pitch, goal at top; x = distance from goal line (% of length), y = across (% of width)
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(68f / 52.5f).clip(RoundedCornerShape(10.dp)).background(pitchBrush())) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxWidth().aspectRatio(68f / 52.5f)) {
            val line = K.PitchLine; val s = Stroke(1.5.dp.toPx())
            val W = size.width; val H = size.height
            drawRect(line, style = s)
            val pw = W * 0.593f; val ph = H * (16.5f / 52.5f)
            val gw = W * 0.269f; val gh = H * (5.5f / 52.5f)
            drawRect(line, Offset((W - pw) / 2, 0f), Size(pw, ph), style = s)
            drawRect(line, Offset((W - gw) / 2, 0f), Size(gw, gh), style = s)
            drawCircle(line, 2.dp.toPx(), Offset(W / 2, H * (11f / 52.5f)))
            drawCircle(line, W * 0.135f, Offset(W / 2, H), style = s)
            drawRect(Color.White, Offset((W - W * 0.108f) / 2, -3.dp.toPx()), Size(W * 0.108f, 3.dp.toPx()))
        }
        shots.filter { it.x <= 52.5f }.forEach { sh ->
            val xg = (sh.xg ?: 0.05).toFloat()
            val d = (10 + xg * 26).dp
            val c = if (sh.isHome) homeColor else awayColor
            val goal = sh.type == "goal"
            val sel = sh == selected
            Box(
                Modifier.offset(w * (sh.y / 100f) - d / 2, h * (sh.x / 52.5f) - d / 2).size(d).clip(CircleShape)
                    .background(if (goal) c else c.copy(alpha = if (sel) 0.9f else 0.35f))
                    .then(if (goal || sel) Modifier.background(Color.Transparent) else Modifier)
                    .clickable { onShot(sh) },
                contentAlignment = Alignment.Center,
            ) { if (goal) Text("⚽", fontSize = (d.value * 0.5f).sp) }
        }
    }
}

// ---------- momentum ----------

@Composable
fun MomentumGraph(points: List<MomentumPoint>, homeColor: Color, awayColor: Color, modifier: Modifier = Modifier, goals: List<Pair<Double, Boolean>> = emptyList()) {
    val pal = LocalPalette.current
    Canvas(modifier.fillMaxWidth().height(90.dp)) {
        if (points.isEmpty()) return@Canvas
        val maxMin = max(90.0, points.maxOf { it.minute })
        val mid = size.height / 2
        val bw = max(1.5f, size.width / maxMin.toFloat() - 0.8f)
        drawLine(pal.divider, Offset(0f, mid), Offset(size.width, mid), 1f)
        drawLine(pal.divider, Offset(size.width * (45f / maxMin.toFloat()), 0f), Offset(size.width * (45f / maxMin.toFloat()), size.height), 1f)
        points.forEach { p ->
            val x = (p.minute / maxMin * size.width).toFloat()
            val v = (p.value / 100f).coerceIn(-1f, 1f) * (mid - 4)
            if (v > 0) drawRoundRect(homeColor, Offset(x - bw / 2, mid - v), Size(bw, v), CornerRadius(1f))
            else if (v < 0) drawRoundRect(awayColor, Offset(x - bw / 2, mid), Size(bw, -v), CornerRadius(1f))
        }
        goals.forEach { (m, home) ->
            val x = (m / maxMin * size.width).toFloat()
            drawCircle(if (home) homeColor else awayColor, 4.dp.toPx(), Offset(x, if (home) 5.dp.toPx() else size.height - 5.dp.toPx()))
            drawCircle(Color.White, 2.dp.toPx(), Offset(x, if (home) 5.dp.toPx() else size.height - 5.dp.toPx()))
        }
    }
}

// ---------- lineup on pitch ----------

fun ratingColor(r: Double?): Color = when {
    r == null -> Color(0xFF6B6F76)
    r >= 8.0 -> Color(0xFF1E88E5)
    r >= 7.0 -> Color(0xFF00A86B)
    r >= 6.5 -> Color(0xFF9ACD32)
    r >= 6.0 -> Color(0xFFF2A100)
    else -> Color(0xFFE5484D)
}

/** Places starters by formation rows: GK, then each formation line. */
private fun formationRows(formation: String?, starters: List<LineupPlayer>): List<List<LineupPlayer>> {
    val lines = formation?.split('-')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
    if (lines.isEmpty() || lines.sum() + 1 != starters.size) {
        // fallback: group by position letter
        val order = listOf("G", "D", "M", "F")
        return order.map { p -> starters.filter { (it.position ?: "M").startsWith(p) } }.filter { it.isNotEmpty() }
    }
    val out = mutableListOf(listOf(starters.first()))
    var i = 1
    for (n in lines) { out += starters.subList(i, i + n); i += n }
    return out
}

@Composable
fun LineupPitch(
    home: List<LineupPlayer>, homeFormation: String?, homeColor: Color,
    away: List<LineupPlayer>, awayFormation: String?, awayColor: Color,
    onPlayer: (LineupPlayer, Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(68f / 105f).clip(RoundedCornerShape(12.dp)).background(pitchBrush())) {
        val w = maxWidth; val h = maxHeight
        Canvas(Modifier.fillMaxWidth().aspectRatio(68f / 105f)) { drawPitchVertical() }
        @Composable
        fun side(players: List<LineupPlayer>, formation: String?, color: Color, isHome: Boolean) {
            if (players.isNotEmpty() && players.all { it.px != null && it.py != null }) {
                players.forEach { p ->
                    // source layout: x 0..1 across (team's own view), y 0..1 from own goal (GK ~0.1)
                    val fy = (p.py!! * 0.5f).coerceIn(0.03f, 0.48f)
                    val y = if (isHome) h * (1f - fy) else h * fy
                    val x = if (isHome) w * p.px!! else w * (1f - p.px!!)
                    PlayerDot(p, color, Modifier.offset(x - 34.dp, y - 20.dp)) { onPlayer(p, isHome) }
                }
                return
            }
            val rows = formationRows(formation, players)
            val n = rows.size
            rows.forEachIndexed { ri, row ->
                // home: GK at bottom; away: GK at top
                val frac = 0.045f + 0.43f * (ri.toFloat() / max(1, n - 1).toFloat()) * 0.98f
                val y = if (isHome) h * (1f - frac) else h * frac
                row.forEachIndexed { ci, p ->
                    // SofaScore lists each line right-to-left from the team's own perspective
                    val xf = (ci + 1f) / (row.size + 1f)
                    val x = if (isHome) w * (1f - xf) else w * xf
                    PlayerDot(p, color, Modifier.offset(x - 34.dp, y - 20.dp)) { onPlayer(p, isHome) }
                }
            }
        }
        side(home, homeFormation, homeColor, true)
        side(away, awayFormation, awayColor, false)
    }
}

@Composable
private fun PlayerDot(p: LineupPlayer, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.width(68.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(p.number ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (p.events.any { it == "goal" }) Text("⚽", fontSize = 10.sp, modifier = Modifier.offset((-8).dp, (-4).dp))
            if (p.events.any { it == "yellowCard" }) Box(Modifier.offset(22.dp, 16.dp).size(7.dp, 10.dp).background(K.Yellow))
            if (p.events.any { it == "redCard" }) Box(Modifier.offset(22.dp, 16.dp).size(7.dp, 10.dp).background(K.Loss))
            if (p.events.any { it == "subOut" }) Text("↓", color = K.Loss, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset((-8).dp, 14.dp))
            p.rating?.let { r ->
                Box(
                    Modifier.offset(16.dp, (-5).dp).clip(RoundedCornerShape(5.dp)).background(ratingColor(r)).padding(horizontal = 3.dp),
                ) { Text(String.format(java.util.Locale.US, "%.1f", r), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Text(
            p.shortName.substringAfterLast(' ').ifEmpty { p.shortName },
            color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RatingBadge(r: Double?) {
    if (r == null) return
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(ratingColor(r)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(String.format(java.util.Locale.US, "%.1f", r), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Suppress("unused") private fun clamp(v: Float) = min(1f, max(0f, v))
@Suppress("unused") private val keepFmt = Fmt
