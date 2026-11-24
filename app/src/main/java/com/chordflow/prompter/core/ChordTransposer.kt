package com.chordflow.prompter.core

/**
 * Chord transposition engine.
 *
 * Handles:
 *  - semitone shifting from -11 to +11
 *  - target key selection (computes the shift for you)
 *  - slash chords:  G/B  ->  F/A
 *  - extensions:    Csus4, Am7, Gmaj7, F#m7b5, Bbadd9, Dsus2/F#
 *  - enharmonic spelling: a sharp key spells A#, a flat key spells Bb
 */
object ChordTransposer {

    val SHARP_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val FLAT_NAMES = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** The 12 keys offered in the Target Key dropdown, in the spelling worship charts normally use. */
    val TARGET_KEYS = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    /** Keys that are conventionally written with flats. */
    private val FLAT_KEYS = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb",
        "Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm", "Abm")

    private val NOTE_BASE = mapOf(
        'C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11
    )

    /**
     * Parsed chord symbol. [root] and [bass] are pitch classes 0..11; -1 means absent.
     * [quality] is everything between the root letter and the slash, e.g. "m7", "sus4", "maj7".
     */
    data class ParsedChord(
        val root: Int,
        val quality: String,
        val bass: Int,
        val original: String
    ) {
        val isMinor: Boolean get() = quality.startsWith("m") && !quality.startsWith("maj")
    }

    /** Pitch class of a note name such as "C", "F#", "Bb", "Cb". Returns -1 if not a note. */
    fun noteToPitchClass(name: String): Int {
        if (name.isEmpty()) return -1
        val base = NOTE_BASE[name[0].uppercaseChar()] ?: return -1
        var pc = base
        for (i in 1 until name.length) {
            when (name[i]) {
                '#' -> pc += 1
                'b', 'B' -> if (i >= 1) pc -= 1 else return -1
                else -> return -1
            }
        }
        return ((pc % 12) + 12) % 12
    }

    /** Spell a pitch class, using flats when the destination key is a flat key. */
    fun pitchClassToName(pc: Int, useFlats: Boolean): String {
        val i = ((pc % 12) + 12) % 12
        return if (useFlats) FLAT_NAMES[i] else SHARP_NAMES[i]
    }

    /** True when chords in [key] should be spelled with flats. */
    fun keyUsesFlats(key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        if (FLAT_KEYS.contains(k)) return true
        // "Ebm", "Bbmaj" etc. — if the root itself carries a flat, keep flats.
        return k.length > 1 && k[1] == 'b'
    }

    /**
     * Parse a single chord token. Returns null when the token is not a chord
     * (so the parser can tell lyrics from chord lines).
     */
    fun parse(token: String): ParsedChord? {
        val raw = token.trim()
        if (raw.isEmpty()) return null
        // Bar lines, repeats and rhythm marks are not chords but are legal inside chord lines.
        if (raw == "|" || raw == "||" || raw == "/" || raw == "%" || raw == "-") return null

        val slash = raw.indexOf('/')
        val main = if (slash >= 0) raw.substring(0, slash) else raw
        val bassPart = if (slash >= 0) raw.substring(slash + 1) else ""

        if (main.isEmpty()) return null
        val letter = main[0]
        if (NOTE_BASE[letter.uppercaseChar()] == null) return null
        // Only an uppercase letter starts a chord; "a", "d" etc. are words.
        if (!letter.isUpperCase()) return null

        var idx = 1
        var rootName = letter.toString()
        if (idx < main.length && (main[idx] == '#' || main[idx] == 'b')) {
            // "b" is only an accidental when something follows or the token ends;
            // "Bb" is a chord, but so is "B" in "Bm". Treat a lone trailing 'b' as flat.
            rootName += main[idx]
            idx++
        }
        val rootPc = noteToPitchClass(rootName)
        if (rootPc < 0) return null

        val quality = main.substring(idx)
        if (!isLegalQuality(quality)) return null

        var bassPc = -1
        if (bassPart.isNotEmpty()) {
            bassPc = noteToPitchClass(bassPart)
            if (bassPc < 0) return null
        }
        return ParsedChord(rootPc, quality, bassPc, raw)
    }

    /** Suffixes that may follow a chord root. Anything else means the token is a word. */
    private fun isLegalQuality(q: String): Boolean {
        if (q.isEmpty()) return true
        val allowed = "mMajindsu2456791113+°ø()#b-/ "
        for (c in q) {
            if (!allowed.contains(c)) return false
        }
        // Reject obvious English words that start with a capital note letter, e.g. "Be", "Dear".
        val lower = q.lowercase()
        val wordish = listOf("e", "ear", "eautiful", "ome", "ame", "ive", "ord", "ay", "on")
        if (lower in wordish) return false
        return true
    }

    /** True when [token] looks like a chord symbol. */
    fun isChordToken(token: String): Boolean = parse(token) != null

    /**
     * Transpose one chord token by [semitones]. Non-chord tokens come back unchanged.
     */
    fun transposeChord(token: String, semitones: Int, useFlats: Boolean): String {
        val parsed = parse(token) ?: return token
        val newRoot = pitchClassToName(parsed.root + semitones, useFlats)
        val newBass = if (parsed.bass >= 0) "/" + pitchClassToName(parsed.bass + semitones, useFlats) else ""
        return newRoot + parsed.quality + newBass
    }

    /** Transpose a whole chord line ("G   D/F#   Em7"), preserving the column spacing. */
    fun transposeChordLine(line: String, semitones: Int, useFlats: Boolean): String {
        if (semitones == 0) return line
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            if (line[i].isWhitespace()) {
                sb.append(line[i]); i++; continue
            }
            val start = i
            while (i < line.length && !line[i].isWhitespace()) i++
            val token = line.substring(start, i)
            val moved = transposeChord(token, semitones, useFlats)
            sb.append(moved)
            // Keep the chart roughly aligned when the new symbol is shorter.
            if (moved.length < token.length) sb.append(" ".repeat(token.length - moved.length))
        }
        return sb.toString()
    }

    /** Normalise any integer offset into the -11..+11 range the app exposes. */
    fun normalizeSemitones(value: Int): Int {
        var v = value % 12
        if (v > 11) v -= 12
        if (v < -11) v += 12
        return v
    }

    /** Semitone distance from [fromKey] to [toKey], expressed in -11..+11. */
    fun semitonesBetween(fromKey: String, toKey: String): Int {
        val a = noteToPitchClass(stripKeyQuality(fromKey))
        val b = noteToPitchClass(stripKeyQuality(toKey))
        if (a < 0 || b < 0) return 0
        var diff = (b - a) % 12
        if (diff > 6) diff -= 12
        if (diff < -6) diff += 12
        return diff
    }

    /** "Am" -> "A", "Bbmaj" -> "Bb". */
    fun stripKeyQuality(key: String): String {
        val k = key.trim()
        if (k.isEmpty()) return k
        var end = 1
        if (k.length > 1 && (k[1] == '#' || k[1] == 'b')) end = 2
        return k.substring(0, end)
    }

    /** The key a song sounds in after [semitones] of transposition. */
    fun transposedKey(originalKey: String, semitones: Int): String {
        val root = stripKeyQuality(originalKey)
        val pc = noteToPitchClass(root)
        if (pc < 0) return originalKey
        val minorSuffix = originalKey.trim().removePrefix(root)
        val target = ((pc + semitones) % 12 + 12) % 12
        // Pick the spelling that the resulting key normally uses.
        val sharp = SHARP_NAMES[target]
        val flat = FLAT_NAMES[target]
        val name = if (FLAT_KEYS.contains(flat) || flat.endsWith("b")) flat else sharp
        return name + minorSuffix
    }

    /** True when chords in a song transposed to this key should be spelled flat. */
    fun useFlatsForKey(originalKey: String, semitones: Int): Boolean =
        keyUsesFlats(transposedKey(originalKey, semitones))
}
