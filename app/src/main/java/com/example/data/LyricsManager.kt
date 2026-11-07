package com.example.data

import com.example.data.model.LyricLine
import java.io.File
import java.util.regex.Pattern

object LyricsManager {
    private val LRC_LINE_PATTERN = Pattern.compile("^\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)$")

    fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val reader = content.lineSequence()

        for (rawLine in reader) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matcher = LRC_LINE_PATTERN.matcher(line)
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msPart = matcher.group(3)
                val ms = when {
                    msPart == null -> 0L
                    msPart.length == 2 -> (msPart.toLongOrNull() ?: 0L) * 10
                    else -> (msPart.toLongOrNull() ?: 0L)
                }
                val timestampMs = (min * 60 + sec) * 1000 + ms
                val text = matcher.group(4)?.trim() ?: ""
                lines.add(LyricLine(timestampMs, text))
            }
        }
        return lines.sortedBy { it.timestampMs }
    }

    fun loadSidecarLyricsForFile(audioFilePath: String?): Pair<List<LyricLine>, String?> {
        if (audioFilePath.isNullOrBlank()) return Pair(emptyList(), null)
        return try {
            val audioFile = File(audioFilePath)
            val parent = audioFile.parentFile ?: return Pair(emptyList(), null)
            val baseName = audioFile.nameWithoutExtension
            val lrcFile = File(parent, "$baseName.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                val content = lrcFile.readText()
                val synced = parseLrc(content)
                if (synced.isNotEmpty()) {
                    return Pair(synced, null)
                }
                return Pair(emptyList(), content)
            }
            val txtFile = File(parent, "$baseName.txt")
            if (txtFile.exists() && txtFile.canRead()) {
                val content = txtFile.readText()
                return Pair(emptyList(), content)
            }
            Pair(emptyList(), null)
        } catch (_: Exception) {
            Pair(emptyList(), null)
        }
    }

    fun getActiveLyricIndex(syncedLyrics: List<LyricLine>, currentPositionMs: Long): Int {
        if (syncedLyrics.isEmpty()) return -1
        var activeIndex = -1
        for (i in syncedLyrics.indices) {
            if (currentPositionMs >= syncedLyrics[i].timestampMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
