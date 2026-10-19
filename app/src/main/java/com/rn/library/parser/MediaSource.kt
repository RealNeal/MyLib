package com.rn.library.parser

import androidx.annotation.StringRes
import com.rn.library.R

enum class SourceCategory(
    val id: String,
    @StringRes val titleRes: Int
) {
    RANOBE("ranobe", R.string.source_category_ranobe),
    MANGA("manga", R.string.source_category_manga),
    ANIME("anime", R.string.source_category_anime),
    FILMS("films", R.string.source_category_films)
}

enum class MediaSource(
    val id: String,
    val title: String,
    val categories: List<SourceCategory>,
    val lang: String
) {
    // Russian sources
    SHIKIMORI("shikimori", "Shikimori", listOf(SourceCategory.ANIME, SourceCategory.MANGA), "RU"),
    MANGALIB("mangalib", "MangaLib", listOf(SourceCategory.MANGA), "RU"),
    RANOBELIB("ranobelib", "RanobeLib", listOf(SourceCategory.RANOBE), "RU"),
    ANIMELIB("animelib", "AnimeLib", listOf(SourceCategory.ANIME), "RU"),
    ANIXART("anixart", "Anixart", listOf(SourceCategory.ANIME), "RU"),
    ANILIBERTY("aniliberty", "AniLiberty", listOf(SourceCategory.ANIME), "RU"),
    ANIMETKA("animetka", "AniMetka", listOf(SourceCategory.ANIME), "RU"),
    REMANGA("remanga", "Remanga", listOf(SourceCategory.MANGA), "RU"),
    RANOBEHUB("ranobehub", "RanobeHub", listOf(SourceCategory.RANOBE), "RU"),
    RULATE("rulate", "Rulate", listOf(SourceCategory.RANOBE), "RU"),
    KINOPOISK("kinopoisk", "Кинопоиск", listOf(SourceCategory.FILMS), "RU"),
    MANGABUFF("mangabuff", "MangaBuff", listOf(SourceCategory.MANGA), "RU"),

    // English sources
    ROYALROAD("royalroad", "Royal Road", listOf(SourceCategory.RANOBE), "EN"),
    RANOBES("ranobes", "Ranobes", listOf(SourceCategory.RANOBE), "EN"),
    SCRIBBLEHUB("scribblehub", "ScribbleHub", listOf(SourceCategory.RANOBE), "EN"),
    MANGADEX("mangadex", "MangaDex", listOf(SourceCategory.MANGA), "EN"),
    ANILIST("anilist", "AniList", listOf(SourceCategory.ANIME, SourceCategory.MANGA), "EN"),
    MYANIMELIST("myanimelist", "MyAnimeList", listOf(SourceCategory.ANIME, SourceCategory.MANGA), "EN"),
    WTRLAB("wtrlab", "WTR-LAB", listOf(SourceCategory.RANOBE), "EN"),
    IMDB("imdb", "IMDb", listOf(SourceCategory.FILMS), "EN");

    val isRussian: Boolean get() = lang == "RU"
    val isEnglish: Boolean get() = lang == "EN"

    val category: String get() = when {
        categories.contains(SourceCategory.ANIME) && categories.contains(SourceCategory.MANGA) -> "Аниме/Манга"
        categories.contains(SourceCategory.MANGA) && categories.contains(SourceCategory.RANOBE) -> "Манга/Ранобэ"
        categories.contains(SourceCategory.RANOBE) -> "Ранобэ"
        categories.contains(SourceCategory.MANGA) -> "Манга"
        categories.contains(SourceCategory.ANIME) -> "Аниме"
        categories.contains(SourceCategory.FILMS) -> "Фильмы/Сериалы"
        else -> ""
    }

    fun hasCategory(cat: SourceCategory): Boolean = categories.contains(cat)

    companion object {
        fun fromId(id: String): MediaSource {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SHIKIMORI
        }
    }
}
