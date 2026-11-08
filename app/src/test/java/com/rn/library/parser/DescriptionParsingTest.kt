package com.rn.library.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionParsingTest {
    @Test fun escapedComparisonsAndUnclosedSignsRemainText() {
        assertEquals("a < b and c > d", ParserUtils.cleanDescription("a &lt; b and c &gt; d"))
        assertEquals("a < b", ParserUtils.cleanDescription("a &lt; b"))
        assertEquals("a < b", ParserUtils.cleanDescription("a < b"))
        assertEquals("Hello\nworld", ParserUtils.cleanDescription("<b>Hello</b><br>world"))
    }
}
