package net.kravos.sports.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kravos.sports.R
import net.kravos.sports.data.Event
import net.kravos.sports.ui.Fmt
import net.kravos.sports.ui.MainActivity
import net.kravos.sports.work.WidgetWorker
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class WidgetItem(
    val id: Int, val home: String, val away: String, val hs: String, val as_: String,
    val status: String, val live: Boolean, val finished: Boolean, val start: Long, val league: String,
)

object WidgetData {
    private fun file(ctx: Context) = File(ctx.filesDir, "widget.json")

    /** Live first, then today's, then the next upcoming and the most recent result of each favourite. */
    fun pick(evs: List<Event>, fav: Set<Int>): List<WidgetItem> {
        val now = System.currentTimeMillis() / 1000
        val today = LocalDate.now()
        val live = evs.filter { it.status.live }
        val todays = evs.filter { !it.status.live && Fmt.localDate(it.start) == today }
        val perTeam = fav.flatMap { id ->
            val mine = evs.filter { it.home.id == id || it.away.id == id }
            listOfNotNull(
                mine.filter { it.status.finished }.maxByOrNull { it.start },
                mine.filter { it.status.notStarted && it.start > now }.minByOrNull { it.start },
            )
        }
        return (live + todays + perTeam).distinctBy { it.id }
            .sortedWith(compareBy<Event> { !it.status.live }.thenBy { kotlin.math.abs(it.start - now) })
            .take(8)
            .map {
                WidgetItem(
                    it.id, it.home.shortName, it.away.shortName,
                    (it.homeScore.display ?: it.homeScore.current)?.toString() ?: "",
                    (it.awayScore.display ?: it.awayScore.current)?.toString() ?: "",
                    when {
                        it.status.live -> Fmt.liveClock(it)
                        it.status.finished -> "Kraj"
                        Fmt.localDate(it.start) == today -> Fmt.time(it.start)
                        else -> "${Fmt.dayShort(Fmt.localDate(it.start))} ${Fmt.time(it.start)}"
                    },
                    it.status.live, it.status.finished, it.start, it.tournament?.name ?: "",
                )
            }
    }

    fun save(ctx: Context, items: List<WidgetItem>) {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("h", it.home).put("a", it.away).put("hs", it.hs).put("as", it.as_)
                .put("st", it.status).put("live", it.live).put("fin", it.finished).put("t", it.start).put("lg", it.league))
        }
        runCatching { file(ctx).writeText(JSONObject().put("items", a).put("at", System.currentTimeMillis()).toString()) }
    }

    fun load(ctx: Context): Pair<List<WidgetItem>, Long> = runCatching {
        val o = JSONObject(file(ctx).readText())
        val a = o.getJSONArray("items")
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            WidgetItem(it.getInt("id"), it.getString("h"), it.getString("a"), it.optString("hs"), it.optString("as"),
                it.optString("st"), it.optBoolean("live"), it.optBoolean("fin"), it.optLong("t"), it.optString("lg"))
        } to o.optLong("at")
    }.getOrDefault(emptyList<WidgetItem>() to 0L)
}

class ScoresWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 80.dp), DpSize(250.dp, 160.dp), DpSize(250.dp, 300.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (items, _) = WidgetData.load(context)
        provideContent { Content(items) }
    }

    @Composable
    private fun Content(items: List<WidgetItem>) {
        val white = ColorProvider(Color.White)
        val dim = ColorProvider(Color(0x99FFFFFF))
        val red = ColorProvider(Color(0xFFFF453A))
        val size = LocalSize.current
        val rows = ((size.height.value - 34) / 38).toInt().coerceIn(1, 8)
        Column(
            GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("★ Moji timovi", style = TextStyle(color = dim, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.height(4.dp))
            if (items.isEmpty()) {
                Text("Dodaj omiljene timove u aplikaciji", style = TextStyle(color = white, fontSize = 13.sp))
                return@Column
            }
            items.take(rows).forEach { it ->
                val intent = Intent().setClassName("net.kravos.sports", "net.kravos.sports.ui.MainActivity").putExtra(MainActivity.EXTRA_MATCH, it.id)
                Row(
                    GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(intent)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(it.status, style = TextStyle(color = if (it.live) red else dim, fontSize = 11.sp, fontWeight = FontWeight.Medium), modifier = GlanceModifier.width(54.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(it.home, style = TextStyle(color = white, fontSize = 13.sp), maxLines = 1)
                        Text(it.away, style = TextStyle(color = white, fontSize = 13.sp), maxLines = 1)
                    }
                    if (it.live || it.finished) Column(horizontalAlignment = Alignment.End) {
                        Text(it.hs, style = TextStyle(color = if (it.live) red else white, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        Text(it.as_, style = TextStyle(color = if (it.live) red else white, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }

    companion object {
        suspend fun update(ctx: Context) = ScoresWidget().updateAll(ctx)
        fun refreshAsync(ctx: Context) {
            WidgetWorker.runNow(ctx)
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { update(ctx) } }
        }
    }
}

class ScoresWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScoresWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); WidgetWorker.runNow(context) }
}
