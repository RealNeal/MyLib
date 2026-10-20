package com.rn.library.ui.screens

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Преобразует сырые цифры даты "DDMMYYYY" в "DD.MM.YYYY" визуально, не меняя реальное значение.
 * Поддерживает как одиночную дату, так и многострочный список дат (разделённый '\n').
 * Точка не смещает фокус курсора: при вводе 2-й цифры (дня) и 4-й цифры (месяца)
 * точка ставится автоматически, а курсор остаётся сразу за точкой.
 */
class DateVisualTransformation : VisualTransformation {

    private data class LineInfo(
        val rawStart: Int,
        val rawLength: Int,
        val transStart: Int,
        val transLength: Int
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text.replace("\r", "")
        val lines = mutableListOf<LineInfo>()
        val outBuilder = StringBuilder()

        var currentRawStart = 0
        var i = 0
        while (i <= rawText.length) {
            if (i == rawText.length || rawText[i] == '\n') {
                val rawLine = rawText.substring(currentRawStart, i)
                val transLine = formatLine(rawLine)
                val transStart = outBuilder.length
                outBuilder.append(transLine)
                val transLength = transLine.length
                lines.add(LineInfo(currentRawStart, rawLine.length, transStart, transLength))
                if (i < rawText.length) {
                    outBuilder.append('\n')
                }
                currentRawStart = i + 1
            }
            i++
        }

        val outString = outBuilder.toString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, rawText.length)
                val line = lines.lastOrNull { clamped >= it.rawStart } ?: lines.firstOrNull() ?: return 0
                val localOffset = (clamped - line.rawStart).coerceIn(0, line.rawLength)
                val localTrans = lineOriginalToTransformed(localOffset, line.rawLength, line.transLength)
                return (line.transStart + localTrans).coerceAtMost(outString.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, outString.length)
                val line = lines.lastOrNull { clamped >= it.transStart } ?: lines.firstOrNull() ?: return 0
                val localOffset = (clamped - line.transStart).coerceIn(0, line.transLength)
                val localOriginal = lineTransformedToOriginal(localOffset, line.rawLength, line.transLength)
                return (line.rawStart + localOriginal).coerceIn(0, rawText.length)
            }
        }

        return TransformedText(AnnotatedString(outString), offsetMapping)
    }

    private fun formatLine(rawLine: String): String {
        val trimmed = rawLine.take(8)
        val sb = StringBuilder()
        for (idx in trimmed.indices) {
            sb.append(trimmed[idx])
            if (idx == 1 || idx == 3) {
                sb.append('.')
            }
        }
        return sb.toString()
    }

    private fun lineOriginalToTransformed(offset: Int, rawLen: Int, transLen: Int): Int {
        val clamped = offset.coerceIn(0, rawLen)
        val transOffset = when {
            clamped <= 1 -> clamped
            clamped <= 3 -> clamped + 1
            else -> clamped + 2
        }
        return transOffset.coerceAtMost(transLen)
    }

    private fun lineTransformedToOriginal(offset: Int, rawLen: Int, transLen: Int): Int {
        val clamped = offset.coerceIn(0, transLen)
        val rawOffset = when {
            clamped <= 2 -> clamped
            clamped <= 5 -> clamped - 1
            else -> clamped - 2
        }
        return rawOffset.coerceIn(0, rawLen)
    }
}

