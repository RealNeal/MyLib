package com.rn.library.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import com.rn.library.R
import com.rn.library.ui.screens.AppTheme
import com.rn.library.ui.theme.FieldHighlightColor
import com.rn.library.ui.theme.IconTextColor
import com.rn.library.ui.theme.SearchBarColor
import com.rn.library.ui.theme.TitleColorBetween

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    query: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    currentTheme: AppTheme = AppTheme.DARK,
    iconOnly: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    autoFocus: Boolean = false,
    enabled: Boolean = true,
    trailingAction: (@Composable () -> Unit)? = null
) {
    val iconColor = IconTextColor()
    val textColor = TitleColorBetween()
    val baseSearchBarColor = SearchBarColor()
    
    // Adjust colors based on theme
    val searchBarColor = if (currentTheme == AppTheme.LIGHT) {
        // Light theme: slightly lighter background
        Color(
            red = kotlin.math.min(1f, baseSearchBarColor.red + 0.05f),
            green = kotlin.math.min(1f, baseSearchBarColor.green + 0.05f),
            blue = kotlin.math.min(1f, baseSearchBarColor.blue + 0.05f)
        )
    } else {
        // Dark theme: slightly lighter to stand out
        Color(
            red = kotlin.math.min(1f, baseSearchBarColor.red * 1.15f),
            green = kotlin.math.min(1f, baseSearchBarColor.green * 1.15f),
            blue = kotlin.math.min(1f, baseSearchBarColor.blue * 1.15f)
        )
    }
    
    val borderColor = if (currentTheme == AppTheme.DARK) {
        Color(0xFF232323) // Темная тема: #232323
    } else {
        Color(0xFFCCCCCC) // Светлая тема: серая окантовка
    }
    
    var internalQuery by remember { mutableStateOf("") }
    val searchQuery = query ?: internalQuery

    var internalExpanded by remember { mutableStateOf(false) }
    val isSearchExpanded = expanded ?: internalExpanded

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Request focus when search is expanded or autoFocus is requested
    LaunchedEffect(isSearchExpanded, autoFocus, enabled) {
        if (!enabled) {
            keyboardController?.hide()
            return@LaunchedEffect
        }
        if (autoFocus || (isSearchExpanded && iconOnly)) {
            delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        } else if (!isSearchExpanded && iconOnly) {
            keyboardController?.hide()
        }
    }

    if (iconOnly && !isSearchExpanded) {
        // Icon-only mode
        IconButton(
            onClick = { 
                if (expanded != null) onExpandedChange(true) else internalExpanded = true
            },
            enabled = enabled,
            modifier = modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        // Full search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { newValue ->
                if (query == null) internalQuery = newValue
                onSearchQueryChange(newValue)
            },
            enabled = enabled,
            modifier = modifier
                .height(56.dp)
                .focusRequester(focusRequester)
                .border(
                    width = 2.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(28.dp)
                ),
            placeholder = {
                Text(
                    text = stringResource(R.string.search),
                    color = textColor.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconOnly && isSearchExpanded) {
                        // Close button when in icon-only mode
                        IconButton(
                            onClick = {
                                if (expanded != null) onExpandedChange(false) else internalExpanded = false
                                if (query == null) internalQuery = ""
                                onSearchQueryChange("")
                                keyboardController?.hide()
                            },
                            enabled = enabled
                        ) {
                            Text(
                                text = "×",
                                color = iconColor.copy(alpha = 0.6f),
                                fontSize = 20.sp
                            )
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (query == null) internalQuery = ""
                                onSearchQueryChange("")
                            },
                            enabled = enabled
                        ) {
                            Text(
                                text = "×",
                                color = iconColor.copy(alpha = 0.6f),
                                fontSize = 20.sp
                            )
                        }
                    }
                    trailingAction?.invoke()
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = searchBarColor,
                unfocusedContainerColor = searchBarColor,
                disabledContainerColor = searchBarColor,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                cursorColor = FieldHighlightColor(),
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                disabledTextColor = textColor.copy(alpha = 0.38f),
                focusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = textColor.copy(alpha = 0.6f),
                disabledPlaceholderColor = textColor.copy(alpha = 0.38f)
            ),
            shape = RoundedCornerShape(28.dp),
            singleLine = true
        )
    }
}
