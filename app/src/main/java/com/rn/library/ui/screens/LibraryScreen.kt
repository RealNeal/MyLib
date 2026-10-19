package com.rn.library.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import com.rn.library.ui.components.ColorSettingsDialog
import com.rn.library.ui.components.TopOptionsMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.rn.library.data.*
import com.rn.library.data.toCoverImageData
import androidx.compose.ui.res.stringResource
import com.rn.library.R
import com.rn.library.ui.Language
import com.rn.library.ui.rememberLanguageState
import com.rn.library.ui.LanguageState
import com.rn.library.ui.workStatusLabel
import com.rn.library.ui.workTypeLabel
import com.rn.library.ui.components.ActivityStatsConfirmDialog
import com.rn.library.ui.components.AppNavigationRail
import com.rn.library.ui.components.BottomNavigationBar
import com.rn.library.ui.components.bottomNavigationClearance
import com.rn.library.ui.components.tabContentColor
import com.rn.library.ui.components.tabBackgroundColor
import com.rn.library.ui.components.NavigationItem
import com.rn.library.ui.components.SearchBar
import com.rn.library.ui.components.SunIcon
import com.rn.library.ui.components.WorkItem
import com.rn.library.ui.components.WorkItemCard
import com.rn.library.ui.components.WorkItemGridCard
import com.rn.library.util.WindowHeightClass
import com.rn.library.util.WindowWidthClass
import com.rn.library.util.rememberWindowSizeInfo
import com.rn.library.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

// Порядок сортировки списка произведений
enum class SortOrder {
    RECENT,
    NEWEST,
    TITLE,
    TITLE_DESC,
    AUTHOR,
    GENRE,
    TAG
}

