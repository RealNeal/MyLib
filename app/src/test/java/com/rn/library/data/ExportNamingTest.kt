package com.rn.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportNamingTest {

    @Test
    fun exportCoverBaseName_usesOneBasedSuffixForAdditionalCovers() {
        assertEquals("Title", exportCoverBaseName("Title", 0))
        assertEquals("Title_2", exportCoverBaseName("Title", 1))
        assertEquals("Title_3", exportCoverBaseName("Title", 2))
    }

    @Test
    fun parseExportCoverIndex_matchesPrimaryAndAdditionalCovers() {
        val base = "Lord of the Mysteries"
        assertEquals(0, parseExportCoverIndex(base, base))
        assertEquals(1, parseExportCoverIndex(base, "${base}_2"))
        assertEquals(2, parseExportCoverIndex(base, "${base}_3"))
        assertNull(parseExportCoverIndex(base, "${base}_1"))
        assertNull(parseExportCoverIndex(base, "Other title"))
    }

    @Test
    fun parseExportCoverIndex_handlesSpecialRegexCharactersInTitle() {
        val base = "Dr. Stone (2)"
        assertEquals(0, parseExportCoverIndex(base, base))
        assertEquals(1, parseExportCoverIndex(base, "${base}_2"))
    }
}
