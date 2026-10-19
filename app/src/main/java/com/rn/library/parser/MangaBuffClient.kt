package com.rn.library.parser

import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MangaBuffClient {
    private const val BASE_URL = "https://mangabuff.ru"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun getBaseUrl(): String = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_MANGABUFF).ifBlank { BASE_URL }

    fun isMangaBuffUrl(url: String): Boolean {
        return url.contains("mangabuff.ru/manga/", ignoreCase = true)
    }

    fun parseMangaBuffSlug(url: String): String? {
        val regex = Regex("""mangabuff\.ru/manga/([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url.trim()) ?: return null
        val slug = match.groupValues[1]
        // Исключаем системные разделы
        if (slug in setOf("top", "new", "ongoing", "completed", "popular")) return null
        return slug
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "${getBaseUrl()}/search?q=$encoded&type=manga"
            val html = fetchHtml(url) ?: return@withContext Result.failure(Exception("Пустой ответ от MangaBuff"))

            val list = mutableListOf<ShikimoriSearchResult>()
            val cardRegex = Regex(
                """<a\s+href="https?://mangabuff\.ru/manga/([a-zA-Z0-9_\-]+)"\s+class="cards__item"(?:[^>]*data-id="(\d+)")?[^>]*>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE
            )

            val matches = cardRegex.findAll(html)
            for (match in matches) {
                val slug = match.groupValues[1]
                val idStr = match.groupValues[2]
                val cardContent = match.groupValues[3]

                if (slug in setOf("top", "new", "ongoing", "completed", "popular")) continue

                val id = idStr.toLongOrNull() ?: (slug.hashCode().toLong() and 0x7FFFFFFF)

                val nameRegex = Regex("""<div class="cards__name">([^<]+)</div>""", RegexOption.IGNORE_CASE)
                val rawName = nameRegex.find(cardContent)?.groupValues?.get(1)?.trim().orEmpty()
                val title = ParserUtils.cleanText(rawName)
                if (title.isBlank()) continue

                val infoRegex = Regex("""<div class="cards__info">([^<]+)</div>""", RegexOption.IGNORE_CASE)
                val info = infoRegex.find(cardContent)?.groupValues?.get(1)?.trim().orEmpty()

                val imgRegex = Regex("""background-image:\s*url\(['"]?([^'"\)]+)['"]?\)""", RegexOption.IGNORE_CASE)
                val rawCover = imgRegex.find(cardContent)?.groupValues?.get(1)
                val coverUrl = normalizeCoverUrl(rawCover, slug)

                val targetType = WorkType.MANGA

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = title,
                        russian = title,
                        kind = info.ifBlank { "манга" }.lowercase(),
                        year = null,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = null,
                        volumes = null,
                        targetType = targetType,
                        endpoint = "manga",
                        url = "${getBaseUrl()}/manga/$slug",
                        source = MediaSource.MANGABUFF,
                        stringId = slug
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(slugOrUrl: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val slug = parseMangaBuffSlug(slugOrUrl) ?: slugOrUrl.trim().trim('/')
            val url = if (slug.startsWith("http")) slug else "${getBaseUrl()}/manga/$slug"
            val html = fetchHtml(url) ?: return@withContext Result.failure(Exception("Не удалось загрузить страницу MangaBuff"))

            // Название
            val titleRegex = Regex("""<h1 class="manga__name">([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE)
            val rawTitle = titleRegex.find(html)?.groupValues?.get(1)?.let { ParserUtils.cleanText(it) }
                ?: return@withContext Result.failure(Exception("Не удалось найти название на MangaBuff"))

            // Альтернативные названия
            val altTitles = mutableListOf<String>()
            val altBlockRegex = Regex("""<h3 class="manga__name-alt">([\s\S]*?)</h3>""", RegexOption.IGNORE_CASE)
            val altBlock = altBlockRegex.find(html)?.groupValues?.get(1)
            if (!altBlock.isNullOrBlank()) {
                val spanRegex = Regex("""<span>([^<]+)</span>""", RegexOption.IGNORE_CASE)
                val spans = spanRegex.findAll(altBlock).map { ParserUtils.cleanText(it.groupValues[1]) }.filter { it.isNotBlank() }.toList()
                if (spans.isNotEmpty()) {
                    altTitles.addAll(spans)
                } else {
                    val rawCleaned = ParserUtils.cleanText(altBlock.replace(Regex("<[^>]+>"), " "))
                    if (rawCleaned.isNotBlank()) {
                        altTitles.add(rawCleaned)
                    }
                }
            }

            // Тип произведения (Манхва, Маньхуа, Манга)
            val middleLinksRegex = Regex("""<div class="manga__middle-links[^"]*">([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
            val middleLinksBlock = middleLinksRegex.find(html)?.groupValues?.get(1).orEmpty()

            val typeLinkRegex = Regex("""<a[^>]*href="[^"]*/types/([^/"]+)"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            val typeMatch = typeLinkRegex.find(middleLinksBlock)
            val typeSlug = typeMatch?.groupValues?.get(1)?.lowercase().orEmpty()
            val typeText = typeMatch?.groupValues?.get(2)?.lowercase().orEmpty()

            val mangaType = when {
                typeSlug.contains("manxva") || typeText.contains("манхва") -> MangaType.MANHWA
                typeSlug.contains("manxya") || typeText.contains("маньхуа") -> MangaType.MANHUA
                else -> MangaType.MANGA
            }

            // Год выпуска
            val yearRegex = Regex("""<a[^>]*href="[^"]*/types/[^/"]+/(\d{4})"[^>]*>(\d{4})</a>""", RegexOption.IGNORE_CASE)
            val year = yearRegex.find(middleLinksBlock)?.groupValues?.get(1)?.toIntOrNull()

            // Обложка
            val posterImgRegex = Regex("""<div class="manga__poster"[^>]*>[\s\S]*?<img[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
            val posterSrc = posterImgRegex.find(html)?.groupValues?.get(1)
            val coverUrl = normalizeCoverUrl(posterSrc, slug)

            // Главы
            val chaptersRegex = Regex("""Главы\s*\((\d+)\)""", RegexOption.IGNORE_CASE)
            val chaptersCount = chaptersRegex.find(html)?.groupValues?.get(1)?.toDoubleOrNull()

            // Жанры
            val genres = mutableListOf<String>()
            val genreRegex = Regex("""<a[^>]*href="[^"]*/genres/[^"]*"[^>]*class="tags__item"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            genreRegex.findAll(html).forEach { m ->
                val g = ParserUtils.cleanText(m.groupValues[1])
                if (g.isNotBlank() && g !in genres) genres.add(g)
            }

            // Теги
            val tags = mutableListOf<String>()
            val tagRegex = Regex("""<a[^>]*href="[^"]*/manga\?tags[^"]*"[^>]*class="tags__item"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
            tagRegex.findAll(html).forEach { m ->
                val t = ParserUtils.cleanText(m.groupValues[1])
                if (t.isNotBlank() && t !in tags) tags.add(t)
            }

            // Описание
            val descRegex = Regex("""<div class="manga__description">([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
            var description = descRegex.find(html)?.groupValues?.get(1)?.let { ParserUtils.cleanDescription(it) }.orEmpty()
            if (description.isBlank()) {
                val ogDescRegex = Regex("""<meta[^>]*property="og:description"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE)
                description = ogDescRegex.find(html)?.groupValues?.get(1)?.let { ParserUtils.cleanDescription(it) }.orEmpty()
            }

            Result.success(
                ParsedWorkMetadata(
                    title = rawTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = description,
                    type = WorkType.MANGA,
                    mangaType = mangaType,
                    year = year,
                    chapters = chaptersCount,
                    volumes = null,
                    coverUrl = coverUrl,
                    author = null,
                    genres = genres,
                    tags = tags,
                    link = "${getBaseUrl()}/manga/$slug",
                    source = MediaSource.MANGABUFF
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeCoverUrl(raw: String?, slug: String): String {
        if (raw.isNullOrBlank()) {
            return "${getBaseUrl()}/img/manga/posters/$slug.jpg"
        }
        val clean = raw.replace("/x180/", "/").replace("/x300/", "/").substringBefore("?")
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("/") -> "${getBaseUrl()}$clean"
            else -> "${getBaseUrl()}/$clean"
        }
    }

    private fun fetchHtml(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            conn.setRequestProperty("Referer", "https://mangabuff.ru/")
            conn.instanceFollowRedirects = true
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
