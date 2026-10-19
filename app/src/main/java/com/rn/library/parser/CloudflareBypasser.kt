package com.rn.library.parser

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.rn.library.MyLibraryApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

object CloudflareBypasser {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Tries fetching via standard HttpURLConnection first with stored cookies and proper headers.
     * If Cloudflare blocks it (403 or challenge title), falls back to Android WebView.
     */
    suspend fun fetchHtmlWithFallback(
        urlStr: String,
        referer: String? = null,
        timeoutMs: Long = 18000
    ): String? {
        val directHtml = fetchDirect(urlStr, referer)
        if (directHtml != null && !isCloudflareChallenge(directHtml)) {
            return directHtml
        }

        // Fallback to WebView
        return fetchViaWebView(urlStr, timeoutMs)
    }

    fun isCloudflareChallenge(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("<title>just a moment...</title>") ||
                lower.contains("<title>attention required! | cloudflare</title>") ||
                lower.contains("id=\"challenge-stage\"") ||
                lower.contains("id=\"challenge-running\"") ||
                lower.contains("id=\"cf-challenge-running\"") ||
                lower.contains("id=\"challenge-form\"") ||
                lower.contains("class=\"cf-browser-verification\"") ||
                (lower.contains("id=\"cf-wrapper\"") && lower.contains("ray id:"))
    }

    private fun fetchDirect(urlStr: String, referer: String?): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (referer != null) {
                conn.setRequestProperty("Referer", referer)
            }
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
            conn.setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Android WebView\";v=\"124\"")
            conn.setRequestProperty("Sec-Ch-Ua-Mobile", "?1")
            conn.setRequestProperty("Sec-Ch-Ua-Platform", "\"Android\"")
            conn.setRequestProperty("Sec-Fetch-Dest", "document")
            conn.setRequestProperty("Sec-Fetch-Mode", "navigate")
            conn.setRequestProperty("Sec-Fetch-Site", "same-origin")
            conn.setRequestProperty("Sec-Fetch-User", "?1")
            conn.setRequestProperty("Upgrade-Insecure-Requests", "1")

            // Attach stored cookies if any
            val cookieManager = try { CookieManager.getInstance() } catch (_: Exception) { null }
            val storedCookies = cookieManager?.getCookie(urlStr).orEmpty()
            val extraCookies = if (urlStr.contains("scribblehub.com", ignoreCase = true)) {
                "mature=1; sh_mature=1; birthdate=1990-01-01"
            } else ""
            val fullCookie = listOf(storedCookies, extraCookies).filter { it.isNotBlank() }.joinToString("; ")
            if (fullCookie.isNotBlank()) {
                conn.setRequestProperty("Cookie", fullCookie)
            }

            conn.connectTimeout = 10000
            conn.readTimeout = 12000
            if (conn.responseCode in 200..299) {
                val setCookies = conn.headerFields["Set-Cookie"]
                if (setCookies != null && cookieManager != null) {
                    for (c in setCookies) {
                        try {
                            cookieManager.setCookie(urlStr, c)
                        } catch (_: Exception) {}
                    }
                    cookieManager.flush()
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchViaWebView(urlStr: String, timeoutMs: Long): String? {
        val app = MyLibraryApp.instance ?: return null
        val activity = MyLibraryApp.currentActivity
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var isFinished = false
                val handler = Handler(Looper.getMainLooper())
                val context = activity ?: app

                val webView = try {
                    WebView(context)
                } catch (_: Exception) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val decorView = activity?.window?.decorView as? ViewGroup
                var attached = false
                try {
                    if (decorView != null) {
                        webView.alpha = 0.01f
                        decorView.addView(webView, ViewGroup.LayoutParams(1080, 1920))
                        attached = true
                    }
                } catch (_: Exception) {}

                fun finish(result: String?) {
                    if (!isFinished) {
                        isFinished = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            if (attached && decorView != null) {
                                decorView.removeView(webView)
                            }
                            webView.stopLoading()
                            webView.destroy()
                        } catch (_: Exception) {}
                        continuation.resume(result)
                    }
                }

                val timeoutRunnable = Runnable {
                    try {
                        webView.evaluateJavascript("document.documentElement.outerHTML") { htmlRaw ->
                            val html = parseJsString(htmlRaw)
                            if (html.isNotBlank() && !isCloudflareChallenge(html)) {
                                finish(html)
                            } else {
                                finish(null)
                            }
                        }
                    } catch (_: Exception) {
                        finish(null)
                    }
                }
                handler.postDelayed(timeoutRunnable, timeoutMs)

                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    if (urlStr.contains("scribblehub.com", ignoreCase = true)) {
                        cookieManager.setCookie("https://www.scribblehub.com", "mature=1; path=/; domain=.scribblehub.com")
                        cookieManager.setCookie("https://www.scribblehub.com", "sh_mature=1; path=/; domain=.scribblehub.com")
                        cookieManager.setCookie("https://www.scribblehub.com", "birthdate=1990-01-01; path=/; domain=.scribblehub.com")
                        cookieManager.flush()
                    }

                    webView.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        javaScriptCanOpenWindowsAutomatically = true
                        userAgentString = DEFAULT_USER_AGENT
                    }
                    webView.layout(0, 0, 1080, 1920)

                    val checkRunnable = object : Runnable {
                        override fun run() {
                            if (isFinished) return
                            try {
                                val cookies = cookieManager.getCookie(urlStr).orEmpty()
                                if (cookies.contains("cf_clearance")) {
                                    cookieManager.flush()
                                }

                                webView.evaluateJavascript("document.title") { titleRaw ->
                                    val title = parseJsString(titleRaw).lowercase()
                                    val isChallengeTitle = title.contains("just a moment") ||
                                            title.contains("attention required") ||
                                            (title.contains("cloudflare") && !title.contains("novel"))

                                    if (!isChallengeTitle && title.isNotBlank()) {
                                        webView.evaluateJavascript("document.documentElement.outerHTML") { htmlRaw ->
                                            val html = parseJsString(htmlRaw)
                                            if (html.length > 500 && !isCloudflareChallenge(html)) {
                                                cookieManager.flush()
                                                finish(html)
                                                return@evaluateJavascript
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}

                            if (!isFinished) {
                                handler.postDelayed(this, 600)
                            }
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            cookieManager.flush()
                            handler.postDelayed(checkRunnable, 300)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            if (request?.isForMainFrame == true && error?.errorCode == ERROR_HOST_LOOKUP) {
                                finish(null)
                            }
                        }
                    }

                    continuation.invokeOnCancellation {
                        finish(null)
                    }

                    webView.loadUrl(urlStr)
                } catch (_: Exception) {
                    finish(null)
                }
            }
        }
    }

    private fun parseJsString(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            val tokener = JSONTokener(raw)
            val value = tokener.nextValue()
            if (value is String) value else raw.trim('"')
        } catch (_: Exception) {
            raw.trim('"')
        }
    }
}
