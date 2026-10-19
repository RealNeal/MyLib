package com.rn.library.ui.components

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rn.library.data.WorkStatus
import com.rn.library.data.toCoverImageData
import com.rn.library.data.WorkType
import com.rn.library.ui.theme.IconTextColor
import com.rn.library.ui.theme.MainBackgroundColor
import com.rn.library.ui.theme.TabCircleColor
import com.rn.library.ui.theme.WorkTitleColor

data class WorkItem(
    val id: String,
    val title: String,
    val imageUrl: String? = null,
    val meta: String,
    val metaLine1: String? = null,
    val metaLine2: String? = null,
    val description: String,
    val status: WorkStatus,
    val type: WorkType,
    val statusLabel: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkItemCard(
    workItem: WorkItem,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mainBackgroundColor = MainBackgroundColor()
    val iconTextColor = IconTextColor()
    val workTitleColor = WorkTitleColor()
    val isDarkBg = (mainBackgroundColor.red * 0.2126f + mainBackgroundColor.green * 0.7152f + mainBackgroundColor.blue * 0.0722f) < 0.5f
    val secondaryTextColor = if (isDarkBg) Color.White else Color.Black

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = mainBackgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),

        ) {
            // Обложка
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                if (!workItem.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(workItem.imageUrl.toCoverImageData())
                            .crossfade(false)
                            // Декодируем под реальный размер карточки, чтобы избежать заметной задержки.
                            .size(160, 240)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "📖",
                        fontSize = 32.sp
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val statusColor = when (workItem.status) {
                    WorkStatus.IN_PLANS -> Color(0xFF8E6687)
                    WorkStatus.ABANDONED -> Color(0xFFFF5F5A)
                    WorkStatus.READING, WorkStatus.WATCHING -> Color(0xFF7179A4)
                    WorkStatus.READ, WorkStatus.WATCHED -> Color(0xFF79C77C)
                }

                // Title
                Text(
                    text = workItem.title,
                    color = workTitleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metaCharsNoSpaces =
                    (workItem.metaLine1?.filterNot { it.isWhitespace() }?.length ?: 0) +
                        (workItem.metaLine2?.filterNot { it.isWhitespace() }?.length ?: 0)
                val isLongMeta = if (workItem.type == WorkType.SERIES) {
                    metaCharsNoSpaces >= 13
                } else {
                    metaCharsNoSpaces > 15
                }
                val metaCombined = listOfNotNull(
                    workItem.metaLine1?.takeIf { it.isNotBlank() },
                    workItem.metaLine2?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")

                // Meta (two lines) + status close to it on the right.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        // Take remaining width, but don't force stretching to the right edge
                        // (so the status stays visually close to the meta).
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (!isLongMeta) {
                            Text(
                                text = metaCombined,
                                color = secondaryTextColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            workItem.metaLine1?.takeIf { it.isNotBlank() }?.let { line ->
                                Text(
                                    text = line,
                                    color = secondaryTextColor,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            workItem.metaLine2?.takeIf { it.isNotBlank() }?.let { line ->
                                Text(
                                    text = line,
                                    color = secondaryTextColor,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = workItem.statusLabel,
                        color = statusColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Description
                Text(
                    text = workItem.description,
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                    maxLines = 3,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkItemGridCard(
    workItem: WorkItem,
    dynamicColorsEnabled: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val mainBackgroundColor = MainBackgroundColor()
    val context = LocalContext.current
    val titleColor = WorkTitleColor()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = mainBackgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Немного уменьшаем внутренние отступы, чтобы обложка занимала максимум ячейки.
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.BottomStart
            ) {
                if (!workItem.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(workItem.imageUrl.toCoverImageData())
                            .crossfade(false)
                            // Ограничиваем размер декодирования под плитку грида (2:3).
                            .size(320, 480)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Короткое название — по центру постера; длинное — бегущая строка без центрирования
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.0f),
                                    Color.Black.copy(alpha = 0.80f)
                                )
                            )
                        )
                        .clipToBounds()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = workItem.title,
                        color = titleColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        onTextLayout = { layoutResult ->
                            val overflow = layoutResult.didOverflowWidth
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                animationMode = MarqueeAnimationMode.Immediately,
                                velocity = 90.dp,
                                initialDelayMillis = 700,
                                repeatDelayMillis = 900
                            )
                    )
                }
            }

            // Статус под обложкой
            val statusColor = when (workItem.status) {
                WorkStatus.IN_PLANS -> Color(0xFF8E6687)
                WorkStatus.ABANDONED -> Color(0xFFFF5F5A)
                WorkStatus.READING, WorkStatus.WATCHING -> Color(0xFF7179A4)
                WorkStatus.READ, WorkStatus.WATCHED -> Color(0xFF79C77C)
            }.copy(alpha = 0.95f)

            Text(
                text = workItem.statusLabel,
                color = statusColor,
                // Делаем статус немного крупнее, чтобы он лучше читался
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
