package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object JikanClient {
    private const val BASE_URL = "https://api.jikan.moe/v4"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun isMalUrl(url: String): Boolean {
        return url.contains("myanimelist.net/anime/", ignoreCase = true) ||
                url.contains("myanimelist.net/manga/", ignoreCase = true)
    }

    fun parseMalUrl(url: String): Pair<String, Long>? {
        val regex = Regex("""myanimelist\.net/(anime|manga)/(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url.trim()) ?: return null
        val type = match.groupValues[1].lowercase()
        val id = match.groupValues[2].toLongOrNull() ?: return null
        return Pair(type, id)
    }

    private fun getShikiBase(): String = "${ShikimoriClient.getBaseUrl()}/api"
    private const val SHIKI_UA = "MyLibraryApp/2.7 (Android; github.com/RealNeal/MyLib)"

    suspend fun search(
        query: String,
        filter: ShikimoriFilterType = ShikimoriFilterType.ALL
    ): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val list = mutableListOf<ShikimoriSearchResult>()
            val shikiBase = getShikiBase()

            val searchAnime = filter == ShikimoriFilterType.ALL || filter == ShikimoriFilterType.ANIME
            val searchManga = filter == ShikimoriFilterType.ALL || filter == ShikimoriFilterType.MANGA || filter == ShikimoriFilterType.RANOBE

            if (searchAnime && searchManga) {
                val animeDeferred = async {
                    val animeUrl = "$shikiBase/animes?search=$encoded&limit=15"
                    fetchShikiMalSearchResults(animeUrl, "anime", WorkType.ANIME)
                }
                val mangaDeferred = async {
                    val kindParam = if (filter == ShikimoriFilterType.RANOBE) "&kind=light_novel,novel" else ""
                    val mangaUrl = "$shikiBase/mangas?search=$encoded$kindParam&limit=15"
                    fetchShikiMalSearchResults(mangaUrl, "manga", if (filter == ShikimoriFilterType.RANOBE) WorkType.BOOK else WorkType.MANGA)
                }
                list.addAll(animeDeferred.await())
                list.addAll(mangaDeferred.await())
            } else if (searchAnime) {
                val animeUrl = "$shikiBase/animes?search=$encoded&limit=15"
                list.addAll(fetchShikiMalSearchResults(animeUrl, "anime", WorkType.ANIME))
            } else if (searchManga) {
                val kindParam = if (filter == ShikimoriFilterType.RANOBE) "&kind=light_novel,novel" else ""
                val mangaUrl = "$shikiBase/mangas?search=$encoded$kindParam&limit=15"
                list.addAll(fetchShikiMalSearchResults(mangaUrl, "manga", if (filter == ShikimoriFilterType.RANOBE) WorkType.BOOK else WorkType.MANGA))
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(endpoint: String, id: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val ep = if (endpoint == "animes") "anime" else if (endpoint == "mangas") "manga" else endpoint
            val url = "$BASE_URL/$ep/$id/full"
            val json = fetchJsonWithTimeout(url, 3500)
            val data = json?.optJSONObject("data")
            if (data == null) {
                val shikiEndpoint = if (ep == "anime") "animes" else "mangas"
                val shikiUrl = "${getShikiBase()}/$shikiEndpoint/$id"
                val shikiJson = fetchJsonWithHeaders(shikiUrl, SHIKI_UA)
                if (shikiJson != null) {
                    return@withContext parseShikiMalDetails(shikiJson, ep, id)
                }
                return@withContext Result.failure(Exception("Данные не найдены на MyAnimeList"))
            }

            val title = data.optString("title").orEmpty()
            val titleEn = data.optString("title_english").takeIf { it.isNotBlank() }
            val titleJp = data.optString("title_japanese").takeIf { it.isNotBlank() }

            val mainTitle = titleEn ?: title
            val altTitles = mutableListOf<String>()
            if (title.isNotBlank() && title != mainTitle) altTitles.add(title)
            if (titleJp != null && titleJp != mainTitle) altTitles.add(titleJp)

            data.optJSONArray("titles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val tObj = arr.optJSONObject(i) ?: continue
                    val tVal = tObj.optString("title").trim()
                    if (tVal.isNotBlank() && tVal != mainTitle && !altTitles.contains(tVal)) {
                        altTitles.add(tVal)
                    }
                }
            }

            val images = data.optJSONObject("images")
            val webp = images?.optJSONObject("webp")
            val jpg = images?.optJSONObject("jpg")
            val coverUrl = webp?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                ?: jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                ?: jpg?.optString("maximum_image_url")?.takeIf { it.isNotBlank() }
                ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() }

            val synopsis = data.optString("synopsis").orEmpty()
            val cleanDesc = ParserUtils.cleanDescription(synopsis.replace(Regex("""\[Written by MAL Rewrite\]"""), ""))

            val typeStr = data.optString("type").lowercase()
            val isAnime = ep == "anime"
            val isNovel = typeStr.contains("novel")
            val targetType = when {
                isAnime -> WorkType.ANIME
                isNovel -> WorkType.BOOK
                else -> WorkType.MANGA
            }

            val mangaType = when {
                typeStr == "manhwa" -> MangaType.MANHWA
                typeStr == "manhua" -> MangaType.MANHUA
                !isAnime && !isNovel -> MangaType.MANGA
                else -> null
            }

            val year = data.optInt("year").takeIf { it > 0 }
                ?: data.optJSONObject("aired")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year")?.takeIf { it > 0 }
                ?: data.optJSONObject("published")?.optJSONObject("prop")?.optJSONObject("from")?.optInt("year")?.takeIf { it > 0 }

            val seasonStr = data.optString("season").lowercase()
            val animeSeason = when (seasonStr) {
                "winter" -> AnimeSeason.WINTER
                "spring" -> AnimeSeason.SPRING
                "summer" -> AnimeSeason.SUMMER
                "fall" -> AnimeSeason.FALL
                else -> null
            }

            val episodes = data.optDouble("episodes").takeIf { !it.isNaN() && it > 0 }
            val chapters = data.optDouble("chapters").takeIf { !it.isNaN() && it > 0 }
            val volumes = data.optDouble("volumes").takeIf { !it.isNaN() && it > 0 }

            val genres = mutableListOf<String>()
            val tags = mutableListOf<String>()
            data.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val gObj = arr.optJSONObject(i) ?: continue
                    val gName = gObj.optString("name").trim()
                    if (gName.isNotBlank() && !genres.contains(gName)) {
                        genres.add(gName)
                    }
                }
            }
            val appendTags: (String) -> Unit = { key ->
                data.optJSONArray(key)?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val gObj = arr.optJSONObject(i) ?: continue
                        val gName = gObj.optString("name").trim()
                        if (gName.isNotBlank() && !tags.contains(gName)) {
                            tags.add(gName)
                        }
                    }
                }
            }
            appendTags("themes")
            appendTags("demographics")

            // Authors
            var authorName: String? = null
            data.optJSONArray("authors")?.let { arr ->
                if (arr.length() > 0) {
                    val rawA = arr.optJSONObject(0)?.optString("name")
                    authorName = ParserUtils.sanitizeAuthor(rawA)
                }
            }

            val malUrl = data.optString("url").takeIf { it.isNotBlank() }
                ?: "https://myanimelist.net/$ep/$id"

            Result.success(
                ParsedWorkMetadata(
                    title = mainTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = cleanDesc,
                    type = targetType,
                    mangaType = mangaType,
                    animeSeason = animeSeason,
                    year = year,
                    episodes = if (targetType == WorkType.ANIME) episodes else null,
                    chapters = if (targetType == WorkType.MANGA) chapters else null,
                    volumes = if (targetType == WorkType.MANGA) volumes else null,
                    bookChapters = if (targetType == WorkType.BOOK) chapters else null,
                    coverUrl = coverUrl,
                    author = authorName,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    link = malUrl,
                    source = MediaSource.MYANIMELIST
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchShikiMalSearchResults(
        urlStr: String,
        endpoint: String,
        targetType: WorkType
    ): List<ShikimoriSearchResult> {
        val text = fetchTextWithHeaders(urlStr, SHIKI_UA) ?: return emptyList()
        val arr = try { org.json.JSONArray(text) } catch (_: Exception) { return emptyList() }
        val list = mutableListOf<ShikimoriSearchResult>()

        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optLong("id")
            val name = item.optString("name").orEmpty()
            val rus = item.optString("russian").orEmpty()
            val kind = item.optString("kind").lowercase()

            val imageObj = item.optJSONObject("image")
            val rawCover = imageObj?.optString("original")?.takeIf { it.isNotBlank() }
                ?: imageObj?.optString("preview")?.takeIf { it.isNotBlank() }
            val coverUrl = when {
                rawCover == null -> null
                rawCover.startsWith("http") -> rawCover
                else -> "https://shikimori.io$rawCover"
            }

            val year = item.optString("aired_on").takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()
                ?: item.optString("released_on").takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()

            val eps = item.optInt("episodes").takeIf { it > 0 }
            val chs = item.optInt("chapters").takeIf { it > 0 }
            val vols = item.optInt("volumes").takeIf { it > 0 }

            val isBook = kind.contains("novel") || targetType == WorkType.BOOK
            val actualTarget = when {
                targetType == WorkType.ANIME -> WorkType.ANIME
                isBook -> WorkType.BOOK
                else -> WorkType.MANGA
            }

            list.add(
                ShikimoriSearchResult(
                    id = id,
                    name = name.ifBlank { rus },
                    russian = if (rus.isNotBlank() && rus != name) rus else "",
                    kind = kind,
                    year = year,
                    coverUrl = coverUrl,
                    episodes = eps,
                    chapters = chs,
                    volumes = vols,
                    targetType = actualTarget,
                    endpoint = endpoint,
                    url = "https://myanimelist.net/$endpoint/$id",
                    source = MediaSource.MYANIMELIST,
                    stringId = id.toString()
                )
            )
        }
        return list
    }

    private fun parseShikiMalDetails(
        json: JSONObject,
        endpoint: String,
        id: Long
    ): Result<ParsedWorkMetadata> {
        val name = json.optString("name").orEmpty()
        val rus = json.optString("russian").orEmpty()
        val mainTitle = name.ifBlank { rus }
        val altTitles = mutableListOf<String>()
        if (rus.isNotBlank() && rus != mainTitle) altTitles.add(rus)
        json.optJSONArray("synonyms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s != mainTitle && !altTitles.contains(s)) altTitles.add(s)
            }
        }
        json.optJSONArray("english")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s != mainTitle && !altTitles.contains(s)) altTitles.add(s)
            }
        }
        json.optJSONArray("japanese")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).trim()
                if (s.isNotBlank() && s != mainTitle && !altTitles.contains(s)) altTitles.add(s)
            }
        }

        val imageObj = json.optJSONObject("image")
        val rawCover = imageObj?.optString("original")?.takeIf { it.isNotBlank() }
            ?: imageObj?.optString("preview")?.takeIf { it.isNotBlank() }
        val coverUrl = when {
            rawCover == null -> null
            rawCover.startsWith("http") -> rawCover
            else -> "https://shikimori.io$rawCover"
        }

        val descHtml = json.optString("description_html").ifBlank { json.optString("description") }
        val cleanDesc = ParserUtils.cleanDescription(descHtml)

        val year = json.optString("aired_on").takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()
            ?: json.optString("released_on").takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()

        val kind = json.optString("kind").lowercase()
        val isAnime = endpoint == "anime"
        val isNovel = kind.contains("novel")
        val targetType = when {
            isAnime -> WorkType.ANIME
            isNovel -> WorkType.BOOK
            else -> WorkType.MANGA
        }
        val mangaType = when {
            kind == "manhwa" -> MangaType.MANHWA
            kind == "manhua" -> MangaType.MANHUA
            !isAnime && !isNovel -> MangaType.MANGA
            else -> null
        }

        val episodes = json.optDouble("episodes").takeIf { !it.isNaN() && it > 0 }
        val chapters = json.optDouble("chapters").takeIf { !it.isNaN() && it > 0 }
        val volumes = json.optDouble("volumes").takeIf { !it.isNaN() && it > 0 }

        val genres = mutableListOf<String>()
        json.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i)?.optString("name")?.trim()
                if (!g.isNullOrBlank() && !genres.contains(g)) genres.add(g)
            }
        }

        return Result.success(
            ParsedWorkMetadata(
                title = mainTitle,
                alternativeTitles = altTitles.distinct(),
                description = cleanDesc,
                type = targetType,
                mangaType = mangaType,
                year = year,
                episodes = if (targetType == WorkType.ANIME) episodes else null,
                chapters = if (targetType == WorkType.MANGA) chapters else null,
                volumes = if (targetType == WorkType.MANGA) volumes else null,
                bookChapters = if (targetType == WorkType.BOOK) chapters else null,
                coverUrl = coverUrl,
                genres = ParserUtils.normalizeChips(genres),
                link = "https://myanimelist.net/$endpoint/$id",
                source = MediaSource.MYANIMELIST
            )
        )
    }

    private fun fetchJsonWithTimeout(urlString: String, timeoutMs: Int): JSONObject? {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchJsonWithHeaders(urlString: String, userAgent: String): JSONObject? {
        val text = fetchTextWithHeaders(urlString, userAgent) ?: return null
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchTextWithHeaders(urlString: String, userAgent: String): String? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
