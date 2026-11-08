package com.rn.library.data

import java.io.InputStream
import java.io.OutputStream
import java.io.File
import java.net.URI

object InputLimits {
    const val MAX_UNITS = 10000
    const val MAX_TEXT = 2 * 1024 * 1024
    const val MAX_BOOK = 32 * 1024 * 1024
    const val MAX_IMAGE = 16 * 1024 * 1024
    const val MAX_EVENTS = 20000

    fun validId(id: String): Boolean = id.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
    fun destination(dir: File, id: String, suffix: String): File {
        require(validId(id)) { "Invalid work ID" }
        val candidate = File(dir, id + suffix)
        require(!java.nio.file.Files.isSymbolicLink(candidate.toPath())) { "Symbolic link destination" }
        val file = candidate.canonicalFile
        require(file.parentFile == dir.canonicalFile) { "Invalid destination" }
        return file
    }
    fun copy(input: InputStream, output: OutputStream, limit: Int) {
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - total + 1))
            if (count < 0) break
            require(count <= limit - total) { "Input size limit exceeded" }
            output.write(buffer, 0, count)
            total += count
        }
    }
    fun text(input: InputStream, limit: Int = MAX_TEXT): String {
        val output = java.io.ByteArrayOutputStream()
        copy(input, output, limit)
        return output.toString("UTF-8")
    }
    fun webUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.none { it.isISOControl() } && uri.scheme?.lowercase() in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    // Returns header text and the first body offset; each character is visited once.
    fun frontMatter(content: String): Pair<String, Int>? {
        if (content.length > MAX_TEXT) return null
        val first = content.indexOf('\n')
        if (first < 0 || content.substring(0, first).trimEnd() != "---") return null
        var start = first + 1
        while (start < content.length && start <= 65536) {
            val end = content.indexOf('\n', start).let { if (it < 0) content.length else it }
            if (end > 65536) return null
            if (content.substring(start, end).trimEnd() == "---")
                return content.substring(first + 1, start) to minOf(end + 1, content.length)
            start = end + 1
        }
        return null
    }

    fun stripTags(raw: String): String = buildString {
        var inTag = false
        for (c in raw.take(65536)) {
            if (c == '<') inTag = true
            else if (c == '>' && inTag) inTag = false
            else if (!inTag) append(c)
        }
    }
    fun titleParts(raw: String): Pair<String, String?> {
        val text = raw.take(512)
        val outside = StringBuilder()
        var start = -1
        var closing = ' '
        var inside: String? = null
        for ((i, c) in text.withIndex()) {
            if (start < 0) {
                if (c == '(' || c == '[') { start = i; closing = if (c == '(') ')' else ']' }
                else outside.append(c)
            } else if (c == closing) {
                if (inside == null) inside = text.substring(start + 1, i)
                start = -1
            }
        }
        if (start >= 0) outside.append(text.substring(start))
        return outside.toString().trim() to inside
    }
}
