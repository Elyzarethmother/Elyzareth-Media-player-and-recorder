package com.example.recorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.ArtworkManager
import com.example.data.LyricsManager
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

sealed class RecordingStatus {
    object Idle : RecordingStatus()
    data class Recording(val elapsedSec: Int, val targetSec: Int, val isPortrait: Boolean) : RecordingStatus()
    object Finalizing : RecordingStatus()
    data class Completed(val exportedVideo: ExportedVideo) : RecordingStatus()
    data class Failed(val reason: String) : RecordingStatus()
}

data class Mp4VerificationResult(
    val isValid: Boolean,
    val hasVideoTrack: Boolean,
    val hasAudioTrack: Boolean,
    val videoWidth: Int,
    val videoHeight: Int,
    val videoSampleCount: Int,
    val audioSampleCount: Int,
    val videoDurationUs: Long,
    val audioDurationUs: Long,
    val failureReason: String? = null
)

object YuvConverter {
    /**
     * Converts ARGB_8888 pixels to YUV420SemiPlanar (NV12: Y plane followed by interleaved U, V)
     * using ITU-R BT.601 integer coefficients with corrected shift operator precedence.
     */
    fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var aIndex = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[aIndex++]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)

                yuv420sp[yIndex++] = y.toByte()
                if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < yuv420sp.size) {
                    yuv420sp[uvIndex++] = u.toByte()
                    yuv420sp[uvIndex++] = v.toByte()
                }
            }
        }
    }

    fun rgbToYuv(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
        val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
        val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
        return Triple(y, u, v)
    }
}

/**
 * Coordinates MediaCodec output format negotiation for both video and audio encoders
 * before starting the MediaMuxer, queuing any early encoded frames until both formats are ready.
 */
class MuxerFormatCoordinator {
    data class QueuedSample(
        val isVideo: Boolean,
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int
    )

    var videoFormat: MediaFormat? = null
        private set
    var audioFormat: MediaFormat? = null
        private set
    var isMuxerStarted: Boolean = false
        private set

    private val pendingSamples = mutableListOf<QueuedSample>()
    private var lastVideoPtsUs = -1L
    private var lastAudioPtsUs = -1L
    private var baseVideoPtsUs: Long? = null
    private var baseAudioPtsUs: Long? = null

    val isReadyToStart: Boolean
        get() = videoFormat != null && audioFormat != null

    fun onVideoFormatChanged(format: MediaFormat) {
        videoFormat = format
    }

    fun onAudioFormatChanged(format: MediaFormat) {
        audioFormat = format
    }

    fun normalizeAndEnforceMonotonicVideoPts(rawPtsUs: Long): Long {
        val base = baseVideoPtsUs ?: rawPtsUs.also { baseVideoPtsUs = it }
        val normalized = (rawPtsUs - base).coerceAtLeast(0L)
        val monotonic = if (lastVideoPtsUs < 0L) {
            normalized
        } else {
            maxOf(normalized, lastVideoPtsUs + 1L)
        }
        lastVideoPtsUs = monotonic
        return monotonic
    }

    fun normalizeAndEnforceMonotonicAudioPts(rawPtsUs: Long): Long {
        val base = baseAudioPtsUs ?: rawPtsUs.also { baseAudioPtsUs = it }
        val normalized = (rawPtsUs - base).coerceAtLeast(0L)
        val monotonic = if (lastAudioPtsUs < 0L) {
            normalized
        } else {
            maxOf(normalized, lastAudioPtsUs + 1L)
        }
        lastAudioPtsUs = monotonic
        return monotonic
    }

    fun enqueueOrReadySample(isVideo: Boolean, data: ByteArray, rawPtsUs: Long, flags: Int): QueuedSample {
        val ptsUs = if (isVideo) {
            normalizeAndEnforceMonotonicVideoPts(rawPtsUs)
        } else {
            normalizeAndEnforceMonotonicAudioPts(rawPtsUs)
        }
        val sample = QueuedSample(isVideo, data, ptsUs, flags)
        if (!isMuxerStarted) {
            pendingSamples.add(sample)
        }
        return sample
    }

    fun markMuxerStarted(): List<QueuedSample> {
        if (!isReadyToStart) {
            throw IllegalStateException("Cannot start MediaMuxer before both video and audio formats are negotiated.")
        }
        isMuxerStarted = true
        val drained = pendingSamples.toList()
        pendingSamples.clear()
        return drained
    }
}

