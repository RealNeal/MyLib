package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AnixartClient {
    private fun getBaseUrls(): List<String> =
        ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_ANIXART_API)

    private const val USER_AGENT = "AnixartApp/9.0 BETA 7-25082901 (Android 9; SDK 28; x86_64; ROG ASUS AI2201_B; ru)"

    fun isAnixartUrl(url: String): Boolean {
        val lower = url.lowercase()
        return Regex("""(?:anixart|anixsekai|anixmirai)\.[a-z]+""").containsMatchIn(lower)
    }

    fun parseAnixartId(url: String): Long? {
        val trimmed = url.trim()
        val regex = Regex("""(?:anixart\.(?:tv|app)|anixsekai\.com|anixmirai\.com)/release/(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].toLongOrNull()
        }
        val releaseRegex = Regex("""release/(\d+)""", RegexOption.IGNORE_CASE)
        return releaseRegex.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isBlank()) return@withContext Result.success(emptyList())

            val body = JSONObject().apply {
                put("query", trimmedQuery)
                put("searchBy", 0)
            }.toString()

            var json: JSONObject? = null
            for (baseUrl in getBaseUrls()) {
                json = postJson("$baseUrl/search/releases/0", body)
                if (json != null) {
                    ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_ANIXART_API, baseUrl)
                    break
                } else {
                    ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_ANIXART_API, baseUrl)
                }
            }

            if (json == null) {
                return@withContext Result.failure(Exception("Не удалось получить ответ от Anixart"))
            }

            val releases = json.optJSONArray("releases") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<ShikimoriSearchResult>()

            for (i in 0 until releases.length()) {
                val item = releases.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                if (id <= 0) continue

                val titleRu = ParserUtils.cleanText(item.optNonEmptyString("title_ru"))
                val titleOriginal = ParserUtils.cleanText(item.optNonEmptyString("title_original"))
                val titleAlt = ParserUtils.cleanText(item.optNonEmptyString("title_alt"))

                val primaryTitle = titleRu.ifBlank { titleOriginal.ifBlank { titleAlt } }
                val secondaryTitle = if (primaryTitle != titleOriginal && titleOriginal.isNotBlank()) {
                    titleOriginal
                } else if (primaryTitle != titleRu && titleRu.isNotBlank()) {
                    titleRu
                } else ""

                val image = item.optNonEmptyString("image")
                val yearStr = item.optString("year")
                val year = Regex("""\d{4}""").find(yearStr)?.value?.toIntOrNull()

                val epTotal = item.optInt("episodes_total", 0).takeIf { it > 0 }
                    ?: item.optInt("episodes_released", 0).takeIf { it > 0 }

                val categoryObj = item.optJSONObject("category")
                val kind = categoryObj?.optNonEmptyString("name") ?: "сериал"

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = secondaryTitle.ifBlank { primaryTitle },
                        russian = primaryTitle,
                        kind = kind.lowercase(),
                        year = year,
                        coverUrl = image,
                        episodes = epTotal,
                        chapters = null,
                        volumes = null,
                        targetType = WorkType.ANIME,
                        endpoint = "release",
                        url = "https://anixart.tv/release/$id",
                        source = MediaSource.ANIXART,
                        stringId = id.toString()
                    )
                )
            }

            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(releaseId: Long): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            var json: JSONObject? = null
            for (baseUrl in getBaseUrls()) {
                json = getJson("$baseUrl/release/$releaseId?extended_mode=true")
                if (json != null) {
                    ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_ANIXART_API, baseUrl)
                    break
                } else {
                    ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_ANIXART_API, baseUrl)
                }
            }

            if (json == null) {
                return@withContext Result.failure(Exception("Не удалось получить информацию о релизе от Anixart"))
            }

            val rel = json.optJSONObject("release")
                ?: return@withContext Result.failure(Exception("Релиз не найден на Anixart"))

            val titleRu = ParserUtils.cleanText(rel.optNonEmptyString("title_ru"))
            val titleOrig = ParserUtils.cleanText(rel.optNonEmptyString("title_original"))
            val titleAlt = ParserUtils.cleanText(rel.optNonEmptyString("title_alt"))

            val primaryTitle = titleRu.ifBlank { titleOrig.ifBlank { titleAlt } }

            val altTitles = mutableListOf<String>()
            if (titleOrig.isNotBlank() && titleOrig != primaryTitle) {
                altTitles.add(titleOrig)
            }
            if (titleAlt.isNotBlank()) {
                titleAlt.split("\n", ",").forEach { part ->
                    val cleanPart = ParserUtils.cleanText(part)
                    if (cleanPart.isNotBlank() && cleanPart != primaryTitle && !altTitles.contains(cleanPart)) {
                        altTitles.add(cleanPart)
                    }
                }
            }
            if (titleRu.isNotBlank() && titleRu != primaryTitle && !altTitles.contains(titleRu)) {
                altTitles.add(titleRu)
            }

            val rawDescription = rel.optString("description")
            val description = ParserUtils.cleanDescription(rawDescription)

            val coverUrl = rel.optNonEmptyString("image")
            val yearStr = rel.optString("year")
            val year = Regex("""\d{4}""").find(yearStr)?.value?.toIntOrNull()

            val epTotal = rel.optDouble("episodes_total").takeIf { !it.isNaN() && it > 0 }
                ?: rel.optDouble("episodes_released").takeIf { !it.isNaN() && it > 0 }

            val authorRaw = rel.optNonEmptyString("author")
                ?: rel.optNonEmptyString("director")
                ?: rel.optNonEmptyString("studio")
            val author = ParserUtils.sanitizeAuthor(authorRaw)

            val genres = mutableListOf<String>()
            val genresStr = rel.optString("genres")
            if (genresStr.isNotBlank()) {
                genresStr.split(",").forEach { g ->
                    val cleanG = g.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    if (cleanG.isNotBlank() && !genres.contains(cleanG)) {
                        genres.add(cleanG)
                    }
                }
            }

            val seasonNum = rel.optInt("season", 0)
            val animeSeason = when (seasonNum) {
                1 -> AnimeSeason.WINTER
                2 -> AnimeSeason.SPRING
                3 -> AnimeSeason.SUMMER
                4 -> AnimeSeason.FALL
                else -> null
            }

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = description,
                    type = WorkType.ANIME,
                    animeSeason = animeSeason,
                    year = year,
                    episodes = epTotal,
                    coverUrl = coverUrl,
                    author = author,
                    genres = ParserUtils.normalizeChips(genres),
                    link = "https://anixart.tv/release/$releaseId",
                    source = MediaSource.ANIXART
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postJson(urlString: String, jsonBody: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("API-Version", "v2")
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 15000

            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun getJson(urlString: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("API-Version", "v2")
            conn.connectTimeout = 12000
            conn.readTimeout = 15000

            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
