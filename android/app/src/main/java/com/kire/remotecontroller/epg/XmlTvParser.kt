package com.kire.remotecontroller.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

object XmlTvParser {
    private val format = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Stream-parse XMLTV without loading the whole file into memory.
     * Only keeps programmes overlapping [windowStartMillis, windowEndMillis], up to [maxProgrammes].
     */
    fun parseStream(
        rawInput: InputStream,
        gzip: Boolean,
        windowStartMillis: Long,
        windowEndMillis: Long,
        maxProgrammes: Int = 2_500,
    ): List<ProgrammeEntity> {
        val stream = if (gzip) GZIPInputStream(rawInput) else rawInput
        return stream.use { input ->
            parseFromStream(input, windowStartMillis, windowEndMillis, maxProgrammes)
        }
    }

    private fun parseFromStream(
        input: InputStream,
        windowStartMillis: Long,
        windowEndMillis: Long,
        maxProgrammes: Int,
    ): List<ProgrammeEntity> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)

        val channels = mutableMapOf<String, String>()
        val programmes = ArrayList<ProgrammeEntity>(minOf(maxProgrammes, 512))
        var event = parser.eventType
        var currentChannelId: String? = null

        while (event != XmlPullParser.END_DOCUMENT && programmes.size < maxProgrammes) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> currentChannelId = parser.getAttributeValue(null, "id")
                    "display-name" -> if (currentChannelId != null) {
                        parser.next()
                        val name = parser.text.orEmpty()
                        if (name.isNotBlank()) {
                            channels[currentChannelId!!] = name
                        }
                    }
                    "programme" -> {
                        val channelId = parser.getAttributeValue(null, "channel") ?: ""
                        val start = parseTime(parser.getAttributeValue(null, "start"))
                        val stop = parseTime(parser.getAttributeValue(null, "stop"))
                        val inWindow = start > 0 && stop > start &&
                            stop >= windowStartMillis && start <= windowEndMillis
                        if (inWindow) {
                            var title = ""
                            var desc = ""
                            val innerDepth = parser.depth
                            while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == innerDepth)) {
                                if (parser.eventType == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        "title" -> {
                                            parser.next()
                                            title = parser.text.orEmpty()
                                        }
                                        "desc" -> {
                                            parser.next()
                                            desc = parser.text.orEmpty()
                                        }
                                    }
                                }
                            }
                            programmes += ProgrammeEntity(
                                channelId = channelId,
                                channelName = channels[channelId] ?: channelId,
                                title = title,
                                startMillis = start,
                                endMillis = stop,
                                description = desc,
                            )
                        } else {
                            skipElement(parser, "programme")
                        }
                    }
                }
            }
            event = parser.next()
        }
        return programmes
    }

    private fun skipElement(parser: XmlPullParser, tag: String) {
        val depth = parser.depth
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG &&
                parser.name == tag &&
                parser.depth == depth
            ) {
                break
            }
        }
    }

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val normalized = if (raw.length > 14) {
            "${raw.substring(0, 14)} ${raw.substring(14).trim()}"
        } else {
            raw
        }
        return runCatching { format.parse(normalized)?.time ?: 0L }.getOrDefault(0L)
    }
}
