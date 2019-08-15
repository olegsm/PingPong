@file:Suppress("DEPRECATION")

package com.vk.pingpong.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log

object DecoderUtils {
    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val I_FRAME_INTERVAL = 3
    const val DECODING_TIMEOUT_US = 2500

    val codec: MediaCodec?
        get() {
            try {
                return MediaCodec.createEncoderByType(MIME_TYPE)
            } catch (e: Exception) {
                Log.w(DecoderUtils::class.java.simpleName, "can't create decoder $e")
            }
            return null
        }

    val info: MediaCodecInfo?
        get() {
            val codecCount = MediaCodecList.getCodecCount()
            for (i in 0 until codecCount) {
                val info = MediaCodecList.getCodecInfoAt(i)
                if (info.isEncoder) {
                    val supportedTypes = info.supportedTypes
                    for (equalsIgnoreCase in supportedTypes) {
                        if (equalsIgnoreCase.equals(MIME_TYPE, ignoreCase = true)) {
                            return info
                        }
                    }
                }
            }
            return null
        }

    fun getFormat(w: Int, h: Int, bitrate: Int, frameRate: Int): MediaFormat {
        return MediaFormat.createVideoFormat(MIME_TYPE, w, h).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0)
        }
    }

    fun configureEncoderForSurface(codec: MediaCodec, videoFormat: MediaFormat) {
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    fun configureEncoder(codec: MediaCodec, videoFormat: MediaFormat): Boolean {
        val codecInfo = info
        if (codecInfo != null) {
            val capabilitiesForType = codecInfo.getCapabilitiesForType(MIME_TYPE)
            for (format in capabilitiesForType.colorFormats) {
                if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                    || format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                    videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, format)
                    try {
                        codec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        return true
                    } catch (e: Exception) {
                        Log.w(DecoderUtils::class.java.simpleName, "can't configure decoder $e")
                    }
                }
            }
        }
        return false
    }

    fun isColorFormatPlanar(videoFormat: MediaFormat) = getColorFormat(videoFormat) == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar

    private fun getColorFormat(videoFormat: MediaFormat) = videoFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT)
}
