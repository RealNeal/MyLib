package com.rn.library.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class KinopoiskKeyTest {
    @Test fun missingKeyExplainsWhySearchCannotStart() = runBlocking {
        KinopoiskClient.apiKey = ""
        val result = KinopoiskClient.search("example")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("API-ключ"))
    }
}
