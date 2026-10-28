package com.rn.library.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rn.library.R
import com.rn.library.data.WorkRepository
import com.rn.library.data.WorkType
import com.rn.library.parser.*
import com.rn.library.ui.AppSettings
import com.rn.library.ui.theme.AppButtonColor
import com.rn.library.ui.theme.FieldHighlightColor
import com.rn.library.ui.theme.IconTextColor
import com.rn.library.ui.theme.ParserIconColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSearchDialog(
    initialQuery: String = "",
    initialSource: MediaSource = MediaSource.SHIKIMORI,
    initialFilter: ShikimoriFilterType = ShikimoriFilterType.ALL,
    workId: String,
    repository: WorkRepository,
    onDismiss: () -> Unit,
    onWorkSelected: (ParsedWorkMetadata, localCoverPath: String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            NetworkParserManager.clearCache(context)
        }
    }

    var query by remember { mutableStateOf(initialQuery) }
    var selectedCategory by remember {
        mutableStateOf(initialSource.categories.firstOrNull() ?: SourceCategory.RANOBE)
    }
    var selectedLanguage by remember { mutableStateOf("ALL") } // "ALL", "RU", "EN"
    val enrichFromOtherSources = remember { AppSettings.isSearchOtherSourcesEnabled(context) }
    var currentSource by remember { mutableStateOf(initialSource) }
    var currentFilter by remember { mutableStateOf(initialFilter) }
    var searchResults by remember { mutableStateOf<List<ShikimoriSearchResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isImportingDetails by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val clipboardText = remember { clipboardManager.getText()?.text?.trim().orEmpty() }
    val clipboardDetectedSource = remember(clipboardText) {
        NetworkParserManager.detectSourceFromUrl(clipboardText)
    }

    val directDetectedSource = remember(query) {
        NetworkParserManager.detectSourceFromUrl(query)
    }

    fun runDirectUrlImport(urlStr: String) {
        val detected = NetworkParserManager.detectSourceFromUrl(urlStr) ?: currentSource
        val cat = detected.categories.firstOrNull()
        if (cat != null) {
            selectedCategory = cat
        }
        currentSource = detected
        isImportingDetails = true
        errorMessage = null
        coroutineScope.launch {
            val searchRes = NetworkParserManager.search(urlStr, detected, currentFilter)
            if (searchRes.isSuccess && searchRes.getOrThrow().isNotEmpty()) {
                val firstItem = searchRes.getOrThrow().first()
                val detailsRes = NetworkParserManager.fetchDetails(firstItem)
                if (detailsRes.isSuccess) {
                    var metadata = detailsRes.getOrThrow()
                    if (enrichFromOtherSources) {
                        metadata = NetworkParserManager.enrichFromOtherSources(
                            primary = metadata,
                            category = selectedCategory,
                            workId = workId,
                            repository = repository
                        )
                    }
                    val localPath = if (!metadata.coverUrl.isNullOrBlank()) {
                        repository.downloadCoverToStorage(metadata.coverUrl, workId, 0)
                    } else null
                    isImportingDetails = false
                    NetworkParserManager.clearCache(context)
                    onWorkSelected(metadata, localPath)
                    onDismiss()
                    return@launch
                }
            }
            isImportingDetails = false
            errorMessage = context.getString(R.string.network_link_load_failed)
        }
    }

    fun executeSearch(searchQuery: String, source: MediaSource, filter: ShikimoriFilterType) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank()) return

        val detected = NetworkParserManager.detectSourceFromUrl(trimmed)
        if (detected != null) {
            runDirectUrlImport(trimmed)
            return
        }

        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val result = NetworkParserManager.search(trimmed, source, filter)
            isLoading = false
            if (result.isSuccess) {
                searchResults = result.getOrThrow()
            } else {
                searchResults = emptyList()
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.search_failed)
            }
        }
    }

    // Dynamic search with debounce
    LaunchedEffect(query, currentSource, currentFilter) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            searchResults = emptyList()
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        val detected = NetworkParserManager.detectSourceFromUrl(trimmed)
        if (detected != null) {
            val cat = detected.categories.firstOrNull()
            if (cat != null) {
                selectedCategory = cat
            }
            if (detected != currentSource) {
                currentSource = detected
            }
            // For URLs, user can tap the "Load from link" button or press enter
            searchResults = emptyList()
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        delay(400)
        isLoading = true
        errorMessage = null
        val result = NetworkParserManager.search(trimmed, currentSource, currentFilter)
        isLoading = false
        if (result.isSuccess) {
            searchResults = result.getOrThrow()
        } else {
            searchResults = emptyList()
            errorMessage = result.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.search_failed)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isImportingDetails) {
                NetworkParserManager.clearCache(context)
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = ParserIconColor(),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.search_network),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = {
                            NetworkParserManager.clearCache(context)
                            onDismiss()
                        },
                        enabled = !isImportingDetails
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Category Selection (Ранобэ, Манга, Аниме, Фильмы)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        SourceCategory.entries.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (selectedCategory != cat) {
                                        selectedCategory = cat
                                        val catSources = MediaSource.entries.filter {
                                            it.hasCategory(cat) && when (selectedLanguage) {
                                                "RU" -> it.isRussian
                                                "EN" -> it.isEnglish
                                                else -> true
                                            }
                                        }
                                        if (currentSource !in catSources) {
                                            currentSource = catSources.firstOrNull() ?: MediaSource.entries.firstOrNull { it.hasCategory(cat) } ?: MediaSource.SHIKIMORI
                                        }
                                        errorMessage = null
                                        if (query.isNotBlank()) {
                                            executeSearch(query, currentSource, currentFilter)
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = stringResource(cat.titleRes),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Language Selection Chips (Все, Русские, Английские)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val languages = listOf(
                        "ALL" to stringResource(R.string.source_lang_all),
                        "RU" to stringResource(R.string.source_lang_ru),
                        "EN" to stringResource(R.string.source_lang_en)
                    )
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        languages.forEach { (code, label) ->
                            val isSelected = selectedLanguage == code
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (selectedLanguage != code) {
                                        selectedLanguage = code
                                        val filtered = MediaSource.entries.filter {
                                            it.hasCategory(selectedCategory) && when (code) {
                                                "RU" -> it.isRussian
                                                "EN" -> it.isEnglish
                                                else -> true
                                            }
                                        }
                                        if (currentSource !in filtered) {
                                            currentSource = filtered.firstOrNull() ?: currentSource
                                        }
                                        errorMessage = null
                                        if (query.isNotBlank()) {
                                            executeSearch(query, currentSource, currentFilter)
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Media Source Selection Chips for selected category and language
                val availableSources = remember(selectedCategory, selectedLanguage) {
                    MediaSource.entries.filter { source ->
                        source.hasCategory(selectedCategory) && when (selectedLanguage) {
                            "RU" -> source.isRussian
                            "EN" -> source.isEnglish
                            else -> true
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        availableSources.forEach { source ->
                            val isSelected = currentSource == source
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    currentSource = source
                                    errorMessage = null
                                    if (query.isNotBlank()) {
                                        executeSearch(query, source, currentFilter)
                                    }
                                },
                                label = {
                                    Text(
                                        text = source.title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val fieldHighlight = FieldHighlightColor()
                val buttonColor = AppButtonColor()

                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.network_dialog_hint),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = {
                                    query = ""
                                    searchResults = emptyList()
                                    errorMessage = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            } else if (clipboardDetectedSource != null || clipboardText.isNotBlank()) {
                                IconButton(onClick = {
                                    query = clipboardText
                                    clipboardDetectedSource?.let {
                                        it.categories.firstOrNull()?.let { cat -> selectedCategory = cat }
                                        currentSource = it
                                    }
                                    runDirectUrlImport(clipboardText)
                                }) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = stringResource(R.string.network_paste_from_clipboard)
                                    )
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        executeSearch(query, currentSource, currentFilter)
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = fieldHighlight,
                        cursorColor = fieldHighlight,
                        focusedLeadingIconColor = fieldHighlight
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // If URL detected, show quick direct download button
                if (directDetectedSource != null) {
                    Button(
                        onClick = { runDirectUrlImport(query) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isImportingDetails
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.network_load_by_link))
                    }
                } else {
                    // Category filter chips for sources that support them (Shikimori, AniList, MyAnimeList)
                    val supportsCategoryFilter = currentSource in listOf(
                        MediaSource.SHIKIMORI,
                        MediaSource.ANILIST,
                        MediaSource.MYANIMELIST
                    )

                    if (supportsCategoryFilter) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val filters = listOf(
                                ShikimoriFilterType.ALL to stringResource(R.string.filter_all),
                                ShikimoriFilterType.ANIME to stringResource(R.string.anime),
                                ShikimoriFilterType.MANGA to stringResource(R.string.manga),
                                ShikimoriFilterType.RANOBE to stringResource(R.string.filter_ranobe)
                            )

                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                filters.forEach { (filter, label) ->
                                    val selected = currentFilter == filter
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            currentFilter = filter
                                            if (query.isNotBlank()) {
                                                executeSearch(query, currentSource, filter)
                                            }
                                        },
                                        label = { Text(label, fontSize = 12.sp) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Content area (where works are displayed)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                        isImportingDetails -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringResource(R.string.network_loading_details),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        isLoading -> {
                            CircularProgressIndicator()
                        }
                        errorMessage != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = errorMessage ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = { executeSearch(query, currentSource, currentFilter) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = buttonColor
                                    )
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                        searchResults.isEmpty() && query.isNotBlank() && !isLoading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = stringResource(R.string.network_empty_results),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (currentSource == MediaSource.SCRIBBLEHUB) {
                                    Text(
                                        text = stringResource(R.string.source_warning_scribblehub),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                    )
                                } else if (currentSource == MediaSource.RANOBES) {
                                    Text(
                                        text = stringResource(R.string.source_warning_ranobes),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        searchResults.isEmpty() -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = stringResource(R.string.network_search_hint_sub),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(searchResults, key = { index, it -> "${it.source.id}_${it.endpoint}_${it.stringId}_$index" }) { _, item ->
                                    NetworkResultItem(
                                        item = item,
                                        onClick = {
                                            isImportingDetails = true
                                            coroutineScope.launch {
                                                val result = NetworkParserManager.fetchDetails(item)
                                                if (result.isSuccess) {
                                                    var metadata = result.getOrThrow()
                                                    if (enrichFromOtherSources) {
                                                        metadata = NetworkParserManager.enrichFromOtherSources(
                                                            primary = metadata,
                                                            category = selectedCategory,
                                                            workId = workId,
                                                            repository = repository
                                                        )
                                                    }
                                                    val localPath = if (!metadata.coverUrl.isNullOrBlank()) {
                                                        repository.downloadCoverToStorage(metadata.coverUrl, workId, 0)
                                                    } else null
                                                    isImportingDetails = false
                                                    NetworkParserManager.clearCache(context)
                                                    onWorkSelected(metadata, localPath)
                                                    onDismiss()
                                                } else {
                                                    isImportingDetails = false
                                                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: context.getString(R.string.load_failed)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Source warning / tip inside works display area (disappears when works appear, floats at top without shifting center)
                    if (searchResults.isEmpty()) {
                        currentSource.warningRes?.let { wRes ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(wRes),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        lineHeight = 16.sp
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

@Composable
private fun NetworkResultItem(
    item: ShikimoriSearchResult,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            if (!item.coverUrl.isNullOrBlank()) {
                val imageRequest = ImageRequest.Builder(LocalContext.current)
                    .data(item.coverUrl)
                    .crossfade(true)
                    .apply {
                        val url = item.coverUrl.orEmpty()
                        val referer = when {
                            url.contains("mangabuff") -> "https://mangabuff.ru/"
                            url.contains("imglib") || url.contains("cdnlibs") || url.contains("lib.social") || url.contains("mangalib") -> {
                                when {
                                    url.contains("ranobelib") || url.contains("ranobe") -> "https://ranobelib.me/"
                                    url.contains("animelib") || url.contains("anime") -> "https://animelib.me/"
                                    else -> "https://mangalib.me/"
                                }
                            }
                            url.contains("ranobelib") -> "https://ranobelib.me/"
                            url.contains("animelib") -> "https://animelib.me/"
                            url.contains("anixmirai") || url.contains("anixart") -> "https://anixart.tv/"
                            url.contains("remanga") -> "https://remanga.org/"
                            url.contains("shikimori") -> "https://shikimori.one/"
                            url.contains("wtr-lab") -> "https://wtr-lab.com/"
                            url.contains("ranobehub") -> "https://ranobehub.org/"
                            url.contains("royalroad") -> "https://www.royalroad.com/"
                            url.contains("ranobes") -> "https://ranobes.net/"
                            url.contains("scribblehub") -> "https://www.scribblehub.com/"
                            url.contains("rulate") || url.contains("tl.rulate.ru") -> "https://tl.rulate.ru/"
                            url.contains("media-amazon.com") || url.contains("imdb.com") -> "https://www.imdb.com/"
                            else -> null
                        }
                        if (referer != null) {
                            addHeader("Referer", referer)
                        }
                        addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    }
                    .build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.russian.ifBlank { item.name },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (item.targetType) {
                            WorkType.ANIME -> Icons.Default.Movie
                            WorkType.BOOK -> Icons.AutoMirrored.Filled.MenuBook
                            WorkType.SERIES -> Icons.Default.Tv
                            else -> Icons.Default.Book
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isRu = item.source.lang == "RU"
                val hasCyrillicInRussian = item.russian.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
                val hasCyrillicInName = item.name.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

                val (primaryTitle, secondaryTitle) = if (isRu) {
                    val primary = when {
                        hasCyrillicInRussian -> item.russian
                        hasCyrillicInName -> item.name
                        item.russian.isNotBlank() -> item.russian
                        else -> item.name
                    }
                    val secondary = if (primary == item.russian) item.name else item.russian
                    primary to if (secondary.isNotBlank() && secondary != primary) secondary else ""
                } else {
                    val primary = if (item.name.isNotBlank()) item.name else item.russian
                    val secondary = if (item.russian.isNotBlank() && item.russian != primary) item.russian else ""
                    primary to secondary
                }

                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (secondaryTitle.isNotBlank()) {
                    Text(
                        text = secondaryTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Metadata row: Kind / Year / Episodes or Chapters
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    val kindDisplay = formatKindDisplay(item.kind, context)
                    if (kindDisplay.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = kindDisplay,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (item.year != null) {
                        Text(
                            text = item.year.toString(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatKindDisplay(kind: String, context: Context): String {
    val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale
    }
    val isRu = locale?.language?.lowercase() == "ru"
    return when (kind.lowercase()) {
        "tv" -> "TV"
        "movie", "film", "фильм" -> if (isRu) "Фильм" else "Movie"
        "ova" -> "OVA"
        "ona" -> "ONA"
        "special" -> if (isRu) "Спешл" else "Special"
        "music" -> if (isRu) "Клип" else "Music"
        "manga", "манга" -> if (isRu) "Манга" else "Manga"
        "manhwa", "манхва" -> if (isRu) "Манхва" else "Manhwa"
        "manhua", "маньхуа" -> if (isRu) "Маньхуа" else "Manhua"
        "light_novel", "ranobe", "ранобэ" -> if (isRu) "Ранобэ" else "Light Novel"
        "novel", "новелла" -> if (isRu) "Новелла" else "Novel"
        "one_shot" -> if (isRu) "Ваншот" else "One-shot"
        "doujin" -> if (isRu) "Додзинси" else "Doujinshi"
        "сериал", "tv_series", "series" -> if (isRu) "Сериал" else "TV Series"
        else -> kind.uppercase()
    }
}
