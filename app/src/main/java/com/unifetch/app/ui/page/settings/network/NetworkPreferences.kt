package com.unifetch.app.ui.page.settings.network

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import com.unifetch.app.util.AUTO_SESSIONS
import com.unifetch.app.util.SiteSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.OfflineBolt
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.SignalCellularConnectedNoInternet4Bar
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.unifetch.app.R
import com.unifetch.app.ui.common.booleanState
import com.unifetch.app.ui.component.BackButton
import com.unifetch.app.ui.component.PreferenceInfo
import com.unifetch.app.ui.component.PreferenceItem
import com.unifetch.app.ui.component.PreferenceSubtitle
import com.unifetch.app.ui.component.PreferenceSwitch
import com.unifetch.app.ui.component.PreferenceSwitchWithDivider
import com.unifetch.app.util.ARIA2C
import com.unifetch.app.util.CELLULAR_DOWNLOAD
import com.unifetch.app.util.COOKIES
import com.unifetch.app.util.CUSTOM_COMMAND
import com.unifetch.app.util.FORCE_IPV4
import com.unifetch.app.util.PROXY
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.updateBoolean
import com.unifetch.app.util.PreferenceUtil.updateValue
import com.unifetch.app.util.RATE_LIMIT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPreferences(navigateToCookieProfilePage: () -> Unit = {}, onNavigateBack: () -> Unit) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
            rememberTopAppBarState(),
            canScroll = { true },
        )

    var showConcurrentDownloadDialog by remember { mutableStateOf(false) }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var aria2c by remember { mutableStateOf(ARIA2C.getBoolean()) }
    var proxy by PROXY.booleanState
    var isCookiesEnabled by COOKIES.booleanState
    var autoSessions by remember { mutableStateOf(AUTO_SESSIONS.getBoolean()) }
    var sessionSites by remember { mutableStateOf(SiteSessions.savedSites()) }
    var showSessionSitesDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var forceIpv4 by FORCE_IPV4.booleanState

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.network)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            val isCustomCommandEnabled by CUSTOM_COMMAND.booleanState

            LazyColumn(contentPadding = it) {
                if (isCustomCommandEnabled)
                    item {
                        PreferenceInfo(
                            text = stringResource(id = R.string.custom_command_enabled_hint)
                        )
                    }
                item { PreferenceSubtitle(text = stringResource(R.string.general_settings)) }
                item {
                    var isRateLimitEnabled by remember { mutableStateOf(RATE_LIMIT.getBoolean()) }

                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.rate_limit),
                        description = stringResource(R.string.rate_limit_desc),
                        icon = Icons.Outlined.Speed,
                        enabled = !isCustomCommandEnabled,
                        isChecked = isRateLimitEnabled,
                        onChecked = {
                            isRateLimitEnabled = !isRateLimitEnabled
                            updateValue(RATE_LIMIT, isRateLimitEnabled)
                        },
                        onClick = { showRateLimitDialog = true },
                    )
                }
                item {
                    var isDownloadWithCellularEnabled by remember {
                        mutableStateOf(CELLULAR_DOWNLOAD.getBoolean())
                    }
                    PreferenceSwitch(
                        title = stringResource(R.string.download_with_cellular),
                        description = stringResource(R.string.download_with_cellular_desc),
                        icon =
                            if (isDownloadWithCellularEnabled) Icons.Outlined.SignalCellular4Bar
                            else Icons.Outlined.SignalCellularConnectedNoInternet4Bar,
                        isChecked = isDownloadWithCellularEnabled,
                        onClick = {
                            isDownloadWithCellularEnabled = !isDownloadWithCellularEnabled
                            updateValue(CELLULAR_DOWNLOAD, isDownloadWithCellularEnabled)
                        },
                    )
                }

                item { PreferenceSubtitle(text = stringResource(id = R.string.advanced_settings)) }

                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.aria2),
                        icon = Icons.Outlined.Bolt,
                        description = stringResource(R.string.aria2_desc),
                        isChecked = aria2c,
                        onClick = {
                            aria2c = !aria2c
                            updateValue(ARIA2C, aria2c)
                        },
                    )
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(id = R.string.proxy),
                        description = stringResource(id = R.string.proxy_desc),
                        icon = Icons.Outlined.VpnKey,
                        isChecked = proxy,
                        onChecked = {
                            proxy = !proxy
                            PROXY.updateBoolean(proxy)
                        },
                        onClick = { showProxyDialog = true },
                        enabled = !isCustomCommandEnabled,
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(id = R.string.concurrent_download),
                        description = stringResource(R.string.concurrent_download_desc),
                        icon = Icons.Outlined.OfflineBolt,
                        enabled = !aria2c && !isCustomCommandEnabled,
                    ) {
                        showConcurrentDownloadDialog = true
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.force_ipv4),
                        description = stringResource(id = R.string.force_ipv4_desc),
                        icon = Icons.Outlined.SettingsEthernet,
                        enabled = !isCustomCommandEnabled,
                        isChecked = forceIpv4,
                    ) {
                        forceIpv4 = !forceIpv4
                        FORCE_IPV4.updateBoolean(forceIpv4)
                    }
                }
                item {
                    PreferenceSwitch(
                        title = stringResource(R.string.auto_sessions),
                        description = stringResource(R.string.auto_sessions_desc),
                        icon = Icons.Outlined.Cookie,
                        isChecked = autoSessions,
                        onClick = {
                            autoSessions = !autoSessions
                            AUTO_SESSIONS.updateBoolean(autoSessions)
                        },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.session_sites),
                        description =
                            if (sessionSites.isEmpty()) stringResource(R.string.session_sites_none)
                            else
                                pluralStringResource(
                                    R.plurals.session_sites_count,
                                    sessionSites.size,
                                    sessionSites.size,
                                ),
                        icon = Icons.Outlined.Language,
                        enabled = sessionSites.isNotEmpty(),
                        onClick = { showSessionSitesDialog = true },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.cookies),
                        description = stringResource(R.string.cookies_desc),
                        icon = Icons.Outlined.Cookie,
                        onClick = { navigateToCookieProfilePage() },
                    )
                }
            }
        },
    )

    if (showSessionSitesDialog) {
        AlertDialog(
            onDismissRequest = { showSessionSitesDialog = false },
            icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            title = { Text(stringResource(R.string.session_sites)) },
            text = {
                LazyColumn {
                    item {
                        Text(
                            text = stringResource(R.string.session_sites_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    items(sessionSites.sorted()) { site ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = site, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        SiteSessions.forget(site)
                                        sessionSites = SiteSessions.savedSites()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.session_forget))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSessionSitesDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            SiteSessions.forgetAll()
                            sessionSites = SiteSessions.savedSites()
                        }
                        showSessionSitesDialog = false
                    }
                ) {
                    Text(stringResource(R.string.session_forget_all))
                }
            },
        )
    }

    if (showConcurrentDownloadDialog) {
        ConcurrentDownloadDialog { showConcurrentDownloadDialog = false }
    }

    if (showRateLimitDialog) {
        RateLimitDialog { showRateLimitDialog = false }
    }
    if (showProxyDialog) {
        ProxyConfigurationDialog { showProxyDialog = false }
    }
}
