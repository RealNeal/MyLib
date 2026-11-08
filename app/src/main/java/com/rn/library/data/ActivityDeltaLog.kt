package com.rn.library.data

import android.content.Context
import java.io.File
import java.time.LocalDate

/** Activity and work updates share a Room transaction. Legacy preferences are kept for recovery. */
class ActivityDeltaLog(
    private val context: Context,
    databaseProvider: () -> LibraryDatabase = { LibraryDatabase.get(context) }
) {
    private val database by lazy(databaseProvider)

    private fun ensureMigrated() {
        val dao = database.libraryDao()
        if (dao.hasState("activity_v1")) return
        database.runInTransaction {
            val legacy = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_EVENTS, emptySet()).orEmpty().mapNotNull { decode(it) }
            dao.saveEvents(legacy.map { StoredActivityEvent(it.date.toString(), it.workId, it.readDelta, it.watchDelta) })
            dao.markState(StorageState("activity_v1"))
        }
    }

    fun appendEvent(event: ActivityDeltaEvent): Unit = synchronized(database) {
        if (event.readDelta == 0.0 && event.watchDelta == 0.0) return
        ensureMigrated()
        database.runInTransaction {
            val dao = database.libraryDao()
            val previous = dao.getEvent(event.date.toString(), event.workId)
            val merged = StoredActivityEvent(event.date.toString(), event.workId,
                event.readDelta + (previous?.readDelta ?: 0.0),
                event.watchDelta + (previous?.watchDelta ?: 0.0))
            if (merged.readDelta == 0.0 && merged.watchDelta == 0.0) dao.deleteEvent(merged)
            else {
                require(previous != null || dao.eventCount() < InputLimits.MAX_EVENTS)
                dao.saveEvents(listOf(merged))
            }
        }
    }

    fun deductEventProgress(
        workId: String,
        readDeduct: Double,
        watchDeduct: Double,
        fallbackDate: LocalDate
    ): Unit = synchronized(database) {
        if (readDeduct <= 0.0 && watchDeduct <= 0.0) return
        ensureMigrated()
        val original = database.libraryDao().getEventsForWork(workId)
        val events = original.map {
            ActivityDeltaEvent(LocalDate.parse(it.date), it.workId, it.readDelta, it.watchDelta)
        }.toMutableList()

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
        val updated = cleaned.map { StoredActivityEvent(it.date.toString(), it.workId, it.readDelta, it.watchDelta) }
            .associateBy { it.date to it.workId }
        database.runInTransaction {
            original.forEach { previous ->
                val next = updated[previous.date to previous.workId]
                if (next == null) database.libraryDao().deleteEvent(previous)
                else if (next != previous) database.libraryDao().saveEvents(listOf(next))
            }
        }
    }

    fun loadEvents(): List<ActivityDeltaEvent> = synchronized(database) {
        ensureMigrated()
        database.libraryDao().getEvents().map {
            ActivityDeltaEvent(LocalDate.parse(it.date), it.workId, it.readDelta, it.watchDelta)
        }
    }

    fun mergeEvents(newEvents: List<ActivityDeltaEvent>): Unit = synchronized(database) {
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

    fun replaceAllEvents(events: List<ActivityDeltaEvent>): Unit = synchronized(database) {
        persistEvents(events)
    }

    private fun persistEvents(events: List<ActivityDeltaEvent>) {
        require(events.size <= InputLimits.MAX_EVENTS)
        ensureMigrated()
        database.runInTransaction {
            database.libraryDao().clearEvents()
            database.libraryDao().saveEvents(events.map {
                StoredActivityEvent(it.date.toString(), it.workId, it.readDelta, it.watchDelta)
            })
        }
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

