package com.vk.pingpong

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaMuxer
import android.os.MemoryFile
import android.os.SystemClock
import android.util.Log

import com.vk.pingpong.utils.DecoderBase
import com.vk.pingpong.utils.DecoderUtils
import com.vk.pingpong.utils.MediaUtils

import java.io.File
import java.util.ArrayList
import kotlin.math.ceil
import kotlin.math.max

@Suppress("DEPRECATION")
@TargetApi(18)
class PingPongDecoder : DecoderBase() {
    val frameWriter = PingPongFrameWriter()
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var file: File? = null
    private var usePlanar = false
    private var config: MediaUtils.VideoSize? = null
    private var trackIndex = UNKNOWN_TRACK_INDEX

    fun isPrepared() = encoder != null

    fun prepare(config: MediaUtils.VideoSize): Boolean {
        synchronized(this) {
            if (encoder != null) {
                return true
            }
            Log.d(TAG, "prepare encoder")
            frameWriter.clear()
            releaseDecoder()

            this.config = config

            val size = config
            if (size.isEmpty) {
                Log.w(TAG, "empty encoder profile!")
                return false
            }

            encoder = DecoderUtils.codec
            if (encoder == null) {
                return false
            }

            val width = size.width
            val height = size.height
            val nextBitrate = ((width * height * 20 * 4).toDouble() * 0.07).toInt()
            val bitRate = max(nextBitrate, config.videoBitrate)
            val frameRate = 1000 / FRAME_DURATION_MS

            val videoFormat = DecoderUtils.getFormat(width, height, bitRate, frameRate)
            if (DecoderUtils.configureEncoder(encoder!!, videoFormat)) {
                usePlanar = DecoderUtils.isColorFormatPlanar(videoFormat)
                return true
            }
        }
        release()
        return false
    }

    fun start(file: File?): Boolean {
        if (file == null || encoder == null) {
            Log.w(TAG, "can't start to file $file encoder $encoder")
            return false
        }
        this.file = file
        return frameWriter.start(executor, usePlanar)
    }

    override fun canDecode() = frameWriter.frames().isNotEmpty() && !isDecoding

    override fun decode(): Boolean {
        val frames = frameWriter.frames()
        val framesCount = frames.size
        if (framesCount <= 2 || file == null || config == null) {
            return false
        }

        prepare(config!!)

        encoder!!.start()
        isDecoding = true

        return try {
            val repeats = max(ceil(MIN_REPEAT_DURATION_MS.toDouble() / ((2 * framesCount - 2) * FRAME_DURATION_MS)).toInt(), MIN_LOOP_REPEATS)
            decodeLoop(frames, repeats) && isDecoding
        } catch (e: Exception) {
            Log.e(TAG, "can't decode $e")
            false
        } finally {
            release()
            releaseMuxer()
        }
    }

    override fun release() {
        synchronized(this) {
            releaseDecoder()
        }
        frameWriter.clear()
        shutdown()
    }

    private fun decodeLoop(frames: ArrayList<MemoryFile>, repeats: Int): Boolean {
        muxer = MediaMuxer(file!!.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val decodingTimeoutUs = DecoderUtils.DECODING_TIMEOUT_US
        val framesCount = frames.size

        val bufferInfo = MediaCodec.BufferInfo()
        val inputBuffers = encoder!!.inputBuffers
        val outputBuffers = encoder!!.outputBuffers

        val durationUs = ((2 * framesCount - 2) * repeats * FRAME_DURATION_MS * 1000).toLong()
        var reverse = false

        var frameIndex = 0
        var frameIndexTotal = 0
        val startTime = SystemClock.elapsedRealtime()

        while (SystemClock.elapsedRealtime() - startTime <= MAX_ENCODING_TIME_MS) {
            synchronized(this) {
                if (!isDecoding) {
                    return false
                }

                if (frameIndex == 0) {
                    reverse = false
                } else if (frameIndex == framesCount - 1) {
                    reverse = true
                }

                val inputBufferIndex = encoder!!.dequeueInputBuffer(decodingTimeoutUs.toLong())
                if (inputBufferIndex >= 0) {
                    val index = if (reverse) frameIndex - 1 else frameIndex + 1

                    val input = inputBuffers[inputBufferIndex]
                    val frame = frames[index]

                    val length = frameWriter.readToBuffer(frame, input)
                    if (length <= 0) {
                        return false
                    }

                    val pts = (frameIndexTotal * FRAME_DURATION_MS * 1000).toLong()

                    encoder!!.queueInputBuffer(inputBufferIndex, 0, length, pts, 0)
                    frameIndex = index
                    frameIndexTotal++
                }

                val outputBufferIndex = encoder!!.dequeueOutputBuffer(bufferInfo, decodingTimeoutUs.toLong())
                if (outputBufferIndex >= 0) {
                    val output = outputBuffers[outputBufferIndex]
                    if (trackIndex == UNKNOWN_TRACK_INDEX) {
                        trackIndex = muxer!!.addTrack(encoder!!.outputFormat)
                        muxer!!.start()
                    }
                    if (bufferInfo.presentationTimeUs >= durationUs) {
                        Log.d(TAG, "end of stream reached")
                        return true
                    }

                    muxer!!.writeSampleData(trackIndex, output, bufferInfo)
                    encoder!!.releaseOutputBuffer(outputBufferIndex, false)
                }
            }
        }
        return false
    }

    private fun releaseDecoder() {
        isDecoding = false
        try {
            encoder?.stop()
        } catch (e: Throwable) {
            Log.w(TAG, "can't stop encoder $e")
        }
        encoder?.release()
        encoder = null
    }

    private fun releaseMuxer() {
        if (trackIndex != UNKNOWN_TRACK_INDEX) {
            muxer?.stop()
        }
        muxer?.release()
        trackIndex = UNKNOWN_TRACK_INDEX
    }

    companion object {
        private val TAG = PingPongDecoder::class.java.simpleName
        private const val MAX_RECORDING_LOOP_LENGTH_MS = 2000
        const val UNKNOWN_TRACK_INDEX = -1
        const val FRAME_DURATION_MS = 50
        private const val MIN_LOOP_REPEATS = 3
        private const val MAX_ENCODING_TIME_MS = (MAX_RECORDING_LOOP_LENGTH_MS * 8).toLong()
        private const val MIN_REPEAT_DURATION_MS = 3000
    }
}
