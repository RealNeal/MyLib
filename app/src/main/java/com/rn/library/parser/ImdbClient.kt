package com.rn.library.parser

import com.rn.library.data.SeriesType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ImdbClient {
    private const val BASE_URL = "https://www.imdb.com"
    private const val SUGGESTION_BASE = "https://v3.sg.media-imdb.com/suggestion/x"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun isImdbUrl(url: String): Boolean {
        return url.contains("imdb.com/title/", ignoreCase = true)
    }

    fun parseImdbId(url: String): String? {
        val regex = Regex("""imdb\.com/title/(tt\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim().lowercase().replace(" ", "_")
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "$SUGGESTION_BASE/$encoded.json"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Пустой ответ от IMDb"))
            val d = json.optJSONArray("d") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until d.length()) {
                val item = d.optJSONObject(i) ?: continue
                val id = item.optString("id").orEmpty()
                if (!id.startsWith("tt")) continue // Skip actors / companies

                val title = item.optString("l").orEmpty()
                val year = item.optInt("y").takeIf { it > 0 }
                val qid = item.optString("qid").orEmpty().lowercase()
                val q = item.optString("q").orEmpty().lowercase()

                val isSeries = qid == "tvseries" || qid == "tvepisode" || q.contains("series")
                val kind = if (isSeries) "сериал" else "фильм"

                val stars = item.optString("s").orEmpty()
                val imgObj = item.optJSONObject("i")
                val coverUrl = imgObj?.optString("imageUrl")?.takeIf { it.isNotBlank() }

                val numId = id.hashCode().toLong() and 0x7FFFFFFF

                list.add(
                    ShikimoriSearchResult(
                        id = numId,
                        name = title,
                        russian = stars, // stars/cast as secondary info
                        kind = kind,
                        year = year,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = null,
                        volumes = null,
                        targetType = WorkType.SERIES,
                        endpoint = "title",
                        url = "$BASE_URL/title/$id/",
                        source = MediaSource.IMDB,
                        stringId = id
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(imdbId: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/title/$imdbId/"
            val html = fetchHtml(url)

            var primaryTitle = ""
            var plotDescription = ""
            val altTitles = mutableListOf<String>()
            var coverUrl: String? = null
            var isTv = false
            var directorOrActor: String? = null
            val genres = mutableListOf<String>()
            var year: Int? = null

            if (html != null) {
                // 1. Extract Original Title (alternative title)
                val origJsonMatch = Regex(""""originalTitleText"\s*:\s*\{\s*"text"\s*:\s*"([^"]+)"""").find(html)
                val origFromJson = origJsonMatch?.groupValues?.get(1)?.let { unescapeJson(it) }?.trim()

                val heroOrigMatch = Regex("""data-testid=["']hero-title-block__original-title["'][^>]*>(?:Original title:\s*)?([^<]+)<""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""Original title:\s*([^<\n\r]+)""", RegexOption.IGNORE_CASE).find(html)
                val origFromHtml = heroOrigMatch?.groupValues?.get(1)?.trim()

                // 2. Extract Plot / Summary (Summaries)
                val plotJsonMatch = Regex(""""plotText"\s*:\s*\{\s*"plainText"\s*:\s*"([^"]+)"""").find(html)
                val plotFromJson = plotJsonMatch?.groupValues?.get(1)?.let { unescapeJson(it) }?.trim()

                val heroPlotMatch = Regex("""data-testid=["'](?:plot-xl|plot-l|plot-xs|plot|storyline-plot-summary)["'][^>]*>(?:<span[^>]*>)?([^<]+)""").find(html)
                val plotFromHtml = heroPlotMatch?.groupValues?.get(1)?.trim()

                // 3. Parse application/ld+json
                val ldJsonMatch = Regex("""<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)
                if (ldJsonMatch != null) {
                    try {
                        val obj = JSONObject(ldJsonMatch.groupValues[1])
                        primaryTitle = obj.optString("name").orEmpty()
                        val desc = obj.optString("description").orEmpty()
                        if (desc.isNotBlank()) {
                            plotDescription = desc
                        }

                        val rawImg = obj.optString("image").orEmpty()
                        if (rawImg.isNotBlank()) {
                            coverUrl = rawImg
                        }

                        val typeStr = obj.optString("@type")
                        isTv = typeStr.contains("TVSeries", ignoreCase = true)

                        val gArr = obj.optJSONArray("genre")
                        if (gArr != null) {
                            for (j in 0 until gArr.length()) {
                                val g = gArr.optString(j)
                                if (g.isNotBlank() && !genres.contains(g)) genres.add(g)
                            }
                        } else {
                            val gStr = obj.optString("genre")
                            if (gStr.isNotBlank() && !genres.contains(gStr)) genres.add(gStr)
                        }

                        val creatorArr = obj.optJSONArray("creator") ?: obj.optJSONArray("director") ?: obj.optJSONArray("actor")
                        if (creatorArr != null && creatorArr.length() > 0) {
                            directorOrActor = creatorArr.optJSONObject(0)?.optString("name")
                        }

                        val datePublished = obj.optString("datePublished")
                        year = Regex("""\d{4}""").find(datePublished)?.value?.toIntOrNull()

                        val altNameObj = obj.opt("alternateName")
                        when (altNameObj) {
                            is String -> if (altNameObj.isNotBlank()) altTitles.add(altNameObj.trim())
                            is org.json.JSONArray -> {
                                for (k in 0 until altNameObj.length()) {
                                    val a = altNameObj.optString(k).trim()
                                    if (a.isNotBlank()) altTitles.add(a)
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                if (!origFromJson.isNullOrBlank()) altTitles.add(origFromJson)
                if (!origFromHtml.isNullOrBlank()) altTitles.add(origFromHtml)

                if (plotDescription.isBlank()) {
                    plotDescription = plotFromJson.orEmpty().ifBlank { plotFromHtml.orEmpty() }
                }

                // If plotDescription is still empty, try fetching plotsummary page
                if (plotDescription.isBlank()) {
                    try {
                        val summaryUrl = "$BASE_URL/title/$imdbId/plotsummary/"
                        val summaryHtml = fetchHtml(summaryUrl)
                        if (summaryHtml != null) {
                            val sumJsonMatch = Regex(""""plotText"\s*:\s*\{\s*"plainText"\s*:\s*"([^"]+)"""").find(summaryHtml)
                            val sumFromJson = sumJsonMatch?.groupValues?.get(1)?.let { unescapeJson(it) }?.trim()
                            val sumDivMatch = Regex("""<div class=["']ipc-html-content-inner-div["'][^>]*>([^<]+)</div>""").find(summaryHtml)
                            val sumFromHtml = sumDivMatch?.groupValues?.get(1)?.trim()
                            plotDescription = sumFromJson.orEmpty().ifBlank { sumFromHtml.orEmpty() }
                        }
                    } catch (ignored: Exception) {}
                }
            }

            // Fallback via suggestion API if title is still empty
            if (primaryTitle.isBlank()) {
                val searchRes = search(imdbId)
                if (searchRes.isSuccess) {
                    val item = searchRes.getOrThrow().firstOrNull { it.stringId == imdbId }
                    if (item != null) {
                        primaryTitle = item.name
                        isTv = item.kind.contains("сериал", ignoreCase = true)
                        if (year == null) year = item.year
                        if (coverUrl == null) coverUrl = item.coverUrl
                        if (directorOrActor == null) directorOrActor = item.russian.takeIf { it.isNotBlank() }
                    }
                }
            }

            if (primaryTitle.isNotBlank()) {
                val cleanAlts = altTitles
                    .map { ParserUtils.cleanText(it) }
                    .filter { it.isNotBlank() && !it.equals(primaryTitle, ignoreCase = true) }
                    .distinct()

                return@withContext Result.success(
                    ParsedWorkMetadata(
                        title = primaryTitle,
                        alternativeTitles = cleanAlts,
                        description = ParserUtils.cleanDescription(plotDescription),
                        type = WorkType.SERIES,
                        seriesType = if (isTv) SeriesType.TV_SERIES else SeriesType.FILM,
                        year = year,
                        coverUrl = coverUrl,
                        author = ParserUtils.sanitizeAuthor(directorOrActor),
                        genres = genres,
                        link = url,
                        source = MediaSource.IMDB
                    )
                )
            }

            Result.failure(Exception("Не удалось получить данные с IMDb"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unescapeJson(str: String): String {
        return str
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", " ")
            .replace("\\/", "/")
            .replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
                it.groupValues[1].toInt(16).toChar().toString()
            }
    }

    private fun fetchJson(urlStr: String): JSONObject? {
        return try {
            val connection = URL(urlStr).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            if (connection.responseCode in 200..299) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchHtml(urlStr: String): String? {
        var currentUrl = urlStr
        for (i in 0 until 5) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                connection.connectTimeout = 12000
                connection.readTimeout = 15000
                val code = connection.responseCode
                if (code in 300..399) {
                    val loc = connection.getHeaderField("Location") ?: return null
                    currentUrl = if (loc.startsWith("http")) loc else "$BASE_URL$loc"
                    continue
                }
                if (code in 200..299) {
                    return connection.inputStream.bufferedReader().use { it.readText() }
                }
                return null
            } catch (e: Exception) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }
}
