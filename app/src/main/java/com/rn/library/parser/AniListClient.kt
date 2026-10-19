package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AniListClient {
    private const val GRAPHQL_URL = "https://graphql.anilist.co"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    fun isAniListUrl(url: String): Boolean {
        return url.contains("anilist.co/anime/", ignoreCase = true) ||
                url.contains("anilist.co/manga/", ignoreCase = true)
    }

    fun parseAniListId(url: String): Pair<String, Int>? {
        val regex = Regex("""anilist\.co/(anime|manga)/(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url.trim()) ?: return null
        val type = match.groupValues[1].lowercase()
        val id = match.groupValues[2].toIntOrNull() ?: return null
        return Pair(type, id)
    }

    suspend fun search(
        query: String,
        filter: ShikimoriFilterType = ShikimoriFilterType.ALL
    ): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val mediaType = when (filter) {
                ShikimoriFilterType.ANIME -> "ANIME"
                ShikimoriFilterType.MANGA, ShikimoriFilterType.RANOBE -> "MANGA"
                ShikimoriFilterType.ALL -> null
            }

            val typeFilterStr = if (mediaType != null) ", type: $mediaType" else ""
            val gqlQuery = """
                query (${'$'}search: String) {
                  Page(page: 1, perPage: 15) {
                    media(search: ${'$'}search$typeFilterStr) {
                      id
                      type
                      format
                      title {
                        romaji
                        english
                        native
                      }
                      coverImage {
                        extraLarge
                        large
                      }
                      startDate {
                        year
                      }
                      episodes
                      chapters
                      volumes
                    }
                  }
                }
            """.trimIndent()

            val variables = JSONObject().put("search", query.trim())
            val body = JSONObject().apply {
                put("query", gqlQuery)
                put("variables", variables)
            }

            val json = postJson(GRAPHQL_URL, body.toString())
                ?: return@withContext Result.failure(Exception("Пустой ответ от AniList"))

            val data = json.optJSONObject("data")
            val page = data?.optJSONObject("Page")
            val mediaList = page?.optJSONArray("media") ?: return@withContext Result.success(emptyList())

            val list = mutableListOf<ShikimoriSearchResult>()
            for (i in 0 until mediaList.length()) {
                val item = mediaList.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val itemType = item.optString("type")
                val format = item.optString("format")

                val titleObj = item.optJSONObject("title")
                val romaji = titleObj?.optString("romaji").orEmpty()
                val english = titleObj?.optString("english").orEmpty()
                val native = titleObj?.optString("native").orEmpty()

                val mainTitle = english.ifBlank { romaji.ifBlank { native } }
                val subTitle = if (romaji.isNotBlank() && romaji != mainTitle) romaji else native

                val coverObj = item.optJSONObject("coverImage")
                val coverUrl = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() }
                    ?: coverObj?.optString("large")?.takeIf { it.isNotBlank() }

                val startObj = item.optJSONObject("startDate")
                val year = startObj?.optInt("year")?.takeIf { it > 0 }

                val isAnime = itemType.equals("ANIME", ignoreCase = true)
                val isNovel = format.equals("NOVEL", ignoreCase = true)
                val targetType = when {
                    isAnime -> WorkType.ANIME
                    isNovel -> WorkType.BOOK
                    else -> WorkType.MANGA
                }

                val eps = item.optInt("episodes").takeIf { it > 0 }
                val chs = item.optInt("chapters").takeIf { it > 0 }
                val vols = item.optInt("volumes").takeIf { it > 0 }

                val endpoint = if (isAnime) "anime" else "manga"

                list.add(
                    ShikimoriSearchResult(
                        id = id,
                        name = mainTitle,
                        russian = subTitle,
                        kind = format.lowercase(),
                        year = year,
                        coverUrl = coverUrl,
                        episodes = eps,
                        chapters = chs,
                        volumes = vols,
                        targetType = targetType,
                        endpoint = endpoint,
                        url = "https://anilist.co/$endpoint/$id",
                        source = MediaSource.ANILIST,
                        stringId = id.toString()
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(id: Int): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val gqlQuery = """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id) {
                    id
                    type
                    format
                    countryOfOrigin
                    title {
                      romaji
                      english
                      native
                    }
                    synonyms
                    coverImage {
                      extraLarge
                      large
                    }
                    startDate {
                      year
                      month
                    }
                    season
                    episodes
                    chapters
                    volumes
                    description(asHtml: false)
                    genres
                    tags {
                      name
                    }
                    staff {
                      edges {
                        role
                        node {
                          name {
                            full
                          }
                        }
                      }
                    }
                    siteUrl
                  }
                }
            """.trimIndent()

            val variables = JSONObject().put("id", id)
            val body = JSONObject().apply {
                put("query", gqlQuery)
                put("variables", variables)
            }

            val json = postJson(GRAPHQL_URL, body.toString())
                ?: return@withContext Result.failure(Exception("AniList не вернул данные"))

            val media = json.optJSONObject("data")?.optJSONObject("Media")
                ?: return@withContext Result.failure(Exception("Медиа не найдено на AniList"))

            val itemType = media.optString("type")
            val format = media.optString("format")
            val country = media.optString("countryOfOrigin")

            val titleObj = media.optJSONObject("title")
            val romaji = titleObj?.optString("romaji").orEmpty()
            val english = titleObj?.optString("english").orEmpty()
            val native = titleObj?.optString("native").orEmpty()

            val primaryTitle = english.ifBlank { romaji.ifBlank { native } }

            val altTitles = mutableListOf<String>()
            if (romaji.isNotBlank() && romaji != primaryTitle) altTitles.add(romaji)
            if (native.isNotBlank() && native != primaryTitle) altTitles.add(native)
            media.optJSONArray("synonyms")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotBlank() && !altTitles.contains(s) && s != primaryTitle) {
                        altTitles.add(s)
                    }
                }
            }

            val coverObj = media.optJSONObject("coverImage")
            val coverUrl = coverObj?.optString("extraLarge")?.takeIf { it.isNotBlank() }
                ?: coverObj?.optString("large")?.takeIf { it.isNotBlank() }

            val rawDesc = media.optString("description").orEmpty()
            val description = cleanAniListText(rawDesc)

            val startObj = media.optJSONObject("startDate")
            val year = startObj?.optInt("year")?.takeIf { it > 0 }

            val seasonStr = media.optString("season")
            val animeSeason = when (seasonStr.uppercase()) {
                "WINTER" -> AnimeSeason.WINTER
                "SPRING" -> AnimeSeason.SPRING
                "SUMMER" -> AnimeSeason.SUMMER
                "FALL" -> AnimeSeason.FALL
                else -> null
            }

            val isAnime = itemType.equals("ANIME", ignoreCase = true)
            val isNovel = format.equals("NOVEL", ignoreCase = true)
            val targetType = when {
                isAnime -> WorkType.ANIME
                isNovel -> WorkType.BOOK
                else -> WorkType.MANGA
            }

            val mangaType = when {
                !isAnime && country.equals("KR", ignoreCase = true) -> MangaType.MANHWA
                !isAnime && (country.equals("CN", ignoreCase = true) || country.equals("HK", ignoreCase = true) || country.equals("TW", ignoreCase = true)) -> MangaType.MANHUA
                !isAnime && !isNovel -> MangaType.MANGA
                else -> null
            }

            val episodes = media.optDouble("episodes").takeIf { !it.isNaN() && it > 0 }
            val chapters = media.optDouble("chapters").takeIf { !it.isNaN() && it > 0 }
            val volumes = media.optDouble("volumes").takeIf { !it.isNaN() && it > 0 }

            val genres = mutableListOf<String>()
            media.optJSONArray("genres")?.let { gArr ->
                for (i in 0 until gArr.length()) {
                    val g = gArr.optString(i).trim()
                    if (g.isNotBlank()) genres.add(g)
                }
            }

            val tags = mutableListOf<String>()
            media.optJSONArray("tags")?.let { tArr ->
                for (i in 0 until tArr.length()) {
                    val t = tArr.optJSONObject(i)?.optString("name")?.trim().orEmpty()
                    if (t.isNotBlank()) tags.add(t)
                }
            }

            // Author from staff
            var authorName: String? = null
            media.optJSONObject("staff")?.optJSONArray("edges")?.let { edges ->
                for (i in 0 until edges.length()) {
                    val edge = edges.optJSONObject(i) ?: continue
                    val role = edge.optString("role")
                    if (role.contains("Original Creator", ignoreCase = true) ||
                        role.contains("Story", ignoreCase = true) ||
                        role.contains("Author", ignoreCase = true)
                    ) {
                        authorName = ParserUtils.sanitizeAuthor(edge.optJSONObject("node")?.optJSONObject("name")?.optString("full"))
                        break
                    }
                }
            }

            val siteUrl = media.optString("siteUrl").takeIf { it.isNotBlank() }
                ?: "https://anilist.co/${if (isAnime) "anime" else "manga"}/$id"

            Result.success(
                ParsedWorkMetadata(
                    title = primaryTitle,
                    alternativeTitles = altTitles.distinct(),
                    description = description,
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
                    link = siteUrl,
                    source = MediaSource.ANILIST
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cleanAniListText(raw: String): String {
        // Remove AniList markdown spoiler/img/links first
        val withoutSpoilers = raw.replace(Regex("""~!.*?(!~)""", RegexOption.DOT_MATCHES_ALL)) { "" }
        return ParserUtils.cleanDescription(withoutSpoilers)
    }

    private fun postJson(urlString: String, jsonBody: String): JSONObject? {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Origin", "https://anilist.co")
            conn.setRequestProperty("Referer", "https://anilist.co/")
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.doOutput = true

            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode !in 200..299) return null
            val respText = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(respText)
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
