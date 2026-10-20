package net.kravos.sports.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fallback transport: a hidden WebView that sits on www.sofascore.com and performs fetch() from that origin.
 * The WebView is real Chromium, so it passes the bot protection that sometimes blocks plain HTTP clients.
 */
class WebBridge(private val appContext: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private val ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Pair<Int, String>>>()
    private val ids = AtomicInteger(1)
    @Volatile private var started = false

    inner class JsBridge {
        @JavascriptInterface
        fun onResult(id: Int, status: Int, body: String) {
            pending.remove(id)?.complete(status to body)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun start() {
        if (started) return
        started = true
        main.post {
            val w = WebView(appContext)
            w.settings.javaScriptEnabled = true
            w.settings.domStorageEnabled = true
            w.settings.blockNetworkImage = true
            w.settings.userAgentString = Http.BROWSER_UA
            w.addJavascriptInterface(JsBridge(), "KS")
            w.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.i(Http.TAG, "bridge page ready: $url")
                    if (!ready.isCompleted) ready.complete(Unit)
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false
            }
            w.loadUrl("https://www.sofascore.com/robots.txt")
            web = w
        }
    }

    suspend fun get(path: String): String {
        start()
        withTimeout(25_000) { ready.await() }
        val id = ids.getAndIncrement()
        val d = CompletableDeferred<Pair<Int, String>>()
        pending[id] = d
        val js = """
            (function(){
              fetch('/api/v1/$path', {credentials:'include', headers:{'Accept':'application/json'}})
                .then(function(r){ return r.text().then(function(t){ KS.onResult($id, r.status, t); }); })
                .catch(function(e){ KS.onResult($id, -1, String(e)); });
            })();
        """.trimIndent()
        main.post { web?.evaluateJavascript(js, null) }
        val (code, body) = try {
            withTimeout(25_000) { d.await() }
        } finally {
            pending.remove(id)
        }
        if (code !in 200..299) {
            Log.w(Http.TAG, "bridge $path -> $code ${body.take(160)}")
            throw HttpException(code, body)
        }
        Log.i(Http.TAG, "bridge $path -> $code (${body.length} b)")
        return body
    }
}
