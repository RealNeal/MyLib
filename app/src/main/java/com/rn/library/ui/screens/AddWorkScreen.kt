package com.rn.library.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.rn.library.R
import com.rn.library.data.*
import com.rn.library.parser.GenreTagTranslator
import com.rn.library.parser.MediaSource
import com.rn.library.parser.NetworkParserManager
import com.rn.library.parser.ParserUtils
import com.rn.library.parser.ShikimoriFilterType
import com.rn.library.ui.Language
import com.rn.library.ui.components.ChipsFlowRow
import com.rn.library.ui.components.NetworkSearchDialog
import com.rn.library.ui.components.ScrollIsolatedMultilineField
import com.rn.library.ui.components.ShikimoriSearchDialog
import com.rn.library.ui.components.UnitProgressEditor
import com.rn.library.ui.components.buildDefaultUnits
import com.rn.library.ui.theme.IconTextColor
import com.rn.library.ui.theme.MainBackgroundColor
import com.rn.library.ui.theme.SearchBarColor
import com.rn.library.ui.theme.TitleColorBetween
import com.rn.library.ui.theme.AppButtonColor
import com.rn.library.ui.theme.FieldHighlightColor
import com.rn.library.ui.theme.ParserIconColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

// Функция для форматирования чисел без десятичных знаков, если они целые
fun formatNumberForDisplay(value: Double?): String {
    return if (value == null) {
        ""
    } else {
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }
}

// Преобразование альтернативных названий из формата сохранения в многострочный для отображения
fun formatAlternativeTitlesForDisplay(input: String?): String {
    if (input.isNullOrEmpty() || input.trim().equals("null", ignoreCase = true)) {
        return ""
    } else {
        return input.split(";")
            .map { ParserUtils.cleanText(it) }
            .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            .joinToString("\n")
    }
}

fun parseLinksList(input: String): List<String> {
    return input.split("\n", ";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun formatLinksForDisplay(link1: String?, link2: String?, linksList: List<String> = emptyList()): String {
    val list = mutableListOf<String>()
    list.addAll(linksList)
    link1?.takeIf { it.isNotBlank() }?.split("\n", ";", "||")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
        for (l in it) if (!list.contains(l)) list.add(l)
    }
    link2?.takeIf { it.isNotBlank() }?.split("\n", ";", "||")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let {
        for (l in it) if (!list.contains(l)) list.add(l)
    }
    return list.distinct().joinToString("\n")
}

fun parseLinksForSave(input: String): Pair<String?, String?> {
    val links = parseLinksList(input)
    val first = links.getOrNull(0)
    val second = links.getOrNull(1)
    return Pair(first, second)
}

