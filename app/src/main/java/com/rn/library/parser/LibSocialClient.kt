package com.rn.library.parser

import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object LibSocialClient {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    fun isLibUrl(url: String): Boolean {
        val lower = url.lowercase()
        return Regex("""(?:mangalib|ranobelib|animelib|anilib|manga\.lib|ranobe\.lib|anime\.lib)\.[a-z]+""").containsMatchIn(lower)
    }

    fun parseLibSlug(url: String): Pair<MediaSource, String>? {
        val trimmed = url.trim()
        val source = when {
            Regex("""(?:animelib|anilib|anime\.lib)\.[a-z]+""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> MediaSource.ANIMELIB
            Regex("""(?:ranobelib|ranobe\.lib)\.[a-z]+""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> MediaSource.RANOBELIB
            Regex("""(?:mangalib|manga\.lib)\.[a-z]+""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> MediaSource.MANGALIB
            else -> return null
        }

        val regex = Regex("""([0-9]+--[a-zA-Z0-9_\-]+)""")
        val match = regex.find(trimmed) ?: return null
        return Pair(source, match.groupValues[1])
    }

    private fun getWebBase(source: MediaSource): String = when (source) {
        MediaSource.ANIMELIB -> ParserDomainManager.getDomain(ParserDomainManager.SOURCE_ANIMELIB_WEB)
        MediaSource.RANOBELIB -> ParserDomainManager.getDomain(ParserDomainManager.SOURCE_RANOBELIB_WEB)
        else -> ParserDomainManager.getDomain(ParserDomainManager.SOURCE_MANGALIB_WEB)
    }

    suspend fun search(
        query: String,
        source: MediaSource
    ): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val isAnime = source == MediaSource.ANIMELIB
            val isRanobe = source == MediaSource.RANOBELIB
            val siteId = when (source) {
                MediaSource.ANIMELIB -> 5
                MediaSource.RANOBELIB -> 3
                else -> 1
            }
            val endpoint = if (isAnime) "anime" else "manga"
            val baseWeb = getWebBase(source)
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val path = "/$endpoint?q=$encoded&site_id[]=$siteId"

            val json = fetchJson(path, siteId, baseWeb)
                ?: return@withContext Result.failure(Exception("Пустой ответ от ${source.title}"))

            val data = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<ShikimoriSearchResult>()

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val rusName = item.optString("rus_name").orEmpty()
                val engName = item.optString("eng_name").orEmpty()
                val name = item.optString("name").orEmpty()
                val slugUrl = item.optString("slug_url").orEmpty()

                val ruTitle = rusName.ifBlank { if (name.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) name else "" }
                val otherTitle = engName.ifBlank { if (ruTitle != name) name else "" }

                val coverObj = item.optJSONObject("cover")
                val coverUrl = coverObj?.optString("default")?.takeIf { it.isNotBlank() }
                    ?: coverObj?.optString("md")?.takeIf { it.isNotBlank() }
                    ?: coverObj?.optString("thumbnail")?.takeIf { it.isNotBlank() }

                val releaseStr = item.optString("releaseDateString")
                val year = Regex("""\d{4}""").find(releaseStr)?.value?.toIntOrNull()

                val typeObj = item.optJSONObject("type")
                val typeLabel = typeObj?.optString("label").orEmpty()

                val targetType = when (source) {
                    MediaSource.ANIMELIB -> WorkType.ANIME
                    MediaSource.RANOBELIB -> WorkType.BOOK
                    else -> WorkType.MANGA
                }

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = otherTitle.ifBlank { ruTitle },
                        russian = ruTitle,
                        kind = typeLabel.lowercase().ifBlank {
                            if (isAnime) "аниме" else if (isRanobe) "ранобэ" else "манга"
                        },
                        year = year,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = null,
                        volumes = null,
                        targetType = targetType,
                        endpoint = endpoint,
                        url = "$baseWeb/ru/$slugUrl",
                        source = source,
                        stringId = slugUrl
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(
        slugUrl: String,
        source: MediaSource
    ): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val isAnime = source == MediaSource.ANIMELIB
            val isRanobe = source == MediaSource.RANOBELIB
            val siteId = when (source) {
                MediaSource.ANIMELIB -> 5
                MediaSource.RANOBELIB -> 3
                else -> 1
            }
            val endpoint = if (isAnime) "anime" else "manga"
            val baseWeb = getWebBase(source)
            val epFields = if (isAnime) "&fields[]=episodes" else ""
            val path = "/$endpoint/$slugUrl?fields[]=summary&fields[]=genres&fields[]=tags&fields[]=authors$epFields"

            val json = fetchJson(path, siteId, baseWeb)
                ?: return@withContext Result.failure(Exception("${source.title} не вернул данные"))

            val data = json.optJSONObject("data")
                ?: return@withContext Result.failure(Exception("Данные не найдены на ${source.title}"))

            val rusName = ParserUtils.cleanText(data.optNonEmptyString("rus_name"))
            val engName = ParserUtils.cleanText(data.optNonEmptyString("eng_name"))
            val name = ParserUtils.cleanText(data.optNonEmptyString("name"))

            val ruTitle = rusName.ifBlank { if (name.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) name else "" }
            val primaryTitle = ruTitle.ifBlank { engName.ifBlank { name } }

            val altTitles = mutableListOf<String>()
            if (engName.isNotBlank() && engName != primaryTitle && !engName.equals("null", ignoreCase = true)) altTitles.add(engName)
            if (name.isNotBlank() && name != primaryTitle && name != engName && !name.equals("null", ignoreCase = true)) altTitles.add(name)

            val summaryObj = data.optJSONObject("summary")
            val rawDescription = extractSummaryText(summaryObj)
            val description = ParserUtils.cleanDescription(rawDescription)

            val coverObj = data.optJSONObject("cover")
            val coverUrl = coverObj?.optString("default")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("md")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("thumbnail")?.takeIf { it.isNotBlank() }

            val releaseStr = data.optString("releaseDateString")
            val year = Regex("""\d{4}""").find(releaseStr)?.value?.toIntOrNull()

            val typeObj = data.optJSONObject("type")
            val typeLabel = typeObj?.optString("label").orEmpty().lowercase()

            val targetType = when (source) {
                MediaSource.ANIMELIB -> WorkType.ANIME
                MediaSource.RANOBELIB -> WorkType.BOOK
                else -> WorkType.MANGA
            }

            val mangaType = when {
                isRanobe || isAnime -> null
                typeLabel.contains("манхва") -> MangaType.MANHWA
                typeLabel.contains("маньхуа") -> MangaType.MANHUA
                else -> MangaType.MANGA
            }

            val genres = mutableListOf<String>()
            data.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.optJSONObject(i)?.optString("name")?.trim()
                    if (!g.isNullOrBlank() && !genres.contains(g)) {
                        genres.add(g)
                    }
                }
            }
            val tags = mutableListOf<String>()
            data.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i)?.optString("name")?.trim()
                    if (!t.isNullOrBlank() && !tags.contains(t)) {
                        tags.add(t)
                    }
                }
            }

            var authorName: String? = null
            data.optJSONArray("authors")?.let { arr ->
                if (arr.length() > 0) {
                    val aObj = arr.optJSONObject(0)
                    val rawAuthor = aObj?.optNonEmptyString("rus_name")
                        ?: aObj?.optNonEmptyString("name")
                    authorName = ParserUtils.sanitizeAuthor(rawAuthor)
                }
            }

            val itemsCountObj = data.optJSONObject("items_count")
            val episodes = if (isAnime) {
                itemsCountObj?.optDouble("total")?.takeIf { !it.isNaN() && it > 0 }
                    ?: itemsCountObj?.optDouble("uploaded")?.takeIf { !it.isNaN() && it > 0 }
                    ?: data.optDouble("episodes").takeIf { !it.isNaN() && it > 0 }
                    ?: data.optDouble("episodes_count").takeIf { !it.isNaN() && it > 0 }
            } else null

            var chaptersCount: Double? = null
            var volumesCount: Double? = null
            if (!isAnime) {
                try {
                    val chJson = fetchJson("/manga/$slugUrl/chapters", siteId, baseWeb)
                    val chArr = chJson?.optJSONArray("data")
                    if (chArr != null && chArr.length() > 0) {
                        chaptersCount = chArr.length().toDouble()
                        var maxVol = 0
                        for (ci in 0 until chArr.length()) {
                            val vol = chArr.optJSONObject(ci)?.optString("volume")?.toIntOrNull()
                                ?: chArr.optJSONObject(ci)?.optInt("volume") ?: 0
                            if (vol > maxVol) maxVol = vol
                        }
                        if (maxVol > 0) volumesCount = maxVol.toDouble()
                    }
                } catch (_: Exception) {}
            }

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = description.trim(),
                    type = targetType,
                    mangaType = mangaType,
                    year = year,
                    episodes = episodes,
                    chapters = if (targetType == WorkType.MANGA) chaptersCount else null,
                    volumes = volumesCount,
                    bookChapters = if (targetType == WorkType.BOOK) chaptersCount else null,
                    coverUrl = coverUrl,
                    author = authorName,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    link = "$baseWeb/ru/$slugUrl",
                    source = source
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractSummaryText(obj: JSONObject?): String {
        if (obj == null) return ""
        val sb = StringBuilder()
        fun traverse(item: Any) {
            when (item) {
                is JSONObject -> {
                    if (item.optString("type") == "text") {
                        val t = item.optString("text")
                        if (t.isNotBlank()) sb.append(t)
                    } else if (item.optString("type") == "paragraph") {
                        val content = item.optJSONArray("content")
                        if (content != null) {
                            for (i in 0 until content.length()) {
                                traverse(content.get(i))
                            }
                        }
                        sb.append("\n\n")
                    } else {
                        val content = item.optJSONArray("content")
                        if (content != null) {
                            for (i in 0 until content.length()) {
                                traverse(content.get(i))
                            }
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until item.length()) {
                        traverse(item.get(i))
                    }
                }
            }
        }
        traverse(obj)
        return sb.toString().trim()
    }

    private fun fetchJson(pathOrUrl: String, siteId: Int, originUrl: String): JSONObject? {
        val path = if (pathOrUrl.startsWith("http")) {
            val idx = pathOrUrl.indexOf("/api/")
            if (idx >= 0) pathOrUrl.substring(idx + 4) else pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_LIB_SOCIAL_API)
        for (mirror in mirrors) {
            val targetUrl = if (path.startsWith("http")) path else "$mirror$path"
            val result = executeSingleFetch(targetUrl, siteId, originUrl)
            if (result != null) {
                if (!path.startsWith("http")) {
                    ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_LIB_SOCIAL_API, mirror)
                }
                return result
            } else if (!path.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_LIB_SOCIAL_API, mirror)
            }
        }
        return null
    }

    private fun executeSingleFetch(urlString: String, siteId: Int, originUrl: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Origin", originUrl)
            conn.setRequestProperty("Referer", "$originUrl/")
            conn.setRequestProperty("Site-Id", siteId.toString())
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
