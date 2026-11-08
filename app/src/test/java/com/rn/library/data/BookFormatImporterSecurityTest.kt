package com.rn.library.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookFormatImporterSecurityTest {
    @Test(timeout = 3000) fun fb2FailingWhitespaceDoesNotBacktrack() {
        val file = Files.createTempFile("book", ".fb2").toFile()
        try {
            file.writeText("<book-title>" + " ".repeat(500000) + "</wrong>")
            assertNull(BookFormatImporter.fromFb2(file))
            file.writeText("<book-title> Example </book-title><first-name>A</first-name><last-name>B</last-name><annotation><p>Description</p></annotation>")
            val preview = BookFormatImporter.fromFb2(file)
            assertEquals("Example", preview?.title)
            assertEquals("A B", preview?.author)
            assertEquals("Description", preview?.description)
        } finally { file.delete() }
    }

    @Test fun epubExpansionBombIsRejected() {
        val file = Files.createTempFile("book", ".epub").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("META-INF/container.xml"))
                zip.write("<rootfile full-path=\"book.opf\"/>".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("book.opf"))
                zip.write(("<dc:title>" + "A".repeat(1024 * 1024) + "</dc:title>").toByteArray())
                zip.closeEntry()
            }
            assertNull(BookFormatImporter.fromEpub(file))
        } finally { file.delete() }
    }

    @Test fun normalEpubMetadataIsPreserved() {
        val file = Files.createTempFile("book", ".epub").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, content) in mapOf("META-INF/container.xml" to "<rootfile full-path=\"book.opf\"/>",
                    "book.opf" to "<dc:title>Title</dc:title><dc:creator>Author</dc:creator><dc:description>Text</dc:description>")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(content.toByteArray()); zip.closeEntry()
                }
            }
            assertEquals("Title", BookFormatImporter.fromEpub(file)?.title)
            assertEquals("Author", BookFormatImporter.fromEpub(file)?.author)
        } finally { file.delete() }
    }
}
