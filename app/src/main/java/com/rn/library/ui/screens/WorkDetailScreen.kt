package com.rn.library.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.rn.library.ui.components.ChipsFlowRow
import com.rn.library.ui.components.SunIcon
import com.rn.library.ui.components.bottomNavigationClearance
import androidx.compose.material3.*
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.rn.library.ui.components.SearchBar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.rn.library.R
import com.rn.library.data.*
import com.rn.library.data.readProgressUnits
import com.rn.library.data.watchedProgressUnits
import com.rn.library.util.LinkOpenHelper
import com.rn.library.ui.theme.*
import java.io.File
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rn.library.ui.AppSettings
import com.rn.library.ui.components.CoverCarousel
import com.rn.library.ui.components.ScrollIsolatedMultilineField
import com.rn.library.ui.components.filterUnitDecimalInput
import com.rn.library.ui.components.formatEditableUnitNumber
import com.rn.library.ui.components.parseUnitDecimalInput
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

// Модель данных для редактирования информации
private data class EditInfoItem(
    val key: String,
    val label: String,
    val value: String?,
    val type: EditInfoType,
    val originalValue: Any?
)

private enum class EditInfoType {
    STRING,
    INT,
    DATE,
    DATE_LIST
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkDetailScreen(
    work: Work,
    coverPaths: List<String> = work.allCoverPaths(),
    sessionCoverPath: String? = null,
    onSessionCoverPathChange: (String) -> Unit = {},
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Work) -> Unit,
    onCoverClick: () -> Unit = {},
    currentTheme: AppTheme = AppTheme.DARK,
    onThemeToggle: (() -> Unit)? = null,
    allWorks: List<Work> = emptyList(),
    onSelectWork: ((Work) -> Unit)? = null,
    onScrollStateChange: ((Boolean) -> Unit)? = null,
    onGenreClick: ((String) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mainBackgroundColor = MainBackgroundColor()
    val titleColorBetween = TitleColorBetween()
    val iconTextColor = IconTextColor()
    val searchBarColor = SearchBarColor()
    val fieldTextColor = if (currentTheme == AppTheme.DARK) Color.White else Color.Black

    val statusLabel = when (work.status) {
        WorkStatus.READ -> stringResource(R.string.read)
        WorkStatus.READING -> stringResource(R.string.reading)
        WorkStatus.WATCHING -> stringResource(R.string.watching)
        WorkStatus.WATCHED -> stringResource(R.string.watched)
        WorkStatus.IN_PLANS -> stringResource(R.string.in_plans)
        WorkStatus.ABANDONED -> stringResource(R.string.abandoned)
    }

    // Цвета для статусов (те же, что в ProfileScreen)
    val statusColor = when (work.status) {
        WorkStatus.IN_PLANS -> Color(0xFF8E6687)      // rgb(142, 102, 147)
        WorkStatus.ABANDONED -> Color(0xFFFF5F5A)     // rgb(255, 95, 90)
        WorkStatus.READING -> Color(0xFF7179A4)       // rgb(113, 121, 164)
        WorkStatus.WATCHING -> Color(0xFF7179A4)      // rgb(113, 121, 164)
        WorkStatus.READ -> Color(0xFF79C77C)          // rgb(121, 199, 124)
        WorkStatus.WATCHED -> Color(0xFF79C77C)        // rgb(121, 199, 124)
    }

    // Лейблы для SeriesType и MangaType
    val seriesTypeLabel = when (work.seriesType) {
        SeriesType.TV_SERIES -> stringResource(R.string.tv_series)
        SeriesType.FILM -> stringResource(R.string.film)
        SeriesType.CARTOON -> stringResource(R.string.cartoon)
        SeriesType.DRAMA -> stringResource(R.string.drama)
        null -> null
    }

    val mangaTypeLabel = when (work.mangaType) {
        MangaType.MANGA -> stringResource(R.string.manga)
        MangaType.MANHWA -> stringResource(R.string.manhwa)
        MangaType.MANHUA -> stringResource(R.string.manhua)
        null -> null
    }

    val scrollState = remember(work.id) { ScrollState(initial = 0) }
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember(work.id) { Animatable(0f) }
    val offsetY = remember(work.id) { Animatable(0f) }

    LaunchedEffect(work.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        scrollState.scrollTo(0)
    }
    val context = LocalContext.current
    // Используем LocalClipboardManager: он помечен deprecated, но стабильно работает в текущей версии Compose
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    var currentCoverPath by remember(work.id, coverPaths, sessionCoverPath) {
        mutableStateOf(
            sessionCoverPath?.takeIf { it in coverPaths }
                ?: coverPaths.firstOrNull()
        )
    }

    LaunchedEffect(sessionCoverPath, coverPaths) {
        val resolved = sessionCoverPath?.takeIf { it in coverPaths } ?: coverPaths.firstOrNull()
        if (resolved != null && resolved != currentCoverPath) {
            currentCoverPath = resolved
        }
    }

    val otherTitles = remember(work.otherTitle) {
        work.otherTitle
            ?.split(";", "\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: emptyList()
    }
    var otherTitlesExpanded by remember { mutableStateOf(false) }
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var seriesTypeMenuExpanded by remember { mutableStateOf(false) }
    var mangaTypeMenuExpanded by remember { mutableStateOf(false) }
    var detailSearchExpanded by remember { mutableStateOf(false) }
    var detailSearchQuery by remember { mutableStateOf("") }

    // Handle system back button for inner popups and menus
    androidx.activity.compose.BackHandler(enabled = detailSearchExpanded) {
        detailSearchExpanded = false
        detailSearchQuery = ""
    }
    androidx.activity.compose.BackHandler(enabled = otherTitlesExpanded) {
        otherTitlesExpanded = false
    }
    // Состояния для редактирования информации
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showEditLinksDialog by remember { mutableStateOf(false) }
    var linksEditText by remember { mutableStateOf("") }
    var editInfoParts by remember { mutableStateOf<List<EditInfoItem>>(emptyList()) }
    var temporaryWork by remember { mutableStateOf(work) }

    // Синхронизируем temporaryWork с work при изменении
    LaunchedEffect(work) {
        temporaryWork = work
    }

    androidx.activity.compose.BackHandler(enabled = statusMenuExpanded || seriesTypeMenuExpanded || mangaTypeMenuExpanded) {
        statusMenuExpanded = false
        seriesTypeMenuExpanded = false
        mangaTypeMenuExpanded = false
    }
    androidx.activity.compose.BackHandler(
        enabled = !detailSearchExpanded && !otherTitlesExpanded &&
            !statusMenuExpanded && !seriesTypeMenuExpanded && !mangaTypeMenuExpanded &&
            !showEditInfoDialog && !showEditLinksDialog
    ) {
        onBack()
    }

    // Pending changes to apply asynchronously
    var pendingStatusChange by remember { mutableStateOf<WorkStatus?>(null) }
    var pendingSeriesTypeChange by remember { mutableStateOf<SeriesType?>(null) }
    var pendingMangaTypeChange by remember { mutableStateOf<MangaType?>(null) }

    // Handle pending status changes asynchronously
    LaunchedEffect(pendingStatusChange) {
        pendingStatusChange?.let { newStatus ->
            if (work.status != newStatus) {
                val updatedWork = work.copy(status = newStatus)
                onSave(updatedWork)
            }
            pendingStatusChange = null
        }
    }

    // Handle pending series type changes asynchronously
    LaunchedEffect(pendingSeriesTypeChange) {
        pendingSeriesTypeChange?.let { newSeriesType ->
            if (work.seriesType != newSeriesType) {
                val updatedWork = work.copy(seriesType = newSeriesType)
                onSave(updatedWork)
            }
            pendingSeriesTypeChange = null
        }
    }

    // Handle pending manga type changes asynchronously
    LaunchedEffect(pendingMangaTypeChange) {
        pendingMangaTypeChange?.let { newMangaType ->
            if (work.mangaType != newMangaType) {
                val updatedWork = work.copy(mangaType = newMangaType)
                onSave(updatedWork)
            }
            pendingMangaTypeChange = null
        }
    }

    // Определяем доступные статусы в зависимости от типа произведения
    val availableStatuses = when (work.type) {
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
        else -> emptyList()
    }

    // Определяем доступные типы сериалов
    val availableSeriesTypes = listOf(
        null to "",
        SeriesType.TV_SERIES to stringResource(R.string.tv_series),
        SeriesType.FILM to stringResource(R.string.film),
        SeriesType.CARTOON to stringResource(R.string.cartoon),
        SeriesType.DRAMA to stringResource(R.string.drama)
    )

    // Определяем доступные типы манги
    val availableMangaTypes = listOf(
        null to "",
        MangaType.MANGA to stringResource(R.string.manga),
        MangaType.MANHWA to stringResource(R.string.manhwa),
        MangaType.MANHUA to stringResource(R.string.manhua)
    )

    // Функция подготовки данных для редактирования
    @Composable
    fun prepareEditInfo(work: Work): List<EditInfoItem> {
        val items = mutableListOf<EditInfoItem>()

        if (work.type == WorkType.SERIES) {
            items.add(
                EditInfoItem(
                    key = "seasons",
                    label = stringResource(R.string.seasons_view),
                    value = work.seasons?.toString().orEmpty(),
                    type = EditInfoType.INT,
                    originalValue = work.seasons
                )
            )
        }

        if (work.type == WorkType.ANIME || work.type == WorkType.SERIES) {
            items.add(
                EditInfoItem(
                    key = "episodes",
                    label = stringResource(R.string.series_view),
                    value = work.episodes?.let { formatDoubleForDisplay(it) }.orEmpty(),
                    type = EditInfoType.INT,
                    originalValue = work.episodes
                )
            )
        }

        if (work.type == WorkType.BOOK) {
            items.add(
                EditInfoItem(
                    key = "chapters",
                    label = stringResource(R.string.volumes_view),
                    value = work.chapters?.let { formatDoubleForDisplay(it) }.orEmpty(),
                    type = EditInfoType.INT,
                    originalValue = work.chapters
                )
            )
            items.add(
                EditInfoItem(
                    key = "bookChapters",
                    label = stringResource(R.string.chapters_view),
                    value = work.bookChapters?.let { formatDoubleForDisplay(it) }.orEmpty(),
                    type = EditInfoType.INT,
                    originalValue = work.bookChapters
                )
            )
        }

        if (work.type == WorkType.MANGA) {
            items.add(
                EditInfoItem(
                    key = "volumes",
                    label = stringResource(R.string.volumes_view),
                    value = work.volumes?.let { formatDoubleForDisplay(it) }.orEmpty(),
                    type = EditInfoType.INT,
                    originalValue = work.volumes
                )
            )
            items.add(
                EditInfoItem(
                    key = "chapters",
                    label = stringResource(R.string.chapters_view),
                    value = work.chapters?.let { formatDoubleForDisplay(it) }.orEmpty(),
                    type = EditInfoType.INT,
                    originalValue = work.chapters
                )
            )
        }

        val progressLabel = when (work.type) {
            WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.read)
            WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.watched)
        }
        items.add(
            EditInfoItem(
                key = "progress",
                label = progressLabel,
                value = work.progress?.let { formatDoubleForDisplay(it) }.orEmpty(),
                type = EditInfoType.INT,
                originalValue = work.progress
            )
        )

        items.add(
            EditInfoItem(
                key = "year",
                label = stringResource(R.string.year),
                value = (work.yearPeriod ?: work.year?.toString()).orEmpty(),
                type = EditInfoType.STRING,
                originalValue = work.yearPeriod ?: work.year
            )
        )


        items.add(
            EditInfoItem(
                key = "dateRead",
                label = when (work.type) {
                    WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.date_read_for_books)
                    WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.date_watched)
                    else -> stringResource(R.string.date_read)
                },
                // Как в AddWorkScreen: храним только цифры DDMMYYYY, чтобы применить визуальную маску
                value = work.dateRead?.split("-")?.let { parts ->
                    if (parts.size == 3) parts[2].padStart(2, '0') + parts[1].padStart(2, '0') + parts[0] else work.dateRead.filter { c -> c.isDigit() }
                }.orEmpty(),
                type = EditInfoType.DATE,
                originalValue = work.dateRead
            )
        )
        items.add(
            EditInfoItem(
                key = "rereadDates",
                label = when (work.type) {
                    WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.date_reread)
                    WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.date_rewatch)
                    else -> stringResource(R.string.repeats)
                },
                value = formatRereadDatesForDisplay(work.rereadDates),
                type = EditInfoType.DATE_LIST,
                originalValue = work.rereadDates
            )
        )

        items.add(
            EditInfoItem(
                key = "note",
                label = stringResource(R.string.note_label),
                value = work.note.orEmpty(),
                type = EditInfoType.STRING,
                originalValue = work.note
            )
        )

        return items
    }

    val preparedEditInfo = prepareEditInfo(work)

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset {
                IntOffset(
                    offsetX.value.roundToInt().coerceAtLeast(0),
                    offsetY.value.roundToInt().coerceAtLeast(0)
                )
            }
            .pointerInput(work.id) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    var swipeDirection = 0 // 0 = undefined, 1 = horizontal (right), 2 = vertical (down)
                    var totalX = 0f
                    var totalY = 0f
                    val dragStartTime = System.currentTimeMillis()
                    val activationSlop = touchSlop * 1.8f

                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val delta = change.positionChange()
                        totalX += delta.x
                        totalY += delta.y

                        if (swipeDirection == 0) {
                            val absX = kotlin.math.abs(totalX)
                            val absY = kotlin.math.abs(totalY)

                            val isHorizontalRight = totalX > activationSlop && totalX > absY * 1.6f
                            val isVerticalDown = scrollState.value == 0 && totalY > activationSlop && totalY > absX * 1.6f

                            if (isHorizontalRight) {
                                swipeDirection = 1
                                change.consume()
                                coroutineScope.launch {
                                    offsetX.snapTo((totalX - activationSlop).coerceIn(0f, size.width.toFloat()))
                                }
                            } else if (isVerticalDown) {
                                swipeDirection = 2
                                change.consume()
                                coroutineScope.launch {
                                    offsetY.snapTo((totalY - activationSlop).coerceIn(0f, size.height.toFloat()))
                                }
                            } else if (totalX < -touchSlop || (scrollState.value > 0 && absY > touchSlop) || totalY < -touchSlop) {
                                break
                            }
                        } else if (swipeDirection == 1) {
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((totalX - activationSlop).coerceIn(0f, size.width.toFloat()))
                            }
                        } else if (swipeDirection == 2) {
                            change.consume()
                            coroutineScope.launch {
                                offsetY.snapTo((totalY - activationSlop).coerceIn(0f, size.height.toFloat()))
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val elapsed = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1L)
                    val smoothDismissSpec = tween<Float>(
                        durationMillis = 320,
                        easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
                    )
                    val smoothCancelSpec = spring<Float>(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )

                    if (swipeDirection == 1) {
                        val velocityPxPerSec = (totalX / elapsed) * 1000f
                        val isFling = velocityPxPerSec > 1350f && totalX > size.width * 0.35f
                        val isDraggedFar = totalX > size.width * 0.52f || offsetX.value > size.width * 0.52f
                        coroutineScope.launch {
                            if (isDraggedFar || isFling) {
                                offsetX.animateTo(
                                    targetValue = size.width.toFloat(),
                                    animationSpec = smoothDismissSpec
                                )
                                onBack()
                            } else {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = smoothCancelSpec
                                )
                            }
                        }
                    } else if (swipeDirection == 2) {
                        val velocityPxPerSec = (totalY / elapsed) * 1000f
                        val isFling = velocityPxPerSec > 1350f && totalY > size.height * 0.28f
                        val isDraggedFar = totalY > size.height * 0.42f || offsetY.value > size.height * 0.42f
                        coroutineScope.launch {
                            if (isDraggedFar || isFling) {
                                offsetY.animateTo(
                                    targetValue = size.height.toFloat(),
                                    animationSpec = smoothDismissSpec
                                )
                                onBack()
                            } else {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = smoothCancelSpec
                                )
                            }
                        }
                    }
                }
            }
            .graphicsLayer {
                val isSwiping = offsetX.value > 0f || offsetY.value > 0f
                shadowElevation = if (isSwiping) 24.dp.toPx() else 0f
                shape = RoundedCornerShape(
                    topStart = if (offsetX.value > 0f || offsetY.value > 0f) 16.dp else 0.dp,
                    bottomStart = if (offsetX.value > 0f) 16.dp else 0.dp,
                    topEnd = if (offsetY.value > 0f) 16.dp else 0.dp
                )
                clip = isSwiping
            }
            .background(mainBackgroundColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {},
        contentAlignment = Alignment.TopCenter
    ) {
        val density = LocalDensity.current
        val bgCover = currentCoverPath ?: sessionCoverPath ?: work.displayCoverPath()
        if (!bgCover.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(439.dp)
                    .align(Alignment.TopCenter)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(bgCover.toCoverImageData())
                            .build()
                    ),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    alpha = 0.35f
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    mainBackgroundColor
                                ),
                                startY = 0f,
                                endY = with(density) { 439.dp.toPx() }
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Верхняя панель управления
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = iconTextColor
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (allWorks.isNotEmpty()) {
                    IconButton(onClick = { detailSearchExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = iconTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (onThemeToggle != null) {
                    SunIcon(
                        onClick = onThemeToggle,
                        color = iconTextColor,
                        iconSize = 20.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_work),
                        tint = iconTextColor
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_work),
                        tint = iconTextColor
                    )
                }
            }

            // Main scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Область обложки
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            ) {
                CoverCarousel(
                    coverPaths = coverPaths,
                    currentPath = currentCoverPath,
                    onCurrentPathChange = { path ->
                        currentCoverPath = path
                        onSessionCoverPathChange(path)
                    },
                    modifier = Modifier
                        .width(215.dp)
                        .height(340.dp)
                        .align(Alignment.Center),
                    cardShape = RoundedCornerShape(20.dp),
                    cardContainerColor = searchBarColor,
                    onCoverClick = onCoverClick
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = work.title,
                    color = titleColorBetween,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                // Other titles: show first one, tap to open bottom popup with all
                otherTitles.firstOrNull()?.let { firstOtherTitle ->
                    Text(
                        text = firstOtherTitle,
                        color = (if (currentTheme == AppTheme.DARK) Color(0xFFEDEDED) else Color(0xFF202020)).copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { otherTitlesExpanded = true }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Статус с цветом (только текст и рамка) - теперь кликабельный
                Box {
                    AssistChip(
                        onClick = { statusMenuExpanded = true },
                        enabled = true,
                        label = { Text(statusLabel) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.Transparent,
                            labelColor = statusColor,
                            disabledContainerColor = Color.Transparent,
                            disabledLabelColor = statusColor
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            borderColor = statusColor,
                            borderWidth = 2.dp,
                            enabled = true
                        )
                    )

                    DropdownMenu(
                        expanded = statusMenuExpanded,
                        onDismissRequest = { statusMenuExpanded = false },
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
                                availableStatuses.forEach { (status, label) ->
                                    val isSelected = work.status == status
                                    val itemStatusColor = when (status) {
                                        WorkStatus.IN_PLANS -> Color(0xFF8E6687)
                                        WorkStatus.ABANDONED -> Color(0xFFFF5F5A)
                                        WorkStatus.READING -> Color(0xFF7179A4)
                                        WorkStatus.WATCHING -> Color(0xFF7179A4)
                                        WorkStatus.READ -> Color(0xFF79C77C)
                                        WorkStatus.WATCHED -> Color(0xFF79C77C)
                                    }

                                    AssistChip(
                                        onClick = {
                                            statusMenuExpanded = false
                                            if (!isSelected) {
                                                pendingStatusChange = status
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                color = itemStatusColor,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.Transparent,
                                            labelColor = itemStatusColor
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(
                                            borderColor = if (isSelected) itemStatusColor else itemStatusColor.copy(alpha = 0.5f),
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
                // SeriesType для сериалов - теперь кликабельный
                if (work.type == WorkType.SERIES) {
                    Box {
                        AssistChip(
                            onClick = { seriesTypeMenuExpanded = true },
                            enabled = true,
                            label = {
                                Text(
                                    text = seriesTypeLabel ?: stringResource(R.string.tv_series_type),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = MaterialTheme.colorScheme.primary,
                                borderWidth = 2.dp,
                                enabled = true
                            )
                        )

                        DropdownMenu(
                            expanded = seriesTypeMenuExpanded,
                            onDismissRequest = { seriesTypeMenuExpanded = false },
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
                                    val itemColor = MaterialTheme.colorScheme.primary
                                    availableSeriesTypes.forEach { (type, label) ->
                                        if (label.isEmpty()) return@forEach // Пропускаем пустой вариант
                                        val isSelected = work.seriesType == type

                                        AssistChip(
                                            onClick = {
                                                seriesTypeMenuExpanded = false
                                                if (!isSelected) {
                                                    pendingSeriesTypeChange = type
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color.Transparent,
                                                labelColor = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = AssistChipDefaults.assistChipBorder(
                                                borderColor = if (isSelected) itemColor else itemColor.copy(alpha = 0.5f),
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
                // MangaType для манги - теперь кликабельный
                if (work.type == WorkType.MANGA) {
                    Box {
                        AssistChip(
                            onClick = { mangaTypeMenuExpanded = true },
                            enabled = true,
                            label = {
                                Text(
                                    text = mangaTypeLabel ?: stringResource(R.string.manga_type),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                borderWidth = 2.dp,
                                enabled = true
                            )
                        )

                        DropdownMenu(
                            expanded = mangaTypeMenuExpanded,
                            onDismissRequest = { mangaTypeMenuExpanded = false },
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
                                    val itemColor = MaterialTheme.colorScheme.primary
                                    availableMangaTypes.forEach { (type, label) ->
                                        if (label.isEmpty()) return@forEach // Пропускаем пустой вариант
                                        val isSelected = work.mangaType == type

                                        AssistChip(
                                            onClick = {
                                                mangaTypeMenuExpanded = false
                                                if (!isSelected) {
                                                    pendingMangaTypeChange = type
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color.Transparent,
                                                labelColor = if (isSelected) itemColor else MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = AssistChipDefaults.assistChipBorder(
                                                borderColor = if (isSelected) itemColor else itemColor.copy(alpha = 0.5f),
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

            val cleanedPrimaryLink = run {
                val raw = work.link?.takeIf { it.isNotBlank() }?.trim()
                    ?: work.link2?.takeIf { it.isNotBlank() }?.trim()
                    ?: work.links.firstOrNull { it.isNotBlank() }?.trim()
                raw?.replace("\\:", ":")?.replace("\\/", "/")
            }
            val hasPrimaryLink = !cleanedPrimaryLink.isNullOrBlank()

            fun openPrimaryLink() {
                val uri = cleanedPrimaryLink ?: return
                LinkOpenHelper.openInExternalViewer(context, uri)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPrimaryLink) {
                    val actionLabel = when (work.type) {
                        WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.link_action_read)
                        WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.link_action_watch)
                    }
                    val fieldHighlight = FieldHighlightColor()
                    Button(
                        onClick = { openPrimaryLink() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppButtonColor()
                        )
                    ) {
                        Text(actionLabel)
                    }
                }
                val buttonColor = AppButtonColor()
                OutlinedButton(
                    onClick = {
                        editInfoParts = preparedEditInfo
                        temporaryWork = work.copy()
                        showEditInfoDialog = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = buttonColor
                    ),
                    border = BorderStroke(1.dp, buttonColor)
                ) {
                    Text(stringResource(R.string.edit_info_button))
                }
            }

            // Информационное поле: прочитано/просмотрено → серии/главы → тома/сезоны → сезон аниме → год → страна → дата
            val infoParts = mutableListOf<String>()
            fun formatDouble(value: Double): String {
                return if (value % 1.0 == 0.0) {
                    value.toInt().toString()
                } else {
                    value.toString().trimEnd('0').trimEnd('.')
                }
            }

            work.progress?.let { p ->
                val formattedProgress = formatDouble(p)
                when (work.type) {
                    WorkType.ANIME, WorkType.SERIES ->
                        infoParts.add("${stringResource(R.string.progress_watched_episodes)}: $formattedProgress")
                    WorkType.BOOK, WorkType.MANGA ->
                        infoParts.add("${stringResource(R.string.progress_read_chapters)}: $formattedProgress")
                }
            }
            when (work.type) {
                WorkType.SERIES -> {
                    work.episodes?.let {
                        val formatted = formatDouble(it)
                        infoParts.add("${stringResource(R.string.series_view)}: $formatted")
                    }
                    work.seasons?.let { infoParts.add("${stringResource(R.string.seasons_view)}: $it") }
                }
                WorkType.ANIME -> {
                    work.episodes?.let {
                        val formatted = formatDouble(it)
                        infoParts.add("${stringResource(R.string.series_view)}: $formatted")
                    }
                }
                WorkType.BOOK -> {
                    work.bookChapters?.let {
                        infoParts.add("${stringResource(R.string.chapters_view)}: ${formatDouble(it)}")
                    }
                    work.chapters?.let {
                        val formatted = formatDouble(it)
                        infoParts.add("${stringResource(R.string.volumes_view)}: $formatted")
                    }
                }
                WorkType.MANGA -> {
                    work.chapters?.let {
                        val formatted = formatDouble(it)
                        infoParts.add("${stringResource(R.string.chapters_view)}: $formatted")
                    }
                    work.volumes?.let {
                        infoParts.add("${stringResource(R.string.volumes_view)}: ${formatDouble(it)}")
                    }
                }
            }
            work.author?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }?.let { infoParts.add("${stringResource(R.string.author)}: $it") }
            if (work.type == WorkType.ANIME) {
                work.animeSeason?.let { season ->
                    val seasonLabel = when (season) {
                        AnimeSeason.SPRING -> stringResource(R.string.spring)
                        AnimeSeason.SUMMER -> stringResource(R.string.summer)
                        AnimeSeason.FALL -> stringResource(R.string.fall)
                        AnimeSeason.WINTER -> stringResource(R.string.winter)
                    }
                    infoParts.add("${stringResource(R.string.anime_season)}: $seasonLabel")
                }
            }
            (work.yearPeriod ?: work.year?.toString())?.let { infoParts.add("${stringResource(R.string.year)}: $it") }
            work.country?.let { infoParts.add("${stringResource(R.string.country)}: $it") }
            work.dateRead?.let { dateStr ->
                val dateLabel = when (work.type) {
                    WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.date_read_for_books)
                    WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.date_watched)
                    else -> stringResource(R.string.date_read)
                }
                // Конвертируем дату из YYYY-MM-DD в DD.MM.YYYY для отображения
                val formattedDate = try {
                    val parts = dateStr.split("-")
                    if (parts.size == 3) {
                        "${parts[2]}.${parts[1]}.${parts[0]}"
                    } else {
                        dateStr
                    }
                } catch (e: Exception) {
                    dateStr
                }
                infoParts.add("$dateLabel: $formattedDate")
            }
            if (work.rereadDates.isNotEmpty()) {
                val rereads = work.rereadDates.joinToString(", ") { date ->
                    val parts = date.split("-")
                    if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else date
                }
                val rereadLabel = when (work.type) {
                    WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.date_reread)
                    WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.date_rewatch)
                    else -> stringResource(R.string.repeats)
                }
                infoParts.add("$rereadLabel: $rereads")
            }
            if (work.readingPeriods.isNotEmpty()) {
                val periods = work.readingPeriods.joinToString("; ") { period ->
                    val end = period.endDate ?: "..."
                    "${period.startDate} - $end"
                }
                val periodsLabel = stringResource(R.string.reading_periods_label)
                infoParts.add("$periodsLabel: $periods")
            }
            if (infoParts.isNotEmpty()) {
                Surface(
                    color = mainBackgroundColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        infoParts.forEach { info ->
                            Text(
                                text = info,
                                color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Description block – фон и текст зависят от темы (без заголовка "Описание")
            Surface(
                modifier = Modifier.padding(top = if (infoParts.isNotEmpty()) 1.dp else 0.dp),
                color = mainBackgroundColor,
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = if (work.description.isBlank()) stringResource(R.string.description_empty) else work.description,
                        color = if (currentTheme == AppTheme.DARK) Color(0xFFEDEDED) else Color(0xFF202020),
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Progress bar for reading / watching (hidden for Abandoned or when no totals)
            val totalUnits = when (work.type) {
                WorkType.BOOK -> work.bookChapters?.toDouble()
                WorkType.MANGA -> work.chapters
                WorkType.ANIME, WorkType.SERIES -> work.episodes
            }
            val currentProgress = when (work.type) {
                WorkType.BOOK, WorkType.MANGA -> readProgressUnits(work)
                WorkType.ANIME, WorkType.SERIES -> watchedProgressUnits(work)
            }.takeIf { it > 0.0 }

            if (totalUnits != null && totalUnits > 0 && currentProgress != null) {
                val clampedProgress = currentProgress.coerceIn(0.0, totalUnits)
                val progressFraction = clampedProgress.toFloat() / totalUnits.toFloat()
                val progressPercent = ((progressFraction * 100f).toInt()).coerceIn(0, 100)

                Spacer(modifier = Modifier.height(4.dp))

                // Custom progress bar to avoid visual split into two segments
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(iconTextColor.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .background(statusColor)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val progressPrefix = when (work.type) {
                    WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.read)
                    WorkType.ANIME, WorkType.SERIES -> stringResource(R.string.watched)
                }
                // Format values for display (remove trailing zeros)
                fun formatDouble(value: Double): String {
                    return if (value % 1.0 == 0.0) {
                        value.toInt().toString()
                    } else {
                        value.toString().trimEnd('0').trimEnd('.')
                    }
                }
                val formattedProgress = formatDouble(clampedProgress)
                val formattedTotal = formatDouble(totalUnits)
                val countText = "$progressPrefix $formattedProgress / $formattedTotal"

                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "$progressPercent%",
                        color = iconTextColor.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Text(
                        text = countText,
                        color = iconTextColor.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }

            // Unified Links Block (All links in one card - now above genres and tags)
            val allLinks = remember(work.link, work.link2, work.links) {
                work.allLinks().map { it.replace("\\:", ":").replace("\\/", "/") }
            }

            Surface(
                color = mainBackgroundColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.links),
                            color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                linksEditText = formatLinksForDisplay(work.link, work.link2, work.links)
                                showEditLinksDialog = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_work),
                                tint = iconTextColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (allLinks.isNotEmpty()) {
                        allLinks.forEachIndexed { index, linkUrl ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = iconTextColor.copy(alpha = 0.15f),
                                    thickness = 1.dp
                                )
                            }
                            val itemLabel = if (allLinks.size > 1) {
                                "${stringResource(R.string.link1).replace(" 1", "")} ${index + 1}"
                            } else null

                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (itemLabel != null) {
                                    Text(
                                        text = itemLabel,
                                        color = (if (currentTheme == AppTheme.DARK) Color.White else Color.Black).copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = linkUrl,
                                    color = Color(0xFF2196F3),
                                    fontSize = 14.sp,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable(
                                        indication = null,
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                    ) {
                                        LinkOpenHelper.openInExternalViewer(context, linkUrl)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.links_empty),
                            color = (if (currentTheme == AppTheme.DARK) Color.White else Color.Black).copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Genres and Tags Block (Below Links)
            if (work.genres.isNotEmpty() || work.tags.isNotEmpty()) {
                Surface(
                    color = mainBackgroundColor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (work.genres.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.genres),
                                    color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    ChipsFlowRow(
                                        horizontalSpacing = 4.dp,
                                        verticalSpacing = 4.dp
                                    ) {
                                        work.genres.forEach { genre ->
                                            AssistChip(
                                                onClick = { onGenreClick?.invoke(genre) },
                                                label = {
                                                    Text(
                                                        text = genre,
                                                        fontSize = 13.sp,
                                                        color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ),
                                                border = AssistChipDefaults.assistChipBorder(
                                                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                    borderWidth = 1.dp,
                                                    enabled = true
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (work.genres.isNotEmpty() && work.tags.isNotEmpty()) {
                            HorizontalDivider(
                                color = iconTextColor.copy(alpha = 0.15f),
                                thickness = 1.dp
                            )
                        }

                        if (work.tags.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(R.string.tags),
                                    color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    ChipsFlowRow(
                                        horizontalSpacing = 4.dp,
                                        verticalSpacing = 4.dp
                                    ) {
                                        work.tags.forEach { tag ->
                                            val displayTag = if (tag.startsWith("#")) tag else "#$tag"
                                            AssistChip(
                                                onClick = { onTagClick?.invoke(displayTag) },
                                                label = {
                                                    Text(
                                                        text = displayTag,
                                                        fontSize = 13.sp,
                                                        color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = statusColor.copy(alpha = 0.12f)
                                                ),
                                                border = AssistChipDefaults.assistChipBorder(
                                                    borderColor = statusColor.copy(alpha = 0.35f),
                                                    borderWidth = 1.dp,
                                                    enabled = true
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Note (at the very bottom)
            work.note?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    color = mainBackgroundColor,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.note_label),
                            color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = note,
                            color = iconTextColor.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom padding so all content can scroll cleanly
            Spacer(modifier = Modifier.height(115.dp))
            }
        }

        // Полноэкранный оверлей поиска с затемненным фоном
        if (detailSearchExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (currentTheme == AppTheme.DARK)
                            Color.Black.copy(alpha = 0.92f)
                        else
                            Color(0xEE181818)
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // Поглощаем клики внутри оверлея
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(40.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                detailSearchExpanded = false
                                detailSearchQuery = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = if (currentTheme == AppTheme.DARK) iconTextColor else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        SearchBar(
                            modifier = Modifier.weight(1f),
                            query = detailSearchQuery,
                            onSearchQueryChange = { detailSearchQuery = it },
                            currentTheme = currentTheme,
                            autoFocus = true
                        )
                    }

                    val dq = detailSearchQuery.trim()
                    val filtered = allWorks.filter {
                        dq.isBlank() ||
                            it.title.contains(dq, ignoreCase = true) ||
                            it.otherTitle?.contains(dq, ignoreCase = true) == true ||
                            it.author?.contains(dq, ignoreCase = true) == true
                    }.sortedBy { it.title.lowercase() }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { item ->
                            val tabLabel = when (item.type) {
                                WorkType.BOOK -> stringResource(R.string.tab_books)
                                WorkType.ANIME -> stringResource(R.string.tab_anime)
                                WorkType.MANGA -> stringResource(R.string.tab_manga)
                                WorkType.SERIES -> stringResource(R.string.tab_series)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectWork?.invoke(item)
                                        detailSearchExpanded = false
                                        detailSearchQuery = ""
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        (item.displayCoverPath())?.toCoverImageData()
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (currentTheme == AppTheme.DARK) titleColorBetween else Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = tabLabel,
                                        color = (if (currentTheme == AppTheme.DARK) iconTextColor else Color.White).copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(bottomNavigationClearance() + 16.dp))
                        }
                    }
                }
            }
        }

        // Bottom popup with names (like sheet), animated over content and overlapping bottom navigation bar
        if (otherTitlesExpanded && otherTitles.isNotEmpty()) {
            Dialog(
                onDismissRequest = { otherTitlesExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            otherTitlesExpanded = false
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp, start = 12.dp, end = 12.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) {
                                // Поглощаем клик внутри Popup, чтобы не срабатывало закрытие
                            },
                        color = mainBackgroundColor,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Название
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.title),
                                    color = titleColorBetween,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(work.title))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_title_content_desc),
                                        tint = iconTextColor
                                    )
                                }
                            }
                            Text(
                                text = work.title,
                                color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                                fontSize = 14.sp
                            )

                            // Разделитель
                            HorizontalDivider(color = iconTextColor.copy(alpha = 0.2f))

                            // Альтернативные названия
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.alternative_titles),
                                    color = titleColorBetween,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(otherTitles.joinToString("\n")))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_alternative_titles_content_desc),
                                        tint = iconTextColor
                                    )
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                otherTitles.forEach { t ->
                                    Text(
                                        text = t,
                                        color = if (currentTheme == AppTheme.DARK) Color.White else Color.Black,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Диалог редактирования информации
        if (showEditInfoDialog) {
            EditInfoDialog(
                work = temporaryWork,
                editInfoParts = editInfoParts,
                onDismiss = { showEditInfoDialog = false },
                onSave = { updatedWork ->
                    onSave(updatedWork)
                    showEditInfoDialog = false
                },
                currentTheme = currentTheme
            )
        }

        if (showEditLinksDialog) {
            EditLinksDialog(
                initialLinksText = linksEditText,
                onDismiss = { showEditLinksDialog = false },
                onSave = { newText ->
                    val allParsedLinks = parseLinksList(newText)
                    val updatedWork = work.copy(
                        link = allParsedLinks.getOrNull(0),
                        link2 = allParsedLinks.getOrNull(1),
                        links = allParsedLinks,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updatedWork)
                    showEditLinksDialog = false
                },
                currentTheme = currentTheme
            )
        }

        // Индикатор свайпа для возврата назад
        val dragOffset = offsetX.value
        if (dragOffset > 8f) {
            val isReadyToDismiss = dragOffset > 100f
            val indicatorAlpha = (dragOffset / 50f).coerceIn(0f, 1f)
            val indicatorScale = 0.6f + 0.4f * (dragOffset / 120f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 16.dp)
                    .graphicsLayer {
                        alpha = indicatorAlpha
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                    }
                    .clip(CircleShape)
                    .background(
                        if (isReadyToDismiss) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    )
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = if (isReadyToDismiss) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Преобразует дату из формата YYYY-MM-DD в ДД.ММ.ГГГГ
 * Если дата уже в формате ДД.ММ.ГГГГ, возвращает её без изменений
 */
private fun convertDateFormat(date: String): String {
    return try {
        // Проверяем, является ли дата в формате YYYY-MM-DD
        if (date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = date.split("-")
            if (parts.size == 3) {
                "${parts[2]}.${parts[1]}.${parts[0]}"
            } else {
                date
            }
        } else {
            // Если уже в формате ДД.ММ.ГГГГ или другом, возвращаем как есть
            date
        }
    } catch (e: Exception) {
        date
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    iconTextColor: Color,
    titleColorBetween: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = iconTextColor.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = titleColorBetween,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditInfoDialog(
    work: Work,
    editInfoParts: List<EditInfoItem>,
    onDismiss: () -> Unit,
    onSave: (Work) -> Unit,
    currentTheme: AppTheme
) {
    val mainBackgroundColor = MainBackgroundColor()
    val titleColorBetween = TitleColorBetween()
    val iconTextColor = IconTextColor()
    val searchBarColor = SearchBarColor()
    // Цвет для текста полей и лейблов: белый в тёмной теме, чёрный в светлой
    val fieldTextColor = if (currentTheme == AppTheme.DARK) Color.White else Color.Black
    val fieldHighlight = FieldHighlightColor()

    var tempWork by remember { mutableStateOf(work) }
    var tempValues by remember { mutableStateOf(editInfoParts.toMutableList()) }
    var unitProgressEdits by remember(work.unitProgress) {
        mutableStateOf(work.unitProgress.toMutableList())
    }
    var selectedUnitIndex by remember(work.unitProgress, work.activeUnitIndex) {
        val savedIndex = work.activeUnitIndex
        val fallback = work.unitProgress.indexOfLast { it.completed > 0.0 }.takeIf { it >= 0 } ?: 0
        val initial = savedIndex?.coerceIn(work.unitProgress.indices) ?: fallback
        mutableIntStateOf(initial)
    }
    var syncApplyUnitProgress by remember {
        mutableStateOf<() -> UnitProgressEditState>({
            UnitProgressEditState(unitProgressEdits, selectedUnitIndex)
        })
    }
    val context = LocalContext.current
    // Шаг инкремента из настроек (минимум 1)
    val incrementStep = remember { AppSettings.getIncrementStep(context).coerceAtLeast(1) }
    val unitPrefixLabel = when (tempWork.type) {
        WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.volume_unit_prefix)
        WorkType.SERIES, WorkType.ANIME -> stringResource(R.string.season_unit_prefix)
        else -> ""
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val editDialogScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !isLandscape)
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (isLandscape) {
                        Modifier
                            .fillMaxWidth(0.95f)
                            .widthIn(max = 860.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    }
                ),
            shape = RoundedCornerShape(16.dp),
            color = mainBackgroundColor
        ) {
            Column(
                modifier = Modifier
                    .padding(if (isLandscape) 10.dp else 16.dp)
                    .verticalScroll(editDialogScrollState),
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 16.dp)
            ) {
                @Composable
                fun RenderEditFieldItem(index: Int, item: EditInfoItem) {
                    if (item.key == "progress" && unitProgressEdits.isNotEmpty()) {
                        EditUnitProgressFieldRow(
                            unitPrefixLabel = unitPrefixLabel,
                            progressLabel = item.label,
                            unitProgressList = unitProgressEdits,
                            selectedUnitIndex = selectedUnitIndex.coerceIn(unitProgressEdits.indices),
                            onSelectedUnitIndexChange = { selectedUnitIndex = it },
                            onUnitProgressListChange = { unitProgressEdits = it.toMutableList() },
                            maxUnitNumber = editDialogMaxUnitNumber(tempWork, tempValues),
                            incrementStep = incrementStep,
                            onRegisterSyncApplyUnitProgress = { syncApplyUnitProgress = it },
                            searchBarColor = searchBarColor,
                            fieldTextColor = fieldTextColor,
                            iconTextColor = iconTextColor,
                        )
                        return
                    }

                    var textValue by remember(item.value) {
                        mutableStateOf(item.value ?: "")
                    }

                    if (item.type == EditInfoType.DATE_LIST) {
                        ScrollIsolatedMultilineField(
                            value = textValue,
                            onValueChange = { newValue ->
                                val filtered = filterRereadDatesInput(newValue)
                                textValue = filtered
                                tempValues[index] = item.copy(value = filtered)
                            },
                            label = { Text(item.label) },
                            placeholder = { Text(stringResource(R.string.reread_dates_placeholder)) },
                            minLines = 1,
                            maxLines = if (isLandscape) 2 else 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = searchBarColor,
                                unfocusedContainerColor = searchBarColor,
                                focusedIndicatorColor = fieldHighlight,
                                unfocusedIndicatorColor = iconTextColor.copy(alpha = 0.3f),
                                focusedTextColor = fieldTextColor,
                                unfocusedTextColor = fieldTextColor,
                                focusedLabelColor = fieldHighlight,
                                unfocusedLabelColor = fieldTextColor,
                                cursorColor = fieldHighlight
                            ),
                            parentScrollState = editDialogScrollState,
                        )
                    } else if (item.type == EditInfoType.DATE) {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { newValue ->
                                val digitsOnly = newValue.filter { it.isDigit() }.take(8)
                                textValue = digitsOnly
                                tempValues[index] = item.copy(value = digitsOnly)
                            },
                            label = { Text(item.label) },
                            placeholder = { Text(stringResource(R.string.date_placeholder)) },
                            visualTransformation = DateVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = searchBarColor,
                                unfocusedContainerColor = searchBarColor,
                                focusedIndicatorColor = fieldHighlight,
                                unfocusedIndicatorColor = iconTextColor.copy(alpha = 0.3f),
                                focusedTextColor = fieldTextColor,
                                unfocusedTextColor = fieldTextColor,
                                focusedLabelColor = fieldHighlight,
                                unfocusedLabelColor = fieldTextColor,
                                cursorColor = fieldHighlight
                            )
                        )
                    } else {
                        val isIncrementField = item.type == EditInfoType.INT &&
                                item.key in setOf(
                            "volumes",
                            "chapters",
                            "bookChapters",
                            "episodes",
                            "seasons",
                            "progress"
                        )

                        if (isIncrementField) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = textValue,
                                    onValueChange = { newValue ->
                                        val filtered = newValue.filter { it.isDigit() || it == '.' }
                                        val parts = filtered.split('.')
                                        val validValue = if (parts.size > 2) {
                                            parts[0] + "." + parts.drop(1).joinToString("")
                                        } else {
                                            filtered
                                        }
                                        textValue = validValue
                                        tempValues[index] = item.copy(value = validValue)
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = searchBarColor,
                                        unfocusedContainerColor = searchBarColor,
                                        focusedIndicatorColor = fieldHighlight,
                                        unfocusedIndicatorColor = iconTextColor.copy(alpha = 0.3f),
                                        focusedTextColor = fieldTextColor,
                                        unfocusedTextColor = fieldTextColor,
                                        focusedLabelColor = fieldHighlight,
                                        unfocusedLabelColor = fieldTextColor,
                                        cursorColor = fieldHighlight
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        val current = textValue.toDoubleOrNull() ?: 0.0
                                        val newValue = (current + incrementStep).coerceAtLeast(0.0)
                                        val asString = if (newValue % 1.0 == 0.0) {
                                            newValue.toInt().toString()
                                        } else {
                                            newValue.toString().trimEnd('0').trimEnd('.')
                                        }
                                        textValue = asString
                                        tempValues[index] = item.copy(value = asString)
                                    },
                                    modifier = Modifier.size(if (isLandscape) 32.dp else 36.dp)
                                ) {
                                    Text(
                                        text = "▲",
                                        color = fieldTextColor,
                                        fontSize = if (isLandscape) 18.sp else 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = textValue,
                                onValueChange = { newValue ->
                                    val filteredValue = when {
                                        item.key == "year" -> {
                                            val normalized = newValue.trimStart()
                                            val singleYearPattern = Regex("^\\d{0,4}$")
                                            val yearPeriodPattern = Regex("^\\d{4}\\s?-\\s?\\d{0,4}$")
                                            if (
                                                normalized.isEmpty() ||
                                                singleYearPattern.matches(normalized) ||
                                                yearPeriodPattern.matches(normalized)
                                            ) normalized else textValue
                                        }
                                        item.type == EditInfoType.INT -> newValue.filter { it.isDigit() }
                                        else -> newValue
                                    }
                                    textValue = filteredValue
                                    tempValues[index] = item.copy(value = filteredValue)
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = item.type != EditInfoType.STRING || item.key == "year",
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = searchBarColor,
                                    unfocusedContainerColor = searchBarColor,
                                    focusedIndicatorColor = fieldHighlight,
                                    unfocusedIndicatorColor = iconTextColor.copy(alpha = 0.3f),
                                    focusedTextColor = fieldTextColor,
                                    unfocusedTextColor = fieldTextColor,
                                    focusedLabelColor = fieldHighlight,
                                    unfocusedLabelColor = fieldTextColor,
                                    cursorColor = fieldHighlight
                                )
                            )
                        }
                    }
                }

                if (!isLandscape) {
                    tempValues.forEachIndexed { index, item ->
                        RenderEditFieldItem(index, item)
                    }
                } else {
                    val rowGroups = remember(tempValues) {
                        val groups = mutableListOf<List<Pair<Int, EditInfoItem>>>()
                        var currentPair = mutableListOf<Pair<Int, EditInfoItem>>()

                        tempValues.forEachIndexed { index, item ->
                            val isFullWidth = (item.key == "progress" && unitProgressEdits.isNotEmpty()) ||
                                    item.key == "note" || item.key == "description"

                            if (isFullWidth) {
                                if (currentPair.isNotEmpty()) {
                                    groups.add(currentPair)
                                    currentPair = mutableListOf()
                                }
                                groups.add(listOf(Pair(index, item)))
                            } else {
                                currentPair.add(Pair(index, item))
                                if (currentPair.size == 2) {
                                    groups.add(currentPair)
                                    currentPair = mutableListOf()
                                }
                            }
                        }
                        if (currentPair.isNotEmpty()) {
                            groups.add(currentPair)
                        }
                        groups
                    }

                    rowGroups.forEach { group ->
                        if (group.size == 1) {
                            RenderEditFieldItem(group[0].first, group[0].second)
                        } else if (group.size == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    RenderEditFieldItem(group[0].first, group[0].second)
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    RenderEditFieldItem(group[1].first, group[1].second)
                                }
                            }
                        }
                    }
                }

                // Кнопки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isLandscape) 2.dp else 0.dp),
                    horizontalArrangement = if (isLandscape) Arrangement.Center else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = if (isLandscape) Modifier.height(44.dp).padding(horizontal = 16.dp) else Modifier
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = iconTextColor,
                            fontSize = if (isLandscape) 16.sp else 14.sp,
                            fontWeight = if (isLandscape) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isLandscape) 24.dp else 8.dp))

                    Button(
                        onClick = {
                            val unitEditState = if (unitProgressEdits.isNotEmpty()) {
                                syncApplyUnitProgress().also {
                                    unitProgressEdits = it.units.toMutableList()
                                    selectedUnitIndex = it.activeIndex
                                }
                            } else {
                                UnitProgressEditState(unitProgressEdits, selectedUnitIndex)
                            }
                            val unitProgressToSave = unitEditState.units
                            val activeUnitIndexToSave = unitEditState.activeIndex

                            // Обновляем временный объект Work
                            var updatedWork = tempWork

                            tempValues.forEach { item ->
                                updatedWork = when (item.key) {
                                    "seasons" -> updatedWork.copy(seasons = item.value?.toIntOrNull())
                                    "episodes" -> updatedWork.copy(episodes = item.value?.toDoubleOrNull())
                                    "chapters" -> {
                                        if (tempWork.type == WorkType.BOOK) {
                                            updatedWork.copy(chapters = item.value?.toDoubleOrNull())
                                        } else {
                                            updatedWork.copy(chapters = item.value?.toDoubleOrNull())
                                        }
                                    }
                                    "volumes" -> updatedWork.copy(volumes = item.value?.toDoubleOrNull())
                                    "bookChapters" -> updatedWork.copy(bookChapters = item.value?.toDoubleOrNull())
                                    "year" -> {
                                        val raw = item.value?.trim().orEmpty()
                                        val singleYearPattern = Regex("^\\d{1,4}$")
                                        val yearPeriodPattern = Regex("^\\d{4}\\s*-\\s*\\d{1,4}$")
                                        when {
                                            raw.isEmpty() -> updatedWork.copy(year = null, yearPeriod = null)
                                            singleYearPattern.matches(raw) ->
                                                updatedWork.copy(year = raw.toIntOrNull(), yearPeriod = null)
                                            yearPeriodPattern.matches(raw) ->
                                                updatedWork.copy(
                                                    year = null,
                                                    yearPeriod = raw.replace(Regex("\\s*-\\s*"), " - ")
                                                )
                                            else -> updatedWork
                                        }
                                    }
                                    "progress" -> {
                                        if (unitProgressToSave.isNotEmpty()) {
                                            updatedWork.copy(
                                                unitProgress = unitProgressToSave,
                                                progress = unitProgressToSave.sumOf { it.completed },
                                                activeUnitIndex = activeUnitIndexToSave,
                                            )
                                        } else {
                                            updatedWork.copy(
                                                progress = item.value?.toDoubleOrNull(),
                                                activeUnitIndex = null,
                                            )
                                        }
                                    }
                                    "country" -> updatedWork.copy(country = item.value?.takeIf { it.isNotBlank() })
                                    "dateRead" -> {
                                        val digits = item.value
                                            ?.filter { it.isDigit() }
                                            .orEmpty()
                                        val newDate = if (digits.length == 8) {
                                            // DDMMYYYY -> YYYY-MM-DD
                                            val day = digits.substring(0, 2)
                                            val month = digits.substring(2, 4)
                                            val year = digits.substring(4, 8)
                                            "$year-$month-$day"
                                        } else if (item.value?.trim()?.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")) == true) {
                                            item.value!!.trim()
                                        } else if (digits.isEmpty() && item.originalValue != null) {
                                            null
                                        } else {
                                            item.originalValue as? String
                                        }
                                        updatedWork.copy(dateRead = newDate)
                                    }
                                    "rereadDates" -> {
                                        updatedWork.copy(
                                            rereadDates = parseRereadDatesForSave(item.value.orEmpty())
                                        )
                                    }
                                    "author" -> updatedWork.copy(author = item.value?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) })
                                    "note" -> updatedWork.copy(note = item.value?.takeIf { it.isNotBlank() })
                                    else -> updatedWork
                                }
                            }

                            // При «Прочитано»/«Просмотрено» синхронизируем progress с актуальным числом глав/серий,
                            // чтобы статистика и активность учитывали изменения полей «Главы»/«Серии».
                            updatedWork = when {
                                updatedWork.status == WorkStatus.READ &&
                                    updatedWork.type in setOf(WorkType.BOOK, WorkType.MANGA) -> {
                                    val total = fullReadChapterUnits(updatedWork)
                                    if (total > 0.0) updatedWork.copy(progress = total) else updatedWork
                                }
                                updatedWork.status == WorkStatus.WATCHED &&
                                    updatedWork.type in setOf(WorkType.ANIME, WorkType.SERIES) -> {
                                    val total = fullWatchedEpisodeUnits(updatedWork)
                                    if (total > 0.0) updatedWork.copy(progress = total) else updatedWork
                                }
                                else -> updatedWork
                            }

                            onSave(updatedWork)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppButtonColor()
                        ),
                        modifier = if (isLandscape) Modifier.height(44.dp).padding(horizontal = 24.dp) else Modifier
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            fontSize = if (isLandscape) 16.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditLinksDialog(
    initialLinksText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    currentTheme: AppTheme
) {
    var text by remember { mutableStateOf(initialLinksText) }
    val mainBackgroundColor = MainBackgroundColor()
    val fieldTextColor = if (currentTheme == AppTheme.DARK) Color.White else Color.Black
    val iconTextColor = IconTextColor()
    val fieldHighlight = FieldHighlightColor()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = mainBackgroundColor,
        shape = RoundedCornerShape(16.dp),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.links)) },
                    placeholder = { Text(stringResource(R.string.links_placeholder)) },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = fieldTextColor,
                        unfocusedTextColor = fieldTextColor,
                        focusedBorderColor = fieldHighlight,
                        unfocusedBorderColor = iconTextColor.copy(alpha = 0.3f),
                        focusedLabelColor = fieldHighlight,
                        unfocusedLabelColor = iconTextColor.copy(alpha = 0.7f),
                        cursorColor = fieldHighlight
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppButtonColor()
                )
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = iconTextColor
                )
            }
        }
    )
}

private data class UnitProgressEditState(
    val units: List<UnitProgress>,
    val activeIndex: Int,
)

/** Максимальный номер тома/сезона из поля «Тома» / «Сезоны» в диалоге редактирования. */
private fun editDialogMaxUnitNumber(work: Work, tempValues: List<EditInfoItem>): Int? {
    val raw = when (work.type) {
        WorkType.MANGA -> tempValues.find { it.key == "volumes" }?.value?.toDoubleOrNull()
        WorkType.BOOK -> tempValues.find { it.key == "chapters" }?.value?.toDoubleOrNull()
        WorkType.SERIES -> tempValues.find { it.key == "seasons" }?.value?.toIntOrNull()?.toDouble()
        WorkType.ANIME -> work.seasons?.toDouble()
        else -> null
    }
    return raw?.toInt()?.takeIf { it > 0 }
}

@Composable
private fun EditUnitProgressFieldRow(
    unitPrefixLabel: String,
    progressLabel: String,
    unitProgressList: List<UnitProgress>,
    selectedUnitIndex: Int,
    onSelectedUnitIndexChange: (Int) -> Unit,
    onUnitProgressListChange: (List<UnitProgress>) -> Unit,
    maxUnitNumber: Int?,
    incrementStep: Int,
    onRegisterSyncApplyUnitProgress: ((() -> UnitProgressEditState) -> Unit)? = null,
    searchBarColor: Color,
    fieldTextColor: Color,
    iconTextColor: Color,
) {
    if (unitProgressList.isEmpty()) return

    val safeIndex = selectedUnitIndex.coerceIn(unitProgressList.indices)
    val currentUnit = unitProgressList[safeIndex]

    var unitNumberText by remember { mutableStateOf((safeIndex + 1).toString()) }
    var unitNumberFieldFocused by remember { mutableStateOf(false) }
    var completedText by remember(safeIndex) {
        mutableStateOf(formatEditableUnitNumber(currentUnit.completed, emptyAsZero = true))
    }
    var completedFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(safeIndex, currentUnit.completed) {
        if (completedFieldFocused) return@LaunchedEffect
        val parsed = completedText.trim().toDoubleOrNull() ?: 0.0
        if (parsed != currentUnit.completed) {
            completedText = formatEditableUnitNumber(currentUnit.completed, emptyAsZero = true)
        }
    }

    LaunchedEffect(safeIndex) {
        if (!unitNumberFieldFocused) {
            unitNumberText = (safeIndex + 1).toString()
        }
    }

    val fieldHighlight = FieldHighlightColor()

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = searchBarColor,
        unfocusedContainerColor = searchBarColor,
        focusedIndicatorColor = fieldHighlight,
        unfocusedIndicatorColor = iconTextColor.copy(alpha = 0.3f),
        focusedTextColor = fieldTextColor,
        unfocusedTextColor = fieldTextColor,
        focusedLabelColor = fieldHighlight,
        unfocusedLabelColor = fieldTextColor,
        cursorColor = fieldHighlight,
    )

    fun formatProgressValue(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }

    fun coerceUnitNumber(raw: Int): Int {
        var n = raw.coerceAtLeast(1)
        if (maxUnitNumber != null) {
            n = n.coerceAtMost(maxUnitNumber)
        }
        return n
    }

    fun ensureUnitsUpTo(targetIndex: Int): List<UnitProgress> {
        val updated = unitProgressList.toMutableList()
        while (updated.size <= targetIndex) {
            updated += UnitProgress(
                unitName = "$unitPrefixLabel ${updated.size + 1}",
                completed = 0.0,
                total = null,
            )
        }
        return updated
    }

    fun selectUnitNumber(unitNumber: Int) {
        val coerced = coerceUnitNumber(unitNumber)
        val targetIndex = coerced - 1
        val updated = ensureUnitsUpTo(targetIndex)
        onUnitProgressListChange(updated)
        onSelectedUnitIndexChange(targetIndex)
        unitNumberText = coerced.toString()
    }

    fun applyUnitNumberFromText(
        fromState: UnitProgressEditState = UnitProgressEditState(unitProgressList, safeIndex),
    ): UnitProgressEditState {
        val digits = unitNumberText.filter { it.isDigit() }
        if (digits.isEmpty()) {
            unitNumberText = (fromState.activeIndex + 1).toString()
            return fromState
        }
        val coerced = coerceUnitNumber(digits.toIntOrNull() ?: (fromState.activeIndex + 1))
        val targetIndex = coerced - 1
        val updated = fromState.units.toMutableList()
        while (updated.size <= targetIndex) {
            updated += UnitProgress(
                unitName = "$unitPrefixLabel ${updated.size + 1}",
                completed = 0.0,
                total = null,
            )
        }
        onUnitProgressListChange(updated)
        onSelectedUnitIndexChange(targetIndex)
        unitNumberText = coerced.toString()
        unitNumberFieldFocused = false
        return UnitProgressEditState(updated, targetIndex)
    }

    fun applyCompletedFromText(): UnitProgressEditState {
        val completed = parseUnitDecimalInput(completedText, emptyAsZero = true) ?: 0.0
        val index = safeIndex
        if (index !in unitProgressList.indices) {
            return UnitProgressEditState(unitProgressList, index)
        }
        val updated = unitProgressList.toMutableList()
        val unit = updated[index]
        val safeCompleted = completed.coerceAtLeast(0.0)
        val newTotal = unit.total?.takeIf { it > 0.0 }
            ?: safeCompleted.takeIf { it > 0.0 }
        updated[index] = unit.copy(completed = safeCompleted, total = newTotal)
        onUnitProgressListChange(updated)
        completedFieldFocused = false
        return UnitProgressEditState(updated, index)
    }

    fun currentUnitProgressState(): UnitProgressEditState =
        UnitProgressEditState(unitProgressList, safeIndex)

    fun syncUnitProgressEditsFromFields(): UnitProgressEditState {
        var state = if (completedText != formatEditableUnitNumber(currentUnit.completed, emptyAsZero = true)) {
            applyCompletedFromText()
        } else {
            currentUnitProgressState()
        }
        if (unitNumberText.filter { it.isDigit() }.isNotEmpty()) {
            state = applyUnitNumberFromText(state)
        }
        return state
    }

    SideEffect {
        onRegisterSyncApplyUnitProgress?.invoke(::syncUnitProgressEditsFromFields)
    }

    fun updateCompletedForIndex(index: Int, completed: Double) {
        val updated = unitProgressList.toMutableList()
        if (index !in updated.indices) return
        val unit = updated[index]
        val safeCompleted = completed.coerceAtLeast(0.0)
        val newTotal = unit.total?.takeIf { it > 0.0 }
            ?: safeCompleted.takeIf { it > 0.0 }
        updated[index] = unit.copy(completed = safeCompleted, total = newTotal)
        onUnitProgressListChange(updated)
    }

    LaunchedEffect(maxUnitNumber, safeIndex) {
        if (maxUnitNumber != null && safeIndex + 1 > maxUnitNumber) {
            selectUnitNumber(maxUnitNumber)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Номер тома / сезона (без подписи) + стрелка
        Row(
            modifier = Modifier.weight(0.38f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = unitNumberText,
                onValueChange = { newValue ->
                    unitNumberText = newValue.filter { it.isDigit() }
                    val digits = unitNumberText
                    if (digits.isNotEmpty()) {
                        val coerced = coerceUnitNumber(digits.toIntOrNull() ?: 1)
                        val targetIndex = coerced - 1
                        if (targetIndex != safeIndex) {
                            val updated = ensureUnitsUpTo(targetIndex)
                            onUnitProgressListChange(updated)
                            onSelectedUnitIndexChange(targetIndex)
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus ->
                        if (focus.isFocused) {
                            unitNumberFieldFocused = true
                        } else if (unitNumberFieldFocused) {
                            applyUnitNumberFromText()
                        }
                    },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { applyUnitNumberFromText() },
                ),
                colors = fieldColors,
            )
            IconButton(
                onClick = {
                    val current = if (unitNumberFieldFocused) {
                        applyUnitNumberFromText()
                        unitNumberText.toIntOrNull() ?: (safeIndex + 1)
                    } else {
                        unitNumberText.toIntOrNull() ?: (safeIndex + 1)
                    }
                    if (maxUnitNumber != null && current >= maxUnitNumber) return@IconButton
                    selectUnitNumber(current + 1)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    text = "▲",
                    color = fieldTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Прочитано / просмотрено + стрелка (значение для выбранного тома/сезона)
        Row(
            modifier = Modifier.weight(0.62f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = completedText,
                onValueChange = { newValue ->
                    val validValue = filterUnitDecimalInput(newValue)
                    completedText = validValue
                    updateCompletedForIndex(
                        safeIndex,
                        parseUnitDecimalInput(validValue, emptyAsZero = true) ?: 0.0,
                    )
                },
                label = { Text(progressLabel, fontSize = 13.5.sp) },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { completedFieldFocused = it.isFocused },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors,
            )
            IconButton(
                onClick = {
                    val current = completedText.toDoubleOrNull() ?: 0.0
                    val newValue = (current + incrementStep).coerceAtLeast(0.0)
                    val asString = formatProgressValue(newValue)
                    completedText = asString
                    updateCompletedForIndex(safeIndex, newValue)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Text(
                    text = "▲",
                    color = fieldTextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
