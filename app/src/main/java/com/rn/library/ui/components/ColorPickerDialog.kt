package com.rn.library.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import com.rn.library.ui.AppSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rn.library.R
import com.rn.library.ui.theme.AppButtonColor

/**
 * Круглая кнопка с радужным спектром (конический/sweep градиент),
 * соответствующая Скриншоту 2.
 */
@Composable
fun RainbowColorWheelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp
) {
    val rainbowColors = remember {
        listOf(
            Color(0xFFFF0000),
            Color(0xFFFF7F00),
            Color(0xFFFFFF00),
            Color(0xFF00FF00),
            Color(0xFF00FFFF),
            Color(0xFF0000FF),
            Color(0xFF8B00FF),
            Color(0xFFFF007F),
            Color(0xFFFF0000)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(Brush.sweepGradient(rainbowColors))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
    )
}

/**
 * 2D-диалог выбора цвета с Saturation-Value плоскостью, ползунком тона (Hue),
 * круговым превью, HEX-полем ввода и кнопками «Отмена»/«Применить»,
 * в точности соответствующий Скриншоту 1.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColorArgb: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val hsvInit = remember(initialColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColorArgb, it) }
    }
    var hue by remember(initialColorArgb) { mutableFloatStateOf(hsvInit[0]) }
    var sat by remember(initialColorArgb) { mutableFloatStateOf(hsvInit[1]) }
    var value by remember(initialColorArgb) { mutableFloatStateOf(hsvInit[2]) }

    fun currentArgb(): Int = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))

    var hexText by remember(initialColorArgb) {
        val hex = "%06X".format(initialColorArgb and 0xFFFFFF)
        mutableStateOf(hex)
    }

    val keyboardController = LocalSoftwareKeyboardController.current

    fun syncHexFromHsv() {
        val hex = "%06X".format(currentArgb() and 0xFFFFFF)
        hexText = hex
    }

    fun applyHex(raw: String) {
        val clean = raw.trim().removePrefix("#").trim()
        if (clean.length == 6) {
            try {
                val parsed = AndroidColor.parseColor("#$clean")
                val hsv = FloatArray(3)
                AndroidColor.colorToHSV(parsed, hsv)
                hue = hsv[0]
                sat = hsv[1]
                value = hsv[2]
            } catch (_: Exception) {}
        }
    }

    val isDarkBg = (0.2126f * backgroundColor.red + 0.7152f * backgroundColor.green + 0.0722f * backgroundColor.blue) < 0.5f
    val fieldBg = if (isDarkBg) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val fieldBorder = if (isDarkBg) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 380.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Заголовок (например: "Цвет фона", "Цвет текста", "Цвет статистики")
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                // 2D Saturation-Value блок с кружком-манипулятором
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(hue) {
                                detectTapGestures { offset ->
                                    val s = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val v = (1f - offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    sat = s
                                    value = v
                                    syncHexFromHsv()
                                }
                            }
                            .pointerInput(hue) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val s = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val v = (1f - change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    sat = s
                                    value = v
                                    syncHexFromHsv()
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height

                        // 1. Горизонтальный градиент: Белый -> Чистый тон (Hue)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.White, Color.hsv(hue, 1f, 1f)),
                                startX = 0f,
                                endX = w
                            )
                        )
                        // 2. Вертикальный градиент: Прозрачный -> Черный
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = h
                            )
                        )

                        // 3. Кружок-индикатор текущей насыщенности и яркости
                        val selX = (sat * w).coerceIn(0f, w)
                        val selY = ((1f - value) * h).coerceIn(0f, h)

                        // Тень кружка
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = 7.dp.toPx(),
                            center = Offset(selX, selY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        // Белое кольцо-маркер
                        drawCircle(
                            color = Color.White,
                            radius = 6.dp.toPx(),
                            center = Offset(selX, selY),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Строка: Круглое превью текущего цвета + Горизонтальный радужный ползунок Hue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Превью выбранного цвета
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(currentArgb()))
                            .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    )

                    // Радужный спектральный ползунок тона
                    val rainbowGradient = remember {
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        val hFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        hue = (hFraction * 360f).coerceIn(0f, 359.9f)
                                        syncHexFromHsv()
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val hFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        hue = (hFraction * 360f).coerceIn(0f, 359.9f)
                                        syncHexFromHsv()
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height

                            drawRect(
                                brush = Brush.horizontalGradient(rainbowGradient, startX = 0f, endX = w)
                            )

                            // Маркер ползунка: круг с белой рамкой и цветом текущего Hue
                            val thumbX = ((hue / 360f) * w).coerceIn(0f, w)
                            drawCircle(
                                color = Color.White,
                                radius = 11.dp.toPx(),
                                center = Offset(thumbX, h / 2f),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                            drawCircle(
                                color = Color.hsv(hue, 1f, 1f),
                                radius = 8.5.dp.toPx(),
                                center = Offset(thumbX, h / 2f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Поле ввода HEX
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexText = cleaned
                        applyHex(cleaned)
                    },
                    label = { Text("HEX", color = Color(0xFFA6BAC0), fontSize = 12.sp) },
                    prefix = {
                        Text(
                            "# ",
                            color = Color(0xFFA6BAC0),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            applyHex(hexText)
                            keyboardController?.hide()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = fieldBg,
                        unfocusedContainerColor = fieldBg,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = fieldBorder,
                        focusedTextColor = contentColor,
                        unfocusedTextColor = contentColor,
                        cursorColor = contentColor
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Кнопки: «Отмена» и «Применить»
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = fieldBg,
                            contentColor = contentColor
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            onColorSelected(currentArgb())
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD6E4E5),
                            contentColor = Color(0xFF102024)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.apply),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Диалоговое окно настроек цвета приложения, открывающееся из верхнего меню:
 * - Переключатель динамических цветов Material You
 * - Настройка цвета приложения/фона (с пипеткой 2D как на Скриншоте 1)
 * - Настройка цвета текста (со спектральным кружком как на Скриншоте 2)
 * - Настройка цвета статистики (со спектральным кружком)
 * - Сброс к значениям по умолчанию
 */
@Composable
fun ColorSettingsDialog(
    onDismissRequest: () -> Unit,
    dynamicColorsEnabled: Boolean,
    onDynamicColorsEnabledChange: (Boolean) -> Unit,
    useCustomIconColor: Boolean = false,
    onUseCustomIconColorChange: (Boolean) -> Unit = {},
    customIconColorArgb: Int = 0xFFEAEAEA.toInt(),
    onCustomIconColorChange: (Int) -> Unit = {},
    useCustomBgColor: Boolean = false,
    onUseCustomBgColorChange: (Boolean) -> Unit = {},
    customBgColorArgb: Int = 0,
    onCustomBgColorArgbChange: (Int) -> Unit = {},
    useCustomTextColor: Boolean,
    onUseCustomTextColorChange: (Boolean) -> Unit,
    customTextColorArgb: Int,
    onCustomTextColorArgbChange: (Int) -> Unit,
    useCustomStatsColor: Boolean,
    onUseCustomStatsColorChange: (Boolean) -> Unit,
    customStatsArgb: Int,
    onCustomStatsArgbChange: (Int) -> Unit,
    useCustomButtonColor: Boolean = false,
    onUseCustomButtonColorChange: (Boolean) -> Unit = {},
    customButtonColorArgb: Int = 0xFF6750A4.toInt(),
    onCustomButtonColorArgbChange: (Int) -> Unit = {},
    onResetToDefaults: () -> Unit
) {
    var activePickerTarget by remember { mutableStateOf<ColorTarget?>(null) }

    val dialogBgColor = MaterialTheme.colorScheme.surface
    val contentTextColor = if (useCustomTextColor) Color(customTextColorArgb) else MaterialTheme.colorScheme.onSurface
    val isDarkBg = (0.2126f * dialogBgColor.red + 0.7152f * dialogBgColor.green + 0.0722f * dialogBgColor.blue) < 0.5f
    val itemRowBgColor = if (isDarkBg) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val subtextColor = contentTextColor.copy(alpha = 0.65f)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBgColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Шапка с заголовком и кнопкой закрытия
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_color_settings),
                        color = contentTextColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            tint = contentTextColor.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Строка Material You
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(itemRowBgColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.dynamic_color_material_you),
                            color = contentTextColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Switch(
                        checked = dynamicColorsEnabled,
                        onCheckedChange = onDynamicColorsEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = contentTextColor.copy(alpha = 0.7f),
                            uncheckedTrackColor = itemRowBgColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.custom_colors),
                    color = subtextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )

                // 1. Настройка цвета иконок (всех иконок в приложении)
                ColorSettingItemRow(
                    label = stringResource(R.string.icon_color_title),
                    currentColorArgb = customIconColorArgb,
                    isEnabled = !dynamicColorsEnabled,
                    backgroundColor = itemRowBgColor,
                    textColor = contentTextColor,
                    onClick = { activePickerTarget = ColorTarget.ICONS }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Настройка цвета текста
                ColorSettingItemRow(
                    label = stringResource(R.string.text_color_title),
                    currentColorArgb = customTextColorArgb,
                    isEnabled = !dynamicColorsEnabled,
                    backgroundColor = itemRowBgColor,
                    textColor = contentTextColor,
                    onClick = { activePickerTarget = ColorTarget.TEXT }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Настройка цвета статистики
                ColorSettingItemRow(
                    label = stringResource(R.string.stats_color_title),
                    currentColorArgb = customStatsArgb,
                    isEnabled = !dynamicColorsEnabled,
                    backgroundColor = itemRowBgColor,
                    textColor = contentTextColor,
                    onClick = { activePickerTarget = ColorTarget.STATS }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Настройка цвета кнопок
                ColorSettingItemRow(
                    label = stringResource(R.string.button_color_title),
                    currentColorArgb = customButtonColorArgb,
                    isEnabled = !dynamicColorsEnabled,
                    backgroundColor = itemRowBgColor,
                    textColor = contentTextColor,
                    onClick = { activePickerTarget = ColorTarget.BUTTONS }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Кнопка сброса к значениям по умолчанию
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onResetToDefaults()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.reset_to_default),
                            color = Color(0xFFFF9E9E),
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppButtonColor()
                        )
                    ) {
                        Text(
                            text = "OK",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // Всплывающий 2D-пикер цвета для выбранной категории
    activePickerTarget?.let { target ->
        when (target) {
            ColorTarget.ICONS -> {
                ColorPickerDialog(
                    title = stringResource(R.string.icon_color_title),
                    initialColorArgb = customIconColorArgb,
                    backgroundColor = dialogBgColor,
                    contentColor = contentTextColor,
                    onDismiss = { activePickerTarget = null },
                    onColorSelected = { newArgb ->
                        onUseCustomIconColorChange(true)
                        onCustomIconColorChange(newArgb)
                    }
                )
            }
            ColorTarget.TEXT -> {
                ColorPickerDialog(
                    title = stringResource(R.string.text_color_title),
                    initialColorArgb = customTextColorArgb,
                    backgroundColor = dialogBgColor,
                    contentColor = contentTextColor,
                    onDismiss = { activePickerTarget = null },
                    onColorSelected = { newArgb ->
                        onUseCustomTextColorChange(true)
                        onCustomTextColorArgbChange(newArgb)
                    }
                )
            }
            ColorTarget.STATS -> {
                ColorPickerDialog(
                    title = stringResource(R.string.stats_color_title),
                    initialColorArgb = customStatsArgb,
                    backgroundColor = dialogBgColor,
                    contentColor = contentTextColor,
                    onDismiss = { activePickerTarget = null },
                    onColorSelected = { newArgb ->
                        onUseCustomStatsColorChange(true)
                        onCustomStatsArgbChange(newArgb)
                    }
                )
            }
            ColorTarget.BUTTONS -> {
                ColorPickerDialog(
                    title = stringResource(R.string.button_color_title),
                    initialColorArgb = customButtonColorArgb,
                    backgroundColor = dialogBgColor,
                    contentColor = contentTextColor,
                    onDismiss = { activePickerTarget = null },
                    onColorSelected = { newArgb ->
                        onUseCustomButtonColorChange(true)
                        onCustomButtonColorArgbChange(newArgb)
                    }
                )
            }
        }
    }
}

private enum class ColorTarget {
    ICONS,
    TEXT,
    STATS,
    BUTTONS
}

/**
 * Строка настройки одного цвета с надписью слева и спектральной кнопкой справа (Скриншот 2).
 */
@Composable
private fun ColorSettingItemRow(
    label: String,
    currentColorArgb: Int,
    isEnabled: Boolean,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = isEnabled, onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (isEnabled) textColor else textColor.copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Кружок текущего выбранного цвета
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(currentColorArgb))
                    .border(1.dp, textColor.copy(alpha = 0.35f), CircleShape)
            )

            // Радужная кнопка спектра (Скриншот 2)
            RainbowColorWheelButton(
                onClick = onClick,
                size = 36.dp
            )
        }
    }
}

