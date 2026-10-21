package net.kravos.sports

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import net.kravos.sports.data.Http
import net.kravos.sports.data.Prefs
import net.kravos.sports.data.Repo
import net.kravos.sports.work.WidgetWorker

class App : Application(), ImageLoaderFactory {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        prefs = Prefs(this)
        repo = Repo(this)
        WidgetWorker.schedule(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Http.client() }
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(120L * 1024 * 1024).build() }
            .respectCacheHeaders(false)
            .crossfade(120)
            .build()
}
