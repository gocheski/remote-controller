package com.kire.remotecontroller.epg

object EpgGridBuilder {
    const val MAX_SLOTS_PER_CHANNEL = 6

    fun build(
        programmes: List<ProgrammeEntity>,
        channelNumbers: Map<String, Int>,
        tagNamesByStableKey: Map<String, List<String>>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<ChannelGuideRow> {
        val byChannel = programmes.groupBy { it.channelId }
        return byChannel.map { (channelId, items) ->
            val channelName = items.firstOrNull()?.channelName ?: channelId
            val number = channelNumbers[channelId]
                ?: channelNumbers[channelName]
                ?: parseLeadingNumber(channelName)
            val slots = items
                .filter { it.endMillis > nowMillis }
                .sortedBy { it.startMillis }
                .take(MAX_SLOTS_PER_CHANNEL)
                .map { p ->
                    ProgrammeSlot(
                        programmeId = p.id,
                        stableKey = p.stableKey.ifBlank { ProgrammeKeys.stableKey(p) },
                        title = p.title,
                        startMillis = p.startMillis,
                        endMillis = p.endMillis,
                        categories = p.categories,
                        description = p.description,
                        tagNames = tagNamesByStableKey[p.stableKey.ifBlank { ProgrammeKeys.stableKey(p) }]
                            ?: emptyList(),
                    )
                }
            ChannelGuideRow(
                channelId = channelId,
                channelNumber = number,
                channelName = channelName,
                slots = slots,
            )
        }.sortedWith(
            compareBy<ChannelGuideRow> { it.channelNumber ?: Int.MAX_VALUE }
                .thenBy { it.channelName.lowercase() },
        )
    }

    private fun parseLeadingNumber(name: String): Int? {
        val match = Regex("""^(\d{1,3})[\.\s\-]""").find(name.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()
    }
}

object ProgrammeKeys {
    fun stableKey(p: ProgrammeEntity): String =
        "${p.channelId}|${p.startMillis}|${p.title.trim().lowercase()}".hashCode().toUInt().toString(16)
}
