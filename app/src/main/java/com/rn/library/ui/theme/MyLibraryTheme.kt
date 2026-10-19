package com.rn.library.ui.theme

import android.os.Build
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class AppColors(
    val panelColor: Color,
    val iconTextColor: Color,
    val mainBackgroundColor: Color,
    val searchBarColor: Color,
    val titleColorBetween: Color,
    val tabCircleColor: Color,
    val selectedTabColor: Color,
    val workTitleColor: Color,
    val bottomPanelLabelColor: Color,
    val bottomPanelIconColor: Color,
    val statsActivityColor: Color,
    /** Фон круга выбранной вкладки без Material You */
    val staticTabBackgroundColor: Color,
    /** Иконки и подписи выбранной вкладки без Material You */
    val staticTabContentColor: Color,
    /** Heatmap и столбцы «Активность по периодам» без Material You */
    val staticChartAccentColor: Color,
    val buttonColor: Color,
    val parserIconColor: Color,
    val fieldHighlightColor: Color,
    val isCustomButtonColorActive: Boolean
)

private val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        panelColor = Color(0xFF111111),
        iconTextColor = Color(0xFFEAEAEA),
        mainBackgroundColor = Color(0xFF1A1A1A),
        searchBarColor = Color(0xFF2A2A2A),
        titleColorBetween = Color(0xFFEAEAEA),
        tabCircleColor = Color(0xFF2C2C2C),
        selectedTabColor = Color(0xFF6750A4),
        workTitleColor = Color(0xFFEAEAEA),
        bottomPanelLabelColor = Color(0xFFBDBDBD),
        bottomPanelIconColor = Color(0xFFBDBDBD),
        statsActivityColor = Color(0xFF7C4DFF),
        staticTabBackgroundColor = Color(0xFF494456),
        staticTabContentColor = Color(0xFFE8DEF7),
        staticChartAccentColor = Color(0xFFE8DEF7),
        buttonColor = Color(0xFF6750A4),
        parserIconColor = Color(0xFF6750A4),
        fieldHighlightColor = Color(0xFF6750A4),
        isCustomButtonColorActive = false
    )
}

