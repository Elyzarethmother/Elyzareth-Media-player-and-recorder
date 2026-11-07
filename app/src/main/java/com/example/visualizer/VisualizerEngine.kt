package com.example.visualizer

import android.content.Context
import android.media.audiofx.Visualizer
import com.example.data.AudioPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.hypot

data class VisualizerFrame(
    val frequencies: FloatArray = FloatArray(32),
    val waveform: FloatArray = FloatArray(64),
    val rmsLevel: Float = 0f,
    val isAttached: Boolean = false,
    val attachmentError: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualizerFrame) return false
        return frequencies.contentEquals(other.frequencies) &&
                waveform.contentEquals(other.waveform) &&
                rmsLevel == other.rmsLevel &&
                isAttached == other.isAttached &&
                attachmentError == other.attachmentError
    }

    override fun hashCode(): Int {
        var result = frequencies.contentHashCode()
        result = 31 * result + waveform.contentHashCode()
        result = 31 * result + rmsLevel.hashCode()
        result = 31 * result + isAttached.hashCode()
        result = 31 * result + (attachmentError?.hashCode() ?: 0)
        return result
    }
}

class VisualizerEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val visualizerFactory: (Int) -> Visualizer = { sessionId -> Visualizer(sessionId) }
) {
    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = 0
    private var isPlaying: Boolean = false

    private val numBars = 32
    private val smoothedFrequencies = FloatArray(numBars)
    private val smoothedWaveform = FloatArray(64)

    private val _frame = MutableStateFlow(VisualizerFrame())
    val frame: StateFlow<VisualizerFrame> = _frame.asStateFlow()

    fun setAudioSession(audioSessionId: Int, playing: Boolean) {
        this.isPlaying = playing

        if (!AudioPermissionHelper.hasRecordAudioPermission(context)) {
            releaseVisualizer()
            currentSessionId = 0
            smoothedFrequencies.fill(0f)
            smoothedWaveform.fill(0f)
            _frame.value = VisualizerFrame(
                frequencies = smoothedFrequencies.copyOf(),
                waveform = smoothedWaveform.copyOf(),
                rmsLevel = 0f,
                isAttached = false,
                attachmentError = "RECORD_AUDIO permission required for audio visualizer capture."
            )
            return
        }

        if (audioSessionId <= 0) {
            releaseVisualizer()
            currentSessionId = 0
            decayToZero(error = null)
            return
        }

        if (currentSessionId == audioSessionId && visualizer != null) {
            try {
                visualizer?.enabled = playing
                if (!playing) {
                    decayToZero(error = null)
                } else {
                    _frame.value = _frame.value.copy(isAttached = true, attachmentError = null)
                }
                return
            } catch (e: Exception) {
                releaseVisualizer()
            }
        }

        releaseVisualizer()
        currentSessionId = audioSessionId

        try {
            val viz = visualizerFactory(audioSessionId).apply {
                val range = Visualizer.getCaptureSizeRange()
                captureSize = range[1]
                val status = setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform != null && isPlaying) {
                                processWaveform(waveform)
                            }
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft != null && isPlaying) {
                                processFft(fft)
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    true
                )
                if (status != Visualizer.SUCCESS) {
                    throw IllegalStateException("Visualizer.setDataCaptureListener returned status $status")
                }
                enabled = playing
            }
            visualizer = viz
            _frame.value = VisualizerFrame(
                frequencies = smoothedFrequencies.copyOf(),
                waveform = smoothedWaveform.copyOf(),
                rmsLevel = 0f,
                isAttached = true,
                attachmentError = null
            )
        } catch (e: Exception) {
            // Never silently substitute synthetic animation when real capture fails; report attachment failure clearly
            visualizer = null
            smoothedFrequencies.fill(0f)
            smoothedWaveform.fill(0f)
            _frame.value = VisualizerFrame(
                frequencies = smoothedFrequencies.copyOf(),
                waveform = smoothedWaveform.copyOf(),
                rmsLevel = 0f,
                isAttached = false,
                attachmentError = "Visualizer attachment failed (session $audioSessionId): ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        this.isPlaying = playing
        val viz = visualizer
        if (viz != null) {
            try {
                viz.enabled = playing
                if (!playing) {
                    decayToZero(error = null)
                }
            } catch (e: Exception) {
                releaseVisualizer()
                _frame.value = VisualizerFrame(
                    isAttached = false,
                    attachmentError = "Visualizer state error: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        } else if (playing && currentSessionId > 0) {
            setAudioSession(currentSessionId, true)
        }
    }

    fun processFft(fft: ByteArray) {
        val n = fft.size
        val binCount = n / 2
        if (binCount <= 0) return
        val step = (binCount / numBars).coerceAtLeast(1)

        var totalMagnitude = 0f
        for (i in 0 until numBars) {
            var sum = 0.0
            val start = i * step
            val end = ((i + 1) * step).coerceAtMost(binCount)
            for (j in start until end) {
                val r = fft[2 * j].toFloat()
                val im = fft[2 * j + 1].toFloat()
                sum += hypot(r.toDouble(), im.toDouble())
            }
            val avg = if (end > start) (sum / (end - start)).toFloat() else 0f
            val normalized = (avg / 128f).coerceIn(0f, 1f)

            if (normalized > smoothedFrequencies[i]) {
                smoothedFrequencies[i] = smoothedFrequencies[i] * 0.3f + normalized * 0.7f
            } else {
                smoothedFrequencies[i] = smoothedFrequencies[i] * 0.82f + normalized * 0.18f
            }
            totalMagnitude += smoothedFrequencies[i]
        }

        val rms = (totalMagnitude / numBars).coerceIn(0f, 1f)
        _frame.value = VisualizerFrame(
            frequencies = smoothedFrequencies.copyOf(),
            waveform = smoothedWaveform.copyOf(),
            rmsLevel = rms,
            isAttached = true,
            attachmentError = null
        )
    }

    fun processWaveform(waveform: ByteArray) {
        if (waveform.isEmpty()) return
        val step = (waveform.size / 64).coerceAtLeast(1)
        for (i in 0 until 64) {
            val idx = (i * step).coerceAtMost(waveform.size - 1)
            val raw = (waveform[idx].toInt() and 0xFF) - 128
            val norm = (raw / 128f).coerceIn(-1f, 1f)
            smoothedWaveform[i] = smoothedWaveform[i] * 0.6f + norm * 0.4f
        }
    }

    private fun decayToZero(error: String?) {
        scope.launch(Dispatchers.Default) {
            for (step in 0 until 6) {
                if (isPlaying) return@launch
                for (i in smoothedFrequencies.indices) {
                    smoothedFrequencies[i] *= 0.55f
                }
                for (i in smoothedWaveform.indices) {
                    smoothedWaveform[i] *= 0.55f
                }
                _frame.value = VisualizerFrame(
                    frequencies = smoothedFrequencies.copyOf(),
                    waveform = smoothedWaveform.copyOf(),
                    rmsLevel = 0f,
                    isAttached = visualizer != null,
                    attachmentError = error
                )
                delay(25)
            }
            smoothedFrequencies.fill(0f)
            smoothedWaveform.fill(0f)
            _frame.value = VisualizerFrame(
                frequencies = smoothedFrequencies.copyOf(),
                waveform = smoothedWaveform.copyOf(),
                rmsLevel = 0f,
                isAttached = visualizer != null,
                attachmentError = error
            )
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
    }

    fun release() {
        releaseVisualizer()
    }
}
