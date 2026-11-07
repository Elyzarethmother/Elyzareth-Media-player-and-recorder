package com.example.data

import com.example.data.model.Song
import com.example.data.model.SongGroup
import java.util.regex.Pattern

object SongGrouper {
    // Matches version descriptors like (Acoustic), [Piano Version], - Instrumental, etc.
    private val VERSION_REGEX = Pattern.compile(
        "\\s*[\\[\\(](Acoustic|Pop|Piano|Reggae|Instrumental|Remix|Live|Radio Edit|Extended|Orchestral|Arrangement|Version\\s*\\d+|Original)[\\)\\]]",
        Pattern.CASE_INSENSITIVE
    )

    private val HYPHEN_VERSION_REGEX = Pattern.compile(
        "\\s*-\\s*(Acoustic|Pop|Piano|Reggae|Instrumental|Remix|Live|Radio Edit|Extended|Orchestral|Original)$",
        Pattern.CASE_INSENSITIVE
    )

    fun parseSongInfo(rawTitle: String, fallbackArtist: String): Pair<String, String> {
        var title = rawTitle.trim()

        // Check parenthetical version
        val parentheticalMatcher = VERSION_REGEX.matcher(title)
        if (parentheticalMatcher.find()) {
            val versionName = parentheticalMatcher.group(1)?.trim() ?: "Original"
            val baseTitle = title.substring(0, parentheticalMatcher.start()).trim()
            if (baseTitle.isNotEmpty()) {
                return Pair(cleanTitle(baseTitle), versionName)
            }
        }

        // Check hyphenated version
        val hyphenMatcher = HYPHEN_VERSION_REGEX.matcher(title)
        if (hyphenMatcher.find()) {
            val versionName = hyphenMatcher.group(1)?.trim() ?: "Original"
            val baseTitle = title.substring(0, hyphenMatcher.start()).trim()
            if (baseTitle.isNotEmpty()) {
                return Pair(cleanTitle(baseTitle), versionName)
            }
        }

        return Pair(cleanTitle(title), "Original")
    }

    private fun cleanTitle(title: String): String {
        var cleaned = title
        if (cleaned.startsWith("Elyzareth - ", ignoreCase = true)) {
            cleaned = cleaned.substring("Elyzareth - ".length).trim()
        }
        return cleaned.ifEmpty { "Unknown Track" }
    }

    fun groupSongs(songs: List<Song>): List<SongGroup> {
        // Group by base title and artist, preserving all original files and versions
        val groupsMap = LinkedHashMap<String, MutableList<Song>>()

        for (song in songs) {
            val key = "${song.artist.lowercase()}_${song.songTitle.lowercase()}"
            groupsMap.getOrPut(key) { mutableListOf() }.add(song)
        }

        return groupsMap.values.map { versionList ->
            val first = versionList.first()
            SongGroup(
                baseTitle = first.songTitle,
                artist = first.artist,
                versions = versionList
            )
        }
    }
}
