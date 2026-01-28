package io.github.devhyper.openvideoeditor.misc

import android.util.Log
import androidx.compose.runtime.mutableStateListOf

object AppLogger {
    private const val TAG = "OpenVideoEditor"
    val logs = mutableStateListOf<LogEntry>()

    data class LogEntry(val level: String, val message: String, val timestamp: Long = System.currentTimeMillis())

    fun d(message: String) {
        Log.d(TAG, message)
        addLog("DEBUG", message)
    }

    fun e(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
        addLog("ERROR", "$message ${error?.localizedMessage ?: ""}")
    }

    fun i(message: String) {
        Log.i(TAG, message)
        addLog("INFO", message)
    }

    private fun addLog(level: String, message: String) {
        logs.add(0, LogEntry(level, message))
        if (logs.size > 100) {
            logs.removeLast()
        }
    }
}