/**
 * Всплывающее меню по клику на 3 вертикальные точки:
 * 1. «Режим отображения» -> подменю «Список» / «Блоки» (открывается рядом, не закрывая основное меню)
 * 2. «Цвет приложения» -> открывает ColorSettingsDialog
 */
@Composable
fun TopOptionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    isGridView: Boolean,
    onViewModeSelected: (Boolean) -> Unit,
    onOpenColorSettings: () -> Unit,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    var showViewModeSubmenu by remember { mutableStateOf(false) }
    var showSearchSourcesSubmenu by remember { mutableStateOf(false) }
    var searchOtherSources by remember { mutableStateOf(AppSettings.isSearchOtherSourcesEnabled(context)) }
    var showWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            searchOtherSources = AppSettings.isSearchOtherSourcesEnabled(context)
        }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showViewModeSubmenu = false
            showSearchSourcesSubmenu = false
            onDismissRequest()
        },
        shape = RoundedCornerShape(0.dp),
        containerColor = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = Modifier
            .padding(0.dp)
            .widthIn(max = 520.dp)
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier.padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Отдельное подменю «Режим отображения», открывающееся РЯДОМ как отдельное всплывающее меню
                if (showViewModeSubmenu) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.25f)),
                        modifier = Modifier.width(IntrinsicSize.Max)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Вариант 1: Список
                            val isListSelected = !isGridView
                            AssistChip(
                                onClick = {
                                    onViewModeSelected(false)
                                    showViewModeSubmenu = false
                                    onDismissRequest()
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.view_mode_list),
                                        fontWeight = if (isListSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isListSelected) activeColor else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ViewAgenda,
                                        contentDescription = null,
                                        tint = if (isListSelected) activeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = if (isListSelected) activeColor else MaterialTheme.colorScheme.onSurface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (isListSelected) activeColor else activeColor.copy(alpha = 0.5f),
                                    borderWidth = if (isListSelected) 2.dp else 1.dp,
                                    enabled = true
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Вариант 2: Блоки
                            val isGridSelected = isGridView
                            AssistChip(
                                onClick = {
                                    onViewModeSelected(true)
                                    showViewModeSubmenu = false
                                    onDismissRequest()
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.view_mode_grid),
                                        fontWeight = if (isGridSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isGridSelected) activeColor else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ViewModule,
                                        contentDescription = null,
                                        tint = if (isGridSelected) activeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = if (isGridSelected) activeColor else MaterialTheme.colorScheme.onSurface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (isGridSelected) activeColor else activeColor.copy(alpha = 0.5f),
                                    borderWidth = if (isGridSelected) 2.dp else 1.dp,
                                    enabled = true
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Отдельное подменю «Поиск в источниках», открывающееся РЯДОМ как отдельное всплывающее меню
                if (showSearchSourcesSubmenu) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.25f)),
                        modifier = Modifier.width(IntrinsicSize.Max)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Вариант 1: Включен
                            val isEnabled = searchOtherSources
                            AssistChip(
                                onClick = {
                                    if (!searchOtherSources) {
                                        showWarningDialog = true
                                    }
                                    showSearchSourcesSubmenu = false
                                    onDismissRequest()
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.search_sources_on),
                                        fontWeight = if (isEnabled) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isEnabled) activeColor else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (isEnabled) activeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = if (isEnabled) activeColor else MaterialTheme.colorScheme.onSurface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (isEnabled) activeColor else activeColor.copy(alpha = 0.5f),
                                    borderWidth = if (isEnabled) 2.dp else 1.dp,
                                    enabled = true
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Вариант 2: Выключен
                            val isDisabled = !searchOtherSources
                            AssistChip(
                                onClick = {
                                    if (searchOtherSources) {
                                        searchOtherSources = false
                                        AppSettings.setSearchOtherSourcesEnabled(context, false)
                                    }
                                    showSearchSourcesSubmenu = false
                                    onDismissRequest()
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.search_sources_off),
                                        fontWeight = if (isDisabled) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isDisabled) activeColor else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = if (isDisabled) activeColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color.Transparent,
                                    labelColor = if (isDisabled) activeColor else MaterialTheme.colorScheme.onSurface
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    borderColor = if (isDisabled) activeColor else activeColor.copy(alpha = 0.5f),
                                    borderWidth = if (isDisabled) 2.dp else 1.dp,
                                    enabled = true
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Отдельное основное меню
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, activeColor.copy(alpha = 0.25f)),
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Режим отображения (клик открывает/закрывает отдельное подменю рядом)
                        AssistChip(
                            onClick = {
                                showSearchSourcesSubmenu = false
                                showViewModeSubmenu = !showViewModeSubmenu
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.display_mode),
                                    fontWeight = if (showViewModeSubmenu) FontWeight.Bold else FontWeight.Medium,
                                    color = if (showViewModeSubmenu) activeColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewModule else Icons.Default.ViewAgenda,
                                    contentDescription = null,
                                    tint = activeColor
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = activeColor
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = if (showViewModeSubmenu) activeColor else MaterialTheme.colorScheme.onSurface
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = if (showViewModeSubmenu) activeColor else activeColor.copy(alpha = 0.5f),
                                borderWidth = if (showViewModeSubmenu) 2.dp else 1.dp,
                                enabled = true
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 2. Цвет приложения
                        AssistChip(
                            onClick = {
                                showViewModeSubmenu = false
                                showSearchSourcesSubmenu = false
                                onDismissRequest()
                                onOpenColorSettings()
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.app_color_settings),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = activeColor
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = activeColor.copy(alpha = 0.5f),
                                borderWidth = 1.dp,
                                enabled = true
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 3. Поиск в источниках (клик открывает/закрывает отдельное подменю рядом)
                        AssistChip(
                            onClick = {
                                showViewModeSubmenu = false
                                showSearchSourcesSubmenu = !showSearchSourcesSubmenu
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.search_sources_short_title),
                                    fontWeight = if (showSearchSourcesSubmenu) FontWeight.Bold else FontWeight.Medium,
                                    color = if (showSearchSourcesSubmenu) activeColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = activeColor
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = activeColor
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Transparent,
                                labelColor = if (showSearchSourcesSubmenu) activeColor else MaterialTheme.colorScheme.onSurface
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                borderColor = if (showSearchSourcesSubmenu) activeColor else activeColor.copy(alpha = 0.5f),
                                borderWidth = if (showSearchSourcesSubmenu) 2.dp else 1.dp,
                                enabled = true
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showWarningDialog) {
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(stringResource(R.string.search_other_sources_warning_title)) },
            text = { Text(stringResource(R.string.search_other_sources_warning_message)) },
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurfaceVariant,
            confirmButton = {
                TextButton(
                    onClick = {
                        searchOtherSources = true
                        AppSettings.setSearchOtherSourcesEnabled(context, true)
                        showWarningDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = AppButtonColor(),
                        contentColor = scheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.search_other_sources_enable))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWarningDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = scheme.onSurfaceVariant
                    )
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
