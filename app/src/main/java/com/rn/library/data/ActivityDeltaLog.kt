package com.rn.library.data

import android.content.Context
import androidx.core.content.edit
import java.io.File
import java.time.LocalDate

/**
 * Минимальная реализация лога активности, достаточная для компиляции и базовой работы.
 * Хранит события в SharedPreferences как строки.
 */
class ActivityDeltaLog(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun appendEvent(event: ActivityDeltaEvent) {
        if (event.readDelta == 0.0 && event.watchDelta == 0.0) return
        val events = loadEvents().toMutableList()
        mergeInto(events, event)
        persistEvents(events)
    }

    fun deductEventProgress(
        workId: String,
        readDeduct: Double,
        watchDeduct: Double,
        fallbackDate: LocalDate
    ) {
        if (readDeduct <= 0.0 && watchDeduct <= 0.0) return
        val events = loadEvents().toMutableList()

        if (readDeduct > 0.0) {
            var remaining = readDeduct
            val matchingIndices = events.mapIndexedNotNull { index, event ->
                if (event.workId == workId && event.readDelta > 0.0) index else null
            }.sortedByDescending { events[it].date }

            for (idx in matchingIndices) {
                val event = events[idx]
                if (event.readDelta >= remaining) {
                    events[idx] = event.copy(readDelta = event.readDelta - remaining)
                    remaining = 0.0
                    break
                } else {
                    remaining -= event.readDelta
                    events[idx] = event.copy(readDelta = 0.0)
                }
            }

            if (remaining > 0.0) {
                val importedIndices = events.mapIndexedNotNull { index, event ->
                    if (event.workId == ActivityStatisticsFormat.IMPORT_WORK_ID && event.readDelta > 0.0) index else null
                }.sortedByDescending { events[it].date }

                for (idx in importedIndices) {
                    val event = events[idx]
                    if (event.readDelta >= remaining) {
                        events[idx] = event.copy(readDelta = event.readDelta - remaining)
                        remaining = 0.0
                        break
                    } else {
                        remaining -= event.readDelta
                        events[idx] = event.copy(readDelta = 0.0)
                    }
                }
            }
        }

        if (watchDeduct > 0.0) {
            var remaining = watchDeduct
            val matchingIndices = events.mapIndexedNotNull { index, event ->
                if (event.workId == workId && event.watchDelta > 0.0) index else null
            }.sortedByDescending { events[it].date }

            for (idx in matchingIndices) {
                val event = events[idx]
                if (event.watchDelta >= remaining) {
                    events[idx] = event.copy(watchDelta = event.watchDelta - remaining)
                    remaining = 0.0
                    break
                } else {
                    remaining -= event.watchDelta
                    events[idx] = event.copy(watchDelta = 0.0)
                }
            }

            if (remaining > 0.0) {
                val importedIndices = events.mapIndexedNotNull { index, event ->
                    if (event.workId == ActivityStatisticsFormat.IMPORT_WORK_ID && event.watchDelta > 0.0) index else null
                }.sortedByDescending { events[it].date }

                for (idx in importedIndices) {
                    val event = events[idx]
                    if (event.watchDelta >= remaining) {
                        events[idx] = event.copy(watchDelta = event.watchDelta - remaining)
                        remaining = 0.0
                        break
                    } else {
                        remaining -= event.watchDelta
                        events[idx] = event.copy(watchDelta = 0.0)
                    }
                }
            }
        }

        val cleaned = events.filterNot { it.readDelta <= 0.0 && it.watchDelta <= 0.0 }
        persistEvents(cleaned)
    }

    fun loadEvents(): List<ActivityDeltaEvent> {
        return prefs.getStringSet(KEY_EVENTS, emptySet()).orEmpty()
            .mapNotNull { decode(it) }
            .sortedBy { it.date }
    }

    fun mergeEvents(newEvents: List<ActivityDeltaEvent>) {
        if (newEvents.isEmpty()) return
        require(newEvents.size <= InputLimits.MAX_EVENTS)
        val events = linkedMapOf<Pair<LocalDate, String>, ActivityDeltaEvent>()
        for (event in loadEvents() + newEvents) {
            val key = event.date to event.workId
            val previous = events[key]
            val merged = event.copy(readDelta = event.readDelta + (previous?.readDelta ?: 0.0),
                watchDelta = event.watchDelta + (previous?.watchDelta ?: 0.0))
            if (merged.readDelta == 0.0 && merged.watchDelta == 0.0) events.remove(key)
            else events[key] = merged
        }
        require(events.size <= InputLimits.MAX_EVENTS)
        persistEvents(events.values.toList())
    }

    fun replaceAllEvents(events: List<ActivityDeltaEvent>) {
        persistEvents(events)
    }

    private fun mergeInto(events: MutableList<ActivityDeltaEvent>, event: ActivityDeltaEvent) {
        if (event.readDelta == 0.0 && event.watchDelta == 0.0) return
        val index = events.indexOfFirst { it.date == event.date && it.workId == event.workId }
        if (index >= 0) {
            val merged = events[index].copy(
                readDelta = events[index].readDelta + event.readDelta,
                watchDelta = events[index].watchDelta + event.watchDelta
            )
            if (merged.readDelta == 0.0 && merged.watchDelta == 0.0) {
                events.removeAt(index)
            } else {
                events[index] = merged
            }
        } else {
            events.add(event)
        }
    }

    private fun persistEvents(events: List<ActivityDeltaEvent>) {
        val encoded = events.map { encode(it) }.toHashSet()
        prefs.edit { putStringSet(KEY_EVENTS, encoded) }
    }

    fun exportToFile(file: File) {
        val events = loadEvents()
        file.parentFile?.mkdirs()
        file.writeText(ActivityStatisticsFormat.buildExportText(events))
    }

    fun importFromFile(file: File): Int {
        if (!file.exists() || !file.isFile) return 0
        val text = file.inputStream().use { InputLimits.text(it) }
        val events = ActivityStatisticsFormat.parseImportText(text)
        if (text.contains("format=${ActivityStatisticsFormat.FORMAT_VERSION}") ||
            text.contains("[Heatmap]")
        ) {
            replaceAllEvents(events)
        } else {
            mergeEvents(events)
        }
        return events.size
    }

    private fun encode(e: ActivityDeltaEvent): String =
        listOf(e.date.toString(), e.workId, e.readDelta.toString(), e.watchDelta.toString()).joinToString("|")

    private fun decode(raw: String): ActivityDeltaEvent? {
        val parts = raw.split('|')
        if (parts.size != 4) return null
        return runCatching {
            ActivityDeltaEvent(
                date = LocalDate.parse(parts[0]),
                workId = parts[1],
                readDelta = parts[2].toDouble(),
                watchDelta = parts[3].toDouble()
            )
        }.getOrNull()
    }

    companion object {
        const val EXPORT_FILENAME = "activity_statistics.txt"
        private const val PREFS = "activity_delta_log"
        private const val KEY_EVENTS = "events"
    }
}

