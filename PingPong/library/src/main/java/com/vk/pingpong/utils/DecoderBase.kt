package com.vk.pingpong.utils

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

abstract class DecoderBase {
    protected val executor: ExecutorService = Executors.newSingleThreadExecutor()
    @Volatile
    var isDecoding = false
        protected set

    interface Callback {
        fun onStart()
        fun onComplete(result: Boolean)
    }

    abstract fun release()
    abstract fun decode(): Boolean

    fun decode(callback: Callback) = executor.execute { doEncode(callback) }
    fun decodeSync(callback: Callback) = doEncode(callback)

    protected open fun canDecode(): Boolean = false

    protected fun shutdown() {
        if (!executor.isShutdown) {
            executor.shutdown()
        }
    }

    private fun doEncode(callback: Callback) {
        if (canDecode()) {
            callback.onStart()
            callback.onComplete(decode())
        }
    }
}