@Composable
fun AddWorkScreen(
    onBack: () -> Unit,
    onSave: (Work) -> Unit,
    work: Work? = null,
    modifier: Modifier = Modifier,
) {
    val bg = MainBackgroundColor()
    val fieldBg = SearchBarColor()
    val text = TitleColorBetween()
    val iconColor = IconTextColor()
    val parserIconColor = ParserIconColor()
    val fieldHighlight = FieldHighlightColor()
    val accentColor = fieldHighlight
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = fieldBg,
        unfocusedContainerColor = fieldBg,
        disabledContainerColor = fieldBg,
        errorContainerColor = fieldBg,
        focusedTextColor = text,
        unfocusedTextColor = text,
        disabledTextColor = text,
        focusedLabelColor = fieldHighlight,
        unfocusedLabelColor = text.copy(alpha = 0.7f),
        focusedPlaceholderColor = text.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = text.copy(alpha = 0.5f),
        cursorColor = fieldHighlight,
        focusedIndicatorColor = fieldHighlight,
        unfocusedIndicatorColor = text.copy(alpha = 0.3f)
    )
    val context = LocalContext.current
    val repository = remember { WorkRepository(context) }
    val stableWorkId = remember(work?.id) { work?.id ?: UUID.randomUUID().toString() }

    // Основные поля
    var title by remember(work?.title) { mutableStateOf(ParserUtils.cleanText(work?.title)) }
    var alternativeTitles by remember(work?.otherTitle) { mutableStateOf(formatAlternativeTitlesForDisplay(work?.otherTitle)) }
    var author by remember(work?.author) {
        val a = work?.author?.trim()
        mutableStateOf(if (a.isNullOrBlank() || a.equals("null", ignoreCase = true)) "" else ParserUtils.cleanText(a))
    }
    var description by remember(work?.description) { mutableStateOf(work?.description ?: "") }
    var type by remember(work?.type) { mutableStateOf(work?.type ?: WorkType.BOOK) }
    var status by remember(work?.status) { mutableStateOf(work?.status ?: WorkStatus.IN_PLANS) }

    // Поля для томов/сезонов и глав/серий
    var volumes by remember(work?.volumes) { mutableStateOf(formatNumberForDisplay(work?.volumes)) }
    var seasons by remember(work?.seasons) { mutableStateOf(work?.seasons?.toString() ?: "") }
    var chapters by remember(work?.chapters) { mutableStateOf(formatNumberForDisplay(work?.chapters)) }
    var bookChapters by remember(work?.bookChapters) { mutableStateOf(formatNumberForDisplay(work?.bookChapters)) }
    var episodes by remember(work?.episodes) { mutableStateOf(formatNumberForDisplay(work?.episodes)) }
    var readChapters by remember(work?.progress) { mutableStateOf(formatNumberForDisplay(work?.progress)) }
    var useUnitProgress by remember(work?.unitProgress) {
        mutableStateOf(work?.unitProgress?.isNotEmpty() == true)
    }
    var unitProgressList by remember(work?.unitProgress) {
        mutableStateOf(work?.unitProgress ?: emptyList())
    }

    // Обложки (до 10) в порядке добавления; selectedCoverIndex — главная для списка библиотеки
    var coverPaths by remember(work?.id, work?.coverPath, work?.coverPaths) {
        mutableStateOf(work?.allCoverPaths()?.take(10) ?: emptyList())
    }
    var selectedCoverIndex by remember(work?.id, work?.coverPath, work?.coverPaths) {
        val paths = work?.allCoverPaths().orEmpty()
        val primaryIndex = work?.coverPath?.let { paths.indexOf(it) }?.takeIf { it >= 0 } ?: 0
        mutableStateOf(primaryIndex)
    }

    // Дополнительные поля
    var country by remember(work?.country) { mutableStateOf(work?.country ?: "") }
    var animeSeason by remember(work?.animeSeason) { mutableStateOf(work?.animeSeason ?: AnimeSeason.SPRING) }
    var mangaType by remember(work?.mangaType) { mutableStateOf(work?.mangaType ?: MangaType.MANGA) }
    var seriesType by remember(work?.seriesType) { mutableStateOf(work?.seriesType ?: SeriesType.TV_SERIES) }
    var year by remember(work?.year, work?.yearPeriod) {
        mutableStateOf(work?.yearPeriod ?: work?.year?.toString().orEmpty())
    }

    val isMonthFirst = isMonthFirstDateOrder()

    // Инициализация дат в формате ДДММГГГГ или ММДДГГГГ для отображения
    var dateRead by remember(work?.dateRead, isMonthFirst) {
        mutableStateOf(formatStorageDateForInput(work?.dateRead, isMonthFirst))
    }
    var rereadDatesText by remember(work?.rereadDates, isMonthFirst) {
        mutableStateOf(formatRereadDatesForInput(work?.rereadDates.orEmpty(), isMonthFirst))
    }
    var linksText by remember(work?.link, work?.link2, work?.links) {
        mutableStateOf(work?.allLinks()?.joinToString("\n") ?: "")
    }
    var genres by remember(work?.genres) {
        mutableStateOf(GenreTagTranslator.translateAndNormalizeChips(work?.genres ?: emptyList(), Language.RUSSIAN))
    }
    var tags by remember(work?.tags) {
        mutableStateOf(GenreTagTranslator.translateAndNormalizeChips(work?.tags ?: emptyList(), Language.RUSSIAN))
    }
    var genreInput by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    val defaultGenres = remember {
        listOf(
            "Детектив", "Драма", "Игры", "История", "Комедия",
            "Магия", "Меха", "Мистика", "Музыка", "Повседневность",
            "Приключения", "Психология", "Романтика", "Сверхъестественное",
            "Спорт", "Сёнэн", "Сэйнэн", "Сёдзё", "Триллер", "Ужасы",
            "Фантастика", "Фэнтези", "Школа", "Экшен"
        )
    }
    val existingGenres = remember {
        GenreTagTranslator.translateAndNormalizeChips(
            repository.getAllWorks().flatMap { it.genres.orEmpty() } + defaultGenres + com.rn.library.data.GenreTagManager.getCustomGenres(context),
            Language.RUSSIAN
        ).sorted()
    }
    val existingTags = remember {
        GenreTagTranslator.translateAndNormalizeChips(
            repository.getAllWorks().flatMap { it.tags.orEmpty() } + com.rn.library.data.GenreTagManager.getCustomTags(context),
            Language.RUSSIAN
        ).sorted()
    }
    var note by remember(work?.note) { mutableStateOf(work?.note ?: "") }

    // Состояния для выпадающих списков
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var seasonDropdownExpanded by remember { mutableStateOf(false) }
    var mangaTypeDropdownExpanded by remember { mutableStateOf(false) }
    var seriesTypeDropdownExpanded by remember { mutableStateOf(false) }
    var showNetworkSearchDialog by remember { mutableStateOf(false) }

    fun closeNow() {
        typeDropdownExpanded = false
        statusDropdownExpanded = false
        seasonDropdownExpanded = false
        mangaTypeDropdownExpanded = false
        seriesTypeDropdownExpanded = false
        onBack()
    }

    // Обработка системной кнопки "Назад"
    BackHandler(enabled = showNetworkSearchDialog) {
        showNetworkSearchDialog = false
        NetworkParserManager.clearCache()
    }
    BackHandler(enabled = !showNetworkSearchDialog) { closeNow() }

    // Launcher для выбора обложки
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            val persisted = repository.persistCoverFromPicker(pickedUri, stableWorkId, coverPaths.size)
                ?: pickedUri.toString()
            val existingIndex = coverPaths.indexOf(persisted)
            if (existingIndex >= 0) {
                selectedCoverIndex = existingIndex
            } else if (coverPaths.size < 10) {
                coverPaths = coverPaths + persisted
            }
        }
    }

    // Валидация года: YYYY или период YYYY - YYYY
    val singleYearPattern = Pattern.compile("^\\d{0,4}$")
    val yearPeriodPattern = Pattern.compile("^\\d{4}\\s?-\\s?\\d{0,4}$")

    // Форматирование альтернативных названий для сохранения (преобразование переносов в точки с запятой)
    fun formatAlternativeTitlesForSave(input: String): String {
        return input.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
    }

    // Получаем правильные опции статуса в зависимости от типа
    val statusOptions = when (type) {
        WorkType.BOOK, WorkType.MANGA -> listOf(
            WorkStatus.IN_PLANS to stringResource(R.string.in_plans),
            WorkStatus.READING to stringResource(R.string.reading),
            WorkStatus.READ to stringResource(R.string.read),
            WorkStatus.ABANDONED to stringResource(R.string.abandoned)
        )
        WorkType.ANIME, WorkType.SERIES -> listOf(
            WorkStatus.IN_PLANS to stringResource(R.string.in_plans),
            WorkStatus.WATCHING to stringResource(R.string.watching),
            WorkStatus.WATCHED to stringResource(R.string.watched),
            WorkStatus.ABANDONED to stringResource(R.string.abandoned)
        )
    }

    // Проверяем, нужно ли показывать дату прочтения
    val showDateRead = status in listOf(WorkStatus.READ, WorkStatus.WATCHED, WorkStatus.ABANDONED)

    val seasonWinter = stringResource(R.string.winter)
    val seasonSpring = stringResource(R.string.spring)
    val seasonSummer = stringResource(R.string.summer)
    val seasonFall = stringResource(R.string.fall)
    val typeBook = stringResource(R.string.book)
    val typeAnime = stringResource(R.string.anime)
    val typeManga = stringResource(R.string.manga)
    val typeSeries = stringResource(R.string.tv_series)
    val mangaTypeManga = stringResource(R.string.manga)
    val mangaTypeManhwa = stringResource(R.string.manhwa)
    val mangaTypeManhua = stringResource(R.string.manhua)
    val seriesTypeTv = stringResource(R.string.tv_series)
    val seriesTypeFilm = stringResource(R.string.film)
    val seriesTypeCartoon = stringResource(R.string.cartoon)
    val seriesTypeDrama = stringResource(R.string.drama)
    val keyboardInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val formKeyboardPadding = (keyboardInset - 44.dp).coerceAtLeast(0.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .padding(bottom = formKeyboardPadding)
        ) {
        // Заголовок с кнопкой назад
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(vertical = 16.dp)
        ) {
            IconButton(onClick = ::closeNow) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = iconColor)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (work == null) stringResource(R.string.add_work) else stringResource(R.string.edit_work),
                color = text,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showNetworkSearchDialog = true }) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = stringResource(R.string.search_network),
                    tint = parserIconColor
                )
            }
        }

        val formScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(formScrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Название
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.title)) },
                placeholder = { Text(stringResource(R.string.title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showNetworkSearchDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = stringResource(R.string.search_network),
                            tint = parserIconColor
                        )
                    }
                },
                colors = fieldColors
            )

            // Альтернативные названия: фиксированная высота + изоляция скролла от формы
            ScrollIsolatedMultilineField(
                value = alternativeTitles,
                onValueChange = { alternativeTitles = it },
                label = { Text(stringResource(R.string.alternative_titles)) },
                placeholder = { Text(stringResource(R.string.other_titles_placeholder)) },
                minLines = 1,
                maxLines = 3,
                colors = fieldColors,
                parentScrollState = formScrollState,
            )

            // Автор
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.author)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors
            )


            ScrollIsolatedMultilineField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description)) },
                minLines = 5,
                maxLines = 11,
                colors = fieldColors,
                parentScrollState = formScrollState,
            )

            // Тип произведения
            CustomDropdown(
                label = stringResource(R.string.type),
                value = when (type) {
                    WorkType.BOOK -> typeBook
                    WorkType.ANIME -> typeAnime
                    WorkType.MANGA -> typeManga
                    WorkType.SERIES -> typeSeries
                },
                items = listOf(typeBook, typeAnime, typeManga, typeSeries),
                expanded = typeDropdownExpanded,
                onExpandedChange = { typeDropdownExpanded = it },
                onItemSelected = { selected ->
                    type = when (selected) {
                        typeBook -> WorkType.BOOK
                        typeAnime -> WorkType.ANIME
                        typeManga -> WorkType.MANGA
                        typeSeries -> WorkType.SERIES
                        else -> WorkType.BOOK
                    }
                    typeDropdownExpanded = false
                    // Сбрасываем статус если он не соответствует типу
                    val validStatuses = statusOptions.map { it.first }
                    if (status !in validStatuses) {
                        status = WorkStatus.IN_PLANS
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Подтип для манги/сериала
            AnimatedVisibility(
                visible = type == WorkType.MANGA,
                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                CustomDropdown(
                    label = stringResource(R.string.manga_type),
                    value = when (mangaType) {
                        MangaType.MANGA -> mangaTypeManga
                        MangaType.MANHWA -> mangaTypeManhwa
                        MangaType.MANHUA -> mangaTypeManhua
                    },
                    items = listOf(mangaTypeManga, mangaTypeManhwa, mangaTypeManhua),
                    expanded = mangaTypeDropdownExpanded,
                    onExpandedChange = { mangaTypeDropdownExpanded = it },
                    onItemSelected = { selected ->
                        mangaType = when (selected) {
                            mangaTypeManga -> MangaType.MANGA
                            mangaTypeManhwa -> MangaType.MANHWA
                            mangaTypeManhua -> MangaType.MANHUA
                            else -> MangaType.MANGA
                        }
                        mangaTypeDropdownExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = type == WorkType.SERIES,
                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                CustomDropdown(
                    label = stringResource(R.string.tv_series_type),
                    value = when (seriesType) {
                        SeriesType.TV_SERIES -> seriesTypeTv
                        SeriesType.FILM -> seriesTypeFilm
                        SeriesType.CARTOON -> seriesTypeCartoon
                        SeriesType.DRAMA -> seriesTypeDrama
                    },
                    items = listOf(seriesTypeTv, seriesTypeFilm, seriesTypeCartoon, seriesTypeDrama),
                    expanded = seriesTypeDropdownExpanded,
                    onExpandedChange = { seriesTypeDropdownExpanded = it },
                    onItemSelected = { selected ->
                        seriesType = when (selected) {
                            seriesTypeTv -> SeriesType.TV_SERIES
                            seriesTypeFilm -> SeriesType.FILM
                            seriesTypeCartoon -> SeriesType.CARTOON
                            seriesTypeDrama -> SeriesType.DRAMA
                            else -> SeriesType.TV_SERIES
                        }
                        seriesTypeDropdownExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Тома/Сезоны
            val volumesLabel = when (type) {
                WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.volumes)
                WorkType.SERIES -> stringResource(R.string.seasons)
                WorkType.ANIME -> "" // Для аниме тома не используются
            }
            val volumesValue = when (type) {
                WorkType.BOOK -> chapters
                WorkType.MANGA -> volumes
                WorkType.SERIES -> seasons
                WorkType.ANIME -> ""
            }

            AnimatedVisibility(
                visible = volumesLabel.isNotEmpty(),
                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                OutlinedTextField(
                    value = volumesValue,
                    onValueChange = { newValue ->
                        when (type) {
                            WorkType.BOOK -> chapters = newValue
                            WorkType.MANGA -> volumes = newValue
                            WorkType.SERIES -> seasons = newValue
                            WorkType.ANIME -> {}
                        }
                    },
                    label = { Text(volumesLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors
                )
            }

            // Главы/Серии
            val chaptersLabel = when (type) {
                WorkType.BOOK -> stringResource(R.string.chapters)
                WorkType.MANGA -> stringResource(R.string.chapters)
                WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.episodes)
            }
            val chaptersValue = when (type) {
                WorkType.BOOK -> bookChapters
                WorkType.MANGA -> chapters
                WorkType.ANIME, WorkType.SERIES -> episodes
            }

            OutlinedTextField(
                value = chaptersValue,
                onValueChange = { newValue ->
                    when (type) {
                        WorkType.BOOK -> bookChapters = newValue
                        WorkType.MANGA -> chapters = newValue
                        WorkType.ANIME, WorkType.SERIES -> episodes = newValue
                    }
                },
                label = { Text(chaptersLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors
            )

            val unitCountHint = when (type) {
                WorkType.BOOK -> chapters.toDoubleOrNull()?.toInt()
                WorkType.MANGA -> volumes.toDoubleOrNull()?.toInt()
                WorkType.SERIES -> seasons.toIntOrNull()
                WorkType.ANIME -> seasons.toIntOrNull()
            }

            val volumeUnitPrefix = stringResource(R.string.volume_unit_prefix)
            val seasonUnitPrefix = stringResource(R.string.season_unit_prefix)

            UnitProgressEditor(
                enabled = useUnitProgress,
                onEnabledChange = { enabled ->
                    useUnitProgress = enabled
                    if (!enabled) {
                        unitProgressList = emptyList()
                    } else if (unitProgressList.isEmpty() && unitCountHint != null && unitCountHint > 0) {
                        val prefix = when (type) {
                            WorkType.BOOK, WorkType.MANGA -> volumeUnitPrefix
                            WorkType.SERIES, WorkType.ANIME -> seasonUnitPrefix
                        }
                        unitProgressList = buildDefaultUnits(unitCountHint, prefix)
                    }
                },
                units = unitProgressList,
                onUnitsChange = { unitProgressList = it },
                workType = type,
                unitCountHint = unitCountHint,
                fieldBg = fieldBg,
                labelColor = text
            )

            val progressLabel = when (type) {
                WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.progress_read_chapters)
                WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.progress_watched_episodes)
            }

            OutlinedTextField(
                value = readChapters,
                onValueChange = { readChapters = it },
                label = { Text(progressLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors
            )

            // Обложка
            Column {
                Text(
                    "${stringResource(R.string.cover)} (${coverPaths.size}/10)",
                    color = text,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(fieldBg, RoundedCornerShape(12.dp))
                        .clickable {
                            if (coverPaths.size < 10) {
                                coverPickerLauncher.launch("image/*")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val selectedCover = coverPaths.getOrNull(selectedCoverIndex)
                    if (!selectedCover.isNullOrBlank()) {
                        androidx.compose.foundation.Image(
                            painter = rememberAsyncImagePainter(selectedCover.toCoverImageData()),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            stringResource(R.string.cover),
                            color = text.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                if (coverPaths.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val density = LocalDensity.current
                    val itemTotalWidthPx = with(density) { 80.dp.toPx() }
                    var draggingCoverPath by remember { mutableStateOf<String?>(null) }
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    var touchViewportX by remember { mutableFloatStateOf(0f) }
                    val rowScrollState = rememberScrollState()
                    var rowViewportWidth by remember { mutableIntStateOf(0) }

                    LaunchedEffect(draggingCoverPath) {
                        val activePath = draggingCoverPath ?: return@LaunchedEffect
                        while (isActive && draggingCoverPath == activePath) {
                            val curList = coverPaths
                            val curIdx = curList.indexOf(activePath)
                            if (curIdx != -1 && rowViewportWidth > 0) {
                                val edgeZone = with(density) { 60.dp.toPx() }
                                val rightDist = rowViewportWidth - touchViewportX
                                val leftDist = touchViewportX

                                if (rightDist < edgeZone && curIdx < curList.size - 1 && rowScrollState.value < rowScrollState.maxValue) {
                                    val factor = ((edgeZone - rightDist) / edgeZone).coerceIn(0.2f, 1f)
                                    val scrollDelta = with(density) { (8.dp * factor).toPx() }
                                    rowScrollState.dispatchRawDelta(scrollDelta)
                                } else if (leftDist < edgeZone && curIdx > 0 && rowScrollState.value > 0) {
                                    val factor = ((edgeZone - leftDist) / edgeZone).coerceIn(0.2f, 1f)
                                    val scrollDelta = with(density) { (-8.dp * factor).toPx() }
                                    rowScrollState.dispatchRawDelta(scrollDelta)
                                }

                                val restingCenter = curIdx * itemTotalWidthPx - rowScrollState.value + itemTotalWidthPx / 2f
                                dragOffset = touchViewportX - restingCenter

                                while (dragOffset > itemTotalWidthPx / 2f) {
                                    val cList = coverPaths
                                    val cIdx = cList.indexOf(activePath)
                                    if (cIdx != -1 && cIdx < cList.size - 1) {
                                        val nextIdx = cIdx + 1
                                        val mutableList = cList.toMutableList()
                                        val item = mutableList.removeAt(cIdx)
                                        mutableList.add(nextIdx, item)
                                        selectedCoverIndex = when (selectedCoverIndex) {
                                            cIdx -> nextIdx
                                            nextIdx -> cIdx
                                            else -> selectedCoverIndex
                                        }
                                        coverPaths = mutableList
                                        val newRestingCenter = nextIdx * itemTotalWidthPx - rowScrollState.value + itemTotalWidthPx / 2f
                                        dragOffset = touchViewportX - newRestingCenter
                                    } else break
                                }

                                while (dragOffset < -itemTotalWidthPx / 2f) {
                                    val cList = coverPaths
                                    val cIdx = cList.indexOf(activePath)
                                    if (cIdx != -1 && cIdx > 0) {
                                        val prevIdx = cIdx - 1
                                        val mutableList = cList.toMutableList()
                                        val item = mutableList.removeAt(cIdx)
                                        mutableList.add(prevIdx, item)
                                        selectedCoverIndex = when (selectedCoverIndex) {
                                            cIdx -> prevIdx
                                            prevIdx -> cIdx
                                            else -> selectedCoverIndex
                                        }
                                        coverPaths = mutableList
                                        val newRestingCenter = prevIdx * itemTotalWidthPx - rowScrollState.value + itemTotalWidthPx / 2f
                                        dragOffset = touchViewportX - newRestingCenter
                                    } else break
                                }
                            }
                            delay(16L)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { rowViewportWidth = it.width }
                            .horizontalScroll(rowScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        coverPaths.forEachIndexed { index, coverPath ->
                            key(coverPath) {
                                val isDragging = draggingCoverPath == coverPath
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationX = dragOffset
                                                scaleX = 1.1f
                                                scaleY = 1.1f
                                                shadowElevation = 8.dp.toPx()
                                            }
                                            shape = RoundedCornerShape(8.dp)
                                            clip = true
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (index == selectedCoverIndex) text.copy(alpha = 0.25f) else fieldBg
                                        )
                                        .then(
                                            if (isDragging) {
                                                Modifier.border(2.dp, accentColor, RoundedCornerShape(8.dp))
                                            } else if (index == selectedCoverIndex) {
                                                Modifier.border(2.dp, text.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .pointerInput(coverPath) {
                                            if (coverPaths.size > 1) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        draggingCoverPath = coverPath
                                                        val curIdx = coverPaths.indexOf(coverPath)
                                                        val itemStartX = curIdx * itemTotalWidthPx - rowScrollState.value
                                                        touchViewportX = itemStartX + offset.x
                                                        dragOffset = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        touchViewportX += dragAmount.x
                                                        val curList = coverPaths
                                                        val curIdx = curList.indexOf(coverPath)
                                                        if (curIdx != -1) {
                                                            val restingCenter = curIdx * itemTotalWidthPx - rowScrollState.value + itemTotalWidthPx / 2f
                                                            dragOffset = touchViewportX - restingCenter

                                                            while (dragOffset > itemTotalWidthPx / 2f) {
                                                                val cList = coverPaths
                                                                val cIdx = cList.indexOf(coverPath)
                                                                if (cIdx != -1 && cIdx < cList.size - 1) {
                                                                    val nextIdx = cIdx + 1
                                                                    val mutableList = cList.toMutableList()
                                                                    val item = mutableList.removeAt(cIdx)
                                                                    mutableList.add(nextIdx, item)
                                                                    selectedCoverIndex = when (selectedCoverIndex) {
                                                                        cIdx -> nextIdx
                                                                        nextIdx -> cIdx
                                                                        else -> selectedCoverIndex
                                                                    }
                                                                    coverPaths = mutableList
                                                                    val newRestingCenter = nextIdx * itemTotalWidthPx - rowScrollState.value + itemTotalWidthPx / 2f
                                                                    dragOffset = touchViewportX - newRestingCenter
                                                                } else break
                                                            }

                                                            while (dragOffset < -itemTotalWidthPx / 2f) {
                                                                val cList = coverPaths
                                                                val cIdx = cList.indexOf(coverPath)
                                                                if (cIdx != -1 && cIdx > 0) {
                                                                    val prevIdx = cIdx - 1
                                                                    val mutableList = cList.toMutableList()
                                                                    val item = mutableList.removeAt(cIdx)
                                                                    mutableList.add(prevIdx, item)
                                                                    selectedCoverIndex = when (selectedCoverIndex) {
                                                                        cIdx -> prevIdx
                                                                        prevIdx -> cIdx
                                                                        else -> selectedCoverIndex
                                                                    }
                                                                    coverPaths = mutableList
                                                                    val newRestingCenter = prevIdx * itemTotalWidthPx - rowScrollState.value + itemTotalWidthPx / 2f
                                                                    dragOffset = touchViewportX - newRestingCenter
                                                                } else break
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        draggingCoverPath = null
                                                        dragOffset = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingCoverPath = null
                                                        dragOffset = 0f
                                                    }
                                                )
                                            }
                                        }
                                        .clickable { selectedCoverIndex = index }
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = rememberAsyncImagePainter(coverPath.toCoverImageData()),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = {
                                            val newList = coverPaths.toMutableList().also { it.removeAt(index) }
                                            coverPaths = newList
                                            selectedCoverIndex = selectedCoverIndex.coerceAtMost((newList.size - 1).coerceAtLeast(0))
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (coverPaths.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.drag_covers_hint),
                            color = text.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Страна
            OutlinedTextField(
                value = country,
                onValueChange = { country = it },
                label = { Text(stringResource(R.string.country)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors
            )

            // Время года (только для аниме)
            AnimatedVisibility(
                visible = type == WorkType.ANIME,
                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                CustomDropdown(
                    label = stringResource(R.string.anime_season),
                    value = when (animeSeason) {
                        AnimeSeason.SPRING -> seasonSpring
                        AnimeSeason.SUMMER -> seasonSummer
                        AnimeSeason.FALL -> seasonFall
                        AnimeSeason.WINTER -> seasonWinter
                    },
                    items = listOf(seasonWinter, seasonSpring, seasonSummer, seasonFall),
                    expanded = seasonDropdownExpanded,
                    onExpandedChange = { seasonDropdownExpanded = it },
                    onItemSelected = { selected ->
                        animeSeason = when (selected) {
                            seasonWinter -> AnimeSeason.WINTER
                            seasonSpring -> AnimeSeason.SPRING
                            seasonSummer -> AnimeSeason.SUMMER
                            seasonFall -> AnimeSeason.FALL
                            else -> AnimeSeason.SPRING
                        }
                        seasonDropdownExpanded = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Год
            OutlinedTextField(
                value = year,
                onValueChange = { input ->
                    val normalized = input.trimStart()
                    if (
                        normalized.isEmpty() ||
                        singleYearPattern.matcher(normalized).matches() ||
                        yearPeriodPattern.matcher(normalized).matches()
                    ) {
                        year = input
                    }
                },
                label = { Text(stringResource(R.string.year)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                placeholder = { Text(stringResource(R.string.year_placeholder)) },
                colors = fieldColors
            )

            // Статус
            CustomDropdown(
                label = stringResource(R.string.status),
                value = when (status) {
                    WorkStatus.IN_PLANS -> stringResource(R.string.in_plans)
                    WorkStatus.READING -> stringResource(R.string.reading)
                    WorkStatus.WATCHING -> stringResource(R.string.watching)
                    WorkStatus.READ -> stringResource(R.string.read)
                    WorkStatus.WATCHED -> stringResource(R.string.watched)
                    WorkStatus.ABANDONED -> stringResource(R.string.abandoned)
                },
                items = statusOptions.map { it.second },
                expanded = statusDropdownExpanded,
                onExpandedChange = { statusDropdownExpanded = it },
                onItemSelected = { selected ->
                    status = statusOptions.find { it.second == selected }?.first ?: WorkStatus.IN_PLANS
                    statusDropdownExpanded = false
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Дата прочтения (если статус прочитано/просмотрено/заброшено)
            AnimatedVisibility(
                visible = showDateRead,
                enter = expandVertically(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                        fadeIn(animationSpec = tween(250, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing))
            ) {
                OutlinedTextField(
                    value = dateRead,
                    onValueChange = { input ->
                        // Сохраняем только цифры, максимум 8 (ДДММГГГГ)
                        val digits = input.filter { it.isDigit() }.take(8)
                        dateRead = digits
                    },
                    label = { Text(
                        when (type) {
                            WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.date_read_for_books)
                            else -> stringResource(R.string.date_watched)
                        }
                    ) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(datePlaceholder(isMonthFirst)) },
                    visualTransformation = DateVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors
                )
            }

            ScrollIsolatedMultilineField(
                value = rereadDatesText,
                onValueChange = { rereadDatesText = filterRereadDatesInput(it, isMonthFirst) },
                label = {
                    Text(
                        when (type) {
                            WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.date_reread)
                            WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.date_rewatch)
                            else -> stringResource(R.string.repeats)
                        }
                    )
                },
                placeholder = { Text(rereadDatesPlaceholder(isMonthFirst)) },
                minLines = 1,
                maxLines = 5,
                visualTransformation = DateVisualTransformation(),
                colors = fieldColors,
                parentScrollState = formScrollState,
            )

            // Жанры
            ChipInputSection(
                label = stringResource(R.string.genres),
                placeholder = stringResource(R.string.genres_placeholder),
                inputValue = genreInput,
                onInputValueChange = { genreInput = it },
                items = genres,
                onItemsChange = { genres = it },
                suggestions = existingGenres,
                fieldColors = fieldColors,
                chipAccentColor = fieldHighlight,
                isTag = false
            )

            // Теги
            ChipInputSection(
                label = stringResource(R.string.tags),
                placeholder = stringResource(R.string.tags_placeholder),
                inputValue = tagInput,
                onInputValueChange = { tagInput = it },
                items = tags,
                onItemsChange = { tags = it },
                suggestions = existingTags,
                fieldColors = fieldColors,
                chipAccentColor = fieldHighlight,
                isTag = true
            )

            // Ссылки (одиночное поле для нескольких ссылок)
            ScrollIsolatedMultilineField(
                value = linksText,
                onValueChange = { linksText = it },
                label = { Text(stringResource(R.string.links)) },
                placeholder = { Text(stringResource(R.string.links_placeholder)) },
                minLines = 1,
                maxLines = 5,
                colors = fieldColors,
                parentScrollState = formScrollState,
            )

            // Заметка
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors
            )

            Button(
                onClick = {
                    val id = stableWorkId
                    val rereadSaved = parseRereadDatesForSave(rereadDatesText, isMonthFirst)

                    val workToSave = Work(
                        // Год может быть либо одиночным, либо периодом.
                        // Если введён период, сохраняем в yearPeriod, а year оставляем null.
                        // Если введён одиночный год, сохраняем в year.
                        // Это сохраняет обратную совместимость со старыми данными.
                        id = id,
                        title = title.trim(),
                        description = description,
                        type = type,
                        status = status,
                        coverPath = coverPaths.getOrNull(selectedCoverIndex),
                        otherTitle = formatAlternativeTitlesForSave(alternativeTitles).ifEmpty { null },
                        coverPaths = coverPaths.filterIndexed { index, _ -> index != selectedCoverIndex },
                        volumes = if (type == WorkType.MANGA) volumes.toDoubleOrNull() else null,
                        seasons = seasons.toIntOrNull(),
                        chapters = when (type) {
                            WorkType.BOOK, WorkType.MANGA -> chapters.toDoubleOrNull()
                            else -> null
                        },
                        bookChapters = bookChapters.toDoubleOrNull(),
                        episodes = episodes.toDoubleOrNull(),
                        progress = when {
                            useUnitProgress && unitProgressList.isNotEmpty() ->
                                unitProgressList.sumOf { it.completed }
                            readChapters.isBlank() -> null
                            else -> readChapters.toDoubleOrNull()
                        },
                        activeUnitIndex = work?.activeUnitIndex,
                        country = country.ifEmpty { null },
                        animeSeason = animeSeason,
                        mangaType = if (type == WorkType.MANGA) mangaType else null,
                        seriesType = if (type == WorkType.SERIES) seriesType else null,
                        year = year.trim().takeIf { singleYearPattern.matcher(it).matches() }?.toIntOrNull(),
                        yearPeriod = year.trim()
                            .takeIf { yearPeriodPattern.matcher(it).matches() }
                            ?.replace(Regex("\\s*-\\s*"), " - "),
                        dateRead = formatDateForSave(dateRead, isMonthFirst).ifEmpty { null },
                        rereadDates = rereadSaved,
                        link = parseLinksList(linksText).getOrNull(0),
                        link2 = parseLinksList(linksText).getOrNull(1),
                        links = parseLinksList(linksText),
                        author = author.trim().ifEmpty { null },
                        note = note.ifEmpty { null },
                        updatedAt = System.currentTimeMillis(),
                        genres = GenreTagTranslator.translateAndNormalizeChips(genres, Language.RUSSIAN),
                        tags = GenreTagTranslator.translateAndNormalizeChips(tags, Language.RUSSIAN),
                        // Сохраняем существующие поля
                        abandonedProgress = work?.abandonedProgress,
                        readingPeriods = work?.readingPeriods ?: emptyList(),
                        unitProgress = if (useUnitProgress) unitProgressList else emptyList()
                    )
                    onSave(workToSave)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppButtonColor()
                )
            ) {
                Text(stringResource(R.string.save))
            }

            // Чтобы кнопка и последние поля не “прилипали” к нижней панели вкладок.
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    }

    if (showNetworkSearchDialog) {
        val initialSrc = when (type) {
            WorkType.BOOK -> MediaSource.RANOBELIB
            WorkType.MANGA -> MediaSource.MANGALIB
            WorkType.SERIES -> MediaSource.KINOPOISK
            WorkType.ANIME -> MediaSource.SHIKIMORI
        }
        NetworkSearchDialog(
            initialQuery = title,
            initialSource = initialSrc,
            initialFilter = ShikimoriFilterType.ALL,
            workId = stableWorkId,
            repository = repository,
            onDismiss = {
                showNetworkSearchDialog = false
                NetworkParserManager.clearCache()
            },
            onWorkSelected = { metadata, localCoverPath ->
                NetworkParserManager.clearCache()
                try {
                    title = ParserUtils.cleanText(metadata.title)
                    val cleanAlt = metadata.alternativeTitles
                        .map { ParserUtils.cleanText(it) }
                        .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) && it != title }
                    alternativeTitles = if (cleanAlt.isNotEmpty()) cleanAlt.joinToString("\n") else ""
                    if (metadata.description.isNotBlank()) {
                        description = ParserUtils.cleanDescription(metadata.description)
                    }
                    type = metadata.type
                    // Проверяем и при необходимости гармонизируем статус с новым типом произведения
                    val validStatuses = when (metadata.type) {
                        WorkType.BOOK, WorkType.MANGA -> listOf(WorkStatus.IN_PLANS, WorkStatus.READING, WorkStatus.READ, WorkStatus.ABANDONED)
                        WorkType.ANIME, WorkType.SERIES -> listOf(WorkStatus.IN_PLANS, WorkStatus.WATCHING, WorkStatus.WATCHED, WorkStatus.ABANDONED)
                    }
                    if (status !in validStatuses) {
                        status = when (status) {
                            WorkStatus.READING -> WorkStatus.WATCHING
                            WorkStatus.WATCHING -> WorkStatus.READING
                            WorkStatus.READ -> WorkStatus.WATCHED
                            WorkStatus.WATCHED -> WorkStatus.READ
                            else -> WorkStatus.IN_PLANS
                        }
                    }

                    metadata.mangaType?.let { mangaType = it }
                    metadata.seriesType?.let { seriesType = it }
                    metadata.animeSeason?.let { animeSeason = it }
                    metadata.year?.let { year = it.toString() }
                    if (metadata.type == WorkType.BOOK) {
                        val totalCh = metadata.bookChapters ?: metadata.chapters
                        totalCh?.let { bookChapters = formatNumberForDisplay(it) }
                        metadata.volumes?.let { chapters = formatNumberForDisplay(it) }
                    } else if (metadata.type == WorkType.MANGA) {
                        metadata.chapters?.let { chapters = formatNumberForDisplay(it) }
                        metadata.volumes?.let { volumes = formatNumberForDisplay(it) }
                    } else {
                        val totalEp = metadata.episodes ?: metadata.chapters
                        totalEp?.let { episodes = formatNumberForDisplay(it) }
                        metadata.volumes?.let { seasons = formatNumberForDisplay(it) }
                    }
                    val cleanAuthor = ParserUtils.sanitizeAuthor(metadata.author)
                    author = cleanAuthor ?: ""
                    if (metadata.genres.isNotEmpty() || genres.isNotEmpty()) {
                        genres = GenreTagTranslator.translateAndNormalizeChips(genres + metadata.genres, Language.RUSSIAN)
                    }
                    if (metadata.tags.isNotEmpty() || tags.isNotEmpty()) {
                        tags = GenreTagTranslator.translateAndNormalizeChips(tags + metadata.tags, Language.RUSSIAN)
                    }
                    val existingLinks = linksText.split("\n", ";").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                    if (!metadata.link.isNullOrBlank() && !existingLinks.contains(metadata.link.trim())) {
                        existingLinks.add(metadata.link.trim())
                    }
                    for (extraLink in metadata.additionalLinks) {
                        val trimmed = extraLink.trim()
                        if (trimmed.isNotEmpty() && !existingLinks.contains(trimmed)) {
                            existingLinks.add(trimmed)
                        }
                    }
                    linksText = existingLinks.joinToString("\n")

                    val primaryCover = localCoverPath ?: metadata.coverUrl
                    val allNewCovers = (listOfNotNull(primaryCover) + metadata.additionalCovers).filter { it.isNotBlank() }
                    if (allNewCovers.isNotEmpty()) {
                        val combined = (allNewCovers + coverPaths).distinct().take(10)
                        coverPaths = combined
                        selectedCoverIndex = 0
                    }
                    Toast.makeText(context, context.getString(R.string.network_import_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        )
    }
}

@Composable
private fun CustomDropdown(
    label: String,
    value: String,
    items: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldBg = SearchBarColor()
    val text = TitleColorBetween()
    val fieldHighlight = FieldHighlightColor()
    val accentColor = fieldHighlight
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = fieldBg,
        unfocusedContainerColor = fieldBg,
        disabledContainerColor = fieldBg,
        errorContainerColor = fieldBg,
        focusedTextColor = text,
        unfocusedTextColor = text,
        disabledTextColor = text,
        focusedLabelColor = accentColor,
        unfocusedLabelColor = text.copy(alpha = 0.7f),
        disabledLabelColor = text.copy(alpha = 0.7f),
        focusedPlaceholderColor = text.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = text.copy(alpha = 0.5f),
        disabledPlaceholderColor = text.copy(alpha = 0.5f),
        cursorColor = accentColor,
        focusedIndicatorColor = accentColor,
        unfocusedIndicatorColor = text.copy(alpha = 0.3f),
        disabledIndicatorColor = text.copy(alpha = 0.3f),
        disabledTrailingIconColor = text
    )

    Box(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        ) {
            focusManager.clearFocus()
            onExpandedChange(!expanded)
        }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = text
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
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
                    items.forEach { item ->
                        val isSelected = item == value
                        AssistChip(
                            onClick = {
                                onItemSelected(item)
                            },
                            label = {
                                Text(
                                    text = item,
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

@Composable
private fun ChipInputSection(
    label: String,
    placeholder: String,
    inputValue: String,
    onInputValueChange: (String) -> Unit,
    items: List<String>,
    onItemsChange: (List<String>) -> Unit,
    suggestions: List<String>,
    fieldColors: TextFieldColors,
    chipAccentColor: Color,
    isTag: Boolean = false
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val arrowColor = TitleColorBetween()
    val interactionSource = remember { MutableInteractionSource() }

    val availableSuggestions = suggestions.filter { it !in items }
    val filteredSuggestions = if (inputValue.isBlank()) {
        availableSuggestions
    } else {
        val clean = if (isTag && inputValue.startsWith("#")) inputValue.removePrefix("#").trim() else inputValue.trim()
        availableSuggestions.filter { it.contains(clean, ignoreCase = true) }
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                if (availableSuggestions.isNotEmpty()) {
                    dropdownExpanded = true
                }
            }
        }
    }

    fun addItems(raw: String) {
        val newParts = raw.split(",", ";", "\n")
            .map { it.trim() }
            .map { if (isTag && it.startsWith("#")) it.removePrefix("#").trim() else it }
            .filter { it.isNotBlank() }
        if (newParts.isNotEmpty()) {
            val updated = GenreTagTranslator.translateAndNormalizeChips(items + newParts, Language.RUSSIAN)
            onItemsChange(updated)
            onInputValueChange("")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = inputValue,
                onValueChange = {
                    onInputValueChange(it)
                    dropdownExpanded = true
                },
                interactionSource = interactionSource,
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && availableSuggestions.isNotEmpty()) {
                            dropdownExpanded = true
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    addItems(inputValue)
                    dropdownExpanded = false
                }),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (inputValue.isNotBlank()) {
                            IconButton(onClick = {
                                addItems(inputValue)
                                dropdownExpanded = false
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = label,
                                    tint = chipAccentColor
                                )
                            }
                        }
                        if (availableSuggestions.isNotEmpty()) {
                            IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                Icon(
                                    imageVector = if (dropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = arrowColor
                                )
                            }
                        }
                    }
                },
                colors = fieldColors
            )

            DropdownMenu(
                expanded = dropdownExpanded && filteredSuggestions.isNotEmpty(),
                onDismissRequest = { dropdownExpanded = false },
                properties = PopupProperties(focusable = false),
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .heightIn(max = 260.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredSuggestions.forEach { suggestion ->
                            val displaySuggestion = if (isTag && !suggestion.startsWith("#")) "#$suggestion" else suggestion
                            AssistChip(
                                onClick = {
                                    val translated = GenreTagTranslator.translateTag(suggestion, Language.RUSSIAN)
                                    onItemsChange(GenreTagTranslator.translateAndNormalizeChips(items + listOf(translated), Language.RUSSIAN))
                                    onInputValueChange("")
                                    dropdownExpanded = false
                                },
                                label = {
                                    Text(
                                        text = displaySuggestion,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Normal
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = chipAccentColor.copy(alpha = 0.5f),
                                    borderWidth = 1.dp,
                                    enabled = true
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Added chips (only selected genres / tags are shown under the field)
        if (items.isNotEmpty()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                ChipsFlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalSpacing = 4.dp,
                    verticalSpacing = 4.dp
                ) {
                    items.forEach { item ->
                        val displayLabel = if (isTag && !item.startsWith("#")) "#$item" else item
                        AssistChip(
                            onClick = { onItemsChange(items - item) },
                            label = { Text(displayLabel, fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = chipAccentColor.copy(alpha = 0.15f),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = chipAccentColor.copy(alpha = 0.45f),
                                borderWidth = 1.dp,
                                enabled = true
                            )
                        )
                    }
                }
            }
        }
    }
}
