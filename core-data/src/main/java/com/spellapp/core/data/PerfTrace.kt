package com.spellapp.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

object PerfTrace {
    @Volatile
    var enabled: Boolean = false

    @Volatile
    var minElapsedMillis: Long = 4L

    inline fun <T> section(
        name: String,
        block: () -> T,
    ): T {
        if (!enabled) return block()
        val startNanos = System.nanoTime()
        return try {
            block()
        } finally {
            logElapsed(name, startNanos)
        }
    }

    suspend inline fun <T> suspendSection(
        name: String,
        block: suspend () -> T,
    ): T {
        if (!enabled) return block()
        val startNanos = System.nanoTime()
        return try {
            block()
        } finally {
            logElapsed(name, startNanos)
        }
    }

    fun mark(message: String) {
        if (enabled) {
            println("$TAG $message")
        }
    }

    fun <T> firstEmission(
        name: String,
        source: Flow<T>,
        sizeOf: (T) -> Int? = { null },
    ): Flow<T> {
        if (!enabled) return source
        return flow {
            val startNanos = System.nanoTime()
            var first = true
            source.collect { value ->
                if (first) {
                    first = false
                    val sizeSuffix = sizeOf(value)?.let { size -> " size=$size" }.orEmpty()
                    logElapsed("$name firstEmission$sizeSuffix", startNanos)
                }
                emit(value)
            }
        }
    }

    fun logElapsed(
        name: String,
        startNanos: Long,
    ) {
        if (!enabled) return
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000
        if (elapsedMillis >= minElapsedMillis) {
            println("$TAG $name ${elapsedMillis}ms")
        }
    }

    private const val TAG = "SpellAppPerf"
}
