package com.chordflow.prompter.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.chordflow.prompter.core.ChordTransposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Live acoustic chord detection from the device microphone.
 *
 * Pipeline, implemented entirely in this app with no external DSP dependency:
 *
 *   AudioRecord (44.1 kHz, 16-bit PCM, mono)
 *     -> Hann window over a 4096-sample frame, 50% overlap
 *     -> [FFT] magnitude spectrum
 *     -> RMS noise gate (the HUD sensitivity slider feeds this)
 *     -> [ChromaAnalyzer]: pitch class profile, template correlation, bass note
 *     -> confidence threshold + frame-agreement debounce
 *     -> [state] StateFlow consumed by the teleprompter
 */
class AudioChordDetector {

    companion object {
        const val SAMPLE_RATE = 44100
        /**
         * 16384 samples is ~371 ms at 44.1 kHz. It sounds long, but it is what gives
         * ~2.7 Hz FFT bins, and anything shorter cannot resolve semitones in the bass
         * register — open guitar chords get misread. Chords last far longer than this.
         */
        const val FRAME_SIZE = 16384
        /** 4096 = ~93 ms between readings, so the HUD still feels live. */
        const val HOP_SIZE = 4096
    }

    /** What the HUD and the auto-scroll logic read. */
    data class DetectionState(
        val running: Boolean = false,
        val chord: String = "",          // e.g. "G", "Em7", "D/F#"
        val rootPitchClass: Int = -1,
        val quality: String = "",
        val confidence: Float = 0f,
        val level: Float = 0f,           // 0..1 input level, for the HUD meter
        val gated: Boolean = true,       // true when below the noise gate
        val error: String? = null
    )

    private val _state = MutableStateFlow(DetectionState())
    val state: StateFlow<DetectionState> = _state.asStateFlow()

    /** 0f..1f. Higher = more aggressive gate (needs a louder signal to listen). */
    @Volatile
    var sensitivity: Float = 0.35f

    /** 0f..1f. Minimum template correlation before a chord is reported. */
    @Volatile
    var confidenceThreshold: Float = 0.62f

    /** How many consecutive frames must agree before the chord is published. */
    @Volatile
    var agreementFrames: Int = 2

    private val analyzer = ChromaAnalyzer(SAMPLE_RATE, FRAME_SIZE)
    private val window = FFT.hannWindow(FRAME_SIZE)
    private val frame = DoubleArray(FRAME_SIZE)
    private val ring = ShortArray(FRAME_SIZE)
    private var ringFill = 0

    // Scratch buffers so the audio thread allocates nothing per frame.
    private val fftRe = DoubleArray(FRAME_SIZE)
    private val fftIm = DoubleArray(FRAME_SIZE)
    private val spectrumBuffer = DoubleArray(FRAME_SIZE / 2)

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    private var candidate = ""
    private var candidateCount = 0

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (job?.isActive == true) return

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            _state.value = DetectionState(running = false, error = "Microphone unavailable on this device")
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SIZE * 4)
            )
        } catch (e: Exception) {
            _state.value = DetectionState(running = false, error = "Could not open microphone: ${e.message}")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            _state.value = DetectionState(running = false, error = "Microphone could not be initialised")
            return
        }

        ringFill = 0
        candidate = ""
        candidateCount = 0

        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        _state.value = DetectionState(running = true)

        job = s.launch {
            val chunk = ShortArray(HOP_SIZE)
            try {
                recorder.startRecording()
                while (isActive) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read <= 0) continue
                    pushSamples(chunk, read)
                    if (ringFill >= FRAME_SIZE) {
                        processFrame()
                        // Slide the ring buffer by one hop.
                        System.arraycopy(ring, HOP_SIZE, ring, 0, FRAME_SIZE - HOP_SIZE)
                        ringFill = FRAME_SIZE - HOP_SIZE
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(running = false, error = e.message)
            } finally {
                try {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
                } catch (_: Exception) {
                    // Already stopped.
                }
                recorder.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null
        _state.value = DetectionState(running = false)
    }

    private fun pushSamples(src: ShortArray, count: Int) {
        var n = count
        if (ringFill + n > FRAME_SIZE) n = FRAME_SIZE - ringFill
        if (n <= 0) return
        System.arraycopy(src, 0, ring, ringFill, n)
        ringFill += n
    }

    private fun processFrame() {
        // --- window + RMS level ---
        var sumSq = 0.0
        for (i in 0 until FRAME_SIZE) {
            val sample = ring[i] / 32768.0
            sumSq += sample * sample
            frame[i] = sample * window[i]
        }
        val rms = sqrt(sumSq / FRAME_SIZE)
        val level = (rms * 6.0).coerceIn(0.0, 1.0).toFloat()

        // --- noise gate: slider 0..1 maps to roughly -60 dB .. -24 dB ---
        val gateThreshold = 0.001 + sensitivity * 0.059
        if (rms < gateThreshold) {
            candidate = ""
            candidateCount = 0
            _state.value = _state.value.copy(level = level, gated = true, confidence = 0f)
            return
        }

        val spectrum = FFT.magnitudeSpectrumInto(frame, fftRe, fftIm, spectrumBuffer)
        val chroma = analyzer.chromagram(spectrum)
        if (chroma == null) {
            _state.value = _state.value.copy(level = level, gated = true, confidence = 0f)
            return
        }

        val best = analyzer.bestMatch(chroma)
        if (best == null || best.score < confidenceThreshold) {
            candidateCount = 0
            _state.value = _state.value.copy(level = level, gated = false, confidence = best?.score ?: 0f)
            return
        }

        val bassPc = analyzer.bassPitchClass(spectrum)
        val rootName = ChordTransposer.pitchClassToName(best.root, useFlats = false)
        val slash = if (bassPc >= 0 && bassPc != best.root && chroma[bassPc] > 0.25f) {
            "/" + ChordTransposer.pitchClassToName(bassPc, useFlats = false)
        } else ""
        val symbol = rootName + best.quality + slash

        // --- debounce: the same chord must hold for a few frames ---
        val core = rootName + best.quality
        if (core == candidate) {
            candidateCount++
        } else {
            candidate = core
            candidateCount = 1
        }

        if (candidateCount >= agreementFrames) {
            _state.value = DetectionState(
                running = true,
                chord = symbol,
                rootPitchClass = best.root,
                quality = best.quality,
                confidence = best.score,
                level = level,
                gated = false
            )
        } else {
            _state.value = _state.value.copy(level = level, gated = false, confidence = best.score)
        }
    }
}
