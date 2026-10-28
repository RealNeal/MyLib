package com.rn.library.data

import android.content.Context
import com.rn.library.parser.GenreTagTranslator
import com.rn.library.parser.ParserUtils
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Управление пользовательскими жанрами, тегами и правилами их слияния (синонимами).
 */
object GenreTagManager {
    private const val PREFS_NAME = "genre_tag_preferences"
    private const val KEY_CUSTOM_GENRES = "custom_genres"
    private const val KEY_CUSTOM_TAGS = "custom_tags"
    private const val KEY_CUSTOM_SYNONYMS = "custom_synonyms"

    private val cachedSynonyms = ConcurrentHashMap<String, String>()
    @Volatile
    private var isLoaded = false

    private fun normalizeKey(s: String): String =
        s.trim().lowercase().replace('ё', 'е').replace(Regex("""[-_\s]+"""), " ")

    /**
     * Сравнивает два названия тега или жанра с учётом регистра, символов '#', '@',
     * знаков препинания, 'ё'/'е' и пробелов/дефисов.
     */
    fun matchesTagOrGenre(item: String, target: String): Boolean {
        val trimmedItem = item.trim()
        val trimmedTarget = target.trim()
        if (trimmedItem.isEmpty() || trimmedTarget.isEmpty()) return false
        if (trimmedItem.equals(trimmedTarget, ignoreCase = true)) return true

        val cleanItem = GenreTagTranslator.cleanTag(trimmedItem).trim()
        val cleanTarget = GenreTagTranslator.cleanTag(trimmedTarget).trim()
        if (cleanItem.isEmpty() || cleanTarget.isEmpty()) return false
        if (cleanItem.equals(cleanTarget, ignoreCase = true)) return true
        if (cleanItem.equals(trimmedTarget, ignoreCase = true)) return true
        if (trimmedItem.equals(cleanTarget, ignoreCase = true)) return true

        val normItem = normalizeKey(cleanItem)
        val normTarget = normalizeKey(cleanTarget)
        return normItem.isNotEmpty() && normItem == normTarget
    }

    fun init(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_CUSTOM_SYNONYMS, null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val json = JSONObject(jsonStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        cachedSynonyms[normalizeKey(k)] = json.getString(k)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoaded = true
        }
    }

    fun getCustomSynonym(normalizedKey: String): String? {
        return cachedSynonyms[normalizedKey]
    }

