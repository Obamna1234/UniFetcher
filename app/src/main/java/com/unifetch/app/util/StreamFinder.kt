package com.unifetch.app.util

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.unifetch.app.App
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.updateString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Video finder: for pages yt-dlp can't read, opens the page in an invisible built-in browser,
 * lets the page's own player load, and notes the video stream it asks for (HLS, DASH or a plain
 * file). That stream is then downloaded with yt-dlp, the same way the player would fetch it.
 *
 * Nothing is clicked or solved for the user: if the player only starts after a tap, or the page
 * shows a check or login, the download goes to "Action needed", where the user presses play
 * themselves in [com.unifetch.app.SiteCheckActivity] and the stream found there is remembered.
 * Copy-protected (DRM) streams are still refused by yt-dlp.
 */
object StreamFinder {
    private const val TAG = "StreamFinder"

    /** Longest a background look at a page may take. */
    private const val TIMEOUT_MS = 45_000L

    /** After the page loads, how long to wait for the player to ask for its video. */
    private const val PLAYER_WAIT_MS = 15_000L

    /** Once a playlist (HLS/DASH) is seen, how long to keep listening for a better one. */
    private const val SETTLE_AFTER_FOUND_MS = 2_500L

    data class Found(val candidate: StreamPicker.Candidate, val pageTitle: String?)

    /** Streams found by the user pressing play in the visible browser, by page link. */
    private val remembered = ConcurrentHashMap<String, Found>()

    fun isEnabled(): Boolean = VIDEO_FINDER.getBoolean()

    fun remember(pageUrl: String, found: Found) {
        remembered[normalize(pageUrl)] = found
    }

    fun rememberedFor(pageUrl: String): Found? = remembered[normalize(pageUrl)]

    private fun normalize(url: String) = url.substringBefore('#').trimEnd('/')

    /**
     * Records [url] if it looks like a video stream. Called for every request a page makes, from
     * the built-in browser; never blocks or changes the request.
     */
    fun observe(url: String, into: MutableList<StreamPicker.Candidate>): StreamPicker.Kind? {
        val kind = StreamPicker.classify(url) ?: return null
        into += StreamPicker.Candidate(url, kind)
        return kind
    }

    /**
     * Looks for the main video on [pageUrl] (blocking; call from a background thread). Returns
     * null if no video stream was seen. A stream the user found by pressing play is used first.
     */
    fun find(pageUrl: String): Found? {
        rememberedFor(pageUrl)?.let {
            return it
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        return runBlocking { look(pageUrl) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun look(pageUrl: String): Found? =
        withContext(Dispatchers.Main) {
            val handler = Handler(Looper.getMainLooper())
            val seen = CopyOnWriteArrayList<StreamPicker.Candidate>()
            var webView: WebView? = null
            var title: String? = null
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val finish = Runnable { if (cont.isActive) cont.resume(Unit) }
                    val view =
                        WebView(App.context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Let the player start by itself where the page allows it.
                            settings.mediaPlaybackRequiresUserGesture = false
                            // Pop-ups, new windows and page-started downloads are blocked (4f).
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            setDownloadListener { _, _, _, _, _ -> }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            USER_AGENT_STRING.updateString(settings.userAgentString)
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean = request?.url?.scheme?.startsWith("http") != true

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): WebResourceResponse? {
                                        val url = request?.url?.toString() ?: return null
                                        val kind = observe(url, seen)
                                        if (kind == StreamPicker.Kind.HLS || kind == StreamPicker.Kind.DASH) {
                                            handler.removeCallbacks(finish)
                                            handler.postDelayed(finish, SETTLE_AFTER_FOUND_MS)
                                        }
                                        return null // never changes what the page loads
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?,
                                    ) {
                                        handler.removeCallbacks(finish)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        title = view?.title
                                        // Keep it quiet: mute any video and let it start.
                                        view?.evaluateJavascript(MUTE_AND_PLAY, null)
                                        val gotPlaylist =
                                            seen.any { it.kind != StreamPicker.Kind.FILE }
                                        handler.removeCallbacks(finish)
                                        handler.postDelayed(
                                            finish,
                                            if (gotPlaylist) SETTLE_AFTER_FOUND_MS else PLAYER_WAIT_MS,
                                        )
                                    }
                                }
                        }
                    webView = view
                    cont.invokeOnCancellation { handler.removeCallbacks(finish) }
                    view.loadUrl(WebSafety.upgradeToHttps(pageUrl))
                }
            }
            webView?.run {
                stopLoading()
                destroy()
            }
            CookieManager.getInstance().flush()
            val best = StreamPicker.pick(seen)
            Log.i(TAG, "looked at the page: ${seen.size} video requests, picked ${best?.let(StreamPicker::describe)}")
            best?.let { Found(it, title?.takeIf { t -> t.isNotBlank() }) }
        }

    /** Mutes every video on the page and asks it to play (no clicks on anything else). */
    const val MUTE_AND_PLAY =
        "document.querySelectorAll('video').forEach(function(v){v.muted=true;" +
            "try{var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}});"
}
