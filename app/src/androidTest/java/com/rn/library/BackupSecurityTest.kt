package com.rn.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rn.library.data.Work
import com.rn.library.data.WorkRepository
import com.rn.library.data.WorkType
import com.rn.library.data.WorkStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSecurityTest {
    private val repository = WorkRepository(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun parse(text: String): Work? {
        val method = WorkRepository::class.java.getDeclaredMethod("parseMarkdownContent", String::class.java)
        method.isAccessible = true
        return method.invoke(repository, text) as Work?
    }
    private fun serialize(work: Work): String {
        val method = WorkRepository::class.java.declaredMethods.single { it.name == "workToMarkdown" }
        method.isAccessible = true
        return method.invoke(repository, work, null) as String
    }

    @Test fun duplicateAndTraversalIdentitiesAreRejected() {
        assertNull(parse("---\nid: ../victim\ntitle: Test\n---\n"))
        val id = WorkRepository.generateId()
        assertNull(parse("---\nid: $id\nid: ${WorkRepository.generateId()}\n---\n"))
    }

    @Test fun linksCannotCreateNewIdentityProperties() {
        val id = WorkRepository.generateId()
        val injected = "https://example.com/\nid: ${WorkRepository.generateId()}\r\n---"
        val original = Work(type = WorkType.BOOK, status = WorkStatus.IN_PLANS, id = id, title = "Title\n---\nid: replacement", link = injected)
        val serialized = serialize(original)
        val loaded = parse(serialized)
        assertEquals(id, loaded?.id)
        assertEquals(original.title, loaded?.title)
        assertEquals(injected, loaded?.link)
    }

    @Test fun loadedIdentityMustMatchItsBackingFilename() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "${WorkRepository.generateId()}.md")
        try {
            file.writeText("---\nid: ${WorkRepository.generateId()}\ntitle: Test\n---\n")
            val method = WorkRepository::class.java.getDeclaredMethod("parseMarkdownFile", java.io.File::class.java)
            method.isAccessible = true
            assertNull(method.invoke(repository, file))
            assertFalse(repository.deleteWork("../victim"))
        } finally { file.delete() }
    }

    @Test fun remoteMetadataCannotOpenLocalFilesAndCountsAreBounded() {
        val id = WorkRepository.generateId()
        val loaded = parse("---\nid: $id\ncover: file:///data/private\n---\n")
        assertNotNull(loaded)
        assertNull(loaded?.coverPath)
        assertNull(parse("---\nid: $id\nchapters: 2147483647\n---\n"))
        assertNull(parse("---\nid: $id\nvolumes: NaN\n---\n"))
    }
}