    fun getCustomGenres(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_CUSTOM_GENRES, emptySet()) ?: emptySet()
    }

    fun addCustomGenre(context: Context, genre: String): Boolean {
        val clean = GenreTagTranslator.cleanTag(genre).trim()
        if (clean.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_CUSTOM_GENRES, emptySet()) ?: emptySet()).toMutableSet()
        val canonical = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        current.removeAll { matchesTagOrGenre(it, canonical) }
        current.add(canonical)
        prefs.edit().putStringSet(KEY_CUSTOM_GENRES, current).apply()
        return true
    }

    fun removeCustomGenre(context: Context, genre: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_CUSTOM_GENRES, emptySet()) ?: emptySet()).toMutableSet()
        current.removeAll { matchesTagOrGenre(it, genre) }
        prefs.edit().putStringSet(KEY_CUSTOM_GENRES, current).apply()
    }

    fun getCustomTags(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_CUSTOM_TAGS, emptySet()) ?: emptySet()
    }

    fun addCustomTag(context: Context, tag: String): Boolean {
        val clean = GenreTagTranslator.cleanTag(tag).trim()
        if (clean.isBlank()) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_CUSTOM_TAGS, emptySet()) ?: emptySet()).toMutableSet()
        val canonical = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        current.removeAll { matchesTagOrGenre(it, canonical) }
        current.add(canonical)
        prefs.edit().putStringSet(KEY_CUSTOM_TAGS, current).apply()
        return true
    }

    fun removeCustomTag(context: Context, tag: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getStringSet(KEY_CUSTOM_TAGS, emptySet()) ?: emptySet()).toMutableSet()
        current.removeAll { matchesTagOrGenre(it, tag) }
        prefs.edit().putStringSet(KEY_CUSTOM_TAGS, current).apply()
    }

    fun addSynonym(context: Context, source: String, target: String) {
        init(context)
        val cleanSrc = GenreTagTranslator.cleanTag(source).trim()
        val normSource = normalizeKey(cleanSrc)
        val canonicalTarget = GenreTagTranslator.cleanTag(target).trim().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        if (normSource.isNotBlank() && canonicalTarget.isNotBlank()) {
            cachedSynonyms[normSource] = canonicalTarget

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = JSONObject()
            cachedSynonyms.forEach { (k, v) -> json.put(k, v) }
            prefs.edit().putString(KEY_CUSTOM_SYNONYMS, json.toString()).apply()
        }
    }

    fun removeSynonym(context: Context, source: String) {
        init(context)
        val cleanSrc = GenreTagTranslator.cleanTag(source).trim()
        val normSource = normalizeKey(cleanSrc)
        cachedSynonyms.remove(normSource)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject()
        cachedSynonyms.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_CUSTOM_SYNONYMS, json.toString()).apply()
    }

    fun getSynonymRules(context: Context): Map<String, String> {
        init(context)
        return cachedSynonyms.toMap()
    }

    /**
     * Слияние жанра: заменяет source на target во всех произведениях библиотеки
     * и сохраняет синоним для будущих импортов.
     */
    fun mergeGenres(
        context: Context,
        source: String,
        target: String,
        updateWorks: Boolean,
        repository: WorkRepository = WorkRepository(context)
    ): Int {
        val cleanSource = GenreTagTranslator.cleanTag(source).trim()
        val cleanTarget = GenreTagTranslator.cleanTag(target).trim().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        if (cleanSource.isBlank() || cleanTarget.isBlank()) {
            return 0
        }
        // Если полностью идентичны по тексту и регистру, слияние не требуется
        if (cleanSource == cleanTarget && source.trim() == target.trim()) {
            return 0
        }

        addSynonym(context, cleanSource, cleanTarget)
        addCustomGenre(context, cleanTarget)
        removeCustomGenre(context, cleanSource)
        removeCustomGenre(context, source.trim())

        var updatedCount = 0
        if (updateWorks) {
            val works = repository.getAllWorks()
            for (work in works) {
                var modified = false
                val newGenres = mutableListOf<String>()
                for (chip in work.genres) {
                    val subChips = if (chip.contains(",") || chip.contains(";") || chip.contains("/")) {
                        chip.split(Regex("""[,;/]+""")).map { it.trim() }.filter { it.isNotBlank() }
                    } else {
                        listOf(chip)
                    }
                    for (sub in subChips) {
                        if (matchesTagOrGenre(sub, source) || matchesTagOrGenre(sub, cleanSource)) {
                            newGenres.add(cleanTarget)
                            modified = true
                        } else {
                            val cleanSub = GenreTagTranslator.cleanTag(sub).trim()
                            if (cleanSub.isNotBlank()) {
                                newGenres.add(cleanSub.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                            }
                        }
                    }
                }
                if (modified) {
                    val deduplicated = ParserUtils.normalizeChips(newGenres)
                    repository.saveWork(work.copy(genres = deduplicated), preserveUpdatedAt = true, recordActivity = false)
                    updatedCount++
                }
            }
        }
        return updatedCount
    }

    /**
     * Слияние тега: заменяет source на target во всех произведениях библиотеки
     * и сохраняет синоним для будущих импортов.
     */
    fun mergeTags(
        context: Context,
        source: String,
        target: String,
        updateWorks: Boolean,
        repository: WorkRepository = WorkRepository(context)
    ): Int {
        val cleanSource = GenreTagTranslator.cleanTag(source).trim()
        val cleanTarget = GenreTagTranslator.cleanTag(target).trim().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        if (cleanSource.isBlank() || cleanTarget.isBlank()) {
            return 0
        }
        // Если полностью идентичны по тексту и регистру, слияние не требуется
        if (cleanSource == cleanTarget && source.trim() == target.trim()) {
            return 0
        }

        addSynonym(context, cleanSource, cleanTarget)
        addCustomTag(context, cleanTarget)
        removeCustomTag(context, cleanSource)
        removeCustomTag(context, source.trim())

        var updatedCount = 0
        if (updateWorks) {
            val works = repository.getAllWorks()
            for (work in works) {
                var modified = false
                val newTags = mutableListOf<String>()
                for (chip in work.tags) {
                    val subChips = if (chip.contains(",") || chip.contains(";") || chip.contains("/")) {
                        chip.split(Regex("""[,;/]+""")).map { it.trim() }.filter { it.isNotBlank() }
                    } else {
                        listOf(chip)
                    }
                    for (sub in subChips) {
                        if (matchesTagOrGenre(sub, source) || matchesTagOrGenre(sub, cleanSource)) {
                            newTags.add(cleanTarget)
                            modified = true
                        } else {
                            val cleanSub = GenreTagTranslator.cleanTag(sub).trim()
                            if (cleanSub.isNotBlank()) {
                                newTags.add(cleanSub.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                            }
                        }
                    }
                }
                if (modified) {
                    val deduplicated = ParserUtils.normalizeChips(newTags)
                    repository.saveWork(work.copy(tags = deduplicated), preserveUpdatedAt = true, recordActivity = false)
                    updatedCount++
                }
            }
        }
        return updatedCount
    }

    /**
     * Удаление жанра (с возможностью убрать его из всех произведений).
     */
    fun deleteGenre(
        context: Context,
        genre: String,
        removeFromWorks: Boolean,
        repository: WorkRepository = WorkRepository(context)
    ): Int {
        removeCustomGenre(context, genre)
        var updatedCount = 0
        if (removeFromWorks) {
            val works = repository.getAllWorks()
            for (work in works) {
                var modified = false
                val newGenres = mutableListOf<String>()
                for (chip in work.genres) {
                    val subChips = if (chip.contains(",") || chip.contains(";") || chip.contains("/")) {
                        chip.split(Regex("""[,;/]+""")).map { it.trim() }.filter { it.isNotBlank() }
                    } else {
                        listOf(chip)
                    }
                    for (sub in subChips) {
                        if (matchesTagOrGenre(sub, genre)) {
                            modified = true
                        } else {
                            val cleanSub = GenreTagTranslator.cleanTag(sub).trim()
                            if (cleanSub.isNotBlank()) {
                                newGenres.add(cleanSub.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                            }
                        }
                    }
                }
                if (modified) {
                    val deduplicated = ParserUtils.normalizeChips(newGenres)
                    repository.saveWork(work.copy(genres = deduplicated), preserveUpdatedAt = true, recordActivity = false)
                    updatedCount++
                }
            }
        }
        return updatedCount
    }

    /**
     * Удаление тега (с возможностью убрать его из всех произведений).
     */
    fun deleteTag(
        context: Context,
        tag: String,
        removeFromWorks: Boolean,
        repository: WorkRepository = WorkRepository(context)
    ): Int {
        removeCustomTag(context, tag)
        var updatedCount = 0
        if (removeFromWorks) {
            val works = repository.getAllWorks()
            for (work in works) {
                var modified = false
                val newTags = mutableListOf<String>()
                for (chip in work.tags) {
                    val subChips = if (chip.contains(",") || chip.contains(";") || chip.contains("/")) {
                        chip.split(Regex("""[,;/]+""")).map { it.trim() }.filter { it.isNotBlank() }
                    } else {
                        listOf(chip)
                    }
                    for (sub in subChips) {
                        if (matchesTagOrGenre(sub, tag)) {
                            modified = true
                        } else {
                            val cleanSub = GenreTagTranslator.cleanTag(sub).trim()
                            if (cleanSub.isNotBlank()) {
                                newTags.add(cleanSub.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
                            }
                        }
                    }
                }
                if (modified) {
                    val deduplicated = ParserUtils.normalizeChips(newTags)
                    repository.saveWork(work.copy(tags = deduplicated), preserveUpdatedAt = true, recordActivity = false)
                    updatedCount++
                }
            }
        }
        return updatedCount
    }
}
