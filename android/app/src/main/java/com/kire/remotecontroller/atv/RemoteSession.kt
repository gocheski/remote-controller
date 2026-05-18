package com.kire.remotecontroller.atv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import remote.Remotemessage
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket

class RemoteSession(
    private val socket: SSLSocket,
    private val enableIme: Boolean = true,
    private val enableVoice: Boolean = true,
) {
    private val input: InputStream = socket.inputStream
    private val output = socket.outputStream
    private val ready = AtomicBoolean(false)
    private var readerJob: Job? = null
    var imeCounter = 0
    var imeFieldCounter = 0
    var voiceSessionId: Int? = null

    private val featurePing = 1 shl 0
    private val featureKey = 1 shl 1
    private val featureIme = 1 shl 2
    private val featureVoice = 1 shl 3
    private val featurePower = 1 shl 5
    private val featureVolume = 1 shl 6
    private val featureAppLink = 1 shl 9

    private var activeFeatures =
        featurePing or featureKey or featurePower or featureVolume or featureAppLink or
            (if (enableIme) featureIme else 0) or
            (if (enableVoice) featureVoice else 0)

    fun start(scope: CoroutineScope, onReady: () -> Unit) {
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val raw = try {
                        ProtobufFraming.readMessage(input)
                    } catch (e: IOException) {
                        Log.d(TAG, "ATV read ended: ${e.message}")
                        break
                    } ?: break
                    try {
                        handleMessage(raw, onReady)
                    } catch (e: Exception) {
                        Log.w(TAG, "ATV message error", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ATV reader stopped", e)
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    fun awaitReady(timeoutMs: Long = 10_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ready.get()) return true
            Thread.sleep(50)
        }
        return ready.get()
    }

    fun sendKey(key: Remotemessage.RemoteKeyCode, direction: Remotemessage.RemoteDirection = Remotemessage.RemoteDirection.SHORT) {
        runCatching {
            val msg = Remotemessage.RemoteMessage.newBuilder()
                .setRemoteKeyInject(
                    Remotemessage.RemoteKeyInject.newBuilder()
                        .setKeyCode(key)
                        .setDirection(direction)
                        .build(),
                )
                .build()
            ProtobufFraming.writeMessage(output, msg)
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        runCatching {
            val end = text.length - 1
            val msg = Remotemessage.RemoteMessage.newBuilder()
                .setRemoteImeBatchEdit(
                    Remotemessage.RemoteImeBatchEdit.newBuilder()
                        .setImeCounter(imeCounter)
                        .setFieldCounter(imeFieldCounter)
                        .addEditInfo(
                            Remotemessage.RemoteEditInfo.newBuilder()
                                .setInsert(1)
                                .setTextFieldStatus(
                                    Remotemessage.RemoteImeObject.newBuilder()
                                        .setStart(end)
                                        .setEnd(end)
                                        .setValue(text)
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build()
            ProtobufFraming.writeMessage(output, msg)
        }
    }

    fun startVoice(): Int {
        sendKey(Remotemessage.RemoteKeyCode.KEYCODE_SEARCH)
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            voiceSessionId?.let { sessionId ->
                runCatching {
                    val begin = Remotemessage.RemoteMessage.newBuilder()
                        .setRemoteVoiceBegin(
                            Remotemessage.RemoteVoiceBegin.newBuilder().setSessionId(sessionId).build(),
                        )
                        .build()
                    ProtobufFraming.writeMessage(output, begin)
                }
                return sessionId
            }
            Thread.sleep(50)
        }
        throw IllegalStateException("Voice session not available")
    }

    fun sendVoiceChunk(sessionId: Int, chunk: ByteArray) {
        val padded = if (chunk.size < 8 * 1024) {
            chunk + ByteArray(8 * 1024 - chunk.size)
        } else {
            chunk
        }
        var offset = 0
        while (offset < padded.size) {
            val end = minOf(offset + 20 * 1024, padded.size)
            val slice = padded.copyOfRange(offset, end)
            runCatching {
                val msg = Remotemessage.RemoteMessage.newBuilder()
                    .setRemoteVoicePayload(
                        Remotemessage.RemoteVoicePayload.newBuilder()
                            .setSessionId(sessionId)
                            .setSamples(com.google.protobuf.ByteString.copyFrom(slice))
                            .build(),
                    )
                    .build()
                ProtobufFraming.writeMessage(output, msg)
            }
            offset = end
        }
    }

    fun endVoice(sessionId: Int) {
        runCatching {
            val msg = Remotemessage.RemoteMessage.newBuilder()
                .setRemoteVoiceEnd(Remotemessage.RemoteVoiceEnd.newBuilder().setSessionId(sessionId).build())
                .build()
            ProtobufFraming.writeMessage(output, msg)
        }
        voiceSessionId = null
    }

    private fun handleMessage(raw: ByteArray, onReady: () -> Unit) {
        val msg = Remotemessage.RemoteMessage.parseFrom(raw)
        val reply = Remotemessage.RemoteMessage.newBuilder()

        when {
            msg.hasRemoteConfigure() -> {
                val supported = msg.remoteConfigure.code1
                activeFeatures = activeFeatures and supported
                reply.remoteConfigure = Remotemessage.RemoteConfigure.newBuilder()
                    .setCode1(activeFeatures)
                    .setDeviceInfo(
                        Remotemessage.RemoteDeviceInfo.newBuilder()
                            .setUnknown1(1)
                            .setUnknown2("1")
                            .setPackageName("atvremote")
                            .setAppVersion("1.0.0")
                            .build(),
                    )
                    .build()
            }
            msg.hasRemoteSetActive() -> {
                reply.remoteSetActive = Remotemessage.RemoteSetActive.newBuilder()
                    .setActive(activeFeatures)
                    .build()
            }
            msg.hasRemotePingRequest() -> {
                reply.remotePingResponse = Remotemessage.RemotePingResponse.newBuilder()
                    .setVal1(msg.remotePingRequest.val1)
                    .build()
            }
            msg.hasRemoteImeBatchEdit() -> {
                imeCounter = msg.remoteImeBatchEdit.imeCounter
                imeFieldCounter = msg.remoteImeBatchEdit.fieldCounter
            }
            msg.hasRemoteStart() -> {
                if (!ready.getAndSet(true)) onReady()
            }
            msg.hasRemoteVoiceBegin() -> {
                voiceSessionId = msg.remoteVoiceBegin.sessionId
            }
        }

        val built = reply.build()
        if (built.hasRemoteConfigure() || built.hasRemoteSetActive() || built.hasRemotePingResponse()) {
            ProtobufFraming.writeMessage(output, built)
        }
    }

    fun close() {
        readerJob?.cancel()
        runCatching { socket.close() }
    }

    companion object {
        private const val TAG = "RemoteSession"
    }
}
