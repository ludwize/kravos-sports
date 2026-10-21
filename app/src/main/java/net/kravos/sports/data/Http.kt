package net.kravos.sports.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(200)}")

object Http {
    const val TAG = "KravosSports"
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

    @Volatile private var client: OkHttpClient? = null

    /** SofaScore rejects API calls without this header ("challenge"); the site uses a short random hex token. */
    @Volatile var xrw: String = "a1b2c3"
        private set

    fun init(context: Context) {
        if (client != null) return
        val sp = context.getSharedPreferences("transport", Context.MODE_PRIVATE)
        xrw = sp.getString("xrw", null) ?: (1..6).map { "0123456789abcdef".random() }.joinToString("").also { sp.edit().putString("xrw", it).apply() }
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 60L * 1024 * 1024))
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val r = chain.request()
                val b = r.newBuilder().header("User-Agent", BROWSER_UA)
                val host = r.url.host
                if (host.contains("sofascore")) {
                    // share cookies the bridge WebView earned (challenge pass) with direct requests, e.g. images
                    runCatching { android.webkit.CookieManager.getInstance().getCookie("https://www.sofascore.com") }
                        .getOrNull()?.takeIf { it.isNotBlank() }?.let { b.header("Cookie", it) }
                    b.header("Referer", "https://www.sofascore.com/")
                    b.header("Origin", "https://www.sofascore.com")
                    b.header("Accept", "*/*")
                    b.header("Accept-Language", "en-US,en;q=0.9")
                    if (r.url.encodedPath.startsWith("/api/")) b.header("X-Requested-With", xrw)
                    b.header("Cache-Control", "no-cache")
                }
                chain.proceed(b.build())
            }
            .build()
    }

    fun client(): OkHttpClient = client ?: OkHttpClient()

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client().newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "GET ${url.take(160)} -> ${resp.code} ${body.take(200).replace('\n', ' ')}")
                throw HttpException(resp.code, body)
            }
            Log.i(TAG, "GET ${url.take(160)} -> ${resp.code} (${body.length} b)")
            body
        }
    }
}
