package com.rn.library.parser

import org.junit.Assert.*
import org.junit.Test

class CatalogUrlSecurityTest {
    @Test fun hostNamesInsidePathsAndQueriesAreRejected() {
        for (url in listOf(
            "https://attacker.example/mangabuff.ru/manga/top",
            "https://attacker.example/?url=mangabuff.ru/manga/top",
            "https://mangabuff.ru.attacker.example/manga/top",
            "https://mangabuff.ru@attacker.example/manga/top",
            "file:///mangabuff.ru/manga/top"
        )) assertNull(url, CatalogSourceRegistry.findByUrl(url))
    }

    @Test fun supportedHostsStillRequireAValidReference() {
        assertEquals(MediaSource.MANGABUFF, CatalogSourceRegistry.findByUrl("https://mangabuff.ru/manga/test-title")?.source)
        assertEquals(MediaSource.SHIKIMORI, CatalogSourceRegistry.findByUrl("https://shikimori.one/animes/123-example")?.source)
        assertNull(CatalogSourceRegistry.findByUrl("https://mangabuff.ru/unsupported"))
    }
}
