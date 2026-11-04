package com.unifetch.app.util

/**
 * Security (F5): the built-in browser only loads HTTPS pages (res/xml/network_security_config.xml).
 * Links typed or pasted as http:// are upgraded to https://, which almost every site supports.
 */
object WebSafety {
    fun upgradeToHttps(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
}
