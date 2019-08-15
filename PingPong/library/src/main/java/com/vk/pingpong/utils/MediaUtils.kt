@file:Suppress("DEPRECATION")

package com.vk.pingpong.utils

import android.graphics.ImageFormat
import android.hardware.Camera
import kotlin.math.ceil

class MediaUtils {
    companion object {
        val TAG = MediaUtils::class.java.simpleName!!

        private const val HI_WIDTH = 720
        private const val HI_HEIGHT = 1280
        private const val HI_RESOLUTION = HI_WIDTH * HI_HEIGHT

        private const val OUTPUT_FORMAT = ImageFormat.NV21

        fun getFrameSize(size: Size): Int {
            val bitsPerPixel = ImageFormat.getBitsPerPixel(OUTPUT_FORMAT)
            val sizeInBits = (size.height * size.width * bitsPerPixel).toLong()
            return ceil(sizeInBits / 8.0).toInt()
        }
    }

    open class Size {
        var width: Int = 0
        var height: Int = 0

        val isEmpty: Boolean
            get() = width * height == 0

        @JvmOverloads
        constructor(w: Int = 0, h: Int = 0) {
            width = w
            height = h
        }

        constructor(size: Camera.Size) {
            width = size.width
            height = size.height
        }

        constructor(size: Size) {
            width = size.width
            height = size.height
        }
    }

    open class VideoSize(w: Int, h: Int) : Size(w, h) {
        val videoBitrate: Int
            get() = makeVideoBitrate()

        private fun makeVideoBitrate(): Int {
            val ratio = (HI_RESOLUTION / (width * height)).toFloat()
            return (2000f * 1000f / ratio).toInt()
        }
    }
}
