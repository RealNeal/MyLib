package com.rn.library.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rn.library.R
import com.rn.library.data.GenreTagManager
import com.rn.library.data.Work
import com.rn.library.data.WorkRepository
import com.rn.library.ui.theme.AppButtonColor
import com.rn.library.ui.theme.FieldHighlightColor
import com.rn.library.ui.theme.IconTextColor
import com.rn.library.ui.theme.MainBackgroundColor
import com.rn.library.ui.theme.TitleColorBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GenreTagItem(
    val name: String,
    val count: Int,
    val isCustom: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreTagSettingsScreen(
    onBack: () -> Unit,
    repository: WorkRepository,
    onWorksUpdated: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainBackgroundColor = MainBackgroundColor()
    val titleColorBetween = TitleColorBetween()
    val iconTextColor = IconTextColor()
    val fieldHighlight = FieldHighlightColor()
    val buttonColor = AppButtonColor()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Genres, 1: Tags
    var searchQuery by remember { mutableStateOf("") }
    var newItemInput by remember { mutableStateOf("") }

    var allWorks by remember { mutableStateOf<List<Work>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Dialog state for Merge
    var mergeSourceItem by remember { mutableStateOf<String?>(null) }
    var mergeTargetInput by remember { mutableStateOf(TextFieldValue("")) }
    var mergeUpdateWorks by remember { mutableStateOf(true) }
    var showMergeDialog by remember { mutableStateOf(false) }

    // Dialog state for Delete
    var deleteItem by remember { mutableStateOf<String?>(null) }
    var deleteRemoveFromWorks by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            allWorks = repository.getAllWorks()
        }
    }

    val defaultGenres = remember {
        listOf(
            "Детектив", "Драма", "Игры", "История", "Комедия",
            "Магия", "Меха", "Мистика", "Музыка", "Повседневность",
            "Приключения", "Психология", "Романтика", "Сверхъестественное",
            "Спорт", "Сёнэн", "Сэйнэн", "Сёдзё", "Триллер", "Ужасы",
            "Фантастика", "Фэнтези", "Школа", "Экшен"
        )
    }

    val customGenres = remember(refreshTrigger) { GenreTagManager.getCustomGenres(context) }
    val customTags = remember(refreshTrigger) { GenreTagManager.getCustomTags(context) }

    // Build genre items with counts
    val genreItems = remember(allWorks, customGenres, refreshTrigger) {
        val countMap = mutableMapOf<String, Int>()
        allWorks.forEach { work ->
            work.genres.forEach { g ->
                val norm = g.trim()
                if (norm.isNotBlank()) {
                    countMap[norm] = (countMap[norm] ?: 0) + 1
                }
            }
        }
        val allNames = (countMap.keys + customGenres + defaultGenres).distinct()
        allNames.map { name ->
            GenreTagItem(
                name = name,
                count = countMap[name] ?: 0,
                isCustom = customGenres.contains(name)
            )
        }.sortedWith(compareByDescending<GenreTagItem> { it.count }.thenBy { it.name.lowercase() })
    }

    // Build tag items with counts
    val tagItems = remember(allWorks, customTags, refreshTrigger) {
        val countMap = mutableMapOf<String, Int>()
        allWorks.forEach { work ->
            work.tags.forEach { t ->
                val norm = t.trim()
                if (norm.isNotBlank()) {
                    countMap[norm] = (countMap[norm] ?: 0) + 1
                }
            }
        }
        val allNames = (countMap.keys + customTags).distinct()
        allNames.map { name ->
            GenreTagItem(
                name = name,
                count = countMap[name] ?: 0,
                isCustom = customTags.contains(name)
            )
        }.sortedWith(compareByDescending<GenreTagItem> { it.count }.thenBy { it.name.lowercase() })
    }

    val currentList = if (selectedTab == 0) genreItems else tagItems
    val filteredList = remember(currentList, searchQuery) {
        if (searchQuery.isBlank()) {
            currentList
        } else {
            val q = searchQuery.trim().lowercase()
            currentList.filter { it.name.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mainBackgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cancel),
                    tint = iconTextColor
                )
            }
            Text(
                text = stringResource(R.string.genres_and_tags_settings),
                color = titleColorBetween,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Distinct, rounded & separated tabs for "Genres" and "Tags" (highlighting bottom edge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val isGenres = selectedTab == 0
            val isTags = selectedTab == 1

            // Genres Tab Button
            Surface(
                onClick = { selectedTab = 0; searchQuery = "" },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (isGenres) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .drawWithContent {
                        drawContent()
                        if (isGenres) {
                            val stroke = 3.5.dp.toPx()
                            val r = 16.dp.toPx()
                            val w = size.width
                            val h = size.height
                            val cornerRadius = r - stroke / 2f
                            val leftRect = Rect(
                                stroke / 2f,
                                h - 2 * cornerRadius - stroke / 2f,
                                2 * cornerRadius + stroke / 2f,
                                h - stroke / 2f
                            )
                            val rightRect = Rect(
                                w - 2 * cornerRadius - stroke / 2f,
                                h - 2 * cornerRadius - stroke / 2f,
                                w - stroke / 2f,
                                h - stroke / 2f
                            )
                            val path = Path().apply {
                                arcTo(leftRect, 135f, -45f, false)
                                lineTo(w - r, h - stroke / 2f)
                                arcTo(rightRect, 90f, -45f, false)
                            }
                            clipPath(Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(0f, 0f, w, h),
                                        cornerRadius = CornerRadius(r, r)
                                    )
                                )
                            }) {
                                drawPath(
                                    path = path,
                                    color = primaryColor,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.genres_label),
                            fontWeight = if (isGenres) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            color = if (isGenres) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isGenres) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${genreItems.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isGenres) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Tags Tab Button
            Surface(
                onClick = { selectedTab = 1; searchQuery = "" },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (isTags) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .drawWithContent {
                        drawContent()
                        if (isTags) {
                            val stroke = 3.5.dp.toPx()
                            val r = 16.dp.toPx()
                            val w = size.width
                            val h = size.height
                            val cornerRadius = r - stroke / 2f
                            val leftRect = Rect(
                                stroke / 2f,
                                h - 2 * cornerRadius - stroke / 2f,
                                2 * cornerRadius + stroke / 2f,
                                h - stroke / 2f
                            )
                            val rightRect = Rect(
                                w - 2 * cornerRadius - stroke / 2f,
                                h - 2 * cornerRadius - stroke / 2f,
                                w - stroke / 2f,
                                h - stroke / 2f
                            )
                            val path = Path().apply {
                                arcTo(leftRect, 135f, -45f, false)
                                lineTo(w - r, h - stroke / 2f)
                                arcTo(rightRect, 90f, -45f, false)
                            }
                            clipPath(Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        rect = Rect(0f, 0f, w, h),
                                        cornerRadius = CornerRadius(r, r)
                                    )
                                )
                            }) {
                                drawPath(
                                    path = path,
                                    color = primaryColor,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tags_label),
                            fontWeight = if (isTags) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            color = if (isTags) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isTags) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "${tagItems.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTags) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Add Item Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newItemInput,
                onValueChange = { newItemInput = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (selectedTab == 0) {
                            stringResource(R.string.add_genre_hint)
                        } else {
                            stringResource(R.string.add_tag_hint)
                        },
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fieldHighlight,
                    cursorColor = fieldHighlight
                )
            )
            Button(
                onClick = {
                    val trimmed = newItemInput.trim()
                    if (trimmed.isNotBlank()) {
                        if (selectedTab == 0) {
                            GenreTagManager.addCustomGenre(context, trimmed)
                        } else {
                            GenreTagManager.addCustomTag(context, trimmed)
                        }
                        Toast.makeText(
                            context,
                            context.getString(R.string.item_added_toast, trimmed),
                            Toast.LENGTH_SHORT
                        ).show()
                        newItemInput = ""
                        refreshTrigger++
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                shape = RoundedCornerShape(14.dp),
                enabled = newItemInput.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.add_genre_or_tag), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Filter & Merge Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(stringResource(R.string.filter_items_hint), fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = fieldHighlight,
                    cursorColor = fieldHighlight
                )
            )

            Button(
                onClick = {
                    mergeSourceItem = null
                    mergeTargetInput = TextFieldValue("")
                    mergeUpdateWorks = true
                    showMergeDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.merge_genres_tags), fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Items List
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_items_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredList, key = { it.name }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.works_count_label, item.count),
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Quick Merge button for this item
                                IconButton(
                                    onClick = {
                                        mergeSourceItem = item.name
                                        mergeTargetInput = TextFieldValue("")
                                        mergeUpdateWorks = true
                                        showMergeDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallMerge,
                                        contentDescription = stringResource(R.string.merge_genres_tags),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = {
                                        deleteItem = item.name
                                        deleteRemoveFromWorks = true
                                        showDeleteDialog = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Merge Dialog
    if (showMergeDialog) {
        val isGenreTab = selectedTab == 0
        val candidateNames = remember(currentList, mergeSourceItem) {
            currentList.map { it.name }.filterNot { item ->
                mergeSourceItem != null && GenreTagManager.matchesTagOrGenre(item, mergeSourceItem!!)
            }
        }
        var targetDropdownExpanded by remember { mutableStateOf(false) }
        var selectedTargetCandidate by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.merge_dialog_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Source Item
                    if (mergeSourceItem != null) {
                        Text(
                            text = "${stringResource(R.string.source_item_label)}: ${mergeSourceItem}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        var sourceInput by remember { mutableStateOf(TextFieldValue("")) }
                        var sourceDropdownExpanded by remember { mutableStateOf(false) }
                        var selectedSourceCandidate by remember { mutableStateOf<String?>(null) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = sourceInput,
                                onValueChange = { newValue ->
                                    sourceInput = newValue
                                    if (selectedSourceCandidate == null || newValue.text != selectedSourceCandidate) {
                                        selectedSourceCandidate = null
                                        sourceDropdownExpanded = true
                                        mergeSourceItem = newValue.text.trim().ifEmpty { null }
                                    } else {
                                        sourceDropdownExpanded = false
                                    }
                                },
                                label = { Text(stringResource(R.string.source_item_label)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            val matchingSources = remember(candidateNames, sourceInput.text, selectedSourceCandidate) {
                                if (selectedSourceCandidate != null || sourceInput.text.isBlank()) {
                                    emptyList()
                                } else {
                                    candidateNames.filter {
                                        it.lowercase().contains(sourceInput.text.lowercase()) &&
                                        !it.equals(sourceInput.text, ignoreCase = true)
                                    }.take(5)
                                }
                            }
                            DropdownMenu(
                                expanded = sourceDropdownExpanded && matchingSources.isNotEmpty(),
                                onDismissRequest = { sourceDropdownExpanded = false },
                                properties = PopupProperties(focusable = false),
                                shape = RoundedCornerShape(14.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                shadowElevation = 6.dp,
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .heightIn(max = 240.dp)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        matchingSources.forEach { candidate ->
                                            AssistChip(
                                                onClick = {
                                                    selectedSourceCandidate = candidate
                                                    sourceDropdownExpanded = false
                                                    sourceInput = TextFieldValue(
                                                        text = candidate,
                                                        selection = TextRange(candidate.length)
                                                    )
                                                    mergeSourceItem = candidate
                                                },
                                                label = {
                                                    Text(
                                                        text = candidate,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = Color.Transparent,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Target Item
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = mergeTargetInput,
                            onValueChange = { newValue ->
                                mergeTargetInput = newValue
                                if (selectedTargetCandidate == null || newValue.text != selectedTargetCandidate) {
                                    selectedTargetCandidate = null
                                    targetDropdownExpanded = true
                                } else {
                                    targetDropdownExpanded = false
                                }
                            },
                            label = { Text(stringResource(R.string.target_item_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        val matchingTargets = remember(candidateNames, mergeTargetInput.text, selectedTargetCandidate) {
                            if (selectedTargetCandidate != null || mergeTargetInput.text.isBlank()) {
                                emptyList()
                            } else {
                                candidateNames.filter {
                                    it.lowercase().contains(mergeTargetInput.text.lowercase()) &&
                                    !it.equals(mergeTargetInput.text, ignoreCase = true)
                                }.take(5)
                            }
                        }
                        DropdownMenu(
                            expanded = targetDropdownExpanded && matchingTargets.isNotEmpty(),
                            onDismissRequest = { targetDropdownExpanded = false },
                            properties = PopupProperties(focusable = false),
                            shape = RoundedCornerShape(14.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            shadowElevation = 6.dp,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    matchingTargets.forEach { candidate ->
                                        AssistChip(
                                            onClick = {
                                                selectedTargetCandidate = candidate
                                                targetDropdownExpanded = false
                                                mergeTargetInput = TextFieldValue(
                                                    text = candidate,
                                                    selection = TextRange(candidate.length)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = candidate,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color.Transparent,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Checkbox update in existing works
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = mergeUpdateWorks,
                                role = Role.Checkbox,
                                onValueChange = { mergeUpdateWorks = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = mergeUpdateWorks,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = fieldHighlight)
                        )
                        Text(
                            text = stringResource(R.string.update_works_checkbox),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val source = mergeSourceItem?.trim().orEmpty()
                        val target = mergeTargetInput.text.trim()
                        if (source.isNotBlank() && target.isNotBlank()) {
                            scope.launch {
                                val updated = withContext(Dispatchers.IO) {
                                    if (isGenreTab) {
                                        GenreTagManager.mergeGenres(context, source, target, mergeUpdateWorks, repository)
                                    } else {
                                        GenreTagManager.mergeTags(context, source, target, mergeUpdateWorks, repository)
                                    }
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.merge_success_toast, updated),
                                    Toast.LENGTH_LONG
                                ).show()
                                refreshTrigger++
                                onWorksUpdated?.invoke()
                                showMergeDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !mergeSourceItem.isNullOrBlank() && mergeTargetInput.text.isNotBlank()
                ) {
                    Text(stringResource(R.string.merge_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && deleteItem != null) {
        val isGenreTab = selectedTab == 0
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isGenreTab) {
                            stringResource(R.string.delete_genre_confirm_message, deleteItem!!)
                        } else {
                            stringResource(R.string.delete_tag_confirm_message, deleteItem!!)
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = deleteRemoveFromWorks,
                                role = Role.Checkbox,
                                onValueChange = { deleteRemoveFromWorks = it }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteRemoveFromWorks,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = fieldHighlight)
                        )
                        Text(
                            text = stringResource(R.string.update_works_checkbox),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = deleteItem ?: return@Button
                        scope.launch {
                            val updated = withContext(Dispatchers.IO) {
                                if (isGenreTab) {
                                    GenreTagManager.deleteGenre(context, target, deleteRemoveFromWorks, repository)
                                } else {
                                    GenreTagManager.deleteTag(context, target, deleteRemoveFromWorks, repository)
                                }
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.item_deleted_toast, target),
                                Toast.LENGTH_SHORT
                            ).show()
                            refreshTrigger++
                            onWorksUpdated?.invoke()
                            showDeleteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
