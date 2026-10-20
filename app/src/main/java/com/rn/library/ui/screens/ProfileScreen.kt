package com.rn.library.ui.screens

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.content.Intent
import android.widget.Toast
import com.rn.library.R
import com.rn.library.data.ActivityDeltaEvent
import com.rn.library.data.ActivityStatisticsFormat
import com.rn.library.data.Work
import com.rn.library.data.WorkRepository
import com.rn.library.data.WorkStatus
import com.rn.library.data.WorkType
import com.rn.library.data.readProgressUnits
import com.rn.library.data.watchedProgressUnits
import com.rn.library.ui.Language
import com.rn.library.ui.workStatusLabel
import com.rn.library.ui.workTypeLabel
import com.rn.library.ui.theme.MainBackgroundColor
import com.rn.library.ui.theme.ProfileChartAccentColor
import com.rn.library.ui.theme.TitleColorBetween
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class AppTheme {
    LIGHT,
    DARK
}

@Composable
fun ProfileScreen(
    currentLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    currentTheme: AppTheme = AppTheme.DARK,
    onThemeChange: (AppTheme) -> Unit = {},
    dynamicColorsEnabled: Boolean = false,
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
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
    booksTabEnabled: Boolean = true,
    onBooksTabEnabledChange: (Boolean) -> Unit = {},
    animeTabEnabled: Boolean = true,
    onAnimeTabEnabledChange: (Boolean) -> Unit = {},
    mangaTabEnabled: Boolean = true,
    onMangaTabEnabledChange: (Boolean) -> Unit = {},
    tvSeriesTabEnabled: Boolean = true,
    onTvSeriesTabEnabledChange: (Boolean) -> Unit = {},
    onWorksUpdated: () -> Unit = {},
    onAddWorkRequested: () -> Unit = {},
    /** Увеличивается при повторном нажатии на вкладку «Профиль» в нижней панели — закрыть вложенные экраны. */
    profileReselectSignal: Int = 0,
    onScrollStateChange: ((Boolean) -> Unit)? = null,
    // Режим отображения произведений в вкладках: список (false) или блоки (true)
    isGridView: Boolean = false,
    onGridViewChange: (Boolean) -> Unit = {},
    works: List<Work> = emptyList(),
    modifier: Modifier = Modifier
) {
    val mainBackgroundColor = MainBackgroundColor()
    val titleColorBetween = TitleColorBetween()
    val dynamicScheme = MaterialTheme.colorScheme
    val profileSectionCardColor = com.rn.library.ui.theme.ProfileSectionCardColor(dynamicColorsEnabled)
    val profileSectionTextColor = com.rn.library.ui.theme.ProfileSectionTextColor(dynamicColorsEnabled, currentTheme)
    val context = LocalContext.current
    val importNothingImported = stringResource(R.string.import_nothing_imported)
    val exportErrorText = stringResource(R.string.export_error)
    val repository = remember { WorkRepository(context) }
    var localWorks by remember { mutableStateOf(works) }
    var activityStatsEpoch by remember { mutableIntStateOf(0) }
    var activityEvents by remember { mutableStateOf<List<ActivityDeltaEvent>>(emptyList()) }

    LaunchedEffect(works) {
        if (works.isNotEmpty()) {
            localWorks = works
        }
    }

    LaunchedEffect(localWorks, activityStatsEpoch) {
        activityEvents = withContext(Dispatchers.IO) { repository.loadActivityEvents() }
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val onWorksUpdatedState = rememberUpdatedState(onWorksUpdated)

    LaunchedEffect(profileReselectSignal) {
        if (profileReselectSignal <= 0) return@LaunchedEffect
        showSettings = false
        showGuide = false
        showImportDialog = false
    }
    val importBooksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            Toast.makeText(context, importNothingImported, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val (ok, total, failedNames) = withContext(Dispatchers.IO) {
                var successCount = 0
                val failed = mutableListOf<String>()
                uris.forEach { uri ->
                    val result = repository.importBookFromUriDetailed(context, uri)
                    if (result.work != null) {
                        successCount++
                    } else {
                        val label = repository.getFileDisplayName(context, uri)
                            ?: uri.lastPathSegment
                            ?: "unknown"
                        val reason = result.errorMessage?.takeIf { it.isNotBlank() }
                        failed += if (reason != null) "$label ($reason)" else label
                    }
                }
                Triple(successCount, uris.size, failed)
            }
            localWorks = repository.getAllWorks()
            onWorksUpdatedState.value()
            val msg = if (ok == 0 && failedNames.isNotEmpty()) {
                val preview = failedNames.take(2).joinToString(", ")
                val tail = if (failedNames.size > 2) " +${failedNames.size - 2}" else ""
                "${importNothingImported}. ${context.getString(R.string.import_errors_prefix, "$preview$tail")}"
            } else if (ok == 0) {
                importNothingImported
            } else {
                val base = context.getString(R.string.import_backup_summary, ok, total)
                if (failedNames.isNotEmpty()) {
                    val preview = failedNames.take(2).joinToString(", ")
                    val tail = if (failedNames.size > 2) " +${failedNames.size - 2}" else ""
                    "$base. ${context.getString(R.string.import_errors_prefix, "$preview$tail")}"
                } else {
                    base
                }
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val importExportedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri == null) {
            Toast.makeText(context, importNothingImported, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            val (ok, total, failedNames) = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                    // Не критично: разовый доступ всё равно может сработать
                }
                repository.importExportedBackupsFromTree(
                    context = context,
                    treeUri = treeUri,
                )
            }
            val updated = repository.getAllWorks()
            localWorks = updated
            activityStatsEpoch++
            onWorksUpdatedState.value()
            val msg = if (ok == 0 && failedNames.isNotEmpty()) {
                val preview = failedNames.take(2).joinToString(", ")
                val tail = if (failedNames.size > 2) " +${failedNames.size - 2}" else ""
                "${importNothingImported}. ${context.getString(R.string.import_errors_prefix, "$preview$tail")}"
            } else if (ok == 0) {
                importNothingImported
            } else {
                val base = context.getString(R.string.import_backup_summary, ok, total)
                if (failedNames.isNotEmpty()) {
                    val preview = failedNames.take(2).joinToString(", ")
                    val tail = if (failedNames.size > 2) " +${failedNames.size - 2}" else ""
                    "$base. ${context.getString(R.string.import_errors_prefix, "$preview$tail")}"
                } else {
                    base
                }
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val scrollState = rememberScrollState()
    
    // Track scroll to hide/show header
    LaunchedEffect(scrollState.value) {
        val shouldHide = scrollState.value > 50
        onScrollStateChange?.invoke(shouldHide)
    }
    
    // Reset header visibility when component is disposed
    DisposableEffect(Unit) {
        onDispose {
            onScrollStateChange?.invoke(false)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .background(mainBackgroundColor)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Statistics Card
            StatisticsCard(
                works = localWorks,
                activityEvents = activityEvents,
                titleColorBetween = titleColorBetween,
                currentTheme = currentTheme,
                dynamicColorsEnabled = dynamicColorsEnabled,
                useCustomStatsColor = useCustomStatsColor,
                customStatsArgb = customStatsArgb,
                notesCount = 0,
                currentLanguage = currentLanguage
            )

            // Add Work Button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddWorkRequested() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = profileSectionCardColor
                )
            ) {
                Text(
                    text = stringResource(R.string.add_work),
                    color = profileSectionTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Export
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        coroutineScope.launch {
                            val worksExportPath = withContext(Dispatchers.IO) {
                                repository.exportWorksToDownloads()
                            }

                            if (worksExportPath != null) {
                                Toast.makeText(
                                    context,
                                    worksExportPath,
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    exportErrorText,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = profileSectionCardColor
                )
            ) {
                Text(
                    text = stringResource(R.string.export_files),
                    color = profileSectionTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Import app files and book formats
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showImportDialog = true
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = profileSectionCardColor)
            ) {
                Text(
                    text = stringResource(R.string.import_backup),
                    color = profileSectionTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text(stringResource(R.string.import_dialog_title), color = titleColorBetween) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showImportDialog = false
                                importBooksLauncher.launch(
                                    arrayOf(
                                        "application/epub+zip",
                                        "application/pdf",
                                        "application/x-fictionbook+xml",
                                        "text/xml",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            }
                        ) {
                            Text(stringResource(R.string.import_pick_books))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showImportDialog = false
                                importExportedLauncher.launch(null)
                            }
                        ) {
                            Text(stringResource(R.string.import_from_exported))
                        }
                    }
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettings = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = profileSectionCardColor)
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    color = profileSectionTextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Bottom spacer to ensure content scrolls cleanly
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            Spacer(modifier = Modifier.height(if (isLandscape) 14.dp else 106.dp))
        }
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(animationSpec = tween(20)) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(20)
            ),
            exit = fadeOut(animationSpec = tween(20)) + slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(20)
            )
        ) {
            SettingsScreen(
                onBack = { showSettings = false },
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange,
                currentTheme = currentTheme,
                dynamicColorsEnabled = dynamicColorsEnabled,
                onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
                useCustomAccent = useCustomAccent,
                onUseCustomAccentChange = onUseCustomAccentChange,
                customAccentArgb = customAccentArgb,
                onCustomAccentArgbChange = onCustomAccentArgbChange,
                useCustomStatsColor = useCustomStatsColor,
                onUseCustomStatsColorChange = onUseCustomStatsColorChange,
                customStatsArgb = customStatsArgb,
                onCustomStatsArgbChange = onCustomStatsArgbChange,
                useCustomIconColor = useCustomIconColor,
                onUseCustomIconColorChange = onUseCustomIconColorChange,
                customIconColorArgb = customIconColorArgb,
                onCustomIconColorChange = onCustomIconColorChange,
                useCustomBgColor = useCustomBgColor,
                onUseCustomBgColorChange = onUseCustomBgColorChange,
                customBgColorArgb = customBgColorArgb,
                onCustomBgColorArgbChange = onCustomBgColorArgbChange,
                useCustomTextColor = useCustomTextColor,
                onUseCustomTextColorChange = onUseCustomTextColorChange,
                customTextColorArgb = customTextColorArgb,
                onCustomTextColorArgbChange = onCustomTextColorArgbChange,
                useCustomButtonColor = useCustomButtonColor,
                onUseCustomButtonColorChange = onUseCustomButtonColorChange,
                customButtonColorArgb = customButtonColorArgb,
                onCustomButtonColorArgbChange = onCustomButtonColorArgbChange,
                booksTabEnabled = booksTabEnabled,
                onBooksTabEnabledChange = onBooksTabEnabledChange,
                animeTabEnabled = animeTabEnabled,
                onAnimeTabEnabledChange = onAnimeTabEnabledChange,
                mangaTabEnabled = mangaTabEnabled,
                onMangaTabEnabledChange = onMangaTabEnabledChange,
                tvSeriesTabEnabled = tvSeriesTabEnabled,
                onTvSeriesTabEnabledChange = onTvSeriesTabEnabledChange,
                isGridView = isGridView,
                onGridViewChange = onGridViewChange,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showGuide,
            enter = fadeIn(animationSpec = tween(20)) + slideInVertically(
                initialOffsetY = { it / 3 },
                animationSpec = tween(20)
            ),
            exit = fadeOut(animationSpec = tween(20)) + slideOutVertically(
                targetOffsetY = { it / 3 },
                animationSpec = tween(20)
            )
        ) {
            AppGuideScreen(
                onBack = { showGuide = false },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun LanguageOption(
    language: Language,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    currentTheme: AppTheme = AppTheme.DARK,
    modifier: Modifier = Modifier
) {
    val titleColorBetween = TitleColorBetween()
    val mainBackgroundColor = MainBackgroundColor()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                if (currentTheme == AppTheme.DARK) Color(0xFF494458) else Color(0xFF5A5568)
            } else {
                if (currentTheme == AppTheme.LIGHT) Color(0xFF2A2A2A) else Color(0xFFF5F5F5)
            }
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else titleColorBetween,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun StatisticsCard(
    works: List<Work>,
    activityEvents: List<ActivityDeltaEvent>,
    titleColorBetween: Color,
    currentTheme: AppTheme = AppTheme.DARK,
    dynamicColorsEnabled: Boolean = false,
    useCustomStatsColor: Boolean = false,
    customStatsArgb: Int = 0xFF7C4DFF.toInt(),
    notesCount: Int = 0,
    currentLanguage: Language = Language.ENGLISH
) {
    var selectedTypeFilter by remember { mutableStateOf<WorkType?>(null) }

    val computedStats = remember(works, activityEvents, selectedTypeFilter) {
        val filteredForStatus = selectedTypeFilter?.let { type ->
            works.filter { it.type == type }
        } ?: works
        val byType = works.groupBy { it.type }
        val byStatus = filteredForStatus.groupBy { it.status }
        ComputedStats(
            byType = byType,
            filteredForStatus = filteredForStatus,
            byStatus = byStatus,
            activityEntries = buildActivityEntries(filteredForStatus, activityEvents),
            totalReadUnits = filteredForStatus.sumOf { readProgressUnits(it) }.roundToInt(),
            totalWatchedUnits = filteredForStatus.sumOf { watchedProgressUnits(it) }.roundToInt()
        )
    }
    val byType = computedStats.byType
    val mangaCount = byType[WorkType.MANGA]?.size ?: 0
    val booksCount = byType[WorkType.BOOK]?.size ?: 0
    val animeCount = byType[WorkType.ANIME]?.size ?: 0
    val seriesCount = byType[WorkType.SERIES]?.size ?: 0
    val totalItems = works.size + notesCount
    val filteredForStatus = computedStats.filteredForStatus
    val totalForStatus = filteredForStatus.size
    val byStatus = computedStats.byStatus
    val activityEntries = computedStats.activityEntries
    val totalReadUnits = computedStats.totalReadUnits
    val totalWatchedUnits = computedStats.totalWatchedUnits

    // Цвета для диаграммы по статусам (оставляем цвета)
    val statusColors = mapOf(
        WorkStatus.IN_PLANS to Color(0xFF8E6687),      // rgb(142, 102, 147)
        WorkStatus.ABANDONED to Color(0xFFFF5F5A),     // rgb(255, 95, 90)
        WorkStatus.READING to Color(0xFF7179A4),       // rgb(113, 121, 164)
        WorkStatus.WATCHING to Color(0xFF7179A4),      // rgb(113, 121, 164)
        WorkStatus.READ to Color(0xFF79C77C),          // rgb(121, 199, 124)
        WorkStatus.WATCHED to Color(0xFF79C77C)        // rgb(121, 199, 124)
    )

    // Цвета для типов произведений (не пересекаются с цветами статусов)
    val typeColors = mapOf(
        WorkType.BOOK to Color(0xFF4E89AE),   // синий
        WorkType.ANIME to Color(0xFFF6AE2D),  // жёлто-оранжевый
        WorkType.MANGA to Color(0xFF55A630),  // зелёный
        WorkType.SERIES to Color(0xFFB56576)  // розовато-фиолетовый
    )
    // Цвет для заметок (не пересекается с цветами статусов и типов)
    val notesColor = Color(0xFF9B59B6)  // фиолетовый
    
    val mainBackgroundColor = MainBackgroundColor()
    val statsActivityTint = if (useCustomStatsColor) {
        Color(customStatsArgb)
    } else if (dynamicColorsEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        ProfileChartAccentColor(false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = mainBackgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.statistics),
                color = titleColorBetween,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Диаграмма по типам произведений (монохромная)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.by_type),
                color = titleColorBetween.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            DonutChartWithLegend(
                slices = buildList {
                    if (booksCount > 0) add(DonutSlice(stringResource(R.string.books), booksCount, typeColors[WorkType.BOOK] ?: titleColorBetween))
                    if (animeCount > 0) add(DonutSlice(stringResource(R.string.anime), animeCount, typeColors[WorkType.ANIME] ?: titleColorBetween))
                    if (mangaCount > 0) add(DonutSlice(stringResource(R.string.manga), mangaCount, typeColors[WorkType.MANGA] ?: titleColorBetween))
                    if (seriesCount > 0) add(DonutSlice(stringResource(R.string.tv_series), seriesCount, typeColors[WorkType.SERIES] ?: titleColorBetween))
                    if (notesCount > 0) add(DonutSlice(stringResource(R.string.notes), notesCount, notesColor))
                },
                total = totalItems,
                showColorDots = true,
                titleColorBetween = titleColorBetween
            )

            // Переключатель типов для статистики по статусам
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.by_status),
                color = titleColorBetween.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val spacing = 6.dp
                val typeButtons = listOf<Pair<WorkType?, String>>(
                    null to stringResource(R.string.all_types),
                    WorkType.BOOK to stringResource(R.string.books),
                    WorkType.ANIME to stringResource(R.string.anime),
                    WorkType.MANGA to stringResource(R.string.manga),
                    WorkType.SERIES to stringResource(R.string.tv_series)
                )
                val weightSum = typeButtons.sumOf { (_, label) -> (label.length + 4).coerceAtLeast(6) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    typeButtons.forEach { (type, label) ->
                        val selected = selectedTypeFilter == type
                        val buttonWeight = ((label.length + 4).coerceAtLeast(6)).toFloat() / weightSum.toFloat()
                        Box(
                            modifier = Modifier
                                .weight(buttonWeight, fill = true)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) {
                                        if (dynamicColorsEnabled) MaterialTheme.colorScheme.secondaryContainer
                                        else titleColorBetween
                                    } else {
                                        titleColorBetween.copy(alpha = 0.12f)
                                    }
                                )
                                .clickable { selectedTypeFilter = type }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    if (dynamicColorsEnabled) MaterialTheme.colorScheme.onSecondaryContainer
                                    else if (currentTheme == AppTheme.LIGHT) Color.White else mainBackgroundColor
                                } else {
                                    titleColorBetween
                                },
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Диаграмма по статусам (с цветами, без статуса "Отложено")
            Spacer(modifier = Modifier.height(8.dp))

            // Определяем какие статусы показывать в зависимости от выбранного типа
            val statusLabels = mapOf(
                WorkStatus.IN_PLANS to stringResource(R.string.in_plans),
                WorkStatus.READING to stringResource(R.string.reading),
                WorkStatus.WATCHING to stringResource(R.string.watching),
                WorkStatus.READ to stringResource(R.string.read),
                WorkStatus.WATCHED to stringResource(R.string.watched),
                WorkStatus.ABANDONED to stringResource(R.string.abandoned)
            )
            
            // Для книг и манги показываем READING и READ, для остальных - WATCHING и WATCHED
            // При выборе "Все" объединяем READING/READ с WATCHING/WATCHED
            val slices = when (selectedTypeFilter) {
                WorkType.BOOK, WorkType.MANGA -> listOf(
                    DonutSlice(
                        label = statusLabels[WorkStatus.READING] ?: "",
                        value = (byStatus[WorkStatus.READING]?.size ?: 0),
                        color = statusColors[WorkStatus.READING] ?: titleColorBetween
                    ),
                    DonutSlice(
                        label = statusLabels[WorkStatus.IN_PLANS] ?: "",
                        value = (byStatus[WorkStatus.IN_PLANS]?.size ?: 0),
                        color = statusColors[WorkStatus.IN_PLANS] ?: titleColorBetween
                    ),
                    DonutSlice(
                        label = statusLabels[WorkStatus.READ] ?: "",
                        value = (byStatus[WorkStatus.READ]?.size ?: 0),
                        color = statusColors[WorkStatus.READ] ?: titleColorBetween
                    ),
                    DonutSlice(
                        label = statusLabels[WorkStatus.ABANDONED] ?: "",
                        value = (byStatus[WorkStatus.ABANDONED]?.size ?: 0),
                        color = statusColors[WorkStatus.ABANDONED] ?: titleColorBetween
                    )
                )
                else -> {
                    // Для "Все" или других типов объединяем READING/READ с WATCHING/WATCHED
                    val activeStatusCount = (byStatus[WorkStatus.READING]?.size ?: 0) + 
                                          (byStatus[WorkStatus.WATCHING]?.size ?: 0)
                    val completedStatusCount = (byStatus[WorkStatus.READ]?.size ?: 0) + 
                                               (byStatus[WorkStatus.WATCHED]?.size ?: 0)
                    val activeLabel = if (selectedTypeFilter == null) {
                        // При "Все" используем общий ярлык
                        stringResource(R.string.watching) // или можно сделать "Читаю/Смотрю"
                    } else {
                        statusLabels[WorkStatus.WATCHING] ?: ""
                    }
                    val completedLabel = if (selectedTypeFilter == null) {
                        stringResource(R.string.watched) // или можно сделать "Прочитано/Просмотрено"
                    } else {
                        statusLabels[WorkStatus.WATCHED] ?: ""
                    }
                    
                    listOf(
                        DonutSlice(
                            label = activeLabel,
                            value = activeStatusCount,
                            color = statusColors[WorkStatus.WATCHING] ?: titleColorBetween
                        ),
                        DonutSlice(
                            label = statusLabels[WorkStatus.IN_PLANS] ?: "",
                            value = (byStatus[WorkStatus.IN_PLANS]?.size ?: 0),
                            color = statusColors[WorkStatus.IN_PLANS] ?: titleColorBetween
                        ),
                        DonutSlice(
                            label = completedLabel,
                            value = completedStatusCount,
                            color = statusColors[WorkStatus.WATCHED] ?: titleColorBetween
                        ),
                        DonutSlice(
                            label = statusLabels[WorkStatus.ABANDONED] ?: "",
                            value = (byStatus[WorkStatus.ABANDONED]?.size ?: 0),
                            color = statusColors[WorkStatus.ABANDONED] ?: titleColorBetween
                        )
                    )
                }
            }

            DonutChartWithLegend(
                slices = slices,
                total = totalForStatus,
                showColorDots = true,
                titleColorBetween = titleColorBetween
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.stats_units_title),
                color = titleColorBetween.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            val staticUnitsValueColor = if (currentTheme == AppTheme.DARK) Color(0xFFEDEDED) else Color(0xFF202020)
            StatisticRow(
                label = stringResource(R.string.progress_read_chapters),
                value = totalReadUnits.toString(),
                titleColorBetween = titleColorBetween,
                valueColor = staticUnitsValueColor
            )
            StatisticRow(
                label = stringResource(R.string.progress_watched_episodes),
                value = totalWatchedUnits.toString(),
                titleColorBetween = titleColorBetween,
                valueColor = staticUnitsValueColor
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_heatmap_title),
                color = titleColorBetween.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            ActivityHeatmap(
                entries = activityEntries,
                titleColorBetween = titleColorBetween,
                statsTint = statsActivityTint,
                currentLanguage = currentLanguage
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_period_activity_title),
                color = titleColorBetween.copy(alpha = 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            PeriodActivityChart(
                entries = activityEntries,
                mode = PeriodMode.DAY,
                titleColorBetween = titleColorBetween,
                statsFillColor = statsActivityTint,
                currentLanguage = currentLanguage
            )
            PeriodActivityChart(
                entries = activityEntries,
                mode = PeriodMode.WEEK,
                titleColorBetween = titleColorBetween,
                statsFillColor = statsActivityTint,
                currentLanguage = currentLanguage
            )
            PeriodActivityChart(
                entries = activityEntries,
                mode = PeriodMode.MONTH,
                titleColorBetween = titleColorBetween,
                statsFillColor = statsActivityTint,
                currentLanguage = currentLanguage
            )
        }
    }
}

@Composable
private fun StatisticRow(
    label: String,
    value: String,
    titleColorBetween: Color,
    valueColor: Color = titleColorBetween
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = titleColorBetween.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TypeStatisticRow(
    label: String,
    count: Int,
    total: Int,
    titleColorBetween: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = titleColorBetween.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress indicator
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = if (total > 0) count.toFloat() / total else 0f,
                    modifier = Modifier.size(24.dp),
                    color = titleColorBetween,
                    strokeWidth = 3.dp,
                    trackColor = titleColorBetween.copy(alpha = 0.2f)
                )
                Text(
                    text = count.toString(),
                    color = titleColorBetween,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private data class DonutSlice(
    val label: String,
    val value: Int,
    val color: Color
)

@Composable
private fun DonutChartWithLegend(
    slices: List<DonutSlice>,
    total: Int,
    showColorDots: Boolean,
    titleColorBetween: Color
) {
    val nonEmptySlices = slices.filter { it.value > 0 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            nonEmptySlices.forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showColorDots) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                    }
                    Text(
                        text = slice.label,
                        color = titleColorBetween.copy(alpha = 0.85f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = slice.value.toString(),
                        color = titleColorBetween,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Размер диаграммы адаптируется к ширине экрана
            val chartSize = maxWidth.coerceAtMost(140.dp)
            Canvas(modifier = Modifier.size(chartSize)) {
                val diameter = size.minDimension
                val strokeWidth = diameter * 0.18f
                val radius = (diameter - strokeWidth) / 2f
                val topLeft = Offset(
                    (size.width - 2 * radius) / 2f,
                    (size.height - 2 * radius) / 2f
                )

                // Фон кольца
                drawArc(
                    color = titleColorBetween.copy(alpha = 0.15f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(2 * radius, 2 * radius),
                    topLeft = topLeft
                )

                if (total > 0 && nonEmptySlices.isNotEmpty()) {
                    var startAngle = -90f
                    nonEmptySlices.forEach { slice ->
                        val sweep = 360f * (slice.value.toFloat() / total)
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                            size = Size(2 * radius, 2 * radius),
                            topLeft = topLeft
                        )
                        startAngle += sweep
                    }
                }
            }
        }
    }
}

private data class ActivityEntry(
    val date: LocalDate,
    val readUnits: Double,
    val watchedUnits: Double
)

private data class ComputedStats(
    val byType: Map<WorkType, List<Work>>,
    val filteredForStatus: List<Work>,
    val byStatus: Map<WorkStatus, List<Work>>,
    val activityEntries: List<ActivityEntry>,
    val totalReadUnits: Int,
    val totalWatchedUnits: Int
) {
    companion object {
        fun empty() = ComputedStats(
            byType = emptyMap(),
            filteredForStatus = emptyList(),
            byStatus = emptyMap(),
            activityEntries = emptyList(),
            totalReadUnits = 0,
            totalWatchedUnits = 0
        )
    }
}

private enum class PeriodMode { DAY, WEEK, MONTH }
private data class ActivityTotals(val readUnits: Double, val watchedUnits: Double) {
    val allUnits: Double get() = readUnits + watchedUnits
}
private data class PeriodBarData(
    val label: String,
    val readUnits: Double,
    val watchedUnits: Double,
    val from: LocalDate,
    val to: LocalDate
) {
    val allUnits: Double get() = readUnits + watchedUnits
}

private fun quantizedIntensity(value: Double, max: Double, levels: Int = 12): Float {
    if (value <= 0.0 || max <= 0.0) return 0f
    val normalized = (value / max).coerceIn(0.0, 1.0)
    val bucket = (normalized * levels).toInt().coerceIn(1, levels)
    return bucket.toFloat() / levels.toFloat()
}

/**
 * Вычисляет цвет ячейки Heatmap с выраженным градиентом от минимальной активности до пиковой.
 * Использует гамма-кривую контраста и динамическую модуляцию насыщенности и яркости HSV.
 */
private fun calculateHeatmapColor(
    value: Double,
    maxUnits: Double,
    baseColor: Color,
    tintColor: Color
): Color {
    if (value <= 0.0 || maxUnits <= 0.0) {
        return baseColor.copy(alpha = 0.22f)
    }
    // 25 дискретных уровней квантования
    val normalized = (value / maxUnits).coerceIn(0.0, 1.0)
    val bucket = (normalized * 25).toInt().coerceIn(1, 25)
    val fraction = bucket.toFloat() / 25f

    // Гамма-кривая контраста для лучшей различимости малых и средних значений
    val gamma = Math.pow(fraction.toDouble(), 0.72).toFloat()

    // Преобразование целевого цвета в HSV для управления контрастом и яркостью
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (tintColor.alpha * 255).toInt(),
            (tintColor.red * 255).toInt(),
            (tintColor.green * 255).toInt(),
            (tintColor.blue * 255).toInt()
        ),
        hsv
    )
    val hue = hsv[0]
    val origSat = hsv[1]
    val origVal = hsv[2]

    // На малых значениях — более мягкая насыщенность, на пиках — максимальная яркость
    val sat = (origSat * (0.40f + 0.60f * gamma)).coerceIn(0f, 1f)
    val brightness = (origVal * (0.45f + 0.55f * gamma)).coerceIn(0f, 1f)
    val hsvColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, brightness)))

    val blended = lerp(baseColor, hsvColor, 0.18f + 0.82f * gamma)
    // Дополнительный контрастный световой блик на пиковой активности (верхние 25%)
    return if (gamma > 0.75f) {
        val highlightFraction = ((gamma - 0.75f) / 0.25f).coerceIn(0f, 1f)
        lerp(blended, Color.White, highlightFraction * 0.22f)
    } else {
        blended
    }
}

