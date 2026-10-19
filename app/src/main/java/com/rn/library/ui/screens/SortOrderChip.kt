package com.rn.library.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rn.library.R
import com.rn.library.data.WorkStatus
import com.rn.library.ui.components.NavigationItem

fun workStatusColor(status: WorkStatus): Color = when (status) {
    WorkStatus.IN_PLANS -> Color(0xFF8E6687)      // rgb(142, 102, 147)
    WorkStatus.ABANDONED -> Color(0xFFFF5F5A)     // rgb(255, 95, 90)
    WorkStatus.READING, WorkStatus.WATCHING -> Color(0xFF7179A4)       // rgb(113, 121, 164)
    WorkStatus.READ, WorkStatus.WATCHED -> Color(0xFF79C77C)          // rgb(121, 199, 124)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOrderChip(
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    tabColor: Color = MaterialTheme.colorScheme.primary,
    iconTextColor: Color = Color.Unspecified
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val label = when (sortOrder) {
        SortOrder.RECENT -> stringResource(R.string.sort_recent)
        SortOrder.NEWEST -> stringResource(R.string.sort_newest)
        SortOrder.TITLE -> stringResource(R.string.sort_by_title)
        SortOrder.TITLE_DESC -> stringResource(R.string.sort_by_title_desc)
        SortOrder.AUTHOR -> stringResource(R.string.sort_by_author)
        SortOrder.GENRE -> stringResource(R.string.sort_by_genre)
        SortOrder.TAG -> stringResource(R.string.sort_by_tag)
    }

    Box {
        AssistChip(
            onClick = { menuExpanded = true },
            label = {
                Text(
                    text = label,
                    color = tabColor,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = null,
                    tint = tabColor
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = tabColor
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color.Transparent,
                labelColor = tabColor
            ),
            border = AssistChipDefaults.assistChipBorder(
                borderColor = tabColor.copy(alpha = 0.7f),
                borderWidth = 2.dp,
                enabled = true
            )
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            val sortOptions = listOf(
                SortOrder.RECENT to stringResource(R.string.sort_recent),
                SortOrder.NEWEST to stringResource(R.string.sort_newest),
                SortOrder.TITLE to stringResource(R.string.sort_by_title),
                SortOrder.TITLE_DESC to stringResource(R.string.sort_by_title_desc),
                SortOrder.AUTHOR to stringResource(R.string.sort_by_author),
                SortOrder.GENRE to stringResource(R.string.sort_by_genre),
                SortOrder.TAG to stringResource(R.string.sort_by_tag)
            )

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortOptions.forEach { (option, optionLabel) ->
                        val isSelected = sortOrder == option
                        AssistChip(
                            onClick = {
                                onSortOrderChange(option)
                                menuExpanded = false
                            },
                            label = {
                                Text(
                                    text = optionLabel,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tabColor else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = if (isSelected) tabColor else MaterialTheme.colorScheme.onSurface
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = if (isSelected) tabColor else tabColor.copy(alpha = 0.5f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterChip(
    statusFilter: WorkStatus?,
    onStatusFilterChange: (WorkStatus?) -> Unit,
    selectedItem: NavigationItem,
    tabColor: Color = MaterialTheme.colorScheme.primary,
    tabBackgroundColor: Color = tabColor.copy(alpha = 0.25f),
    iconTextColor: Color = Color.Unspecified
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val statusItems = when (selectedItem) {
        NavigationItem.Anime, NavigationItem.TVSeries -> listOf(
            null to stringResource(R.string.filter_all),
            WorkStatus.IN_PLANS to stringResource(R.string.in_plans),
            WorkStatus.WATCHING to stringResource(R.string.watching),
            WorkStatus.WATCHED to stringResource(R.string.watched),
            WorkStatus.ABANDONED to stringResource(R.string.abandoned)
        )
        NavigationItem.Books, NavigationItem.Manga -> listOf(
            null to stringResource(R.string.filter_all),
            WorkStatus.IN_PLANS to stringResource(R.string.in_plans),
            WorkStatus.READING to stringResource(R.string.reading),
            WorkStatus.READ to stringResource(R.string.read),
            WorkStatus.ABANDONED to stringResource(R.string.abandoned)
        )
        else -> emptyList()
    }

    if (statusItems.isEmpty()) return

    val currentLabel = statusItems.firstOrNull { it.first == statusFilter }?.second
        ?: stringResource(R.string.filter_all)
    val currentChipColor = statusFilter?.let { workStatusColor(it) } ?: tabColor

    Box {
        AssistChip(
            onClick = { menuExpanded = true },
            label = {
                Text(
                    text = currentLabel,
                    color = currentChipColor,
                    fontWeight = FontWeight.Medium
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = currentChipColor
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color.Transparent,
                labelColor = currentChipColor
            ),
            border = AssistChipDefaults.assistChipBorder(
                borderColor = if (statusFilter != null) currentChipColor else tabColor.copy(alpha = 0.7f),
                borderWidth = 2.dp,
                enabled = true
            )
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
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
                    statusItems.forEach { (st, itemLabel) ->
                        val isSelected = statusFilter == st
                        val itemColor = if (st != null) workStatusColor(st) else tabColor

                        AssistChip(
                            onClick = {
                                onStatusFilterChange(st)
                                menuExpanded = false
                            },
                            label = {
                                Text(
                                    text = itemLabel,
                                    color = itemColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = itemColor
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
