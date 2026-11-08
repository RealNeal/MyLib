package com.rn.library.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Человекочитаемый экспорт/импорт статистики (format=2).
 * Таблица: первая строка — ключи, далее строки значений через «|».
 */
object ActivityStatisticsFormat {

    const val FORMAT_VERSION = 2
    const val IMPORT_WORK_ID = "imported_stats"

    private const val SECTION_HEATMAP = "[Heatmap]"
    private const val SECTION_ACTIVITY = "[ActivityByPeriod]"
    private const val TABLE_HEADER = "label | date_from | date_to | read_chapters | watched_episodes"

    data class PeriodRow(
        val label: String = "",
        val dateFrom: LocalDate,
        val dateTo: LocalDate,
        val readUnits: Double,
        val watchedUnits: Double
    )

    fun buildExportText(events: List<ActivityDeltaEvent>): String {
        val entries = buildDailyEntries(events)
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val locale = Locale.forLanguageTag("ru-RU")

        return buildString {
            appendLine("# MyLibrary — статистика активности")
            appendLine("# format=$FORMAT_VERSION")
            appendLine("# Первая строка таблицы — ключи, ниже — значения (разделитель | )")
            appendLine()

            appendLine(SECTION_HEATMAP)
            appendLine("# Heatmap — только по дням")
            appendLine()
            appendTable(
                entries.sortedBy { it.date }.map { e ->
                    val label = e.date.format(DateTimeFormatter.ofPattern("EEE", locale))
                        .replaceFirstChar { it.uppercase() }
                        .take(3)
                    PeriodRow(
                        label = label,
                        dateFrom = e.date,
                        dateTo = e.date,
                        readUnits = e.readUnits,
                        watchedUnits = e.watchedUnits
                    )
                }
            )
            appendLine()

            appendLine(SECTION_ACTIVITY)
            appendLine("# Активность по периодам")
            appendLine()

            appendLine("## Дни")
            appendTable(buildActivityDayRows(entries, weekStart, locale))
            appendLine()

            appendLine("## Недели")
            appendTable(buildActivityWeekRows(entries, weekStart))
            appendLine()

            appendLine("## Месяцы")
            appendTable(buildActivityMonthRows(entries, today.year, locale))
        }.trimEnd() + "\n"
    }

    fun parseImportText(text: String): List<ActivityDeltaEvent> {
        require(text.length <= InputLimits.MAX_TEXT && text.count { it == '\n' } < InputLimits.MAX_EVENTS) { "Statistics limit exceeded" }
        if (!text.contains("format=$FORMAT_VERSION") && !text.contains(SECTION_HEATMAP)) {
            return parseLegacyPipeFormat(text)
        }

        val byDate = linkedMapOf<LocalDate, Pair<Double, Double>>()
        var section: String? = null
        var subsection: String? = null
        var tableHeaders: List<String> = emptyList()

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            when {
                line == SECTION_HEATMAP -> {
                    section = "heatmap"
                    subsection = null
                    tableHeaders = emptyList()
                }
                line == SECTION_ACTIVITY -> {
                    section = "activity"
                    subsection = null
                    tableHeaders = emptyList()
                }
                line.startsWith("##") -> {
                    subsection = line.removePrefix("##").trim()
                    tableHeaders = emptyList()
                }
                isTableHeaderRow(line) -> {
                    tableHeaders = parsePipeColumns(line)
                }
                line.contains("|") -> {
                    if (section != "heatmap") return@forEach
                    val cols = parsePipeColumns(line)
                    if (cols.isEmpty() || isTableHeaderRow(line)) return@forEach
                    val headers = tableHeaders.ifEmpty { defaultHeaders(cols.size) }
                    parseTableRow(cols, headers)?.let { (date, read, watch) ->
                        byDate[date] = read to watch
                    }
                }
                line.contains(" - ") && line.contains("date_from") -> {
                    if (section != "heatmap") return@forEach
                    parseKeyValueRow(line, section, subsection)?.let { (date, read, watch) ->
                        byDate[date] = read to watch
                    }
                }
                line.contains(";") -> {
                    if (section != "heatmap") return@forEach
                    parseLegacySemicolonRow(line, section, subsection)?.let { (date, read, watch) ->
                        byDate[date] = read to watch
                    }
                }
            }
        }

