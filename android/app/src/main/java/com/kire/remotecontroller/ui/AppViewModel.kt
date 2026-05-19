package com.kire.remotecontroller.ui

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kire.remotecontroller.RemoteFacade
import com.kire.remotecontroller.data.DeviceStore
import com.kire.remotecontroller.discovery.DiscoveredTv
import com.kire.remotecontroller.discovery.NsdDiscovery
import com.kire.remotecontroller.epg.ChannelGuideRow
import com.kire.remotecontroller.epg.EpgRepository
import com.kire.remotecontroller.epg.GenreFilter
import com.kire.remotecontroller.epg.GenreMatcher
import com.kire.remotecontroller.epg.EpgGridBuilder
import com.kire.remotecontroller.epg.ProgrammeEntity
import com.kire.remotecontroller.epg.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = DeviceStore(app)
    private val discovery = NsdDiscovery(app)

    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _selectedHost = MutableStateFlow<String?>(store.getHost())
    val selectedHost: StateFlow<String?> = _selectedHost

    private val _tvName = MutableStateFlow<String?>(store.getName() ?: store.getHost())
    val tvName: StateFlow<String?> = _tvName

    private var facade: RemoteFacade? = null
    private var epgRepo: EpgRepository? = null

    private val _epgProgrammes = MutableStateFlow<List<ProgrammeEntity>>(emptyList())
    val epgProgrammes: StateFlow<List<ProgrammeEntity>> = _epgProgrammes

    private val _epgGridRows = MutableStateFlow<List<ChannelGuideRow>>(emptyList())
    val epgGridRows: StateFlow<List<ChannelGuideRow>> = _epgGridRows

    private val _userTags = MutableStateFlow<List<TagEntity>>(emptyList())
    val userTags: StateFlow<List<TagEntity>> = _userTags

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _genreFilter = MutableStateFlow(GenreFilter.ALL)
    val genreFilter: StateFlow<GenreFilter> = _genreFilter

    private val _selectedTagId = MutableStateFlow<Long?>(null)
    val selectedTagId: StateFlow<Long?> = _selectedTagId

    private val _currentChannel = MutableStateFlow<String?>(null)
    val currentChannel: StateFlow<String?> = _currentChannel

    private val _channelBuffer = MutableStateFlow("")
    val channelBuffer: StateFlow<String> = _channelBuffer

    private var channelNumberMap = emptyMap<String, Int>()
    private var lastKeyAt = 0L
    private var lastKeyName: String? = null

    val needsPhilipsPairing: Boolean
        get() = store.getPhilipsUser().isNullOrBlank()

    val needsAtvPairing: Boolean
        get() = !store.isAtvPaired()

    val canAutoResume: Boolean
        get() = !store.getHost().isNullOrBlank() && !needsPhilipsPairing

    val xmlTvUrl: String
        get() = store.getXmlTvUrl() ?: EpgRepository.DEFAULT_XMLTV

    fun ensureFacade(): RemoteFacade {
        val host = _selectedHost.value ?: store.getHost()
            ?: error("No TV selected")
        val existing = facade
        if (existing != null && existing.host == host) return existing
        val created = RemoteFacade(getApplication(), host)
        facade = created
        return created
    }

    private suspend fun epgRepoOrNull(): EpgRepository? = withContext(Dispatchers.IO) {
        val f = facade ?: return@withContext null
        epgRepo ?: EpgRepository(getApplication(), f.philipsApi(), store.getXmlTvUrl()).also {
            epgRepo = it
        }
    }

    fun onPermissionsReady() {
        if (_selectedHost.value == null) {
            store.getHost()?.let { host ->
                _selectedHost.value = host
                _tvName.value = store.getName() ?: host
            }
        }
        scan()
    }

    fun scan() {
        viewModelScope.launch {
            _status.value = "Scanning for TVs…"
            runCatching {
                val list = discovery.discover().first()
                _tvs.value = list
            }.onFailure {
                Log.w(TAG, "Scan failed", it)
                _status.value = "Scan failed: ${it.message}"
            }
            _status.value = if (_tvs.value.isEmpty()) "No TVs found" else "Found ${_tvs.value.size} TV(s)"
        }
    }

    fun selectTv(tv: DiscoveredTv) {
        _selectedHost.value = tv.host
        _tvName.value = tv.name
        store.saveDevice(tv.host, tv.name, store.getPhilipsUser(), store.getPhilipsPass(), store.isAtvPaired())
        facade = RemoteFacade(getApplication(), tv.host)
        epgRepo = null
        _status.value = "Selected ${tv.name}"
    }

    fun selectManual(host: String, name: String = host) {
        _selectedHost.value = host
        _tvName.value = name
        store.saveDevice(host, name, store.getPhilipsUser(), store.getPhilipsPass(), store.isAtvPaired())
        facade = RemoteFacade(getApplication(), host)
        epgRepo = null
    }

    fun updateTvHost(host: String) {
        val trimmed = host.trim()
        if (trimmed.isBlank()) return
        val name = _tvName.value ?: trimmed
        selectManual(trimmed, name)
        _status.value = "TV IP updated"
    }

    fun testConnection() {
        viewModelScope.launch {
            _status.value = "Testing connection…"
            runCatching {
                val f = ensureFacade()
                val ch = f.currentChannel()
                _status.value = if (ch != null) "Connected to TV" else "TV reachable (no channel info)"
            }.onFailure {
                _status.value = "Connection failed: ${it.message}"
            }
        }
    }

    fun startPhilipsPairing() {
        viewModelScope.launch {
            runCatching {
                ensureFacade().startPhilipsPairing()
                _status.value = "Enter the PIN shown on your TV"
            }.onFailure {
                _status.value = "Philips pairing start failed: ${it.message}"
            }
        }
    }

    fun pairPhilips(pin: String) {
        viewModelScope.launch {
            runCatching {
                ensureFacade().pairPhilips(pin)
                _status.value = "Philips pairing complete"
            }.onFailure {
                _status.value = "Philips pairing failed: ${it.message}"
            }
        }
    }

    fun startAtvPairing() {
        viewModelScope.launch {
            runCatching {
                ensureFacade().startAtvPairing()
                _status.value = "Enter the 6-character code shown on your TV"
            }.onFailure {
                _status.value = "ATV pairing start failed: ${it.message}"
            }
        }
    }

    fun pairAtv(pin: String) {
        viewModelScope.launch {
            runCatching {
                ensureFacade().pairAtv(pin)
                _status.value = "Android TV pairing complete"
            }.onFailure {
                _status.value = "ATV pairing failed: ${it.message}"
            }
        }
    }

    fun key(name: String) {
        val now = System.currentTimeMillis()
        if (name == lastKeyName && now - lastKeyAt < KEY_DEBOUNCE_MS) return
        lastKeyAt = now
        lastKeyName = name
        viewModelScope.launch {
            runCatching { ensureFacade().sendKey(name) }
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
        viewModelScope.launch {
            runCatching {
                ensureFacade().sendDigitSequence(digits)
                _channelBuffer.value = ""
            }.onFailure { _status.value = it.message ?: "Channel send failed" }
        }
    }

    override fun onCleared() {
        facade?.disconnect()
        super.onCleared()
    }

    fun text(value: String) {
        viewModelScope.launch { ensureFacade().sendText(value) }
    }

    fun powerOff() {
        viewModelScope.launch { ensureFacade().powerOff() }
    }

    fun sources() = shortcut { it.sources() }
    fun watchTv() = shortcut { it.watchTv() }
    fun youtube() = shortcut { it.youtube() }
    fun ambilight() = shortcut { it.ambilightToggle() }
    fun guide() = shortcut { it.guideKey() }
    fun tvSettings() = shortcut { it.tvSettings() }

    private fun shortcut(block: suspend (RemoteFacade) -> Unit) {
        viewModelScope.launch {
            runCatching { block(ensureFacade()) }.onFailure { _status.value = it.message ?: "Error" }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch { applyEpgFilters() }
    }

    fun setGenreFilter(filter: GenreFilter) {
        _genreFilter.value = filter
        viewModelScope.launch { applyEpgFilters() }
    }

    fun setSelectedTagId(tagId: Long?) {
        _selectedTagId.value = tagId
        viewModelScope.launch { applyEpgFilters() }
    }

    fun loadCachedEpg() {
        viewModelScope.launch {
            val repo = epgRepoOrNull() ?: return@launch
            runCatching {
                repo.trimOversizedCache()
                loadChannelNumbers()
                _epgProgrammes.value = repo.programmesForDay(todayStartMillis())
                _userTags.value = repo.allTags()
                rebuildGrid()
            }.onFailure {
                Log.e(TAG, "loadCachedEpg failed", it)
                _status.value = "Guide cache error — clear guide in Settings"
            }
        }
    }

    fun refreshEpg() {
        viewModelScope.launch {
            val repo = epgRepoOrNull() ?: run {
                _status.value = "Select a TV first"
                return@launch
            }
            _status.value = "Loading TV guide…"
            runCatching {
                val count = repo.refresh()
                loadChannelNumbers()
                _epgProgrammes.value = repo.programmesForDay(todayStartMillis())
                _userTags.value = repo.allTags()
                rebuildGrid()
                _status.value = "Loaded $count programmes"
            }.onFailure { error ->
                Log.e(TAG, "refreshEpg failed", error)
                _status.value = when (error) {
                    is OutOfMemoryError -> "TV guide too large — pick a regional source in Settings"
                    else -> "EPG failed: ${error.message}"
                }
            }
        }
    }

    fun addTagToProgramme(stableKey: String, tagName: String) {
        viewModelScope.launch {
            val repo = epgRepoOrNull() ?: return@launch
            runCatching {
                repo.addTagToProgramme(stableKey, tagName)
                _userTags.value = repo.allTags()
                rebuildGrid()
            }.onFailure {
                _status.value = "Tag failed: ${it.message}"
            }
        }
    }

    fun removeTagFromProgramme(stableKey: String, tagName: String) {
        viewModelScope.launch {
            val repo = epgRepoOrNull() ?: return@launch
            runCatching {
                repo.removeTagFromProgramme(stableKey, tagName)
                rebuildGrid()
            }
        }
    }

    private suspend fun loadChannelNumbers() {
        channelNumberMap = runCatching { ensureFacade().fetchChannelNumberMap() }.getOrDefault(emptyMap())
    }

    private suspend fun rebuildGrid() {
        val repo = epgRepoOrNull() ?: return
        val tagMap = repo.tagNamesByStableKey()
        val allRows = EpgGridBuilder.build(_epgProgrammes.value, channelNumberMap, tagMap)
        _epgGridRows.value = filterRows(allRows, repo)
    }

    private suspend fun applyEpgFilters() {
        val repo = epgRepoOrNull() ?: return
        val tagMap = repo.tagNamesByStableKey()
        val allRows = EpgGridBuilder.build(_epgProgrammes.value, channelNumberMap, tagMap)
        _epgGridRows.value = filterRows(allRows, repo)
    }

    private suspend fun filterRows(rows: List<ChannelGuideRow>, repo: EpgRepository): List<ChannelGuideRow> {
        val query = _searchQuery.value.trim().lowercase()
        val genre = _genreFilter.value
        val tagId = _selectedTagId.value
        val tagStableKeys = if (tagId != null) repo.stableKeysForTag(tagId) else null

        return rows.mapNotNull { row ->
            val filteredSlots = row.slots.filter { slot ->
                val programme = _epgProgrammes.value.find { it.id == slot.programmeId }
                    ?: ProgrammeEntity(
                        id = slot.programmeId,
                        channelId = row.channelId,
                        channelName = row.channelName,
                        title = slot.title,
                        startMillis = slot.startMillis,
                        endMillis = slot.endMillis,
                        description = slot.description,
                        categories = slot.categories,
                        stableKey = slot.stableKey,
                    )
                val genreOk = GenreMatcher.matches(programme, genre)
                val tagOk = tagStableKeys == null || slot.stableKey in tagStableKeys
                val searchOk = query.isBlank() ||
                    row.channelName.lowercase().contains(query) ||
                    slot.title.lowercase().contains(query)
                genreOk && tagOk && searchOk
            }
            if (filteredSlots.isEmpty()) return@mapNotNull null
            val channelMatches = query.isNotBlank() && row.channelName.lowercase().contains(query)
            if (query.isNotBlank() && !channelMatches && filteredSlots.isEmpty()) return@mapNotNull null
            row.copy(slots = if (channelMatches) row.slots else filteredSlots)
        }
    }

    private fun todayStartMillis(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun refreshCurrentChannel() {
        viewModelScope.launch {
            val json = runCatching { ensureFacade().currentChannel() }.getOrNull()
            _currentChannel.value = json?.let { formatChannel(it) }
        }
    }

    fun setXmlTvUrl(url: String) {
        store.setXmlTvUrl(url)
        epgRepo = null
    }

    fun clearEpgCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    epgRepo?.clearGuideCache()
                    EpgRepository.clearDatabase(getApplication())
                }
                epgRepo = null
                _epgProgrammes.value = emptyList()
                _epgGridRows.value = emptyList()
                _status.value = "TV guide cache cleared"
            }.onFailure {
                _status.value = "Clear failed: ${it.message}"
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            facade?.disconnect()
            facade = null
            epgRepo = null
            store.clearAll()
            EpgRepository.clearDatabase(getApplication())
            _selectedHost.value = null
            _tvName.value = null
            _epgProgrammes.value = emptyList()
            _epgGridRows.value = emptyList()
            _userTags.value = emptyList()
            _status.value = "All app data cleared"
        }
    }

    fun clearPairingOnly() {
        viewModelScope.launch {
            store.clearPairing()
            _status.value = "Pairing cleared — pair again in Settings"
        }
    }

    fun recordVoiceAndSend() {
        viewModelScope.launch {
            runCatching {
                val pcm = recordPcmSample()
                ensureFacade().sendVoice(pcm)
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
        private const val TAG = "RemoteController"
        private const val KEY_DEBOUNCE_MS = 400L
    }
}
