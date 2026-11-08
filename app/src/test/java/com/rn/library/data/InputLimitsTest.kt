package com.rn.library.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class InputLimitsTest {
    @Test fun traversalAndSymlinksCannotSelectDestinations() {
        val root = Files.createTempDirectory("destinations").toFile()
        try {
            val id = "12345678-1234-1234-1234-123456789abc"
            assertEquals(root.canonicalFile, InputLimits.destination(root, id, ".md").parentFile)
            for (bad in listOf("../victim", "$id/../victim", "", "not-a-uuid")) {
                assertTrue(runCatching { InputLimits.destination(root, bad, ".md") }.isFailure)
            }
            Files.createSymbolicLink(root.resolve("$id.md").toPath(), root.parentFile!!.resolve("victim.md").toPath())
            assertTrue(runCatching { InputLimits.destination(root, id, ".md") }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun byteLimitUsesActualBytesAndDoesNotWriteExcess() {
        val output = ByteArrayOutputStream()
        assertTrue(runCatching { InputLimits.copy(ByteArrayInputStream(ByteArray(8193)), output, 8192) }.isFailure)
        assertEquals(8192, output.size())
        assertEquals("abc", InputLimits.text(ByteArrayInputStream("abc".toByteArray()), 3))
    }

    @Test(timeout = 2000) fun malformedHeadersAndDelimitersHaveBoundedCost() {
        assertNull(InputLimits.frontMatter("---\n" + "\n".repeat(100000)))
        assertNull(InputLimits.frontMatter("---\n" + "a".repeat(100000) + "\n---"))
        assertEquals("Hello\n", InputLimits.frontMatter("---\r\nid: test\r\n---\r\nHello\n")?.let { "---\r\nid: test\r\n---\r\nHello\n".substring(it.second) })
        assertEquals("", InputLimits.stripTags("<".repeat(100000)))
        assertEquals(512, InputLimits.titleParts("(".repeat(100000)).first.length)
        assertEquals("Title", InputLimits.titleParts("Title (Alternative)").first)
        assertEquals("Alternative", InputLimits.titleParts("Title (Alternative)").second)
    }

    @Test fun localAndControlCharacterUrlsAreRejected() {
        assertTrue(InputLimits.webUrl("https://example.com/cover.jpg"))
        for (url in listOf("file:///data/private", "content://provider/file", "https://example.com/\nid: injected", "https://user@example.com/"))
            assertFalse(InputLimits.webUrl(url))
    }
}
