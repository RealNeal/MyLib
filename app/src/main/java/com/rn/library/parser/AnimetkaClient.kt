package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AnimetkaClient {

    private const val BASE_URL = "https://animetka.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun isAnimetkaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("animetka.com")
    }

    fun parseAnimetkaId(url: String): String? {
        val trimmed = url.trim()
        val regex = Regex("""(?:animetka\.com/(?:api/)?anime/|/anime/)(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        val idRegex = Regex("""^(\d+)""")
        val lastSegment = trimmed.substringAfterLast("/")
        return idRegex.find(lastSegment)?.groupValues?.get(1) ?: trimmed.filter { it.isDigit() }.takeIf { it.isNotBlank() }
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())

            val baseUrl = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_ANIMETKA).ifBlank { BASE_URL }
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "$baseUrl/api/anime/search?name=$encoded&limit=20&offset=0"

            val jsonArray = fetchJsonArray(urlString)
                ?: return@withContext Result.failure(Exception("Не удалось получить ответ от AniMetka"))

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val id = item.optLong("animetka_id").takeIf { it > 0 }
                    ?: item.optLong("id")
                if (id <= 0) continue

                val ruTitle = item.optString("title").orEmpty()
                val origTitle = item.optString("title_orig").orEmpty()
                val year = item.optInt("year").takeIf { it > 0 }
                val episodes = item.optInt("episodes_total").takeIf { it > 0 }

                val coverUrl = item.optString("anime_poster_url").takeIf { it.isNotBlank() }
                    ?: item.optString("poster_url").takeIf { it.isNotBlank() }
                    ?: "$baseUrl/api/images/posters/big/$id.webp"

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = origTitle.ifBlank { ruTitle },
                        russian = ruTitle,
                        kind = "anime",
                        year = year,
                        coverUrl = coverUrl,
                        episodes = episodes,
                        targetType = WorkType.ANIME,
                        endpoint = "anime",
                        url = "$baseUrl/anime/$id",
                        source = MediaSource.ANIMETKA,
                        stringId = id.toString()
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(targetId: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val id = parseAnimetkaId(targetId) ?: targetId.filter { it.isDigit() }
            if (id.isBlank()) {
                return@withContext Result.failure(Exception("Некорректный ID аниме: $targetId"))
            }

            val baseUrl = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_ANIMETKA).ifBlank { BASE_URL }
            val urlString = "$baseUrl/api/anime/$id"

            val json = fetchJsonObject(urlString)
                ?: return@withContext Result.failure(Exception("Релиз AniMetka не найден: $id"))

            val titleRu = json.optString("title").orEmpty()
            val animeTitle = json.optString("anime_title").orEmpty()
            val primaryTitle = animeTitle.ifBlank { titleRu }

            val titleEn = json.optString("title_en").orEmpty()
            val titleOrig = json.optString("title_orig").orEmpty()

            val alts = mutableListOf<String>()
            if (titleRu.isNotBlank() && titleRu != primaryTitle) alts.add(titleRu)
            if (titleOrig.isNotBlank() && titleOrig != primaryTitle) alts.add(titleOrig)
            if (titleEn.isNotBlank() && titleEn != primaryTitle) alts.add(titleEn)

            fun addStringArray(key: String) {
                val arr = json.optJSONArray(key) ?: return
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).orEmpty().trim()
                    if (s.isNotBlank() && s != primaryTitle && !alts.contains(s)) {
                        alts.add(s)
                    }
                }
            }

            addStringArray("other_titles")
            addStringArray("other_titles_en")
            addStringArray("other_titles_jp")

            val rawDesc = json.optString("anime_description").takeIf { it.isNotBlank() }
                ?: json.optString("description").orEmpty()
            val description = ParserUtils.cleanDescription(rawDesc)

            val year = json.optInt("year").takeIf { it > 0 }
            val episodesTotal = json.optDouble("episodes_total").takeIf { !it.isNaN() && it > 0 }

            val animePosterUrl = json.optString("anime_poster_url").takeIf { it.isNotBlank() }
            val posterUrl = json.optString("poster_url").takeIf { it.isNotBlank() }
            val coverUrl = animePosterUrl ?: posterUrl ?: "$baseUrl/api/images/posters/big/$id.webp"

            val screenshotsList = mutableListOf<String>()
            val screenshotsArr = json.optJSONArray("screenshots")
            if (screenshotsArr != null) {
                for (i in 0 until screenshotsArr.length()) {
                    val sc = screenshotsArr.optString(i).orEmpty().trim()
                    if (sc.isNotBlank() && !screenshotsList.contains(sc)) {
                        screenshotsList.add(sc)
                    }
                }
            }

            val genresList = mutableListOf<String>()
            fun collectArray(key: String, target: MutableList<String>) {
                val arr = json.optJSONArray(key) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).orEmpty().trim()
                    if (item.isNotBlank() && !target.contains(item)) {
                        target.add(item)
                    }
                }
            }
            collectArray("all_genres", genresList)
            collectArray("anime_genres", genresList)
            collectArray("genres", genresList)

            val tagsList = mutableListOf<String>()
            collectArray("anime_studios", tagsList)
            collectArray("countries", tagsList)

            // Author / Directors / Writers / Studios
            val directorsArr = json.optJSONArray("directors")
            val writersArr = json.optJSONArray("writers")
            val studiosArr = json.optJSONArray("anime_studios")
            val author = writersArr?.optString(0)?.takeIf { it.isNotBlank() }
                ?: directorsArr?.optString(0)?.takeIf { it.isNotBlank() }
                ?: studiosArr?.optString(0)?.takeIf { it.isNotBlank() }

            // Determine Season from aired_at / premiere_world (YYYY-MM-DD)
            val dateStr = json.optString("aired_at").takeIf { it.isNotBlank() }
                ?: json.optString("premiere_world").takeIf { it.isNotBlank() }
            val animeSeason = dateStr?.let { d ->
                val month = d.split("-").getOrNull(1)?.toIntOrNull()
                when (month) {
                    12, 1, 2 -> AnimeSeason.WINTER
                    3, 4, 5 -> AnimeSeason.SPRING
                    6, 7, 8 -> AnimeSeason.SUMMER
                    9, 10, 11 -> AnimeSeason.FALL
                    else -> null
                }
            }

            val meta = ParsedWorkMetadata(
                title = primaryTitle,
                alternativeTitles = alts,
                description = description,
                type = WorkType.ANIME,
                animeSeason = animeSeason,
                year = year,
                episodes = episodesTotal,
                coverUrl = coverUrl,
                author = author,
                genres = ParserUtils.normalizeChips(genresList),
                tags = ParserUtils.normalizeChips(tagsList),
                additionalCovers = screenshotsList,
                link = "$baseUrl/anime/$id",
                source = MediaSource.ANIMETKA
            )

            Result.success(meta)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchJsonObject(urlString: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://animetka.com/")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (text.startsWith("[")) {
                    val arr = JSONArray(text)
                    arr.optJSONObject(0)
                } else if (text.startsWith("{")) {
                    JSONObject(text)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchJsonArray(urlString: String): JSONArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://animetka.com/")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (text.startsWith("[")) {
                    JSONArray(text)
                } else if (text.startsWith("{")) {
                    val obj = JSONObject(text)
                    obj.optJSONArray("data")
                        ?: obj.optJSONArray("results")
                        ?: obj.optJSONArray("value")
                        ?: obj.optJSONArray("anime")
                } else {
                    null
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
