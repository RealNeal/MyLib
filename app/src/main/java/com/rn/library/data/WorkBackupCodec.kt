package com.rn.library.data

import org.json.JSONArray
import org.json.JSONObject

/** Lossless portable records; Markdown remains the separate compatibility format. */
internal object WorkBackupCodec {
    private val lists = WorkConverters()

    fun encode(work: Work): JSONObject = JSONObject().apply {
        fun field(name: String, value: Any?) { put(name, value ?: JSONObject.NULL) }
        field("id", work.id); field("title", work.title); field("description", work.description)
        field("type", work.type.name); field("status", work.status.name)
        field("coverPath", work.coverPath); field("coverPaths", JSONArray(work.coverPaths))
        field("chapters", work.chapters); field("volumes", work.volumes); field("bookChapters", work.bookChapters)
        field("episodes", work.episodes); field("seasons", work.seasons); field("year", work.year)
        field("yearPeriod", work.yearPeriod); field("country", work.country)
        field("seriesType", work.seriesType?.name); field("mangaType", work.mangaType?.name)
        field("animeSeason", work.animeSeason?.name); field("abandonedProgress", work.abandonedProgress)
        field("progress", work.progress); field("otherTitle", work.otherTitle); field("dateRead", work.dateRead)
        field("rereadDates", JSONArray(work.rereadDates))
        field("readingPeriods", JSONArray(lists.periodsToJson(work.readingPeriods)))
        field("unitProgress", JSONArray(lists.unitsToJson(work.unitProgress)))
        field("activeUnitIndex", work.activeUnitIndex); field("note", work.note)
        field("link", work.link); field("link2", work.link2); field("links", JSONArray(work.links))
        field("author", work.author); field("updatedAt", work.updatedAt)
        field("genres", JSONArray(work.genres)); field("tags", JSONArray(work.tags))
    }

    fun decode(json: JSONObject): Work {
        fun text(key: String): String? = if (json.isNull(key)) null else json.getString(key)
        fun number(key: String): Double? = if (json.isNull(key)) null else json.getDouble(key).also {
            require(it.isFinite() && it in 0.0..InputLimits.MAX_UNITS.toDouble())
        }
        fun integer(key: String): Int? = if (json.isNull(key)) null else json.getInt(key)
        fun strings(key: String) = lists.jsonToStrings(json.getJSONArray(key).toString())
        val id = json.getString("id")
        require(InputLimits.validId(id))
        val units = lists.jsonToUnits(json.getJSONArray("unitProgress").toString())
        require(units.size <= InputLimits.MAX_UNITS && units.all {
            it.completed.isFinite() && it.completed in 0.0..InputLimits.MAX_UNITS.toDouble() &&
                (it.total == null || it.total.isFinite() && it.total in 0.0..InputLimits.MAX_UNITS.toDouble())
        })
        val seasons = integer("seasons")?.also { require(it in 0..InputLimits.MAX_UNITS) }
        return Work(
            id = id, title = json.getString("title"), description = json.getString("description"),
            type = WorkType.valueOf(json.getString("type")), status = WorkStatus.valueOf(json.getString("status")),
            coverPath = text("coverPath"), coverPaths = strings("coverPaths"),
            chapters = number("chapters"), volumes = number("volumes"), bookChapters = number("bookChapters"),
            episodes = number("episodes"), seasons = seasons, year = integer("year"), yearPeriod = text("yearPeriod"),
            country = text("country"), seriesType = text("seriesType")?.let { SeriesType.valueOf(it) },
            mangaType = text("mangaType")?.let { MangaType.valueOf(it) },
            animeSeason = text("animeSeason")?.let { AnimeSeason.valueOf(it) },
            abandonedProgress = integer("abandonedProgress"), progress = number("progress"),
            otherTitle = text("otherTitle"), dateRead = text("dateRead"), rereadDates = strings("rereadDates"),
            readingPeriods = lists.jsonToPeriods(json.getJSONArray("readingPeriods").toString()),
            unitProgress = units, activeUnitIndex = integer("activeUnitIndex"), note = text("note"),
            link = text("link"), link2 = text("link2"), links = strings("links"), author = text("author"),
            updatedAt = if (json.isNull("updatedAt")) null else json.getLong("updatedAt"),
            genres = strings("genres"), tags = strings("tags")
        )
    }
}
