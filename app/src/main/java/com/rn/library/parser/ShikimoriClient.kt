package com.rn.library.parser

import com.rn.library.data.AnimeSeason
import com.rn.library.data.MangaType
import com.rn.library.data.WorkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object ShikimoriClient {
    fun getBaseUrl(): String = ParserDomainManager.getDomain(ParserDomainManager.SOURCE_SHIKIMORI)
    private const val USER_AGENT = "MyLibraryApp/2.7 (Android; github.com/RealNeal/MyLib)"

    fun isShikimoriUrl(input: String): Boolean {
        val trimmed = input.trim().lowercase()
        return Regex("""shikimori\.[a-z]+""").containsMatchIn(trimmed)
    }

    fun parseShikimoriUrl(input: String): Pair<String, Long>? {
        val trimmed = input.trim()
        val regex = Regex("""shikimori\.[a-z]+\/(animes|mangas|ranobe)\/(?:z)?(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed) ?: return null
        val endpoint = match.groupValues[1].lowercase()
        val id = match.groupValues[2].toLongOrNull() ?: return null
        return Pair(endpoint, id)
    }

    suspend fun search(
        query: String,
        filter: ShikimoriFilterType = ShikimoriFilterType.ALL
    ): Result<List<ShikimoriSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val results = mutableListOf<ShikimoriSearchResult>()

            when (filter) {
                ShikimoriFilterType.ALL -> {
                    val animeDeferred = async { fetchSearchList("/api/animes?search=$encodedQuery&limit=15", "animes") }
                    val mangaDeferred = async { fetchSearchList("/api/mangas?search=$encodedQuery&limit=15", "mangas") }
                    results.addAll(animeDeferred.await())
                    results.addAll(mangaDeferred.await())
                }
                ShikimoriFilterType.ANIME -> {
                    results.addAll(fetchSearchList("/api/animes?search=$encodedQuery&limit=25", "animes"))
                }
                ShikimoriFilterType.MANGA -> {
                    results.addAll(fetchSearchList("/api/mangas?search=$encodedQuery&limit=25", "mangas"))
                }
                ShikimoriFilterType.RANOBE -> {
                    results.addAll(fetchSearchList("/api/ranobe?search=$encodedQuery&limit=25", "ranobe"))
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun fetchDetails(
        endpoint: String,
        id: Long
    ): Result<ParsedWorkMetadata> = withContext(Dispatchers.IO) {
        try {
            val actualEndpoint = if (endpoint == "ranobe") "mangas" else endpoint
            val detailsJson = fetchJson("/api/$actualEndpoint/$id")
                ?: return@withContext Result.failure(Exception("Failed to load details from Shikimori"))

            val rolesJsonArray = try {
                fetchJsonArray("/api/$actualEndpoint/$id/roles")
            } catch (_: Exception) {
                null
            }

            val metadata = parseMetadata(detailsJson, rolesJsonArray, endpoint)
            Result.success(metadata)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun fetchSearchList(urlString: String, endpoint: String): List<ShikimoriSearchResult> {
        val array = fetchJsonArray(urlString) ?: return emptyList()
        val list = mutableListOf<ShikimoriSearchResult>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val parsed = parseSearchResultItem(item, endpoint)
            list.add(parsed)
        }
        return list
    }

    private fun parseSearchResultItem(obj: JSONObject, defaultEndpoint: String): ShikimoriSearchResult {
        val id = obj.getLong("id")
        val name = obj.optString("name")
        val russian = obj.optString("russian").ifBlank { name }
        val kind = obj.optString("kind")
        val imageObj = obj.optJSONObject("image")
        val imagePath = imageObj?.optString("original")
            ?: imageObj?.optString("preview")
            ?: imageObj?.optString("x96")
        val coverUrl = imagePath?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else "${getBaseUrl()}$it"
        }
        val episodes = obj.optInt("episodes").takeIf { it > 0 }
        val chapters = obj.optInt("chapters").takeIf { it > 0 }
        val volumes = obj.optInt("volumes").takeIf { it > 0 }
        val airedOn = obj.optString("aired_on")
        val year = airedOn.take(4).toIntOrNull()
        val url = obj.optString("url")

        val targetType = when {
            defaultEndpoint == "animes" -> WorkType.ANIME
            kind == "light_novel" || kind == "novel" || defaultEndpoint == "ranobe" -> WorkType.BOOK
            else -> WorkType.MANGA
        }

        return ShikimoriSearchResult(
            id = id,
            name = name,
            russian = russian,
            kind = kind,
            year = year,
            coverUrl = coverUrl,
            episodes = episodes,
            chapters = chapters,
            volumes = volumes,
            targetType = targetType,
            endpoint = defaultEndpoint,
            url = url
        )
    }

    private fun parseMetadata(
        json: JSONObject,
        rolesArray: JSONArray?,
        endpoint: String
    ): ParsedWorkMetadata {
        val id = json.optLong("id")
        val name = json.optString("name")
        val russian = json.optString("russian").ifBlank { name }
        val title = russian.ifBlank { name }

        // Alternative titles: name, english array, japanese array, synonyms array
        val altTitles = mutableListOf<String>()
        if (name.isNotBlank() && name != title) {
            altTitles.add(name)
        }
        json.optJSONArray("english")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank() && s != title && !altTitles.contains(s)) altTitles.add(s)
            }
        }
        json.optJSONArray("japanese")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank() && s != title && !altTitles.contains(s)) altTitles.add(s)
            }
        }
        json.optJSONArray("synonyms")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i)
                if (s.isNotBlank() && s != title && !altTitles.contains(s)) altTitles.add(s)
            }
        }

        val kind = json.optString("kind").lowercase()
        val isRanobe = kind == "light_novel" || kind == "novel" || endpoint == "ranobe"
        val isAnime = endpoint == "animes"

        val workType = when {
            isAnime -> WorkType.ANIME
            isRanobe -> WorkType.BOOK
            else -> WorkType.MANGA
        }

        val mangaType = when (kind) {
            "manga", "one_shot", "doujin" -> MangaType.MANGA
            "manhwa" -> MangaType.MANHWA
            "manhua" -> MangaType.MANHUA
            else -> if (!isAnime && !isRanobe) MangaType.MANGA else null
        }

        val airedOn = json.optString("aired_on")
        val year = airedOn.take(4).toIntOrNull()
        val animeSeason = if (isAnime) parseSeason(airedOn) else null

        // Episodes / Chapters / Volumes
        val rawEpisodes = json.optInt("episodes").takeIf { it > 0 }?.toDouble()
        val rawChapters = json.optInt("chapters").takeIf { it > 0 }?.toDouble()
        val rawVolumes = json.optInt("volumes").takeIf { it > 0 }?.toDouble()

        val episodes = if (isAnime) rawEpisodes else null
        val chapters = if (workType == WorkType.MANGA) rawChapters else null
        val volumes = if (workType == WorkType.MANGA) rawVolumes else null
        val bookChapters = if (workType == WorkType.BOOK) rawChapters else null
        val bookVolumes = if (workType == WorkType.BOOK) rawVolumes else null

        // Description
        val rawDescription = json.optString("description")
        val description = cleanShikimoriText(rawDescription)

        // Cover
        val graphQLPoster = fetchPosterFromGraphQL(endpoint, id)
        val imageObj = json.optJSONObject("image")
        val imagePath = imageObj?.optString("original")
            ?: imageObj?.optString("preview")
        val fallbackCoverUrl = imagePath?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("http")) it else "${getBaseUrl()}$it"
        }
        val coverUrl = graphQLPoster ?: fallbackCoverUrl

        // Genres
        val genres = mutableListOf<String>()
        json.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val g = arr.optJSONObject(i) ?: continue
                val gRu = g.optString("russian").ifBlank { g.optString("name") }
                if (gRu.isNotBlank()) {
                    val formatted = gRu.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    if (!genres.contains(formatted)) genres.add(formatted)
                }
            }
        }

        // Author / Studio
        var author: String? = null
        if (rolesArray != null) {
            val authorCandidates = mutableListOf<String>()
            for (i in 0 until rolesArray.length()) {
                val roleItem = rolesArray.optJSONObject(i) ?: continue
                val person = roleItem.optJSONObject("person") ?: continue
                val roles = roleItem.optJSONArray("roles")
                val isTargetRole = if (roles != null) {
                    var match = false
                    for (r in 0 until roles.length()) {
                        val roleStr = roles.optString(r).lowercase()
                        if (isAnime) {
                            if (roleStr.contains("original") || roleStr.contains("director") || roleStr.contains("creator")) match = true
                        } else {
                            if (roleStr.contains("story") || roleStr.contains("art") || roleStr.contains("author") || roleStr.contains("creator")) match = true
                        }
                    }
                    match
                } else false

                if (isTargetRole) {
                    val pName = person.optString("russian").ifBlank { person.optString("name") }
                    if (pName.isNotBlank() && !authorCandidates.contains(pName)) {
                        authorCandidates.add(pName)
                    }
                }
            }
            if (authorCandidates.isNotEmpty()) {
                author = authorCandidates.take(2).joinToString(", ")
            }
        }

        // Fallback for anime studio
        if (author.isNullOrBlank() && isAnime) {
            json.optJSONArray("studios")?.let { arr ->
                val studioList = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i)?.optString("name")
                    if (!s.isNullOrBlank()) studioList.add(s)
                }
                if (studioList.isNotEmpty()) {
                    author = studioList.joinToString(", ")
                }
            }
        }

        // Link
        val urlPath = json.optString("url")
        val link = if (urlPath.isNotBlank()) {
            if (urlPath.startsWith("http")) urlPath else "${getBaseUrl()}$urlPath"
        } else null

        return ParsedWorkMetadata(
            title = title,
            alternativeTitles = altTitles.take(5),
            description = description,
            type = workType,
            mangaType = mangaType,
            animeSeason = animeSeason,
            year = year,
            episodes = episodes,
            chapters = chapters ?: bookVolumes, // For books, volume count is stored in chapters
            volumes = volumes,
            bookChapters = bookChapters,
            coverUrl = coverUrl,
            author = author,
            genres = ParserUtils.normalizeChips(genres),
            link = link
        )
    }

    private fun parseSeason(airedOn: String?): AnimeSeason? {
        if (airedOn.isNullOrBlank()) return null
        val parts = airedOn.split("-")
        if (parts.size >= 2) {
            val month = parts[1].toIntOrNull() ?: return null
            return when (month) {
                12, 1, 2 -> AnimeSeason.WINTER
                3, 4, 5 -> AnimeSeason.SPRING
                6, 7, 8 -> AnimeSeason.SUMMER
                9, 10, 11 -> AnimeSeason.FALL
                else -> null
            }
        }
        return null
    }

    fun cleanShikimoriText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw

        // Unescape common HTML entities
        text = text.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")

        // [tag=value]content[/tag] -> content
        text = text.replace(
            Regex("""\[(?:character|anime|manga|ranobe|person|entry|url)=[^\]]*\](.*?)\[/(?:character|anime|manga|ranobe|person|entry|url)\]""", RegexOption.DOT_MATCHES_ALL)
        ) { it.groupValues[1] }

        // [spoiler=title]content[/spoiler] or [spoiler]content[/spoiler] -> content
        text = text.replace(
            Regex("""\[spoiler(?:=[^\]]*)?\](.*?)\[/spoiler\]""", RegexOption.DOT_MATCHES_ALL)
        ) { it.groupValues[1] }

        // [[keyword]] -> keyword
        text = text.replace(Regex("""\[\[(.*?)\]\]""")) { it.groupValues[1] }

        // Strip remaining BBCode tags like [b], [/b], [i], [/i]
        text = text.replace(Regex("""\[/?[a-zA-Z0-9_-]+(?:=[^\]]*)?\]"""), "")

        return text.trim()
    }

    private fun fetchJson(pathOrUrl: String): JSONObject? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val idx = pathOrUrl.indexOf("/api/")
            if (idx >= 0) pathOrUrl.substring(idx) else pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_SHIKIMORI)
        for (mirror in mirrors) {
            val fullUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            try {
                val conn = openConnection(fullUrl)
                try {
                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        if (!cleanPath.startsWith("http")) {
                            ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_SHIKIMORI, mirror)
                        }
                        return JSONObject(text)
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {}
            if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_SHIKIMORI, mirror)
            }
        }
        return null
    }

    private fun fetchJsonArray(pathOrUrl: String): JSONArray? {
        val cleanPath = if (pathOrUrl.startsWith("http")) {
            val idx = pathOrUrl.indexOf("/api/")
            if (idx >= 0) pathOrUrl.substring(idx) else pathOrUrl
        } else if (!pathOrUrl.startsWith("/")) "/$pathOrUrl" else pathOrUrl

        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_SHIKIMORI)
        for (mirror in mirrors) {
            val fullUrl = if (cleanPath.startsWith("http")) cleanPath else "$mirror$cleanPath"
            try {
                val conn = openConnection(fullUrl)
                try {
                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        if (!cleanPath.startsWith("http")) {
                            ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_SHIKIMORI, mirror)
                        }
                        return JSONArray(text)
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {}
            if (!cleanPath.startsWith("http")) {
                ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_SHIKIMORI, mirror)
            }
        }
        return null
    }

    private fun openConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 4000
        conn.readTimeout = 6000
        return conn
    }

    private fun fetchPosterFromGraphQL(endpoint: String, id: Long): String? {
        val target = if (endpoint == "animes") "animes" else "mangas"
        val query = """{ $target(ids: "$id", limit: 1) { poster { originalUrl mainUrl } } }"""
        val mirrors = ParserDomainManager.getMirrors(ParserDomainManager.SOURCE_SHIKIMORI)

        for (mirror in mirrors) {
            try {
                val url = URL("$mirror/api/graphql")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 4000
                    readTimeout = 6000
                    doOutput = true
                }
                val body = JSONObject().put("query", query).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode in 200..299) {
                    val respText = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val root = JSONObject(respText)
                    val data = root.optJSONObject("data")
                    val list = data?.optJSONArray(target)
                    if (list != null && list.length() > 0) {
                        val item = list.getJSONObject(0)
                        val poster = item.optJSONObject("poster")
                        val originalUrl = poster?.optString("originalUrl")?.takeIf { it.isNotBlank() }
                        val mainUrl = poster?.optString("mainUrl")?.takeIf { it.isNotBlank() }
                        val candidate = originalUrl ?: mainUrl
                        if (candidate != null) {
                            ParserDomainManager.reportWorking(ParserDomainManager.SOURCE_SHIKIMORI, mirror)
                            return if (candidate.startsWith("http")) candidate else "$mirror$candidate"
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (_: Exception) {}
            ParserDomainManager.reportFailure(ParserDomainManager.SOURCE_SHIKIMORI, mirror)
        }
        return null
    }
}
