package com.rn.library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Environment
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rn.library.parser.ParserUtils

data class BookImportResult(
    val work: Work? = null,
    val errorMessage: String? = null
)

class WorkRepository(val context: Context) {
    private val activityDeltaLog = ActivityDeltaLog(context)

    private data class TreeDocument(
        val uri: Uri,
        val name: String,
        val relativePath: String
    )
    private val worksDirectory: File
        get() {
            // Use external files directory so it's visible in Android/data
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val dir = File(baseDir, "works")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun getAllWorks(): List<Work> {
        val works = mutableListOf<Work>()
        val files = worksDirectory.listFiles { _, name -> name.endsWith(".md") }
        files?.forEach { file ->
            try {
                val work = parseMarkdownFile(file)
                work?.let { parsed ->
                    val withCovers = persistWorkCovers(parsed)
                    if (withCovers != parsed) {
                        saveWork(withCovers, preserveUpdatedAt = true, recordActivity = false)
                    }
                    works.add(withCovers)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return works
    }

    /** Копирует обложку из пикера в каталог приложения и возвращает абсолютный путь к файлу. */
    fun persistCoverFromPicker(uri: Uri, workId: String, index: Int): String? =
        saveCoverToStorage(uri, workId, index)

    /** Скачивает обложку по URL и сохраняет в каталог covers/, возвращая абсолютный путь. */
    suspend fun downloadCoverToStorage(imageUrl: String, workId: String, index: Int): String? =
        withContext(Dispatchers.IO) {
            try {
                val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
                val coversDir = File(baseDir, "covers").apply { mkdirs() }
                val outputFile = File(coversDir, "${workId}_$index.jpg")

                val upgradedUrl = ParserUtils.upgradeCoverUrl(imageUrl)
                val urlsToTry = if (!upgradedUrl.isNullOrBlank() && upgradedUrl != imageUrl) {
                    listOf(upgradedUrl, imageUrl)
                } else {
                    listOf(imageUrl)
                }

                for (targetUrl in urlsToTry) {
                    var currentUrl = targetUrl
                    var redirects = 0
                    var downloaded = false

                    while (redirects < 5) {
                        val connection = URL(currentUrl).openConnection() as HttpURLConnection
                        val referer = when {
                            currentUrl.contains("imglib") || currentUrl.contains("cdnlibs") || currentUrl.contains("lib.social") || currentUrl.contains("mangalib") -> {
                                when {
                                    currentUrl.contains("ranobelib") || currentUrl.contains("ranobe") -> "https://ranobelib.me/"
                                    currentUrl.contains("animelib") || currentUrl.contains("anime") -> "https://animelib.me/"
                                    else -> "https://mangalib.me/"
                                }
                            }
                            currentUrl.contains("ranobelib") -> "https://ranobelib.me/"
                            currentUrl.contains("animelib") -> "https://animelib.me/"
                            currentUrl.contains("anixmirai") || currentUrl.contains("anixart") -> "https://anixart.tv/"
                            currentUrl.contains("remanga.org") -> "https://remanga.org/"
                            currentUrl.contains("shikimori") -> "https://shikimori.one/"
                            currentUrl.contains("wtr-lab") -> "https://wtr-lab.com/"
                            currentUrl.contains("ranobehub") -> "https://ranobehub.org/"
                            currentUrl.contains("ranobes") -> "https://ranobes.net/"
                            currentUrl.contains("royalroad") -> "https://www.royalroad.com/"
                            currentUrl.contains("scribblehub") -> "https://www.scribblehub.com/"
                            currentUrl.contains("rulate") || currentUrl.contains("tl.rulate.ru") -> "https://tl.rulate.ru/"
                            currentUrl.contains("media-amazon.com") || currentUrl.contains("imdb.com") -> "https://www.imdb.com/"
                            currentUrl.contains("mangabuff") -> "https://mangabuff.ru/"
                            else -> null
                        }
                        if (referer != null) {
                            connection.setRequestProperty("Referer", referer)
                        }
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                        connection.connectTimeout = 12000
                        connection.readTimeout = 20000
                        connection.instanceFollowRedirects = true
                        val code = connection.responseCode
                        if (code in 300..399) {
                            val newLoc = connection.getHeaderField("Location") ?: break
                            currentUrl = if (newLoc.startsWith("http")) newLoc else URL(URL(currentUrl), newLoc).toString()
                            redirects++
                            continue
                        }
                        if (code in 200..299) {
                            connection.inputStream.use { input ->
                                FileOutputStream(outputFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            downloaded = true
                            break
                        } else {
                            break
                        }
                    }

                    if (downloaded && outputFile.exists() && outputFile.length() > 0) {
                        optimizeCoverFile(outputFile)
                        return@withContext outputFile.absolutePath
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Заменяет временные content:// URI на файлы в каталоге covers/.
     * Уже сохранённые абсолютные пути не трогает.
     */
    fun persistWorkCovers(work: Work): Work {
        val orderedPaths = work.allCoverPaths()
        if (orderedPaths.isEmpty()) return work

        val persistedPaths = orderedPaths.mapIndexed { index, path ->
            persistSingleCoverPath(path, work.id, index)
        }

        val primaryIndex = work.coverPath
            ?.let { orderedPaths.indexOf(it).takeIf { idx -> idx >= 0 } }
            ?: 0
        val primary = persistedPaths.getOrElse(primaryIndex) { persistedPaths.first() }

        val additional = persistedPaths.filterIndexed { index, _ -> index != primaryIndex }
        val updated = work.copy(coverPath = primary, coverPaths = additional)
        return if (updated.coverPath == work.coverPath && updated.coverPaths == work.coverPaths) work else updated
    }

    private fun persistSingleCoverPath(path: String, workId: String, index: Int): String {
        if (path.startsWith("/")) {
            val file = File(path)
            if (file.exists() && file.isFile) return path
        }
        if (needsPersisting(path)) {
            val uri = Uri.parse(path)
            saveCoverToStorage(uri, workId, index)?.let { return it }
        }
        return path
    }

    private fun needsPersisting(path: String): Boolean {
        if (path.startsWith("content://") || path.startsWith("file://")) return true
        if (path.startsWith("/")) {
            val file = File(path)
            return !file.exists()
        }
        return true
    }

    private fun saveCoverToStorage(uri: Uri, workId: String, index: Int): String? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val coversDir = File(baseDir, "covers").apply { mkdirs() }
            val outputFile = File(coversDir, "${workId}_$index.jpg")
            val compressed = compressImage(uri, context)
            if (compressed != null) {
                FileOutputStream(outputFile).use { it.write(compressed) }
                outputFile.absolutePath
            } else {
                copyCoverFromDocument(context, uri, workId, index)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** События прироста прогресса по дням (для heatmap / графиков активности). */
    fun loadActivityEvents(): List<ActivityDeltaEvent> = activityDeltaLog.loadEvents()

    fun getWorkById(workId: String): Work? {
        val file = File(worksDirectory, "$workId.md")
        return if (file.exists()) parseMarkdownFile(file) else null
    }

    /** Прирост единиц прогресса между сохранённым и новым состоянием произведения. */
    fun calculateProgressDelta(previous: Work?, saved: Work): Pair<Double, Double> {
        val oldRead = previous?.let { activityReadProgressUnits(it) } ?: 0.0
        val newRead = activityReadProgressUnits(saved)
        var readDelta = newRead - oldRead
        val oldWatch = previous?.let { activityWatchedProgressUnits(it) } ?: 0.0
        val newWatch = activityWatchedProgressUnits(saved)
        var watchDelta = newWatch - oldWatch
        if (previous != null && previous.status != saved.status) {
            // Смена статуса на «Прочитано»/«Просмотрено» без поштучного учёта не должна
            // добавлять в heatmap весь объём произведения разом.
            if (saved.type in setOf(WorkType.BOOK, WorkType.MANGA) &&
                saved.status == WorkStatus.READ &&
                saved.unitProgress.isEmpty()
            ) {
                readDelta = 0.0
            }
            if (saved.type in setOf(WorkType.ANIME, WorkType.SERIES) &&
                saved.status == WorkStatus.WATCHED &&
                saved.unitProgress.isEmpty()
            ) {
                watchDelta = 0.0
            }
            // Смена с «Прочитано»/«Просмотрено» не должна списывать ранее записанную активность.
            if (previous.type in setOf(WorkType.BOOK, WorkType.MANGA) &&
                previous.status == WorkStatus.READ &&
                saved.status != WorkStatus.READ
            ) {
                readDelta = 0.0
            }
            if (previous.type in setOf(WorkType.ANIME, WorkType.SERIES) &&
                previous.status == WorkStatus.WATCHED &&
                saved.status != WorkStatus.WATCHED
            ) {
                watchDelta = 0.0
            }
        }
        return readDelta to watchDelta
    }

    /** РќСѓР¶РЅРѕ Р»Рё СЃРїСЂР°С€РёРІР°С‚СЊ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ Рѕ Р·Р°РїРёСЃРё РІ Heatmap / В«РђРєС‚РёРІРЅРѕСЃС‚СЊ РїРѕ РїРµСЂРёРѕРґР°РјВ». */
    fun shouldConfirmLargeActivityDelta(previous: Work?, saved: Work): Boolean {
        val (readDelta, watchDelta) = calculateProgressDelta(previous, saved)
        if (readDelta == 0.0 && watchDelta == 0.0) return false
        return when (saved.type) {
            WorkType.BOOK, WorkType.MANGA -> kotlin.math.abs(readDelta) > LARGE_ACTIVITY_DELTA_THRESHOLD
            WorkType.ANIME, WorkType.SERIES -> kotlin.math.abs(watchDelta) > LARGE_ACTIVITY_DELTA_THRESHOLD
        }
    }

    fun importActivityStatisticsFromUri(context: Context, uri: Uri): Int {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return 0
            importActivityStatisticsFromText(text)
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun importActivityStatisticsFromFile(file: File): Int = activityDeltaLog.importFromFile(file)

    private fun importActivityStatisticsFromText(text: String): Int {
        val events = ActivityStatisticsFormat.parseImportText(text)
        if (text.contains("format=${ActivityStatisticsFormat.FORMAT_VERSION}") ||
            text.contains("[Heatmap]")
        ) {
            activityDeltaLog.replaceAllEvents(events)
        } else {
            activityDeltaLog.mergeEvents(events)
        }
        return events.size
    }

    fun saveWork(
        work: Work,
        preserveUpdatedAt: Boolean = false,
        recordActivity: Boolean = true
    ): Boolean {
        return try {
            val workWithPersistedCovers = persistWorkCovers(work)
            val filename = "${workWithPersistedCovers.id}.md"
            val file = File(worksDirectory, filename)
            val previous = if (file.exists()) parseMarkdownFile(file) else null
            val now = System.currentTimeMillis()
            val workWithTimestamp = if (preserveUpdatedAt && workWithPersistedCovers.updatedAt != null) {
                workWithPersistedCovers
            } else {
                workWithPersistedCovers.copy(updatedAt = now)
            }
            val content = workToMarkdown(workWithTimestamp)
            file.writeText(content)
            if (recordActivity) {
                recordProgressDelta(previous, workWithTimestamp)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun recordProgressDelta(previous: Work?, saved: Work) {
        val (readDelta, watchDelta) = calculateProgressDelta(previous, saved)
        if (readDelta == 0.0 && watchDelta == 0.0) return

        val today = LocalDate.now(ZoneId.systemDefault())

        val positiveRead = if (readDelta > 0.0) readDelta else 0.0
        val negativeRead = if (readDelta < 0.0) -readDelta else 0.0

        val positiveWatch = if (watchDelta > 0.0) watchDelta else 0.0
        val negativeWatch = if (watchDelta < 0.0) -watchDelta else 0.0

        if (positiveRead > 0.0 || positiveWatch > 0.0) {
            activityDeltaLog.appendEvent(
                ActivityDeltaEvent(
                    date = today,
                    workId = saved.id,
                    readDelta = positiveRead,
                    watchDelta = positiveWatch
                )
            )
        }

        if (negativeRead > 0.0 || negativeWatch > 0.0) {
            val fallbackDate = previous?.updatedAt?.let {
                java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            } ?: today
            activityDeltaLog.deductEventProgress(
                workId = saved.id,
                readDeduct = negativeRead,
                watchDeduct = negativeWatch,
                fallbackDate = fallbackDate
            )
        }
    }

    fun deleteWork(workId: String): Boolean {
        return try {
            val file = File(worksDirectory, "$workId.md")
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private data class ExportCoverHints(
        val exportBase: String? = null,
        val exportCoverNames: List<String> = emptyList(),
    )

    private fun workToMarkdown(
        work: Work,
        exportHints: ExportCoverHints? = null,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("---")
        sb.appendLine("id: ${work.id}")
        sb.appendLine("title: ${escapeMarkdown(work.title)}")
        sb.appendLine("type: ${work.type.name}")
        sb.appendLine("status: ${work.status.name}")
        if (exportHints != null) {
            exportHints.exportBase?.let {
                sb.appendLine("exportBase: ${escapeMarkdown(it)}")
            }
            if (exportHints.exportCoverNames.isNotEmpty()) {
                sb.appendLine("exportCovers: ${encodeList(exportHints.exportCoverNames)}")
            }
        } else {
            work.coverPath?.let {
                sb.appendLine("cover: $it")
            }
            if (work.coverPaths.isNotEmpty()) {
                sb.appendLine("covers: ${encodeList(work.coverPaths)}")
            }
        }
        work.chapters?.let {
            sb.appendLine("chapters: ${formatDoubleForDisplay(it)}")
        }
        work.volumes?.let {
            sb.appendLine("volumes: ${formatDoubleForDisplay(it)}")
        }
        work.bookChapters?.let {
            sb.appendLine("bookChapters: ${formatDoubleForDisplay(it)}")
        }
        work.episodes?.let {
            sb.appendLine("episodes: ${formatDoubleForDisplay(it)}")
        }
        work.seasons?.let {
            sb.appendLine("seasons: $it")
        }
        work.dateRead?.takeIf { it.isNotBlank() && it != "null" }?.let {
            sb.appendLine("dateRead: $it")
        }
        if (work.rereadDates.isNotEmpty()) {
            sb.appendLine("rereadDates: ${encodeList(work.rereadDates)}")
        }
        if (work.readingPeriods.isNotEmpty()) {
            sb.appendLine("readingPeriods: ${encodePeriods(work.readingPeriods)}")
        }
        if (work.unitProgress.isNotEmpty()) {
            sb.appendLine("unitProgress: ${encodeUnitProgress(work.unitProgress)}")
        }
        work.activeUnitIndex?.let {
            sb.appendLine("activeUnitIndex: $it")
        }
        work.year?.let {
            sb.appendLine("year: $it")
        }
        work.yearPeriod?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("yearPeriod: ${escapeMarkdown(it)}")
        }
        work.country?.let {
            sb.appendLine("country: ${escapeMarkdown(it)}")
        }
        work.seriesType?.let {
            sb.appendLine("seriesType: ${it.name}")
        }
        work.mangaType?.let {
            sb.appendLine("mangaType: ${it.name}")
        }
        work.animeSeason?.let {
            sb.appendLine("animeSeason: ${it.name}")
        }
        work.abandonedProgress?.let {
            sb.appendLine("abandonedProgress: $it")
        }
        work.progress?.let {
            sb.appendLine("progress: ${formatDoubleForDisplay(it)}")
        }
        work.otherTitle?.let {
            sb.appendLine("otherTitle: ${escapeMarkdown(it)}")
        }
        work.note?.let {
            sb.appendLine("note: ${escapeMarkdown(it)}")
        }
        val allLinks = work.allLinks()
        if (allLinks.isNotEmpty()) {
            sb.appendLine("links: ${encodeList(allLinks)}")
        }
        allLinks.getOrNull(0)?.let {
            // Не применяем escapeMarkdown к ссылкам, чтобы избежать проблем с обратными слэшами
            sb.appendLine("link: $it")
        }
        allLinks.getOrNull(1)?.let {
            // Вторая ссылка хранится отдельно
            sb.appendLine("link2: $it")
        }
        work.author?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("author: ${escapeMarkdown(it)}")
        }
        if (work.genres.isNotEmpty()) {
            sb.appendLine("genres: ${encodeList(work.genres)}")
        }
        if (work.tags.isNotEmpty()) {
            sb.appendLine("tags: ${encodeList(work.tags)}")
        }
        work.updatedAt?.let {
            sb.appendLine("updatedAt: $it")
        }
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine(work.description)
        return sb.toString()
    }

    private fun parseMarkdownFile(file: File): Work? {
        return try {
            parseMarkdownContent(file.readText())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseMarkdownContent(content: String): Work? {
        return try {
            val frontMatterRegex = Regex("---\\s*\\n([\\s\\S]*?)\\n---")
            val match = frontMatterRegex.find(content)
            if (match == null) return null

            val frontMatter = match.groupValues[1]
            val properties = frontMatter.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .associate { line ->
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val key = line.substring(0, colonIndex).trim()
                        val value = unescapeMarkdown(line.substring(colonIndex + 1).trim())
                        key to value
                    } else {
                        "" to ""
                    }
                }

            val id = properties["id"] ?: return null
            val title = properties["title"] ?: ""
            val type = WorkType.valueOf(properties["type"] ?: "BOOK")
            val status = WorkStatus.valueOf(properties["status"] ?: "IN_PLANS")
            val coverPath = properties["cover"]
            val coverPaths = decodeList(properties["covers"])
            val chapters = properties["chapters"]?.toDoubleOrNull()
            val volumes = properties["volumes"]?.toDoubleOrNull()
            val bookChapters = properties["bookChapters"]?.toDoubleOrNull()
            val episodes = properties["episodes"]?.toDoubleOrNull()
            val seasons = properties["seasons"]?.toIntOrNull()
            val year = properties["year"]?.toIntOrNull()
            val yearPeriod = properties["yearPeriod"]
            val country = properties["country"]
            val seriesType = properties["seriesType"]?.let { SeriesType.valueOf(it) }
            val mangaType = properties["mangaType"]?.let { MangaType.valueOf(it) }
            val animeSeason = properties["animeSeason"]?.let { AnimeSeason.valueOf(it) }
            val abandonedProgress = properties["abandonedProgress"]?.toIntOrNull()
            val progress = properties["progress"]?.toDoubleOrNull()
            val otherTitle = properties["otherTitle"]
            val dateRead = properties["dateRead"]?.takeIf { it.isNotBlank() && it != "null" }
            val rereadDates = decodeList(properties["rereadDates"])
            val readingPeriods = decodePeriods(properties["readingPeriods"])
            val unitProgress = decodeUnitProgress(properties["unitProgress"])
            val activeUnitIndex = properties["activeUnitIndex"]?.toIntOrNull()
            val note = properties["note"]
            val decodedLinks = decodeList(properties["links"])
            val fallbackLink = properties["link"]?.takeIf { it.isNotBlank() }
            val fallbackLink2 = properties["link2"]?.takeIf { it.isNotBlank() }
            val links = if (decodedLinks.isNotEmpty()) {
                decodedLinks
            } else {
                listOfNotNull(fallbackLink, fallbackLink2)
            }
            val link = links.getOrNull(0) ?: fallbackLink
            val link2 = links.getOrNull(1) ?: fallbackLink2
            val author = properties["author"]
            val genres = decodeList(properties["genres"])
            val tags = decodeList(properties["tags"])
            val updatedAt = properties["updatedAt"]?.toLongOrNull()

            val bodyStart = match.range.last + 1
            val description = content.substring(bodyStart).trim()

            Work(
                id = id,
                title = title,
                description = description,
                type = type,
                coverPath = coverPath,
                coverPaths = coverPaths,
                chapters = chapters,
                volumes = volumes,
                bookChapters = bookChapters,
                episodes = episodes,
                seasons = seasons,
                year = year,
                yearPeriod = yearPeriod,
                country = country,
                status = status,
                seriesType = seriesType,
                mangaType = mangaType,
                animeSeason = animeSeason,
                abandonedProgress = abandonedProgress,
                progress = progress,
                otherTitle = otherTitle,
                dateRead = dateRead,
                rereadDates = rereadDates,
                readingPeriods = readingPeriods,
                unitProgress = unitProgress,
                activeUnitIndex = activeUnitIndex,
                note = note,
                link = link,
                link2 = link2,
                links = links,
                author = author,
                updatedAt = updatedAt,
                genres = genres,
                tags = tags
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Импорт одного файла бэкапа приложения (.md с YAML‑шапкой), как после экспорта.
     */
    fun importMarkdownBackup(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader().readText()
                val work = parseMarkdownContent(text) ?: return false
                saveWork(work, preserveUpdatedAt = true, recordActivity = false)
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importExportedBackupsFromTree(
        context: Context,
        treeUri: Uri,
        booksFolder: String = "Книги",
        animeFolder: String = "Аниме",
        mangaFolder: String = "Манга",
        seriesFolder: String = "Сериалы",
        bookCoversFolder: String = "Обложки книг",
        animeCoversFolder: String = "Обложки аниме",
        mangaCoversFolder: String = "Обложки манги",
        seriesCoversFolder: String = "Обложки сериалов",
    ): Triple<Int, Int, List<String>> {
        val errors = mutableListOf<String>()
        return try {
            val allDocs = collectTreeDocuments(context, treeUri)
            val markdownDocs = allDocs.filter { it.name.endsWith(".md", ignoreCase = true) }
            val imageDocs = allDocs.filter { isImageName(it.name) }
            var imported = 0
            allDocs
                .filter { isActivityStatisticsDocument(context, it) }
                .distinctBy { it.uri.toString() }
                .forEach { statsDoc ->
                    importActivityStatisticsFromUri(context, statsDoc.uri)
                }

            markdownDocs.forEach { doc ->
                try {
                    val text = context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.use { it.readText() }
                    if (text.isNullOrBlank()) {
                        errors += "${doc.name} (пустой файл)"
                        return@forEach
                    }
                    val parsed = parseMarkdownContent(text)
                    if (parsed == null) {
                        errors += "${doc.name} (некорректный markdown)"
                        return@forEach
                    }
                    val hints = parseExportCoverHints(text)
                    val exportBase = hints.exportBase ?: doc.name.substringBeforeLast('.')
                    val importedCoverPaths = importCoversFromDocuments(
                        context = context,
                        exportBase = exportBase,
                        exportCoverNames = hints.exportCoverNames,
                        imageDocs = imageDocs,
                        workId = parsed.id,
                        workType = parsed.type,
                    )
                    val workToSave = applyImportedCovers(parsed, importedCoverPaths)
                    if (saveWork(workToSave, preserveUpdatedAt = true, recordActivity = false)) {
                        imported++
                    } else {
                        errors += "${doc.name} (не удалось сохранить)"
                    }
                } catch (e: Exception) {
                    errors += "${doc.name} (${e.message ?: "ошибка"})"
                }
            }
            Triple(imported, markdownDocs.size, errors)
        } catch (e: Exception) {
            Triple(0, 0, listOf(e.message ?: "Ошибка чтения папки"))
        }
    }

    /**
     * Импорт книги FB2 / EPUB / PDF: копирует файл в хранилище приложения и создаёт произведение типа «Книга».
     */
    fun importBookFromUri(context: Context, uri: Uri): Work? {
        return importBookFromUriDetailed(context, uri).work
    }

    fun importBookFromUriDetailed(context: Context, uri: Uri): BookImportResult {
        return try {
            val cr = context.contentResolver
            val displayName = queryDisplayName(context, uri) ?: "book.bin"
            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.getDefault())
            val destDir = File(context.filesDir, "imported_books").apply { mkdirs() }
            val destFile = File(destDir, "${UUID.randomUUID()}_$displayName")
            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            } ?: return BookImportResult(errorMessage = "Не удалось открыть файл: $displayName")
            if (!destFile.exists() || destFile.length() <= 0L) {
                return BookImportResult(errorMessage = "Файл пустой или не был скопирован: $displayName")
            }

            val preview = when (ext) {
                "fb2" -> BookFormatImporter.fromFb2(destFile)
                "epub" -> BookFormatImporter.fromEpub(destFile)
                "pdf" -> BookFormatImporter.fromPdf(destFile)
                "zip" -> previewFromZipArchive(destFile)
                else -> {
                    val magic = ByteArray(4)
                    destFile.inputStream().use { it.read(magic) }
                    val isZip = magic.size >= 2 &&
                            magic[0] == 0x50.toByte() &&
                            magic[1] == 0x4B.toByte()
                    when {
                        isZip -> previewFromZipArchive(destFile) ?: BookFormatImporter.fromEpub(destFile)
                        else -> BookFormatImporter.fromFb2(destFile) ?: BookFormatImporter.fromPdf(destFile)
                    }
                }
            }

            val fallbackTitle = displayName.substringBeforeLast('.', displayName)
            val title = preview?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val description = preview?.description?.takeIf { it.isNotBlank() } ?: ""

            val work = Work(
                id = generateId(),
                title = title,
                description = description,
                author = preview?.author,
                type = WorkType.BOOK,
                status = WorkStatus.IN_PLANS,
                link = destFile.absolutePath
            )
            if (saveWork(work, recordActivity = false)) {
                BookImportResult(work = work)
            } else {
                BookImportResult(errorMessage = "Не удалось сохранить произведение: $displayName")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BookImportResult(errorMessage = e.message ?: "Ошибка импорта")
        }
    }

    private fun previewFromZipArchive(zipFile: File): BookImportPreview? {
        return try {
            ZipFile(zipFile).use { zip ->
                // EPUB в ZIP чаще всего содержит META-INF/container.xml
                if (zip.getEntry("META-INF/container.xml") != null) {
                    return BookFormatImporter.fromEpub(zipFile)
                }
                // Ищем упакованный FB2, если это fb2.zip
                var fb2Entry: java.util.zip.ZipEntry? = null
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                        fb2Entry = entry
                        break
                    }
                }
                if (fb2Entry == null) return null
                val fb2Temp = File(context.cacheDir, "tmp_fb2_${UUID.randomUUID()}.fb2")
                zip.getInputStream(fb2Entry).use { input ->
                    FileOutputStream(fb2Temp).use { output -> input.copyTo(output) }
                }
                try {
                    BookFormatImporter.fromFb2(fb2Temp)
                } finally {
                    fb2Temp.delete()
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Импортирует ранее экспортированные .md из Downloads/MyLibrary и его подкаталогов.
     * Возвращает Pair(успешно импортировано, найдено .md файлов).
     */
    fun importExportedBackupsFromDownloads(
        context: Context,
        booksFolder: String = "Книги",
        animeFolder: String = "Аниме",
        mangaFolder: String = "Манга",
        seriesFolder: String = "Сериалы",
        bookCoversFolder: String = "Обложки книг",
        animeCoversFolder: String = "Обложки аниме",
        mangaCoversFolder: String = "Обложки манги",
        seriesCoversFolder: String = "Обложки сериалов"
    ): Pair<Int, Int> {
        return try {
            val externalRoot = Environment.getExternalStorageDirectory()
            val candidateDownloadsDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                File(externalRoot, "Download"),
                File(externalRoot, "Downloads"),
                File("/storage/emulated/0/Download"),
                File("/storage/emulated/0/Downloads"),
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                context.getExternalFilesDir(null)?.let { File(it, "Download") }
            ).distinctBy { it.absolutePath }

            val directExportDirs = candidateDownloadsDirs
                .map { File(it, "MyLibrary") }
                .filter { it.exists() && it.isDirectory }

            // Ищем папку экспорта не только в Downloads, но и по основным корням памяти устройства.
            val scanRoots = listOf(
                File("/storage/emulated/0"),
                externalRoot,
                File("/sdcard"),
                File("/storage")
            ).distinctBy { it.absolutePath }
                .filter { it.exists() && it.isDirectory }

            val discoveredExportDirs = mutableListOf<File>()
            scanRoots.forEach { root ->
                try {
                    root.walkTopDown()
                        .maxDepth(6)
                        .forEach { file ->
                            if (file.isDirectory && file.name.equals("MyLibrary", ignoreCase = true)) {
                                discoveredExportDirs += file
                            }
                        }
                } catch (_: Exception) {
                    // skip inaccessible folders
                }
            }

            val exportDirs = (directExportDirs + discoveredExportDirs)
                .distinctBy { it.absolutePath }

            if (exportDirs.isEmpty()) {
                return 0 to 0
            }

            val candidateDirs = exportDirs
                .flatMap { exportDir ->
                    listOf(
                        exportDir,
                        File(exportDir, booksFolder),
                        File(exportDir, animeFolder),
                        File(exportDir, mangaFolder),
                        File(exportDir, seriesFolder),
                        File(exportDir, "Books"),
                        File(exportDir, "Books folder"),
                        File(exportDir, "Anime"),
                        File(exportDir, "Anime folder"),
                        File(exportDir, "Manga"),
                        File(exportDir, "Manga folder"),
                        File(exportDir, "TV Series"),
                        File(exportDir, "Series folder"),
                        File(exportDir, "Книги"),
                        File(exportDir, "Аниме"),
                        File(exportDir, "Манга"),
                        File(exportDir, "Сериалы")
                    )
                }
                .distinctBy { it.absolutePath }
                .filter { it.exists() && it.isDirectory }

            val mdFiles = candidateDirs
                .flatMap { dir ->
                    dir.listFiles { file -> file.isFile && file.name.endsWith(".md", ignoreCase = true) }
                        ?.toList()
                        .orEmpty()
                }
                .distinctBy { it.absolutePath }

            val coverDirsByType = exportDirs.associateWith { exportDir ->
                mapOf(
                    WorkType.BOOK to resolveCoverDirs(
                        exportDir,
                        bookCoversFolder,
                        *knownCoverFolderNames(WorkType.BOOK).toTypedArray()
                    ),
                    WorkType.ANIME to resolveCoverDirs(
                        exportDir,
                        animeCoversFolder,
                        *knownCoverFolderNames(WorkType.ANIME).toTypedArray()
                    ),
                    WorkType.MANGA to resolveCoverDirs(
                        exportDir,
                        mangaCoversFolder,
                        *knownCoverFolderNames(WorkType.MANGA).toTypedArray()
                    ),
                    WorkType.SERIES to resolveCoverDirs(
                        exportDir,
                        seriesCoversFolder,
                        *knownCoverFolderNames(WorkType.SERIES).toTypedArray()
                    ),
                )
            }

            var imported = 0
            mdFiles.forEach { file ->
                try {
                    val text = file.readText()
                    val work = parseMarkdownContent(text) ?: return@forEach
                    val hints = parseExportCoverHints(text)
                    val exportDir = exportDirs.firstOrNull { file.absolutePath.startsWith(it.absolutePath) }
                    val coverDirs = exportDir?.let { coverDirsByType[it]?.get(work.type) }.orEmpty()
                    val exportBase = hints.exportBase ?: file.name.substringBeforeLast('.')
                    val coverFiles = coverDirs.flatMap { dir ->
                        dir.listFiles { f -> f.isFile && isImageName(f.name) }?.toList().orEmpty()
                    }.distinctBy { it.absolutePath }
                    val importedCoverPaths = importCoversFromFiles(
                        context = context,
                        exportBase = exportBase,
                        exportCoverNames = hints.exportCoverNames,
                        coverFiles = coverFiles,
                        workId = work.id,
                    )
                    val workToSave = applyImportedCovers(work, importedCoverPaths)
                    if (saveWork(workToSave, preserveUpdatedAt = true, recordActivity = false)) {
                        imported++
                    }
                } catch (_: Exception) {
                    // ignore bad file and continue
                }
            }
            exportDirs.forEach { exportDir ->
                val statsFile = File(exportDir, ActivityDeltaLog.EXPORT_FILENAME)
                if (statsFile.exists()) {
                    importActivityStatisticsFromFile(statsFile)
                }
            }
            imported to mdFiles.size
        } catch (e: Exception) {
            e.printStackTrace()
            0 to 0
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun getFileDisplayName(context: Context, uri: Uri): String? {
        return queryDisplayName(context, uri)
    }

    private fun escapeMarkdown(text: String): String {
        return text.replace("\n", "\\n").replace(":", "\\:")
    }

    private fun unescapeMarkdown(text: String): String {
        return text.replace("\\:", ":").replace("\\n", "\n").replace("\\\\", "\\")
    }

    private fun encodeList(values: List<String>): String {
        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("||") {
                it.replace("|", "\\|").replace(":", "\\:")
            }
    }

    private fun decodeList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("||")
            .map { it.replace("\\|", "|").replace("\\:", ":").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseExportCoverHints(content: String): ExportCoverHints {
        val frontMatterRegex = Regex("---\\s*\\n([\\s\\S]*?)\\n---")
        val match = frontMatterRegex.find(content) ?: return ExportCoverHints()
        val properties = parseFrontMatterProperties(match.groupValues[1])
        return ExportCoverHints(
            exportBase = properties["exportBase"],
            exportCoverNames = decodeList(properties["exportCovers"]),
        )
    }

    private fun parseFrontMatterProperties(frontMatter: String): Map<String, String> {
        return frontMatter.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .associate { line ->
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim()
                    val value = unescapeMarkdown(line.substring(colonIndex + 1).trim())
                    key to value
                } else {
                    "" to ""
                }
            }
    }

    private fun encodePeriods(periods: List<ReadingPeriod>): String {
        return periods.joinToString("||") { period ->
            val end = period.endDate ?: ""
            "${period.startDate}..$end"
        }
    }

    private fun decodePeriods(raw: String?): List<ReadingPeriod> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("||").mapNotNull { item ->
            val start = item.substringBefore("..").trim()
            if (start.isBlank()) return@mapNotNull null
            val end = item.substringAfter("..", "").trim().ifBlank { null }
            ReadingPeriod(startDate = start, endDate = end)
        }
    }

    private fun encodeUnitProgress(items: List<UnitProgress>): String {
        return items.joinToString("||") { item ->
            val totalRaw = item.total?.toString().orEmpty()
            "${item.unitName.replace("|", "\\|")}::${item.completed}/$totalRaw"
        }
    }

    private fun decodeUnitProgress(raw: String?): List<UnitProgress> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("||").mapNotNull { row ->
            val name = row.substringBefore("::").replace("\\|", "|").trim()
            val values = row.substringAfter("::", "")
            if (name.isBlank() || values.isBlank()) return@mapNotNull null
            val completed = values.substringBefore("/").toDoubleOrNull() ?: return@mapNotNull null
            val total = values.substringAfter("/", "").toDoubleOrNull()
            UnitProgress(unitName = name, completed = completed, total = total)
        }
    }

    private fun collectTreeDocuments(context: Context, treeUri: Uri): List<TreeDocument> {
        val result = mutableListOf<TreeDocument>()
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        fun walk(parentUri: Uri, relativePath: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri,
                DocumentsContract.getDocumentId(parentUri)
            )
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    if (idIdx < 0 || nameIdx < 0 || mimeIdx < 0) continue
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    if (DocumentsContract.Document.MIME_TYPE_DIR == mime) {
                        val nextPath = if (relativePath.isBlank()) name else "$relativePath/$name"
                        walk(docUri, nextPath)
                    } else {
                        result += TreeDocument(
                            uri = docUri,
                            name = name,
                            relativePath = relativePath
                        )
                    }
                }
            }
        }

        walk(rootDocumentUri, "")
        return result
    }

    private fun isImageName(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.getDefault())
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
    }

    private fun isActivityStatisticsDocument(context: Context, doc: TreeDocument): Boolean {
        if (doc.name.equals(ActivityDeltaLog.EXPORT_FILENAME, ignoreCase = true)) return true
        if (!doc.name.endsWith(".txt", ignoreCase = true)) return false
        return looksLikeActivityStatisticsExport(context, doc.uri)
    }

    private fun looksLikeActivityStatisticsExport(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = input.bufferedReader().readText().take(4096)
                head.contains("format=${ActivityStatisticsFormat.FORMAT_VERSION}") ||
                        head.contains("[Heatmap]")
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun copyCoverFromDocument(context: Context, uri: Uri, workId: String, index: Int): String? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val coversDir = File(baseDir, "covers").apply { mkdirs() }
            val imageFile = File(coversDir, "${workId}_$index.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(imageFile).use { output -> input.copyTo(output) }
            } ?: return null
            imageFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun copyCoverFromFile(context: Context, source: File, workId: String, index: Int): String? {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val coversDir = File(baseDir, "covers").apply { mkdirs() }
            val imageFile = File(coversDir, "${workId}_$index.jpg")
            FileInputStream(source).use { input ->
                FileOutputStream(imageFile).use { output -> input.copyTo(output) }
            }
            imageFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun importCoversFromDocuments(
        context: Context,
        exportBase: String,
        exportCoverNames: List<String>,
        imageDocs: List<TreeDocument>,
        workId: String,
        workType: WorkType,
    ): List<String> {
        val scopedDocs = imageDocs.filter { doc ->
            isInKnownCoverFolder(doc.relativePath, workType) || exportCoverNames.any {
                doc.name.equals(it, ignoreCase = true)
            }
        }
        val candidates = scopedDocs.ifEmpty { imageDocs }

        if (exportCoverNames.isNotEmpty()) {
            return exportCoverNames.mapIndexedNotNull { index, fileName ->
                val doc = candidates.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
                    ?: imageDocs.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
                doc?.let { copyCoverFromDocument(context, it.uri, workId, index) }
            }
        }

        return candidates
            .mapNotNull { doc ->
                val fileBase = doc.name.substringBeforeLast('.')
                val index = parseExportCoverIndex(exportBase, fileBase) ?: return@mapNotNull null
                index to doc
            }
            .sortedBy { it.first }
            .distinctBy { it.first }
            .mapIndexedNotNull { sequentialIndex, (_, doc) ->
                copyCoverFromDocument(context, doc.uri, workId, sequentialIndex)
            }
    }

    private fun importCoversFromFiles(
        context: Context,
        exportBase: String,
        exportCoverNames: List<String>,
        coverFiles: List<File>,
        workId: String,
    ): List<String> {
        if (exportCoverNames.isNotEmpty()) {
            return exportCoverNames.mapIndexedNotNull { index, fileName ->
                val file = coverFiles.firstOrNull { it.name.equals(fileName, ignoreCase = true) }
                file?.let { copyCoverFromFile(context, it, workId, index) }
            }
        }

        return coverFiles
            .mapNotNull { file ->
                val fileBase = file.name.substringBeforeLast('.')
                val index = parseExportCoverIndex(exportBase, fileBase) ?: return@mapNotNull null
                index to file
            }
            .sortedBy { it.first }
            .distinctBy { it.first }
            .mapIndexedNotNull { sequentialIndex, (_, file) ->
                copyCoverFromFile(context, file, workId, sequentialIndex)
            }
    }

    private fun applyImportedCovers(work: Work, importedPaths: List<String>): Work {
        if (importedPaths.isEmpty()) {
            return work.copy(coverPath = null, coverPaths = emptyList())
        }
        val oldPaths = work.allCoverPaths()
        val primaryIndex = work.coverPath
            ?.let { oldPaths.indexOf(it).takeIf { idx -> idx >= 0 } }
            ?: 0
        val safePrimaryIndex = primaryIndex.coerceIn(importedPaths.indices)
        val primary = importedPaths[safePrimaryIndex]
        val additional = importedPaths.filterIndexed { index, _ -> index != safePrimaryIndex }
        return work.copy(coverPath = primary, coverPaths = additional)
    }

    private fun resolveCoverDirs(exportDir: File, vararg folderNames: String): List<File> {
        return folderNames
            .map { File(exportDir, it) }
            .distinctBy { it.absolutePath }
            .filter { it.exists() && it.isDirectory }
    }

    fun exportWorksToDownloads(
        booksFolder: String = "Книги",
        animeFolder: String = "Аниме",
        mangaFolder: String = "Манга",
        seriesFolder: String = "Сериалы",
        bookCoversFolder: String = "Обложки книг",
        animeCoversFolder: String = "Обложки аниме",
        mangaCoversFolder: String = "Обложки манги",
        seriesCoversFolder: String = "Обложки сериалов"
    ): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadsDir, "MyLibrary")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Create subdirectories for each work type
            val booksDir = File(exportDir, booksFolder)
            val animeDir = File(exportDir, animeFolder)
            val mangaDir = File(exportDir, mangaFolder)
            val seriesDir = File(exportDir, seriesFolder)
            val coversDir = File(exportDir, bookCoversFolder)
            val animeCoversDir = File(exportDir, animeCoversFolder)
            val mangaCoversDir = File(exportDir, mangaCoversFolder)
            val seriesCoversDir = File(exportDir, seriesCoversFolder)

            listOf(booksDir, animeDir, mangaDir, seriesDir, coversDir, animeCoversDir, mangaCoversDir, seriesCoversDir).forEach { dir ->
                if (!dir.exists()) {
                    dir.mkdirs()
                }
            }
            listOf(coversDir, animeCoversDir, mangaCoversDir, seriesCoversDir).forEach { coverDir ->
                File(coverDir, ".nomedia").takeIf { it.exists() }?.delete()
            }
            val rootNoMedia = File(exportDir, ".nomedia")
            if (!rootNoMedia.exists()) {
                rootNoMedia.writeText("")
            }

            val usedExportNames = mutableSetOf<String>()
            val sourceFiles = worksDirectory.listFiles { _, name -> name.endsWith(".md") }
            sourceFiles?.forEach { sourceFile ->
                val work = parseMarkdownFile(sourceFile)
                if (work != null) {
                    val targetDir = when (work.type) {
                        WorkType.BOOK -> booksDir
                        WorkType.ANIME -> animeDir
                        WorkType.MANGA -> mangaDir
                        WorkType.SERIES -> seriesDir
                    }

                    val base = uniqueExportBase(work.title, work.id, usedExportNames)
                    val destFile = File(targetDir, "$base.md")

                    val coverTargetDir = when (work.type) {
                        WorkType.BOOK -> coversDir
                        WorkType.ANIME -> animeCoversDir
                        WorkType.MANGA -> mangaCoversDir
                        WorkType.SERIES -> seriesCoversDir
                    }
                    val exportedCoverNames = mutableListOf<String>()
                    work.allCoverPaths().forEachIndexed { index, coverPath ->
                        val coverFile = File(coverPath)
                        if (!coverFile.exists()) return@forEachIndexed
                        val coverExt = coverFile.extension.ifBlank { "jpg" }
                        val coverFileName = "${exportCoverBaseName(base, index)}.$coverExt"
                        val coverDestFile = File(coverTargetDir, coverFileName)
                        FileInputStream(coverFile).use { input ->
                            FileOutputStream(coverDestFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        exportedCoverNames += coverFileName
                    }

                    val exportMarkdown = workToMarkdown(
                        work = work,
                        exportHints = ExportCoverHints(
                            exportBase = base,
                            exportCoverNames = exportedCoverNames,
                        ),
                    )
                    destFile.writeText(exportMarkdown)
                }
            }

            val statsFile = File(exportDir, ActivityDeltaLog.EXPORT_FILENAME)
            activityDeltaLog.exportToFile(statsFile)

            exportDir.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getWorksDirectoryPath(): String {
        return worksDirectory.absolutePath
    }

    /**
     * Пересжимает все существующие обложки для уменьшения размера
     * @return Pair<Int, Int> - (успешно пересжато, всего обработано)
     */
    fun recompressAllCovers(context: Context): Pair<Int, Int> {
        var successCount = 0
        var totalCount = 0

        try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val coversDir = File(baseDir, "covers")

            if (!coversDir.exists() || !coversDir.isDirectory) {
                return Pair(0, 0)
            }

            val coverFiles = coversDir.listFiles { _, name ->
                name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) ||
                        name.endsWith(".png", ignoreCase = true)
            }

            coverFiles?.forEach { coverFile ->
                totalCount++
                try {
                    val compressedImage = compressImageFile(coverFile, context)
                    if (compressedImage != null) {
                        // Сохраняем сжатое изображение обратно в тот же файл
                        FileOutputStream(coverFile).use { outputStream ->
                            outputStream.write(compressedImage)
                        }
                        successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(successCount, totalCount)
    }

    /**
     * Оптимизирует скачанную обложку: масштабирует до оптимальных размеров (макс 1200x1800),
     * пережимает в чистый JPEG высокого качества (88%), удаляя мусорные метаданные и снижая размер файла.
     */
    private fun optimizeCoverFile(
        file: File,
        maxWidth: Int = 1200,
        maxHeight: Int = 1800,
        quality: Int = 88
    ): Boolean {
        return try {
            if (!file.exists() || file.length() == 0L) return false

            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) return false

            var inSampleSize = 1
            while ((origW / (inSampleSize * 2) >= maxWidth) || (origH / (inSampleSize * 2) >= maxHeight)) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return false

            if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
                if (ratio < 1.0f) {
                    val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                    val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                    if (scaled != bitmap) {
                        bitmap.recycle()
                        bitmap = scaled
                    }
                }
            }

            val tempFile = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            bitmap.recycle()

            if (tempFile.exists() && tempFile.length() > 0) {
                file.delete()
                tempFile.renameTo(file)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Сжимает изображение из файла
     */
    private fun compressImageFile(
        imageFile: File,
        context: Context,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024,
        quality: Int = 85
    ): ByteArray? {
        return try {
            if (!imageFile.exists()) return null

            // Загружаем изображение с уменьшенным разрешением для экономии памяти
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)

            // Вычисляем коэффициент масштабирования
            val scale = minOf(
                maxWidth.toFloat() / options.outWidth,
                maxHeight.toFloat() / options.outHeight
            ).coerceAtMost(1f)

            // Загружаем изображение с нужным масштабом
            val scaledOptions = BitmapFactory.Options().apply {
                inSampleSize = if (scale < 1f) {
                    var sampleSize = (1f / scale).toInt()
                    // Округляем до ближайшей степени двойки (требование Android)
                    var rounded = 1
                    while (rounded * 2 <= sampleSize) {
                        rounded *= 2
                    }
                    rounded
                } else {
                    1
                }
            }

            var bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, scaledOptions)
            if (bitmap == null) return null

            // Дополнительное масштабирование, если нужно
            if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                val width = bitmap.width
                val height = bitmap.height
                val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
                val newWidth = (width * ratio).toInt()
                val newHeight = (height * ratio).toInt()

                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }

            // Сжимаем в JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            bitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Сжимает и масштабирует изображение для уменьшения размера файла
     */
    private fun compressImage(
        uri: Uri,
        context: Context,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024,
        quality: Int = 85
    ): ByteArray? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            // Загружаем изображение с уменьшенным разрешением для экономии памяти
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Вычисляем коэффициент масштабирования
            val scale = minOf(
                maxWidth.toFloat() / options.outWidth,
                maxHeight.toFloat() / options.outHeight
            ).coerceAtMost(1f)

            // Загружаем изображение с нужным масштабом
            val scaledOptions = BitmapFactory.Options().apply {
                inSampleSize = if (scale < 1f) {
                    var sampleSize = (1f / scale).toInt()
                    // Округляем до ближайшей степени двойки (требование Android)
                    var rounded = 1
                    while (rounded * 2 <= sampleSize) {
                        rounded *= 2
                    }
                    rounded
                } else {
                    1
                }
            }

            val inputStream2 = context.contentResolver.openInputStream(uri) ?: return null
            var bitmap = BitmapFactory.decodeStream(inputStream2, null, scaledOptions)
            inputStream2.close()

            if (bitmap == null) return null

            // Дополнительное масштабирование, если нужно
            if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                val width = bitmap.width
                val height = bitmap.height
                val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
                val newWidth = (width * ratio).toInt()
                val newHeight = (height * ratio).toInt()

                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }

            // Сжимаем в JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            bitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        const val LARGE_ACTIVITY_DELTA_THRESHOLD = 50

        fun generateId(): String = UUID.randomUUID().toString()
    }
}
