package com.rn.library.ui.screens

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rn.library.MyLibraryApp
import com.rn.library.R
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Получает системную локаль устройства (телефона), независящую от локали внутри приложения.
 */
fun getSystemLocale(): Locale {
    val config = Resources.getSystem().configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val list = config.locales
        if (!list.isEmpty) list[0] else Locale.getDefault()
    } else {
        @Suppress("DEPRECATION")
        config.locale ?: Locale.getDefault()
    }
}

/**
 * Получает системный контекст устройства с системной конфигурацией телефона.
 */
private fun getSystemContext(context: Context?): Context? {
    return try {
        val app = context?.applicationContext ?: MyLibraryApp.instance
        val systemConfig = Resources.getSystem().configuration
        app?.createConfigurationContext(systemConfig) ?: app
    } catch (e: Throwable) {
        context?.applicationContext ?: MyLibraryApp.instance
    }
}

/**
 * Определяет, принят ли порядок месяц-день-год (ММ.ДД.ГГГГ) в системных настройках телефона.
 * Учитывает системные настройки даты Android (DateFormat.getDateFormatOrder / DateFormat.getDateFormat)
 * и системную локаль устройства, независимо от выбранного языка внутри приложения.
 */
fun isMonthFirstDateOrder(context: Context? = null): Boolean {
    // 1. Проверяем системный порядок компонентов даты через android.text.format.DateFormat
    try {
        val sysContext = getSystemContext(context)
        if (sysContext != null) {
            val order = android.text.format.DateFormat.getDateFormatOrder(sysContext)
            if (order.isNotEmpty()) {
                val mIdx = order.indexOf('M')
                val dIdx = order.indexOf('d')
                val yIdx = order.indexOf('y')
                if (mIdx != -1 && dIdx != -1) {
                    // Месяц идёт перед днём и перед годом (например, M-d-y)
                    if (mIdx < dIdx && (yIdx == -1 || mIdx < yIdx)) {
                        return true
                    }
                    if (dIdx < mIdx) {
                        return false
                    }
                }
            }

            val df = android.text.format.DateFormat.getDateFormat(sysContext)
            if (df is SimpleDateFormat) {
                val pattern = df.toPattern()
                val mIdx = pattern.indexOf('M')
                val dIdx = pattern.indexOf('d')
                val yIdx = pattern.indexOf('y')
                if (mIdx != -1 && dIdx != -1) {
                    if (mIdx < dIdx && (yIdx == -1 || mIdx < yIdx)) {
                        return true
                    }
                    if (dIdx < mIdx) {
                        return false
                    }
                }
            }
        }
    } catch (e: Throwable) {
        // Игнорируем и переходим к проверке через системную локаль
    }

    // 2. Проверяем паттерн системной локали телефона через java.text.DateFormat
    try {
        val systemLocale = getSystemLocale()
        val javaDf = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, systemLocale)
        if (javaDf is SimpleDateFormat) {
            val pattern = javaDf.toPattern()
            val mIdx = pattern.indexOf('M')
            val dIdx = pattern.indexOf('d')
            val yIdx = pattern.indexOf('y')
            if (mIdx != -1 && dIdx != -1) {
                if (mIdx < dIdx && (yIdx == -1 || mIdx < yIdx)) {
                    return true
                }
                if (dIdx < mIdx) {
                    return false
                }
            }
        }
        return isMonthFirstDateOrder(systemLocale)
    } catch (e: Throwable) {
        return false
    }
}

/**
 * Проверяет, принят ли порядок месяц-день-год для конкретной локали (по умолчанию для США).
 */
fun isMonthFirstDateOrder(locale: Locale): Boolean {
    val country = locale.country.uppercase()
    if (country == "US") return true
    val lang = locale.language.lowercase()
    if (country.isEmpty() && lang == "en") return true
    return false
}

/**
 * Composable-функция для определения формата дат на основе системных настроек телефона.
 */
@Composable
fun isMonthFirstDateOrder(): Boolean {
    val context = LocalContext.current
    return isMonthFirstDateOrder(context)
}

/**
 * Возвращает плейсхолдер для одиночного поля даты с учётом языка интерфейса и настроек телефона.
 */
@Composable
fun datePlaceholder(isMonthFirst: Boolean): String {
    val base = stringResource(R.string.date_placeholder)
    return when {
        base.contains("ДД") || base.contains("ММ") -> {
            if (isMonthFirst) "ММ.ДД.ГГГГ" else "ДД.ММ.ГГГГ"
        }
        base.contains("DD") || base.contains("MM") -> {
            if (isMonthFirst) "MM.DD.YYYY" else "DD.MM.YYYY"
        }
        else -> {
            if (isMonthFirst) "MM.DD.YYYY" else base
        }
    }
}

/**
 * Возвращает плейсхолдер для многострочного поля дат повторов с учётом настроек телефона.
 */
