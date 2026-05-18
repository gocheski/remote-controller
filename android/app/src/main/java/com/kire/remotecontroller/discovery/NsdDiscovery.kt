package com.kire.remotecontroller.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull

data class DiscoveredTv(
    val name: String,
    val host: String,
    val port: Int,
)

class NsdDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val serviceType = "_androidtvremote2._tcp."

    fun discover(timeoutMs: Long = 8_000): Flow<List<DiscoveredTv>> = callbackFlow {
        val nsdManager = appContext.getSystemService(NsdManager::class.java)
        if (nsdManager == null) {
            Log.w(TAG, "NsdManager not available")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val found = linkedMapOf<String, DiscoveredTv>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery start failed: $errorCode")
                trySend(found.values.toList())
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress ?: return
                        val name = serviceInfo.serviceName
                            .replace(".$serviceType", "")
                            .replace(".${serviceType.trimEnd('.')}", "")
                        val tv = DiscoveredTv(name = name, host = host, port = serviceInfo.port)
                        found[host] = tv
                        trySend(found.values.toList())
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val host = service.host?.hostAddress
                if (host != null) found.remove(host)
                trySend(found.values.toList())
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.delay(timeoutMs)
            }
            trySend(found.values.toList())
        } catch (e: SecurityException) {
            Log.w(TAG, "Discovery permission denied", e)
            trySend(emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Discovery failed", e)
            trySend(emptyList())
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    companion object {
        private const val TAG = "NsdDiscovery"
    }
}