/**
 * Computes real FFT frequency bins from decoded 16-bit PCM audio samples so MP4 video frames
 * render genuine audio-reactive visualizer bars synchronized to the decoded audio stream.
 */
class PcmFftAnalyzer(private val numBars: Int = 28) {
    private val spectrumTimeline = ConcurrentSkipListMap<Long, FloatArray>()

    fun recordPcmChunk(ptsUs: Long, pcmBytes: ByteArray, size: Int, channelCount: Int) {
        if (size < 4) return
        val spectrum = computeSpectrumFromPcm16(pcmBytes, size, channelCount, numBars)
        spectrumTimeline[ptsUs] = spectrum
    }

    fun getSpectrumAt(ptsUs: Long, fallbackRealtimeFrequencies: FloatArray? = null): FloatArray {
        val floorEntry = spectrumTimeline.floorEntry(ptsUs)
        val ceilEntry = spectrumTimeline.ceilingEntry(ptsUs)
        val candidate = when {
            floorEntry != null && ceilEntry != null -> {
                if (ptsUs - floorEntry.key <= ceilEntry.key - ptsUs) floorEntry.value else ceilEntry.value
            }
            floorEntry != null -> floorEntry.value
            ceilEntry != null -> ceilEntry.value
            else -> null
        }

        if (candidate != null) {
            return candidate
        }
        if (fallbackRealtimeFrequencies != null && fallbackRealtimeFrequencies.isNotEmpty()) {
            return FloatArray(numBars) { idx ->
                val srcIdx = (idx * fallbackRealtimeFrequencies.size / numBars)
                    .coerceIn(0, fallbackRealtimeFrequencies.size - 1)
                fallbackRealtimeFrequencies[srcIdx].coerceIn(0f, 1f)
            }
        }
        return FloatArray(numBars)
    }

    companion object {
        fun computeSpectrumFromPcm16(
            pcmBytes: ByteArray,
            byteCount: Int,
            channelCount: Int,
            numBars: Int = 28
        ): FloatArray {
            val validBytes = byteCount.coerceAtMost(pcmBytes.size) and -2
            val totalShorts = validBytes / 2
            val channels = channelCount.coerceAtLeast(1)
            val frameCount = totalShorts / channels
            if (frameCount <= 0) return FloatArray(numBars)

            val fftSize = 256
            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)

            val shortBuf = ByteBuffer.wrap(pcmBytes, 0, validBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()

            val samplesToRead = min(frameCount, fftSize)
            for (i in 0 until samplesToRead) {
                var monoSum = 0.0
                for (ch in 0 until channels) {
                    val sampleIdx = i * channels + ch
                    if (sampleIdx < totalShorts) {
                        monoSum += shortBuf.get(sampleIdx).toDouble() / 32768.0
                    }
                }
                val mono = monoSum / channels
                // Hann window
                val window = 0.5 * (1.0 - cos(2.0 * Math.PI * i / (samplesToRead - 1).coerceAtLeast(1)))
                real[i] = mono * window
            }

            fftRadix2(real, imag)

            val halfBins = fftSize / 2
            val result = FloatArray(numBars)
            for (b in 0 until numBars) {
                // Logarithmic / perceptual bin mapping across 1..halfBins-1
                val startBin = (1 + (b * (halfBins - 1)) / numBars).coerceIn(1, halfBins - 1)
                val endBin = (1 + ((b + 1) * (halfBins - 1)) / numBars).coerceIn(startBin + 1, halfBins)
                var magSum = 0.0
                for (k in startBin until endBin) {
                    magSum += hypot(real[k], imag[k])
                }
                val avgMag = magSum / (endBin - startBin).coerceAtLeast(1)
                // Scale magnitude to 0..1 range
                val normalized = (ln(1.0 + avgMag * 12.0) / ln(13.0)).toFloat().coerceIn(0f, 1f)
                result[b] = normalized
            }
            return result
        }

        private fun fftRadix2(real: DoubleArray, imag: DoubleArray) {
            val n = real.size
            var j = 0
            for (i in 0 until n) {
                if (i < j) {
                    val tr = real[i]; real[i] = real[j]; real[j] = tr
                    val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
                }
                var m = n shr 1
                while (m >= 1 && j >= m) {
                    j -= m
                    m = m shr 1
                }
                j += m
            }

            var len = 2
            while (len <= n) {
                val halfLen = len shr 1
                val angle = -2.0 * Math.PI / len
                val wlenR = cos(angle)
                val wlenI = sin(angle)
                var i = 0
                while (i < n) {
                    var wR = 1.0
                    var wI = 0.0
                    for (k in 0 until halfLen) {
                        val uR = real[i + k]
                        val uI = imag[i + k]
                        val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                        val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR
                        real[i + k] = uR + vR
                        imag[i + k] = uI + vI
                        real[i + k + halfLen] = uR - vR
                        imag[i + k + halfLen] = uI - vI
                        val nextWR = wR * wlenR - wI * wlenI
                        val nextWI = wR * wlenI + wI * wlenR
                        wR = nextWR
                        wI = nextWI
                    }
                    i += len
                }
                len = len shl 1
            }
        }
    }
}

