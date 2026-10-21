package net.kravos.sports.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Transport through a hidden WebView that has the real www.sofascore.com page loaded.
 * The page's own JavaScript passes SofaScore's bot "challenge"; we then call fetch() from inside that page,
 * re-using the cookies and the extra headers the site itself sends with its API calls.
 */
class WebBridge(private val appContext: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    @Volatile private var ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String>>>()
    private val ids = AtomicInteger(1)
    private val reloadLock = Mutex()
    @Volatile private var started = false
    @Volatile private var loadedAt = 0L

    /** API base the site itself uses (learned from its own requests). */
    @Volatile var apiBase: String = "https://www.sofascore.com/api/v1/"
        private set
    /** Non-standard headers the site sends with API requests (e.g. anti-bot tokens). */
    private val siteHeaders = ConcurrentHashMap<String, String>()
    @Volatile var status: String = "nije pokrenut"
        private set

    inner class JsBridge {
        @JavascriptInterface
        fun onResult(id: Int, status: Int, body: String) {
            pending.remove(id)?.complete(status to body)
        }
    }

    private val SKIP = setOf(
        "accept", "accept-encoding", "accept-language", "user-agent", "referer", "origin", "cookie", "connection", "host",
        "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site",
        "content-type", "cache-control", "pragma", "if-none-match", "if-modified-since", "range", "priority",
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (started) return
        started = true
        main.post {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val w = WebView(appContext)
            cm.setAcceptThirdPartyCookies(w, true)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.databaseEnabled = true
            w.settings.loadsImagesAutomatically = false
            w.settings.blockNetworkImage = true
            w.settings.mediaPlaybackRequiresUserGesture = true
            w.settings.userAgentString = Http.BROWSER_UA
            // Android WebView adds "X-Requested-With: <package>" which bot filters flag; remove it where supported.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(w.settings, emptySet()) }
            }
            w.addJavascriptInterface(JsBridge(), "KS")
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    val i = url.indexOf("/api/v1/")
                    if (i > 0 && !url.contains("img.sofascore")) {
                        apiBase = url.substring(0, i + "/api/v1/".length)
                        request.requestHeaders?.forEach { (k, v) ->
                            if (k.lowercase() !in SKIP) siteHeaders[k] = v
                        }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.i(Http.TAG, "bridge page finished: $url")
                    loadedAt = System.currentTimeMillis()
                    // give the challenge script a moment to finish and set its cookie
                    main.postDelayed({ if (!ready.isCompleted) ready.complete(Unit) }, 1500)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            }
            status = "učitava sofascore.com"
            w.loadUrl("https://www.sofascore.com/")
            web = w
        }
    }

    private suspend fun reloadPage() = reloadLock.withLock {
        // another caller already reloaded in the last few seconds
        if (System.currentTimeMillis() - loadedAt < 8000 && ready.isCompleted) return@withLock
        Log.i(Http.TAG, "bridge: reloading page to pass challenge again")
        ready = CompletableDeferred()
        main.post { web?.loadUrl("https://www.sofascore.com/") }
        withTimeout(30_000) { ready.await() }
    }

    private suspend fun jsFetch(url: String): Pair<Int, String> {
        val id = ids.getAndIncrement()
        val d = CompletableDeferred<Pair<Int, String>>()
        pending[id] = d
        val hdr = JSONObject().apply {
            put("Accept", "*/*")
            siteHeaders.forEach { (k, v) -> put(k, v) }
        }
        val js = """
            (function(){
              fetch(${JSONObject.quote(url)}, {credentials:'include', headers:$hdr})
                .then(function(r){ return r.text().then(function(t){ KS.onResult($id, r.status, t); }); })
                .catch(function(e){ KS.onResult($id, -1, String(e)); });
            })();
        """.trimIndent()
        main.post { web?.evaluateJavascript(js, null) }
        return try {
            withTimeout(25_000) { d.await() }
        } finally {
            pending.remove(id)
        }
    }

    suspend fun get(path: String): String {
        start()
        withTimeout(35_000) { ready.await() }
        var (code, body) = jsFetch(apiBase + path)
        if (code == 403 || code == -1) {
            // challenge expired or not yet solved: reload the page, wait, retry once (also try the other API host)
            reloadPage()
            delay(500)
            val r = jsFetch(apiBase + path)
            code = r.first; body = r.second
            if (code == 403 || code == 404) {
                val alt = if (apiBase.contains("//api.")) "https://www.sofascore.com/api/v1/" else "https://api.sofascore.com/api/v1/"
                val r2 = jsFetch(alt + path)
                if (r2.first in 200..299) { apiBase = alt; code = r2.first; body = r2.second }
            }
        }
        if (code !in 200..299) {
            status = "greška $code (${body.take(80)})"
            Log.w(Http.TAG, "bridge $path -> $code ${body.take(160)}")
            throw HttpException(code, body)
        }
        status = "radi · $apiBase" + if (siteHeaders.isNotEmpty()) " · +${siteHeaders.keys.joinToString()}" else ""
        Log.i(Http.TAG, "bridge $path -> $code (${body.length} b)")
        return body
    }
}
