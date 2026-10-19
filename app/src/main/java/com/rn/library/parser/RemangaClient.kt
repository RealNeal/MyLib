package com.rn.library.parser

import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RemangaClient {
    private const val BASE_URL = "https://remanga.org"
    private const val API_BASE = "https://api.remanga.org/api"
    private const val USER_AGENT = "MyLibraryApp/2.7 (Android; github.com/RealNeal/MyLib)"

    fun isRemangaUrl(url: String): Boolean {
        return url.contains("remanga.org/manga/", ignoreCase = true)
    }

    fun parseRemangaDir(url: String): String? {
        val regex = Regex("""remanga\.org\/manga\/([a-zA-Z0-9_\-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$API_BASE/v2/search/?query=$encoded&count=15"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Пустой ответ от Remanga"))
            val results = json.optJSONArray("results") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val dir = item.optString("dir")
                val mainName = item.optString("main_name").orEmpty()
                val secondaryName = item.optString("secondary_name").orEmpty()
                val year = item.optInt("issue_year").takeIf { it > 0 }
                val chs = item.optInt("count_chapters").takeIf { it > 0 }

                val typeObj = item.optJSONObject("type")
                val typeName = typeObj?.optString("name").orEmpty()

                val coverObj = item.optJSONObject("cover")
                val rawCover = coverObj?.optString("high")?.takeIf { it.isNotBlank() }
                    ?: coverObj?.optString("mid")?.takeIf { it.isNotBlank() }
                    ?: coverObj?.optString("low")?.takeIf { it.isNotBlank() }

                val coverUrl = when {
                    rawCover.isNullOrBlank() -> null
                    rawCover.startsWith("http") -> rawCover
                    rawCover.startsWith("/media/") -> "$BASE_URL$rawCover"
                    rawCover.startsWith("media/") -> "$BASE_URL/$rawCover"
                    rawCover.startsWith("/") -> "$BASE_URL/media$rawCover"
                    else -> "$BASE_URL/media/$rawCover"
                }

                val isNovel = typeName.contains("новелла", ignoreCase = true) ||
                        typeName.contains("ранобэ", ignoreCase = true)
                val targetType = if (isNovel) WorkType.BOOK else WorkType.MANGA
                val ruTitle = mainName.ifBlank { if (secondaryName.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) secondaryName else "" }
                val otherTitle = secondaryName.ifBlank { if (ruTitle != mainName) mainName else "" }

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = otherTitle.ifBlank { ruTitle },
                        russian = ruTitle,
                        kind = typeName.lowercase(),
                        year = year,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = chs,
                        volumes = null,
                        targetType = targetType,
                        endpoint = "titles",
                        url = "$BASE_URL/manga/$dir",
                        source = MediaSource.REMANGA,
                        stringId = dir
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(dir: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/titles/$dir/"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Пустой ответ от Remanga"))
            val content = json.optJSONObject("content") ?: return@withContext Result.failure(Exception("Данные не найдены"))

            val mainName = ParserUtils.cleanText(content.optNonEmptyString("main_name"))
            val secondaryName = ParserUtils.cleanText(content.optNonEmptyString("secondary_name"))
            val anotherName = ParserUtils.cleanText(content.optNonEmptyString("another_name"))
            val rawDesc = content.optString("description").orEmpty()
            val cleanDesc = ParserUtils.cleanDescription(rawDesc)

            val ruTitle = mainName.ifBlank { if (secondaryName.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) secondaryName else "" }
            val primaryTitle = ruTitle.ifBlank { secondaryName }

            val altTitles = mutableListOf<String>()
            if (secondaryName.isNotBlank() && secondaryName != primaryTitle && !secondaryName.equals("null", ignoreCase = true)) {
                altTitles.add(secondaryName)
            }
            if (anotherName.isNotBlank()) {
                anotherName.split("/").forEach { alt ->
                    val t = ParserUtils.cleanText(alt)
                    if (t.isNotBlank() && t != primaryTitle && !t.equals("null", ignoreCase = true) && !altTitles.contains(t)) {
                        altTitles.add(t)
                    }
                }
            }

            val year = content.optInt("issue_year").takeIf { it > 0 }
            val countChapters = content.optDouble("count_chapters").takeIf { !it.isNaN() && it > 0 }

            val coverObj = content.optJSONObject("cover")
            val rawCover = coverObj?.optString("high")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("mid")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("low")?.takeIf { it.isNotBlank() }

            val coverUrl = when {
                rawCover.isNullOrBlank() -> null
                rawCover.startsWith("http") -> rawCover
                rawCover.startsWith("/media/") -> "$BASE_URL$rawCover"
                rawCover.startsWith("media/") -> "$BASE_URL/$rawCover"
                rawCover.startsWith("/") -> "$BASE_URL/media$rawCover"
                else -> "$BASE_URL/media/$rawCover"
            }

            val typeObj = content.optJSONObject("type")
            val typeName = typeObj?.optString("name").orEmpty().lowercase()

            val isNovel = typeName.contains("новелла") || typeName.contains("ранобэ")
            val targetType = if (isNovel) WorkType.BOOK else WorkType.MANGA

            val mangaType = when {
                isNovel -> null
                typeName.contains("манхва") -> MangaType.MANHWA
                typeName.contains("маньхуа") -> MangaType.MANHUA
                else -> MangaType.MANGA
            }

            val genres = mutableListOf<String>()
            content.optJSONArray("genres")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.optJSONObject(i)?.optString("name")?.trim()
                    if (!g.isNullOrBlank() && !genres.contains(g)) {
                        genres.add(g)
                    }
                }
            }
            val tags = mutableListOf<String>()
            content.optJSONArray("categories")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i)?.optString("name")?.trim()
                    if (!c.isNullOrBlank() && !tags.contains(c)) {
                        tags.add(c)
                    }
                }
            }

            // Parse actual author from creators (Type 1 is Author, Type 4 is Artist)
            // Translation teams in "publishers" must NOT be treated as authors
            var authorName: String? = null
            val creatorsObj = content.optJSONObject("creators")
            if (creatorsObj != null) {
                val authorsArr = creatorsObj.optJSONArray("1")
                if (authorsArr != null && authorsArr.length() > 0) {
                    val rawA = authorsArr.optJSONObject(0)?.optNonEmptyString("name")
                    authorName = ParserUtils.sanitizeAuthor(rawA)
                }
                if (authorName == null) {
                    val artistsArr = creatorsObj.optJSONArray("4")
                    if (artistsArr != null && artistsArr.length() > 0) {
                        val rawArt = artistsArr.optJSONObject(0)?.optNonEmptyString("name")
                        authorName = ParserUtils.sanitizeAuthor(rawArt)
                    }
                }
            }

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = cleanDesc,
                    type = targetType,
                    mangaType = mangaType,
                    year = year,
                    chapters = if (targetType == WorkType.MANGA) countChapters else null,
                    bookChapters = if (targetType == WorkType.BOOK) countChapters else null,
                    coverUrl = coverUrl,
                    author = authorName,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    link = "$BASE_URL/manga/$dir",
                    source = MediaSource.REMANGA
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
