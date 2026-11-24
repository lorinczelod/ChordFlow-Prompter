package com.chordflow.prompter.core

/**
 * Parses the three chord-sheet formats worship leaders actually have on their phones:
 *
 *  1. ChordPro directives      {title: Amazing Grace} {key: G} {artist: ...} {tempo: 84}
 *  2. Inline bracketed chords  [G]Amazing [D]grace, how [Em]sweet the [C]sound
 *  3. Chord-over-lyric text    G        D        Em       C
 *                              Amazing grace, how sweet the sound
 *
 * The teleprompter shows [SongLine.lyric] only — chords never reach the screen.
 * [SongLine.chords] carries the hidden triggers the audio engine matches against.
 */
object ChordParser {

    data class SongLine(
        val lyric: String,
        val chords: List<String>,
        val isSection: Boolean = false,
        val isBlank: Boolean = false
    )

    data class ParsedSong(
        val title: String,
        val artist: String,
        val key: String,
        val tempo: Int,
        val lines: List<SongLine>
    ) {
        /** All chords in order of appearance, de-duplicated consecutively. */
        val chordProgression: List<String>
            get() {
                val out = mutableListOf<String>()
                lines.forEach { line ->
                    line.chords.forEach { c -> if (out.lastOrNull() != c) out.add(c) }
                }
                return out
            }
    }

    private val DIRECTIVE = Regex("^\\s*\\{\\s*([a-zA-Z_]+)\\s*:?\\s*(.*?)\\s*}\\s*$")
    private val INLINE_CHORD = Regex("\\[([^\\]]{1,12})]")
    private val SECTION_HINT = Regex(
        "^\\s*\\(?\\s*(verse|chorus|bridge|intro|outro|tag|pre-?chorus|refrain|interlude|instrumental|ending|coda|vamp|turnaround)\\b.*",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): ParsedSong {
        var title = ""
        var artist = ""
        var key = ""
        var tempo = 0

        val sourceLines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        val staged = mutableListOf<SongLine>()

        var i = 0
        while (i < sourceLines.size) {
            val line = sourceLines[i]

            // --- 1. ChordPro directive ---------------------------------------
            val directive = DIRECTIVE.find(line)
            if (directive != null) {
                val name = directive.groupValues[1].lowercase()
                val value = directive.groupValues[2].trim()
                when (name) {
                    "title", "t" -> if (title.isEmpty()) title = value
                    "subtitle", "st", "artist", "composer" -> if (artist.isEmpty()) artist = value
                    "key" -> if (key.isEmpty()) key = value
                    "tempo", "bpm" -> tempo = value.filter { it.isDigit() }.toIntOrNull() ?: tempo
                    "comment", "c", "comment_italic", "ci" ->
                        if (value.isNotEmpty()) staged.add(SongLine(value, emptyList(), isSection = true))
                    "start_of_chorus", "soc" -> staged.add(SongLine("CHORUS", emptyList(), isSection = true))
                    "start_of_verse", "sov" -> staged.add(SongLine("VERSE", emptyList(), isSection = true))
                    "start_of_bridge", "sob" -> staged.add(SongLine("BRIDGE", emptyList(), isSection = true))
                    // end_of_* and anything unknown: drop silently.
                }
                i++
                continue
            }

            // --- 2. Blank line -----------------------------------------------
            if (line.isBlank()) {
                if (staged.isNotEmpty() && !staged.last().isBlank) {
                    staged.add(SongLine("", emptyList(), isBlank = true))
                }
                i++
                continue
            }

            // --- 3. Inline bracketed chords ----------------------------------
            if (INLINE_CHORD.containsMatchIn(line)) {
                val chords = INLINE_CHORD.findAll(line)
                    .map { it.groupValues[1].trim() }
                    .filter { ChordTransposer.isChordToken(it) }
                    .toList()
                val lyric = INLINE_CHORD.replace(line, "").trim()
                if (lyric.isEmpty() && chords.isNotEmpty()) {
                    // A pure chord line written in ChordPro brackets — e.g. "[G] [D] [Em] [C]"
                    staged.add(SongLine("", chords, isBlank = true))
                } else {
                    staged.add(SongLine(lyric, chords, isSection = isSectionLabel(lyric)))
                }
                i++
                continue
            }

            // --- 4. Chord-over-lyric -----------------------------------------
            if (isChordLine(line)) {
                val chords = line.trim().split(Regex("\\s+"))
                    .filter { ChordTransposer.isChordToken(it) }
                // Attach the chords to the next non-blank, non-chord line.
                var j = i + 1
                while (j < sourceLines.size && sourceLines[j].isBlank()) j++
                if (j < sourceLines.size && !isChordLine(sourceLines[j]) &&
                    !DIRECTIVE.matches(sourceLines[j])
                ) {
                    val lyric = sourceLines[j].trim()
                    staged.add(SongLine(lyric, chords, isSection = isSectionLabel(lyric)))
                    i = j + 1
                } else {
                    // Instrumental run with no lyric underneath.
                    staged.add(SongLine("", chords, isBlank = true))
                    i++
                }
                continue
            }

            // --- 5. Plain lyric ----------------------------------------------
            val text = line.trim()
            staged.add(SongLine(text, emptyList(), isSection = isSectionLabel(text)))
            i++
        }

        // Trim leading/trailing blanks.
        while (staged.isNotEmpty() && staged.first().isBlank) staged.removeAt(0)
        while (staged.isNotEmpty() && staged.last().isBlank) staged.removeAt(staged.size - 1)

        if (title.isEmpty()) {
            title = staged.firstOrNull { it.lyric.isNotBlank() && !it.isSection }?.lyric?.take(60) ?: "Untitled"
        }
        if (key.isEmpty()) {
            // Auto-detect from the first chord in the song.
            val first = staged.firstOrNull { it.chords.isNotEmpty() }?.chords?.firstOrNull()
            if (first != null) {
                val parsed = ChordTransposer.parse(first)
                if (parsed != null) {
                    val name = ChordTransposer.pitchClassToName(parsed.root, useFlats = false)
                    key = if (parsed.isMinor) name + "m" else name
                }
            }
        }
        if (key.isEmpty()) key = "C"

        return ParsedSong(title, artist, key, tempo, staged)
    }

