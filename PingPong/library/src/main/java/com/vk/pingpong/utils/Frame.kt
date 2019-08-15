package com.vk.pingpong.utils

import java.nio.ByteBuffer

class Frame : MediaUtils.Size() {
    private var yuv: ByteBuffer? = null
    private var rotation = 0
    var timestamp: Long = 0
    private var hasYUVData = true
    var isProcessed = false

    fun pull(frame: Frame) {
        if (!exists()) {
            return
        }

        frame.resize(width, height)
        frame.push(if (yuv != null) yuv!!.array() else null)

        frame.rotation = rotation
        frame.timestamp = timestamp
        frame.setResolution(width, height)
        frame.hasYUVData = hasYUVData
        frame.isProcessed = isProcessed
    }

    fun push(data: ByteArray?) {
        if (data == null || !hasYUVData) {
            return
        }
        if (yuv == null || yuv!!.array().size < data.size) {
            yuv = ByteBuffer.allocateDirect(data.size)
        }
        yuv!!.rewind()
        System.arraycopy(data, 0, yuv!!.array(), 0, data.size)
    }

    fun yuv(): ByteBuffer? {
        if (yuv != null) {
            yuv!!.rewind()
            return yuv
        }
        return null
    }

    private fun exists() = height * width > 0

    fun resize(width: Int, height: Int) {
        setResolution(width, height)
    }

    private fun setResolution(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun clear() {
        yuv = null
        width = 0
        height = 0
        timestamp = 0
        hasYUVData = true
        isProcessed = false
    }

    override fun toString(): String {
        return ("frame: " + width + "x" + height + ", time:" + timestamp
                + " rotation:" + rotation + " processed:" + isProcessed)
    }
}
