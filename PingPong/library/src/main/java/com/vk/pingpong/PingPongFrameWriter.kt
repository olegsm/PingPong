package com.vk.pingpong

import android.os.MemoryFile
import android.util.Log
import com.vk.pingpong.utils.ColorConverter
import com.vk.pingpong.utils.Frame

import com.vk.pingpong.utils.MediaUtils

import java.io.Closeable
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.concurrent.ExecutorService

class PingPongFrameWriter {
    private val lock = Object()
    private val frames = ArrayList<MemoryFile>()
    private var frameBuffer: ByteBuffer? = null
    private var size: MediaUtils.Size? = null
    private var usePlanarConvert = false
    private var running = false
    private var waiting = true
    private val frame = Frame()
    private val buffer = ByteArray(4096)

    fun setSize(size: MediaUtils.Size) {
        synchronized(lock) {
            this.size = MediaUtils.Size(size)
            this.frame.resize(size.width, size.height)
        }
    }

    fun start(service: ExecutorService, planarConvert: Boolean): Boolean {
        synchronized(lock) {
            if (size!!.isEmpty) {
                Log.w(TAG, "can't start on empty size!")
                return false
            }
            usePlanarConvert = planarConvert
            if (!running) {
                frameBuffer = ByteBuffer.allocateDirect(MediaUtils.getFrameSize(size!!))
                running = true
                service.execute { process() }
            }
        }
        return true
    }

    fun stop() {
        synchronized(lock) {
            if (running) {
                running = false
                frameBuffer = null
                frame.clear()
                signal()
            }
        }
    }

    fun push(frame: Frame) {
        synchronized(lock) {
            if (running && frame.yuv() != null) {
                frame.pull(this.frame)
                signal()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            for (file in frames) {
                file.close()
            }
            frames.clear()
        }
    }

    fun frames(): ArrayList<MemoryFile> {
        synchronized(lock) {
            return frames
        }
    }

    fun readToBuffer(file: MemoryFile, dstBuffer: ByteBuffer): Int {
        dstBuffer.clear()
        val inputStream = file.inputStream
        var total = 0
        try {
            val size = dstBuffer.limit()
            var read: Int
            while (true) {
                read = inputStream.read(buffer)
                if (read <= 0) {
                    return total
                }
                if (total + read > size) {
                    read = size - total
                }
                dstBuffer.put(buffer, 0, read)
                total += read
            }
        } catch (e: Throwable) {
            Log.d(TAG, "can't read frame $e")
            return 0
        } finally {
            closeStream(inputStream)
        }
    }

    private fun signal() {
        waiting = false
        lock.notifyAll()
    }

    private fun waitForFrame() {
        while (running && waiting) {
            try {
                lock.wait()
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun process() {
        while (running) {
            synchronized(lock) {
                waitForFrame()
                waiting = true

                if (!running) {
                    return
                }

                allocBuffer(frame)
                if (ColorConverter.convertNV21toI420(frame.yuv(), frame.width, frame.height, frameBuffer, usePlanarConvert)) {
                    push(frameBuffer)
                }
            }
        }
    }

    private fun push(frame: ByteBuffer?) {
        if (frame == null) return

        var file: MemoryFile? = null
        try {
            val data = frame.array()
            if (data.isNotEmpty()) {
                file = MemoryFile(null, data.size)
                file.writeBytes(data, 0, 0, data.size)
                frames.add(file)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "can't create frame mmap")
            file?.close()
        }
    }

    private fun allocBuffer(frame: Frame?) {
        if (frame?.yuv() == null) {
            return
        }
        if (frameBuffer == null || frameBuffer!!.array().size < frame.yuv()!!.array().size) {
            frameBuffer = ByteBuffer.allocateDirect(MediaUtils.getFrameSize(frame))
        }
    }

    private fun closeStream(closeable: Closeable?) {
        try {
            closeable?.close()
        } catch (ignored: Exception) {}
    }

    companion object {
        private val TAG = PingPongFrameWriter::class.java.simpleName
    }
}
