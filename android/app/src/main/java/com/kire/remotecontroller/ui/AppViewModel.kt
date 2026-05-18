package com.kire.remotecontroller.ui

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kire.remotecontroller.RemoteFacade
import com.kire.remotecontroller.data.DeviceStore
import com.kire.remotecontroller.discovery.DiscoveredTv
import com.kire.remotecontroller.discovery.NsdDiscovery
import com.kire.remotecontroller.epg.ChannelRow
import com.kire.remotecontroller.epg.EpgRepository
import com.kire.remotecontroller.epg.ProgrammeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DeviceStore(app)
    private val discovery = NsdDiscovery(app)

    init {
        store.getHost()?.let { selectManual(it, store.getName() ?: it) }
    }

    /** Call after runtime permissions are granted (from MainActivity). */
    fun onPermissionsReady() {
        scan()
    }

    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _selectedHost = MutableStateFlow<String?>(store.getHost())
    val selectedHost: StateFlow<String?> = _selectedHost

    private var facade: RemoteFacade? = null
    private var epgRepo: EpgRepository? = null

    private val _epgChannels = MutableStateFlow<List<ChannelRow>>(emptyList())
    val epgChannels: StateFlow<List<ChannelRow>> = _epgChannels

    private val _epgProgrammes = MutableStateFlow<List<ProgrammeEntity>>(emptyList())
    val epgProgrammes: StateFlow<List<ProgrammeEntity>> = _epgProgrammes

    private val _currentChannel = MutableStateFlow<String?>(null)
    val currentChannel: StateFlow<String?> = _currentChannel

    private val _channelBuffer = MutableStateFlow("")
    val channelBuffer: StateFlow<String> = _channelBuffer

    private var lastKeyAt = 0L
    private var lastKeyName: String? = null

    val needsPhilipsPairing: Boolean
        get() = store.getPhilipsUser().isNullOrBlank()

    val needsAtvPairing: Boolean
        get() = !store.isAtvPaired()

    fun scan() {
        viewModelScope.launch {
            _status.value = "Scanning for TVs…"
            runCatching {
                discovery.discover().collect { list ->
                    _tvs.value = list
                }
            }.onFailure {
                _status.value = "Scan failed: ${it.message}"
            }
            _status.value = if (_tvs.value.isEmpty()) "No TVs found" else "Found ${_tvs.value.size} TV(s)"
        }
    }

    fun selectTv(tv: DiscoveredTv) {
        _selectedHost.value = tv.host
        store.saveDevice(tv.host, tv.name, store.getPhilipsUser(), store.getPhilipsPass(), store.isAtvPaired())
        facade = RemoteFacade(getApplication(), tv.host)
        epgRepo = EpgRepository(getApplication(), facade!!.philipsApi(), store.getXmlTvUrl())
        _status.value = "Selected ${tv.name}"
    }

    fun selectManual(host: String, name: String = host) {
        _selectedHost.value = host
        store.saveDevice(host, name, store.getPhilipsUser(), store.getPhilipsPass(), store.isAtvPaired())
        facade = RemoteFacade(getApplication(), host)
        epgRepo = EpgRepository(getApplication(), facade!!.philipsApi(), store.getXmlTvUrl())
    }

    fun startPhilipsPairing() {
        val f = facade ?: return
        viewModelScope.launch {
            runCatching {
                f.startPhilipsPairing()
                _status.value = "Enter the PIN shown on your TV"
            }.onFailure {
                _status.value = "Philips pairing start failed: ${it.message}"
            }
        }
    }

    fun pairPhilips(pin: String) {
        val f = facade ?: return
        viewModelScope.launch {
            runCatching {
                f.pairPhilips(pin)
                _status.value = "Philips pairing complete"
            }.onFailure {
                _status.value = "Philips pairing failed: ${it.message}"
            }
        }
    }

    fun pairAtv(pin: String) {
        val f = facade ?: return
        viewModelScope.launch {
            runCatching {
                f.pairAtv(pin)
                _status.value = "Android TV pairing complete"
            }.onFailure {
                _status.value = "ATV pairing failed: ${it.message}"
            }
        }
    }

    fun key(name: String) {
        val f = facade ?: return
        val now = System.currentTimeMillis()
        if (name == lastKeyName && now - lastKeyAt < KEY_DEBOUNCE_MS) return
        lastKeyAt = now
        lastKeyName = name
        viewModelScope.launch {
            runCatching { f.sendKey(name) }
                .onFailure { _status.value = it.message ?: "Key failed" }
        }
    }

    fun appendChannelDigit(digit: String) {
        if (digit.length != 1 || !digit[0].isDigit()) return
        val current = _channelBuffer.value
        if (current.length >= 4) return
        _channelBuffer.value = current + digit
    }

    fun clearChannelBuffer() {
        _channelBuffer.value = ""
    }

    fun sendChannelBuffer() {
        val digits = _channelBuffer.value
        if (digits.isEmpty()) return
        val f = facade ?: return
        viewModelScope.launch {
            runCatching {
                f.sendDigitSequence(digits)
                _channelBuffer.value = ""
            }.onFailure { _status.value = it.message ?: "Channel send failed" }
        }
    }

    override fun onCleared() {
        facade?.disconnect()
        super.onCleared()
    }

    fun text(value: String) {
        val f = facade ?: return
        viewModelScope.launch { f.sendText(value) }
    }

    fun powerOff() {
        val f = facade ?: return
        viewModelScope.launch { f.powerOff() }
    }

    fun sources() = shortcut { it.sources() }
    fun watchTv() = shortcut { it.watchTv() }
    fun youtube() = shortcut { it.youtube() }
    fun ambilight() = shortcut { it.ambilightToggle() }
    fun guide() = shortcut { it.guideKey() }

    private fun shortcut(block: suspend (RemoteFacade) -> Unit) {
        val f = facade ?: return
        viewModelScope.launch {
            runCatching { block(f) }.onFailure { _status.value = it.message ?: "Error" }
        }
    }

    fun loadCachedEpg() {
        val repo = epgRepo ?: return
        viewModelScope.launch {
            runCatching {
                repo.trimOversizedCache()
                _epgChannels.value = repo.channels()
                _epgProgrammes.value = repo.programmesForDay(todayStartMillis())
            }
        }
    }

    fun refreshEpg() {
        val repo = epgRepo ?: return
        viewModelScope.launch {
            _status.value = "Loading TV guide…"
            runCatching {
                val count = repo.refresh()
                _epgChannels.value = repo.channels()
                _epgProgrammes.value = repo.programmesForDay(todayStartMillis())
                _status.value = "Loaded $count programmes"
            }.onFailure { error ->
                _status.value = when (error) {
                    is OutOfMemoryError -> "TV guide too large — tap Refresh after choosing a regional source"
                    else -> "EPG failed: ${error.message}"
                }
            }
        }
    }

    private fun todayStartMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun refreshCurrentChannel() {
        val f = facade ?: return
        viewModelScope.launch {
            val json = f.currentChannel()
            _currentChannel.value = json?.let { formatChannel(it) }
        }
    }

    fun setXmlTvUrl(url: String) {
        store.setXmlTvUrl(url)
        facade?.let { f ->
            epgRepo = EpgRepository(getApplication(), f.philipsApi(), url)
        }
    }

    fun recordVoiceAndSend() {
        val f = facade ?: return
        viewModelScope.launch {
            runCatching {
                val pcm = recordPcmSample()
                f.sendVoice(pcm)
            }.onFailure {
                _status.value = "Voice failed: ${it.message}"
            }
        }
    }

    private fun formatChannel(json: JSONObject): String {
        val ch = json.optJSONObject("channel")
        val name = ch?.optString("name", "") ?: ""
        val preset = ch?.optString("preset", "")
        return if (preset.isNullOrBlank()) name else "$preset · $name"
    }

    private fun recordPcmSample(): ByteArray {
        val sampleRate = 8000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        val data = ByteArray(sampleRate * 2)
        recorder.startRecording()
        recorder.read(data, 0, data.size)
        recorder.stop()
        recorder.release()
        return data
    }

    companion object {
        private const val KEY_DEBOUNCE_MS = 400L
    }
}
