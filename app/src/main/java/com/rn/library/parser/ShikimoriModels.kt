package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.MangaType
import com.rn.library.data.SeriesType
import com.rn.library.data.WorkType

enum class ShikimoriFilterType {
    ALL,
    ANIME,
    MANGA,
    RANOBE
}

data class ShikimoriSearchResult(
    val id: Long,
    val name: String,
    val russian: String,
    val kind: String,
    val year: Int?,
    val coverUrl: String?,
    val episodes: Int? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val targetType: WorkType,
    val endpoint: String,
    val url: String,
    val source: MediaSource = MediaSource.SHIKIMORI,
    val stringId: String = id.toString()
)

data class ParsedWorkMetadata(
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val description: String = "",
    val type: WorkType,
    val mangaType: MangaType? = null,
    val seriesType: SeriesType? = null,
    val animeSeason: AnimeSeason? = null,
    val year: Int? = null,
    val episodes: Double? = null,
    val chapters: Double? = null,
    val volumes: Double? = null,
    val bookChapters: Double? = null,
    val coverUrl: String? = null,
    val author: String? = null,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val link: String? = null,
    val source: MediaSource = MediaSource.SHIKIMORI,
    val additionalCovers: List<String> = emptyList(),
    val additionalLinks: List<String> = emptyList()
)

