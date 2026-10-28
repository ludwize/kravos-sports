package net.kravos.sports.work

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.kravos.sports.App
import net.kravos.sports.data.Http
import net.kravos.sports.widget.ScoresWidget
import net.kravos.sports.widget.WidgetData
import java.util.concurrent.TimeUnit

/** Refreshes favourite-team matches for the home-screen widget. Polls faster while a favourite is live. */
class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val teams = app.prefs.teams.value
        if (teams.isEmpty()) { WidgetData.save(applicationContext, emptyList()); ScoresWidget.update(applicationContext); return Result.success() }
        return try {
            val now = System.currentTimeMillis() / 1000
            val evs = coroutineScope {
                val base = teams.map { t -> async { app.repo.teamEvents(t.key) } }
                    .awaitAll().flatten().distinctBy { it.key }
                // Team pages lag behind (and EuroLeague schedules never carry live state), so any match
                // that should be running right now is re-read from its live match endpoint.
                base.map { e ->
                    async {
                        val running = !e.status.finished && e.start in 1..(now + 60) && now - e.start < 4 * 3600
                        if (!running) e
                        else runCatching { app.repo.match(e.key, live = true).event }.getOrNull()
                            ?.let { f -> f.copy(home = e.home, away = e.away, tournament = e.tournament ?: f.tournament) } ?: e
                    }
                }.awaitAll()
            }
            val items = WidgetData.pick(evs, teams.map { it.key }.toSet())
            WidgetData.save(applicationContext, items)
            ScoresWidget.update(applicationContext)
            // keep polling while something is live or about to start (kick-off within 15 min / in its usual window)
            val hot = items.any { it.live } || evs.any { !it.status.finished && it.start in 1..(now + 15 * 60) && now - it.start < 4 * 3600 }
            if (hot) scheduleSoon(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.w(Http.TAG, "widget refresh failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("widget", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork("widget-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetWorker>().build())
        }

        private fun scheduleSoon(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<WidgetWorker>().setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            // APPEND_OR_REPLACE: when this runs inside the "widget-live" job itself, KEEP would see the
            // still-running job and silently drop the next one, ending live polling after one round.
            WorkManager.getInstance(ctx).enqueueUniqueWork("widget-live", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
