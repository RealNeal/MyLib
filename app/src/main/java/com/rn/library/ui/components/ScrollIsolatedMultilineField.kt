package com.rn.library.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize

/**
 * Многострочное поле с внутренней прокруткой: изолирует свайпы внутри себя,
 * не передавая оверскролл родительской форме.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollIsolatedMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    placeholder: @Composable (() -> Unit)? = null,
    parentScrollState: ScrollState? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    var frozenScroll by remember { mutableIntStateOf(0) }

    if (parentScrollState != null) {
        LaunchedEffect(parentScrollState, isFocused) {
            if (!isFocused) return@LaunchedEffect
            snapshotFlow { parentScrollState.value }
                .collect { current ->
                    if (current != frozenScroll) {
                        parentScrollState.scrollTo(frozenScroll)
                    }
                }
        }
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value))
    }
    SideEffect {
        if (textFieldValue.text != value) {
            val selection = textFieldValue.selection
            val clampedSelection = if (selection.start <= value.length && selection.end <= value.length) {
                selection
            } else {
                TextRange(value.length)
            }
            textFieldValue = TextFieldValue(text = value, selection = clampedSelection)
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Изоляция ручного скролла: поглощаем весь "остаточный" скролл,
    // чтобы он не дергал родительский ScrollState (экрана), когда текст кончается.
    val fieldNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Возвращаем available, говоря системе "мы сами обработали этот остаток"
                return available
            }
        }
    }
    val bringIntoViewResponder = remember {
        object : BringIntoViewResponder {
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // Поглощаем запрос bringIntoView при фокусе, чтобы родительский verticalScroll не дёргался
            }

            override fun calculateRectForParent(localRect: Rect): Rect {
                // Курсор в длинном тексте может иметь localRect далеко за пределами видимой области
                // (координаты всего содержимого). Передаём родителю только границы контейнера поля,
                // чтобы форма не пыталась прокрутиться к строке внутри поля — это делает сам TextField.
                val size = containerSize
                return if (size != IntSize.Zero) {
                    Rect(Offset.Zero, Size(size.width.toFloat(), size.height.toFloat()))
                } else {
                    localRect
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { containerSize = it.size }
            .bringIntoViewResponder(bringIntoViewResponder)
            .then(
                if (parentScrollState != null) {
                    Modifier.pointerInput(parentScrollState) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            frozenScroll = parentScrollState.value
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        OutlinedTextField(
            value = textFieldValue, // Передаем объект с памятью о курсоре
            onValueChange = { newValue ->
                textFieldValue = newValue // Сохраняем курсор локально
                // Отправляем наверх только текст, как и ожидает родительский экран
                if (newValue.text != value) {
                    onValueChange(newValue.text)
                }
            },
            label = label,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(fieldNestedScrollConnection)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        frozenScroll = parentScrollState?.value ?: frozenScroll
                        isFocused = true
                    } else {
                        isFocused = false
                    }
                },
            singleLine = false,
            minLines = minLines,
            maxLines = maxLines,
            interactionSource = interactionSource,
            colors = colors,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
        )
    }
}

/**
 * Заглушка, оставленная чтобы не ломать вызовы в других файлах (например, AddWorkScreen.kt).
 */
@Composable
fun rememberBlockParentScrollOnFocusedField(blockParentScroll: Boolean): NestedScrollConnection {
    return remember {
        object : NestedScrollConnection {}
    }
}