package com.rn.library.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Открывает ссылку во внешнем приложении:
 * - Если для веб-ссылки установлено приложение сайта — открывает в нём
 * - Иначе — в веб-браузере
 * - локальный путь к файлу — через [FileProvider] и [Intent.ACTION_VIEW] с MIME.
 */
object LinkOpenHelper {

    private val KNOWN_SITE_PACKAGES = mapOf(
        "anixart.tv" to listOf("ru.anixart", "org.anixart", "com.anixart"),
        "anixart.app" to listOf("ru.anixart", "org.anixart", "com.anixart"),
        "kinopoisk.ru" to listOf("ru.kinopoisk"),
        "imdb.com" to listOf("com.imdb.mobile"),
        "shikimori.one" to listOf("org.shikimori", "com.github.k1rakishou.shikimori"),
        "shikimori.me" to listOf("org.shikimori", "com.github.k1rakishou.shikimori"),
        "remanga.org" to listOf("org.remanga"),
        "mangalib.me" to listOf("me.mangalib", "com.libsocial"),
        "animelib.me" to listOf("me.animelib", "com.libsocial"),
        "ranobelib.me" to listOf("me.ranobelib", "com.libsocial"),
        "myanimelist.net" to listOf("net.myanimelist.app"),
        "scribblehub.com" to listOf("com.scribblehub.app"),
        "royalroad.com" to listOf("com.royalroad.app"),
        "youtube.com" to listOf("com.google.android.youtube"),
        "youtu.be" to listOf("com.google.android.youtube")
    )

    fun openInExternalViewer(context: Context, rawLink: String): Boolean {
        val cleaned = rawLink.trim().replace("\\:", ":").replace("\\/", "/")
        if (cleaned.isBlank()) return false

        if (cleaned.startsWith("http://", ignoreCase = true) ||
            cleaned.startsWith("https://", ignoreCase = true)
        ) {
            return openWebLink(context, cleaned)
        }

        if (cleaned.startsWith("content://", ignoreCase = true)) {
            return startView(context, Intent(Intent.ACTION_VIEW, Uri.parse(cleaned)))
        }

        val file = localFileOrNull(cleaned) ?: return false
        if (!file.isFile || !file.canRead()) return false

        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = mimeForBookFile(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startView(context, intent)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun openWebLink(context: Context, urlStr: String): Boolean {
        val uri = Uri.parse(urlStr)
        val pm = context.packageManager
        val host = uri.host?.lowercase().orEmpty()

        // 1. Поиск установленного специального приложения для этого сайта
        val targetPackage = findAppForWebLink(pm, uri, host)

        if (targetPackage != null) {
            val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(targetPackage)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (startView(context, appIntent)) {
                return true
            }
        }

        // 2. Стандартный просмотр (браузер / системный выбор)
        val defaultIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startView(context, defaultIntent)
    }

    private fun findAppForWebLink(pm: PackageManager, uri: Uri, host: String): String? {
        // Проверяем известные пакеты приложений сайтов
        for ((siteHost, packages) in KNOWN_SITE_PACKAGES) {
            if (host.contains(siteHost) || siteHost.contains(host)) {
                for (pkg in packages) {
                    if (isPackageInstalled(pm, pkg)) {
                        return pkg
                    }
                }
            }
        }

        // Динамическое определение: ищем обработчики ссылки, которые не являются общими браузерами
        try {
            val dummyBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val browserPackages = pm.queryIntentActivities(dummyBrowserIntent, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .toSet()

            val targetIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val handlers = pm.queryIntentActivities(targetIntent, PackageManager.MATCH_DEFAULT_ONLY)

            val specificApp = handlers.firstOrNull { it.activityInfo.packageName !in browserPackages }
            if (specificApp != null) {
                return specificApp.activityInfo.packageName
            }
        } catch (_: Exception) {}

        return null
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun localFileOrNull(cleaned: String): File? {
        return when {
            cleaned.startsWith("file://") -> {
                val path = Uri.parse(cleaned).path ?: return null
                File(path)
            }
            cleaned.startsWith("/") -> File(cleaned)
            else -> null
        }
    }

    private fun mimeForBookFile(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "fb2" -> "application/x-fictionbook+xml"
            else -> "application/octet-stream"
        }
    }

    private fun startView(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
