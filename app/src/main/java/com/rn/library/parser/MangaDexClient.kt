package com.rn.library.parser

import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MangaDexClient {
    private const val BASE_URL = "https://api.mangadex.org"
    private const val COVER_BASE = "https://uploads.mangadex.org/covers"
    private const val USER_AGENT = "MyLibraryApp/2.7 (Android; github.com/RealNeal/MyLib)"

    fun isMangaDexUrl(url: String): Boolean {
        return url.contains("mangadex.org/title/", ignoreCase = true)
    }

    fun parseMangaDexId(url: String): String? {
        val regex = Regex("""mangadex\.org\/title\/([a-f0-9\-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(url.trim())?.groupValues?.get(1)
    }

    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE_URL/manga?title=$encoded&limit=15&includes[]=cover_art&includes[]=author"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("Пустой ответ от MangaDex"))
            val data = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val idStr = item.optString("id")
                val attr = item.optJSONObject("attributes") ?: continue

                val (enTitle, ruTitle) = extractMangaDexTitles(attr)
                val mainTitle = enTitle.ifBlank { ruTitle }
                val subTitle = if (ruTitle.isNotBlank() && ruTitle != mainTitle) ruTitle else ""

                // Cover art filename from relationships
                var coverFileName: String? = null
                val rels = item.optJSONArray("relationships")
                if (rels != null) {
                    for (r in 0 until rels.length()) {
                        val rel = rels.optJSONObject(r) ?: continue
                        if (rel.optString("type") == "cover_art") {
                            coverFileName = rel.optJSONObject("attributes")?.optString("fileName")
                            break
                        }
                    }
                }
                val coverUrl = if (!coverFileName.isNullOrBlank()) {
                    "$COVER_BASE/$idStr/$coverFileName"
                } else null

                val year = attr.optInt("year").takeIf { it > 0 }
                val lastChapter = attr.optString("lastChapter").toDoubleOrNull()?.toInt()
                    ?: attr.optInt("lastChapter").takeIf { it > 0 }
                val lastVolume = attr.optString("lastVolume").toDoubleOrNull()?.toInt()
                    ?: attr.optInt("lastVolume").takeIf { it > 0 }

                val numId = idStr.hashCode().toLong() and 0x7FFFFFFF

                list.add(
                    ShikimoriSearchResult(
                        id = numId,
                        name = mainTitle,
                        russian = subTitle,
                        kind = "manga",
                        year = year,
                        coverUrl = coverUrl,
                        episodes = null,
                        chapters = lastChapter,
                        volumes = lastVolume,
                        targetType = WorkType.MANGA,
                        endpoint = "mangadex",
                        url = "https://mangadex.org/title/$idStr",
                        source = MediaSource.MANGADEX,
                        stringId = idStr
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(mangaId: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/manga/$mangaId?includes[]=cover_art&includes[]=author"
            val json = fetchJson(url) ?: return@withContext Result.failure(Exception("MangaDex не вернул данные"))
            val data = json.optJSONObject("data") ?: return@withContext Result.failure(Exception("Данные не найдены"))
            val attr = data.optJSONObject("attributes") ?: return@withContext Result.failure(Exception("Атрибуты не найдены"))

            val (enTitle, ruTitle) = extractMangaDexTitles(attr)
            val primaryTitle = enTitle.ifBlank { ruTitle }
            val altTitles = mutableListOf<String>()
            if (ruTitle.isNotBlank() && ruTitle != primaryTitle) {
                altTitles.add(ruTitle)
            }
            attr.optJSONArray("altTitles")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    obj.keys().forEach { k ->
                        val v = obj.optString(k).trim()
                        if (v.isNotBlank() && !altTitles.contains(v) && v != primaryTitle) {
                            altTitles.add(v)
                        }
                    }
                }
            }

            // Description
            val descObj = attr.optJSONObject("description")
            val rawDesc = descObj?.optString("ru")?.takeIf { it.isNotBlank() }
                ?: descObj?.optString("en")?.takeIf { it.isNotBlank() }
                ?: ""
            val description = ParserUtils.cleanDescription(rawDesc)

            // Relationships (cover, author)
            var coverFileName: String? = null
            var authorName: String? = null
            data.optJSONArray("relationships")?.let { rels ->
                for (i in 0 until rels.length()) {
                    val rel = rels.optJSONObject(i) ?: continue
                    val type = rel.optString("type")
                    if (type == "cover_art" && coverFileName == null) {
                        coverFileName = rel.optJSONObject("attributes")?.optString("fileName")
                    } else if (type == "author" && authorName == null) {
                        val rawA = rel.optJSONObject("attributes")?.optString("name")
                        authorName = ParserUtils.sanitizeAuthor(rawA)
                    }
                }
            }

            val coverUrl = if (!coverFileName.isNullOrBlank()) {
                "$COVER_BASE/$mangaId/$coverFileName"
            } else null

            // Genres & Tags
            val genres = mutableListOf<String>()
            val tags = mutableListOf<String>()
            attr.optJSONArray("tags")?.let { tagsArr ->
                for (i in 0 until tagsArr.length()) {
                    val t = tagsArr.optJSONObject(i) ?: continue
                    val tAttr = t.optJSONObject("attributes") ?: continue
                    val nameObj = tAttr.optJSONObject("name")
                    val tagName = nameObj?.optString("ru")?.takeIf { it.isNotBlank() }
                        ?: nameObj?.optString("en")?.takeIf { it.isNotBlank() }
                    val group = tAttr.optString("group", "").lowercase()
                    if (tagName != null) {
                        if (group == "genre") {
                            genres.add(tagName)
                        } else {
                            tags.add(tagName)
                        }
                    }
                }
            }

            val year = attr.optInt("year").takeIf { it > 0 }
            var lastChapter = attr.optString("lastChapter").toDoubleOrNull()
            var lastVolume = attr.optString("lastVolume").toDoubleOrNull()

            if (lastChapter == null || lastChapter == 0.0) {
                // If lastChapter is missing from manga record, query aggregate endpoint
                try {
                    val aggJson = fetchJson("$BASE_URL/manga/$mangaId/aggregate")
                    val volumesObj = aggJson?.optJSONObject("volumes")
                    if (volumesObj != null) {
                        var maxCh = 0.0
                        var volCount = 0
                        volumesObj.keys().forEach { volKey ->
                            val vObj = volumesObj.optJSONObject(volKey)
                            val volNum = volKey.toDoubleOrNull()
                            if (volNum != null && volNum > volCount) volCount = volNum.toInt()
                            val chObj = vObj?.optJSONObject("chapters")
                            chObj?.keys()?.forEach { chKey ->
                                val chNum = chKey.toDoubleOrNull()
                                if (chNum != null && chNum > maxCh) {
                                    maxCh = chNum
                                }
                            }
                        }
                        if (maxCh > 0) lastChapter = maxCh
                        if ((lastVolume == null || lastVolume == 0.0) && volCount > 0) lastVolume = volCount.toDouble()
                    }
                } catch (_: Exception) {}
            }

            val originalLanguage = attr.optString("originalLanguage")
            val mangaType = when (originalLanguage) {
                "ko" -> MangaType.MANHWA
                "zh", "zh-hk" -> MangaType.MANHUA
                else -> MangaType.MANGA
            }

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = description,
                    type = WorkType.MANGA,
                    mangaType = mangaType,
                    year = year,
                    chapters = lastChapter,
                    volumes = lastVolume,
                    coverUrl = coverUrl,
                    author = authorName,
                    genres = ParserUtils.normalizeChips(genres),
                    tags = ParserUtils.normalizeChips(tags),
                    link = "https://mangadex.org/title/$mangaId",
                    source = MediaSource.MANGADEX
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractMangaDexTitles(attr: JSONObject): Pair<String, String> {
        val titlesObj = attr.optJSONObject("title")
        var en = titlesObj?.optString("en")?.takeIf { it.isNotBlank() }
            ?: titlesObj?.optString("ja-ro")?.takeIf { it.isNotBlank() }
            ?: titlesObj?.optString("ko-ro")?.takeIf { it.isNotBlank() }
            ?: titlesObj?.keys()?.asSequence()?.mapNotNull { k -> titlesObj.optString(k).takeIf { it.isNotBlank() } }?.firstOrNull()
            ?: ""

        var ru = titlesObj?.optString("ru")?.takeIf { it.isNotBlank() } ?: ""

        val altTitlesArr = attr.optJSONArray("altTitles")
        if (altTitlesArr != null) {
            for (i in 0 until altTitlesArr.length()) {
                val altObj = altTitlesArr.optJSONObject(i) ?: continue
                if (en.isBlank()) {
                    en = altObj.optString("en").takeIf { it.isNotBlank() }
                        ?: altObj.optString("ja-ro").takeIf { it.isNotBlank() }
                        ?: ""
                }
                if (ru.isBlank()) {
                    ru = altObj.optString("ru").takeIf { it.isNotBlank() } ?: ""
                }
                if (en.isBlank()) {
                    val anyKey = altObj.keys().asSequence().firstOrNull()
                    if (anyKey != null) {
                        val candidate = altObj.optString(anyKey).trim()
                        if (candidate.isNotBlank() && candidate != ru) {
                            en = candidate
                        }
                    }
                }
            }
        }
        if (en.isBlank() && ru.isNotBlank()) en = ru
        return Pair(en, ru)
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
