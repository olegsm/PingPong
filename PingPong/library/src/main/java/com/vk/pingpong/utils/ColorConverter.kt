package com.vk.pingpong.utils

import java.nio.ByteBuffer

object ColorConverter {
     fun convertNV21toI420(src: ByteBuffer?, width: Int, height: Int, dst: ByteBuffer?, planar: Boolean): Boolean {
        if (src == null || dst == null || width * height == 0) {
            return false
        }
        src.rewind()
        dst.rewind()

        val s = src.array()
        val d = dst.array()

        val ySize = width * height
        val uSize = ySize / 4

        // NV21: Y..Y + VUV...U
        // I420: Y..Y + U.U + V.V (planar)
        // I420: Y..Y + UVU...V (semi planar)

        // copy Y
        System.arraycopy(s, 0, d, 0, ySize)

        for (k in 0 until uSize) {
            if (planar) {
                d[ySize + k] = s[ySize + k * 2 + 1] // copy U
                d[ySize + uSize + k] = s[ySize + k * 2] // copy V
            } else {
                d[ySize + k * 2] = s[ySize + k * 2 + 1] // copy U
                d[ySize + k * 2 + 1] = s[ySize + k * 2] // copy V
            }
        }
        return true
    }
}