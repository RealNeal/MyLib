package com.rn.library.parser

import android.content.Context
import com.rn.library.MyLibraryApp
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер зеркал и отказоустойчивости доменов для парсеров.
 * Автоматически сохраняет рабочий домен и переключается на зеркала при блокировках или смене доменов.
 */
object ParserDomainManager {
    private const val PREFS_NAME = "parser_domain_cache_v32"

    const val SOURCE_LIB_SOCIAL_API = "lib_social_api"
    const val SOURCE_ANIMELIB_WEB = "animelib_web"
    const val SOURCE_MANGALIB_WEB = "mangalib_web"
    const val SOURCE_RANOBELIB_WEB = "ranobelib_web"
    const val SOURCE_RANOBES = "ranobes"
    const val SOURCE_RULATE = "rulate"
    const val SOURCE_ANIXART_API = "anixart_api"
    const val SOURCE_RANOBEHUB = "ranobehub"
    const val SOURCE_SHIKIMORI = "shikimori"
    const val SOURCE_REMANGA = "remanga"
    const val SOURCE_SCRIBBLEHUB = "scribblehub"
    const val SOURCE_MANGABUFF = "mangabuff"
    const val SOURCE_ANIMETKA = "animetka"
    const val SOURCE_ROYALROAD = "royalroad"
    const val SOURCE_ANILIBERTY = "aniliberty"

    private val DEFAULT_MIRRORS = mapOf(
        SOURCE_LIB_SOCIAL_API to listOf(
            "https://api.lib.social/api",
            "https://api.cdnlibs.org/api"
        ),
        SOURCE_ANIMELIB_WEB to listOf(
            "https://animelib.me",
            "https://anilib.me",
            "https://animelib.org",
            "https://anime.lib.social"
        ),
        SOURCE_MANGALIB_WEB to listOf(
            "https://mangalib.me",
            "https://mangalib.org",
            "https://manga.lib.social"
        ),
        SOURCE_RANOBELIB_WEB to listOf(
            "https://ranobelib.me",
            "https://ranobelib.org",
            "https://ranobe.lib.social"
        ),
        SOURCE_RANOBES to listOf(
            "https://ranobes.top",
            "https://ranobes.net",
            "https://ranobes.org",
            "https://ranobes.com"
        ),
        SOURCE_RULATE to listOf(
            "https://tl.rulate.ru",
            "https://rulate.ru",
            "https://tl.rulate.org"
        ),
        SOURCE_ANIXART_API to listOf(
            "https://api.anixsekai.com",
            "https://api-s.anixsekai.com",
            "https://api.anixart.tv"
        ),
        SOURCE_RANOBEHUB to listOf(
            "https://ranobehub.org",
            "https://ranobehub.com"
        ),
        SOURCE_SHIKIMORI to listOf(
            "https://shikimori.io",
            "https://shikimori.one",
            "https://shikimori.me"
        ),
        SOURCE_REMANGA to listOf(
            "https://remanga.org",
            "https://xn--80aaig9ah.xn--c1avg"
        ),
        SOURCE_SCRIBBLEHUB to listOf(
            "https://www.scribblehub.com",
            "https://scribblehub.com"
        ),
        SOURCE_MANGABUFF to listOf(
            "https://mangabuff.ru"
        ),
        SOURCE_ANIMETKA to listOf(
            "https://animetka.com"
        ),
        SOURCE_ROYALROAD to listOf(
            "https://www.royalroad.com",
            "https://royalroad.com"
        ),
        SOURCE_ANILIBERTY to listOf(
            "https://anilibria.top/api/v1",
            "https://aniliberty.top/api/v1"
        )
    )

    private val activeDomains = ConcurrentHashMap<String, String>()

    private fun getPrefs() = MyLibraryApp.instance?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDomain(key: String): String {
        activeDomains[key]?.let { return it }
        val stored = getPrefs()?.getString(key, null)
        val defaultList = DEFAULT_MIRRORS[key] ?: return ""
        val working = if (stored != null && defaultList.contains(stored)) stored else defaultList.first()
        activeDomains[key] = working
        return working
    }

    fun getMirrors(key: String): List<String> {
        val list = DEFAULT_MIRRORS[key] ?: return emptyList()
        val current = getDomain(key)
        return listOf(current) + list.filter { it != current }
    }

    fun reportWorking(key: String, domain: String) {
        val normalized = domain.trimEnd('/')
        activeDomains[key] = normalized
        getPrefs()?.edit()?.putString(key, normalized)?.apply()
    }

    fun reportFailure(key: String, failedDomain: String) {
        val list = DEFAULT_MIRRORS[key] ?: return
        val normalized = failedDomain.trimEnd('/')
        val next = list.firstOrNull { it.trimEnd('/') != normalized } ?: return
        activeDomains[key] = next
        getPrefs()?.edit()?.putString(key, next)?.apply()
    }

    suspend fun <T> executeWithMirrorFallback(
        key: String,
        block: suspend (domain: String) -> T?
    ): T? {
        val mirrors = getMirrors(key)
        for (domain in mirrors) {
            try {
                val result = block(domain)
                if (result != null) {
                    reportWorking(key, domain)
                    return result
                }
            } catch (_: Exception) {
                reportFailure(key, domain)
            }
        }
        return null
    }
}
