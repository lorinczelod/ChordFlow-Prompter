package com.chordflow.prompter.core

/**
 * Decides whether what the microphone heard counts as "the band played the next chord".
 *
 * Deliberately forgiving: a guitarist voicing Gsus4 into G, or an Em7 played as Em,
 * should still advance the lyrics. The root is what must agree.
 */
object ChordMatcher {

    enum class Strictness {
        /** Root only — most reliable on a noisy stage. */
        ROOT,
        /** Root plus major/minor family — the default. */
        FAMILY,
        /** Root plus exact quality suffix. */
        EXACT
    }

    private fun family(quality: String): String = when {
        quality.startsWith("maj") -> "major"
        quality.startsWith("m") -> "minor"
        quality.startsWith("dim") || quality.startsWith("°") -> "dim"
        quality.startsWith("aug") || quality.startsWith("+") -> "aug"
        quality.startsWith("sus") -> "major"   // sus resolves to the major triad on stage
        else -> "major"
    }

    fun matches(detected: String, target: String, strictness: Strictness = Strictness.FAMILY): Boolean {
        if (detected.isBlank() || target.isBlank()) return false
        val d = ChordTransposer.parse(detected) ?: return false
        val t = ChordTransposer.parse(target) ?: return false
        if (d.root != t.root) return false
        return when (strictness) {
            Strictness.ROOT -> true
            Strictness.FAMILY -> family(d.quality) == family(t.quality)
            Strictness.EXACT -> d.quality == t.quality
        }
    }

    /** True when [detected] matches any chord in [targets]. */
    fun matchesAny(detected: String, targets: List<String>, strictness: Strictness = Strictness.FAMILY): Boolean =
        targets.any { matches(detected, it, strictness) }
}
