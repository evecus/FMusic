package cn.toside.music.compose.js

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch

/**
 * JS 引擎线程：QuickJSContext 非线程安全，两个沙箱（内置 SDK / 用户脚本）
 * 的所有 evaluate 与 native 回调注入都串行在这一个线程上执行。
 */
class JsEngineThread(name: String = "js-engine") : HandlerThread(name) {

    val handler: Handler by lazy {
        start()
        Handler(looper)
    }

    fun post(runnable: Runnable): Boolean = handler.post(runnable)

    fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean =
        handler.postDelayed(runnable, delayMillis)

    /** 同步等待（在 JS 线程上执行 block），带超时 */
    fun runBlocking(timeoutMs: Long, block: () -> Unit) {
        if (Thread.currentThread() == handler.looper.thread) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        post {
            try {
                block()
            } catch (e: Throwable) {
                error = e
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            throw java.util.concurrent.TimeoutException("js engine timeout")
        }
        error?.let { throw it }
    }

    fun quitEngine() {
        handler.looper.quit()
    }
}