@Composable
fun MyLibraryTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    palette: ThemePalette = ThemePalette.DEFAULT,
    useCustomAccent: Boolean = false,
    customAccentArgb: Int = 0,
    useCustomStatsColor: Boolean = false,
    customStatsArgb: Int = 0,
    useCustomIconColor: Boolean = false,
    customIconColorArgb: Int = 0,
    useCustomBgColor: Boolean = false,
    customBgColorArgb: Int = 0,
    useCustomTextColor: Boolean = false,
    customTextColorArgb: Int = 0,
    useCustomButtonColor: Boolean = false,
    customButtonColorArgb: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val accent = if (useCustomAccent) Color(customAccentArgb) else when (palette) {
        ThemePalette.DEFAULT -> Color(0xFF6750A4)
        ThemePalette.OCEAN -> Color(0xFF1E88E5)
        ThemePalette.SUNSET -> Color(0xFFFF7043)
    }

    val customStats = if (useCustomStatsColor) Color(customStatsArgb) else null
    val customIcon = if (useCustomIconColor) Color(customIconColorArgb) else null
    val customText = if (useCustomTextColor) Color(customTextColorArgb) else null
    val customButton = if (useCustomButtonColor) Color(customButtonColorArgb) else null

    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = customStats ?: Color(0xFF7C4DFF),
            background = Color(0xFF1B1B1B),
            surface = Color(0xFF1B1B1B),
            onBackground = customText ?: Color(0xFFEDEDED),
            onSurface = customText ?: Color(0xFFEDEDED)
        )
        else -> lightColorScheme(
            primary = accent,
            secondary = accent,
            tertiary = customStats ?: Color(0xFF7C4DFF),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            onBackground = customText ?: Color(0xFF202020),
            onSurface = customText ?: Color(0xFF202020)
        )
    }

    // Приоритет цвета статистики: если включён пользовательский, используем его, иначе Material You / default
    val finalStatsColor = when {
        useCustomStatsColor && customStats != null -> customStats
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> scheme.tertiary
        else -> Color(0xFF7C4DFF)
    }

    // Иконки и текст полностью изолированы друг от друга
    val iconColor = customIcon ?: (if (darkTheme) Color(0xFFEAEAEA) else Color(0xFF202020))
    val text = customText ?: (if (darkTheme) Color(0xFFEDEDED) else Color(0xFF202020))

    // Выделение полей ввода: при пользовательском цвете текста — он, иначе primary из темы / Material You
    val fieldHighlight = if (useCustomTextColor && customText != null) customText else scheme.primary

    // Цвет кнопок: при пользовательском цвете кнопок — он, иначе primary
    val resolvedButtonColor = if (useCustomButtonColor && customButton != null) customButton else scheme.primary

    // В режиме списка цвет названий произведений совпадает с режимом блоков при Material You + поддерживает кастомный цвет
    val resolvedWorkTitleColor = when {
        useCustomTextColor && customText != null -> customText
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context).secondaryContainer
        else -> if (darkTheme) Color(0xFFEDEDED) else Color(0xFF202020)
    }

    val isCustomBtnActive = useCustomButtonColor && customButton != null

    // Иконка парсера: при Material You принимает динамический цвет системы, при кастомном цвете иконок — его, иначе цвет темы (scheme.primary / accent)
    val resolvedParserIconColor = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> scheme.primary
        useCustomIconColor && customIcon != null -> customIcon
        else -> scheme.primary
    }

    val appColors = if (darkTheme) {
        val bg = Color(0xFF1B1B1B)
        val searchBar = Color(0xFF2A2A2A)
        AppColors(
            panelColor = bg,
            iconTextColor = iconColor,
            mainBackgroundColor = bg,
            searchBarColor = searchBar,
            titleColorBetween = text,
            tabCircleColor = Color(0xFF2C2C2C),
            selectedTabColor = accent,
            workTitleColor = resolvedWorkTitleColor,
            bottomPanelLabelColor = if (useCustomTextColor) text.copy(alpha = 0.8f) else Color(0xFFBDBDBD),
            bottomPanelIconColor = if (useCustomIconColor) iconColor else Color(0xFFBDBDBD),
            statsActivityColor = finalStatsColor,
            staticTabBackgroundColor = Color(0xFF494456),
            staticTabContentColor = Color(0xFFE8DEF7),
            staticChartAccentColor = finalStatsColor,
            buttonColor = resolvedButtonColor,
            parserIconColor = resolvedParserIconColor,
            fieldHighlightColor = fieldHighlight,
            isCustomButtonColorActive = isCustomBtnActive
        )
    } else {
        val bg = Color(0xFFFFFFFF)
        val searchBar = Color(0xFFF0F0F0)
        AppColors(
            panelColor = bg,
            iconTextColor = iconColor,
            mainBackgroundColor = bg,
            searchBarColor = searchBar,
            titleColorBetween = text,
            tabCircleColor = Color(0xFFE6E6E6),
            selectedTabColor = accent,
            workTitleColor = resolvedWorkTitleColor,
            bottomPanelLabelColor = if (useCustomTextColor) text.copy(alpha = 0.8f) else Color(0xFF757575),
            bottomPanelIconColor = if (useCustomIconColor) iconColor else Color(0xFF757575),
            statsActivityColor = finalStatsColor,
            staticTabBackgroundColor = Color(0xFFFFDBD7),
            staticTabContentColor = Color(0xFFF04E4F),
            staticChartAccentColor = finalStatsColor,
            buttonColor = resolvedButtonColor,
            parserIconColor = resolvedParserIconColor,
            fieldHighlightColor = fieldHighlight,
            isCustomButtonColorActive = isCustomBtnActive
        )
    }

    val textSelectionColors = remember(fieldHighlight) {
        TextSelectionColors(
            handleColor = fieldHighlight,
            backgroundColor = fieldHighlight.copy(alpha = 0.4f)
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalTextSelectionColors provides textSelectionColors
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}

@Composable fun PanelColor(): Color = LocalAppColors.current.panelColor
@Composable fun IconTextColor(): Color = LocalAppColors.current.iconTextColor
@Composable fun MainBackgroundColor(): Color = LocalAppColors.current.mainBackgroundColor
@Composable fun SearchBarColor(): Color = LocalAppColors.current.searchBarColor
@Composable fun TitleColorBetween(): Color = LocalAppColors.current.titleColorBetween
@Composable fun TabCircleColor(): Color = LocalAppColors.current.tabCircleColor
@Composable fun SelectedTabColor(): Color = LocalAppColors.current.selectedTabColor
@Composable fun WorkTitleColor(): Color = LocalAppColors.current.workTitleColor
@Composable fun BottomPanelLabelColor(): Color = LocalAppColors.current.bottomPanelLabelColor
@Composable fun BottomPanelIconColor(): Color = LocalAppColors.current.bottomPanelIconColor
@Composable fun StatsActivityColor(): Color = LocalAppColors.current.statsActivityColor
@Composable fun StaticTabBackgroundColor(): Color = LocalAppColors.current.staticTabBackgroundColor
@Composable fun StaticTabContentColor(): Color = LocalAppColors.current.staticTabContentColor
@Composable fun StaticChartAccentColor(): Color = LocalAppColors.current.staticChartAccentColor
@Composable fun AppButtonColor(): Color = LocalAppColors.current.buttonColor
@Composable fun ParserIconColor(): Color = LocalAppColors.current.parserIconColor
@Composable fun FieldHighlightColor(): Color = LocalAppColors.current.fieldHighlightColor
@Composable fun IsCustomButtonColorActive(): Boolean = LocalAppColors.current.isCustomButtonColorActive