    private fun isSectionLabel(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.startsWith("[") && t.endsWith("]")) return true
        if (t.endsWith(":") && t.length <= 24) return true
        return SECTION_HINT.matches(t) && t.length <= 32
    }

    /**
     * A line is a chord line when it holds at least one chord and every
     * whitespace-separated token is a chord, a bar line, or a rhythm mark.
     */
    fun isChordLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size > 24) return false
        var chordCount = 0
        for (t in tokens) {
            if (t == "|" || t == "||" || t == "/" || t == "%" || t == "-" || t == "x2" || t == "x3" || t == "x4") continue
            if (ChordTransposer.isChordToken(t)) chordCount++ else return false
        }
        return chordCount > 0
    }

    /**
     * Returns the song's lines with every chord transposed by [semitones].
     * The lyric text is untouched — only the hidden triggers move.
     */
    fun transposed(song: ParsedSong, semitones: Int): ParsedSong {
        if (semitones == 0) return song
        val useFlats = ChordTransposer.useFlatsForKey(song.key, semitones)
        val moved = song.lines.map { line ->
            line.copy(chords = line.chords.map { ChordTransposer.transposeChord(it, semitones, useFlats) })
        }
        return song.copy(lines = moved)
    }

    /**
     * Re-render the chart as readable ChordPro-style text with the chords transposed.
     * Used by the Song Editor preview, never by the teleprompter.
     */
    fun renderWithChords(song: ParsedSong, semitones: Int): String {
        val moved = transposed(song, semitones)
        val sb = StringBuilder()
        moved.lines.forEach { line ->
            when {
                line.isBlank && line.chords.isEmpty() -> sb.append('\n')
                line.isBlank -> sb.append(line.chords.joinToString("  ")).append('\n')
                line.chords.isEmpty() -> sb.append(line.lyric).append('\n')
                else -> {
                    sb.append(line.chords.joinToString("  ")).append('\n')
                    sb.append(line.lyric).append('\n')
                }
            }
        }
        return sb.toString().trimEnd()
    }
}
