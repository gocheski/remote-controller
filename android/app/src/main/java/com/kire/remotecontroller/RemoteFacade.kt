package com.kire.remotecontroller

import android.content.Context
import com.kire.remotecontroller.atv.AtvRemoteClient
import com.kire.remotecontroller.data.DeviceStore
import com.kire.remotecontroller.philips.PhilipsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class RemoteFacade(
    context: Context,
    host: String,
) {
    private val store = DeviceStore(context)
    private val philips = PhilipsApi(
        host = host,
        deviceId = store.getPhilipsUser(),
        authKey = store.getPhilipsPass(),
    )
    private val atv = AtvRemoteClient(context, host)

    val host: String = host

    fun isPhilipsPaired(): Boolean = philips.isPaired
    fun isAtvPaired(): Boolean = store.isAtvPaired()

    private var pendingPhilipsChallenge: PhilipsApi.PairingChallenge? = null

    suspend fun startPhilipsPairing(): PhilipsApi.PairingChallenge {
        val challenge = philips.pairRequest()
        pendingPhilipsChallenge = challenge
        return challenge
    }

    suspend fun pairPhilips(pin: String) {
        val challenge = pendingPhilipsChallenge ?: error("Call startPhilipsPairing first")
        val authKey = philips.pairGrant(pin, challenge)
        pendingPhilipsChallenge = null
        store.saveDevice(
            host = host,
            name = store.getName() ?: host,
            philipsUser = challenge.deviceId,
            philipsPass = authKey,
            atvPaired = store.isAtvPaired(),
        )
        philips.setCredentials(challenge.deviceId, authKey)
    }

    suspend fun pairAtv(pinHex: String) {
        atv.pair(pinHex.uppercase())
        store.saveDevice(
            host = host,
            name = store.getName() ?: host,
            philipsUser = store.getPhilipsUser(),
            philipsPass = store.getPhilipsPass(),
            atvPaired = true,
        )
    }

    /** Send one key — never both Philips and Android TV (that caused double presses). */
    suspend fun sendKey(name: String) {
        val key = name.uppercase()
        val philipsKey = PHILIPS_KEYS[key]
        when {
            philips.isPaired && philipsKey != null -> {
                philips.sendKey(philipsKey)
            }
            store.isAtvPaired() && !atv.useFallbackOnly -> {
                atv.sendKeyCode(key)
            }
            philips.isPaired && philipsKey != null -> {
                philips.sendKey(philipsKey)
            }
        }
    }

    /** Send several digits in order (e.g. channel 30) with a short gap between each. */
    suspend fun sendDigitSequence(digits: String) = withContext(Dispatchers.IO) {
        for (ch in digits) {
            if (!ch.isDigit()) continue
            sendKey(ch.toString())
            delay(DIGIT_GAP_MS)
        }
    }

    suspend fun sendText(text: String) {
        if (store.isAtvPaired() && !atv.useFallbackOnly) {
            atv.sendText(text)
        }
    }

    suspend fun sendVoice(pcm: ByteArray) {
        if (store.isAtvPaired() && !atv.useFallbackOnly) {
            atv.sendVoice(pcm)
        }
    }

    suspend fun powerOff() {
        if (philips.isPaired) {
            philips.sendKey("Standby")
        } else if (store.isAtvPaired()) {
            atv.sendKeyCode("POWER")
        }
    }

    suspend fun sources() = philips.sendKey("Source")
    suspend fun watchTv() = philips.sendKey("WatchTV")
    suspend fun youtube() = philips.launchYouTube()
    suspend fun ambilightToggle() = philips.toggleAmbilight()

    suspend fun guideKey() {
        if (philips.isPaired) {
            runCatching { philips.sendKey("Guide") }
        }
        if (store.isAtvPaired() && !atv.useFallbackOnly) {
            atv.sendKeyCode("GUIDE")
        }
    }

    suspend fun currentChannel() = withContext(Dispatchers.IO) {
        if (!philips.isPaired) return@withContext null
        runCatching { philips.getCurrentTv() }.getOrNull()
    }

    fun philipsApi(): PhilipsApi = philips
    fun disconnect() {
        atv.disconnect()
    }

    companion object {
        private const val DIGIT_GAP_MS = 250L

        private val PHILIPS_KEYS = mapOf(
            "UP" to "CursorUp",
            "DOWN" to "CursorDown",
            "LEFT" to "CursorLeft",
            "RIGHT" to "CursorRight",
            "ENTER" to "Confirm",
            "OK" to "Confirm",
            "HOME" to "Home",
            "BACK" to "Back",
            "VOL_UP" to "VolumeUp",
            "VOLUME_UP" to "VolumeUp",
            "VOL_DOWN" to "VolumeDown",
            "VOLUME_DOWN" to "VolumeDown",
            "MUTE" to "Mute",
            "0" to "Digit0",
            "1" to "Digit1",
            "2" to "Digit2",
            "3" to "Digit3",
            "4" to "Digit4",
            "5" to "Digit5",
            "6" to "Digit6",
            "7" to "Digit7",
            "8" to "Digit8",
            "9" to "Digit9",
            "POWER" to "Standby",
            "GUIDE" to "Guide",
        )
    }
}
