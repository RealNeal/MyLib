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

        // 1. Явные маркеры страниц-проверок
        val isChallengeTitle = lower.contains("<title>just a moment...</title>") ||
                lower.contains("<title>attention required! | cloudflare</title>") ||
                lower.contains("<title>ddos-guard</title>") ||
                lower.contains("<title>security check</title>")

        val hasCaptchaWidget = lower.contains("vb-custom-captcha") ||
                lower.contains("vb_custom_captcha") ||
                lower.contains("id=\"challenge-stage\"") ||
                lower.contains("id=\"challenge-running\"") ||
                lower.contains("id=\"cf-challenge-running\"") ||
                lower.contains("class=\"cf-browser-verification\"") ||
                lower.contains("class=\"cf-turnstile\"") ||
                lower.contains("id=\"cf-turnstile\"")

        if (isChallengeTitle || hasCaptchaWidget) {
            return true
        }

        // 2. Реальный контент сайта (если найден, страница успешно загрузилась)
        val hasRealContent = lower.contains("<article") ||
                lower.contains("class=\"moreless__full\"") ||
                lower.contains("class=\"r-fullstory\"") ||
                lower.contains("dle-content") ||
                lower.contains("class=\"b-story\"") ||
                lower.contains("class=\"fic_title\"") ||
                lower.contains("class=\"wi_fic_desc\"") ||
                lower.contains("<nav") ||
                lower.contains("<header") ||
                lower.contains("<footer") ||
                lower.contains("class=\"short-story\"") ||
                lower.contains("class=\"search-page\"") ||
                lower.contains("class=\"title\"")

        if (hasRealContent && html.length > 2500) {
            return false
        }

        // 3. Короткие страницы редиректов защиты
        return lower.contains("enable javascript and cookies to continue") ||
                (lower.contains("browser_check=") && html.length < 3000) ||
                (lower.contains("check.ddos-guard.net") && html.length < 3000) ||
                (lower.contains("id=\"cf-wrapper\"") && lower.contains("ray id:"))
    }

    private fun fetchDirect(
        urlStr: String,
        referer: String?,
        retryCount: Int = 1,
        retryCookie: String? = null
    ): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            if (referer != null) {
                conn.setRequestProperty("Referer", referer)
            }
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
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
            val fullCookie = listOf(storedCookies, extraCookies, retryCookie).filter { !it.isNullOrBlank() }.joinToString("; ")
            if (fullCookie.isNotBlank()) {
                conn.setRequestProperty("Cookie", fullCookie)
            }

            conn.connectTimeout = 5000
            conn.readTimeout = 7000

            // Save set cookies from headers regardless of responseCode
            val setCookies = conn.headerFields["Set-Cookie"]
            if (setCookies != null && cookieManager != null) {
                for (c in setCookies) {
                    try {
                        cookieManager.setCookie(urlStr, c)
                    } catch (_: Exception) {}
                }
                cookieManager.flush()
            }

            val code = conn.responseCode
            val rawText = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() }
            }

            if (rawText != null) {
                // Check for Ranobes / DDoS check browser_check cookie token
                val browserCheckMatch = Regex("""browser_check\s*=\s*['"]?([^'"\s;]+)""").find(rawText)
                if (browserCheckMatch != null && retryCount > 0) {
                    val token = browserCheckMatch.groupValues[1]
                    val cookieStr = "browser_check=$token; path=/"
                    cookieManager?.setCookie(urlStr, cookieStr)
                    val host = try { URL(urlStr).host } catch (_: Exception) { "" }
                    if (host.isNotBlank()) {
                        cookieManager?.setCookie(host, cookieStr)
                        cookieManager?.setCookie(".$host", cookieStr)
                    }
                    cookieManager?.flush()
                    conn.disconnect()
                    return fetchDirect(urlStr, referer, retryCount - 1, retryCookie = "browser_check=$token")
                }

                if (code in 200..299 && !isCloudflareChallenge(rawText)) {
                    return rawText
                }
            }
            null
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

                var verificationDialog: android.app.Dialog? = null

                fun finish(result: String?) {
                    if (!isFinished) {
                        isFinished = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            if (verificationDialog?.isShowing == true) {
                                verificationDialog?.dismiss()
                            }
                            webView.stopLoading()
                            (webView.parent as? ViewGroup)?.removeView(webView)
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

                // Если через 2.5 сек защита требует ввода капчи — показываем диалог поверх экрана
                val showIfChallengeRunnable = Runnable {
                    if (!isFinished && activity != null && !activity.isFinishing && !activity.isDestroyed) {
                        try {
                            webView.evaluateJavascript("document.documentElement.outerHTML") { htmlRaw ->
                                val html = parseJsString(htmlRaw)
                                if (isCloudflareChallenge(html) && !isFinished) {
                                    val container = android.widget.LinearLayout(activity).apply {
                                        orientation = android.widget.LinearLayout.VERTICAL
                                        setBackgroundColor(android.graphics.Color.WHITE)
                                    }
                                    val header = android.widget.LinearLayout(activity).apply {
                                        orientation = android.widget.LinearLayout.HORIZONTAL
                                        setPadding(32, 24, 32, 24)
                                        setBackgroundColor(android.graphics.Color.parseColor("#1E1E2E"))
                                        gravity = android.view.Gravity.CENTER_VERTICAL
                                    }
                                    val titleText = android.widget.TextView(activity).apply {
                                        text = "Проверка защиты (капча)"
                                        setTextColor(android.graphics.Color.WHITE)
                                        textSize = 15f
                                        layoutParams = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                                    }
                                    val closeBtn = android.widget.Button(activity).apply {
                                        text = "Закрыть"
                                        setOnClickListener { finish(null) }
                                    }
                                    header.addView(titleText)
                                    header.addView(closeBtn)
                                    container.addView(header)

                                    (webView.parent as? ViewGroup)?.removeView(webView)
                                    webView.alpha = 1.0f
                                    val webParams = android.widget.LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        0,
                                        1.0f
                                    )
                                    container.addView(webView, webParams)

                                    val dialog = android.app.Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen).apply {
                                        setContentView(container)
                                        setCancelable(true)
                                        setOnCancelListener { finish(null) }
                                    }
                                    verificationDialog = dialog
                                    dialog.show()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                handler.postDelayed(showIfChallengeRunnable, 2500)

                try {
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(webView, true)
                    if (urlStr.contains("scribblehub.com", ignoreCase = true)) {
                        cookieManager.setCookie("https://www.scribblehub.com", "mature=1; path=/; domain=.scribblehub.com")
                        cookieManager.setCookie("https://www.scribblehub.com", "sh_mature=1; path=/; domain=.scribblehub.com")
                        cookieManager.setCookie("https://www.scribblehub.com", "birthdate=1990-01-01; path=/; domain=.scribblehub.com")
                        cookieManager.setCookie("https://www.scribblehub.com", "adult=1; path=/; domain=.scribblehub.com")
                        cookieManager.flush()
                    }
                    if (urlStr.contains("ranobes", ignoreCase = true)) {
                        cookieManager.setCookie("https://ranobes.top", "user_country=RU; path=/; domain=.ranobes.top")
                        cookieManager.setCookie("https://ranobes.net", "user_country=RU; path=/; domain=.ranobes.net")
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
                                if (cookies.contains("cf_clearance") || cookies.contains("browser_check")) {
                                    cookieManager.flush()
                                }

                                webView.evaluateJavascript("document.title") { titleRaw ->
                                    val title = parseJsString(titleRaw).lowercase()
                                    val isChallengeTitle = title.contains("just a moment") ||
                                            title.contains("attention required") ||
                                            (title.contains("cloudflare") && !title.contains("novel")) ||
                                            title.contains("security check")

                                    webView.evaluateJavascript("document.documentElement.outerHTML") { htmlRaw ->
                                        val html = parseJsString(htmlRaw)
                                        if (html.length > 500 && !isChallengeTitle && !isCloudflareChallenge(html)) {
                                            cookieManager.flush()
                                            finish(html)
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
