package com.kire.remotecontroller.epg

import android.content.Context
import com.kire.remotecontroller.philips.PhilipsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EpgRepository(
    context: Context,
    private val philips: PhilipsApi,
    private val xmlTvUrl: String?,
) {
    private val dao = EpgDatabase.get(context).epgDao()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        trimOversizedCache()
        val windowStart = System.currentTimeMillis() - 2 * 60 * 60 * 1000
        val windowEnd = windowStart + 36 * 60 * 60 * 1000

        val programmes = loadFromTvProbe().ifEmpty {
            val url = resolveXmlTvUrl(xmlTvUrl)
            loadFromXmlTv(url, windowStart, windowEnd)
        }

        dao.clear()
        programmes.chunked(INSERT_BATCH).forEach { batch ->
            dao.insertAll(batch)
        }
        programmes.size
    }

    suspend fun channels() = dao.channels()

    suspend fun programmesForDay(dayStartMillis: Long): List<ProgrammeEntity> {
        val dayEnd = dayStartMillis + 24 * 60 * 60 * 1000
        return dao.programmesBetween(dayStartMillis, dayEnd, MAX_DISPLAY)
    }

    suspend fun trimOversizedCache() {
        val count = dao.count()
        if (count > MAX_PROGRAMMES) dao.clear()
    }

    private fun loadFromXmlTv(url: String, windowStart: Long, windowEnd: Long): List<ProgrammeEntity> {
        val request = Request.Builder().url(url).get().build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("XMLTV download failed (${response.code})")
            val body = response.body ?: error("Empty XMLTV body")
            val gzip = url.endsWith(".gz", ignoreCase = true)
            XmlTvParser.parseStream(
                rawInput = body.byteStream(),
                gzip = gzip,
                windowStartMillis = windowStart,
                windowEndMillis = windowEnd,
                maxProgrammes = MAX_PROGRAMMES,
            )
        }
    }

    private suspend fun loadFromTvProbe(): List<ProgrammeEntity> {
        if (!philips.isPaired) return emptyList()
        val paths = listOf(
            "recordings/list",
            "tv/search",
            "epg/list",
            "nettv/epg",
        )
        for (path in paths) {
            val raw = philips.probePath(path) ?: continue
            if (raw.length > MAX_PROBE_JSON_CHARS) continue
            val parsed = parseTvJson(raw)
            if (parsed.isNotEmpty()) return parsed.take(MAX_PROGRAMMES)
        }
        return emptyList()
    }

    private fun parseTvJson(raw: String): List<ProgrammeEntity> {
        return runCatching {
            val json = JSONObject(raw)
            when {
                json.has("recordings") -> parseRecordings(json)
                json.has("items") -> parseItems(json.getJSONArray("items"))
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun parseRecordings(json: JSONObject): List<ProgrammeEntity> {
        val list = json.optJSONArray("recordings") ?: return emptyList()
        return parseItems(list)
    }

    private fun parseItems(array: org.json.JSONArray): List<ProgrammeEntity> {
        val result = mutableListOf<ProgrammeEntity>()
        val limit = minOf(array.length(), MAX_PROGRAMMES)
        for (i in 0 until limit) {
            val item = array.optJSONObject(i) ?: continue
            val title = item.optString("title", item.optString("name", ""))
            val channel = item.optString("channel", item.optString("channelName", "TV"))
            val start = item.optLong("start", item.optLong("startTime", 0L))
            val end = item.optLong("end", item.optLong("endTime", start + 3_600_000))
            if (title.isNotBlank() && start > 0) {
                result += ProgrammeEntity(
                    channelId = channel,
                    channelName = channel,
                    title = title,
                    startMillis = start,
                    endMillis = end,
                    description = item.optString("desc", ""),
                )
            }
        }
        return result
    }

    companion object {
        /** Regional XMLTV — stream-filtered to the next ~36h (not the full file). */
        const val DEFAULT_XMLTV = "https://www.open-epg.com/files/macedonia1.xml"

        const val MAX_PROGRAMMES = 2_500
        const val MAX_DISPLAY = 800
        private const val INSERT_BATCH = 400
        private const val MAX_PROBE_JSON_CHARS = 2_000_000

        fun resolveXmlTvUrl(stored: String?): String {
            if (stored.isNullOrBlank()) return DEFAULT_XMLTV
            if (stored.contains("epgshare", ignoreCase = true) || stored.endsWith(".gz", ignoreCase = true)) {
                return DEFAULT_XMLTV
            }
            return stored
        }

        val PRESET_URLS = listOf(
            DEFAULT_XMLTV to "Open-EPG North Macedonia (recommended)",
            "https://www.open-epg.com/files/serbia1.xml" to "Open-EPG Serbia",
            "https://www.open-epg.com/files/greece1.xml" to "Open-EPG Greece",
            "https://www.open-epg.com/files/albania1.xml" to "Open-EPG Albania",
            "https://www.open-epg.com/files/bulgaria1.xml" to "Open-EPG Bulgaria",
        )
    }
}