        return byDate.map { (date, totals) ->
            ActivityDeltaEvent(
                date = date,
                workId = IMPORT_WORK_ID,
                readDelta = totals.first,
                watchDelta = totals.second
            )
        }.sortedBy { it.date }
    }

    private fun StringBuilder.appendTable(rows: List<PeriodRow>) {
        if (rows.isEmpty()) return
        appendLine(TABLE_HEADER)
        rows.forEach { appendLine(formatDataRow(it)) }
    }

    private fun formatDataRow(row: PeriodRow): String =
        listOf(
            row.label,
            row.dateFrom.toString(),
            row.dateTo.toString(),
            formatNum(row.readUnits),
            formatNum(row.watchedUnits)
        ).joinToString(" | ")

    private fun isTableHeaderRow(line: String): Boolean =
        parsePipeColumns(line).firstOrNull()?.equals("label", ignoreCase = true) == true

    private fun parsePipeColumns(line: String): List<String> =
        line.split("|").map { it.trim() }

    private fun defaultHeaders(columnCount: Int): List<String> =
        when (columnCount) {
            5 -> listOf("label", "date_from", "date_to", "read_chapters", "watched_episodes")
            4 -> listOf("date_from", "date_to", "read_chapters", "watched_episodes")
            3 -> listOf("date_from", "read_chapters", "watched_episodes")
            else -> emptyList()
        }

    private fun parseTableRow(
        cols: List<String>,
        headers: List<String>
    ): Triple<LocalDate, Double, Double>? {
        val fields = headers.mapIndexed { index, key ->
            key to cols.getOrElse(index) { "" }
        }.toMap()

        val read = fields["read_chapters"]?.toDoubleOrNull() ?: return null
        val watch = fields["watched_episodes"]?.toDoubleOrNull() ?: return null

        val dateStr = fields["date_from"].orEmpty().ifBlank { fields["date_to"].orEmpty() }
        val date = when {
            dateStr.isNotBlank() -> LocalDate.parse(dateStr)
            fields["year_month"]?.isNotBlank() == true -> YearMonth.parse(fields["year_month"]!!).atDay(1)
            else -> return null
        }

        return Triple(date, read, watch)
    }

    private fun parseKeyValueRow(
        line: String,
        section: String?,
        subsection: String?
    ): Triple<LocalDate, Double, Double>? {
        val fields = mutableMapOf<String, String>()
        line.split(";").forEach { segment ->
            val trimmed = segment.trim()
            val separator = trimmed.indexOf(" - ")
            if (separator < 0) return@forEach
            fields[trimmed.substring(0, separator).trim()] = trimmed.substring(separator + 3).trim()
        }
        val read = fields["read_chapters"]?.toDoubleOrNull() ?: return null
        val watch = fields["watched_episodes"]?.toDoubleOrNull() ?: return null
        val date = fields["date_from"]?.let { LocalDate.parse(it) }
            ?: fields["date_to"]?.let { LocalDate.parse(it) }
            ?: fields["year_month"]?.let { YearMonth.parse(it).atDay(1) }
            ?: return null
        return Triple(date, read, watch)
    }

    private fun parseLegacySemicolonRow(
        line: String,
        section: String?,
        subsection: String?
    ): Triple<LocalDate, Double, Double>? {
        val parts = line.split(";").map { it.trim() }
        return try {
            when {
                section == "heatmap" && parts.size >= 3 -> {
                    Triple(LocalDate.parse(parts[0]), parts[1].toDouble(), parts[2].toDouble())
                }
                section == "activity" && subsection == "Дни" && parts.size >= 5 -> {
                    Triple(LocalDate.parse(parts[1]), parts[3].toDouble(), parts[4].toDouble())
                }
                section == "activity" && subsection == "Недели" && parts.size >= 5 -> {
                    Triple(LocalDate.parse(parts[1]), parts[3].toDouble(), parts[4].toDouble())
                }
                section == "activity" && subsection == "Месяцы" && parts.size >= 4 -> {
                    Triple(YearMonth.parse(parts[1]).atDay(1), parts[2].toDouble(), parts[3].toDouble())
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLegacyPipeFormat(text: String): List<ActivityDeltaEvent> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("[") && !it.startsWith("##") }
            .mapNotNull { line ->
                val parts = line.split('|').map { it.trim() }
                if (parts.size == 4 && parts[1].contains("-").not()) {
                    // date|workId|read|watch legacy
                    runCatching {
                        ActivityDeltaEvent(
                            date = LocalDate.parse(parts[0]),
                            workId = parts[1],
                            readDelta = parts[2].toDouble(),
                            watchDelta = parts[3].toDouble()
                        )
                    }.getOrNull()
                } else null
            }
            .toList()

    private data class DailyEntry(
        val date: LocalDate,
        val readUnits: Double,
        val watchedUnits: Double
    )

    private fun buildDailyEntries(events: List<ActivityDeltaEvent>): List<DailyEntry> =
        events
            .groupBy { it.date }
            .map { (date, dayEvents) ->
                DailyEntry(
                    date = date,
                    readUnits = dayEvents.sumOf { it.readDelta },
                    watchedUnits = dayEvents.sumOf { it.watchDelta }
                )
            }
            .filter { it.readUnits + it.watchedUnits > 0.0 }

    private fun buildActivityDayRows(
        entries: List<DailyEntry>,
        weekStart: LocalDate,
        locale: Locale
    ): List<PeriodRow> =
        (0..6).map { index ->
            val date = weekStart.plusDays(index.toLong())
            val read = entries.filter { it.date == date }.sumOf { it.readUnits }
            val watched = entries.filter { it.date == date }.sumOf { it.watchedUnits }
            PeriodRow(
                label = date.format(DateTimeFormatter.ofPattern("EEE", locale))
                    .replaceFirstChar { it.uppercase() }
                    .take(3),
                dateFrom = date,
                dateTo = date,
                readUnits = read,
                watchedUnits = watched
            )
        }

    private fun buildActivityWeekRows(
        entries: List<DailyEntry>,
        weekStart: LocalDate
    ): List<PeriodRow> =
        (0..7).map { offset ->
            val start = weekStart.minusWeeks((7 - offset).toLong())
            val end = start.plusDays(6)
            val read = entries.filter { it.date in start..end }.sumOf { it.readUnits }
            val watched = entries.filter { it.date in start..end }.sumOf { it.watchedUnits }
            PeriodRow(
                label = "${start.dayOfMonth}.${start.monthValue}",
                dateFrom = start,
                dateTo = end,
                readUnits = read,
                watchedUnits = watched
            )
        }

    private fun buildActivityMonthRows(
        entries: List<DailyEntry>,
        year: Int,
        locale: Locale
    ): List<PeriodRow> =
        (1..12).map { month ->
            val ym = YearMonth.of(year, month)
            val start = ym.atDay(1)
            val end = ym.atEndOfMonth()
            val read = entries.filter { it.date in start..end }.sumOf { it.readUnits }
            val watched = entries.filter { it.date in start..end }.sumOf { it.watchedUnits }
            PeriodRow(
                label = start.format(DateTimeFormatter.ofPattern("MMM", locale))
                    .replace(".", "")
                    .take(3)
                    .replaceFirstChar { it.uppercase() },
                dateFrom = start,
                dateTo = end,
                readUnits = read,
                watchedUnits = watched
            )
        }

    private fun formatNum(value: Double): String = kotlin.math.round(value).toLong().toString()
}
