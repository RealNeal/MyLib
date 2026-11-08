package com.rn.library.data

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.zip.ZipFile

/**
 * Импорт превью из fb2/epub/pdf.
 * Это восстановленная реализация (не обязательно 1:1 со старой), но с теми же API,
 * которые ожидает `WorkRepository.kt`: fromFb2/fromEpub/fromPdf(File): BookImportPreview?
 */
object BookFormatImporter {

    fun fromFb2(file: File): BookImportPreview? {
        return runCatching {
            val text = file.inputStream().use { InputLimits.text(it, InputLimits.MAX_BOOK) }
            val title = element(text, "book-title")?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val desc = stripXmlToPlain(element(text, "annotation").orEmpty()).take(4000)
            val firstName = element(text, "first-name")?.trim()
            val lastName = element(text, "last-name")?.trim()
            val author = listOfNotNull(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
            BookImportPreview(title = title, description = desc, author = author)
        }.getOrNull()
    }

    fun fromEpub(file: File): BookImportPreview? {
        return runCatching {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val containerXml = zip.getInputStream(containerEntry).use { InputLimits.text(it, 512 * 1024) }
                val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                    .find(containerXml)?.groupValues?.getOrNull(1)?.trim()
                    ?.trimStart('/') ?: return null

                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opf = zip.getInputStream(opfEntry).use { InputLimits.text(it, 512 * 1024) }

                val title = element(opf, "dc:title")?.trim()
                    ?: element(opf, "title")?.trim() ?: file.nameWithoutExtension
                val descRaw = element(opf, "dc:description").orEmpty()
                val author = element(opf, "dc:creator")?.trim()?.ifBlank { null }

                BookImportPreview(title = title, description = stripXmlToPlain(descRaw).take(4000), author = author)
            }
        }.getOrNull()
    }

    fun fromPdf(file: File): BookImportPreview? {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val title = file.nameWithoutExtension.ifBlank { "PDF" }
                    BookImportPreview(title = title, description = "PDF (${renderer.pageCount})")
                }
            }
        }.getOrNull()
    }

    // A fixed number of index scans avoids regex backtracking on malformed XML.
    private fun element(text: String, name: String): String? {
        val open = "<$name"
        var start = text.indexOf(open, ignoreCase = true)
        while (start >= 0) {
            val boundary = start + open.length
            if (boundary < text.length && (text[boundary] == '>' || text[boundary].isWhitespace())) {
                val valueStart = text.indexOf('>', boundary)
                if (valueStart < 0) return null
                val end = text.indexOf("</$name>", valueStart + 1, ignoreCase = true)
                return if (end < 0) null else text.substring(valueStart + 1, end)
            }
            start = text.indexOf(open, boundary, ignoreCase = true)
        }
        return null
    }

    private fun stripXmlToPlain(raw: String): String {
        return raw
             .let { InputLimits.stripTags(it) }
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