object Mp4StreamVerifier {
    fun verifyMp4File(file: File, expectedWidth: Int, expectedHeight: Int): Mp4VerificationResult {
        if (!file.exists() || file.length() == 0L) {
            return Mp4VerificationResult(
                isValid = false,
                hasVideoTrack = false,
                hasAudioTrack = false,
                videoWidth = 0,
                videoHeight = 0,
                videoSampleCount = 0,
                audioSampleCount = 0,
                videoDurationUs = 0L,
                audioDurationUs = 0L,
                failureReason = "Output MP4 file does not exist or is empty."
            )
        }

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var width = 0
            var height = 0

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex < 0) {
                    videoTrackIndex = i
                    if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                        width = format.getInteger(MediaFormat.KEY_WIDTH)
                    }
                    if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                        height = format.getInteger(MediaFormat.KEY_HEIGHT)
                    }
                } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                    audioTrackIndex = i
                }
            }

            if (videoTrackIndex < 0 || audioTrackIndex < 0) {
                return Mp4VerificationResult(
                    isValid = false,
                    hasVideoTrack = videoTrackIndex >= 0,
                    hasAudioTrack = audioTrackIndex >= 0,
                    videoWidth = width,
                    videoHeight = height,
                    videoSampleCount = 0,
                    audioSampleCount = 0,
                    videoDurationUs = 0L,
                    audioDurationUs = 0L,
                    failureReason = "Missing required stream(s): hasVideo=${videoTrackIndex >= 0}, hasAudio=${audioTrackIndex >= 0}"
                )
            }

            // Count video samples & verify monotonic timestamps
            extractor.selectTrack(videoTrackIndex)
            var videoSamples = 0
            var lastVideoTimeUs = -1L
            var videoMonotonic = true
            while (extractor.sampleTime >= 0) {
                val t = extractor.sampleTime
                if (lastVideoTimeUs >= 0 && t < lastVideoTimeUs) {
                    videoMonotonic = false
                }
                lastVideoTimeUs = t
                videoSamples++
                if (!extractor.advance()) break
            }
            extractor.unselectTrack(videoTrackIndex)

            // Count audio samples & verify monotonic timestamps
            extractor.selectTrack(audioTrackIndex)
            var audioSamples = 0
            var lastAudioTimeUs = -1L
            var audioMonotonic = true
            while (extractor.sampleTime >= 0) {
                val t = extractor.sampleTime
                if (lastAudioTimeUs >= 0 && t < lastAudioTimeUs) {
                    audioMonotonic = false
                }
                lastAudioTimeUs = t
                audioSamples++
                if (!extractor.advance()) break
            }
            extractor.unselectTrack(audioTrackIndex)

            val dimensionsMatch = (width == expectedWidth && height == expectedHeight)
            val isValid = videoSamples > 0 &&
                audioSamples > 0 &&
                dimensionsMatch &&
                videoMonotonic &&
                audioMonotonic

            val reason = when {
                videoSamples <= 0 -> "Video track contains zero samples."
                audioSamples <= 0 -> "Audio track contains zero samples."
                !dimensionsMatch -> "Video dimensions ${width}x${height} do not match expected ${expectedWidth}x${expectedHeight}."
                !videoMonotonic -> "Video presentation timestamps are not monotonic."
                !audioMonotonic -> "Audio presentation timestamps are not monotonic."
                else -> null
            }

            Mp4VerificationResult(
                isValid = isValid,
                hasVideoTrack = true,
                hasAudioTrack = true,
                videoWidth = width,
                videoHeight = height,
                videoSampleCount = videoSamples,
                audioSampleCount = audioSamples,
                videoDurationUs = lastVideoTimeUs.coerceAtLeast(0L),
                audioDurationUs = lastAudioTimeUs.coerceAtLeast(0L),
                failureReason = reason
            )
        } catch (e: Exception) {
            Mp4VerificationResult(
                isValid = false,
                hasVideoTrack = false,
                hasAudioTrack = false,
                videoWidth = 0,
                videoHeight = 0,
                videoSampleCount = 0,
                audioSampleCount = 0,
                videoDurationUs = 0L,
                audioDurationUs = 0L,
                failureReason = "Failed to inspect MP4 container: ${e.message}"
            )
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }
}

