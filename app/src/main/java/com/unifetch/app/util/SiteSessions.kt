package com.unifetch.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.util.FileUtil.getCookiesFile
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.getString
import com.unifetch.app.util.PreferenceUtil.updateString
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Automatic per-site browser sessions (spec 4a, 4e, 4i).
 *
 * When a site refuses a download with a session error (401/403/410), UniFetcher opens the link
 * in an invisible built-in browser, lets the site set its cookies (including passing "checking
 * your browser" pages that a normal browser passes by itself), then retries once with those
 * cookies and the same browser identity (user agent). Sites that needed this are remembered, so
 * later downloads from them use the session straight away. If a remembered session stops
 * working it is refreshed once; it never loops. CAPTCHAs and logins are never automated.
 *
 * Cookies live in the built-in browser's private storage and are written to UniFetcher's
 * private cookie file for yt-dlp; they are never logged or included in diagnostics.
 */
object SiteSessions {
    private const val TAG = "SiteSessions"

    /** How long a background visit may take before giving up. */
    private const val VISIT_TIMEOUT_MS = 35_000L

    /** After the page loads, wait this long for "checking your browser" pages to finish. */
    private const val SETTLE_MS = 6_000L

    /** A visit this recent is reused instead of visiting again (e.g. many links, one site). */
    private const val REUSE_VISIT_MS = 120_000L

    private val siteLocks = ConcurrentHashMap<String, Mutex>()
    private val lastVisit = ConcurrentHashMap<String, Long>()
    private val tasksUsingCookies = ConcurrentHashMap.newKeySet<String>()

    /** Thrown when a download fails, with a plain reason and a safe diagnostic. */
    class DownloadFailure(
        val reason: FailureReason,
        val cookieSource: CookieSource,
        diagnostic: String,
        cause: Throwable,
    ) : Exception(diagnostic, cause)

    enum class CookieSource {
        NONE,
        MANUAL,
        AUTOMATIC,
    }

    fun isEnabled(): Boolean = AUTO_SESSIONS.getBoolean()

    /** "www.example.com" -> "example.com". Null if the link has no host. */
    fun siteOf(url: String?): String? =
        runCatching { URI(url?.trim()).host }
            .getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }

    fun savedSites(): Set<String> = PreferenceUtil.getSessionSites()

    private fun rememberSite(site: String) {
        val sites = savedSites()
        if (site !in sites) PreferenceUtil.updateSessionSites(sites + site)
    }

    /** True while a download with this ID is using cookies (shown in its progress text). */
    fun isUsingCookies(taskId: String?): Boolean = taskId != null && taskId in tasksUsingCookies

    /**
     * Runs a yt-dlp request for [url], adding the site's saved session when there is one, and
     * getting (or refreshing) a session once if the site refuses. Also checks for a yt-dlp
     * update on extractor errors (via [YtdlpUpdateManager]). On failure throws a
     * [DownloadFailure] with a plain reason and the steps tried.
     */
    fun <T> run(url: String, request: YoutubeDLRequest, taskId: String?, block: () -> T): T {
        val site = siteOf(url)
        val steps = mutableListOf<String>()
        var cookieSource =
            if (request.hasOption("--cookies")) CookieSource.MANUAL else CookieSource.NONE

        if (cookieSource == CookieSource.NONE && site != null && isEnabled() && site in savedSites()) {
            applySession(request)
            cookieSource = CookieSource.AUTOMATIC
            steps += "Used the saved automatic session for this site"
        }
        if (cookieSource != CookieSource.NONE) taskId?.let { tasksUsingCookies += it }

        try {
            return YtdlpUpdateManager.runYtdlpWithAutoRetry(onStep = { steps += it }, block = block)
        } catch (e: YoutubeDL.CanceledException) {
            throw e
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            var last: Exception = e
            var sessionTried = false
            if (site != null && isEnabled() && FailureReason.isSessionError(e.message)) {
                steps +=
                    if (cookieSource == CookieSource.NONE)
                        "The site refused without a session: opened it in the background browser"
                    else "The site refused the session: refreshed it in the background browser"
                if (refreshSession(site, url)) {
                    rememberSite(site)
                    applySession(request)
                    if (cookieSource == CookieSource.NONE) cookieSource = CookieSource.AUTOMATIC
                    taskId?.let { tasksUsingCookies += it }
                    sessionTried = true
                    steps += "Retried once with the new session"
                    try {
                        return YtdlpUpdateManager.runYtdlp(block)
                    } catch (c: YoutubeDL.CanceledException) {
                        throw c
                    } catch (e2: Exception) {
                        last = e2
                    }
                } else {
                    steps += "The background browser couldn't open the site (timed out)"
                }
            } else if (cookieSource != CookieSource.NONE) {
                sessionTried = true
            }
            throw failure(last, cookieSource, sessionTried, steps)
        } finally {
            taskId?.let { tasksUsingCookies -= it }
        }
    }

    /** Adds the cookie file and the built-in browser's identity to a request. */
    private fun applySession(request: YoutubeDLRequest) {
        val context = App.context
        if (!request.hasOption("--cookies")) {
            request.addOption("--cookies", context.getCookiesFile().absolutePath)
        }
        val userAgent = USER_AGENT_STRING.getString()
        val hasUserAgent =
            request.getArguments("--add-header")?.any {
                it?.startsWith("User-Agent", ignoreCase = true) == true
            } == true
        if (userAgent.isNotBlank() && !hasUserAgent) {
            request.addOption("--add-header", "User-Agent:$userAgent")
        }
    }

    /** Gets a fresh session for [site] by visiting [url], unless one was just made. */
    private fun refreshSession(site: String, url: String): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        val lock = siteLocks.getOrPut(site) { Mutex() }
        return runBlocking {
            lock.withLock {
                val recent = lastVisit[site]?.let { System.currentTimeMillis() - it < REUSE_VISIT_MS }
                if (recent == true) return@withLock true
                val ok = visit(url)
                if (ok) {
                    writeCookieFile()
                    lastVisit[site] = System.currentTimeMillis()
                }
                ok
            }
        }
    }

    /** Writes every built-in browser cookie to UniFetcher's private cookie file for yt-dlp. */
    private fun writeCookieFile() {
        DownloadUtil.getCookiesContentFromDatabase()
            .onSuccess { FileUtil.writeContentToFile(it, App.context.getCookiesFile()) }
            .onFailure { Log.w(TAG, "couldn't write the cookie file", it) }
    }

    /**
     * Opens [url] in an invisible built-in browser and waits until the page has settled.
     * Pop-ups, new windows, page-started downloads and non-web links are blocked (spec 4f).
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun visit(url: String): Boolean =
        withContext(Dispatchers.Main) {
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            val result =
                withTimeoutOrNull(VISIT_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val done = Runnable {
                            CookieManager.getInstance().flush()
                            if (cont.isActive) cont.resume(true)
                        }
                        val view =
                            WebView(App.context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                // Never let a page start a download by itself.
                                setDownloadListener { _, _, _, _, _ -> }
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                USER_AGENT_STRING.updateString(settings.userAgentString)
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean =
                                            request?.url?.scheme?.startsWith("http") != true

                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?,
                                        ) {
                                            // A new page (e.g. after a browser check): wait again.
                                            handler.removeCallbacks(done)
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            handler.removeCallbacks(done)
                                            handler.postDelayed(done, SETTLE_MS)
                                        }
                                    }
                            }
                        webView = view
                        cont.invokeOnCancellation { handler.removeCallbacks(done) }
                        view.loadUrl(url)
                    }
                }
            webView?.run {
                stopLoading()
                destroy()
            }
            (result == true).also { Log.i(TAG, "background visit finished: $it") }
        }

    // ---- Settings: sites with saved sessions ----------------------------------------------------

    /** Forgets one site's automatic session and deletes its cookies. */
    fun forget(site: String) {
        PreferenceUtil.updateSessionSites(savedSites() - site)
        lastVisit -= site
        deleteCookiesFor(setOf(site))
    }

    /** Forgets every automatic session and deletes those sites' cookies. */
    fun forgetAll() {
        val sites = savedSites()
        PreferenceUtil.updateSessionSites(emptySet())
        lastVisit.clear()
        deleteCookiesFor(sites)
    }

    /** Expires the built-in browser's cookies for these sites (and their subdomains). */
    private fun deleteCookiesFor(sites: Set<String>) {
        if (sites.isEmpty()) return
        val cookies = DownloadUtil.getCookieListFromDatabase().getOrNull() ?: return
        val manager = CookieManager.getInstance()
        cookies
            .filter { cookie ->
                val host = cookie.domain.removePrefix(".")
                sites.any { site -> host == site || host.endsWith(".$site") }
            }
            .forEach { cookie ->
                val host = cookie.domain.removePrefix(".")
                manager.setCookie(
                    "https://$host",
                    "${cookie.name}=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; " +
                        "Path=${cookie.path}; Domain=${cookie.domain}",
                )
            }
        manager.flush()
        writeCookieFile()
    }

    // ---- Failure details -------------------------------------------------------------------------

    private fun failure(
        error: Exception,
        cookieSource: CookieSource,
        sessionTried: Boolean,
        steps: List<String>,
    ): DownloadFailure {
        val reason = FailureReason.classify(error.message, sessionTried)
        val context = App.context
        val diagnostic = buildString {
            appendLine(context.getString(R.string.failure_reason_line, reason.label(context)))
            appendLine(
                context.getString(
                    when (cookieSource) {
                        CookieSource.NONE -> R.string.failure_cookies_none
                        CookieSource.MANUAL -> R.string.failure_cookies_manual
                        CookieSource.AUTOMATIC -> R.string.failure_cookies_automatic
                    }
                )
            )
            steps.forEach { appendLine("- $it") }
            appendLine()
            append(redact(error.message.orEmpty()))
        }
        return DownloadFailure(reason, cookieSource, diagnostic, error)
    }

    /** Removes link query strings (which can hold tokens) from error text. */
    private fun redact(text: String): String =
        text.replace(Regex("""(https?://[^\s?#'"]+)\?[^\s'"]*"""), "$1?…")
}

/** Short label for a failure reason, shown on the download card. */
fun FailureReason.label(context: Context): String =
    context.getString(
        when (this) {
            FailureReason.SITE_NOT_SUPPORTED -> R.string.reason_site_not_supported
            FailureReason.BLOCKED_BY_SECURITY -> R.string.reason_blocked_by_security
            FailureReason.SESSION_REJECTED -> R.string.reason_session_rejected
            FailureReason.NEEDS_LOGIN -> R.string.reason_needs_login
            FailureReason.VIDEO_UNAVAILABLE -> R.string.reason_video_unavailable
            FailureReason.DRM -> R.string.reason_drm
            FailureReason.NETWORK -> R.string.reason_network
            FailureReason.STORAGE -> R.string.reason_storage
            FailureReason.OTHER -> R.string.status_error
        }
    )

/** The plain failure reason for an error from a download. */
fun Throwable.failureReason(): FailureReason =
    (this as? SiteSessions.DownloadFailure)?.reason ?: FailureReason.classify(message)
