package com.kire.remotecontroller.epg

object GenreMatcher {
    fun matches(programme: ProgrammeEntity, filter: GenreFilter): Boolean {
        if (filter == GenreFilter.ALL) return true
        val haystack = buildString {
            append(programme.categories)
            append(' ')
            append(programme.title)
            append(' ')
            append(programme.description)
        }.lowercase()
        return when (filter) {
            GenreFilter.ALL -> true
            GenreFilter.SPORT -> containsAny(haystack, SPORT_WORDS)
            GenreFilter.MUSIC -> containsAny(haystack, MUSIC_WORDS)
            GenreFilter.MOVIES -> containsAny(haystack, MOVIE_WORDS)
            GenreFilter.GENERAL -> !containsAny(haystack, SPORT_WORDS + MUSIC_WORDS + MOVIE_WORDS)
        }
    }

    private fun containsAny(text: String, words: List<String>): Boolean =
        words.any { text.contains(it) }

    private val SPORT_WORDS = listOf(
        "sport", "football", "soccer", "basketball", "tennis", "formula",
        "olympic", "match", "liga", "uefa", "fifa", "nba", "nfl",
    )
    private val MUSIC_WORDS = listOf(
        "music", "concert", "festival", "mtv", "hits", "song", "karaoke",
        "opera", "jazz", "rock",
    )
    private val MOVIE_WORDS = listOf(
        "movie", "film", "cinema", "drama", "thriller", "horror",
        "series", "episode", "premiere",
    )
}
