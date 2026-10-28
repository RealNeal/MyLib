package com.rn.library.parser

import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Парсер произведений с сайта ScribbleHub (scribblehub.com).
 *
 * Использует официальный WordPress REST API мобильного приложения (/wp-json/fictionapp/v1/),
 * который работает напрямую без блокировок Cloudflare Turnstile, в сочетании с интеллектуальной
 * генерацией вариаций поисковых запросов и нечётким ранжированием результатов.
 */
object ScribbleHubClient {

    private const val DEFAULT_BASE_URL = "https://www.scribblehub.com"
    private const val API_PATH = "/wp-json/fictionapp/v1"
    private const val USER_AGENT = "Dart/3.0 (dart:io)"
    private const val MAX_PAGES_TO_FETCH = 5
    private const val STORIES_PER_PAGE = 50

    private fun getBaseUrl(): String {
        val domain = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_SCRIBBLEHUB)
        return if (domain.isNotBlank()) domain.trimEnd('/') else DEFAULT_BASE_URL
    }

    fun isScribbleHubUrl(url: String): Boolean {
        val lower = url.trim().lowercase(Locale.ROOT)
        return lower.contains("scribblehub.com/series/") || lower.contains("scribblehub.com/read/")
    }

    fun parseScribbleHubSeriesId(urlOrId: String): Long? {
        val trimmed = urlOrId.trim()
        val regex = Regex("""scribblehub\.com/(?:series|read)/(\d+)""", RegexOption.IGNORE_CASE)
        regex.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return trimmed.toLongOrNull()
    }

    /**
     * Поиск произведений на ScribbleHub с пагинацией по всей базе данных
     * и ранжированием по соответствию исходному названию.
     */
    suspend fun search(query: String): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) {
                return@withContext Result.success(emptyList())
            }

            // 1. Если передана прямая ссылка или числовой ID серии
            if (isScribbleHubUrl(cleanQuery) || cleanQuery.matches(Regex("""^\d{3,9}$"""))) {
                val directId = parseScribbleHubSeriesId(cleanQuery)
                if (directId != null) {
                    val details = fetchDetails(directId.toString())
                    if (details.isSuccess) {
                        val meta = details.getOrThrow()
                        val singleResult = ShikimoriSearchResult(
                            id = directId,
                            name = meta.title,
                            russian = "",
                            kind = "novel",
                            year = meta.year,
                            coverUrl = meta.coverUrl,
                            episodes = null,
                            chapters = meta.bookChapters?.toInt() ?: meta.chapters?.toInt(),
                            volumes = meta.volumes?.toInt(),
                            targetType = WorkType.BOOK,
                            endpoint = "series",
                            url = meta.link ?: "$DEFAULT_BASE_URL/series/$directId/",
                            source = MediaSource.SCRIBBLEHUB,
                            stringId = directId.toString()
                        )
                        return@withContext Result.success(listOf(singleResult))
                    }
                }
            }

            val collectedResults = ConcurrentHashMap<Long, ShikimoriSearchResult>()
            val collectedAuthors = ConcurrentHashMap<Long, String>()

            // 2. Варианты запроса (исходный + нормализация пунктуации + транслит/перевод при кириллице + сегменты)
            val searchVariations = generateSearchVariations(cleanQuery)
            val primaryQuery = searchVariations.firstOrNull() ?: cleanQuery

            // 3. Основной опрос: первая страница /stories (до 50 результатов)
            //    и параллельный запрос /search для мгновенных совпадений и авторов
            val searchApiDeferred = async { querySearchApi(primaryQuery) }
            val firstPageDeferred = async { queryStoriesPage(primaryQuery, page = 1, perPage = STORIES_PER_PAGE) }

            val searchApiResp = searchApiDeferred.await()
            val firstPageResp = firstPageDeferred.await()

            for (story in searchApiResp.stories) {
                collectedResults.putIfAbsent(story.id, story)
            }
            for (author in searchApiResp.authors) {
                collectedAuthors.putIfAbsent(author.first, author.second)
            }
            if (collectedAuthors.isEmpty()) {
                val usersFound = queryUsersSearchApi(primaryQuery)
                for (author in usersFound) {
                    collectedAuthors.putIfAbsent(author.first, author.second)
                }
            }
            for (story in firstPageResp.stories) {
                collectedResults.putIfAbsent(story.id, story)
            }

            // 4. Пагинация: если доступно больше 1 страницы, параллельно выгружаем страницы
            if (firstPageResp.totalPages > 1) {
                val pagesToFetch = (2..minOf(firstPageResp.totalPages, MAX_PAGES_TO_FETCH)).toList()
                val additionalPages = pagesToFetch.map { p ->
                    async { queryStoriesPage(primaryQuery, page = p, perPage = STORIES_PER_PAGE) }
                }.awaitAll()

                for (pageResp in additionalPages) {
                    for (story in pageResp.stories) {
                        collectedResults.putIfAbsent(story.id, story)
                    }
                }
            }

            // 5. Опрашиваем вариации, если нет строгого совпадения или результатов мало
            val secondaryVariations = searchVariations.drop(1).take(5)
            if ((collectedResults.size < 25 || !hasStrongMatch(cleanQuery, collectedResults.values)) && secondaryVariations.isNotEmpty()) {
                val variationDeferred = secondaryVariations.map { variation ->
                    async {
                        val extraList = mutableListOf<ShikimoriSearchResult>()
                        val sApi = querySearchApi(variation)
                        extraList.addAll(sApi.stories)
                        for (author in sApi.authors) {
                            collectedAuthors.putIfAbsent(author.first, author.second)
                        }

                        val p1 = queryStoriesPage(variation, page = 1, perPage = STORIES_PER_PAGE)
                        extraList.addAll(p1.stories)
                        if (p1.totalPages > 1) {
                            val extraPages = (2..minOf(p1.totalPages, 3)).map { p ->
                                async { queryStoriesPage(variation, page = p, perPage = STORIES_PER_PAGE) }
                            }.awaitAll()
                            extraPages.forEach { extraList.addAll(it.stories) }
                        }
                        extraList
                    }
                }
                variationDeferred.awaitAll().flatten().forEach { story ->
                    collectedResults.putIfAbsent(story.id, story)
                }
            }

            // 6. Если найдено мало результатов, но нашлись авторы, догружаем их истории
            if (collectedResults.size < 5 && collectedAuthors.isNotEmpty()) {
                val authorStoriesDeferred = collectedAuthors.keys.take(5).map { authorId ->
                    async {
                        fetchStoriesByAuthor(authorId)
                    }
                }
                authorStoriesDeferred.awaitAll().flatten().forEach { story ->
                    collectedResults.putIfAbsent(story.id, story)
                }
            }

            // 7. Поиск авторов через кросс-резолвинг с коротким таймаутом (когда нет сильного совпадения в публичном API)
            if (!hasStrongMatch(cleanQuery, collectedResults.values)) {
                withTimeoutOrNull(2500L) {
                    try {
                        val crossAuthors = resolveAuthorsFromCrossSources(cleanQuery)
                        for (authorName in crossAuthors) {
                            val usersFound = queryUsersSearchApi(authorName)
                            for ((uId, uDisplayName) in usersFound.take(3)) {
                                collectedAuthors.putIfAbsent(uId, uDisplayName)
                                val stories = fetchStoriesByAuthor(uId)
                                for (story in stories) {
                                    collectedResults.putIfAbsent(story.id, story)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (collectedResults.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 8. Ранжирование по степени совпадения (возвращаются абсолютно все найденные произведения)
            val ranked = rankSearchResults(cleanQuery, collectedResults.values.toList())
            Result.success(ranked)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Загрузка подробных метаданных произведения по ссылке или ID серии.
     */
    suspend fun fetchDetails(urlOrId: String): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val seriesId = parseScribbleHubSeriesId(urlOrId)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid ScribbleHub series ID or URL: $urlOrId"))

            // 1. Пробуем получить данные через JSON API
            val apiMeta = fetchDetailsViaApi(seriesId)
            if (apiMeta != null) {
                return@withContext Result.success(apiMeta)
            }

            // 2. Если API вернул ошибку, пробуем через HTML с обходом Cloudflare
            val webUrl = "$DEFAULT_BASE_URL/series/$seriesId/"
            val html = CloudflareBypasser.fetchHtmlWithFallback(webUrl, DEFAULT_BASE_URL, 15000L)
            if (html != null) {
                val htmlMeta = parseMetadataFromHtml(html, seriesId, webUrl)
                if (htmlMeta != null) {
                    return@withContext Result.success(htmlMeta)
                }
            }

            Result.failure(NoSuchElementException("Failed to fetch ScribbleHub details for series $seriesId"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // API Networking
    // ---------------------------------------------------------------------------------------------

    private data class StoriesPageResponse(
        val stories: List<ShikimoriSearchResult>,
        val page: Int,
        val totalPages: Int,
        val total: Int
    )

    private fun queryStoriesPage(
        queryText: String,
        page: Int = 1,
        perPage: Int = STORIES_PER_PAGE
    ): StoriesPageResponse {
        val trimmed = queryText.trim()
        if (trimmed.isBlank()) return StoriesPageResponse(emptyList(), page, 0, 0)

        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val baseUrl = getBaseUrl()
            val url = URL("$baseUrl$API_PATH/stories?search=$encoded&perPage=$perPage&page=$page")
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "$DEFAULT_BASE_URL/")
            conn.setRequestProperty("Cookie", "mature=1; sh_mature=1; birthdate=1990-01-01; adult=1")
            conn.connectTimeout = 8000
            conn.readTimeout = 12000

            if (conn.responseCode !in 200..299) {
                return StoriesPageResponse(emptyList(), page, 0, 0)
            }

            val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonText)
            val dataArr = root.optJSONArray("data") ?: JSONArray()
            val stories = parseStoriesJsonArray(dataArr)
            val meta = root.optJSONObject("meta")
            val totalPages = meta?.optInt("totalPages", 1) ?: 1
            val total = meta?.optInt("total", stories.size) ?: stories.size
            return StoriesPageResponse(stories, page, totalPages, total)
        } catch (_: Exception) {
            return StoriesPageResponse(emptyList(), page, 0, 0)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private data class SearchApiResponse(
        val stories: List<ShikimoriSearchResult>,
        val authors: List<Pair<Long, String>>
    )

    private fun querySearchApi(queryText: String): SearchApiResponse {
        val trimmed = queryText.trim()
        if (trimmed.isBlank()) return SearchApiResponse(emptyList(), emptyList())

        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val baseUrl = getBaseUrl()
            val url = URL("$baseUrl$API_PATH/search?q=$encoded")
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "$DEFAULT_BASE_URL/")
            conn.setRequestProperty("Cookie", "mature=1; sh_mature=1; birthdate=1990-01-01; adult=1")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000

            if (conn.responseCode !in 200..299) {
                return SearchApiResponse(emptyList(), emptyList())
            }

            val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseSearchResponse(jsonText)
        } catch (_: Exception) {
            return SearchApiResponse(emptyList(), emptyList())
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun queryUsersSearchApi(queryText: String): List<Pair<Long, String>> {
        val trimmed = queryText.trim()
        if (trimmed.isBlank()) return emptyList()

        var conn: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val baseUrl = getBaseUrl()
            val url = URL("$baseUrl$API_PATH/users/search?q=$encoded")
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "$DEFAULT_BASE_URL/")
            conn.setRequestProperty("Cookie", "mature=1; sh_mature=1; birthdate=1990-01-01; adult=1")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000

            if (conn.responseCode !in 200..299) return emptyList()

            val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonText)
            val data = root.optJSONObject("data") ?: return emptyList()
            val usersArr = data.optJSONArray("users") ?: return emptyList()
            val list = mutableListOf<Pair<Long, String>>()
            for (i in 0 until usersArr.length()) {
                val uObj = usersArr.optJSONObject(i) ?: continue
                val uId = uObj.optLong("id", 0L)
                val uName = uObj.optString("displayName").ifBlank { uObj.optString("username") }
                if (uId > 0 && uName.isNotBlank()) {
                    list.add(uId to uName)
                }
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun fetchStoriesByAuthor(authorId: Long): List<ShikimoriSearchResult> {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = getBaseUrl()
            val url = URL("$baseUrl$API_PATH/users/$authorId/stories")
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "$DEFAULT_BASE_URL/")
            conn.setRequestProperty("Cookie", "mature=1; sh_mature=1; birthdate=1990-01-01; adult=1")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000

            if (conn.responseCode !in 200..299) return emptyList()

            val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonText)
            val storiesArr = root.optJSONArray("data")
                ?: root.optJSONObject("data")?.optJSONArray("stories")
                ?: return emptyList()
            return parseStoriesJsonArray(storiesArr)
        } catch (_: Exception) {
            return emptyList()
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun fetchDetailsViaApi(seriesId: Long): ParsedWorkMetadata? {
        var conn: HttpURLConnection? = null
        try {
            val baseUrl = getBaseUrl()
            val url = URL("$baseUrl$API_PATH/stories/$seriesId")
            conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "$DEFAULT_BASE_URL/")
            conn.setRequestProperty("Cookie", "mature=1; sh_mature=1; birthdate=1990-01-01; adult=1")
            conn.connectTimeout = 8000
            conn.readTimeout = 10000

            if (conn.responseCode !in 200..299) return null

            val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return parseMetadataFromJson(jsonText, seriesId)
        } catch (_: Exception) {
            return null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun hasStrongMatch(query: String, items: Collection<ShikimoriSearchResult>): Boolean {
        val qClean = query.replace(Regex("""[^\p{L}\p{Nd}]"""), "").lowercase(Locale.ROOT)
        if (qClean.isBlank()) return false
        return items.any { item ->
            val tClean = item.name.replace(Regex("""[^\p{L}\p{Nd}]"""), "").lowercase(Locale.ROOT)
            tClean == qClean || tClean.contains(qClean) || qClean.contains(tClean)
        }
    }

    /**
     * Поиск авторов новелл через открытые зеркала/каталоги (Ranobes, Royal Road),
     * на которых 18+ новеллы со ScribbleHub не скрываются за закрытыми API-фильтрами.
     * Найденные авторы затем используются для выгрузки оригинальных карточек произведений со ScribbleHub.
     */
    private suspend fun resolveAuthorsFromCrossSources(query: String): Set<String> = withContext(Dispatchers.IO) {
        val cleanQuery = query.replace(Regex("""[.,!?;:]"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (cleanQuery.isBlank()) return@withContext emptySet()

        val authors = ConcurrentHashMap.newKeySet<String>()
        val queryTokens = cleanQuery.lowercase(Locale.ROOT)
            .split(Regex("""\s+"""))
            .map { it.replace(Regex("""[^\p{L}\p{Nd}]"""), "") }
            .filter { it.length >= 2 }
            .toSet()

        fun isRelevantTitle(title: String): Boolean {
            val tLower = title.lowercase(Locale.ROOT)
            val cleanLower = cleanQuery.lowercase(Locale.ROOT)
            if (tLower.contains(cleanLower) || cleanLower.contains(tLower)) return true
            val tTokens = tLower.split(Regex("""\s+"""))
                .map { it.replace(Regex("""[^\p{L}\p{Nd}]"""), "") }
                .filter { it.length >= 2 }
                .toSet()
            if (queryTokens.isEmpty() || tTokens.isEmpty()) return false
            val overlap = queryTokens.intersect(tTokens).size
            return overlap >= 2 || (queryTokens.size == 1 && overlap == 1)
        }

        val ranobesDeferred = async {
            try {
                val searchRes = RanobesClient.search(cleanQuery).getOrNull().orEmpty()
                val relevant = searchRes.filter { isRelevantTitle(it.name) }.take(2)
                for (item in relevant) {
                    val details = RanobesClient.fetchDetails(item.url).getOrNull()
                    val a = details?.author?.trim()
                    if (!a.isNullOrBlank()) {
                        authors.add(a)
                    }
                }
            } catch (_: Exception) {}
        }

        val royalRoadDeferred = async {
            try {
                val searchRes = RoyalRoadClient.search(cleanQuery).getOrNull().orEmpty()
                val relevant = searchRes.filter { isRelevantTitle(it.name) }.take(2)
                for (item in relevant) {
                    val details = RoyalRoadClient.fetchDetails(item.id).getOrNull()
                    val a = details?.author?.trim()
                    if (!a.isNullOrBlank()) {
                        authors.add(a)
                    }
                }
            } catch (_: Exception) {}
        }

        ranobesDeferred.await()
        royalRoadDeferred.await()

        authors
    }

    private suspend fun searchHtmlFallback(queryText: String): String? {
        return try {
            val clean = queryText.replace(Regex("""[.,!?;:]"""), " ").replace(Regex("""\s+"""), " ").trim()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val searchUrl = "$DEFAULT_BASE_URL/?s=$encoded&post_type=fictionposts"
            CloudflareBypasser.fetchHtmlWithFallback(searchUrl, DEFAULT_BASE_URL, 14000L)
        } catch (_: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------------------------------------
    // JSON Parsing
    // ---------------------------------------------------------------------------------------------

    private fun parseSearchResponse(jsonText: String): SearchApiResponse {
        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return SearchApiResponse(emptyList(), emptyList())
        }
        val data = root.optJSONObject("data") ?: return SearchApiResponse(emptyList(), emptyList())

        val storiesList = data.optJSONArray("stories")?.let { parseStoriesJsonArray(it) }.orEmpty()

        val authorsList = mutableListOf<Pair<Long, String>>()
        val authorsArr = data.optJSONArray("authors")
        if (authorsArr != null) {
            for (i in 0 until authorsArr.length()) {
                val aObj = authorsArr.optJSONObject(i) ?: continue
                val aId = aObj.optLong("id", 0L)
                val aName = aObj.optString("displayName").ifBlank { aObj.optString("username") }
                if (aId > 0 && aName.isNotBlank()) {
                    authorsList.add(aId to aName)
                }
            }
        }

        return SearchApiResponse(storiesList, authorsList)
    }

    private fun parseStoriesJsonArray(stories: JSONArray): List<ShikimoriSearchResult> {
        val list = mutableListOf<ShikimoriSearchResult>()
        for (i in 0 until stories.length()) {
            val item = stories.optJSONObject(i) ?: continue
            val id = item.optLong("id", 0L)
            if (id == 0L) continue

            val title = ParserUtils.cleanText(item.optString("title"))
            if (title.isBlank()) continue

            val slug = item.optString("slug", "")
            val coverRaw = item.optString("coverUrl").takeIf {
                it.isNotBlank() && !it.contains("noimage", ignoreCase = true)
            }
            val cover = coverRaw?.let { ParserUtils.upgradeCoverUrl(it) }

            val chapters = item.optInt("chapterCount", 0).takeIf { it > 0 }
            val lastUpdated = item.optString("lastUpdated", "")
            val year = Regex("""^(\d{4})""").find(lastUpdated)?.groupValues?.get(1)?.toIntOrNull()

            val cleanSlug = if (slug.startsWith("$id-")) slug.removePrefix("$id-") else slug
            val webUrl = if (cleanSlug.isNotBlank()) {
                "$DEFAULT_BASE_URL/series/$id/$cleanSlug/"
            } else {
                "$DEFAULT_BASE_URL/series/$id/"
            }

            list.add(
                ShikimoriSearchResult(
                    id = id,
                    name = title,
                    russian = "",
                    kind = "novel",
                    year = year,
                    coverUrl = cover,
                    episodes = null,
                    chapters = chapters,
                    volumes = null,
                    targetType = WorkType.BOOK,
                    endpoint = "series",
                    url = webUrl,
                    source = MediaSource.SCRIBBLEHUB,
                    stringId = id.toString()
                )
            )
        }
        return list
    }

    private fun parseMetadataFromJson(jsonText: String, seriesId: Long): ParsedWorkMetadata? {
        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return null
        }
        val data = root.optJSONObject("data") ?: root

        val title = ParserUtils.cleanText(data.optString("title"))
        if (title.isBlank()) return null

        val slug = data.optString("slug", "")
        val rawDesc = data.optString("description", "")
        val desc = ParserUtils.cleanDescription(rawDesc)

        val coverRaw = data.optString("coverUrl").takeIf {
            it.isNotBlank() && !it.contains("noimage", ignoreCase = true)
        }
        val coverUrl = coverRaw?.let { ParserUtils.upgradeCoverUrl(it) }

        val authorObj = data.optJSONObject("author")
        val authorName = authorObj?.optString("displayName")?.ifBlank { null }
            ?: authorObj?.optString("username")?.ifBlank { null }
            ?: data.optString("author", "")
        val author = ParserUtils.sanitizeAuthor(authorName)

        val chapterCount = data.optDouble("chapterCount", 0.0).takeIf { it > 0.0 }

        val genres = mutableListOf<String>()
        val mainGenre = data.optJSONObject("mainGenre")?.optString("name")
        if (!mainGenre.isNullOrBlank()) {
            genres.add(ParserUtils.cleanText(mainGenre))
        }
        val genresArr = data.optJSONArray("genres")
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                val gObj = genresArr.optJSONObject(i) ?: continue
                val gName = ParserUtils.cleanText(gObj.optString("name"))
                if (gName.isNotBlank() && !genres.contains(gName)) {
                    genres.add(gName)
                }
            }
        }

        // Tags
        val tags = mutableListOf<String>()
        val tagsArr = data.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val tObj = tagsArr.optJSONObject(i) ?: continue
                val tName = ParserUtils.cleanText(tObj.optString("name"))
                if (tName.isNotBlank() && !tags.contains(tName)) {
                    tags.add(tName)
                }
            }
        }

        // Year from lastUpdated (e.g. 2024-05-10T10:20:36+00:00)
        val lastUpdated = data.optString("lastUpdated", "")
        val year = Regex("""^(\d{4})""").find(lastUpdated)?.groupValues?.get(1)?.toIntOrNull()

        val cleanSlug = if (slug.startsWith("$seriesId-")) slug.removePrefix("$seriesId-") else slug
        val link = if (cleanSlug.isNotBlank()) {
            "$DEFAULT_BASE_URL/series/$seriesId/$cleanSlug/"
        } else {
            "$DEFAULT_BASE_URL/series/$seriesId/"
        }

        return ParsedWorkMetadata(
            title = title,
            alternativeTitles = emptyList(),
            description = desc,
            type = WorkType.BOOK,
            year = year,
            bookChapters = chapterCount,
            coverUrl = coverUrl,
            author = author,
            genres = ParserUtils.normalizeChips(genres),
            tags = ParserUtils.normalizeChips(tags),
            link = link,
            source = MediaSource.SCRIBBLEHUB
        )
    }

    private fun parseMetadataFromHtml(html: String, seriesId: Long, webUrl: String): ParsedWorkMetadata? {
        val titleMatch = Regex("""<div[^>]*class="fic_title"[^>]*>(.*?)</div>""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<h1[^>]*class="fic_title"[^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<meta[^>]*property="og:title"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<title>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)

        val rawTitle = titleMatch?.groupValues?.get(1)
            ?.replace(Regex("""<[^>]*>"""), "")
            ?.replace(Regex("""\s*\|\s*Scribble\s*Hub.*$""", RegexOption.IGNORE_CASE), "")
        val title = ParserUtils.cleanText(rawTitle)
        if (title.isBlank()) return null

        val descMatch = Regex("""<div[^>]*class="wi_fic_desc"[^>]*>(.*?)</div>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: Regex("""<meta[^>]*property="og:description"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<meta[^>]*name="description"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
        val desc = descMatch?.groupValues?.get(1)?.let { ParserUtils.cleanDescription(it) }.orEmpty()

        val coverMatch = Regex("""<div[^>]*class="fic_image"[^>]*>.*?<img[^>]*src="([^"]+)"""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?: Regex("""<meta[^>]*property="og:image"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<link[^>]*rel="image_src"[^>]*href="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
        val rawCover = coverMatch?.groupValues?.get(1)?.takeIf { !it.contains("noimage", ignoreCase = true) }
        val cover = rawCover?.let { ParserUtils.upgradeCoverUrl(it) }

        val authorMatch = Regex("""<span[^>]*class="auth_name_fic"[^>]*>(.*?)</span>""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<meta[^>]*name="author"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<meta[^>]*property="books:author"[^>]*content="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
            ?: Regex("""<a[^>]*href="[^"]*/profile/\d+/[^"]*"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).find(html)
        val author = authorMatch?.groupValues?.get(1)?.replace(Regex("""<[^>]*>"""), "")?.let { ParserUtils.sanitizeAuthor(it) }

        val chMatch = Regex("""(\d+)\s*(?:Chapters|ch\b)""", RegexOption.IGNORE_CASE).find(html)
        val chapters = chMatch?.groupValues?.get(1)?.toDoubleOrNull()

        val genres = mutableListOf<String>()
        val genreMatches = Regex("""<a[^>]*class="fic_genre"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).findAll(html)
        for (m in genreMatches) {
            val g = ParserUtils.cleanText(m.groupValues[1])
            if (g.isNotBlank() && !genres.contains(g)) genres.add(g)
        }

        val tags = mutableListOf<String>()
        val tagMatches = Regex("""<a[^>]*class=['"][^'"]*(?:stag|fic_tags)[^'"]*['"][^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE).findAll(html)
        for (m in tagMatches) {
            val t = ParserUtils.cleanText(m.groupValues[1])
            if (t.isNotBlank() && !tags.contains(t)) tags.add(t)
        }

        return ParsedWorkMetadata(
            title = title,
            alternativeTitles = emptyList(),
            description = desc,
            type = WorkType.BOOK,
            year = null,
            bookChapters = chapters,
            coverUrl = cover,
            author = author,
            genres = ParserUtils.normalizeChips(genres),
            tags = ParserUtils.normalizeChips(tags),
            link = webUrl,
            source = MediaSource.SCRIBBLEHUB
        )
    }

    private fun parseHtmlSearchResults(html: String): List<ShikimoriSearchResult> {
        val list = mutableListOf<ShikimoriSearchResult>()
        val titleRegex = Regex(
            """<div[^>]*class="search_title"[^>]*>\s*<a[^>]*href="([^"]*(?:series|read)/(\d+)[^"]*)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val imgRegex = Regex(
            """<div[^>]*class="search_img"[^>]*>.*?<img[^>]*(?:src|data-src)="([^"]+)"""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val chaptersRegex = Regex("""(\d+)\s*(?:Chapters|ch\b)""", RegexOption.IGNORE_CASE)

        val blocks = html.split(Regex("""<div[^>]*class="search_main_box[^"]*"[^>]*>""", RegexOption.IGNORE_CASE)).drop(1)
        if (blocks.isNotEmpty()) {
            for (box in blocks) {
                val match = titleRegex.find(box) ?: continue
                val fullUrl = match.groupValues[1]
                val seriesId = match.groupValues[2].toLongOrNull() ?: continue
                val rawTitle = match.groupValues[3]
                val title = ParserUtils.cleanText(rawTitle.replace(Regex("""<[^>]*>"""), "").trim())
                if (title.isBlank()) continue

                val cover = imgRegex.find(box)?.groupValues?.get(1)
                    ?.takeIf { !it.contains("noimage", ignoreCase = true) }
                    ?.let { ParserUtils.upgradeCoverUrl(it) }

                val chapters = chaptersRegex.find(box)?.groupValues?.get(1)?.toIntOrNull()

                val webUrl = if (fullUrl.startsWith("http")) fullUrl else "$DEFAULT_BASE_URL$fullUrl"
                list.add(
                    ShikimoriSearchResult(
                        id = seriesId,
                        name = title,
                        russian = "",
                        kind = "novel",
                        year = null,
                        coverUrl = cover,
                        episodes = null,
                        chapters = chapters,
                        volumes = null,
                        targetType = WorkType.BOOK,
                        endpoint = "series",
                        url = webUrl,
                        source = MediaSource.SCRIBBLEHUB,
                        stringId = seriesId.toString()
                    )
                )
            }
        }
        return list
    }

    // ---------------------------------------------------------------------------------------------
    // Intelligent Query Generation & Ranking
    // ---------------------------------------------------------------------------------------------

    /**
     * Генерирует набор поисковых запросов для ScribbleHub API, обходящих ограничения
     * строгого поиска по подстроке (LIKE '%q%').
     */
    fun generateSearchVariations(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        fun addCandidate(c: String) {
            val candidate = c.trim().replace(Regex("""\s+"""), " ")
            if (candidate.length >= 2 && !results.any { it.equals(candidate, ignoreCase = true) }) {
                results.add(candidate)
            }
        }

        // 1. Исходный запрос
        addCandidate(trimmed)

        // Нормализация «умной» типографики и кавычек (особенно актуально для Android клавиатур)
        val normQuotes = trimmed
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('“', '"')
            .replace('”', '"')
            .replace('«', '"')
            .replace('»', '"')
            .replace('–', '-')
            .replace('—', '-')
        if (normQuotes != trimmed) {
            addCandidate(normQuotes)
        }

        // Вариант без апострофов и кавычек (например: "Omniscient Reader's Viewpoint" -> "Omniscient Reader Viewpoint")
        val noApostrophes = normQuotes.replace("'", " ").replace("\"", " ").replace(Regex("""\s+"""), " ").trim()
        if (noApostrophes != normQuotes && noApostrophes.isNotBlank()) {
            addCandidate(noApostrophes)
        }

        // Вариант без знаков препинания и точек/запятых (например: "Heaven, Earth, Me" -> "Heaven Earth Me", "Cosmos." -> "Cosmos")
        val cleanPunctuation = noApostrophes.replace(Regex("""[.,!?;:]"""), " ").replace(Regex("""\s+"""), " ").trim()
        if (cleanPunctuation.isNotBlank() && cleanPunctuation != trimmed && cleanPunctuation != noApostrophes) {
            addCandidate(cleanPunctuation)
        }

        // 2. Если запрос на кириллице — переводим и транслитерируем
        val hasCyrillic = trimmed.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        if (hasCyrillic) {
            val translated = translateCyrillicNovelQuery(trimmed)
            if (translated.isNotBlank() && translated != trimmed) {
                addCandidate(translated)
            }
            val transliterated = transliterateCyrillic(trimmed)
            if (transliterated.isNotBlank() && transliterated != trimmed) {
                addCandidate(transliterated)
            }
        }

        // Базовый рабочий запрос (на английском)
        val baseQuery = if (hasCyrillic && results.size > 1) results[1] else cleanPunctuation

        // 3. Разбиение по основным структурным разделителям: ':', '-', '–', '—', '(', '[', '|', '/'
        // Пример: "One Piece : Kingdom Building System" -> ["Kingdom Building System", "One Piece"]
        val majorSegments = baseQuery.split(Regex("""[:\-–—|/()\[\]]"""))
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.length >= 3 }
        // Приоритет отдаем более длинным сегментам
        majorSegments.sortedByDescending { it.length }.forEach { segment ->
            addCandidate(segment)
        }

        // 4. Фильтрация стоп-слов (например "Lord of the Mysteries" -> "Lord Mysteries")
        val stopWords = setOf("the", "a", "an", "of", "in", "and", "is", "at", "by", "for", "to", "on", "with", "as", "into", "that", "from")
        val wordsWithoutStops = cleanPunctuation.split(Regex("""\s+"""))
            .filter { it.lowercase(Locale.ROOT) !in stopWords && it.length >= 2 }
        if (wordsWithoutStops.size >= 2 && wordsWithoutStops.size < cleanPunctuation.split(Regex("""\s+""")).size) {
            addCandidate(wordsWithoutStops.joinToString(" "))
        }

        // 5. Комбинации слов для длинных запросов (помогает при частичном совпадении)
        val words = (if (wordsWithoutStops.isNotEmpty()) wordsWithoutStops else cleanPunctuation.split(Regex("""\s+"""))).filter { it.length >= 3 }
        if (words.size >= 3) {
            addCandidate(words.take(3).joinToString(" "))
            addCandidate(words.take(2).joinToString(" "))
        } else if (words.size == 2) {
            addCandidate(words[0])
            addCandidate(words[1])
        }

        return results
    }

    /**
     * Сортировка найденных результатов по степени соответствия оригинальному запросу пользователя.
     */
    private fun rankSearchResults(
        query: String,
        items: List<ShikimoriSearchResult>
    ): List<ShikimoriSearchResult> {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)
        val queryWords = cleanQuery.split(Regex("""\s+""")).filter { it.length >= 2 }.toSet()

        return items.sortedByDescending { item ->
            var score = 0.0
            val titleLower = item.name.lowercase(Locale.ROOT)
            val titleWords = titleLower.split(Regex("""\s+""")).filter { it.length >= 2 }.toSet()

            val tClean = titleLower.replace(Regex("""[^\p{L}\p{Nd}]"""), "")
            val qClean = cleanQuery.replace(Regex("""[^\p{L}\p{Nd}]"""), "")

            // 1. Полное точное совпадение (с учётом знаков препинания и без них)
            if (titleLower == cleanQuery || (qClean.isNotBlank() && tClean == qClean)) {
                score += 2000.0
            }

            // 2. Начинается с запроса
            if (titleLower.startsWith(cleanQuery) || cleanQuery.startsWith(titleLower) ||
                (qClean.length >= 3 && (tClean.startsWith(qClean) || qClean.startsWith(tClean)))) {
                score += 800.0
            }

            // 3. Содержит запрос как подстроку
            if (titleLower.contains(cleanQuery) || cleanQuery.contains(titleLower) ||
                (qClean.length >= 4 && (tClean.contains(qClean) || qClean.contains(tClean)))) {
                score += 400.0
            }

            // 4. Пересечение токенов (Jaccard / Token Overlap)
            if (queryWords.isNotEmpty() && titleWords.isNotEmpty()) {
                val intersection = queryWords.intersect(titleWords).size
                val union = queryWords.union(titleWords).size
                score += (intersection.toDouble() / union.toDouble()) * 500.0
                score += intersection * 80.0
            }

            // 5. Штраф за слишком большую разницу в длине, если нет точного вхождения
            val wordDiff = max(0, titleWords.size - queryWords.size)
            score -= wordDiff * 5.0

            // 6. Небольшой бонус за наличие обложки и количество глав (признак живой книги)
            if (!item.coverUrl.isNullOrBlank()) score += 10.0
            val chapters = item.chapters ?: 0
            if (chapters > 0) score += 5.0

            score
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Russian Dictionary & Transliteration
    // ---------------------------------------------------------------------------------------------

    private val RUSSIAN_TO_ENGLISH_DICT = mapOf(
        "раб тени" to "shadow slave",
        "повелитель тайн" to "lord of the mysteries",
        "восхождение в тени" to "eminence in shadow",
        "начало после конца" to "beginning after the end",
        "одиночная прокачка" to "solo leveling",
        "поднятие уровня в одиночку" to "solo leveling",
        "точка зрения всеведущего читателя" to "omniscient reader",
        "всеведущий читатель" to "omniscient reader",
        "маг на полную ставку" to "versatile mage",
        "магия" to "magic",
        "маг" to "mage",
        "система" to "system",
        "тень" to "shadow",
        "теней" to "shadows",
        "монарх" to "monarch",
        "раб" to "slave",
        "дракон" to "dragon",
        "подземелье" to "dungeon",
        "подземелья" to "dungeons",
        "перерождение" to "reborn",
        "реинкарнация" to "reincarnation",
        "злодей" to "villain",
        "злодейка" to "villainess",
        "рыцарь" to "knight",
        "король" to "king",
        "бог" to "god",
        "богиня" to "goddess",
        "герой" to "hero",
        "башня" to "tower",
        "убийца" to "slayer",
        "уровень" to "level",
        "одиночный" to "solo",
        "культивация" to "cultivation",
        "бессмертный" to "immortal",
        "чудовище" to "monster",
        "монстр" to "monster",
        "академия" to "academy",
        "мир" to "world",
        "тьма" to "darkness",
        "свет" to "light",
        "клинок" to "blade",
        "меч" to "sword",
        "принцесса" to "princess",
        "некромант" to "necromancer",
        "охотник" to "hunter",
        "возвращение" to "return",
        "вторая жизнь" to "second life",
        "прокачка" to "leveling",
        "поднятие уровня" to "level up"
    )

    private fun translateCyrillicNovelQuery(query: String): String {
        var translated = query.lowercase(Locale.ROOT)
        for ((ru, en) in RUSSIAN_TO_ENGLISH_DICT) {
            if (translated.contains(ru)) {
                translated = translated.replace(ru, en)
            }
        }
        val stillHasCyrillic = translated.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        return if (!stillHasCyrillic) translated.trim() else ""
    }

    private val TRANSLIT_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )

    private fun transliterateCyrillic(query: String): String {
        val lower = query.lowercase(Locale.ROOT)
        val sb = StringBuilder()
        for (ch in lower) {
            val mapped = TRANSLIT_MAP[ch]
            if (mapped != null) sb.append(mapped) else sb.append(ch)
        }
        return sb.toString().trim()
    }
}
