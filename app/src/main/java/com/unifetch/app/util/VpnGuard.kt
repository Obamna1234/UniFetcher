package com.unifetch.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.getString
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * VPN status and test (spec 4h).
 * - [vpnOn] comes from Android's own network information: nothing is contacted to work it out.
 * - "Pause downloads when no VPN is active" (off by default) stops the queue while the VPN is off
 *   and carries on when it's back (see DownloaderV2).
 * - [test] asks one "what is my IP" service, only when the user presses "Test VPN".
 */
object VpnGuard {
    private const val TAG = "VpnGuard"

    /**
     * The only service "Test VPN" contacts (spec section 7), and only when the button is pressed.
     * Run by Mullvad VPN; shows the IP address and country websites see, no account or tracking.
     */
    const val TEST_URL = "https://am.i.mullvad.net/json"

    private val mutableVpnOn = MutableStateFlow(false)
    val vpnOn: StateFlow<Boolean> = mutableVpnOn.asStateFlow()

    fun isRequired(): Boolean = PAUSE_WITHOUT_VPN.getBoolean()

    /** False while "Pause downloads when no VPN is active" is on and the VPN is off. */
    fun canDownload(): Boolean = !isRequired() || mutableVpnOn.value

    /** Starts watching the phone's connection. Call once from App.onCreate. */
    fun init(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        mutableVpnOn.value = isVpnActive(cm)
        runCatching {
                cm.registerDefaultNetworkCallback(
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            caps: NetworkCapabilities,
                        ) {
                            update(caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                        }

                        override fun onLost(network: Network) {
                            update(isVpnActive(cm))
                        }

                        override fun onAvailable(network: Network) {
                            update(isVpnActive(cm))
                        }
                    }
                )
            }
            .onFailure { Log.w(TAG, "couldn't watch the network", it) }
    }

    private fun update(on: Boolean) {
        if (mutableVpnOn.value != on) {
            Log.i(TAG, "VPN is now ${if (on) "on" else "off"}")
            mutableVpnOn.value = on
        }
    }

    private fun isVpnActive(cm: ConnectivityManager): Boolean =
        runCatching {
                cm.getNetworkCapabilities(cm.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
            .getOrDefault(false)

    @Serializable
    data class TestResult(
        val ip: String? = null,
        val country: String? = null,
        val city: String? = null,
        val organization: String? = null,
        @SerialName("mullvad_exit_ip") val mullvadExitIp: Boolean? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * "Test VPN": asks [TEST_URL] which IP address and country it sees, through the same
     * connection downloads use (including UniFetcher's proxy setting if it's on). Blocking; call
     * from a background thread.
     */
    fun test(): Result<TestResult> = runCatching {
        val proxy =
            if (PROXY.getBoolean()) proxyFrom(PROXY_URL.getString())
            else Proxy.NO_PROXY
        val connection =
            (URI(TEST_URL).toURL().openConnection(proxy) as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "UniFetcher")
            }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            json.decodeFromString<TestResult>(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    /** "socks5://127.0.0.1:1080" or "http://host:8080" -> a Java proxy (yt-dlp's format). */
    private fun proxyFrom(url: String): Proxy {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return Proxy.NO_PROXY
        val host = uri.host ?: return Proxy.NO_PROXY
        val type =
            if (uri.scheme?.startsWith("socks", ignoreCase = true) == true) Proxy.Type.SOCKS
            else Proxy.Type.HTTP
        val port = if (uri.port > 0) uri.port else if (type == Proxy.Type.SOCKS) 1080 else 8080
        return Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }
}
