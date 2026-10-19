package com.rn.library.parser

import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RanobeHubClient {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun getBaseUrl(): String = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_RANOBEHUB)

    fun isRanobeHubUrl(url: String): Boolean {
        return Regex("""ranobehub\.[a-z]+/ranobe/""", RegexOption.IGNORE_CASE).containsMatchIn(url)
    }

    fun parseRanobeHubId(url: String): Long? {
        val regex = Regex("""ranobehub\.[a-z]+/ranobe/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)?.toLongOrNull()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "/api/search?q=$encoded"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Пустой ответ от RanobeHub"))
            val books = json.optJSONArray("books") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until books.length()) {
                val item = books.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val slug = item.optString("slug").orEmpty()
                val title = item.optString("title").orEmpty()
                val originalTitle = item.optString("originalTitle").orEmpty()
                val year = item.optInt("year").takeIf { it > 0 }
                val chapters = item.optInt("chapters").takeIf { it > 0 }
                val volumes = item.optInt("volumes").takeIf { it > 0 }

                var rawPoster = item.optString("posterUrl")
                val mediaIdFromPoster = Regex("""(?:/api/media/|%2Fapi%2Fmedia%2F)(\d+)""").find(rawPoster)?.groupValues?.get(1)
                val coverUrl = when {
                    mediaIdFromPoster != null -> "${getBaseUrl()}/api/media/$mediaIdFromPoster?size=original"
                    rawPoster.isBlank() -> null
                    rawPoster.startsWith("http") -> rawPoster
                    rawPoster.startsWith("/") -> "${getBaseUrl()}$rawPoster"
                    else -> "${getBaseUrl()}/$rawPoster"
                }

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = originalTitle.ifBlank { title },
                        russian = title,
                        kind = "ранобэ",
                        year = year,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = chapters,
                        volumes = volumes,
                        targetType = WorkType.BOOK,
                        endpoint = "ranobe",
                        url = "${getBaseUrl()}/ranobe/$id-$slug",
                        source = MediaSource.RANOBEHUB,
                        stringId = id.toString()
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(bookId: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val bookUrl = "${getBaseUrl()}/ranobe/$bookId"
            val html = fetchHtml("/ranobe/$bookId")

            if (html != null) {
                val titleMatch = Regex("""<h1[^>]*class="[^"]*book-title-cyrillic[^"]*"[^>]*>(.*?)</h1>""").find(html)
                val origMatch = Regex("""<p[^>]*class="[^"]*book-original-title[^"]*"[^>]*>(.*?)</p>""").find(html)
                val htmlDescMatch = Regex("""<div[^>]*class=['"][^'"]*(?:book-description|ui-description|wysiwyg)[^'"]*['"][^>]*>([\s\S]*?)</div>""", RegexOption.IGNORE_CASE).find(html)
                val descMatch = htmlDescMatch?.groupValues?.get(1)
                    ?: Regex("""<meta[^>]*property="og:description"[^>]*content="([^"]*)"""").find(html)?.groupValues?.get(1)
                    ?: Regex("""<p[^>]*class="[^"]*book-hero-summary[^"]*"[^>]*>(.*?)</p>""").find(html)?.groupValues?.get(1)
                val posterMatch = Regex("""(?:/api/media/|%2Fapi%2Fmedia%2F)(\d+)""").find(html)
                val ogImageMatch = Regex("""<meta[^>]*property="og:image"[^>]*content="([^"]*)"""").find(html)

                val authorMatch = Regex("""<a[^>]*class="[^"]*is-author[^"]*"[^>]*>.*?<strong>(.*?)</strong>""", RegexOption.DOT_MATCHES_ALL).find(html)
                val yearMatch = Regex("""/year/(\d+)""").find(html)

                val title = titleMatch?.groupValues?.get(1)?.trim().orEmpty()
                val origTitle = origMatch?.groupValues?.get(1)?.trim().orEmpty()
                val desc = descMatch.orEmpty()
                val author = authorMatch?.groupValues?.get(1)?.trim()
                val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

                val mediaId = posterMatch?.groupValues?.get(1)
                    ?: ogImageMatch?.groupValues?.get(1)?.let {
                        Regex("""(?:/api/media/|%2Fapi%2Fmedia%2F)(\d+)""").find(it)?.groupValues?.get(1)
                    }
                val coverUrl = if (mediaId != null) {
                    "${getBaseUrl()}/api/media/$mediaId?size=original"
                } else {
                    ogImageMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                }

                val genres = mutableListOf<String>()
                val tags = mutableListOf<String>()
                Regex("""<a[^>]*href="/genre/\d+"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                    val g = ParserUtils.cleanText(m.groupValues[1])
                    if (g.isNotBlank() && !genres.contains(g)) {
                        genres.add(g)
                    }
                }
                Regex("""<a[^>]*href="/tag/\d+"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                    val t = ParserUtils.cleanText(m.groupValues[1])
                    if (t.isNotBlank() && !tags.contains(t)) {
                        tags.add(t)
                    }
                }
                if (genres.isEmpty() && tags.isNotEmpty()) {
                    genres.addAll(tags.take(3))
                }

                val primaryTitle = title.ifBlank { origTitle }
                val altTitles = mutableListOf<String>()
                if (origTitle.isNotBlank() && origTitle != primaryTitle) {
                    altTitles.add(origTitle)
                }

                val chapters = Regex("""id="book-tab-chapters"[^>]*>[\s\S]*?<small>(\d+)</small>""").find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                    ?: Regex("""chapters\\":(\d+)""").find(html)?.groupValues?.get(1)?.toDoubleOrNull()
                    ?: Regex(""""chapters":\s*(\d+)""").find(html)?.groupValues?.get(1)?.toDoubleOrNull()

                val volumes = Regex("""volumes\\":\[([^\]]+)\]""").find(html)?.let { m ->
                    val count = Regex("""id\\":\d+""").findAll(m.groupValues[1]).count()
                    if (count > 0) count.toDouble() else null
                } ?: Regex(""""volumes":\s*\[([^\]]+)\]""").find(html)?.let { m ->
                    val count = Regex(""""id":\d+""").findAll(m.groupValues[1]).count()
                    if (count > 0) count.toDouble() else null
                }

                return@withContext Result.success(
                    ParsedWorkMetadata(
                        title = primaryTitle,
                        alternativeTitles = altTitles.distinct(),
                        description = ParserUtils.cleanDescription(desc),
                        type = WorkType.BOOK,
                        year = year,
                        coverUrl = coverUrl,
                        author = ParserUtils.sanitizeAuthor(author),
                        bookChapters = chapters,
                        volumes = volumes,
                        genres = ParserUtils.normalizeChips(genres),
                        tags = ParserUtils.normalizeChips(tags),
                        link = bookUrl,
                        source = MediaSource.RANOBEHUB
                    )
                )
            }

            // Fallback: search by id
            val searchRes = search(bookId.toString())
            if (searchRes.isSuccess) {
                val item = searchRes.getOrThrow().firstOrNull { it.id == bookId }
                if (item != null) {
                    return@withContext Result.success(
                        ParsedWorkMetadata(
                            title = item.russian.ifBlank { item.name },
                            alternativeTitles = listOfNotNull(item.name.takeIf { it != item.russian && it.isNotBlank() }),
                            description = "",
                            type = WorkType.BOOK,
                            year = item.year,
                            coverUrl = item.coverUrl,
                            bookChapters = item.chapters?.toDouble(),
                            volumes = item.volumes?.toDouble(),
                            link = item.url,
                            source = MediaSource.RANOBEHUB
                        )
                    )
                }
            }

            Result.failure(Exception("Не удалось получить данные с RanobeHub"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchJson(pathOrUrl: String): JSONObject? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val uri = try { URL(pathOrUrl) } catch (_: Exception) { null }
            uri?.file ?: pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_RANOBEHUB)
        for (mirror in mirrors) {
            val fullUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            try {
                val connection = URL(fullUrl).openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Referer", "$mirror/")
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                if (connection.responseCode in 200..299) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()
                    if (!cleanPath.startsWith("http")) {
                        ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_RANOBEHUB, mirror)
                    }
                    return JSONObject(text)
                }
                connection.disconnect()
            } catch (_: Exception) {}
            if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_RANOBEHUB, mirror)
            }
        }
        return null
    }

    private fun fetchHtml(pathOrUrl: String): String? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val uri = try { URL(pathOrUrl) } catch (_: Exception) { null }
            uri?.file ?: pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_RANOBEHUB)
        for (mirror in mirrors) {
            val startUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            var currentUrl = startUrl
            var succeeded = false
            var result: String? = null

            for (i in 0 until 5) {
                val connection = try {
                    URL(currentUrl).openConnection() as HttpURLConnection
                } catch (_: Exception) { break }

                try {
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    connection.setRequestProperty("Referer", "$mirror/")
                    connection.connectTimeout = 8000
                    connection.readTimeout = 12000
                    val code = connection.responseCode
                    if (code in 300..399) {
                        val loc = connection.getHeaderField("Location") ?: break
                        currentUrl = if (loc.startsWith("http")) loc else "$mirror$loc"
                        continue
                    }
                    if (code in 200..299) {
                        result = connection.inputStream.bufferedReader().use { it.readText() }
                        succeeded = true
                        break
                    }
                    break
                } catch (_: Exception) {
                    break
                } finally {
                    connection.disconnect()
                }
            }

            if (succeeded && result != null) {
                if (!cleanPath.startsWith("http")) {
                    ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_RANOBEHUB, mirror)
                }
                return result
            } else if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_RANOBEHUB, mirror)
            }
        }
        return null
    }
}