/**
 * Агрегирует по дням прирост прогресса по произведениям из [visibleWorks]
 * и по импортированной статистике ([ActivityStatisticsFormat.IMPORT_WORK_ID]).
 */
private fun buildActivityEntries(
    visibleWorks: List<Work>,
    events: List<ActivityDeltaEvent>
): List<ActivityEntry> {
    val ids = visibleWorks.map { it.id }.toSet()
    val relevant = events.filter { event ->
        event.workId == ActivityStatisticsFormat.IMPORT_WORK_ID || event.workId in ids
    }
    if (relevant.isEmpty()) return emptyList()
    return relevant
        .groupBy { it.date }
        .map { (date, dayEvents) ->
            ActivityEntry(
                date = date,
                readUnits = dayEvents.sumOf { it.readDelta },
                watchedUnits = dayEvents.sumOf { it.watchDelta }
            )
        }
        .filter { it.readUnits + it.watchedUnits > 0.0 }
}

@Composable
private fun ActivityHeatmap(
    entries: List<ActivityEntry>,
    titleColorBetween: Color,
    statsTint: Color,
    currentLanguage: Language
) {
    val locale = Locale.forLanguageTag(currentLanguage.languageTag)
    val today = LocalDate.now()
    val currentWeekMonday = remember(today) { today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
    val windowStart = remember(today) { today.minusYears(1) }
    val cycleDays = remember(today, windowStart) {
        (ChronoUnit.DAYS.between(windowStart, today) + 1).toInt().coerceAtLeast(1)
    }
    val futureGapWeeks = 8
    val futureGapDays = futureGapWeeks * 7
    val points = remember(entries) {
        entries.groupBy { it.date }.mapValues { (_, dayEntries) ->
            ActivityTotals(
                readUnits = dayEntries.sumOf { it.readUnits },
                watchedUnits = dayEntries.sumOf { it.watchedUnits }
            )
        }
    }
    // Динамический максимум: при новом пике пересчитывается вся шкала интенсивности.
    val maxUnits = remember(points) {
        points.values.maxOfOrNull { it.allUnits }?.coerceAtLeast(1.0) ?: 1.0
    }
    val heatmapBase = MaterialTheme.colorScheme.surfaceVariant
    var selected by remember { mutableStateOf<Pair<LocalDate, ActivityTotals>?>(null) }
    val centerWeekIndex = Int.MAX_VALUE / 2
    val listState = rememberLazyListState()

    // При каждом заходе в профиль показываем текущую неделю + 5 предыдущих.
    LaunchedEffect(today) {
        listState.scrollToItem((centerWeekIndex - 5).coerceAtLeast(0))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(
                count = Int.MAX_VALUE,
                key = { it }
            ) { index ->
                val weekOffset = index - centerWeekIndex
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(7) { day ->
                        val absoluteDate = currentWeekMonday.plusDays((weekOffset * 7L) + day)
                        val date = when {
                            absoluteDate <= today -> {
                                val rawOffset = ChronoUnit.DAYS.between(windowStart, absoluteDate).toInt()
                                val normalizedOffset = Math.floorMod(rawOffset, cycleDays)
                                windowStart.plusDays(normalizedOffset.toLong())
                            }
                            ChronoUnit.DAYS.between(today, absoluteDate).toInt() <= futureGapDays -> {
                                null
                            }
                            else -> {
                                val shiftStart = today.plusDays((futureGapDays + 1).toLong())
                                val shiftOffset = ChronoUnit.DAYS.between(shiftStart, absoluteDate).toInt()
                                val normalizedOffset = Math.floorMod(shiftOffset, cycleDays)
                                windowStart.plusDays(normalizedOffset.toLong())
                            }
                        }
                        val value = points[date] ?: ActivityTotals(0.0, 0.0)
                        val cellColor = calculateHeatmapColor(
                            value = value.allUnits,
                            maxUnits = maxUnits,
                            baseColor = heatmapBase,
                            tintColor = statsTint
                        )
                        val modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cellColor)
                        val clickableModifier = if (date != null) {
                            modifier.clickable { selected = date to value }
                        } else {
                            modifier
                        }
                        Box(modifier = clickableModifier)
                    }
                }
            }
        }
    }

    selected?.let { (date, totals) ->
        val isMonthFirst = isMonthFirstDateOrder()
        val pattern = if (isMonthFirst) "MM.dd.yyyy" else "dd.MM.yyyy"
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern(pattern, locale)),
                    color = titleColorBetween
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${stringResource(R.string.progress_read_chapters)}: ${totals.readUnits.roundToInt()}", color = titleColorBetween.copy(alpha = 0.9f))
                    Text("${stringResource(R.string.progress_watched_episodes)}: ${totals.watchedUnits.roundToInt()}", color = titleColorBetween.copy(alpha = 0.9f))
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PeriodActivityChart(
    entries: List<ActivityEntry>,
    mode: PeriodMode,
    titleColorBetween: Color,
    statsFillColor: Color,
    currentLanguage: Language
) {
    val locale = Locale.forLanguageTag(currentLanguage.languageTag)
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val bars = remember(entries, mode, currentLanguage) {
        when (mode) {
            PeriodMode.DAY -> {
                (0..6).map { index ->
                    val date = weekStart.plusDays(index.toLong())
                    val read = entries.filter { it.date == date }.sumOf { it.readUnits }
                    val watched = entries.filter { it.date == date }.sumOf { it.watchedUnits }
                    PeriodBarData(
                        label = date.format(DateTimeFormatter.ofPattern("EEE", locale)).replaceFirstChar { it.uppercase() }.take(3),
                        readUnits = read,
                        watchedUnits = watched,
                        from = date,
                        to = date
                    )
                }
            }
            PeriodMode.WEEK -> {
                (0..7).map { offset ->
                    val start = weekStart.minusWeeks((7 - offset).toLong())
                    val end = start.plusDays(6)
                    val read = entries.filter { it.date in start..end }.sumOf { it.readUnits }
                    val watched = entries.filter { it.date in start..end }.sumOf { it.watchedUnits }
                    PeriodBarData(
                        label = "${start.dayOfMonth}.${start.monthValue}",
                        readUnits = read,
                        watchedUnits = watched,
                        from = start,
                        to = end
                    )
                }
            }
            PeriodMode.MONTH -> {
                val currentYear = today.year
                (1..12).map { month ->
                    val ym = YearMonth.of(currentYear, month)
                    val start = ym.atDay(1)
                    val end = ym.atEndOfMonth()
                    val read = entries.filter { it.date in start..end }.sumOf { it.readUnits }
                    val watched = entries.filter { it.date in start..end }.sumOf { it.watchedUnits }
                    PeriodBarData(
                        label = ym.atDay(1)
                            .format(DateTimeFormatter.ofPattern("MMM", locale))
                            .replace(".", "")
                            .take(3)
                            .replaceFirstChar { it.uppercase() },
                        readUnits = read,
                        watchedUnits = watched,
                        from = start,
                        to = end
                    )
                }
            }
        }
    }
    var selectedBar by remember { mutableStateOf<PeriodBarData?>(null) }
    val title = when (mode) {
        PeriodMode.DAY -> stringResource(R.string.period_days)
        PeriodMode.WEEK -> stringResource(R.string.period_weeks)
        PeriodMode.MONTH -> stringResource(R.string.period_months)
    }
    val maxBarUnits = remember(bars) {
        bars.maxOfOrNull { it.allUnits }?.coerceAtLeast(1.0) ?: 1.0
    }

    Column {
        Text(
            text = title,
            color = titleColorBetween.copy(alpha = 0.75f),
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                val spacing = 4.dp
                val barsCount = bars.size.coerceAtLeast(1)
                val totalSpacing = spacing * (barsCount - 1)
                val rawBarWidth = (maxWidth - totalSpacing) / barsCount
                val barWidth = if (mode == PeriodMode.MONTH) {
                    rawBarWidth
                } else {
                    rawBarWidth.coerceAtLeast(12.dp)
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars.forEach { bar ->
                        Column(
                            modifier = Modifier.width(barWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Динамический максимум + дискретные промежуточные уровни.
                            val barProgress = quantizedIntensity(bar.allUnits, maxBarUnits, levels = 16)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((62f * barProgress).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(statsFillColor.copy(alpha = 0.95f))
                                    .clickable { selectedBar = bar }
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = bar.label,
                                color = titleColorBetween.copy(alpha = 0.75f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
    selectedBar?.let { bar ->
        val isMonthFirst = isMonthFirstDateOrder()
        val fullPattern = if (isMonthFirst) "MM.dd.yyyy" else "dd.MM.yyyy"
        val shortPattern = if (isMonthFirst) "MM.dd" else "dd.MM"
        val titleText = if (bar.from == bar.to) {
            bar.from.format(DateTimeFormatter.ofPattern(fullPattern, locale))
        } else {
            "${bar.from.format(DateTimeFormatter.ofPattern(shortPattern, locale))} - ${bar.to.format(DateTimeFormatter.ofPattern(shortPattern, locale))}"
        }
        AlertDialog(
            onDismissRequest = { selectedBar = null },
            title = { Text(titleText, color = titleColorBetween) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${stringResource(R.string.progress_read_chapters)}: ${bar.readUnits.roundToInt()}", color = titleColorBetween.copy(alpha = 0.9f))
                    Text("${stringResource(R.string.progress_watched_episodes)}: ${bar.watchedUnits.roundToInt()}", color = titleColorBetween.copy(alpha = 0.9f))
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBar = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
    trackColor: Color
) {
    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()
        val radius = (size.minDimension - strokeWidthPx) / 2
        val center = Offset(size.width / 2, size.height / 2)
        
        // Draw track
        drawCircle(
            color = trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidthPx)
        )
        
        // Draw progress
        if (progress > 0) {
            val sweepAngle = 360f * progress
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
        }
    }
}