private fun sortWorks(list: List<Work>, sortOrder: SortOrder): List<Work> {
    return when (sortOrder) {
        SortOrder.RECENT -> list.sortedWith(
            compareByDescending<Work> { it.updatedAt ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        SortOrder.NEWEST -> list.sortedWith(
            compareByDescending<Work> { it.year ?: 0 }
                .thenByDescending { it.updatedAt ?: 0L }
                .thenBy { it.title.lowercase() }
        )
        SortOrder.TITLE -> list.sortedWith(
            compareBy<Work> { it.title.lowercase() }
        )
        SortOrder.TITLE_DESC -> list.sortedWith(
            compareByDescending<Work> { it.title.lowercase() }
        )
        SortOrder.AUTHOR -> list.sortedWith(
            compareBy<Work> { it.author?.lowercase() ?: "\uFFFF" }
                .thenBy { it.title.lowercase() }
        )
        SortOrder.GENRE -> list.sortedWith(
            compareBy<Work> { it.genres.firstOrNull()?.lowercase() ?: "\uFFFF" }
                .thenBy { it.title.lowercase() }
        )
        SortOrder.TAG -> list.sortedWith(
            compareBy<Work> { it.tags.firstOrNull()?.lowercase() ?: "\uFFFF" }
                .thenBy { it.title.lowercase() }
        )
    }
}

private data class PendingWorkSave(
    val work: Work,
    val previous: Work?,
    val onAfterSave: (Work) -> Unit
)

// Extension function to convert Work to WorkItem (with localized labels and status)
fun Work.toWorkItem(
    statusLabel: String,
    imageUrlOverride: String? = null,
    currentLanguage: Language = Language.RUSSIAN
): WorkItem {
    fun formatDouble(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    }

    fun formatCount(count: Int, kind: String): String {
        return when (currentLanguage) {
            Language.RUSSIAN -> {
                val mod10 = count % 10
                val mod100 = count % 100
                val form = when (kind) {
                    "season" -> if (mod10 == 1 && mod100 != 11) "сезон" else if (mod10 in 2..4 && mod100 !in 12..14) "сезона" else "сезонов"
                    "volume" -> if (mod10 == 1 && mod100 != 11) "том" else if (mod10 in 2..4 && mod100 !in 12..14) "тома" else "томов"
                    "chapter" -> if (mod10 == 1 && mod100 != 11) "глава" else if (mod10 in 2..4 && mod100 !in 12..14) "главы" else "глав"
                    else -> if (mod10 == 1 && mod100 != 11) "серия" else if (mod10 in 2..4 && mod100 !in 12..14) "серии" else "серий"
                }
                "$count $form"
            }
            Language.GERMAN -> {
                val form = when (kind) {
                    "season" -> if (count == 1) "Staffel" else "Staffeln"
                    "volume" -> if (count == 1) "Band" else "Bände"
                    "chapter" -> "Kapitel"
                    else -> if (count == 1) "Episode" else "Episoden"
                }
                "$count $form"
            }
            Language.FRENCH -> {
                val form = when (kind) {
                    "season" -> if (count <= 1) "saison" else "saisons"
                    "volume" -> if (count <= 1) "volume" else "volumes"
                    "chapter" -> if (count <= 1) "chapitre" else "chapitres"
                    else -> if (count <= 1) "épisode" else "épisodes"
                }
                "$count $form"
            }
            Language.SPANISH -> {
                val form = when (kind) {
                    "season" -> if (count == 1) "temporada" else "temporadas"
                    "volume" -> if (count == 1) "volumen" else "volúmenes"
                    "chapter" -> if (count == 1) "capítulo" else "capítulos"
                    else -> if (count == 1) "episodio" else "episodios"
                }
                "$count $form"
            }
            Language.PORTUGUESE -> {
                val form = when (kind) {
                    "season" -> if (count == 1) "temporada" else "temporadas"
                    "volume" -> if (count == 1) "volume" else "volumes"
                    "chapter" -> if (count == 1) "capítulo" else "capítulos"
                    else -> if (count == 1) "episódio" else "episódios"
                }
                "$count $form"
            }
            Language.JAPANESE -> when (kind) {
                "season" -> "${count}期"
                "volume" -> "${count}巻"
                else -> "${count}話"
            }
            Language.KOREAN -> when (kind) {
                "season" -> "${count}시즌"
                "volume" -> "${count}권"
                else -> "${count}화"
            }
            Language.CHINESE -> when (kind) {
                "season" -> "${count}季"
                "volume" -> "${count}卷"
                "chapter" -> "${count}话"
                else -> "${count}集"
            }
            Language.ENGLISH -> {
                val form = when (kind) {
                    "season" -> if (count == 1) "season" else "seasons"
                    "volume" -> if (count == 1) "volume" else "volumes"
                    "chapter" -> if (count == 1) "chapter" else "chapters"
                    else -> if (count == 1) "episode" else "episodes"
                }
                "$count $form"
            }
        }
    }

    fun formatDoubleWithKind(value: Double, kind: String): String {
        val formatted = formatDouble(value)
        val intPart = value.toInt()
        return when (currentLanguage) {
            Language.RUSSIAN -> {
                val mod10 = intPart % 10
                val mod100 = intPart % 100
                val form = when (kind) {
                    "season" -> if (mod10 == 1 && mod100 != 11) "сезон" else if (mod10 in 2..4 && mod100 !in 12..14) "сезона" else "сезонов"
                    "volume" -> if (mod10 == 1 && mod100 != 11) "том" else if (mod10 in 2..4 && mod100 !in 12..14) "тома" else "томов"
                    "chapter" -> if (mod10 == 1 && mod100 != 11) "глава" else if (mod10 in 2..4 && mod100 !in 12..14) "главы" else "глав"
                    else -> if (mod10 == 1 && mod100 != 11) "серия" else if (mod10 in 2..4 && mod100 !in 12..14) "серии" else "серий"
                }
                "$formatted $form"
            }
            Language.GERMAN -> {
                val form = when (kind) {
                    "season" -> if (intPart == 1) "Staffel" else "Staffeln"
                    "volume" -> if (intPart == 1) "Band" else "Bände"
                    "chapter" -> "Kapitel"
                    else -> if (intPart == 1) "Episode" else "Episoden"
                }
                "$formatted $form"
            }
            Language.FRENCH -> {
                val form = when (kind) {
                    "season" -> if (intPart <= 1) "saison" else "saisons"
                    "volume" -> if (intPart <= 1) "volume" else "volumes"
                    "chapter" -> if (intPart <= 1) "chapitre" else "chapitres"
                    else -> if (intPart <= 1) "épisode" else "épisodes"
                }
                "$formatted $form"
            }
            Language.SPANISH -> {
                val form = when (kind) {
                    "season" -> if (intPart == 1) "temporada" else "temporadas"
                    "volume" -> if (intPart == 1) "volumen" else "volúmenes"
                    "chapter" -> if (intPart == 1) "capítulo" else "capítulos"
                    else -> if (intPart == 1) "episodio" else "episodios"
                }
                "$formatted $form"
            }
            Language.PORTUGUESE -> {
                val form = when (kind) {
                    "season" -> if (intPart == 1) "temporada" else "temporadas"
                    "volume" -> if (intPart == 1) "volume" else "volumes"
                    "chapter" -> if (intPart == 1) "capítulo" else "capítulos"
                    else -> if (intPart == 1) "episódio" else "episódios"
                }
                "$formatted $form"
            }
            Language.JAPANESE -> when (kind) {
                "season" -> "${formatted}期"
                "volume" -> "${formatted}巻"
                else -> "${formatted}話"
            }
            Language.KOREAN -> when (kind) {
                "season" -> "${formatted}시즌"
                "volume" -> "${formatted}권"
                else -> "${formatted}화"
            }
            Language.CHINESE -> when (kind) {
                "season" -> "${formatted}季"
                "volume" -> "${formatted}卷"
                "chapter" -> "${formatted}话"
                else -> "${formatted}集"
            }
            Language.ENGLISH -> {
                val form = when (kind) {
                    "season" -> if (intPart == 1) "season" else "seasons"
                    "volume" -> if (intPart == 1) "volume" else "volumes"
                    "chapter" -> if (intPart == 1) "chapter" else "chapters"
                    else -> if (intPart == 1) "episode" else "episodes"
                }
                "$formatted $form"
            }
        }
    }

    val metaParts = mutableListOf<String>()
    val metaLine1: String?
    val metaLine2: String?
    when (type) {
        WorkType.BOOK -> {
            val volumesText = chapters?.let { formatDoubleWithKind(it, "volume") }
            val chaptersText = bookChapters?.let { formatDoubleWithKind(it, "chapter") }
            metaLine1 = volumesText
            metaLine2 = chaptersText
            volumesText?.let { metaParts.add(it) }
            chaptersText?.let { metaParts.add(it) }
        }
        WorkType.MANGA -> {
            val volumesText = volumes?.let { formatDoubleWithKind(it, "volume") }
            val chaptersText = chapters?.let { formatDoubleWithKind(it, "chapter") }
            metaLine1 = volumesText
            metaLine2 = chaptersText
            volumesText?.let { metaParts.add(it) }
            chaptersText?.let { metaParts.add(it) }
        }
        WorkType.ANIME -> {
            val episodesText = episodes?.let { formatDoubleWithKind(it, "episode") }
            metaLine1 = episodesText
            metaLine2 = null
            episodesText?.let { metaParts.add(it) }
        }
        WorkType.SERIES -> {
            val seasonsText = seasons?.let { formatCount(it, "season") }
            val episodesText = episodes?.let { formatDoubleWithKind(it, "episode") }
            metaLine1 = seasonsText
            metaLine2 = episodesText
            seasonsText?.let { metaParts.add(it) }
            episodesText?.let { metaParts.add(it) }
        }
    }

    return WorkItem(
        id = id,
        title = title,
        imageUrl = imageUrlOverride ?: displayCoverPath(),
        meta = metaParts.joinToString(" • "),
        metaLine1 = metaLine1,
        metaLine2 = metaLine2,
        description = description,
        status = status,
        type = type,
        statusLabel = statusLabel
    )
}

fun Work.matchesSearchQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val trimmed = query.trim().lowercase()
    val tagQuery = if (trimmed.startsWith("#")) trimmed.removePrefix("#").trim() else trimmed

    // Title & alternative title
    if (title.lowercase().contains(trimmed)) return true
    if (otherTitle?.lowercase()?.contains(trimmed) == true) return true

    // Author
    if (author?.lowercase()?.contains(trimmed) == true) return true

    // Genres (support partial and exact match)
    if (genres.any { g ->
        val cleanG = g.trim().lowercase()
        cleanG.contains(trimmed) || (trimmed.length >= 3 && trimmed.contains(cleanG))
    }) return true

    // Tags (with or without '#')
    if (tags.any { t ->
        val cleanT = t.trim().lowercase().removePrefix("#")
        cleanT.contains(tagQuery) || (tagQuery.length >= 3 && tagQuery.contains(cleanT))
    }) return true

    return false
}

@Composable
fun LibraryScreen(
    currentTheme: AppTheme = AppTheme.DARK,
    onThemeChange: (AppTheme) -> Unit = {},
    dynamicColorsEnabled: Boolean = false,
    onDynamicColorsEnabledChange: (Boolean) -> Unit = {},
    themePalette: ThemePalette = ThemePalette.DEFAULT,
    onThemePaletteChange: (ThemePalette) -> Unit = {},
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
    providedLanguageState: LanguageState? = null,
    modifier: Modifier = Modifier
) {
    val ownedLanguageState = rememberLanguageState()
    val languageState = providedLanguageState ?: ownedLanguageState
    val currentLanguage = languageState.currentLanguage
    val context = LocalContext.current
    val density = LocalDensity.current
    val repository = remember { WorkRepository(context) }

    // Preferences for tabs и сортировки
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val coverPrefs = remember { context.getSharedPreferences("cover_prefs", Context.MODE_PRIVATE) }

    // Сортировка списка
    var sortOrder by remember {
        mutableStateOf(
            try {
                SortOrder.valueOf(
                    prefs.getString("sort_order", SortOrder.RECENT.name) ?: SortOrder.RECENT.name
                )
            } catch (e: Exception) {
                SortOrder.RECENT
            }
        )
    }

    // Per-tab visibility switches (except Profile, который всегда показан)
    var booksTabEnabled by remember {
        mutableStateOf(prefs.getBoolean("tab_books_enabled", true))
    }
    var animeTabEnabled by remember {
        mutableStateOf(prefs.getBoolean("tab_anime_enabled", true))
    }
    var mangaTabEnabled by remember {
        mutableStateOf(prefs.getBoolean("tab_manga_enabled", true))
    }
    var tvSeriesTabEnabled by remember {
        mutableStateOf(prefs.getBoolean("tab_tv_enabled", true))
    }

    // Режим отображения произведений: список (false) или блоки (true)
    var isGridView by remember {
        mutableStateOf(prefs.getBoolean("view_grid_mode", false))
    }

    fun getDefaultTab(
        booksEnabled: Boolean,
        animeEnabled: Boolean,
        mangaEnabled: Boolean,
        tvEnabled: Boolean
    ): NavigationItem {
        // Выбираем первую доступную вкладку с произведениями
        return when {
            booksEnabled -> NavigationItem.Books
            animeEnabled -> NavigationItem.Anime
            mangaEnabled -> NavigationItem.Manga
            tvEnabled -> NavigationItem.TVSeries
            else -> NavigationItem.Profile
        }
    }

    var selectedItemKey by rememberSaveable { mutableStateOf("default") }
    fun navItemForKey(key: String): NavigationItem = when (key) {
        "books" -> NavigationItem.Books
        "anime" -> NavigationItem.Anime
        "manga" -> NavigationItem.Manga
        "series" -> NavigationItem.TVSeries
        "profile" -> NavigationItem.Profile
        else -> getDefaultTab(booksTabEnabled, animeTabEnabled, mangaTabEnabled, tvSeriesTabEnabled)
    }
    fun navItemKey(item: NavigationItem): String = when (item) {
        NavigationItem.Books -> "books"
        NavigationItem.Anime -> "anime"
        NavigationItem.Manga -> "manga"
        NavigationItem.TVSeries -> "series"
        NavigationItem.Profile -> "profile"
    }
    var selectedItem by remember { mutableStateOf(navItemForKey(selectedItemKey)) }
    LaunchedEffect(selectedItemKey) { selectedItem = navItemForKey(selectedItemKey) }

    var searchQuery by remember { mutableStateOf<String>("") }
    var isTagOrGenreQuery by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf<WorkStatus?>(null) }
    var showAddWorkScreen by remember { mutableStateOf<Boolean>(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showColorSettingsDialog by remember { mutableStateOf(false) }
    var works by remember { mutableStateOf<List<Work>>(emptyList()) }

    val allKnownGenres = remember(works) {
        val predefined = listOf(
            "Детектив", "Драма", "Игры", "История", "Комедия",
            "Магия", "Меха", "Мистика", "Музыка", "Повседневность",
            "Приключения", "Психология", "Романтика", "Сверхъестественное",
            "Спорт", "Сёнэн", "Сэйнэн", "Сёдзё", "Триллер", "Ужасы",
            "Фантастика", "Фэнтези", "Школа", "Экшен"
        )
        (works.flatMap { it.genres } + predefined)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    val allKnownTags = remember(works) {
        works.flatMap { it.tags }
            .map { it.trim().lowercase().removePrefix("#") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isQueryTagOrGenre(q: String): Boolean {
        val trimmed = q.trim().lowercase()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("#")) return true
        if (allKnownGenres.contains(trimmed)) return true
        if (allKnownTags.contains(trimmed)) return true
        return false
    }
    var sessionCoverByWorkId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedWork by remember { mutableStateOf<Work?>(null) }
    var editingWork by remember { mutableStateOf<Work?>(null) }
    var workToDelete by remember { mutableStateOf<Work?>(null) }
    var expandedCoverWork by remember { mutableStateOf<Work?>(null) }
    var pendingWorkSave by remember { mutableStateOf<PendingWorkSave?>(null) }
    var showActivityStatsConfirm by remember { mutableStateOf(false) }

    /** Счётчик повторного выбора вкладки «Профиль» (закрытие Настроек и др. оверлеев). */
    var profileReselectSignal by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    fun commitWorkSave(
        work: Work,
        previous: Work?,
        recordActivity: Boolean,
        onAfterSave: (Work) -> Unit
    ) {
        repository.saveWork(work, recordActivity = recordActivity)
        works = repository.getAllWorks()
        onAfterSave(work)
    }

    fun requestSaveWork(work: Work, previous: Work?, onAfterSave: (Work) -> Unit) {
        val resolvedPrevious = previous ?: repository.getWorkById(work.id)
        if (repository.shouldConfirmLargeActivityDelta(resolvedPrevious, work)) {
            pendingWorkSave = PendingWorkSave(work, resolvedPrevious, onAfterSave)
            showActivityStatsConfirm = true
        } else {
            commitWorkSave(work, resolvedPrevious, recordActivity = true, onAfterSave)
        }
    }

    // Как и для грида: новое состояние при смене вкладки/фильтра/сортировки/поиска/режима —
    // список начинается с первого элемента (прокрутка не «залипает» на старой позиции после сортировки).
    val listState = remember(statusFilter, selectedItem, isGridView, sortOrder, searchQuery) {
        LazyListState()
    }
    val gridState = remember(statusFilter, selectedItem, isGridView, sortOrder, searchQuery) {
        LazyGridState()
    }
    var isHeaderVisible by remember { mutableStateOf(true) }

    // Back для увеличенной обложки
    BackHandler(enabled = expandedCoverWork != null) {
        expandedCoverWork = null
    }

    // Общий back: закрываем экран добавления или просмотра произведения.
    BackHandler(enabled = showAddWorkScreen || (selectedWork != null && expandedCoverWork == null)) {
        when {
            showAddWorkScreen -> {
                // Если редактировали существующее произведение, возвращаемся к экрану просмотра
                if (editingWork != null) {
                    selectedWork = editingWork
                }
                showAddWorkScreen = false
            }

            selectedWork != null -> selectedWork = null
        }
    }

    // Подгружаем каталог при старте и при каждой смене вкладки (после импорта с Профиля список не «залипает»).
    LaunchedEffect(selectedItem) {
        works = repository.getAllWorks()
    }

    // При заходе в приложение — случайная обложка; при >2 не повторяем предыдущую.
    // При добавлении нового произведения — выбираем только для него, не трогая остальные.
    LaunchedEffect(works) {
        val selectedMap = sessionCoverByWorkId.toMutableMap()
        val isAppSessionStart = selectedMap.isEmpty()
        var changed = false
        works.forEach { work ->
            if (!isAppSessionStart && work.id in selectedMap) return@forEach
            val candidates = work.allCoverPaths()
            val chosen = pickRandomCoverAvoidingLast(
                candidates,
                coverPrefs.getString("last_cover_${work.id}", null)
            ) ?: return@forEach
            selectedMap[work.id] = chosen
            coverPrefs.edit { putString("last_cover_${work.id}", chosen) }
            changed = true
        }
        if (changed) sessionCoverByWorkId = selectedMap
    }

    var lastExpandedCoverWork by remember { mutableStateOf<Work?>(null) }

    // Reset detail search when leaving work details и возвращаем хедер (строку поиска) при входе в детали
    LaunchedEffect(selectedWork) {
        if (selectedWork != null) {
            // При открытии экрана просмотра произведения гарантированно показываем хедер,
            // даже если он был скрыт прокруткой.
            isHeaderVisible = true
        }
    }

    LaunchedEffect(expandedCoverWork) {
        if (expandedCoverWork != null) {
            lastExpandedCoverWork = expandedCoverWork
        }
    }

    // Filter works by selected tab
    val filteredWorks = remember(works, selectedItem, searchQuery, statusFilter, sortOrder) {
        val typeFilter = when (selectedItem) {
            NavigationItem.Books -> WorkType.BOOK
            NavigationItem.Anime -> WorkType.ANIME
            NavigationItem.Manga -> WorkType.MANGA
            NavigationItem.TVSeries -> WorkType.SERIES
            else -> null
        }

        var filtered = if (typeFilter != null) {
            works.filter { it.type == typeFilter }
        } else {
            emptyList()
        }

        // Apply search filter
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { it.matchesSearchQuery(searchQuery) }
        }

        // Apply status filter
        statusFilter?.let { sf ->
            filtered = filtered.filter { it.status == sf }
        }

        sortWorks(filtered, sortOrder)
    }

    // Track scroll to hide/show header (only when not viewing work details and not in Profile).
    LaunchedEffect(
        listState.firstVisibleItemScrollOffset,
        listState.firstVisibleItemIndex,
        gridState.firstVisibleItemScrollOffset,
        gridState.firstVisibleItemIndex,
        gridState.layoutInfo.totalItemsCount,
        listState.layoutInfo.totalItemsCount,
        filteredWorks.size,
        selectedWork,
        selectedItem,
        isGridView
    ) {
        if (selectedWork == null && selectedItem != NavigationItem.Profile) {
            if (filteredWorks.size <= 4) {
                if (!isHeaderVisible) isHeaderVisible = true
                return@LaunchedEffect
            }

            val canScroll = if (isGridView) {
                gridState.canScrollForward || gridState.canScrollBackward
            } else {
                listState.canScrollForward || listState.canScrollBackward
            }

            val index: Int
            val offset: Int
            if (isGridView) {
                index = gridState.firstVisibleItemIndex
                offset = gridState.firstVisibleItemScrollOffset
            } else {
                index = listState.firstVisibleItemIndex
                offset = listState.firstVisibleItemScrollOffset
            }
            val shouldHide = index > 0 || offset > 50

            when {
                !canScroll -> {
                    if (!isHeaderVisible) isHeaderVisible = true
                }

                shouldHide -> {
                    if (isHeaderVisible) isHeaderVisible = false
                }

                else -> {
                    if (!isHeaderVisible) isHeaderVisible = true
                }
            }
        }
    }

    val mainBackgroundColor = MainBackgroundColor()
    // Colors used across header + detail-search list
    val iconTextColor = IconTextColor()
    val titleColorBetween = TitleColorBetween()

    val windowInfo = rememberWindowSizeInfo()
        val useNavRail = windowInfo.isLandscape
        val bottomBarClearance = if (useNavRail) 16.dp else bottomNavigationClearance()

        val view = LocalView.current
        DisposableEffect(windowInfo.isLandscape, windowInfo.heightClass) {
            val activity = (view.context as? Activity)
                ?: (view.context as? ContextWrapper)?.baseContext as? Activity
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                if (windowInfo.isLandscape && windowInfo.heightClass == WindowHeightClass.COMPACT) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                val activity = (view.context as? Activity)
                    ?: (view.context as? ContextWrapper)?.baseContext as? Activity
                activity?.window?.let { window ->
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        val onNavItemSelected: (NavigationItem) -> Unit = { item ->
            if (item == NavigationItem.Profile && selectedItem == NavigationItem.Profile) {
                profileReselectSignal++
            } else {
                selectedItem = item
                selectedItemKey = navItemKey(item)
                isHeaderVisible = true
                statusFilter = null
                if (!isTagOrGenreQuery) {
                    searchQuery = ""
                }
                selectedWork = null
                expandedCoverWork = null
                showAddWorkScreen = false
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (useNavRail) {
                    AppNavigationRail(
                        selectedItem = selectedItem,
                        onItemSelected = onNavItemSelected,
                        currentTheme = currentTheme,
                        dynamicColorsEnabled = dynamicColorsEnabled,
                        booksEnabled = booksTabEnabled,
                        animeEnabled = animeTabEnabled,
                        mangaEnabled = mangaTabEnabled,
                        tvSeriesEnabled = tvSeriesTabEnabled
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(mainBackgroundColor)
                    ) {
                        // Spacer to push search bar from top
                        Spacer(modifier = Modifier.height(if (useNavRail) 12.dp else 40.dp))

                        // Search bar with theme toggle and add/edit button - сворачивается при прокрутке
                        // Управление видимостью через isHeaderVisible, который обновляется:
                        // - Для списков произведений: через LaunchedEffect с listState
                        // - Для Profile: через onScrollStateChange из ProfileScreen
                        // - Для WorkDetail: через onScrollStateChange из WorkDetailScreen
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isHeaderVisible,
                            enter = androidx.compose.animation.fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                    androidx.compose.animation.slideInVertically(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                                    ) { -it / 2 },
                            exit = androidx.compose.animation.fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
                                    androidx.compose.animation.slideOutVertically(
                                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                                    ) { -it / 2 }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                                    .widthIn(max = 720.dp)
                                    .padding(start = 16.dp, end = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val iconTextColor = IconTextColor()
                                    val tabColor = tabContentColor(currentTheme, dynamicColorsEnabled)

                                    SearchBar(
                                        modifier = Modifier.weight(1f),
                                        currentTheme = currentTheme,
                                        query = searchQuery,
                                        enabled = !showAddWorkScreen && selectedWork == null,
                                        onSearchQueryChange = { q ->
                                            searchQuery = q
                                            isTagOrGenreQuery = isQueryTagOrGenre(q)
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = {
                                            onThemeChange(if (currentTheme == AppTheme.DARK) AppTheme.LIGHT else AppTheme.DARK)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        SunIcon(
                                            onClick = {
                                                onThemeChange(if (currentTheme == AppTheme.DARK) AppTheme.LIGHT else AppTheme.DARK)
                                            },
                                            color = iconTextColor,
                                            iconSize = 20.dp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

                                    IconButton(
                                        onClick = {
                                            editingWork = null
                                            showAddWorkScreen = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = stringResource(R.string.add_work),
                                            tint = iconTextColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(2.dp))

                                    Box {
                                        IconButton(
                                            onClick = { showTopMenu = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = stringResource(R.string.more_options),
                                                tint = iconTextColor,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        TopOptionsMenu(
                                            expanded = showTopMenu,
                                            onDismissRequest = { showTopMenu = false },
                                            isGridView = isGridView,
                                            onViewModeSelected = { grid ->
                                                isGridView = grid
                                                prefs.edit { putBoolean("view_grid_mode", grid) }
                                                showTopMenu = false
                                            },
                                            onOpenColorSettings = {
                                                showTopMenu = false
                                                showColorSettingsDialog = true
                                            },
                                            activeColor = tabColor
                                        )
                                    }
                                }

                                // Status filter chip & Sort order chip (under search bar)
                                if (selectedItem != NavigationItem.Profile) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val tabColor = tabContentColor(currentTheme, dynamicColorsEnabled)
                                        val tabBgColor = tabBackgroundColor(dynamicColorsEnabled)

                                        Row(
                                            modifier = Modifier
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            StatusFilterChip(
                                                selectedItem = selectedItem,
                                                statusFilter = statusFilter,
                                                onStatusFilterChange = { statusFilter = it },
                                                tabColor = tabColor,
                                                tabBackgroundColor = tabBgColor
                                            )

                                            SortOrderChip(
                                                sortOrder = sortOrder,
                                                onSortOrderChange = { newOrder ->
                                                    sortOrder = newOrder
                                                    prefs.edit {
                                                        putString(
                                                            "sort_order",
                                                            newOrder.name
                                                        )
                                                    }
                                                },
                                                tabColor = tabColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Main content area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // Delete confirmation dialog
                            workToDelete?.let { w ->
                                val scheme = androidx.compose.material3.MaterialTheme.colorScheme
                                AlertDialog(
                                    onDismissRequest = { workToDelete = null },
                                    title = { Text(stringResource(R.string.delete_work)) },
                                    text = { Text(stringResource(R.string.delete_work_confirm)) },
                                    containerColor = scheme.surface,
                                    titleContentColor = scheme.onSurface,
                                    textContentColor = scheme.onSurfaceVariant,
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                if (repository.deleteWork(w.id)) {
                                                    works = repository.getAllWorks()
                                                    if (selectedWork?.id == w.id) selectedWork =
                                                        null
                                                    workToDelete = null
                                                }
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                                containerColor = AppButtonColor(),
                                                contentColor = scheme.onPrimary
                                            )
                                        ) { Text(stringResource(R.string.delete)) }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = { workToDelete = null },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                                contentColor = scheme.primary
                                            )
                                        ) { Text(stringResource(R.string.cancel)) }
                                    }
                                )
                            }

                            if (!showAddWorkScreen) {
                                AnimatedContent(
                                    targetState = selectedItem,
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        fun navIndex(item: NavigationItem): Int = when (item) {
                                            NavigationItem.Books -> 0
                                            NavigationItem.Anime -> 1
                                            NavigationItem.Manga -> 2
                                            NavigationItem.TVSeries -> 3
                                            NavigationItem.Profile -> 4
                                        }

                                        val oldIndex = navIndex(initialState)
                                        val newIndex = navIndex(targetState)
                                        val animDuration = 135
                                        val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
                                            durationMillis = animDuration,
                                            easing = FastOutSlowInEasing
                                        )
                                        val fadeSpec = tween<Float>(
                                            durationMillis = animDuration,
                                            easing = FastOutSlowInEasing
                                        )

                                        if (newIndex > oldIndex) {
                                            (slideInHorizontally(animationSpec = slideSpec) { width -> width / 8 } + fadeIn(
                                                animationSpec = fadeSpec
                                            ))
                                                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> -width / 8 } + fadeOut(
                                                    animationSpec = fadeSpec
                                                ))
                                        } else {
                                            (slideInHorizontally(animationSpec = slideSpec) { width -> -width / 8 } + fadeIn(
                                                animationSpec = fadeSpec
                                            ))
                                                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { width -> width / 8 } + fadeOut(
                                                    animationSpec = fadeSpec
                                                ))
                                        }
                                    },
                                    label = "TabContentTransition"
                                ) { targetTab ->
                                    when (targetTab) {
                                        NavigationItem.Profile -> {
                                            ProfileScreen(
                                                currentLanguage = languageState.currentLanguage,
                                                onLanguageChange = { languageState.setLanguage(it) },
                                                currentTheme = currentTheme,
                                                onThemeChange = onThemeChange,
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
                                                onBooksTabEnabledChange = { enabled ->
                                                    booksTabEnabled = enabled
                                                    prefs.edit {
                                                        putBoolean(
                                                            "tab_books_enabled",
                                                            enabled
                                                        )
                                                    }
                                                    if (!enabled && selectedItem == NavigationItem.Books) {
                                                        selectedItem = getDefaultTab(
                                                            booksEnabled = false,
                                                            animeEnabled = animeTabEnabled,
                                                            mangaEnabled = mangaTabEnabled,
                                                            tvEnabled = tvSeriesTabEnabled
                                                        )
                                                    }
                                                },
                                                animeTabEnabled = animeTabEnabled,
                                                onAnimeTabEnabledChange = { enabled ->
                                                    animeTabEnabled = enabled
                                                    prefs.edit {
                                                        putBoolean(
                                                            "tab_anime_enabled",
                                                            enabled
                                                        )
                                                    }
                                                    if (!enabled && selectedItem == NavigationItem.Anime) {
                                                        selectedItem = getDefaultTab(
                                                            booksEnabled = booksTabEnabled,
                                                            animeEnabled = false,
                                                            mangaEnabled = mangaTabEnabled,
                                                            tvEnabled = tvSeriesTabEnabled
                                                        )
                                                    }
                                                },
                                                mangaTabEnabled = mangaTabEnabled,
                                                onMangaTabEnabledChange = { enabled ->
                                                    mangaTabEnabled = enabled
                                                    prefs.edit {
                                                        putBoolean(
                                                            "tab_manga_enabled",
                                                            enabled
                                                        )
                                                    }
                                                    if (!enabled && selectedItem == NavigationItem.Manga) {
                                                        selectedItem = getDefaultTab(
                                                            booksEnabled = booksTabEnabled,
                                                            animeEnabled = animeTabEnabled,
                                                            mangaEnabled = false,
                                                            tvEnabled = tvSeriesTabEnabled
                                                        )
                                                    }
                                                },
                                                tvSeriesTabEnabled = tvSeriesTabEnabled,
                                                onTvSeriesTabEnabledChange = { enabled ->
                                                    tvSeriesTabEnabled = enabled
                                                    prefs.edit {
                                                        putBoolean(
                                                            "tab_tv_enabled",
                                                            enabled
                                                        )
                                                    }
                                                    if (!enabled && selectedItem == NavigationItem.TVSeries) {
                                                        selectedItem = getDefaultTab(
                                                            booksEnabled = booksTabEnabled,
                                                            animeEnabled = animeTabEnabled,
                                                            mangaEnabled = mangaTabEnabled,
                                                            tvEnabled = false
                                                        )
                                                    }
                                                },
                                                isGridView = isGridView,
                                                onGridViewChange = { asGrid ->
                                                    isGridView = asGrid
                                                    prefs.edit {
                                                        putBoolean(
                                                            "view_grid_mode",
                                                            asGrid
                                                        )
                                                    }
                                                },
                                                onAddWorkRequested = {
                                                    editingWork = null
                                                    showAddWorkScreen = true
                                                },
                                                profileReselectSignal = profileReselectSignal,
                                                onScrollStateChange = { shouldHide ->
                                                    isHeaderVisible = !shouldHide
                                                },
                                                works = works,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        else -> {
                                            val currentTabType = when (targetTab) {
                                                NavigationItem.Books -> WorkType.BOOK
                                                NavigationItem.Anime -> WorkType.ANIME
                                                NavigationItem.Manga -> WorkType.MANGA
                                                NavigationItem.TVSeries -> WorkType.SERIES
                                                else -> null
                                            }
                                            val tabWorks = remember(
                                                works,
                                                currentTabType,
                                                statusFilter,
                                                searchQuery,
                                                sortOrder
                                            ) {
                                                val list =
                                                    if (currentTabType == null) emptyList() else works.filter { it.type == currentTabType }
                                                val filtered = list.filter { work ->
                                                    val matchesStatus =
                                                        statusFilter == null || work.status == statusFilter
                                                    val matchesQuery = work.matchesSearchQuery(searchQuery)
                                                    matchesStatus && matchesQuery
                                                }
                                                sortWorks(filtered, sortOrder)
                                            }
                                            if (isGridView) {
                                                key(targetTab) {
                                                    LazyVerticalGrid(
                                                        state = gridState,
                                                        columns = GridCells.Adaptive(minSize = 160.dp),
                                                        modifier = Modifier.fillMaxSize(),
                                                        userScrollEnabled = !showAddWorkScreen,
                                                        contentPadding = PaddingValues(
                                                            start = 8.dp,
                                                            end = 8.dp,
                                                            top = 2.dp,
                                                            bottom = bottomBarClearance
                                                        ),
                                                        verticalArrangement = Arrangement.spacedBy(
                                                            4.dp
                                                        ),
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            4.dp
                                                        )
                                                    ) {
                                                        items(
                                                            tabWorks,
                                                            key = { it.id }) { work ->
                                                            WorkItemGridCard(
                                                                workItem = work.toWorkItem(
                                                                    statusLabel = workStatusLabel(work.status),
                                                                    imageUrlOverride = sessionCoverByWorkId[work.id],
                                                                    currentLanguage = currentLanguage
                                                                ),
                                                                dynamicColorsEnabled = dynamicColorsEnabled,
                                                                onClick = {
                                                                    selectedWork = work
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                key(targetTab) {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.TopCenter
                                                    ) {
                                                        LazyColumn(
                                                            state = listState,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .widthIn(max = 720.dp),
                                                            userScrollEnabled = !showAddWorkScreen,
                                                            contentPadding = PaddingValues(
                                                                start = 16.dp,
                                                                end = 16.dp,
                                                                top = 8.dp,
                                                                bottom = bottomBarClearance
                                                            ),
                                                            verticalArrangement = Arrangement.spacedBy(
                                                                8.dp
                                                            )
                                                        ) {
                                                            items(
                                                                tabWorks,
                                                                key = { it.id }) { work ->
                                                                WorkItemCard(
                                                                    workItem = work.toWorkItem(
                                                                        statusLabel = workStatusLabel(work.status),
                                                                        imageUrlOverride = sessionCoverByWorkId[work.id],
                                                                        currentLanguage = currentLanguage
                                                                    ),
                                                                    onClick = {
                                                                        selectedWork = work
                                                                    },
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Work Detail Screen (полноэкранный слой поверх главного контейнера с меню вкладки)
                    selectedWork?.let { work ->
                        key(work.id) {
                            WorkDetailScreen(
                                work = work,
                                allWorks = works,
                                onSelectWork = { nextWork ->
                                    selectedWork = nextWork
                                },
                                coverPaths = work.allCoverPaths(),
                                sessionCoverPath = sessionCoverByWorkId[work.id],
                                onSessionCoverPathChange = { path ->
                                    sessionCoverByWorkId =
                                        sessionCoverByWorkId + (work.id to path)
                                    coverPrefs.edit {
                                        putString(
                                            "last_cover_${work.id}",
                                            path
                                        )
                                    }
                                },
                                onBack = { selectedWork = null },
                                onEdit = {
                                    editingWork = work
                                    selectedWork = null
                                    showAddWorkScreen = true
                                },
                                onDelete = { workToDelete = work },
                                onSave = { updatedWork ->
                                    requestSaveWork(
                                        updatedWork,
                                        selectedWork
                                    ) { saved ->
                                        selectedWork = saved
                                        sessionCoverByWorkId[updatedWork.id]?.let { current ->
                                            if (updatedWork.allCoverPaths()
                                                    .none { it == current }
                                            ) {
                                                val next =
                                                    pickRandomCoverAvoidingLast(
                                                        updatedWork.allCoverPaths(),
                                                        coverPrefs.getString(
                                                            "last_cover_${updatedWork.id}",
                                                            null
                                                        )
                                                    )
                                                if (next != null) {
                                                    sessionCoverByWorkId =
                                                        sessionCoverByWorkId + (updatedWork.id to next)
                                                    coverPrefs.edit {
                                                        putString(
                                                            "last_cover_${updatedWork.id}",
                                                            next
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onCoverClick = { expandedCoverWork = work },
                                currentTheme = currentTheme,
                                onThemeToggle = {
                                    onThemeChange(if (currentTheme == AppTheme.DARK) AppTheme.LIGHT else AppTheme.DARK)
                                },
                                onScrollStateChange = null,
                                onGenreClick = { genre ->
                                    selectedWork = null
                                    val targetItem = when (work.type) {
                                        WorkType.BOOK -> NavigationItem.Books
                                        WorkType.ANIME -> NavigationItem.Anime
                                        WorkType.MANGA -> NavigationItem.Manga
                                        WorkType.SERIES -> NavigationItem.TVSeries
                                    }
                                    selectedItem = targetItem
                                    selectedItemKey = navItemKey(targetItem)
                                    searchQuery = genre
                                    isTagOrGenreQuery = true
                                    statusFilter = null
                                    isHeaderVisible = true
                                },
                                onTagClick = { tag ->
                                    selectedWork = null
                                    val targetItem = when (work.type) {
                                        WorkType.BOOK -> NavigationItem.Books
                                        WorkType.ANIME -> NavigationItem.Anime
                                        WorkType.MANGA -> NavigationItem.Manga
                                        WorkType.SERIES -> NavigationItem.TVSeries
                                    }
                                    selectedItem = targetItem
                                    selectedItemKey = navItemKey(targetItem)
                                    searchQuery = if (tag.startsWith("#")) tag else "#$tag"
                                    isTagOrGenreQuery = true
                                    statusFilter = null
                                    isHeaderVisible = true
                                }
                            )
                        }
                    }

                    if (!useNavRail) {
                        BottomNavigationBar(
                            selectedItem = selectedItem,
                            onItemSelected = onNavItemSelected,
                            currentTheme = currentTheme,
                            dynamicColorsEnabled = dynamicColorsEnabled,
                            booksEnabled = booksTabEnabled,
                            animeEnabled = animeTabEnabled,
                            mangaEnabled = mangaTabEnabled,
                            tvSeriesEnabled = tvSeriesTabEnabled,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    // Add/Edit Work Screen — полноэкранный слой поверх главного контейнера (закрывает поиск и чипы)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showAddWorkScreen,
                        enter = fadeIn(
                            animationSpec = tween(
                                200,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ),
                        exit = fadeOut(
                            animationSpec = tween(
                                180,
                                easing = FastOutSlowInEasing
                            )
                        ) + slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(mainBackgroundColor)
                        ) {
                            AddWorkScreen(
                                onBack = {
                                    if (editingWork != null) {
                                        selectedWork = editingWork
                                    }
                                    showAddWorkScreen = false
                                },
                                onSave = { work ->
                                    requestSaveWork(work, editingWork) { saved ->
                                        showAddWorkScreen = false
                                        selectedWork =
                                            if (editingWork != null) saved else selectedWork
                                    }
                                },
                                work = editingWork
                            )
                        }
                    }

                    // Полноэкранное увеличенное изображение обложки (закрывает поиск и чипы)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expandedCoverWork != null,
                        enter = fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.85f, animationSpec = tween(220, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                scaleOut(targetScale = 0.85f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val coverWorkToDisplay = expandedCoverWork ?: lastExpandedCoverWork
                        coverWorkToDisplay?.let { work ->
                            val coverPath = sessionCoverByWorkId[work.id] ?: work.displayCoverPath()
                            if (coverPath != null && coverPath.isNotBlank()) {
                                val coverImageUri = when {
                                    coverPath.startsWith("/") -> Uri.fromFile(File(coverPath))
                                    else -> coverPath.toUri()
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.95f))
                                        .clickable { expandedCoverWork = null },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            ImageRequest.Builder(context)
                                                .data(coverImageUri)
                                                .build()
                                        ),
                                        contentDescription = stringResource(R.string.cover),
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .fillMaxHeight(0.85f),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }


                    if (showActivityStatsConfirm && pendingWorkSave != null) {
                        val pending = pendingWorkSave!!
                        ActivityStatsConfirmDialog(
                            onConfirm = {
                                commitWorkSave(
                                    pending.work,
                                    pending.previous,
                                    recordActivity = true,
                                    onAfterSave = pending.onAfterSave
                                )
                                pendingWorkSave = null
                                showActivityStatsConfirm = false
                            },
                            onDecline = {
                                commitWorkSave(
                                    pending.work,
                                    pending.previous,
                                    recordActivity = false,
                                    onAfterSave = pending.onAfterSave
                                )
                                pendingWorkSave = null
                                showActivityStatsConfirm = false
                            },
                            onDismiss = {
                                pendingWorkSave = null
                                showActivityStatsConfirm = false
                            }
                        )
                    }

                    if (showColorSettingsDialog) {
                        ColorSettingsDialog(
                            onDismissRequest = { showColorSettingsDialog = false },
                            dynamicColorsEnabled = dynamicColorsEnabled,
                            onDynamicColorsEnabledChange = onDynamicColorsEnabledChange,
                            useCustomIconColor = useCustomIconColor,
                            onUseCustomIconColorChange = onUseCustomIconColorChange,
                            customIconColorArgb = customIconColorArgb,
                            onCustomIconColorChange = onCustomIconColorChange,
                            useCustomTextColor = useCustomTextColor,
                            onUseCustomTextColorChange = onUseCustomTextColorChange,
                            customTextColorArgb = customTextColorArgb,
                            onCustomTextColorArgbChange = onCustomTextColorArgbChange,
                            useCustomStatsColor = useCustomStatsColor,
                            onUseCustomStatsColorChange = onUseCustomStatsColorChange,
                            customStatsArgb = customStatsArgb,
                            onCustomStatsArgbChange = onCustomStatsArgbChange,
                            useCustomButtonColor = useCustomButtonColor,
                            onUseCustomButtonColorChange = onUseCustomButtonColorChange,
                            customButtonColorArgb = customButtonColorArgb,
                            onCustomButtonColorArgbChange = onCustomButtonColorArgbChange,
                            onResetToDefaults = {
                                onUseCustomIconColorChange(false)
                                onCustomIconColorChange(0xFFEAEAEA.toInt())
                                onUseCustomTextColorChange(false)
                                onCustomTextColorArgbChange(0xFFEDEDED.toInt())
                                onUseCustomStatsColorChange(false)
                                onCustomStatsArgbChange(0xFF7C4DFF.toInt())
                                onUseCustomButtonColorChange(false)
                                onCustomButtonColorArgbChange(0xFF6750A4.toInt())
                                onUseCustomAccentChange(false)
                                onCustomAccentArgbChange(0xFF6750A4.toInt())
                                com.rn.library.ui.AppSettings.setUseCustomIconColor(context, false)
                                com.rn.library.ui.AppSettings.setCustomIconColorArgb(context, 0xFFEAEAEA.toInt())
                                com.rn.library.ui.AppSettings.setUseCustomTextColor(context, false)
                                com.rn.library.ui.AppSettings.setCustomTextColorArgb(context, 0xFFEDEDED.toInt())
                                com.rn.library.ui.AppSettings.setUseCustomStatsColor(context, false)
                                com.rn.library.ui.AppSettings.setCustomStatsArgb(context, 0xFF7C4DFF.toInt())
                                com.rn.library.ui.AppSettings.setUseCustomButtonColor(context, false)
                                com.rn.library.ui.AppSettings.setCustomButtonColorArgb(context, 0xFF6750A4.toInt())
                                com.rn.library.ui.AppSettings.setUseCustomAccent(context, false)
                                com.rn.library.ui.AppSettings.setCustomAccentArgb(context, 0xFF6750A4.toInt())
                            }
                        )
                    }
                }
            }
        }
}
