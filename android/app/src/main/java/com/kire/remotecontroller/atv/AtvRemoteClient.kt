package com.kire.remotecontroller.atv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import remote.Remotemessage

class AtvRemoteClient(
    private val context: Context,
    private val host: String,
    private val clientName: String = "Philips Remote",
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var session: RemoteSession? = null
    private var connected = false
    var useFallbackOnly = false

    suspend fun ensureConnected(): Boolean = mutex.withLock {
        if (useFallbackOnly) return false
        if (connected && session != null) return true
        withContext(Dispatchers.IO) {
            runCatching {
                val material = CertGenerator.loadOrCreate(context, host)
                val keyStore = CertGenerator.createKeyStore(material.certPem, material.keyPem, "atvremote".toCharArray())
                val socket = TlsSockets.connect(host, 6466, keyStore, "atvremote".toCharArray())
                val remote = RemoteSession(socket)
                var readyCalled = false
                remote.start(scope) { readyCalled = true }
                if (!remote.awaitReady()) {
                    remote.close()
                    error("Remote not ready")
                }
                session = remote
                connected = true
                true
            }.getOrElse {
                useFallbackOnly = true
                false
            }
        }
    }

    suspend fun pair(pinHex: String) = withContext(Dispatchers.IO) {
        val material = CertGenerator.loadOrCreate(context, host)
        val keyStore = CertGenerator.createKeyStore(material.certPem, material.keyPem, "atvremote".toCharArray())
        val socket = TlsSockets.connect(host, 6467, keyStore, "atvremote".toCharArray())
        val pairing = PairingClient(socket, clientName, material.cert)
        try {
            pairing.startPairing()
            pairing.finishPairing(pinHex.uppercase())
        } finally {
            pairing.close()
        }
    }

    suspend fun sendKeyCode(name: String) {
        if (!ensureConnected()) return
        val key = when (name.uppercase()) {
            "UP" -> Remotemessage.RemoteKeyCode.KEYCODE_DPAD_UP
            "DOWN" -> Remotemessage.RemoteKeyCode.KEYCODE_DPAD_DOWN
            "LEFT" -> Remotemessage.RemoteKeyCode.KEYCODE_DPAD_LEFT
            "RIGHT" -> Remotemessage.RemoteKeyCode.KEYCODE_DPAD_RIGHT
            "ENTER", "OK" -> Remotemessage.RemoteKeyCode.KEYCODE_DPAD_CENTER
            "HOME" -> Remotemessage.RemoteKeyCode.KEYCODE_HOME
            "BACK" -> Remotemessage.RemoteKeyCode.KEYCODE_BACK
            "POWER" -> Remotemessage.RemoteKeyCode.KEYCODE_POWER
            "VOL_UP", "VOLUME_UP" -> Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_UP
            "VOL_DOWN", "VOLUME_DOWN" -> Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_DOWN
            "MUTE" -> Remotemessage.RemoteKeyCode.KEYCODE_VOLUME_MUTE
            "0" -> Remotemessage.RemoteKeyCode.KEYCODE_0
            "1" -> Remotemessage.RemoteKeyCode.KEYCODE_1
            "2" -> Remotemessage.RemoteKeyCode.KEYCODE_2
            "3" -> Remotemessage.RemoteKeyCode.KEYCODE_3
            "4" -> Remotemessage.RemoteKeyCode.KEYCODE_4
            "5" -> Remotemessage.RemoteKeyCode.KEYCODE_5
            "6" -> Remotemessage.RemoteKeyCode.KEYCODE_6
            "7" -> Remotemessage.RemoteKeyCode.KEYCODE_7
            "8" -> Remotemessage.RemoteKeyCode.KEYCODE_8
            "9" -> Remotemessage.RemoteKeyCode.KEYCODE_9
            "GUIDE" -> Remotemessage.RemoteKeyCode.KEYCODE_GUIDE
            "SEARCH" -> Remotemessage.RemoteKeyCode.KEYCODE_SEARCH
            else -> return
        }
        mutex.withLock { session?.sendKey(key) }
    }

    suspend fun sendText(text: String) {
        if (!ensureConnected()) return
        mutex.withLock { session?.sendText(text) }
    }

    suspend fun sendVoice(pcm: ByteArray) {
        if (!ensureConnected()) return
        mutex.withLock {
            val remote = session ?: return@withLock
            val sessionId = runCatching { remote.startVoice() }.getOrNull() ?: return@withLock
            remote.sendVoiceChunk(sessionId, pcm)
            remote.endVoice(sessionId)
        }
    }

    fun disconnect() {
        runCatching {
            session?.close()
        }
        session = null
        connected = false
    }
}
