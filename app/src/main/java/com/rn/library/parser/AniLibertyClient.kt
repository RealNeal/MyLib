package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object AniLibertyClient {

    private const val BASE_API_URL = "https://anilibria.top/api/v1"
    private const val MIRROR_API_URL = "https://aniliberty.top/api/v1"
    private const val WEB_BASE_URL = "https://aniliberty.top"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun isAniLibertyUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("aniliberty.top") ||
                lower.contains("aniliberty.me") ||
                lower.contains("aniliberty.app") ||
                lower.contains("anilibria.top") ||
                lower.contains("anilibria.tv")
    }

    fun parseAniLibertyId(url: String): String? {
        val trimmed = url.trim()
        val regex = Regex("""(?:anime/releases|release)/([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1]
        }
        val idRegex = Regex("""/(\d+)(?:[/?#]|$)""")
        return idRegex.find(trimmed)?.groupValues?.get(1)
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext Result.success(emptyList())

            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "$BASE_API_URL/anime/catalog/releases?f[search]=$encoded&limit=20"

            val json = fetchJson(urlString) ?: fetchJson("$MIRROR_API_URL/anime/catalog/releases?f[search]=$encoded&limit=20")
            if (json == null) {
                return@withContext Result.failure(Exception("Не удалось получить ответ от AniLiberty"))
            }

            val data = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<ShikimoriSearchResult>()

            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val alias = item.optString("alias").orEmpty()
                val nameObj = item.optJSONObject("name")
                val mainTitle = nameObj?.optString("main").orEmpty()
                val engTitle = nameObj?.optString("english").orEmpty()
                val ruTitle = if (mainTitle.isNotBlank()) mainTitle else engTitle

                val posterObj = item.optJSONObject("poster")
                val posterOptimized = posterObj?.optJSONObject("optimized")
                val rawPoster = posterOptimized?.optString("preview")?.takeIf { it.isNotBlank() }
                    ?: posterObj?.optString("src")?.takeIf { it.isNotBlank() }
                val coverUrl = when {
                    rawPoster == null -> null
                    rawPoster.startsWith("http") -> rawPoster
                    else -> "https://anilibria.top$rawPoster"
                }

                val year = item.optInt("year").takeIf { it > 0 }
                val episodes = item.optInt("episodes_total").takeIf { it > 0 }

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = engTitle.ifBlank { ruTitle },
                        russian = ruTitle,
                        kind = "anime",
                        year = year,
                        coverUrl = coverUrl,
                        episodes = episodes,
                        targetType = WorkType.ANIME,
                        endpoint = "release",
                        url = "$WEB_BASE_URL/anime/releases/${alias.ifBlank { id.toString() }}",
                        source = MediaSource.ANILIBERTY,
                        stringId = alias.ifBlank { id.toString() }
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
            val urlString = "$BASE_API_URL/anime/releases/$targetId"
            val json = fetchJson(urlString) ?: fetchJson("$MIRROR_API_URL/anime/releases/$targetId")
                ?: return@withContext Result.failure(Exception("Релиз AniLiberty не найден: $targetId"))

            val id = json.optLong("id")
            val alias = json.optString("alias").orEmpty()
            val nameObj = json.optJSONObject("name")
            val mainTitle = nameObj?.optString("main").orEmpty()
            val engTitle = nameObj?.optString("english").orEmpty()
            val altTitle = nameObj?.optString("alternative").orEmpty()

            val primaryTitle = mainTitle.ifBlank { engTitle }
            val altTitles = listOfNotNull(
                engTitle.takeIf { it.isNotBlank() && it != primaryTitle },
                altTitle.takeIf { it.isNotBlank() }
            )

            val description = json.optString("description").orEmpty()
            val year = json.optInt("year").takeIf { it > 0 }

            val seasonObj = json.optJSONObject("season")
            val seasonStr = seasonObj?.optString("value") ?: json.optString("season")
            val animeSeason = when (seasonStr.lowercase()) {
                "winter" -> AnimeSeason.WINTER
                "spring" -> AnimeSeason.SPRING
                "summer" -> AnimeSeason.SUMMER
                "fall", "autumn" -> AnimeSeason.FALL
                else -> null
            }

            val posterObj = json.optJSONObject("poster")
            val posterOptimized = posterObj?.optJSONObject("optimized")
            val rawPoster = posterOptimized?.optString("src")?.takeIf { it.isNotBlank() }
                ?: posterObj?.optString("src")?.takeIf { it.isNotBlank() }
            val coverUrl = when {
                rawPoster == null -> null
                rawPoster.startsWith("http") -> rawPoster
                else -> "https://anilibria.top$rawPoster"
            }

            val episodesTotal = json.optDouble("episodes_total").takeIf { !it.isNaN() && it > 0 }

            val genresList = mutableListOf<String>()
            val genresArr = json.optJSONArray("genres")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val g = genresArr.optJSONObject(i)
                    val gName = g?.optString("name")
                    if (!gName.isNullOrBlank()) {
                        genresList.add(gName)
                    }
                }
            }

            val meta = ParsedWorkMetadata(
                title = primaryTitle,
                alternativeTitles = altTitles,
                description = description,
                type = WorkType.ANIME,
                animeSeason = animeSeason,
                year = year,
                episodes = episodesTotal,
                coverUrl = coverUrl,
                genres = ParserUtils.normalizeChips(genresList),
                link = "$WEB_BASE_URL/anime/releases/${alias.ifBlank { id.toString() }}",
                source = MediaSource.ANILIBERTY
            )

            Result.success(meta)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchJson(urlString: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text)
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
