package com.rn.library.parser

import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RanobesClient {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun getBaseUrl(): String = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_RANOBES)

    fun isRanobesUrl(url: String): Boolean {
        return Regex("""ranobes\.[a-z]+/(?:novels|ranobe)/""", RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    fun parseRanobesNovelId(url: String): Long? {
        val regex = Regex("""ranobes\.[a-z]+/(?:novels|ranobe)/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)?.toLongOrNull()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "").trim().ifBlank { query.trim() }
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val html = fetchHtml("/search/$encoded/")
                ?: return@withContext Result.failure(Exception("Пустой ответ от Ranobes"))

            val list = mutableListOf<ShikimoriSearchResult>()
            val itemRegex = Regex(
                """<h[1-4]\b[^>]*class=["'][^"']*title[^"']*["'][^>]*>\s*<a\b[^>]*href=["']([^"']*(?:novels|ranobe)/(\d+)-([^"']*?)\.html)["'][^>]*>([\s\S]*?)</a>""",
                RegexOption.IGNORE_CASE
            )
            val coverRegex = Regex("""background-image:\s*url\(['"]?([^'"\)]+)['"]?\)""")

            // Split by articles
            val articleBlocks = html.split("<article")
            for (block in articleBlocks.drop(1)) {
                val match = itemRegex.find(block) ?: continue
                val fullUrl = match.groupValues[1]
                val novelId = match.groupValues[2].toLongOrNull() ?: continue
                val title = ParserUtils.cleanText(match.groupValues[4])
                if (title.isBlank()) continue

                val rawCover = coverRegex.find(block)?.groupValues?.get(1)?.trim('\'', '"')
                val cover = ParserUtils.upgradeCoverUrl(rawCover)

                // Try to find chapter count in the block
                val chapters = Regex("""(\d+)\+?\s*chapters""", RegexOption.IGNORE_CASE)
                    .find(block)?.groupValues?.get(1)?.toIntOrNull()

                list.add(
                    ShikimoriSearchResult(
                        id = novelId,
                        name = title,
                        russian = "",
                        kind = "novel",
                        year = null,
                        coverUrl = cover,
                        episodes = null,
                        chapters = chapters,
                        volumes = null,
                        targetType = WorkType.BOOK,
                        endpoint = "novels",
                        url = if (fullUrl.startsWith("http")) fullUrl else "${getBaseUrl()}$fullUrl",
                        source = MediaSource.RANOBES,
                        stringId = fullUrl
                    )
                )
            }

            // Fallback global regex if blocks didn't match
            if (list.isEmpty()) {
                val matches = itemRegex.findAll(html)
                for (m in matches) {
                    val fullUrl = m.groupValues[1]
                    val novelId = m.groupValues[2].toLongOrNull() ?: continue
                    val title = ParserUtils.cleanText(m.groupValues[4])
                    if (title.isBlank()) continue

                    list.add(
                        ShikimoriSearchResult(
                            id = novelId,
                            name = title,
                            russian = "",
                            kind = "novel",
                            year = null,
                            coverUrl = null,
                            episodes = null,
                            chapters = null,
                            volumes = null,
                            targetType = WorkType.BOOK,
                            endpoint = "novels",
                            url = if (fullUrl.startsWith("http")) fullUrl else "${getBaseUrl()}$fullUrl",
                            source = MediaSource.RANOBES,
                            stringId = fullUrl
                        )
                    )
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(urlOrId: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = when {
                urlOrId.startsWith("http") -> urlOrId
                urlOrId.startsWith("/") -> "${getBaseUrl()}$urlOrId"
                urlOrId.toLongOrNull() != null -> "${getBaseUrl()}/novels/$urlOrId-novel.html"
                else -> "${getBaseUrl()}/novels/$urlOrId"
            }

            val html = fetchHtml(targetUrl)
                ?: return@withContext Result.failure(Exception("Не удалось загрузить новеллу с Ranobes"))

            val ogTitle = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""<h1\b[^>]*class=["'][^"']*title[^"']*["'][^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?: Regex("""<h1\b[^>]*>([\s\S]*?)</h1>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            val title = ParserUtils.cleanText(ogTitle ?: "Ranobes Novel")

            val altTitles = mutableListOf<String>()
            val titleTag = Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1).orEmpty()
            if (titleTag.isNotBlank()) {
                val segments = titleTag.split("•").map { ParserUtils.cleanText(it) }.filter { it.isNotBlank() }
                for (seg in segments) {
                    if (seg != title && !altTitles.contains(seg)) {
                        altTitles.add(seg)
                    }
                }
            }
            val subtitleBlock = Regex("""<span class="subtitle">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            if (!subtitleBlock.isNullOrBlank()) {
                val cleanedSubtitle = ParserUtils.cleanDescription(subtitleBlock)
                for (line in cleanedSubtitle.lines()) {
                    val clean = ParserUtils.cleanText(line.replace(Regex("""\((?:old|new)\)"""), ""))
                    if (clean.isNotBlank() && clean != title && !altTitles.contains(clean)) {
                        altTitles.add(clean)
                    }
                }
            }

            // Full description
            val descBlock = Regex("""<div class="moreless__full">(.*?)</div>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
                ?: Regex("""<meta property="og:description" content="([^"]+)"""").find(html)?.groupValues?.get(1).orEmpty()
            val cleanDesc = ParserUtils.cleanDescription(descBlock)

            // Cover
            val rawCover = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                ?: Regex("""background-image:\s*url\(([^)]+)\);""").find(html)?.groupValues?.get(1)?.trim('\'', '"')
            val coverUrl = ParserUtils.upgradeCoverUrl(rawCover)

            // Author
            val authorMatch = Regex("""<li>Authors:\s*<span class="tag_list"><a[^>]*>([^<]+)</a>""").find(html)
                ?: Regex("""Authors:\s*<a[^>]*>([^<]+)</a>""").find(html)
            val author = ParserUtils.sanitizeAuthor(authorMatch?.groupValues?.get(1))

            // Chapters: "Available: <span class="grey">3179 chapters" or "Total written: <a...3000+ chapters"
            val chapters = Regex("""Available:\s*<span class="grey">(\d+)\s*chapters""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex("""Total written:\s*<a[^>]*>(\d+)\+?\s*chapters""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex("""<span itemprop="name">(\d+)\+?\s*chapters</span>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.toDoubleOrNull()

            // Year
            val year = Regex("""Ranobes\s*\((\d{4})\)""").find(html)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""/uploads/posts/(\d{4})-""").find(html)?.groupValues?.get(1)?.toIntOrNull()

            // Genres & Tags
            val genres = mutableListOf<String>()
            val tags = mutableListOf<String>()
            Regex("""<a href="[^"]*/tags/genre/[^"]*">([^<]+)</a>""").findAll(html).forEach { gMatch ->
                val g = ParserUtils.cleanText(gMatch.groupValues[1])
                if (g.isNotBlank() && !genres.contains(g)) {
                    genres.add(g)
                }
            }
            Regex("""<a href="[^"]*/tags/(?!genre/)[^"]*">([^<]+)</a>""").findAll(html).forEach { tMatch ->
                val t = ParserUtils.cleanText(tMatch.groupValues[1])
                if (t.isNotBlank() && !tags.contains(t)) {
                    tags.add(t)
                }
            }

            Result.success(
                ParsedWorkMetadata(
                    title = title,
                    alternativeTitles = altTitles,
                    description = cleanDesc,
                    type = WorkType.BOOK,
                    year = year,
                    bookChapters = chapters,
                    author = author,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    coverUrl = coverUrl,
                    link = targetUrl,
                    source = MediaSource.RANOBES
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    private suspend fun fetchHtml(pathOrUrl: String): String? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val uri = try { URL(pathOrUrl) } catch (_: Exception) { null }
            uri?.file ?: pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_RANOBES)
        for (mirror in mirrors) {
            val fullUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            val html = CloudflareBypasser.fetchHtmlWithFallback(fullUrl, referer = "$mirror/", timeoutMs = 8000L)
            if (html != null) {
                if (!cleanPath.startsWith("http")) {
                    ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_RANOBES, mirror)
                }
                return html
            } else if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_RANOBES, mirror)
            }
        }
        return null
    }
}
