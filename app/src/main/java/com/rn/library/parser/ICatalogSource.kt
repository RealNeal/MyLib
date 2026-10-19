package com.rn.library.parser

/**
 * Единый интерфейс парсера источников по образцу архитектуры AniLabX.
 * Позволяет единообразно выполнять поиск, проверку URL и получение детальной информации
 * без необходимости ветвления через гигантские when-блоки.
 */
interface ICatalogSource {
    /** Источник медиаданных */
    val source: MediaSource

    /** Человекочитаемое название */
    val name: String get() = source.title

    /** Язык источника */
    val lang: String get() = source.lang

    /**
     * Проверяет, принадлежит ли данный URL источнику.
     */
    fun isUrlMatching(url: String): Boolean

    /**
     * Извлекает уникальный целевой ID или слаг произведения из переданного URL.
     * Возвращает null, если URL не принадлежит источнику или ID не удалось извлечь.
     */
    fun extractTargetId(url: String): String?

    /**
     * Выполняет поиск по запросу.
     */
    suspend fun search(
        query: String,
        filter: ShikimoriFilterType = ShikimoriFilterType.ALL
    ): Result<List<ShikimoriSearchResult>>

    /**
     * Получает полную информацию о произведении по его targetId (или URL).
     */
    suspend fun fetchDetails(targetId: String): Result<ParsedWorkMetadata>

    /**
     * Получает полную информацию о произведении по результату поиска.
     */
    suspend fun fetchDetails(result: ShikimoriSearchResult): Result<ParsedWorkMetadata> {
        val targetId = when (source) {
            MediaSource.SHIKIMORI, MediaSource.MYANIMELIST -> {
                if (result.endpoint.isNotBlank() && !result.stringId.contains("/")) {
                    "${result.endpoint}/${result.stringId.ifBlank { result.id.toString() }}"
                } else {
                    result.stringId.ifBlank { result.id.toString() }
                }
            }
            MediaSource.RANOBES -> result.stringId.ifBlank { result.url }
            else -> result.stringId.ifBlank { result.id.toString() }
        }
        return fetchDetails(targetId)
    }
}

