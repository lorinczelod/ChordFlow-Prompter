package com.chordflow.prompter.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Self-contained in-place iterative radix-2 Cooley-Tukey FFT.
 * No third-party DSP library — this runs entirely offline on the device.
 */
object FFT {

    /**
     * In-place complex FFT. [re] and [im] must be the same length and a power of two.
     */
    fun transform(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size) { "re and im must be the same length" }
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of two, got $n" }

        // --- bit-reversal permutation ---
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        // --- butterflies ---
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wRe = cos(angle)
            val wIm = sin(angle)
            val half = len shr 1
            var base = 0
            while (base < n) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until half) {
                    val a = base + k
                    val b = a + half
                    val vRe = re[b] * curRe - im[b] * curIm
                    val vIm = re[b] * curIm + im[b] * curRe
                    val uRe = re[a]
                    val uIm = im[a]
                    re[a] = uRe + vRe
                    im[a] = uIm + vIm
                    re[b] = uRe - vRe
                    im[b] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                base += len
            }
            len = len shl 1
        }
    }

    /**
     * Magnitude spectrum of a real signal. Returns [size]/2 bins.
     * [input] is consumed as-is; a Hann window should already be applied.
     */
    fun magnitudeSpectrum(input: DoubleArray): DoubleArray {
        val n = input.size
        val re = input.copyOf()
        val im = DoubleArray(n)
        transform(re, im)
        val half = n / 2
        val mag = DoubleArray(half)
        for (i in 0 until half) {
            mag[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }
        return mag
    }

    /**
     * Allocation-free magnitude spectrum for the real-time path.
     * [re], [im] and [out] are caller-owned scratch buffers: `re` and `im` must be
     * [input].size long, `out` must be at least half that. Returns [out].
     */
    fun magnitudeSpectrumInto(
        input: DoubleArray,
        re: DoubleArray,
        im: DoubleArray,
        out: DoubleArray
    ): DoubleArray {
        val n = input.size
        System.arraycopy(input, 0, re, 0, n)
        java.util.Arrays.fill(im, 0, n, 0.0)
        transform(re, im)
        val half = n / 2
        for (i in 0 until half) {
            out[i] = sqrt(re[i] * re[i] + im[i] * im[i])
        }
        return out
    }

    /** Pre-computed Hann window of [n] points. */
    fun hannWindow(n: Int): DoubleArray {
        val w = DoubleArray(n)
        for (i in 0 until n) {
            w[i] = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
        }
        return w
    }
}
