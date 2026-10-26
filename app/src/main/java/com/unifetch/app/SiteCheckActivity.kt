package com.unifetch.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.unifetch.app.download.DownloaderV2
import com.unifetch.app.download.needsUser
import com.unifetch.app.ui.common.LocalDarkTheme
import com.unifetch.app.ui.common.SettingsProvider
import com.unifetch.app.ui.theme.SealTheme
import com.unifetch.app.util.PreferenceUtil.updateString
import com.unifetch.app.util.SiteSessions
import com.unifetch.app.util.USER_AGENT_STRING
import org.koin.android.ext.android.inject

/**
 * "Needs you" (spec 4a): shows a site in UniFetcher's built-in browser so the user can pass a
 * "verify you are human" check or log in themselves. Tapping Done saves that session and retries
 * every download from the site that was waiting. Nothing here is automated.
 */
class SiteCheckActivity : FragmentActivity() {
    private val downloader: DownloaderV2 by inject()

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val site = intent.getStringExtra(EXTRA_SITE)
        val url = intent.getStringExtra(EXTRA_URL)
        if (site.isNullOrBlank() || url.isNullOrBlank()) {
            finish()
            return
        }
        var webView: WebView? = null

        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    var progress by remember { mutableIntStateOf(0) }
                    var canGoBack by remember { mutableStateOf(false) }

                    BackHandler {
                        if (canGoBack) webView?.goBack() else finish()
                    }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = site,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = stringResource(R.string.site_check_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                        )
                                    }
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.Outlined.Close, contentDescription = null)
                                    }
                                },
                                actions = {
                                    TextButton(onClick = { onDone(site) }) {
                                        Text(stringResource(R.string.site_check_done))
                                    }
                                },
                            )
                        }
                    ) { padding ->
                        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                            if (progress in 1..99) {
                                LinearProgressIndicator(
                                    progress = { progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    WebView(context).apply {
                                        webView = this
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        // Pop-ups and new windows are blocked (spec 4f).
                                        settings.javaScriptCanOpenWindowsAutomatically = false
                                        settings.setSupportMultipleWindows(false)
                                        // Pages can't start downloads by themselves (spec 4f).
                                        setDownloadListener { _, _, _, _, _ -> }
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance()
                                            .setAcceptThirdPartyCookies(this, true)
                                        // Downloads use this same browser identity (spec 4i).
                                        USER_AGENT_STRING.updateString(settings.userAgentString)
                                        webViewClient =
                                            object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?,
                                                ): Boolean =
                                                    request?.url?.scheme?.startsWith("http") != true

                                                override fun doUpdateVisitedHistory(
                                                    view: WebView?,
                                                    url: String?,
                                                    isReload: Boolean,
                                                ) {
                                                    canGoBack = view?.canGoBack() == true
                                                }
                                            }
                                        webChromeClient =
                                            object : WebChromeClient() {
                                                override fun onProgressChanged(
                                                    view: WebView?,
                                                    newProgress: Int,
                                                ) {
                                                    progress = newProgress
                                                }
                                            }
                                        loadUrl(url)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun onDone(site: String) {
        SiteSessions.onUserSessionReady(site)
        // Retry every download from this site that was waiting for the user.
        downloader
            .getTaskStateMap()
            .toMap()
            .filter { (task, state) ->
                state.downloadState.needsUser() && SiteSessions.siteOf(task.url) == site
            }
            .keys
            .forEach { task -> runCatching { downloader.restart(task) } }
        finish()
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SITE = "site"
        private const val EXTRA_URL = "url"

        fun open(context: Context, site: String, url: String) {
            context.startActivity(
                Intent(context, SiteCheckActivity::class.java)
                    .putExtra(EXTRA_SITE, site)
                    .putExtra(EXTRA_URL, url)
            )
        }
    }
}
