package com.gato.client.util

/**
 * In-app ring buffer for relay diagnostics. System.out is teed into this at
 * relay start, so every println from GatoRelay (auth flow, login errors,
 * [AUTH-DIAG] lines) can be read from the Settings page without a PC.
 */
object RelayLog {

    private const val MAX_LINES = 500

    private val buffer = ArrayDeque<String>()

    val lines: List<String>
        get() = synchronized(buffer) { buffer.toList() }

    fun log(line: String) {
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
    }

    fun clear() = synchronized(buffer) { buffer.clear() }

    fun asText(): String = synchronized(buffer) { buffer.joinToString("\n") }
}