object ParserUtils {
    /**
     * Cleans up HTML entities, tags, BBCode, non-breaking spaces, and excessive whitespace
     * from descriptions across all parsers.
     */
    fun cleanDescription(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw

        // Decode common HTML entities
        text = text.replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&hellip;", "…")
            .replace("&#8212;", "—")
            .replace("&#8211;", "–")
            .replace("&#8216;", "'")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#8230;", "…")

        // Decode numeric decimal entities &#123;
        text = Regex("""&#(\d+);""").replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..65535) code.toChar().toString() else match.value
        }

        // Decode hex entities &#x1F;
        text = Regex("""&#x([0-9a-fA-F]+);""").replace(text) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..65535) code.toChar().toString() else match.value
        }

        // Replace <br> and </p> with newlines before stripping other tags
        text = text.replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</p>"""), "\n\n")

        // Strip remaining HTML tags
        text = text.replace(Regex("""<[^>]+>"""), "")

        // Strip BBCode like [b], [/b], [i], [spoiler], [url=...], etc.
        text = text.replace(Regex("""\[/?[a-zA-Z0-9_\-]+(=[^\]]+)?\]"""), "")

        // Replace non-breaking spaces and unicode spaces with regular space
        text = text.replace('\u00A0', ' ')
            .replace('\u2007', ' ')
            .replace('\u202F', ' ')

        // Normalize carriage returns and excessive newlines
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        text = Regex("""\n{3,}""").replace(text, "\n\n")

        // Remove HTML entities like &bull; and bullet points if present
        text = text.replace(Regex("""&bull;?"""), "")

        // Unescape escaped quotes, slashes, and backslashes
        text = text.replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
        // Remove stray backslashes before non-word characters
        text = Regex("""\\([^a-zA-Z0-9\s])""").replace(text) { it.groupValues[1] }
        text = text.replace("\\", "")

        // Trim each line
        text = text.lines().joinToString("\n") { it.trimEnd() }

        return text.trim()
    }

    /**
     * Cleans general text (titles, names, alternate titles), removing quotes escapes,
     * backslashes, &bull, and discarding "null" strings.
     */
    fun cleanText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.trim()
        if (text.equals("null", ignoreCase = true)) return ""

        text = text.replace(Regex("""&bull;?"""), "")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\/", "/")
            .replace("\\\\", "\\")
        text = Regex("""\\([^a-zA-Z0-9\s])""").replace(text) { it.groupValues[1] }
        text = text.replace("\\", "")

        return if (text.trim().equals("null", ignoreCase = true)) "" else text.trim()
    }

    /**
     * Checks if a string represents an actual author rather than a scanlation team, translator, or placeholder.
     */
    fun isValidAuthor(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val clean = cleanText(name).trim('-', '—', '–', '.', ',', ' ', ':', ';').trim()
        if (clean.isBlank() || clean.equals("null", ignoreCase = true) || clean.length <= 1) return false
        val lower = clean.lowercase()

        val forbiddenKeywords = listOf(
            "перевод", "команда", "scan", "team", "transl",
            "group", "scanlate", "edit", "sub", "dub", "milf",
            "release", "chapter", "глав", "сайт", "vk.com",
            "t.me", "discord", "admin", "модератор", "user",
            "не указан", "неизвест", "unknown", "none", "rulate",
            "рулейт", "отсутств", "нет автора", "аноним", "n/a"
        )

        if (forbiddenKeywords.any { lower.contains(it) }) return false
        if (lower == "нет" || lower == "автор" || lower == "неизвестен") return false

        return true
    }

    /**
     * Cleans an author name, returning null if it looks like a translation team, is blank, or is "null".
     */
    fun sanitizeAuthor(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val cleaned = cleanText(name).trim('-', '—', '–', '.', ',', ' ', ':', ';').trim()
        if (cleaned.isBlank() || cleaned.equals("null", ignoreCase = true)) return null
        if (!isValidAuthor(cleaned)) return null
        return cleaned
    }

    /**
     * Upgrades cover image URLs to their highest quality / original full-resolution version
     * across all sources (except Shikimori).
     */
    fun upgradeCoverUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val url = rawUrl.trim()

        // Skip Shikimori covers as requested
        if (url.contains("shikimori.one") || url.contains("shikimori.io") || url.contains("shikimori.me")) {
            return url
        }

        var result = url

        // 1. IMDb / Amazon Media CDN
        if (result.contains("media-amazon.com") || result.contains("images-amazon.com") || result.contains("imdb.com")) {
            result = result.replace(Regex("""\._V1_.*?\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE), "._V1_.$1")
        }

        // 2. Ranobes (remove DLE /thumbs/)
        if (result.contains("ranobes.net") || result.contains("ranobes.top")) {
            result = result.replace("/thumbs/", "/")
        }

        // 3. Royal Road (covers-small / covers-large -> covers-full)
        if (result.contains("royalroad") || result.contains("royalroadcdn.com")) {
            result = result.replace("/covers-small/", "/covers-full/")
                .replace("/covers-large/", "/covers-full/")
        }

        // 4. Rulate (remove square crop -220220 thumbnail suffix)
        if (result.contains("rulate.ru") || result.contains("/i/book/")) {
            result = result.replace(Regex("""-\d{4,}\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE), ".$1")
        }

        // 5. MangaDex (remove .512.jpg and .256.jpg thumbnail suffixes)
        if (result.contains("mangadex.org")) {
            result = result.replace(Regex("""\.(512|256)\.jpg$""", RegexOption.IGNORE_CASE), "")
        }

        // 6. MangaLib / AnimeLib / RanobeLib (remove _thumb)
        if (result.contains("cdnlibs.org") || result.contains("mangalib") || result.contains("animelib") || result.contains("ranobelib")) {
            result = result.replace(Regex("""_thumb\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE), ".$1")
        }

        // 7. RanobeHub (switch to size=original)
        if (result.contains("ranobehub.org")) {
            val mediaId = Regex("""(?:/api/media/|%2Fapi%2Fmedia%2F)(\d+)""").find(result)?.groupValues?.get(1)
            if (mediaId != null) {
                result = "https://ranobehub.org/api/media/$mediaId?size=original"
            }
        }

        // 8. WTR-LAB (remove resize query params)
        if (result.contains("wtr-lab.com")) {
            result = result.substringBefore("?")
        }

        // 9. AniList (medium / large -> extraLarge)
        if (result.contains("anilistcdn") || result.contains("anilist.co")) {
            result = result.replace("/medium/", "/extraLarge/")
                .replace("/large/", "/extraLarge/")
        }

        // 10. Remanga (low / mid -> high)
        if (result.contains("remanga.org")) {
            result = result.replace("/low.", "/high.")
                .replace("/mid.", "/high.")
        }

        // 11. WordPress-based sites (Scribblehub, etc. - remove dimensions -150x150)
        if (result.contains("scribblehub.com")) {
            result = result.replace(Regex("""-\d+x\d+\.(jpe?g|png|webp)$""", RegexOption.IGNORE_CASE), ".$1")
        }

        return result
    }

    fun isFuzzyTitleMatch(title1: String?, title2: String?): Boolean {
        if (title1.isNullOrBlank() || title2.isNullOrBlank()) return false
        val n1 = normalizeTitle(title1)
        val n2 = normalizeTitle(title2)
        if (n1 == n2) return true
        if (n1.length >= 4 && n2.length >= 4 && (n1.contains(n2) || n2.contains(n1))) return true
        return false
    }

    private fun normalizeTitle(str: String): String {
        return str.lowercase()
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Проверяет, принадлежит ли произведение в библиотеке ДРУГОМУ источнику, чем [currentSourceDomain].
     * Предотвращает нежелательное склеивание дубликатов с разных источников.
     */
    fun belongsToDifferentSource(work: com.rn.library.data.Work, currentSourceDomain: String): Boolean {
        val allLinks = listOfNotNull(work.link, work.link2)
        if (allLinks.isEmpty()) return false

        fun normalizeSourceGroup(domainOrUrl: String): String {
            val d = domainOrUrl.lowercase()
            return when {
                d.contains("mangalib") || d.contains("ranobelib") || d.contains("animelib") ||
                        d.contains("lib.social") || d.contains("cdnlibs") || d.contains("lib.") -> "libsocial"
                d.contains("shikimori") -> "shikimori"
                d.contains("anilist") -> "anilist"
                d.contains("myanimelist") -> "mal"
                d.contains("remanga") -> "remanga"
                d.contains("anixart") || d.contains("anixmirai") -> "anixart"
                d.contains("aniliberty") -> "aniliberty"
                d.contains("animetka") -> "animetka"
                d.contains("ranobehub") -> "ranobehub"
                d.contains("rulate") -> "rulate"
                d.contains("wtr-lab") -> "wtrlab"
                d.contains("royalroad") -> "royalroad"
                d.contains("ranobes") -> "ranobes"
                d.contains("scribblehub") -> "scribblehub"
                d.contains("mangadex") -> "mangadex"
                d.contains("imdb") -> "imdb"
                d.contains("kinopoisk") -> "kinopoisk"
                else -> d
            }
        }

        val targetGroup = normalizeSourceGroup(currentSourceDomain)

        return allLinks.any { link ->
            val linkGroup = normalizeSourceGroup(link)
            linkGroup != targetGroup && linkGroup != link.lowercase()
        }
    }

    /**
     * Normalizes a list of chips (genres or tags):
     * - Capitalizes first letter
     * - Trims whitespace and punctuation
     * - Case-insensitively deduplicates so e.g. "Магия" and "магия" become a single "Магия"
     */
    fun normalizeChips(items: List<String>): List<String> {
        if (items.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val seenLower = mutableSetOf<String>()

        for (item in items) {
            val trimmed = item.trim().trim(',', ';', '.')
            if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) continue
            val lower = trimmed.lowercase()
            if (!seenLower.contains(lower)) {
                seenLower.add(lower)
                val normalized = trimmed.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
                result.add(normalized)
            }
        }
        return result
    }
}

/**
 * Safely extracts a string from JSONObject, returning null if missing, null, blank, or literal "null".
 */
fun org.json.JSONObject.optNonEmptyString(key: String): String? {
    if (isNull(key)) return null
    val raw = optString(key, "")
    val cleaned = ParserUtils.cleanText(raw)
    return cleaned.ifBlank { null }
}

