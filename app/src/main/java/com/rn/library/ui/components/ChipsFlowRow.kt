package com.rn.library.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A robust, zero-dependency wrapping row layout for chips.
 * Unlike ExperimentalLayoutApi/FlowRow, this uses standard Compose Layout
 * and avoids binary mismatch / NoSuchMethodError across Compose runtime versions.
 */
@Composable
fun ChipsFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val hSpacing = horizontalSpacing.roundToPx()
        val vSpacing = verticalSpacing.roundToPx()

        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }

        val lines = mutableListOf<List<Placeable>>()
        var currentLine = mutableListOf<Placeable>()
        var currentLineWidth = 0

        for (placeable in placeables) {
            val spaceNeeded = if (currentLine.isEmpty()) placeable.width else currentLineWidth + hSpacing + placeable.width
            if (currentLine.isNotEmpty() && spaceNeeded > constraints.maxWidth) {
                lines.add(currentLine)
                currentLine = mutableListOf(placeable)
                currentLineWidth = placeable.width
            } else {
                currentLine.add(placeable)
                currentLineWidth = spaceNeeded
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        val totalHeight = lines.sumOf { line -> line.maxOfOrNull { it.height } ?: 0 } +
                (if (lines.size > 1) (lines.size - 1) * vSpacing else 0)
        val calculatedWidth = lines.maxOfOrNull { line ->
            line.sumOf { it.width } + (line.size - 1).coerceAtLeast(0) * hSpacing
        } ?: 0

        val finalWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth.coerceAtLeast(constraints.minWidth)
        } else {
            calculatedWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        }

        layout(
            width = finalWidth,
            height = totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            var y = 0
            for (line in lines) {
                val lineHeight = line.maxOfOrNull { it.height } ?: 0
                var x = 0
                for (placeable in line) {
                    val yOffset = y + (lineHeight - placeable.height) / 2
                    placeable.placeRelative(x, yOffset)
                    x += placeable.width + hSpacing
                }
                y += lineHeight + vSpacing
            }
        }
    }
}