class Mp4VideoRecorder(
    private val context: Context,
    private val artworkManager: ArtworkManager,
    private val exportManager: ExportManager
) {
    private val _status = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val status: StateFlow<RecordingStatus> = _status.asStateFlow()

    @Volatile
    private var isRecordingRequested = false

    @Volatile
    private var isStopFinalizeRequested = false

    fun stopRecording() {
        if (isRecordingRequested) {
            isStopFinalizeRequested = true
        }
    }

    fun cancelRecording() {
        isRecordingRequested = false
        isStopFinalizeRequested = false
    }

    fun resetStatus() {
        isRecordingRequested = false
        isStopFinalizeRequested = false
        _status.value = RecordingStatus.Idle
    }

    internal fun simulateStartRecordingForTest(isPortrait: Boolean, targetSec: Int) {
        isRecordingRequested = true
        isStopFinalizeRequested = false
        _status.value = RecordingStatus.Recording(0, targetSec, isPortrait)
    }

    internal fun simulateStopAndExportForTest(songTitle: String, isPortrait: Boolean): ExportedVideo {
        stopRecording()
        _status.value = RecordingStatus.Finalizing
        val out = exportManager.createOutputFile(songTitle, isPortrait)
        out.writeBytes(ByteArray(8192) { 0x42 })
        val exported = ExportedVideo(
            file = out,
            name = out.name,
            sizeBytes = out.length(),
            formattedDate = "Just now",
            uri = exportManager.getShareableUri(out)
        )
        isRecordingRequested = false
        isStopFinalizeRequested = false
        _status.value = RecordingStatus.Completed(exported)
        return exported
    }

    suspend fun recordComposition(
        song: Song,
        isPortrait: Boolean,
        durationSeconds: Int = 15,
        startPositionMs: Long = 0L,
        defaultArtworkBitmap: Bitmap? = null,
        realtimeFrequenciesProvider: (() -> FloatArray)? = null
    ): ExportedVideo? = withContext(Dispatchers.IO) {
        val width = if (isPortrait) 720 else 1280
        val height = if (isPortrait) 1280 else 720
        val frameRate = 30
        val totalFrames = durationSeconds * frameRate
        val targetDurationUs = durationSeconds * 1_000_000L
        val startPositionUs = startPositionMs.coerceAtLeast(0L) * 1000L
        val outputFile = exportManager.createOutputFile(song.songTitle, isPortrait)

        isRecordingRequested = true
        isStopFinalizeRequested = false
        _status.value = RecordingStatus.Recording(0, durationSeconds, isPortrait)

        var videoCodec: MediaCodec? = null
        var audioDecoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var extractor: MediaExtractor? = null
        var videoTrackIndex = -1
        var audioTrackIndex = -1

        val coordinator = MuxerFormatCoordinator()
        val pcmFftAnalyzer = PcmFftAnalyzer(numBars = 28)

        try {
            val customArt = artworkManager.loadBitmap(song.customArtworkUri)
            val artworkBitmap = customArt ?: defaultArtworkBitmap

            // 1. Setup Audio Extractor & locate audio track
            extractor = MediaExtractor()
            if (song.filePath != null && File(song.filePath).exists()) {
                extractor.setDataSource(song.filePath)
            } else if (song.contentUri != null) {
                extractor.setDataSource(context, Uri.parse(song.contentUri), null)
            } else if (song.isAsset && song.assetPath != null) {
                val afd = context.assets.openFd(song.assetPath)
                extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                throw IllegalArgumentException("Song source is missing or inaccessible.")
            }

            var audioSourceTrack = -1
            var audioMime = ""
            var sampleRate = 44100
            var channelCount = 2

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioSourceTrack = i
                    audioMime = mime
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    extractor.selectTrack(i)
                    break
                }
            }

            if (audioSourceTrack < 0) {
                throw IllegalStateException("Selected audio file contains no valid audio track.")
            }

            // Seek to the selected playback segment
            if (startPositionUs > 0L) {
                extractor.seekTo(startPositionUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            // 2. Configure Video Encoder
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, 3_500_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // 3. Configure Audio Decoder & AAC Encoder
            val srcFormat = extractor.getTrackFormat(audioSourceTrack)
            audioDecoder = MediaCodec.createDecoderByType(audioMime).apply {
                configure(srcFormat, null, null, 0)
                start()
            }

            val audioOutFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(audioOutFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // 4. Setup MediaMuxer
            val activeMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = activeMuxer

            val renderBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(renderBitmap)
            val argbPixels = IntArray(width * height)
            val yuvBuffer = ByteArray(width * height * 3 / 2)

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioEncBufferInfo = MediaCodec.BufferInfo()
            val audioDecBufferInfo = MediaCodec.BufferInfo()

            var videoFrameIndex = 0
            var isVideoEosSent = false
            var isVideoEosReceived = false
            var isAudioDecEosSent = false
            var isAudioDecEosReceived = false
            var isAudioEncEosSent = false
            var isAudioEncEosReceived = false

            var baseExtractorPtsUs: Long? = null
            var totalPcmBytesEncoded = 0L
            val bytesPerSecond = (sampleRate * channelCount * 2).coerceAtLeast(1)

            fun tryStartMuxerAndFlush() {
                if (!coordinator.isMuxerStarted && coordinator.isReadyToStart) {
                    videoTrackIndex = activeMuxer.addTrack(coordinator.videoFormat!!)
                    audioTrackIndex = activeMuxer.addTrack(coordinator.audioFormat!!)
                    activeMuxer.start()
                    val queued = coordinator.markMuxerStarted()
                    for (item in queued) {
                        val buf = ByteBuffer.wrap(item.data)
                        val info = MediaCodec.BufferInfo().apply {
                            set(0, item.data.size, item.presentationTimeUs, item.flags)
                        }
                        val trackIdx = if (item.isVideo) videoTrackIndex else audioTrackIndex
                        activeMuxer.writeSampleData(trackIdx, buf, info)
                    }
                }
            }

            // Main synchronized encoding loop: runs until BOTH video and audio encoders reach EOS
            while (!isVideoEosReceived || !isAudioEncEosReceived) {
                val stopRequested = !isRecordingRequested || isStopFinalizeRequested

                // --- STEP A: FEED & DECODE AUDIO FIRST SO PCM FFT SPECTRUM IS AVAILABLE FOR VIDEO FRAMES ---
                if (!isAudioDecEosSent) {
                    val decInIdx = audioDecoder.dequeueInputBuffer(5_000)
                    if (decInIdx >= 0) {
                        val inBuf = audioDecoder.getInputBuffer(decInIdx)
                        inBuf?.clear()
                        val sampleSize = if (stopRequested) -1 else (inBuf?.let { extractor.readSampleData(it, 0) } ?: -1)
                        val rawSampleTimeUs = extractor.sampleTime
                        if (sampleSize < 0 || rawSampleTimeUs < 0L) {
                            audioDecoder.queueInputBuffer(decInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isAudioDecEosSent = true
                        } else {
                            val baseUs = baseExtractorPtsUs ?: rawSampleTimeUs.also { baseExtractorPtsUs = it }
                            val relativePtsUs = (rawSampleTimeUs - baseUs).coerceAtLeast(0L)
                            if (relativePtsUs >= targetDurationUs) {
                                audioDecoder.queueInputBuffer(decInIdx, 0, 0, relativePtsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isAudioDecEosSent = true
                            } else {
                                audioDecoder.queueInputBuffer(decInIdx, 0, sampleSize, relativePtsUs, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Drain audioDecoder -> compute real PCM FFT -> feed audioEncoder
                if (!isAudioDecEosReceived) {
                    val decOutIdx = audioDecoder.dequeueOutputBuffer(audioDecBufferInfo, 5_000)
                    if (decOutIdx >= 0) {
                        val isDecEos = (audioDecBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val decOutBuf = audioDecoder.getOutputBuffer(decOutIdx)

                        if (decOutBuf != null && audioDecBufferInfo.size > 0) {
                            decOutBuf.position(audioDecBufferInfo.offset)
                            decOutBuf.limit(audioDecBufferInfo.offset + audioDecBufferInfo.size)
                            val pcmBytes = ByteArray(audioDecBufferInfo.size)
                            decOutBuf.get(pcmBytes)

                            val pcmPtsUs = (totalPcmBytesEncoded * 1_000_000L) / bytesPerSecond
                            // Compute real FFT from decoded PCM audio at this timestamp
                            pcmFftAnalyzer.recordPcmChunk(pcmPtsUs, pcmBytes, pcmBytes.size, channelCount)

                            // Feed PCM into audioEncoder (splitting if larger than encoder input buffer)
                            var offset = 0
                            while (offset < pcmBytes.size && !isAudioEncEosSent) {
                                val encInIdx = audioEncoder.dequeueInputBuffer(10_000)
                                if (encInIdx >= 0) {
                                    val encInBuf = audioEncoder.getInputBuffer(encInIdx)
                                    encInBuf?.clear()
                                    val chunkSize = min(pcmBytes.size - offset, encInBuf?.remaining() ?: (pcmBytes.size - offset))
                                    encInBuf?.put(pcmBytes, offset, chunkSize)
                                    val chunkPtsUs = (totalPcmBytesEncoded * 1_000_000L) / bytesPerSecond
                                    audioEncoder.queueInputBuffer(encInIdx, 0, chunkSize, chunkPtsUs, 0)
                                    totalPcmBytesEncoded += chunkSize
                                    offset += chunkSize
                                } else {
                                    break
                                }
                            }
                        }

                        audioDecoder.releaseOutputBuffer(decOutIdx, false)

                        if (isDecEos || stopRequested) {
                            isAudioDecEosReceived = true
                        }
                    }
                }

                if (isAudioDecEosReceived && !isAudioEncEosSent) {
                    val encInIdx = audioEncoder.dequeueInputBuffer(10_000)
                    if (encInIdx >= 0) {
                        val eosPtsUs = (totalPcmBytesEncoded * 1_000_000L) / bytesPerSecond
                        audioEncoder.queueInputBuffer(
                            encInIdx,
                            0,
                            0,
                            eosPtsUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isAudioEncEosSent = true
                    }
                }

                // Drain audioEncoder output
                var encOutIdx = audioEncoder.dequeueOutputBuffer(audioEncBufferInfo, 5_000)
                while (encOutIdx >= 0 || encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        coordinator.onAudioFormatChanged(audioEncoder.outputFormat)
                        tryStartMuxerAndFlush()
                    } else if (encOutIdx >= 0) {
                        val isCodecConfig = (audioEncBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isCodecConfig && audioEncBufferInfo.size > 0) {
                            val encodedData = audioEncoder.getOutputBuffer(encOutIdx)
                            if (encodedData != null) {
                                encodedData.position(audioEncBufferInfo.offset)
                                encodedData.limit(audioEncBufferInfo.offset + audioEncBufferInfo.size)
                                val bytes = ByteArray(audioEncBufferInfo.size)
                                encodedData.get(bytes)

                                val sample = coordinator.enqueueOrReadySample(
                                    isVideo = false,
                                    data = bytes,
                                    rawPtsUs = audioEncBufferInfo.presentationTimeUs,
                                    flags = audioEncBufferInfo.flags
                                )
                                if (coordinator.isMuxerStarted) {
                                    val info = MediaCodec.BufferInfo().apply {
                                        set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
                                    }
                                    muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(sample.data), info)
                                }
                            }
                        }
                        if ((audioEncBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isAudioEncEosReceived = true
                        }
                        audioEncoder.releaseOutputBuffer(encOutIdx, false)
                    }
                    if (isAudioEncEosReceived) break
                    encOutIdx = audioEncoder.dequeueOutputBuffer(audioEncBufferInfo, 0)
                }

                // --- STEP B: VIDEO ENCODING ---
                if (!isVideoEosSent) {
                    val inputBufIdx = videoCodec.dequeueInputBuffer(10_000)
                    if (inputBufIdx >= 0) {
                        val ptsUs = videoFrameIndex * 1_000_000L / frameRate
                        if (videoFrameIndex >= totalFrames || stopRequested) {
                            videoCodec.queueInputBuffer(
                                inputBufIdx,
                                0,
                                0,
                                ptsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isVideoEosSent = true
                        } else {
                            val elapsedTimeMs = (videoFrameIndex * 1000L) / frameRate
                            val songPositionMs = startPositionMs + elapsedTimeMs
                            val spectrumBars = pcmFftAnalyzer.getSpectrumAt(
                                ptsUs = ptsUs,
                                fallbackRealtimeFrequencies = realtimeFrequenciesProvider?.invoke()
                            )

                            drawCompositionFrame(
                                canvas = canvas,
                                width = width,
                                height = height,
                                song = song,
                                artwork = artworkBitmap,
                                songTimeMs = songPositionMs,
                                frequencies = spectrumBars,
                                isPortrait = isPortrait
                            )

                            renderBitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
                            YuvConverter.encodeYUV420SP(yuvBuffer, argbPixels, width, height)

                            val inputBuffer = videoCodec.getInputBuffer(inputBufIdx)
                            inputBuffer?.clear()
                            inputBuffer?.put(yuvBuffer)

                            videoCodec.queueInputBuffer(inputBufIdx, 0, yuvBuffer.size, ptsUs, 0)
                            videoFrameIndex++

                            if (videoFrameIndex % frameRate == 0) {
                                val currentSec = videoFrameIndex / frameRate
                                _status.value = RecordingStatus.Recording(currentSec, durationSeconds, isPortrait)
                            }
                        }
                    }
                }

                // Drain videoCodec output
                var vOutIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, 5_000)
                while (vOutIndex >= 0 || vOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (vOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        coordinator.onVideoFormatChanged(videoCodec.outputFormat)
                        tryStartMuxerAndFlush()
                    } else if (vOutIndex >= 0) {
                        val isCodecConfig = (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isCodecConfig && videoBufferInfo.size > 0) {
                            val encodedData = videoCodec.getOutputBuffer(vOutIndex)
                            if (encodedData != null) {
                                encodedData.position(videoBufferInfo.offset)
                                encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
                                val bytes = ByteArray(videoBufferInfo.size)
                                encodedData.get(bytes)

                                val sample = coordinator.enqueueOrReadySample(
                                    isVideo = true,
                                    data = bytes,
                                    rawPtsUs = videoBufferInfo.presentationTimeUs,
                                    flags = videoBufferInfo.flags
                                )
                                if (coordinator.isMuxerStarted) {
                                    val info = MediaCodec.BufferInfo().apply {
                                        set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
                                    }
                                    muxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(sample.data), info)
                                }
                            }
                        }
                        if ((videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isVideoEosReceived = true
                        }
                        videoCodec.releaseOutputBuffer(vOutIndex, false)
                    }
                    if (isVideoEosReceived) break
                    vOutIndex = videoCodec.dequeueOutputBuffer(videoBufferInfo, 0)
                }
            }

            _status.value = RecordingStatus.Finalizing

            try { videoCodec.stop() } catch (_: Exception) {}
            try { audioEncoder.stop() } catch (_: Exception) {}
            try { audioDecoder.stop() } catch (_: Exception) {}

            if (coordinator.isMuxerStarted) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                muxer.release()
                muxer = null
            } catch (_: Exception) {}

            if (!isRecordingRequested && !isStopFinalizeRequested && outputFile.exists()) {
                outputFile.delete()
                _status.value = RecordingStatus.Idle
                return@withContext null
            }

            // Verify that the finalized MP4 contains both valid video and audio tracks
            val verification = Mp4StreamVerifier.verifyMp4File(outputFile, width, height)
            if (verification.isValid) {
                val exported = ExportedVideo(
                    file = outputFile,
                    name = outputFile.name,
                    sizeBytes = outputFile.length(),
                    formattedDate = "Just now",
                    uri = exportManager.getShareableUri(outputFile)
                )
                _status.value = RecordingStatus.Completed(exported)
                exported
            } else {
                outputFile.delete()
                _status.value = RecordingStatus.Failed(
                    verification.failureReason ?: "MP4 stream verification failed."
                )
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            _status.value = RecordingStatus.Failed("Recording error: ${e.message}")
            null
        } finally {
            try { videoCodec?.release() } catch (_: Exception) {}
            try { audioEncoder?.release() } catch (_: Exception) {}
            try { audioDecoder?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            isRecordingRequested = false
        }
    }

    internal fun drawCompositionFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        song: Song,
        artwork: Bitmap?,
        songTimeMs: Long,
        frequencies: FloatArray,
        isPortrait: Boolean
    ) {
        // 1. Background Gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.rgb(18, 14, 28), Color.rgb(10, 8, 18), Color.rgb(5, 4, 10)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Artwork Rendering (FIT Mode as required by Section 3.4)
        if (artwork != null && !artwork.isRecycled) {
            val artFrameRect = if (isPortrait) {
                RectF(width * 0.1f, height * 0.12f, width * 0.9f, height * 0.52f)
            } else {
                RectF(width * 0.08f, height * 0.15f, width * 0.45f, height * 0.85f)
            }

            val glowPaint = Paint().apply {
                color = Color.argb(60, 180, 120, 255)
            }
            val glowRect = RectF(artFrameRect)
            glowRect.inset(-12f, -12f)
            canvas.drawRoundRect(glowRect, 28f, 28f, glowPaint)

            val imgW = artwork.width.toFloat()
            val imgH = artwork.height.toFloat()
            val scale = min(artFrameRect.width() / imgW, artFrameRect.height() / imgH)
            val destW = imgW * scale
            val destH = imgH * scale
            val destX = artFrameRect.centerX() - destW / 2f
            val destY = artFrameRect.centerY() - destH / 2f
            val targetRect = RectF(destX, destY, destX + destW, destY + destH)

            val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(artwork, null, targetRect, artPaint)
        }

        // 3. Song Title & Version Badge
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (isPortrait) 36f else 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(190, 160, 255)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val textStartX = if (isPortrait) width * 0.1f else width * 0.50f
        var textY = if (isPortrait) height * 0.58f else height * 0.24f

        canvas.drawText(song.songTitle, textStartX, textY, titlePaint)
        textY += 34f
        canvas.drawText("${song.artist} • Version: ${song.versionName}", textStartX, textY, subPaint)

        // 4. Lyrics Display (Section 3.5: Synchronized progression when timing available)
        val lyrics = song.syncedLyrics
        if (lyrics.isNotEmpty()) {
            val activeIdx = LyricsManager.getActiveLyricIndex(lyrics, songTimeMs)
            var lyricY = if (isPortrait) height * 0.67f else height * 0.40f

            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val startLine = (activeIdx - 1).coerceAtLeast(0)
            val endLine = (activeIdx + 2).coerceAtMost(lyrics.size - 1)

            for (i in startLine..endLine) {
                val line = lyrics[i]
                if (i == activeIdx) {
                    lyricPaint.color = Color.rgb(255, 230, 100)
                    lyricPaint.textSize = 28f
                    lyricPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                } else {
                    lyricPaint.color = Color.argb(140, 220, 220, 255)
                    lyricPaint.textSize = 22f
                    lyricPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                canvas.drawText(line.text, textStartX, lyricY, lyricPaint)
                lyricY += 38f
            }
        } else if (!song.lyricsText.isNullOrBlank()) {
            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(220, 210, 245)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            var lyricY = if (isPortrait) height * 0.67f else height * 0.40f
            song.lyricsText.lines().take(3).forEach { line ->
                canvas.drawText(line, textStartX, lyricY, lyricPaint)
                lyricY += 34f
            }
        }

        // 5. Real Audio-Reactive Visualizer (driven by actual decoded PCM FFT data)
        val vizBottom = if (isPortrait) height * 0.88f else height * 0.85f
        val vizLeft = if (isPortrait) width * 0.08f else width * 0.50f
        val vizWidth = if (isPortrait) width * 0.84f else width * 0.42f
        val barCount = frequencies.size.coerceAtLeast(1)
        val barSpacing = vizWidth / barCount
        val barWidth = barSpacing * 0.68f
        val maxBarHeight = if (isPortrait) 160f else 120f

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until barCount) {
            val mag = frequencies[i].coerceIn(0f, 1f)
            val barHeight = (mag * maxBarHeight) + 8f
            val bx = vizLeft + i * barSpacing
            val by = vizBottom - barHeight

            barPaint.shader = LinearGradient(
                bx, by, bx, vizBottom,
                intArrayOf(Color.rgb(255, 120, 220), Color.rgb(130, 90, 255), Color.rgb(0, 220, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(bx, by, bx + barWidth, vizBottom, 6f, 6f, barPaint)
        }

        // 6. Studio Branding
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 180, 180, 220)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val brandY = height - 24f
        canvas.drawText("Elyzareth Player pro v1.0 • Elyzareth Studio", width * 0.08f, brandY, brandPaint)
    }
}
