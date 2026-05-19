package com.kire.remotecontroller.atv

import com.kire.remotecontroller.net.LocalTvTls
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

object TlsSockets {
    fun connect(
        host: String,
        port: Int,
        keyStore: KeyStore,
        password: CharArray,
    ): SSLSocket {
        require(LocalTvTls.isLocalTvHost(host)) {
            "Android TV remote only works on your local network (private IP)"
        }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        val trustManagers = LocalTvTls.trustManagers(host)
        val context = SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, trustManagers, null)
        }
        return (context.socketFactory.createSocket() as SSLSocket).apply {
            soTimeout = 20_000
            connect(InetSocketAddress(host, port), 15_000)
            startHandshake()
        }
    }
}
