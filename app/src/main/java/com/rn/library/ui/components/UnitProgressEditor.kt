package com.rn.library.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rn.library.R
import com.rn.library.data.UnitProgress
import com.rn.library.data.WorkType
import com.rn.library.ui.theme.FieldHighlightColor

@Composable
fun UnitProgressEditor(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    units: List<UnitProgress>,
    onUnitsChange: (List<UnitProgress>) -> Unit,
    workType: WorkType,
    unitCountHint: Int?,
    fieldBg: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    val supportsUnits = workType in listOf(WorkType.BOOK, WorkType.MANGA, WorkType.SERIES, WorkType.ANIME)
    if (!supportsUnits) return

    val unitPrefix = unitPrefixLabel(workType)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.progress_by_units), color = labelColor, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.unit_progress_hint),
                    color = labelColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        if (enabled) {
            if (unitCountHint != null && unitCountHint > 0) {
                OutlinedButton(
                    onClick = {
                        onUnitsChange(buildDefaultUnits(unitCountHint, unitPrefix))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.generate_units_from_count, unitCountHint))
                }
            }

            units.forEachIndexed { index, unit ->
                UnitProgressRow(
                    unit = unit,
                    onChange = { updated ->
                        onUnitsChange(units.toMutableList().also { it[index] = updated })
                    },
                    onRemove = {
                        onUnitsChange(units.toMutableList().also { it.removeAt(index) })
                    },
                    fieldBg = fieldBg,
                    labelColor = labelColor,
                    workType = workType
                )
            }

            TextButton(
                onClick = {
                    val nextIndex = units.size + 1
                    onUnitsChange(
                        units + UnitProgress(unitName = "$unitPrefix $nextIndex", completed = 0.0, total = null)
                    )
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = labelColor)
                Text(stringResource(R.string.add_unit), color = labelColor)
            }
        }
    }
}

@Composable
private fun UnitProgressRow(
    unit: UnitProgress,
    onChange: (UnitProgress) -> Unit,
    onRemove: () -> Unit,
    fieldBg: Color,
    labelColor: Color,
    workType: WorkType
) {
    val completedLabel = when (workType) {
        WorkType.SERIES -> stringResource(R.string.watched)
        else -> stringResource(R.string.unit_completed)
    }
    val accentColor = FieldHighlightColor()
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = fieldBg,
        unfocusedContainerColor = fieldBg,
        disabledContainerColor = fieldBg,
        errorContainerColor = fieldBg,
        focusedTextColor = labelColor,
        unfocusedTextColor = labelColor,
        disabledTextColor = labelColor,
        focusedLabelColor = accentColor,
        unfocusedLabelColor = labelColor.copy(alpha = 0.7f),
        focusedPlaceholderColor = labelColor.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = labelColor.copy(alpha = 0.5f),
        cursorColor = accentColor,
        focusedIndicatorColor = accentColor,
        unfocusedIndicatorColor = labelColor.copy(alpha = 0.3f)
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = unit.unitName,
                onValueChange = { onChange(unit.copy(unitName = it)) },
                label = { Text(stringResource(R.string.unit_name)) },
                modifier = Modifier
                    .weight(1f),
                singleLine = true,
                colors = colors
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), tint = labelColor)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnitProgressDecimalField(
                fieldKey = "${unit.unitName}_completed",
                numericValue = unit.completed,
                onNumericChange = { onChange(unit.copy(completed = it ?: 0.0)) },
                emptyAsZero = true,
                label = completedLabel,
                modifier = Modifier.weight(1f),
                colors = colors,
            )
            UnitProgressDecimalField(
                fieldKey = "${unit.unitName}_total",
                numericValue = unit.total,
                onNumericChange = { onChange(unit.copy(total = it)) },
                emptyAsZero = false,
                label = stringResource(R.string.unit_total),
                modifier = Modifier.weight(1f),
                colors = colors,
            )
        }
    }
}

@Composable
private fun UnitProgressDecimalField(
    fieldKey: String,
    numericValue: Double?,
    onNumericChange: (Double?) -> Unit,
    emptyAsZero: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.TextFieldColors,
) {
    var text by remember(fieldKey) {
        mutableStateOf(formatEditableUnitNumber(numericValue, emptyAsZero))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(numericValue) {
        if (isFocused) return@LaunchedEffect
        val parsed = text.trim().toDoubleOrNull()
        val matches = when {
            text.isBlank() -> numericValue == null || (emptyAsZero && numericValue == 0.0)
            else -> parsed == numericValue
        }
        if (!matches) {
            text = formatEditableUnitNumber(numericValue, emptyAsZero)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = filterUnitDecimalInput(raw)
            text = filtered
            onNumericChange(parseUnitDecimalInput(filtered, emptyAsZero))
        },
        label = { Text(label) },
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = colors,
    )
}

/** Пустое поле = 0 (прочитано) или null (всего). */
internal fun formatEditableUnitNumber(value: Double?, emptyAsZero: Boolean): String {
    if (value == null || (emptyAsZero && value == 0.0)) return ""
    return formatUnitNumber(value)
}

internal fun filterUnitDecimalInput(input: String): String {
    val filtered = input.filter { it.isDigit() || it == '.' }
    val parts = filtered.split('.')
    return if (parts.size > 2) {
        parts[0] + "." + parts.drop(1).joinToString("")
    } else {
        filtered
    }
}

internal fun parseUnitDecimalInput(text: String, emptyAsZero: Boolean): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return if (emptyAsZero) 0.0 else null
    return trimmed.toDoubleOrNull()?.coerceAtLeast(0.0)
}

@Composable
private fun unitPrefixLabel(type: WorkType): String = when (type) {
    WorkType.BOOK, WorkType.MANGA -> stringResource(R.string.volume_unit_prefix)
    WorkType.SERIES, WorkType.ANIME -> stringResource(R.string.season_unit_prefix)
}

fun buildDefaultUnits(count: Int, prefix: String): List<UnitProgress> =
    (1..count).map { index -> UnitProgress(unitName = "$prefix $index", completed = 0.0, total = null) }

private fun formatUnitNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