@Composable
fun rereadDatesPlaceholder(isMonthFirst: Boolean): String {
    val base = stringResource(R.string.reread_dates_placeholder)
    return when {
        base.contains("ДД.ММ.ГГГГ") || base.contains("ММ.ДД.ГГГГ") -> {
            val suffix = base.substringAfter("ДД.ММ.ГГГГ", "").ifEmpty {
                base.substringAfter("ММ.ДД.ГГГГ", ", по одной в строке")
            }
            if (isMonthFirst) "ММ.ДД.ГГГГ$suffix" else "ДД.ММ.ГГГГ$suffix"
        }
        base.contains("DD.MM.YYYY") || base.contains("MM.DD.YYYY") -> {
            val suffix = base.substringAfter("MM.DD.YYYY", "").ifEmpty {
                base.substringAfter("DD.MM.YYYY", ", one per line")
            }
            if (isMonthFirst) "MM.DD.YYYY$suffix" else "DD.MM.YYYY$suffix"
        }
        else -> {
            if (isMonthFirst) "MM.DD.YYYY, ..." else base
        }
    }
}

/** YYYY-MM-DD → ДД.ММ.ГГГГ или ММ.ДД.ГГГГ */
fun formatStorageDateToDisplay(date: String, isMonthFirst: Boolean = false): String {
    val parts = date.split("-")
    return if (parts.size == 3) {
        if (isMonthFirst) "${parts[1]}.${parts[2]}.${parts[0]}"
        else "${parts[2]}.${parts[1]}.${parts[0]}"
    } else {
        date
    }
}

/** YYYY-MM-DD → 8 цифр DDMMYYYY или MMDDYYYY для поля ввода. */
fun formatStorageDateForInput(date: String?, isMonthFirst: Boolean = false): String {
    if (date.isNullOrBlank()) return ""
    val clean = date.trim()
    val parts = clean.split("-")
    return if (parts.size == 3) {
        val y = parts[0]
        val m = parts[1].padStart(2, '0')
        val d = parts[2].padStart(2, '0')
        if (isMonthFirst) m + d + y else d + m + y
    } else {
        clean.filter { it.isDigit() }.take(8)
    }
}

/** Список дат хранения → текст для многострочного поля (по одной дате в строке с точками). */
fun formatRereadDatesForDisplay(dates: List<String>, isMonthFirst: Boolean = false): String =
    dates.joinToString("\n") { formatStorageDateToDisplay(it, isMonthFirst) }

/** Список дат хранения → сырой ввод DDMMYYYY или MMDDYYYY для многострочного поля с DateVisualTransformation. */
fun formatRereadDatesForInput(dates: List<String>, isMonthFirst: Boolean = false): String =
    dates.joinToString("\n") { date -> formatStorageDateForInput(date, isMonthFirst) }

/** Форматирование даты для сохранения (только цифры -> YYYY-MM-DD). */
fun formatDateForSave(input: String, isMonthFirst: Boolean = false): String {
    val trimmed = input.trim()
    if (trimmed.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
        return trimmed
    }
    val digits = trimmed.filter { it.isDigit() }
    if (digits.length >= 8) {
        val firstPart = digits.substring(0, 2)
        val secondPart = digits.substring(2, 4)
        val year = digits.substring(4, 8)
        val month = if (isMonthFirst) firstPart else secondPart
        val day = if (isMonthFirst) secondPart else firstPart
        return "$year-$month-$day"
    }
    return ""
}

/** Одна строка (ДД.ММ.ГГГГ, ММ.ДД.ГГГГ или сырые цифры) → YYYY-MM-DD или null. */
fun parseDateLineToStorage(line: String, isMonthFirst: Boolean = false): String? {
    val digits = line.filter { it.isDigit() }
    if (digits.length < 8) return null
    val firstPart = digits.substring(0, 2)
    val secondPart = digits.substring(2, 4)
    val year = digits.substring(4, 8)
    val month = if (isMonthFirst) firstPart else secondPart
    val day = if (isMonthFirst) secondPart else firstPart
    return "$year-$month-$day"
}

/** Многострочный ввод → список дат в формате YYYY-MM-DD. */
fun parseRereadDatesForSave(input: String, isMonthFirst: Boolean = false): List<String> =
    input.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { parseDateLineToStorage(it, isMonthFirst) }
        .toList()

/** Фильтрует ввод многострочного поля дат: сохраняет цифры и переходы на новую строку (максимум 8 цифр на дату). */
fun filterRereadDatesInput(input: String, isMonthFirst: Boolean = false): String =
    input.replace("\r", "")
        .lineSequence()
        .map { line ->
            val trimmed = line.trim()
            val dashParts = trimmed.split("-")
            if (dashParts.size == 3 && dashParts[0].length == 4) {
                // YYYY-MM-DD -> MMDDYYYY if isMonthFirst, else DDMMYYYY
                val y = dashParts[0]
                val m = dashParts[1].padStart(2, '0')
                val d = dashParts[2].padStart(2, '0')
                if (isMonthFirst) m + d + y else d + m + y
            } else {
                trimmed.filter { it.isDigit() }.take(8)
            }
        }
        .joinToString("\n")
