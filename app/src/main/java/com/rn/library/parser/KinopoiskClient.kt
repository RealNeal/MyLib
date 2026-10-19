package com.rn.library.parser

import com.rn.library.data.SeriesType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object KinopoiskClient {
    private const val API_BASE = "https://kinopoiskapiunofficial.tech/api"
    private const val API_KEY = "e30ffed0-76ab-4dd6-b41f-4c9da2b2735b"
    private const val USER_AGENT = "MyLibraryApp/2.7 (Android; github.com/RealNeal/MyLib)"

    fun isKinopoiskUrl(url: String): Boolean {
        return url.contains("kinopoisk.ru/film/", ignoreCase = true) ||
                url.contains("kinopoisk.ru/series/", ignoreCase = true)
    }

    fun parseKinopoiskId(url: String): Long? {
        val regex = Regex("""kinopoisk\.ru/(?:film|series)/(\d+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)?.toLongOrNull()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$API_BASE/v2.1/films/search-by-keyword?keyword=$encoded&page=1"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Пустой ответ от Кинопоиска"))
            val films = json.optJSONArray("films") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until films.length()) {
                val item = films.optJSONObject(i) ?: continue
                val id = item.optLong("filmId")
                val nameRu = item.optNonEmptyString("nameRu").orEmpty()
                val nameEn = item.optNonEmptyString("nameEn").orEmpty()

                val mainTitle = nameRu.ifBlank { nameEn }
                val subTitle = if (nameRu.isNotBlank() && nameEn.isNotBlank() && nameRu != nameEn) nameEn else ""

                val typeStr = item.optString("type")
                val isSeries = typeStr.contains("SERIES", ignoreCase = true) || typeStr.contains("SHOW", ignoreCase = true)
                val targetType = WorkType.SERIES

                val year = item.optString("year").toIntOrNull()
                val posterUrl = item.optString("posterUrl").takeIf { it.isNotBlank() && !it.contains("no-poster") }
                    ?: item.optString("posterUrlPreview").takeIf { it.isNotBlank() && !it.contains("no-poster") }

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = mainTitle,
                        russian = subTitle,
                        kind = if (isSeries) "сериал" else "фильм",
                        year = year,
                        coverUrl = posterUrl,
                        episodes = null,
                        chapters = null,
                        volumes = null,
                        targetType = targetType,
                        endpoint = "films",
                        url = "https://www.kinopoisk.ru/film/$id/",
                        source = MediaSource.KINOPOISK,
                        stringId = id.toString()
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(filmId: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/v2.2/films/$filmId"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Кинопоиск не вернул данные"))

            val nameRu = json.optNonEmptyString("nameRu").orEmpty()
            val nameOriginal = json.optNonEmptyString("nameOriginal").orEmpty()
            val nameEn = json.optNonEmptyString("nameEn").orEmpty()

            val primaryTitle = nameRu.ifBlank { nameOriginal.ifBlank { nameEn } }

            val altTitles = mutableListOf<String>()
            if (nameOriginal.isNotBlank() && nameOriginal != primaryTitle && !nameOriginal.equals("null", ignoreCase = true)) {
                altTitles.add(nameOriginal)
            }
            if (nameEn.isNotBlank() && nameEn != primaryTitle && nameEn != nameOriginal && !nameEn.equals("null", ignoreCase = true)) {
                altTitles.add(nameEn)
            }

            val desc = json.optNonEmptyString("description")
                ?: json.optNonEmptyString("shortDescription").orEmpty()

            val year = json.optInt("year").takeIf { it > 0 }
            val isSerial = json.optBoolean("serial", false)
            val seriesType = if (isSerial) SeriesType.TV_SERIES else SeriesType.FILM
            val targetType = WorkType.SERIES

            val posterUrl = json.optString("posterUrl").takeIf { it.isNotBlank() && !it.contains("no-poster") }
                ?: json.optString("coverUrl").takeIf { it.isNotBlank() }

            val genres = mutableListOf<String>()
            json.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.optJSONObject(i)?.optString("genre")?.trim()
                    if (!g.isNullOrBlank()) {
                        genres.add(g.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                    }
                }
            }

            val webUrl = json.optString("webUrl").takeIf { it.isNotBlank() }
                ?: "https://www.kinopoisk.ru/film/$filmId/"

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = desc.trim(),
                    type = targetType,
                    seriesType = seriesType,
                    year = year,
                    coverUrl = posterUrl,
                    genres = ParserUtils.normalizeChips(genres),
                    link = webUrl,
                    source = MediaSource.KINOPOISK
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchJson(urlString: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("X-API-KEY", API_KEY)
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
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
