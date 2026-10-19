package com.rn.library.parser

import android.content.Context
import com.rn.library.MyLibraryApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object NetworkParserManager {

    fun detectSourceFromUrl(input: String): MediaSource? {
        val trimmed = input.trim()
        return CatalogSourceRegistry.findByUrl(trimmed)?.source
    }


    private data class CachedSearch(
        val timestamp: Long,
        val results: List<ShikimoriSearchResult>
    )

    private data class CachedDetails(
        val timestamp: Long,
        val metadata: ParsedWorkMetadata
    )

    private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 минут кеш для экономии трафика

    private val searchCache = object : LinkedHashMap<String, CachedSearch>(40, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedSearch>?): Boolean {
            return size > 60
        }
    }

    private val detailsCache = object : LinkedHashMap<String, CachedDetails>(40, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetails>?): Boolean {
            return size > 60
        }
    }

    fun clearCache(context: Context? = null) {
        synchronized(searchCache) { searchCache.clear() }
        synchronized(detailsCache) { detailsCache.clear() }
        WtrLabClient.clearCache()
        try {
            val ctx = context ?: MyLibraryApp.instance
            if (ctx != null) {
                coil.Coil.imageLoader(ctx).memoryCache?.clear()
            }
        } catch (e: Exception) {}
    }

    suspend fun search(
        query: String,
        source: MediaSource,
        filter: ShikimoriFilterType = ShikimoriFilterType.ALL
    ): Result<List<ShikimoriSearchResult>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return Result.success(emptyList())

        // If user pasted a URL, check if we can direct-search or detect
        val isUrl = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val detectedSource = if (isUrl) detectSourceFromUrl(trimmed) else null
        val actualSource = detectedSource ?: source

        val cacheKey = "${actualSource.id}_${filter.name}_${trimmed.lowercase()}"
        synchronized(searchCache) {
            val cached = searchCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return Result.success(cached.results)
            }
        }

        val result = performSearch(trimmed, actualSource, filter)
        if (result.isSuccess) {
            val list = result.getOrThrow()
            if (list.isNotEmpty()) {
                synchronized(searchCache) {
                    searchCache[cacheKey] = CachedSearch(System.currentTimeMillis(), list)
                }
            }
        }
        return result
    }

    private suspend fun performSearch(
        trimmed: String,
        actualSource: MediaSource,
        filter: ShikimoriFilterType
    ): Result<List<ShikimoriSearchResult>> {
        val client = CatalogSourceRegistry.get(actualSource)
            ?: return Result.failure(IllegalArgumentException("Источник не поддерживается: $actualSource"))

        if (client.isUrlMatching(trimmed)) {
            val rawTargetId = client.extractTargetId(trimmed) ?: trimmed
            val detailRes = client.fetchDetails(rawTargetId)
            if (detailRes.isSuccess) {
                val meta = detailRes.getOrThrow()
                val numericId = rawTargetId.split("/").lastOrNull()?.filter { it.isDigit() }?.toLongOrNull()
                    ?: (rawTargetId.hashCode().toLong() and 0x7FFFFFFF)
                val endpoint = when (actualSource) {
                    MediaSource.SHIKIMORI, MediaSource.MYANIMELIST -> rawTargetId.substringBefore("/")
                    MediaSource.REMANGA, MediaSource.MANGABUFF -> "manga"
                    MediaSource.ROYALROAD -> "fiction"
                    MediaSource.RANOBES -> "novels"
                    MediaSource.SCRIBBLEHUB -> "series"
                    MediaSource.IMDB -> "title"
                    MediaSource.KINOPOISK -> "film"
                    MediaSource.ANILIBERTY -> "release"
                    MediaSource.ANIMETKA -> "anime"
                    else -> rawTargetId
                }
                val stringId = when (actualSource) {
                    MediaSource.SHIKIMORI, MediaSource.MYANIMELIST -> rawTargetId.substringAfter("/")
                    else -> rawTargetId
                }
                val searchResult = ShikimoriSearchResult(
                    id = numericId,
                    name = meta.title,
                    russian = meta.alternativeTitles.firstOrNull() ?: "",
                    kind = when (actualSource) {
                        MediaSource.ANIMELIB, MediaSource.ANIXART, MediaSource.ANILIBERTY, MediaSource.ANIMETKA -> "anime"
                        MediaSource.MANGALIB, MediaSource.MANGADEX, MediaSource.REMANGA, MediaSource.MANGABUFF -> "manga"
                        MediaSource.RANOBELIB, MediaSource.RANOBEHUB, MediaSource.RULATE,
                        MediaSource.WTRLAB, MediaSource.ROYALROAD, MediaSource.RANOBES,
                        MediaSource.SCRIBBLEHUB -> "novel"
                        MediaSource.KINOPOISK, MediaSource.IMDB -> "movie"
                        else -> when (meta.type) {
                            com.rn.library.data.WorkType.ANIME -> "anime"
                            com.rn.library.data.WorkType.MANGA -> "manga"
                            com.rn.library.data.WorkType.BOOK -> "novel"
                            com.rn.library.data.WorkType.SERIES -> "movie"
                        }
                    },
                    year = meta.year,
                    coverUrl = meta.coverUrl,
                    episodes = meta.episodes?.toInt(),
                    chapters = (meta.bookChapters ?: meta.chapters)?.toInt(),
                    volumes = meta.volumes?.toInt(),
                    targetType = meta.type,
                    endpoint = endpoint,
                    url = meta.link ?: trimmed,
                    source = actualSource,
                    stringId = stringId
                )
                return Result.success(listOf(searchResult))
            }
        }

        return client.search(trimmed, filter)
    }

    suspend fun fetchDetails(result: ShikimoriSearchResult): Result<ParsedWorkMetadata> {
        val detailKey = "${result.source.id}_${result.endpoint}_${result.stringId}"
        synchronized(detailsCache) {
            val cached = detailsCache[detailKey]
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return Result.success(cached.metadata)
            }
        }

        val res = performFetchDetails(result)
        if (res.isSuccess) {
            val meta = res.getOrThrow()
            synchronized(detailsCache) {
                detailsCache[detailKey] = CachedDetails(System.currentTimeMillis(), meta)
            }
        }
        return res
    }

    private suspend fun performFetchDetails(result: ShikimoriSearchResult): Result<ParsedWorkMetadata> {
        val client = CatalogSourceRegistry.get(result.source)
            ?: return Result.failure(IllegalArgumentException("Неизвестный источник: ${result.source}"))
        return client.fetchDetails(result)
    }

    private fun isAllowedSearchQuery(text: String): Boolean {
        val clean = text.trim()
        if (clean.length < 2) return false

        // Exclude hieroglyphs (Chinese, Japanese, Korean CJK scripts)
        val hasHieroglyphs = clean.any { c ->
            val block = Character.UnicodeBlock.of(c)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.HANGUL_JAMO ||
            block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
        }
        if (hasHieroglyphs) return false

        // Must contain at least one Cyrillic or Latin letter
        return clean.any { c ->
            (c in 'a'..'z') || (c in 'A'..'Z') || (c in 'а'..'я') || (c in 'А'..'Я') || c == 'ё' || c == 'Ё'
        }
    }

    suspend fun enrichFromOtherSources(
        primary: ParsedWorkMetadata,
        category: SourceCategory,
        workId: String,
        repository: com.rn.library.data.WorkRepository
    ): ParsedWorkMetadata = withContext(Dispatchers.IO) {
        val targetSources = MediaSource.entries.filter { it != primary.source && it.hasCategory(category) }
        val targetLang = com.rn.library.ui.AppSettings.getLanguage(repository.context)
        if (targetSources.isEmpty()) {
            return@withContext primary.copy(
                genres = GenreTagTranslator.translateAndNormalizeChips(primary.genres, targetLang),
                tags = GenreTagTranslator.translateAndNormalizeChips(primary.tags, targetLang)
            )
        }

        val searchQueries = mutableListOf<String>()
        fun addQuery(q: String) {
            val clean = ParserUtils.cleanText(q).trim()
            if (isAllowedSearchQuery(clean) && !searchQueries.any { it.equals(clean, ignoreCase = true) }) {
                searchQueries.add(clean)
            }
        }

        val mainTitle = primary.title
        addQuery(mainTitle)
        val withoutParens = mainTitle.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "").trim()
        if (withoutParens.isNotBlank() && withoutParens != mainTitle) {
            addQuery(withoutParens)
        }
        val parensMatch = Regex("""\(([^)]+)\)|\[([^\]]+)\]""").find(mainTitle)
        val inParens = (parensMatch?.groupValues?.get(1)?.ifBlank { null }
            ?: parensMatch?.groupValues?.get(2))?.trim()
        if (!inParens.isNullOrBlank() && inParens.length >= 3) {
            addQuery(inParens)
        }

        for (alt in primary.alternativeTitles) {
            addQuery(alt)
            val altWithoutParens = alt.replace(Regex("""\([^)]*\)|\[[^\]]*\]"""), "").trim()
            if (altWithoutParens.isNotBlank() && altWithoutParens != alt) {
                addQuery(altWithoutParens)
            }
        }

        fun normalizeForMatch(s: String): String =
            s.lowercase().replace(Regex("""[^\p{L}\p{Nd}]+"""), "")

        val allNormalizedCandidates = (listOf(primary.title) + primary.alternativeTitles + searchQueries)
            .map { normalizeForMatch(it) }
            .filter { it.isNotBlank() }
            .distinct()

        val matches: List<ParsedWorkMetadata> = targetSources.map { source ->
            async {
                try {
                    withTimeoutOrNull(3500L) {
                        var bestResult: ShikimoriSearchResult? = null
                        for (q in searchQueries) {
                            val sRes = search(q, source, ShikimoriFilterType.ALL)
                            if (sRes.isSuccess) {
                                val items = sRes.getOrThrow()
                                bestResult = items.firstOrNull { item ->
                                    val normName = normalizeForMatch(item.name)
                                    val normRus = normalizeForMatch(item.russian)

                                    allNormalizedCandidates.any { cand ->
                                        cand.length >= 3 && (
                                            (normName.isNotBlank() && (normName == cand || normName.contains(cand) || cand.contains(normName))) ||
                                            (normRus.isNotBlank() && (normRus == cand || normRus.contains(cand) || cand.contains(normRus)))
                                        )
                                    }
                                } ?: if (items.size == 1 && q.length >= 4) items.first() else null

                                if (bestResult != null) break
                            }
                        }
                        if (bestResult != null) {
                            val detailRes = fetchDetails(bestResult)
                            if (detailRes.isSuccess) detailRes.getOrThrow() else null
                        } else null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()

        if (matches.isEmpty()) {
            return@withContext primary.copy(
                genres = GenreTagTranslator.translateAndNormalizeChips(primary.genres, targetLang),
                tags = GenreTagTranslator.translateAndNormalizeChips(primary.tags, targetLang)
            )
        }

        fun cleanItem(s: String): String = ParserUtils.cleanText(s).trim()

        val mergedAltTitles = (primary.alternativeTitles + matches.flatMap { it.alternativeTitles } + matches.map { it.title })
            .map { cleanItem(it) }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.equals(primary.title, ignoreCase = true) }
            .distinctBy { it.lowercase() }

        val mergedGenres = GenreTagTranslator.translateAndNormalizeChips(
            primary.genres + matches.flatMap { it.genres },
            targetLang
        )

        val mergedTags = GenreTagTranslator.translateAndNormalizeChips(
            primary.tags + matches.flatMap { it.tags },
            targetLang
        )

        val mergedAuthor = if (primary.author.isNullOrBlank()) {
            matches.firstOrNull { !it.author.isNullOrBlank() }?.author?.let { ParserUtils.sanitizeAuthor(it) }
        } else {
            primary.author
        }

        val mergedYear = primary.year ?: matches.firstOrNull { it.year != null }?.year
        val mergedChapters = primary.chapters ?: matches.firstOrNull { it.chapters != null }?.chapters
        val mergedVolumes = primary.volumes ?: matches.firstOrNull { it.volumes != null }?.volumes
        val mergedBookChapters = primary.bookChapters ?: matches.firstOrNull { it.bookChapters != null }?.bookChapters
        val mergedEpisodes = primary.episodes ?: matches.firstOrNull { it.episodes != null }?.episodes

        val mergedDescription = if (primary.description.length < 50) {
            matches.firstOrNull { it.description.length >= 50 }?.description ?: primary.description
        } else {
            primary.description
        }

        val existingLinks = (listOfNotNull(primary.link) + primary.additionalLinks).map { it.trim() }.toMutableSet()
        val extraLinks = mutableListOf<String>()
        extraLinks.addAll(primary.additionalLinks.filter { it.isNotBlank() })
        for (m in matches) {
            val l = m.link?.trim()
            if (!l.isNullOrBlank() && !existingLinks.contains(l)) {
                existingLinks.add(l)
                extraLinks.add(l)
            }
            for (al in m.additionalLinks) {
                val alt = al.trim()
                if (alt.isNotBlank() && !existingLinks.contains(alt)) {
                    existingLinks.add(alt)
                    extraLinks.add(alt)
                }
            }
        }

        val candidateCoverUrls = matches.mapNotNull { it.coverUrl?.trim() }
            .filter { it.isNotBlank() && it != primary.coverUrl && !it.contains("no-poster") }
            .distinct()
            .take(3)

        val extraCovers = candidateCoverUrls.mapIndexed { idx, extraCoverUrl ->
            async {
                try {
                    withTimeoutOrNull(2500L) {
                        repository.downloadCoverToStorage(extraCoverUrl, workId, idx + 1)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull().filter { it.isNotBlank() }

        primary.copy(
            alternativeTitles = mergedAltTitles,
            genres = mergedGenres,
            tags = mergedTags,
            author = mergedAuthor,
            year = mergedYear,
            chapters = mergedChapters,
            volumes = mergedVolumes,
            bookChapters = mergedBookChapters,
            episodes = mergedEpisodes,
            description = mergedDescription,
            additionalCovers = extraCovers,
            additionalLinks = extraLinks
        )
    }
}
