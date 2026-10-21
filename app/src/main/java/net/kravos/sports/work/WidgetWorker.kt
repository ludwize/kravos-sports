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
            val evs = coroutineScope {
                teams.map { t -> async { app.repo.teamEvents(t.key) } }
                    .awaitAll().flatten().distinctBy { it.key }
            }
            val items = WidgetData.pick(evs, teams.map { it.key }.toSet())
            WidgetData.save(applicationContext, items)
            ScoresWidget.update(applicationContext)
            if (items.any { it.live }) scheduleSoon(applicationContext)
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
            val req = OneTimeWorkRequestBuilder<WidgetWorker>().setInitialDelay(3, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("widget-live", ExistingWorkPolicy.KEEP, req)
        }
    }
}
