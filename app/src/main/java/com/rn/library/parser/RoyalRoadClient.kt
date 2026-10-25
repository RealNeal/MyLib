package com.rn.library.parser

import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RoyalRoadClient {
    private const val BASE_URL = "https://www.royalroad.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun getBaseUrl(): String = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_ROYALROAD).ifBlank { BASE_URL }

    fun isRoyalRoadUrl(url: String): Boolean {
        return url.contains("royalroad.com/fiction/", ignoreCase = true)
    }

    fun parseRoyalRoadFictionId(url: String): Long? {
        val regex = Regex("""royalroad\.com/fiction/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)?.toLongOrNull()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val base = getBaseUrl()
            var html = fetchHtml("$base/fictions/search?title=$encoded")
            var list = html?.let { parseSearchResults(it) }.orEmpty()

            // If title search yielded no results, fallback to keyword search
            if (list.isEmpty()) {
                val keywordHtml = fetchHtml("$base/fictions/search?keyword=$encoded")
                if (keywordHtml != null) {
                    list = parseSearchResults(keywordHtml)
                }
            }

            // If still empty and query contains punctuation or extra symbols, try stripped alphanumeric query
            if (list.isEmpty()) {
                val simplified = cleanQuery.replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ").trim().replace(Regex("""\s+"""), " ")
                if (simplified.isNotBlank() && simplified != cleanQuery) {
                    val simpEncoded = URLEncoder.encode(simplified, "UTF-8")
                    val simpHtml = fetchHtml("$base/fictions/search?title=$simpEncoded")
                        ?: fetchHtml("$base/fictions/search?keyword=$simpEncoded")
                    if (simpHtml != null) {
                        list = parseSearchResults(simpHtml)
                    }
                }
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseSearchResults(html: String): List<ShikimoriSearchResult> {
        val list = mutableListOf<ShikimoriSearchResult>()
        val itemPattern = Regex(
            """<h2 class="fiction-title">\s*<a href="(/fiction/(\d+)[^"]*)"[^>]*>([^<]+)</a>""",
            RegexOption.IGNORE_CASE
        )
        val imgPattern = Regex("""<img[^>]*src="([^"]+)"""", RegexOption.IGNORE_CASE)
        val chaptersPattern = Regex("""<span>(\d+)\s*Chapters</span>""", RegexOption.IGNORE_CASE)

        val blocks = html.split(Regex("""<div class="[^"]*fiction-list-item[^"]*">""")).drop(1)
        if (blocks.isNotEmpty()) {
            for (block in blocks) {
                val match = itemPattern.find(block) ?: continue
                val fullRelUrl = match.groupValues[1]
                val fictionId = match.groupValues[2].toLongOrNull() ?: continue
                val title = ParserUtils.cleanText(match.groupValues[3])
                if (title.isBlank()) continue

                val cover = imgPattern.find(block)?.groupValues?.get(1)?.takeIf { !it.contains("nocover") }?.let { ParserUtils.upgradeCoverUrl(it) }
                val chapters = chaptersPattern.find(block)?.groupValues?.get(1)?.toIntOrNull()

                list.add(
                    ShikimoriSearchResult(
                        id = fictionId,
                        name = title,
                        russian = "",
                        kind = "novel",
                        year = null,
                        coverUrl = cover,
                        episodes = null,
                        chapters = chapters,
                        volumes = null,
                        targetType = WorkType.BOOK,
                        endpoint = "fiction",
                        url = "$BASE_URL$fullRelUrl",
                        source = MediaSource.ROYALROAD,
                        stringId = fictionId.toString()
                    )
                )
            }
        }

        if (list.isEmpty()) {
            val matches = itemPattern.findAll(html)
            for (m in matches) {
                val fullRelUrl = m.groupValues[1]
                val fictionId = m.groupValues[2].toLongOrNull() ?: continue
                val title = ParserUtils.cleanText(m.groupValues[3])
                if (title.isBlank()) continue

                list.add(
                    ShikimoriSearchResult(
                        id = fictionId,
                        name = title,
                        russian = "",
                        kind = "novel",
                        year = null,
                        coverUrl = null,
                        episodes = null,
                        chapters = null,
                        volumes = null,
                        targetType = WorkType.BOOK,
                        endpoint = "fiction",
                        url = "$BASE_URL$fullRelUrl",
                        source = MediaSource.ROYALROAD,
                        stringId = fictionId.toString()
                    )
                )
            }
        }
        return list
    }

    suspend fun fetchDetails(fictionId: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val novelUrl = "${getBaseUrl()}/fiction/$fictionId"
            val html = fetchHtml(novelUrl) ?: return@withContext Result.failure(Exception("Не удалось загрузить страницу новеллы Royal Road"))

            var title = ""
            var description = ""
            var coverUrl: String? = null
            var author: String? = null
            var year: Int? = null

            val ldJsonMatches = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html)

            for (m in ldJsonMatches) {
                try {
                    val json = JSONObject(m.groupValues[1].trim())
                    if (json.optString("@type").equals("Book", ignoreCase = true)) {
                        title = ParserUtils.cleanText(json.optString("name"))
                        val rawDesc = json.optString("description")
                        description = ParserUtils.cleanDescription(rawDesc)
                        coverUrl = json.optNonEmptyString("image")
                        val authorObj = json.optJSONObject("author")
                        author = ParserUtils.sanitizeAuthor(authorObj?.optNonEmptyString("name"))
                        val dateStr = json.optNonEmptyString("datePublished") ?: json.optNonEmptyString("dateCreated")
                        year = dateStr?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }
                        break
                    }
                } catch (ignored: Exception) {}
            }

            // 1. Full description from HTML if available
            val htmlDesc = Regex("""<div[^>]*class=['"][^'"]*description[^'"]*['"][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
            if (!htmlDesc.isNullOrBlank()) {
                description = ParserUtils.cleanDescription(htmlDesc)
            }

            // Fallbacks for title, description, cover
            if (title.isBlank()) {
                val ogTitle = Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                title = ParserUtils.cleanText(ogTitle ?: "Royal Road #$fictionId")
            }
            if (description.isBlank()) {
                val ogDesc = Regex("""<meta property="og:description" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                description = ParserUtils.cleanDescription(ogDesc.orEmpty())
            }
            if (coverUrl.isNullOrBlank()) {
                coverUrl = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                    ?.takeIf { !it.contains("nocover") }
            }
            coverUrl = ParserUtils.upgradeCoverUrl(coverUrl)
            if (author.isNullOrBlank()) {
                val metaAuthor = Regex("""<meta property="books:author" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                author = ParserUtils.sanitizeAuthor(metaAuthor)
            }

            // Chapter count from data-chapters attribute or span
            val chapters = Regex("""data-chapters="(\d+)"""").find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                ?: Regex("""<span class="label[^"]*pull-right">(\d+)\s*Chapters</span>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)?.toDoubleOrNull()

            // Genres & tags
            val genres = mutableListOf<String>()
            val tags = mutableListOf<String>()
            Regex("""<a[^>]*class=['"][^'"]*(?:font-red-sunglo|fiction-genre)[^'"]*['"][^>]*>([^<]+)</a>""").findAll(html).forEach { gMatch ->
                val g = ParserUtils.cleanText(gMatch.groupValues[1])
                if (g.isNotBlank() && !genres.contains(g)) {
                    genres.add(g)
                }
            }
            Regex("""class="label[^"]*fiction-tag"[^>]*>([^<]+)</a>""").findAll(html).forEach { tagMatch ->
                val t = ParserUtils.cleanText(tagMatch.groupValues[1])
                if (t.isNotBlank() && !tags.contains(t)) {
                    tags.add(t)
                }
            }
            if (genres.isEmpty() && tags.isNotEmpty()) {
                genres.addAll(tags.take(3))
            }

            Result.success(
                ParsedWorkMetadata(
                    title = title,
                    alternativeTitles = emptyList(),
                    description = description,
                    type = WorkType.BOOK,
                    year = year,
                    bookChapters = chapters,
                    chapters = chapters,
                    author = author,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    coverUrl = coverUrl,
                    link = novelUrl,
                    source = MediaSource.ROYALROAD
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun fetchHtml(urlString: String): String? {
        return CloudflareBypasser.fetchHtmlWithFallback(
            urlStr = urlString,
            referer = "${getBaseUrl()}/"
        )
    }
}
