package com.rn.library.ui.screens

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rn.library.R
import com.rn.library.data.WorkRepository
import com.rn.library.ui.AppSettings
import com.rn.library.ui.Language
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.rn.library.ui.components.ColorPickerDialog
import com.rn.library.ui.components.RainbowColorWheelButton
import com.rn.library.ui.components.bottomNavigationClearance
import com.rn.library.ui.theme.AppButtonColor
import com.rn.library.ui.theme.FieldHighlightColor
import com.rn.library.ui.theme.IconTextColor
import com.rn.library.ui.theme.MainBackgroundColor
import com.rn.library.ui.theme.TitleColorBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    currentTheme: AppTheme,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsEnabledChange: (Boolean) -> Unit,
    useCustomAccent: Boolean = false,
    onUseCustomAccentChange: (Boolean) -> Unit = {},
    customAccentArgb: Int = 0xFF6750A4.toInt(),
    onCustomAccentArgbChange: (Int) -> Unit = {},
    useCustomStatsColor: Boolean = false,
    onUseCustomStatsColorChange: (Boolean) -> Unit = {},
    customStatsArgb: Int = 0xFF7C4DFF.toInt(),
    onCustomStatsArgbChange: (Int) -> Unit = {},
    useCustomIconColor: Boolean = false,
    onUseCustomIconColorChange: (Boolean) -> Unit = {},
    customIconColorArgb: Int = 0xFFEAEAEA.toInt(),
    onCustomIconColorChange: (Int) -> Unit = {},
    useCustomBgColor: Boolean = false,
    onUseCustomBgColorChange: (Boolean) -> Unit = {},
    customBgColorArgb: Int = 0xFF1B1B1B.toInt(),
    onCustomBgColorArgbChange: (Int) -> Unit = {},
    useCustomTextColor: Boolean = false,
    onUseCustomTextColorChange: (Boolean) -> Unit = {},
    customTextColorArgb: Int = 0xFFEDEDED.toInt(),
    onCustomTextColorArgbChange: (Int) -> Unit = {},
    useCustomButtonColor: Boolean = false,
    onUseCustomButtonColorChange: (Boolean) -> Unit = {},
    customButtonColorArgb: Int = 0xFF6750A4.toInt(),
    onCustomButtonColorArgbChange: (Int) -> Unit = {},
    booksTabEnabled: Boolean,
    onBooksTabEnabledChange: (Boolean) -> Unit,
    animeTabEnabled: Boolean,
    onAnimeTabEnabledChange: (Boolean) -> Unit,
    mangaTabEnabled: Boolean,
    onMangaTabEnabledChange: (Boolean) -> Unit,
    tvSeriesTabEnabled: Boolean,
    onTvSeriesTabEnabledChange: (Boolean) -> Unit,
    isGridView: Boolean = false,
    onGridViewChange: (Boolean) -> Unit = {},
    onOpenGenreTagScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { WorkRepository(context) }
    val scope = rememberCoroutineScope()
    val mainBackgroundColor = MainBackgroundColor()
    val titleColorBetween = TitleColorBetween()
    val iconTextColor = IconTextColor()
    val dynamicScheme = MaterialTheme.colorScheme
    val settingsSectionCardColor =
        if (dynamicColorsEnabled) dynamicScheme.secondaryContainer else Color(0xFF5A5568)
    val settingsCardTextColor =
        if (dynamicColorsEnabled) {
            if (currentTheme == AppTheme.LIGHT) dynamicScheme.onSurface else dynamicScheme.onSurface
        } else {
            Color.White
        }
    val settingsInsetSurfaceColor =
        if (dynamicColorsEnabled) dynamicScheme.surfaceContainerHighest else Color(0xFF494458)

    var incrementStepText by remember {
        mutableStateOf(AppSettings.getIncrementStep(context).toString())
    }
    var searchOtherSources by remember {
        mutableStateOf(AppSettings.isSearchOtherSourcesEnabled(context))
    }
    var showSearchWarningDialog by remember { mutableStateOf(false) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<com.rn.library.update.GitHubRelease?>(null) }
    var activeSettingsColorPicker by remember { mutableStateOf<SettingsColorTarget?>(null) }
    val scrollState = rememberScrollState()

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(mainBackgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cancel),
                    tint = iconTextColor
                )
            }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings),
                color = titleColorBetween,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val (success, total) = withContext(Dispatchers.IO) {
                                repository.recompressAllCovers(context)
                            }
                            val message = if (total > 0) {
                                context.getString(R.string.covers_optimized_summary, success, total)
                            } else {
                                context.getString(R.string.covers_not_found)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Text(
                    text = stringResource(R.string.optimize_covers),
                    color = settingsCardTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenGenreTagScreen() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Text(
                    text = stringResource(R.string.genres_and_tags_settings),
                    color = settingsCardTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tabs),
                        color = settingsCardTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    tabSwitchRow(stringResource(R.string.tab_books), booksTabEnabled, onBooksTabEnabledChange, currentTheme, dynamicColorsEnabled, settingsCardTextColor)
                    tabSwitchRow(stringResource(R.string.tab_anime), animeTabEnabled, onAnimeTabEnabledChange, currentTheme, dynamicColorsEnabled, settingsCardTextColor)
                    tabSwitchRow(stringResource(R.string.tab_manga), mangaTabEnabled, onMangaTabEnabledChange, currentTheme, dynamicColorsEnabled, settingsCardTextColor)
                    tabSwitchRow(stringResource(R.string.tab_series), tvSeriesTabEnabled, onTvSeriesTabEnabledChange, currentTheme, dynamicColorsEnabled, settingsCardTextColor)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.color_settings_section),
                        color = settingsCardTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Material You switch row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(settingsInsetSurfaceColor)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.dynamic_color_material_you),
                                color = settingsCardTextColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Switch(
                            checked = dynamicColorsEnabled,
                            onCheckedChange = { enabled ->
                                onDynamicColorsEnabledChange(enabled)
                                AppSettings.setDynamicColorsEnabled(context, enabled)
                            },
                            colors = switchColors(currentTheme, dynamicColorsEnabled)
                        )
                    }

                    Text(
                        text = stringResource(R.string.custom_colors),
                        color = settingsCardTextColor.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    // 1. Цвет иконок
                    SettingsColorSettingRow(
                        label = stringResource(R.string.icon_color_title),
                        currentColorArgb = customIconColorArgb,
                        isEnabled = !dynamicColorsEnabled,
                        backgroundColor = settingsInsetSurfaceColor,
                        textColor = settingsCardTextColor,
                        onClick = { activeSettingsColorPicker = SettingsColorTarget.ICONS }
                    )

                    // 2. Цвет текста
                    SettingsColorSettingRow(
                        label = stringResource(R.string.text_color_title),
                        currentColorArgb = customTextColorArgb,
                        isEnabled = !dynamicColorsEnabled,
                        backgroundColor = settingsInsetSurfaceColor,
                        textColor = settingsCardTextColor,
                        onClick = { activeSettingsColorPicker = SettingsColorTarget.TEXT }
                    )

                    // 3. Цвет статистики
                    SettingsColorSettingRow(
                        label = stringResource(R.string.stats_color_title),
                        currentColorArgb = customStatsArgb,
                        isEnabled = !dynamicColorsEnabled,
                        backgroundColor = settingsInsetSurfaceColor,
                        textColor = settingsCardTextColor,
                        onClick = { activeSettingsColorPicker = SettingsColorTarget.STATS }
                    )

                    // 4. Цвет кнопок
                    SettingsColorSettingRow(
                        label = stringResource(R.string.button_color_title),
                        currentColorArgb = customButtonColorArgb,
                        isEnabled = !dynamicColorsEnabled,
                        backgroundColor = settingsInsetSurfaceColor,
                        textColor = settingsCardTextColor,
                        onClick = { activeSettingsColorPicker = SettingsColorTarget.BUTTONS }
                    )

                    // Сбросить по умолчанию
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onUseCustomIconColorChange(false)
                                onCustomIconColorChange(0xFFEAEAEA.toInt())
                                AppSettings.setUseCustomIconColor(context, false)
                                AppSettings.setCustomIconColorArgb(context, 0xFFEAEAEA.toInt())

                                onUseCustomTextColorChange(false)
                                onCustomTextColorArgbChange(0xFFEDEDED.toInt())
                                AppSettings.setUseCustomTextColor(context, false)
                                AppSettings.setCustomTextColorArgb(context, 0xFFEDEDED.toInt())

                                onUseCustomStatsColorChange(false)
                                onCustomStatsArgbChange(0xFF7C4DFF.toInt())
                                AppSettings.setUseCustomStatsColor(context, false)
                                AppSettings.setCustomStatsArgb(context, 0xFF7C4DFF.toInt())

                                onUseCustomButtonColorChange(false)
                                onCustomButtonColorArgbChange(0xFF6750A4.toInt())
                                AppSettings.setUseCustomButtonColor(context, false)
                                AppSettings.setCustomButtonColorArgb(context, 0xFF6750A4.toInt())

                                onUseCustomAccentChange(false)
                                onCustomAccentArgbChange(0xFF6750A4.toInt())
                                AppSettings.setUseCustomAccent(context, false)
                                AppSettings.setCustomAccentArgb(context, 0xFF6750A4.toInt())
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.reset_to_default),
                                color = Color(0xFFFF9E9E),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    var languageMenuExpanded by remember { mutableStateOf(false) }
                    val languages = listOf(
                        Language.ENGLISH to stringResource(R.string.english),
                        Language.RUSSIAN to stringResource(R.string.russian),
                        Language.GERMAN to stringResource(R.string.german),
                        Language.FRENCH to stringResource(R.string.french),
                        Language.SPANISH to stringResource(R.string.spanish),
                        Language.PORTUGUESE to stringResource(R.string.portuguese),
                        Language.CHINESE to stringResource(R.string.chinese),
                        Language.JAPANESE to stringResource(R.string.japanese),
                        Language.KOREAN to stringResource(R.string.korean)
                    )
                    val selectedLanguageLabel = languages.firstOrNull { it.first == currentLanguage }?.second
                        ?: stringResource(R.string.english)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { languageMenuExpanded = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.language),
                            color = settingsCardTextColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedLanguageLabel,
                                    color = settingsCardTextColor,
                                    fontSize = 18.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = settingsCardTextColor
                                )
                            }
                            DropdownMenu(
                                expanded = languageMenuExpanded,
                                onDismissRequest = { languageMenuExpanded = false },
                                shape = RoundedCornerShape(14.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                tonalElevation = 2.dp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val accentColor = MaterialTheme.colorScheme.primary
                                        languages.forEach { (lang, label) ->
                                            val isSelected = currentLanguage == lang
                                            AssistChip(
                                                onClick = {
                                                    onLanguageChange(lang)
                                                    languageMenuExpanded = false
                                                },
                                                label = {
                                                    Text(
                                                        text = label,
                                                        color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = Color.Transparent,
                                                    labelColor = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = AssistChipDefaults.assistChipBorder(
                                                    borderColor = if (isSelected) accentColor else accentColor.copy(alpha = 0.5f),
                                                    borderWidth = if (isSelected) 2.dp else 1.dp,
                                                    enabled = true
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.increment_step_label),
                        color = settingsCardTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                            value = incrementStepText,
                            onValueChange = { newValue: String ->
                                incrementStepText = newValue.filter(Char::isDigit)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                textAlign = TextAlign.Center,
                                color = settingsCardTextColor,
                                fontSize = 16.sp
                            ),
                            modifier = Modifier
                                .width(72.dp)
                                .heightIn(max = 48.dp)
                                .onFocusChanged { state ->
                                    if (!state.isFocused) {
                                        val step = incrementStepText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                        incrementStepText = step.toString()
                                        AppSettings.setIncrementStep(context, step)
                                    }
                                },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = settingsInsetSurfaceColor,
                                unfocusedContainerColor = settingsInsetSurfaceColor,
                                disabledContainerColor = settingsInsetSurfaceColor,
                                focusedIndicatorColor = FieldHighlightColor(),
                                unfocusedIndicatorColor = settingsCardTextColor.copy(alpha = 0.4f),
                                focusedTextColor = settingsCardTextColor,
                                unfocusedTextColor = settingsCardTextColor,
                                cursorColor = FieldHighlightColor()
                            )
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_other_sources_toggle),
                        color = settingsCardTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = searchOtherSources,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                showSearchWarningDialog = true
                            } else {
                                searchOtherSources = false
                                AppSettings.setSearchOtherSourcesEnabled(context, false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = FieldHighlightColor(),
                            uncheckedThumbColor = settingsCardTextColor.copy(alpha = 0.6f),
                            uncheckedTrackColor = settingsInsetSurfaceColor
                        )
                    )
                }
            }


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !checkingUpdates) {
                        checkingUpdates = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                com.rn.library.update.GitHubUpdateChecker.check(context)
                            }
                            checkingUpdates = false
                            when (result) {
                                is com.rn.library.update.UpdateCheckResult.UpToDate -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.update_up_to_date, result.currentVersion),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.rn.library.update.UpdateCheckResult.Available -> {
                                    availableRelease = result.release
                                }
                                is com.rn.library.update.UpdateCheckResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.update_error),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = settingsSectionCardColor)
            ) {
                Text(
                    text = if (checkingUpdates) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.check_updates)
                    },
                    color = settingsCardTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else bottomNavigationClearance()))
        }
    }


        availableRelease?.let { release ->
            AlertDialog(
                onDismissRequest = { availableRelease = null },
                title = { Text(stringResource(R.string.update_available_title), color = titleColorBetween) },
                text = {
                    Text(
                        stringResource(
                            R.string.update_available_message,
                            com.rn.library.update.GitHubUpdateChecker.currentVersionName(context),
                            release.tag
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetUrl = release.htmlUrl.ifBlank {
                                "https://github.com/${com.rn.library.update.GitHubUpdateChecker.OWNER}/${com.rn.library.update.GitHubUpdateChecker.REPO}/releases/latest"
                            }
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetUrl)).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                            }
                            availableRelease = null
                        }
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { availableRelease = null }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showSearchWarningDialog) {
            val scheme = MaterialTheme.colorScheme
            AlertDialog(
                onDismissRequest = { showSearchWarningDialog = false },
                title = { Text(stringResource(R.string.search_other_sources_warning_title)) },
                text = { Text(stringResource(R.string.search_other_sources_warning_message)) },
                containerColor = scheme.surface,
                titleContentColor = scheme.onSurface,
                textContentColor = scheme.onSurfaceVariant,
                confirmButton = {
                    TextButton(
                        onClick = {
                            searchOtherSources = true
                            AppSettings.setSearchOtherSourcesEnabled(context, true)
                            showSearchWarningDialog = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = AppButtonColor(),
                            contentColor = scheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.search_other_sources_enable))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSearchWarningDialog = false },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = scheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        activeSettingsColorPicker?.let { target ->
            val titleRes = when (target) {
                SettingsColorTarget.ICONS -> R.string.icon_color_title
                SettingsColorTarget.TEXT -> R.string.text_color_title
                SettingsColorTarget.STATS -> R.string.stats_color_title
                SettingsColorTarget.BUTTONS -> R.string.button_color_title
            }
            val initialArgb = when (target) {
                SettingsColorTarget.ICONS -> customIconColorArgb
                SettingsColorTarget.TEXT -> customTextColorArgb
                SettingsColorTarget.STATS -> customStatsArgb
                SettingsColorTarget.BUTTONS -> customButtonColorArgb
            }
            val dialogBg = dynamicScheme.surface
            val dialogContentColor = if (useCustomTextColor && !dynamicColorsEnabled) {
                Color(customTextColorArgb)
            } else {
                dynamicScheme.onSurface
            }

            ColorPickerDialog(
                title = stringResource(titleRes),
                initialColorArgb = initialArgb,
                backgroundColor = dialogBg,
                contentColor = dialogContentColor,
                onDismiss = { activeSettingsColorPicker = null },
                onColorSelected = { newArgb ->
                    when (target) {
                        SettingsColorTarget.ICONS -> {
                            onUseCustomIconColorChange(true)
                            onCustomIconColorChange(newArgb)
                            AppSettings.setUseCustomIconColor(context, true)
                            AppSettings.setCustomIconColorArgb(context, newArgb)
                        }
                        SettingsColorTarget.TEXT -> {
                            onUseCustomTextColorChange(true)
                            onCustomTextColorArgbChange(newArgb)
                            AppSettings.setUseCustomTextColor(context, true)
                            AppSettings.setCustomTextColorArgb(context, newArgb)
                        }
                        SettingsColorTarget.STATS -> {
                            onUseCustomStatsColorChange(true)
                            onCustomStatsArgbChange(newArgb)
                            AppSettings.setUseCustomStatsColor(context, true)
                            AppSettings.setCustomStatsArgb(context, newArgb)
                        }
                        SettingsColorTarget.BUTTONS -> {
                            onUseCustomButtonColorChange(true)
                            onCustomButtonColorArgbChange(newArgb)
                            AppSettings.setUseCustomButtonColor(context, true)
                            AppSettings.setCustomButtonColorArgb(context, newArgb)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun tabSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    currentTheme: AppTheme,
    dynamicColorsEnabled: Boolean,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = textColor, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = switchColors(currentTheme, dynamicColorsEnabled)
        )
    }
}

@Composable
private fun switchColors(currentTheme: AppTheme, dynamicColorsEnabled: Boolean) = SwitchDefaults.colors(
    checkedThumbColor = if (dynamicColorsEnabled) MaterialTheme.colorScheme.onPrimary else Color.White,
    checkedTrackColor = when {
        dynamicColorsEnabled -> MaterialTheme.colorScheme.primary
        currentTheme == AppTheme.DARK -> Color(0xFF494458)
        else -> Color(0xFF8A84A3)
    },
    uncheckedThumbColor = if (dynamicColorsEnabled) {
        MaterialTheme.colorScheme.outline
    } else if (currentTheme == AppTheme.DARK) {
        Color(0xFF757575)
    } else {
        Color(0xFFBDBDBD)
    },
    uncheckedTrackColor = if (dynamicColorsEnabled) {
        MaterialTheme.colorScheme.surfaceVariant
    } else if (currentTheme == AppTheme.DARK) {
        Color(0xFF2A2A2A)
    } else {
        Color(0xFFE0E0E0)
    }
)

private enum class SettingsColorTarget {
    ICONS,
    TEXT,
    STATS,
    BUTTONS
}

@Composable
private fun SettingsColorSettingRow(
    label: String,
    currentColorArgb: Int,
    isEnabled: Boolean,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isEnabled) textColor else textColor.copy(alpha = 0.4f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(currentColorArgb))
                    .border(1.dp, textColor.copy(alpha = 0.35f), CircleShape)
            )

            RainbowColorWheelButton(
                onClick = onClick,
                size = 36.dp
            )
        }
    }
}
