package com.rn.library.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryStorageTest {
    private lateinit var context: Context
    private lateinit var database: LibraryDatabase
    private lateinit var repository: WorkRepository
    private lateinit var legacy: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        repository = WorkRepository(context) { database }
        legacy = File(repository.getWorksDirectoryPath()).apply { mkdirs() }
    }

    @After fun tearDown() {
        database.close()
        legacy.deleteRecursively()
    }

    private fun work() = Work(
        id = UUID.randomUUID().toString(), title = "Название\n---\nТест", description = "Описание",
        type = WorkType.BOOK, status = WorkStatus.READING,
        chapters = 17.5, volumes = 2.0, bookChapters = 31.0, episodes = 8.0, seasons = 2,
        year = 2024, yearPeriod = "2024 - 2025", country = "Россия", seriesType = SeriesType.FILM,
        mangaType = MangaType.MANHWA, animeSeason = AnimeSeason.WINTER, abandonedProgress = 2,
        progress = 3.5, otherTitle = "Other", dateRead = "2024-01-02", rereadDates = listOf("2025-01-03"),
        readingPeriods = listOf(ReadingPeriod("2024-01-01", null), ReadingPeriod("2025-01-01", "2025-01-03")),
        unitProgress = listOf(UnitProgress("Том 1", 1.5, null), UnitProgress("Том 2", 2.0, 10.0)),
        activeUnitIndex = 1, note = "Заметка\nс переносом", link = "https://example.com/",
        link2 = "https://example.org/", links = listOf("https://example.com/", "https://example.org/"),
        author = "Автор", updatedAt = 1234L, genres = listOf("Драма"), tags = listOf("Метка")
    )

    private fun markdown(work: Work): String {
        val method = WorkRepository::class.java.declaredMethods.single { it.name == "workToMarkdown" }
        method.isAccessible = true
        return method.invoke(repository, work, null) as String
    }

    @Test fun roomRoundTripPreservesEveryFieldWithoutMarkdownEscaping() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work().copy(tags = listOf("a||b", "comma,semi;", "\"quote\"\n雪"))
        assertTrue(repository.saveWork(expected, preserveUpdatedAt = true, recordActivity = false))
        assertEquals(expected, repository.getWorkById(expected.id))
        assertEquals(emptyList<String>(), legacy.listFiles()!!.map { it.name })
    }

    @Test fun legacyMigrationIsIdempotentAndDeletedWorksDoNotReturn() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work()
        val file = File(legacy, "${expected.id}.md").apply { writeText(markdown(expected)) }
        val original = file.readBytes()
        assertEquals(listOf(expected), repository.getAllWorks())
        assertArrayEquals(original, file.readBytes())
        val edited = expected.copy(note = "edited")
        assertTrue(repository.saveWork(edited, preserveUpdatedAt = true, recordActivity = false))
        assertEquals(edited, WorkRepository(context) { database }.getWorkById(expected.id))
        assertTrue(repository.deleteWork(expected.id))
        assertTrue(WorkRepository(context) { database }.getAllWorks().isEmpty())
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun invalidLegacyRecordIsReportedWithoutBlockingValidWorks() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work()
        File(legacy, "${expected.id}.md").writeText(markdown(expected))
        val invalid = File(legacy, "${UUID.randomUUID()}.md").apply { writeText("corrupt") }
        assertEquals(listOf(expected), repository.getAllWorks())
        assertEquals(listOf(invalid.name), repository.migrationWarnings())
        assertTrue(invalid.exists())
        assertTrue(database.libraryDao().hasState("markdown_v1"))
        repository.acknowledgeMigrationWarnings()
        assertTrue(repository.migrationWarnings().isEmpty())
        assertTrue(repository.saveWork(expected.copy(note = "edited"), recordActivity = false))
    }

    @Test fun legacyIoFailureRollsBackAndCanBeRetried() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work()
        File(legacy, "${expected.id}.md").writeText(markdown(expected))
        val unreadable = File(legacy, "${UUID.randomUUID()}.md").apply { mkdirs() }
        try { repository.getAllWorks(); fail("Expected read failure") } catch (_: java.io.IOException) { }
        assertTrue(database.libraryDao().getAllWorks().isEmpty())
        assertFalse(database.libraryDao().hasState("markdown_v1"))
        unreadable.delete()
        assertEquals(listOf(expected), repository.getAllWorks())
    }

    @Test fun activityMigrationAndWorkUpdatesDoNotReplayLegacyEvents() = runBlocking<Unit>(Dispatchers.IO) {
        val original = work().copy(progress = 0.0, unitProgress = emptyList())
        context.getSharedPreferences("activity_delta_log", Context.MODE_PRIVATE).edit()
            .putStringSet("events", setOf("2024-01-01|${original.id}|2.5|0.0")).commit()
        assertEquals(2.5, repository.loadActivityEvents().sumOf { it.readDelta }, 0.0)
        assertTrue(repository.saveWork(original, recordActivity = false))
        assertTrue(repository.saveWork(original.copy(progress = 1.0)))
        assertEquals(3.5, repository.loadActivityEvents().sumOf { it.readDelta }, 0.0)
        assertEquals(3.5, WorkRepository(context) { database }.loadActivityEvents().sumOf { it.readDelta }, 0.0)
    }

    @Test fun failedActivityWriteRollsBackWorkChange() = runBlocking<Unit>(Dispatchers.IO) {
        val original = work().copy(progress = 0.0, unitProgress = emptyList())
        assertTrue(repository.saveWork(original, recordActivity = false))
        database.libraryDao().markState(StorageState("activity_v1"))
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_activity BEFORE INSERT ON activity_events BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertFalse(repository.saveWork(original.copy(progress = 1.0)))
        assertEquals(0.0, repository.getWorkById(original.id)!!.progress!!, 0.0)
    }

    @Test fun observersSeeSavesAndDeletes() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work()
        assertTrue(repository.observeWorks().first().isEmpty())
        val changed = async { withTimeout(5000) { repository.observeWorks().first { it.isNotEmpty() } } }
        assertTrue(repository.saveWork(expected, preserveUpdatedAt = true, recordActivity = false))
        assertEquals(listOf(expected), changed.await())
        assertTrue(repository.deleteWork(expected.id))
        assertTrue(repository.observeWorks().first().isEmpty())
    }

    @Test fun markdownExportUsesDatabaseIncludingNewEditsAndImportsBack() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work()
        assertTrue(repository.saveWork(expected, preserveUpdatedAt = true, recordActivity = false))
        val export = File(repository.exportWorksToDownloads()!!)
        val markdown = export.walkTopDown().single { it.extension == "md" }
        assertTrue(repository.deleteWork(expected.id))
        assertTrue(repository.importMarkdownBackup(context, Uri.fromFile(markdown)))
        assertEquals(expected, repository.getWorkById(expected.id))
    }

    @Test fun archiveRoundTripPreservesWorksCoversAndPerWorkActivity() = runBlocking<Unit>(Dispatchers.IO) {
        val cover = File(context.filesDir, "covers/${UUID.randomUUID()}.jpg").apply {
            parentFile!!.mkdirs()
            outputStream().use { Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val expected = work().copy(coverPath = cover.absolutePath, coverPaths = listOf("https://example.com/remote.webp"),
            tags = listOf("a||b", "comma,semi;", "\"quote\"\n雪"),
            unitProgress = listOf(UnitProgress("Том || ; 1", 1.5, null)))
        assertTrue(repository.saveWork(expected, preserveUpdatedAt = true, recordActivity = false))
        val events = listOf(ActivityDeltaEvent(LocalDate.of(2024, 1, 1), expected.id, 2.5, 1.0))
        ActivityDeltaLog(context) { database }.replaceAllEvents(events)
        val archive = File(context.cacheDir, "backup.mylib")
        assertTrue(repository.exportLibraryArchive(Uri.fromFile(archive)))
        assertTrue(repository.deleteWork(expected.id))
        ActivityDeltaLog(context) { database }.replaceAllEvents(emptyList())
        assertEquals(1, repository.importLibraryArchive(Uri.fromFile(archive)))
        val restored = repository.getWorkById(expected.id)!!
        assertEquals(expected.copy(coverPath = restored.coverPath), restored)
        assertTrue(File(restored.coverPath!!).isFile)
        assertArrayEquals(cover.readBytes(), File(restored.coverPath).readBytes())
        assertEquals(events, repository.loadActivityEvents())
        val countBefore = File(context.filesDir, "covers").listFiles()!!.size
        assertEquals(1, repository.importLibraryArchive(Uri.fromFile(archive)))
        assertEquals(countBefore, File(context.filesDir, "covers").listFiles()!!.size)
    }

    @Test fun archiveImportPreservesUnrelatedLocalWorksAndTheirActivity() = runBlocking<Unit>(Dispatchers.IO) {
        val backedUp = work()
        val local = work()
        val date = LocalDate.of(2024, 1, 1)
        val log = ActivityDeltaLog(context) { database }
        assertTrue(repository.saveWork(backedUp, recordActivity = false))
        val event = ActivityDeltaEvent(date, backedUp.id, 2.0, 0.0)
        log.replaceAllEvents(listOf(event))
        val archive = File(context.cacheDir, "merge.mylib")
        assertTrue(repository.exportLibraryArchive(Uri.fromFile(archive)))
        assertTrue(repository.saveWork(local, preserveUpdatedAt = true, recordActivity = false))
        val localEvent = ActivityDeltaEvent(date, local.id, 7.0, 0.0)
        log.replaceAllEvents(listOf(event.copy(readDelta = 9.0), localEvent))
        assertEquals(1, repository.importLibraryArchive(Uri.fromFile(archive)))
        assertEquals(local, repository.getWorkById(local.id))
        assertEquals(setOf(event, localEvent), repository.loadActivityEvents().toSet())
    }

    @Test fun frequentEditsAfter700WorkMigrationNeverReadLegacyFilesAgain() = runBlocking<Unit>(Dispatchers.IO) {
        val works = List(700) { work().copy(progress = 0.0, unitProgress = emptyList()) }
        works.forEach { File(legacy, "${it.id}.md").writeText(markdown(it)) }
        assertEquals(700, repository.getAllWorks().size)
        // A subsequent save must use Room even when the recovery copy is unreadable.
        legacy.listFiles()!!.forEach { it.writeText("corrupt recovery copy") }
        val selected = works.first()
        repeat(100) { assertTrue(repository.saveWork(selected.copy(progress = (it + 1).toDouble()))) }
        assertEquals(100.0, repository.getWorkById(selected.id)!!.progress!!, 0.0)
        assertEquals(works[1], repository.getWorkById(works[1].id))
        assertEquals(700, repository.getAllWorks().size)
        assertEquals(100.0, repository.loadActivityEvents().sumOf { it.readDelta }, 0.0)
    }

    @Test fun deductionsTouchOnlyMatchingAndImportedActivity() = runBlocking<Unit>(Dispatchers.IO) {
        val first = work().id
        val other = work().id
        val date = LocalDate.of(2024, 1, 1)
        val log = ActivityDeltaLog(context) { database }
        log.replaceAllEvents(listOf(
            ActivityDeltaEvent(date, first, 5.0, 0.0),
            ActivityDeltaEvent(date, ActivityStatisticsFormat.IMPORT_WORK_ID, 3.0, 0.0),
            ActivityDeltaEvent(date, other, 7.0, 0.0)
        ))
        log.deductEventProgress(first, 6.0, 0.0, date)
        val events = log.loadEvents()
        assertEquals(2, events.size)
        assertEquals(2.0, events.single { it.workId == ActivityStatisticsFormat.IMPORT_WORK_ID }.readDelta, 0.0)
        assertEquals(7.0, events.single { it.workId == other }.readDelta, 0.0)
    }

    @Test fun pickedCoversNeverOverwriteSavedFilesAfterRemovingEarlierCovers() = runBlocking<Unit>(Dispatchers.IO) {
        val id = work().id
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val original = File(directory, "${id}_1.jpg").apply { writeText("saved cover") }
        val uri = Uri.parse("content://external.picker/image")
        val provider = android.content.pm.ProviderInfo().apply {
            authority = "external.picker"
            packageName = "external.picker"
            name = "external.picker.Provider"
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                uid = context.applicationInfo.uid + 1
                packageName = "external.picker"
            }
        }
        org.robolectric.Shadows.shadowOf(context.packageManager).addOrUpdateProvider(provider)
        val bytes = java.io.ByteArrayOutputStream().apply {
            Bitmap.createBitmap(20, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, this)
        }.toByteArray()
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val first = repository.persistCoverFromPicker(uri, id)
        val second = repository.persistCoverFromPicker(uri, id)
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        assertNotEquals(original.absolutePath, first)
        assertEquals("saved cover", original.readText())
        assertTrue(File(first!!).isFile)
        assertTrue(File(second!!).isFile)
    }

    @Test fun maliciousArchivePathsAndVersionsLeaveExistingDataIntact() = runBlocking<Unit>(Dispatchers.IO) {
        val expected = work()
        assertTrue(repository.saveWork(expected, preserveUpdatedAt = true, recordActivity = false))
        val archive = File(context.cacheDir, "bad.mylib")
        for (entries in listOf(mapOf("../outside" to "bad"), mapOf("manifest.json" to "{\"format\":\"MyLibrary\",\"version\":99}"))) {
            ZipOutputStream(archive.outputStream()).use { zip ->
                entries.forEach { (name, value) -> zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
            }
            try { repository.importLibraryArchive(Uri.fromFile(archive)); fail("Should reject archive") } catch (_: IllegalArgumentException) { }
            assertEquals(listOf(expected), repository.getAllWorks())
        }
    }
}
