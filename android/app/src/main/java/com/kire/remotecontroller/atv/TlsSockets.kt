package com.kire.remotecontroller.atv

import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object TlsSockets {
    private val trustAll = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        },
    )

    fun connect(
        host: String,
        port: Int,
        keyStore: KeyStore,
        password: CharArray,
    ): SSLSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, trustAll, null)
        }
        return (context.socketFactory.createSocket() as SSLSocket).apply {
            soTimeout = 20_000
            connect(InetSocketAddress(host, port), 15_000)
            startHandshake()
        }
    }
}
