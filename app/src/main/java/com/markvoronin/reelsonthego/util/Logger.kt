package com.markvoronin.reelsonthego.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {

    private const val TAG = "ReelsLogger"
    private const val MAX_LOGS = 50

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(message: String, isError: Boolean = false) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, message, isError)

        if (isError) {
            Log.e(TAG, message)
        } else {
            Log.d(TAG, message)
        }

        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Newest first
        if (currentList.size > MAX_LOGS) {
            currentList.removeAt(currentList.lastIndex)
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}

data class LogEntry(
    val timestamp: String,
    val message: String,
    val isError: Boolean = false
)
