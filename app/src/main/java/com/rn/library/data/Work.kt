package com.rn.library.data

import java.io.File
import java.time.LocalDate

enum class WorkType {
    ANIME,
    BOOK,
    MANGA,
    SERIES
}

enum class WorkStatus {
    READ,           // For books and manga
    READING,        // For books and manga
    WATCHING,       // For anime and TV series
    WATCHED,        // For anime and TV series
    IN_PLANS,       // For all works
    ABANDONED       // For all works
}

enum class SeriesType {
    TV_SERIES,
    FILM,
    CARTOON,
    DRAMA
}

enum class MangaType {
    MANGA,
    MANHWA,
    MANHUA
}

enum class AnimeSeason {
    SPRING,   // Весна
    SUMMER,   // Лето
    FALL,     // Осень
    WINTER    // Зима
}

data class Work(
    val id: String,
    val title: String,
    val description: String = "",
    val type: WorkType,
    val coverPath: String? = null,  // Path to cover image
    val coverPaths: List<String> = emptyList(), // Additional covers for the same work
    val chapters: Double? = null,      // For manga (chapters) and books (volumes)
    val volumes: Double? = null,      // For manga (volumes)
    val bookChapters: Double? = null,  // For books (chapters) - separate from volumes
    val episodes: Double? = null,      // For anime (episodes)
    val seasons: Int? = null,       // For series (seasons)
    val year: Int? = null,
    val yearPeriod: String? = null, // Optional year range, e.g. "2009 - 2010"
    val country: String? = null,
    val status: WorkStatus,
    val seriesType: SeriesType? = null,  // For TV series
    val mangaType: MangaType? = null,    // For manga
    val animeSeason: AnimeSeason? = null, // For anime (season of the year)
    val abandonedProgress: Int? = null,   // Legacy progress when abandoned (chapters/episodes/volumes)
    val progress: Double? = null,            // Current reading / watching progress (chapters/episodes)
    val otherTitle: String? = null,      // Alternative title
    val dateRead: String? = null,         // Date when work was read/watched (format: YYYY-MM-DD)
    val rereadDates: List<String> = emptyList(), // Re-read/re-watch dates (YYYY-MM-DD)
    val readingPeriods: List<ReadingPeriod> = emptyList(), // Reading periods across years
    val unitProgress: List<UnitProgress> = emptyList(), // Volumes/seasons and chapter/episode counters
    val activeUnitIndex: Int? = null, // Last selected volume/season in unit-progress UI (0-based)
    val note: String? = null,             // Small user note
    val link: String? = null,             // Primary link to the work
    val link2: String? = null,            // Secondary link to the work
    val links: List<String> = emptyList(), // All links to the work
    val author: String? = null,           // Author of the work
    val updatedAt: Long? = null,          // Last update timestamp (epoch millis)
    val genres: List<String> = emptyList(), // Genres of the work
    val tags: List<String> = emptyList()    // Tags of the work
)

fun Work.allLinks(): List<String> {
    val result = mutableListOf<String>()
    if (links.isNotEmpty()) {
        result.addAll(links)
    }
    link?.takeIf { it.isNotBlank() }?.split("\n", ";", "||")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
        for (l in it) if (!result.contains(l)) result.add(l)
    }
    link2?.takeIf { it.isNotBlank() }?.split("\n", ";", "||")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
        for (l in it) if (!result.contains(l)) result.add(l)
    }
    return result.distinct()
}

data class ReadingPeriod(
    val startDate: String,
    val endDate: String? = null
)

data class UnitProgress(
    val unitName: String,
    val completed: Double,
    val total: Double? = null
)

/** Преобразует путь обложки в модель для Coil (File для локальных путей). */
fun String.toCoverImageData(): Any =
    if (startsWith("/")) File(this) else this

/**
 * Все обложки в порядке добавления.
 * Новый формат: все пути в [coverPaths], [coverPath] — выбранная «главная» для списка.
 * Старый формат: [coverPath] первая, [coverPaths] — остальные без дубликата главной.
 */
fun Work.allCoverPaths(): List<String> {
    val ordered = coverPaths.filter { it.isNotBlank() }
    val primary = coverPath?.takeIf { it.isNotBlank() }
    return when {
        ordered.isNotEmpty() -> {
            if (primary != null && primary !in ordered) listOf(primary) + ordered else ordered.distinct()
        }
        primary != null -> listOf(primary)
        else -> emptyList()
    }
}

/**
 * Случайная обложка; не повторяет [lastCover], если обложек больше двух.
 */
fun pickRandomCoverAvoidingLast(candidates: List<String>, lastCover: String?): String? {
    if (candidates.isEmpty()) return null
    val pool = if (candidates.size > 2 && !lastCover.isNullOrBlank()) {
        candidates.filter { it != lastCover }.ifEmpty { candidates }
    } else {
        candidates
    }
    return pool.random()
}

fun Work.displayCoverPath(today: LocalDate = LocalDate.now()): String? {
    val allCovers = allCoverPaths()
    if (allCovers.isEmpty()) return null
    val seed = (id + today.toString()).hashCode()
    val index = kotlin.math.abs(seed) % allCovers.size
    return allCovers[index]
}

/** Formats a Double for display: whole numbers without decimal part (90.0 -> "90"). */
fun formatDoubleForDisplay(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else value.toString().trimEnd('0').trimEnd('.')