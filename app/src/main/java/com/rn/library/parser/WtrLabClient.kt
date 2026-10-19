package com.rn.library.parser

import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object WtrLabClient {
    private const val BASE_URL = "https://wtr-lab.com"
    private const val API_BASE = "https://wtr-lab.com/api"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val recentMetadataCache = ConcurrentHashMap<Long, ParsedWorkMetadata>()
    private val slugCache = ConcurrentHashMap<Long, String>()

    fun isWtrLabUrl(url: String): Boolean {
        return url.contains("wtr-lab.com", ignoreCase = true) && url.contains("/novel/", ignoreCase = true)
    }

    fun parseWtrLabId(url: String): Long? {
        val regex = Regex("""wtr-lab\.com/(?:[a-zA-Z]{2}/)?novel/(\d+)(?:/([a-zA-Z0-9_\-]+))?""", RegexOption.IGNORE_CASE)
        val match = regex.find(url.trim()) ?: return null
        val id = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val slug = match.groupValues.getOrNull(2)
        if (!slug.isNullOrBlank()) {
            slugCache[id] = slug
        }
        return id
    }

    fun clearCache() {
        recentMetadataCache.clear()
        slugCache.clear()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().put("text", query.trim()).toString()
            val json = postJson("$API_BASE/search", jsonPayload)
                ?: return@withContext Result.failure(Exception("Пустой ответ от WTR-LAB"))
            val data = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val slug = item.optString("slug").orEmpty()
                if (slug.isNotBlank()) {
                    slugCache[id] = slug
                }

                val dataObj = item.optJSONObject("data")
                val rawObj = dataObj?.optJSONObject("raw")

                val enTitle = dataObj?.optString("title").orEmpty()
                val rawTitle = rawObj?.optString("title").orEmpty()
                val mainTitle = enTitle.ifBlank { rawTitle }
                val subTitle = if (rawTitle.isNotBlank() && rawTitle != mainTitle) rawTitle else ""

                val rawImage = dataObj?.optString("image").orEmpty()
                // Strip size query params (?w=180&h=240) to get high quality original image
                val coverUrl = if (rawImage.isNotBlank()) {
                    rawImage.substringBefore("?")
                } else null

                val chapters = item.optInt("chapter_count").takeIf { it > 0 }
                val desc = dataObj?.optString("description").orEmpty()
                    .ifBlank { rawObj?.optString("description").orEmpty() }
                val author = dataObj?.optString("author").orEmpty()
                    .ifBlank { rawObj?.optString("author").orEmpty() }
                    .takeIf { it.isNotBlank() }

                val altTitles = mutableListOf<String>()
                if (subTitle.isNotBlank()) {
                    altTitles.add(subTitle)
                }

                val metadata = ParsedWorkMetadata(
                    title = mainTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = ParserUtils.cleanDescription(desc),
                    type = WorkType.BOOK,
                    year = null,
                    coverUrl = coverUrl,
                    author = ParserUtils.sanitizeAuthor(author),
                    bookChapters = chapters?.toDouble(),
                    link = "$BASE_URL/en/novel/$id/$slug",
                    source = MediaSource.WTRLAB
                )
                recentMetadataCache[id] = metadata

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = mainTitle,
                        russian = subTitle,
                        kind = "novel",
                        year = null,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = chapters,
                        volumes = null,
                        targetType = WorkType.BOOK,
                        endpoint = "novel",
                        url = "$BASE_URL/en/novel/$id/$slug",
                        source = MediaSource.WTRLAB,
                        stringId = id.toString()
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(novelId: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cached = recentMetadataCache[novelId]
            if (cached != null) {
                return@withContext Result.success(cached)
            }

            // Otherwise fetch the novel web page
            val slug = slugCache[novelId].orEmpty()
            val targetUrl = if (slug.isNotBlank()) {
                "$BASE_URL/en/novel/$novelId/$slug"
            } else {
                "$BASE_URL/en/novel/$novelId"
            }

            val html = fetchHtml(targetUrl)
            if (html != null) {
                val h1Match = Regex("""<h1[^>]*>([^<]+)</h1>""").find(html)
                val ogTitle = Regex("""<meta property="og:title" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                val cleanTitle = h1Match?.groupValues?.get(1)?.trim()
                    ?: ogTitle?.replace(" - WTR-LAB", "")?.replace("Read ", "")?.replace(" RAW English Translation", "")?.trim()
                    ?: "Novel #$novelId"

                val htmlDesc = Regex("""<div[^>]*class=['"][^'"]*(?:raw-description|description|synopsis)[^'"]*['"][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE)
                    .find(html)?.groupValues?.get(1)
                val ogDesc = Regex("""<meta property="og:description" content="([^"]+)"""").find(html)?.groupValues?.get(1).orEmpty()
                val desc = ParserUtils.cleanDescription(htmlDesc ?: ogDesc.replace(" - WTR-LAB", ""))

                val ogImg = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)?.groupValues?.get(1)
                val coverUrl = ogImg?.substringBefore("?")

                val authorMatch = Regex("""Author[^<]*<[^>]+>([^<]+)<""").find(html)
                val author = ParserUtils.sanitizeAuthor(authorMatch?.groupValues?.get(1))

                val chMatch = Regex("""(\d+)\s*Chapters""", RegexOption.IGNORE_CASE).find(html)
                val chapters = chMatch?.groupValues?.get(1)?.toDoubleOrNull()

                val genres = mutableListOf<String>()
                val tags = mutableListOf<String>()
                Regex("""<a[^>]*href=['"][^'"]*/genre/[^'"]*['"][^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                    val g = ParserUtils.cleanText(m.groupValues[1])
                    if (g.isNotBlank() && !genres.contains(g)) genres.add(g)
                }
                Regex("""<a[^>]*href=['"][^'"]*/tag/[^'"]*['"][^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                    val t = ParserUtils.cleanText(m.groupValues[1])
                    if (t.isNotBlank() && !tags.contains(t)) tags.add(t)
                }

                val meta = ParsedWorkMetadata(
                    title = cleanTitle,
                    alternativeTitles = emptyList(),
                    description = desc,
                    type = WorkType.BOOK,
                    year = null,
                    coverUrl = coverUrl,
                    author = author,
                    bookChapters = chapters,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    link = targetUrl,
                    source = MediaSource.WTRLAB
                )
                recentMetadataCache[novelId] = meta
                return@withContext Result.success(meta)
            }

            Result.failure(Exception("Не удалось найти данные новеллы на WTR-LAB"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postJson(urlStr: String, jsonBody: String): JSONObject? {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Referer", "$BASE_URL/")
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.outputStream.bufferedWriter().use { it.write(jsonBody) }
            if (connection.responseCode in 200..299) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchHtml(urlString: String): String? {
        var currentUrl = urlString
        for (i in 0 until 5) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Referer", "$BASE_URL/")
                conn.connectTimeout = 12000
                conn.readTimeout = 15000

                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    currentUrl = if (loc.startsWith("http")) loc else "$BASE_URL$loc"
                    continue
                }
                if (code in 200..299) {
                    return conn.inputStream.bufferedReader().use { it.readText() }
                }
                return null
            } catch (e: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }
}
