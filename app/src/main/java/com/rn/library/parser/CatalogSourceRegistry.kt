package com.rn.library.parser

import java.util.concurrent.ConcurrentHashMap

/**
 * Реестр источников каталога по образцу архитектуры расширений AniLabX.
 * Хранит зарегистрированные парсеры и предоставляет доступ по типу источника или URL.
 */
object CatalogSourceRegistry {

    private val sources = ConcurrentHashMap<MediaSource, ICatalogSource>()

    init {
        registerAllBuiltinSources()
    }

    fun register(catalogSource: ICatalogSource) {
        sources[catalogSource.source] = catalogSource
    }

    fun get(source: MediaSource): ICatalogSource? = sources[source]

    fun findByUrl(url: String): ICatalogSource? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return sources.values.firstOrNull { it.isUrlMatching(trimmed) }
    }

    fun all(): List<ICatalogSource> = sources.values.toList()

    private fun registerAllBuiltinSources() {
        // 1. Shikimori
        register(object : ICatalogSource {
            override val source = MediaSource.SHIKIMORI
            override fun isUrlMatching(url: String) = ShikimoriClient.isShikimoriUrl(url)
            override fun extractTargetId(url: String): String? {
                val pair = ShikimoriClient.parseShikimoriUrl(url) ?: return null
                return "${pair.first}/${pair.second}"
            }
            override suspend fun search(query: String, filter: ShikimoriFilterType) =
                ShikimoriClient.search(query, filter)
            override suspend fun fetchDetails(targetId: String): Result<ParsedWorkMetadata> {
                val parts = targetId.split("/")
                if (parts.size >= 2) {
                    val ep = parts[0]
                    val id = parts[1].toLongOrNull() ?: 0L
                    return ShikimoriClient.fetchDetails(ep, id)
                }
                return ShikimoriClient.fetchDetails("animes", targetId.toLongOrNull() ?: 0L)
            }
        })

        // 2. MangaDex
        register(object : ICatalogSource {
            override val source = MediaSource.MANGADEX
            override fun isUrlMatching(url: String) = MangaDexClient.isMangaDexUrl(url)
            override fun extractTargetId(url: String) = MangaDexClient.parseMangaDexId(url)
            override suspend fun search(query: String, filter: ShikimoriFilterType) = MangaDexClient.search(query)
            override suspend fun fetchDetails(targetId: String) = MangaDexClient.fetchDetails(targetId)
        })

        // 3. AniList
        register(object : ICatalogSource {
            override val source = MediaSource.ANILIST
            override fun isUrlMatching(url: String) = AniListClient.isAniListUrl(url)
            override fun extractTargetId(url: String) = AniListClient.parseAniListId(url)?.second?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = AniListClient.search(query, filter)
            override suspend fun fetchDetails(targetId: String) =
                AniListClient.fetchDetails(targetId.toIntOrNull() ?: 0)
        })

        // 4. MyAnimeList (Jikan)
        register(object : ICatalogSource {
            override val source = MediaSource.MYANIMELIST
            override fun isUrlMatching(url: String) = JikanClient.isMalUrl(url)
            override fun extractTargetId(url: String): String? {
                val pair = JikanClient.parseMalUrl(url) ?: return null
                return "${pair.first}/${pair.second}"
            }
            override suspend fun search(query: String, filter: ShikimoriFilterType) = JikanClient.search(query, filter)
            override suspend fun fetchDetails(targetId: String): Result<ParsedWorkMetadata> {
                val parts = targetId.split("/")
                if (parts.size >= 2) {
                    val ep = parts[0]
                    val id = parts[1].toLongOrNull() ?: 0L
                    return JikanClient.fetchDetails(ep, id)
                }
                return JikanClient.fetchDetails("anime", targetId.toLongOrNull() ?: 0L)
            }
        })

        // 5. Remanga
        register(object : ICatalogSource {
            override val source = MediaSource.REMANGA
            override fun isUrlMatching(url: String) = RemangaClient.isRemangaUrl(url)
            override fun extractTargetId(url: String) = RemangaClient.parseRemangaDir(url)
            override suspend fun search(query: String, filter: ShikimoriFilterType) = RemangaClient.search(query)
            override suspend fun fetchDetails(targetId: String) = RemangaClient.fetchDetails(targetId)
        })

        // 6. MangaLib
        register(object : ICatalogSource {
            override val source = MediaSource.MANGALIB
            override fun isUrlMatching(url: String) =
                LibSocialClient.isLibUrl(url) &&
                !url.contains("ranobelib", ignoreCase = true) &&
                !url.contains("ranobe.lib", ignoreCase = true) &&
                !url.contains("animelib", ignoreCase = true) &&
                !url.contains("anilib", ignoreCase = true) &&
                !url.contains("anime.lib", ignoreCase = true)
            override fun extractTargetId(url: String): String? {
                val pair = LibSocialClient.parseLibSlug(url)
                return pair?.second
            }
            override suspend fun search(query: String, filter: ShikimoriFilterType) =
                LibSocialClient.search(query, MediaSource.MANGALIB)
            override suspend fun fetchDetails(targetId: String) =
                LibSocialClient.fetchDetails(targetId, MediaSource.MANGALIB)
        })

        // 7. RanobeLib
        register(object : ICatalogSource {
            override val source = MediaSource.RANOBELIB
            override fun isUrlMatching(url: String) =
                LibSocialClient.isLibUrl(url) && (url.contains("ranobelib", ignoreCase = true) || url.contains("ranobe.lib", ignoreCase = true))
            override fun extractTargetId(url: String): String? {
                val pair = LibSocialClient.parseLibSlug(url)
                return if (pair?.first == MediaSource.RANOBELIB) pair.second else null
            }
            override suspend fun search(query: String, filter: ShikimoriFilterType) =
                LibSocialClient.search(query, MediaSource.RANOBELIB)
            override suspend fun fetchDetails(targetId: String) =
                LibSocialClient.fetchDetails(targetId, MediaSource.RANOBELIB)
        })

        // 8. AnimeLib
        register(object : ICatalogSource {
            override val source = MediaSource.ANIMELIB
            override fun isUrlMatching(url: String) =
                LibSocialClient.isLibUrl(url) && (url.contains("animelib", ignoreCase = true) || url.contains("anilib", ignoreCase = true) || url.contains("anime.lib", ignoreCase = true))
            override fun extractTargetId(url: String): String? {
                val pair = LibSocialClient.parseLibSlug(url)
                return if (pair?.first == MediaSource.ANIMELIB) pair.second else null
            }
            override suspend fun search(query: String, filter: ShikimoriFilterType) =
                LibSocialClient.search(query, MediaSource.ANIMELIB)
            override suspend fun fetchDetails(targetId: String) =
                LibSocialClient.fetchDetails(targetId, MediaSource.ANIMELIB)
        })

        // 9. Anixart
        register(object : ICatalogSource {
            override val source = MediaSource.ANIXART
            override fun isUrlMatching(url: String) = AnixartClient.isAnixartUrl(url)
            override fun extractTargetId(url: String) = AnixartClient.parseAnixartId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = AnixartClient.search(query)
            override suspend fun fetchDetails(targetId: String) =
                AnixartClient.fetchDetails(targetId.toLongOrNull() ?: AnixartClient.parseAnixartId(targetId) ?: 0L)
        })

        // 10. AniLiberty
        register(object : ICatalogSource {
            override val source = MediaSource.ANILIBERTY
            override fun isUrlMatching(url: String) = AniLibertyClient.isAniLibertyUrl(url)
            override fun extractTargetId(url: String) = AniLibertyClient.parseAniLibertyId(url)
            override suspend fun search(query: String, filter: ShikimoriFilterType) = AniLibertyClient.search(query)
            override suspend fun fetchDetails(targetId: String) = AniLibertyClient.fetchDetails(targetId)
        })

        // 10. RanobeHub
        register(object : ICatalogSource {
            override val source = MediaSource.RANOBEHUB
            override fun isUrlMatching(url: String) = RanobeHubClient.isRanobeHubUrl(url)
            override fun extractTargetId(url: String) = RanobeHubClient.parseRanobeHubId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = RanobeHubClient.search(query)
            override suspend fun fetchDetails(targetId: String) =
                RanobeHubClient.fetchDetails(targetId.toLongOrNull() ?: 0L)
        })

        // 11. Rulate
        register(object : ICatalogSource {
            override val source = MediaSource.RULATE
            override fun isUrlMatching(url: String) = RulateClient.isRulateUrl(url)
            override fun extractTargetId(url: String) = RulateClient.parseRulateBookId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = RulateClient.search(query)
            override suspend fun fetchDetails(targetId: String) =
                RulateClient.fetchDetails(targetId.toLongOrNull() ?: 0L)
        })

        // 12. WTR-LAB
        register(object : ICatalogSource {
            override val source = MediaSource.WTRLAB
            override fun isUrlMatching(url: String) = WtrLabClient.isWtrLabUrl(url)
            override fun extractTargetId(url: String) = WtrLabClient.parseWtrLabId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = WtrLabClient.search(query)
            override suspend fun fetchDetails(targetId: String) =
                WtrLabClient.fetchDetails(targetId.toLongOrNull() ?: 0L)
        })

        // 13. Кинопоиск
        register(object : ICatalogSource {
            override val source = MediaSource.KINOPOISK
            override fun isUrlMatching(url: String) = KinopoiskClient.isKinopoiskUrl(url)
            override fun extractTargetId(url: String) = KinopoiskClient.parseKinopoiskId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = KinopoiskClient.search(query)
            override suspend fun fetchDetails(targetId: String) =
                KinopoiskClient.fetchDetails(targetId.toLongOrNull() ?: 0L)
        })

        // 14. IMDb
        register(object : ICatalogSource {
            override val source = MediaSource.IMDB
            override fun isUrlMatching(url: String) = ImdbClient.isImdbUrl(url)
            override fun extractTargetId(url: String) = ImdbClient.parseImdbId(url)
            override suspend fun search(query: String, filter: ShikimoriFilterType) = ImdbClient.search(query)
            override suspend fun fetchDetails(targetId: String) = ImdbClient.fetchDetails(targetId)
        })

        // 15. Royal Road
        register(object : ICatalogSource {
            override val source = MediaSource.ROYALROAD
            override fun isUrlMatching(url: String) = RoyalRoadClient.isRoyalRoadUrl(url)
            override fun extractTargetId(url: String) = RoyalRoadClient.parseRoyalRoadFictionId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = RoyalRoadClient.search(query)
            override suspend fun fetchDetails(targetId: String) =
                RoyalRoadClient.fetchDetails(targetId.toLongOrNull() ?: 0L)
        })

        // 16. Ranobes
        register(object : ICatalogSource {
            override val source = MediaSource.RANOBES
            override fun isUrlMatching(url: String) = RanobesClient.isRanobesUrl(url)
            override fun extractTargetId(url: String) = RanobesClient.parseRanobesNovelId(url)?.toString() ?: url
            override suspend fun search(query: String, filter: ShikimoriFilterType) = RanobesClient.search(query)
            override suspend fun fetchDetails(targetId: String) = RanobesClient.fetchDetails(targetId)
        })

        // 17. ScribbleHub
        register(object : ICatalogSource {
            override val source = MediaSource.SCRIBBLEHUB
            override fun isUrlMatching(url: String) = ScribbleHubClient.isScribbleHubUrl(url)
            override fun extractTargetId(url: String) =
                ScribbleHubClient.parseScribbleHubSeriesId(url)?.toString()
            override suspend fun search(query: String, filter: ShikimoriFilterType) = ScribbleHubClient.search(query)
            override suspend fun fetchDetails(targetId: String): Result<ParsedWorkMetadata> {
                val seriesId = targetId.toLongOrNull()
                    ?: ScribbleHubClient.parseScribbleHubSeriesId(targetId)
                return ScribbleHubClient.fetchDetails(seriesId?.toString() ?: targetId)
            }
        })

        // 18. MangaBuff
        register(object : ICatalogSource {
            override val source = MediaSource.MANGABUFF
            override fun isUrlMatching(url: String) = MangaBuffClient.isMangaBuffUrl(url)
            override fun extractTargetId(url: String) = MangaBuffClient.parseMangaBuffSlug(url) ?: url
            override suspend fun search(query: String, filter: ShikimoriFilterType) = MangaBuffClient.search(query)
            override suspend fun fetchDetails(targetId: String) = MangaBuffClient.fetchDetails(targetId)
        })

        // 19. AniMetka
        register(object : ICatalogSource {
            override val source = MediaSource.ANIMETKA
            override fun isUrlMatching(url: String) = AnimetkaClient.isAnimetkaUrl(url)
            override fun extractTargetId(url: String) = AnimetkaClient.parseAnimetkaId(url)
            override suspend fun search(query: String, filter: ShikimoriFilterType) = AnimetkaClient.search(query)
            override suspend fun fetchDetails(targetId: String) = AnimetkaClient.fetchDetails(targetId)
        })
    }
}
