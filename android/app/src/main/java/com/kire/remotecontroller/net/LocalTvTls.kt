package com.kire.remotecontroller.net

import java.net.InetAddress
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Philips / Android TV remotes on your LAN use self-signed certificates.
 * We only relax TLS checks for private/local IP addresses — not the public internet.
 */
object LocalTvTls {
    fun trustManagers(expectedHost: String): Array<TrustManager> = arrayOf(
        LocalTvTrustManager(expectedHost),
    )

    fun hostnameVerifier(expectedHost: String): HostnameVerifier =
        HostnameVerifier { hostname, _ ->
            hostsMatch(hostname, expectedHost)
        }

    fun isLocalTvHost(host: String): Boolean {
        return runCatching {
            val address = InetAddress.getByName(host.trim())
            address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress
        }.getOrDefault(false)
    }

    private fun hostsMatch(a: String, b: String): Boolean =
        a.equals(b, ignoreCase = true) ||
            a.removePrefix("[").removeSuffix("]") == b.removePrefix("[").removeSuffix("]")

    private class LocalTvTrustManager(
        private val expectedHost: String,
    ) : X509TrustManager {
        override fun checkClientTrusted(
            chain: Array<java.security.cert.X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<java.security.cert.X509Certificate>?,
            authType: String?,
        ) {
            require(!chain.isNullOrEmpty()) { "No server certificate" }
            if (!isLocalTvHost(expectedHost)) {
                throw java.security.cert.CertificateException("TLS pinning only allowed for local TV hosts")
            }
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }
}
