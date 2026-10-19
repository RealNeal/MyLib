package com.rn.library.parser

import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RulateClient {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun getBaseUrl(): String = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_RULATE)

    fun isRulateUrl(url: String): Boolean {
        return Regex("""rulate\.[a-z]+/book/""", RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    fun parseRulateBookId(url: String): Long? {
        val regex = Regex("""rulate\.[a-z]+/book/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)?.toLongOrNull()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val html = fetchHtml("/search?t=$encoded")

            val list = mutableListOf<ShikimoriSearchResult>()

            if (!html.isNullOrBlank()) {
                val startTag = "<ul class=\"search-results\""
                val altStartTag = "<ul class='search-results'"
                val startIndex = html.indexOf(startTag).let { if (it >= 0) it else html.indexOf(altStartTag) }
                if (startIndex >= 0) {
                    val contentStart = html.indexOf(">", startIndex) + 1
                    val endIndex = html.indexOf("</ul>", contentStart)
                    val resultsBlock = if (endIndex > contentStart) html.substring(contentStart, endIndex) else null

                    if (!resultsBlock.isNullOrBlank()) {
                        val liBlocks = resultsBlock.split("<li>").drop(1)
                        for (block in liBlocks) {
                            val linkMatch = Regex("""<a href=['"]/book/(\d+)['"]>([^<]+)</a>""", RegexOption.IGNORE_CASE).find(block) ?: continue
                            val id = linkMatch.groupValues[1].toLongOrNull() ?: continue
                            val mainTitle = ParserUtils.cleanText(linkMatch.groupValues[2])
                            if (mainTitle.isBlank()) continue

                            val subMatch = Regex("""<small[^>]*>([^<]+)</small>""").find(block)
                            val subTitle = ParserUtils.cleanText(subMatch?.groupValues?.get(1))

                            val imgMatch = Regex("""<img src=['"]([^'"]+)['"]""").find(block)
                            val rawImg = imgMatch?.groupValues?.get(1)
                            val coverUrl = when {
                                rawImg.isNullOrBlank() || rawImg.contains("placeholder") -> null
                                rawImg.startsWith("http") -> ParserUtils.upgradeCoverUrl(rawImg)
                                rawImg.startsWith("/") -> ParserUtils.upgradeCoverUrl("${getBaseUrl()}$rawImg")
                                else -> ParserUtils.upgradeCoverUrl("${getBaseUrl()}/$rawImg")
                            }

                            // Chapters from <i class="icon-book icon-white"></i> 155 / 1 434</span>
                            val chMatch = Regex("""icon-book[^>]*></i>\s*([^<]+)</span>""").find(block)
                            val rawCh = chMatch?.groupValues?.get(1)?.replace("\u00a0", " ")?.trim()
                            val chapters = if (!rawCh.isNullOrBlank()) {
                                val parts = rawCh.split("/").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
                                parts.maxOrNull()
                            } else null

                            list.add(
                                ShikimoriSearchResult(
                                    id = id,
                                    name = mainTitle,
                                    russian = subTitle,
                                    kind = "ranobe",
                                    year = null,
                                    coverUrl = coverUrl,
                                    episodes = null,
                                    chapters = chapters,
                                    volumes = null,
                                    targetType = WorkType.BOOK,
                                    endpoint = "book",
                                    url = "${getBaseUrl()}/book/$id",
                                    source = MediaSource.RULATE,
                                    stringId = id.toString()
                                )
                            )
                        }
                    }
                }
            }

            // Fallback to autocomplete if search page didn't yield results
            if (list.isEmpty()) {
                val autocompleteUrl = "/search/autocomplete?query=$encoded"
                val array = fetchJsonArray(autocompleteUrl)
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val id = item.optLong("id")
                        val t1 = ParserUtils.cleanText(item.optString("title_one"))
                        val t2 = ParserUtils.cleanText(item.optString("title_two"))
                        val itemUrl = item.optString("url")
                        val rawImg = item.optString("img")

                        val mainTitle = t1.ifBlank { t2 }
                        val subTitle = if (t1.isNotBlank() && t2.isNotBlank() && t1 != t2) t2 else ""
                        if (mainTitle.isBlank()) continue

                        val coverUrl = when {
                            rawImg.isBlank() || rawImg.contains("placeholder") -> null
                            rawImg.startsWith("http") -> ParserUtils.upgradeCoverUrl(rawImg)
                            rawImg.startsWith("/") -> ParserUtils.upgradeCoverUrl("${getBaseUrl()}$rawImg")
                            else -> ParserUtils.upgradeCoverUrl("${getBaseUrl()}/$rawImg")
                        }

                        list.add(
                            ShikimoriSearchResult(
                                id = id,
                                name = mainTitle,
                                russian = subTitle,
                                kind = "ranobe",
                                year = null,
                                coverUrl = coverUrl,
                                episodes = null,
                                chapters = null,
                                volumes = null,
                                targetType = WorkType.BOOK,
                                endpoint = "book",
                                url = if (itemUrl.startsWith("http")) itemUrl else "${getBaseUrl()}$itemUrl",
                                source = MediaSource.RULATE,
                                stringId = id.toString()
                            )
                        )
                    }
                }
            }

            Result.success(list.distinctBy { it.stringId })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(bookId: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val bookUrl = "${getBaseUrl()}/book/$bookId"
            val html = fetchHtml("/book/$bookId") ?: return@withContext Result.failure(Exception("Не удалось загрузить страницу книги Rulate"))

            var primaryTitle = ""
            val altTitles = mutableListOf<String>()
            var description = ""
            var coverUrl: String? = null
            var author: String? = null
            var year: Int? = null
            var bookChapters: Double? = null
            val genres = mutableListOf<String>()
            val tags = mutableListOf<String>()

            // 1. Parse full multi-paragraph description from HTML first
            val htmlDescBlock = Regex("""<div[^>]*class=['"][^'"]*book-description[^'"]*['"][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
            if (!htmlDescBlock.isNullOrBlank()) {
                description = ParserUtils.cleanDescription(htmlDescBlock)
            }

            // 2. Find all JSON-LD scripts and pick the Book schema
            val ldJsonMatches = Regex("""<script type="application/ld\+json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(html)

            for (m in ldJsonMatches) {
                try {
                    val obj = JSONObject(m.groupValues[1].trim())
                    val type = obj.optString("@type")
                    if (type.equals("Book", ignoreCase = true)) {
                        val name = ParserUtils.cleanText(obj.optString("name"))
                        val altName = ParserUtils.cleanText(obj.optString("alternateName"))
                        primaryTitle = name.ifBlank { altName }

                        if (altName.isNotBlank() && altName != primaryTitle && !altName.equals("null", ignoreCase = true)) {
                            altTitles.add(altName)
                        }

                        if (description.isBlank()) {
                            val desc = obj.optString("description").orEmpty()
                            description = ParserUtils.cleanDescription(desc)
                        }

                        val rawImg = obj.optString("image").orEmpty()
                        coverUrl = when {
                            rawImg.isBlank() || rawImg.contains("placeholder") -> null
                            rawImg.startsWith("http") -> ParserUtils.upgradeCoverUrl(rawImg)
                            rawImg.startsWith("/") -> ParserUtils.upgradeCoverUrl("${getBaseUrl()}$rawImg")
                            else -> ParserUtils.upgradeCoverUrl("${getBaseUrl()}/$rawImg")
                        }

                        val datePublished = obj.optString("datePublished")
                        year = Regex("""\d{4}""").find(datePublished)?.value?.toIntOrNull()

                        val numberOfPages = obj.optDouble("numberOfPages").takeIf { !it.isNaN() && it > 0 }
                        var ldJsonPages = numberOfPages

                        // NOTE: Do NOT use obj.optJSONObject("author") on Rulate because Rulate
                        // puts the site user (translator/owner/creator) into JSON-LD author.

                        obj.optJSONArray("genre")?.let { gArr ->
                            for (i in 0 until gArr.length()) {
                                val g = ParserUtils.cleanText(gArr.optString(i))
                                if (g.isNotBlank() && !genres.contains(g)) {
                                    genres.add(g)
                                }
                            }
                        }
                        break
                    }
                } catch (ignored: Exception) {}
            }

            // 3. Parse actual Author from HTML (avoiding site creator/translator)
            val authorMatch = Regex("""<strong>Автор(?: оригинала)?:</strong>\s*<em>(?:<a[^>]*>)?([^<]+)""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""<p[^>]*>\s*<strong>Автор(?: оригинала)?:</strong>\s*(?:<em>)?(?:<a[^>]*>)?([^<]+)""", RegexOption.IGNORE_CASE).find(html)
            var extractedAuthor = ParserUtils.sanitizeAuthor(authorMatch?.groupValues?.get(1))

            // Fallback author from FAQPage schema: "Кто автор ...?" -> "Автор ранобэ ... - [Автор]."
            if (extractedAuthor.isNullOrBlank()) {
                val faqQuestionMatch = Regex("""Кто\s+автор\s+[^?]+\?.*?"text":\s*"Автор\s+[^—–-]+[—–-]\s*([^."]+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
                if (faqQuestionMatch != null) {
                    extractedAuthor = ParserUtils.sanitizeAuthor(faqQuestionMatch.groupValues[1])
                }
            }

            author = extractedAuthor.takeIf { !it.isNullOrBlank() }

            // 4. Parse Genres from HTML (to supplement or fallback)
            val genresBlock = Regex("""<strong>Жанры:</strong>\s*<em>(.*?)</em>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1)
            if (!genresBlock.isNullOrBlank()) {
                Regex("""<a[^>]*>([^<]+)</a>""").findAll(genresBlock).forEach { gm ->
                    val g = ParserUtils.cleanText(gm.groupValues[1])
                    if (g.isNotBlank() && !genres.contains(g)) {
                        genres.add(g)
                    }
                }
            }

            // 5. Parse Tags from HTML
            val tagsBlock = Regex("""<strong>Т[еэ]ги:</strong>\s*<em>(.*?)</em>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)?.groupValues?.get(1)
            if (!tagsBlock.isNullOrBlank()) {
                Regex("""<a[^>]*>([^<]+)</a>""").findAll(tagsBlock).forEach { tm ->
                    val t = ParserUtils.cleanText(tm.groupValues[1])
                    if (t.isNotBlank() && !tags.contains(t)) {
                        tags.add(t)
                    }
                }
            }

            // 6. Chapter count: prioritize actual original/total chapters of the work from HTML over translator upload count
            val workChaptersMatch = Regex("""<strong>(?:Количество глав|В оригинале|Всего глав):</strong>\s*(?:<em>)?(?:\D*?)(\d+)""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""<strong>(?:Количество глав|В оригинале|Всего глав):</strong>\s*([^<]+)""", RegexOption.IGNORE_CASE).find(html)
            val parsedWorkChapters = workChaptersMatch?.groupValues?.get(1)?.filter { it.isDigit() }?.toDoubleOrNull()

            bookChapters = parsedWorkChapters
                ?: run {
                    val spanChapters = Regex("""icon-book[^>]*></i>\s*([^<]+)</span>""").find(html)?.groupValues?.get(1)?.replace("\u00a0", " ")?.trim()
                    val parsedSpan = if (!spanChapters.isNullOrBlank()) {
                        val parts = spanChapters.split("/").mapNotNull { it.filter { c -> c.isDigit() }.toDoubleOrNull() }
                        parts.maxOrNull()
                    } else null

                    val faqChapters = Regex("""переведено\s+(\d+)\s+глав""", RegexOption.IGNORE_CASE)
                        .find(html)?.groupValues?.get(1)?.toDoubleOrNull()

                    parsedSpan ?: faqChapters
                }

            // Fallback titles if Book schema was absent
            if (primaryTitle.isBlank()) {
                val ogTitle = Regex("""<meta property="og:title" content="([^"]+)"\s*/?>""").find(html)?.groupValues?.get(1).orEmpty()
                primaryTitle = ParserUtils.cleanText(ogTitle.ifBlank { "Rulate Book #$bookId" })
            }
            if (description.isBlank()) {
                val ogDesc = Regex("""<meta property="og:description" content="([^"]+)"\s*/?>""").find(html)?.groupValues?.get(1).orEmpty()
                description = ParserUtils.cleanDescription(ogDesc)
            }
            if (coverUrl.isNullOrBlank()) {
                val ogImg = Regex("""<meta property="og:image" content="([^"]+)"\s*/?>""").find(html)?.groupValues?.get(1).orEmpty()
                coverUrl = ogImg.takeIf { it.isNotBlank() && !it.contains("placeholder") }?.let { ParserUtils.upgradeCoverUrl(it) }
            }

            val cleanAlt = altTitles.map { ParserUtils.cleanText(it) }
                .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) && it != primaryTitle }
                .distinct()

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = cleanAlt,
                    description = description,
                    type = WorkType.BOOK,
                    year = year,
                    bookChapters = bookChapters,
                    coverUrl = coverUrl,
                    author = author,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    link = bookUrl,
                    source = MediaSource.RULATE
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchJsonArray(pathOrUrl: String): JSONArray? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val uri = try { URL(pathOrUrl) } catch (_: Exception) { null }
            uri?.file ?: pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_RULATE)
        for (mirror in mirrors) {
            val fullUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            try {
                val url = URL(fullUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (!cleanPath.startsWith("http")) {
                        ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_RULATE, mirror)
                    }
                    return JSONArray(text)
                }
                conn.disconnect()
            } catch (_: Exception) {}
            if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_RULATE, mirror)
            }
        }
        return null
    }

    private fun fetchHtml(pathOrUrl: String): String? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val uri = try { URL(pathOrUrl) } catch (_: Exception) { null }
            uri?.file ?: pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_RULATE)
        for (mirror in mirrors) {
            val fullUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            try {
                val url = URL(fullUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (!cleanPath.startsWith("http")) {
                        ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_RULATE, mirror)
                    }
                    return text
                }
                conn.disconnect()
            } catch (_: Exception) {}
            if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_RULATE, mirror)
            }
        }
        return null
    }
}
