package com.chordflow.prompter.audio

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The pure-DSP half of the chord engine: spectrum -> Pitch Class Profile -> chord symbol.
 *
 * Free of any Android class, so the maths can be exercised on its own.
 * [AudioChordDetector] owns the microphone and feeds frames into this.
 *
 * Two design points that matter, both found by testing against synthesised guitar and
 * piano voicings rather than by theory:
 *
 *  1. Energy is gathered per SEMITONE BAND, not per FFT bin. A per-bin "nearest note"
 *     rule silently discards low fundamentals, because the bins are wider than a
 *     semitone down there.
 *
 *  2. The analysis frame is long (16384 samples, ~371 ms at 44.1 kHz). At the more
 *     usual 4096 the bins are ~10.8 Hz apart — wider than a semitone below ~370 Hz —
 *     so the bass register smears across neighbouring semitones and open guitar chords
 *     are misread. 16384 gives ~2.7 Hz bins, which resolves down to the low E string.
 *     Chords last far longer than 371 ms, so the latency costs nothing musically.
 */
class ChromaAnalyzer(
    private val sampleRate: Int = AudioChordDetector.SAMPLE_RATE,
    private val frameSize: Int = AudioChordDetector.FRAME_SIZE
) {

    companion object {
        /** Lowest and highest MIDI notes considered: D2 (~73 Hz) to B6 (~1976 Hz). */
        const val MIDI_LOW = 38
        const val MIDI_HIGH = 95

        /** Top of the window searched for the bass note of a slash chord (B3). */
        const val BASS_MAX_MIDI = 59

        /** Upper partials are tilted down so they do not outweigh the notes played. */
        private const val OCTAVE_TILT = 25.0

        /** How hard to penalise energy the chord template does not explain. */
        private const val OUTSIDE_PENALTY = 0.55f

        /**
         * Semitone offsets from the root for each supported quality.
         * The suffix is what the detector reports, so it matches chart spelling.
         */
        val TEMPLATES: List<Pair<String, IntArray>> = listOf(
            "" to intArrayOf(0, 4, 7),            // major
            "m" to intArrayOf(0, 3, 7),           // minor
            "7" to intArrayOf(0, 4, 7, 10),       // dominant 7
            "maj7" to intArrayOf(0, 4, 7, 11),
            "m7" to intArrayOf(0, 3, 7, 10),
            "sus4" to intArrayOf(0, 5, 7),
            "sus2" to intArrayOf(0, 2, 7),
            "dim" to intArrayOf(0, 3, 6),
            "aug" to intArrayOf(0, 4, 8),
            "add9" to intArrayOf(0, 2, 4, 7)
        )

        fun midiToHz(midi: Double): Double = 440.0 * 2.0.pow((midi - 69.0) / 12.0)
    }

    data class Match(val root: Int, val quality: String, val score: Float)

    private val bins = frameSize / 2
    private val binHz = sampleRate.toDouble() / frameSize

    private val noteCount = MIDI_HIGH - MIDI_LOW + 1
    private val bandLo = IntArray(noteCount)
    private val bandHi = IntArray(noteCount)
    private val noteWeight = DoubleArray(noteCount)
    private val noteEnergyBuffer = DoubleArray(noteCount)

    init {
        for (n in 0 until noteCount) {
            val midi = (MIDI_LOW + n).toDouble()
            var lo = floor(midiToHz(midi - 0.5) / binHz).toInt()
            var hi = ceil(midiToHz(midi + 0.5) / binHz).toInt()
            if (lo < 1) lo = 1
            if (hi > bins - 1) hi = bins - 1
            if (hi < lo) hi = lo
            bandLo[n] = lo
            bandHi[n] = hi
            noteWeight[n] = 1.0 / (1.0 + (midi - MIDI_LOW) / OCTAVE_TILT)
        }
    }

    /**
     * Energy per semitone across the usable range. Writes into a reusable buffer,
     * so a frame of audio costs no allocation here.
     */
    fun noteEnergies(spectrum: DoubleArray): DoubleArray {
        val limit = minOf(spectrum.size, bins)
        for (n in 0 until noteCount) {
            var sum = 0.0
            var i = bandLo[n]
            val hi = minOf(bandHi[n], limit - 1)
            while (i <= hi) {
                val m = spectrum[i]
                sum += m * m
                i++
            }
            noteEnergyBuffer[n] = sum * noteWeight[n]
        }
        return noteEnergyBuffer
    }

    /** Normalised 12-bin pitch class profile, or null when the frame has no pitched energy. */
    fun chromagram(spectrum: DoubleArray): FloatArray? {
        val notes = noteEnergies(spectrum)
        val chroma = DoubleArray(12)
        var total = 0.0
        for (n in 0 until noteCount) {
            val energy = notes[n]
            chroma[(MIDI_LOW + n) % 12] += energy
            total += energy
        }
        if (total <= 1e-12) return null

        val max = chroma.maxOrNull() ?: return null
        if (max <= 1e-12) return null

        val out = FloatArray(12)
        for (i in 0 until 12) out[i] = (chroma[i] / max).toFloat()
        return out
    }

    /** Strongest pitch class in the bass register, for slash-chord naming. -1 when unclear. */
    fun bassPitchClass(spectrum: DoubleArray): Int {
        val notes = noteEnergies(spectrum)
        var bestPc = -1
        var bestEnergy = 0.0
        for (n in 0 until noteCount) {
            val midi = MIDI_LOW + n
            if (midi > BASS_MAX_MIDI) break
            if (notes[n] > bestEnergy) {
                bestEnergy = notes[n]
                bestPc = midi % 12
            }
        }
        return bestPc
    }

    /**
     * Cosine similarity of the chroma vector against every root/quality template,
     * with a penalty for energy the template leaves unexplained and a small bias
     * toward the plain triads that dominate worship charts.
     *
     * Note: sus2 and sus4 a fourth apart contain identical pitch classes
     * (Csus2 = C D G, Gsus4 = G C D), so the two are not separable from a chromagram
     * alone. The teleprompter's chord matching treats sus as its parent triad anyway.
     */
    fun bestMatch(chroma: FloatArray): Match? {
        var normChroma = 0.0
        for (v in chroma) normChroma += v * v
        normChroma = sqrt(normChroma)
        if (normChroma <= 1e-9) return null

        var best: Match? = null
        for ((quality, intervals) in TEMPLATES) {
            for (root in 0 until 12) {
                var dot = 0.0
                for (iv in intervals) dot += chroma[(root + iv) % 12]
                var score = (dot / (normChroma * sqrt(intervals.size.toDouble()))).toFloat()

                var outside = 0.0
                for (pc in 0 until 12) {
                    var belongs = false
                    for (iv in intervals) {
                        if ((root + iv) % 12 == pc) {
                            belongs = true
                            break
                        }
                    }
                    if (!belongs) outside += chroma[pc]
                }
                score -= (outside / 12.0).toFloat() * OUTSIDE_PENALTY

                when (quality) {
                    "" -> score += 0.0500f
                    "m" -> score += 0.0425f
                    "7", "m7" -> score += 0.0125f
                    "dim", "aug" -> score -= 0.0500f
                }

                val current = best
                if (current == null || score > current.score) {
                    best = Match(root, quality, score.coerceIn(0f, 1f))
                }
            }
        }
        return best
    }

    /** Convenience for tests: window, transform, and classify one frame of mono samples. */
    fun analyze(samples: DoubleArray, window: DoubleArray): Pair<Match, Int>? {
        if (samples.size != frameSize) return null
        val windowed = DoubleArray(frameSize) { samples[it] * window[it] }
        val spectrum = FFT.magnitudeSpectrum(windowed)
        val chroma = chromagram(spectrum) ?: return null
        val match = bestMatch(chroma) ?: return null
        return match to bassPitchClass(spectrum)
    }
}
