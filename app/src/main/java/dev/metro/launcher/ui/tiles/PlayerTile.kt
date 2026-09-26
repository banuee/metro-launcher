package dev.metro.launcher.ui.tiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.data.PlayerRepository
import dev.metro.launcher.data.TrackInfo
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.MetroScheme
import dev.metro.launcher.ui.theme.TileFrame
import dev.metro.launcher.ui.theme.metroClickable

/**
 * Музыкальный виджет Metro:
 * Поддерживает адаптивные лейауты под все размеры (1x1, 2x1, 2x2, 4x1, 4x2+).
 * Чистая типографика Segoe, акриловые кнопки, обложка альбома, прогресс-бар трека
 * и название приложения-источника.
 */
@Composable
fun PlayerTile(
    repo: PlayerRepository,
    modifier: Modifier = Modifier,
    colSpan: Int = 2,
    rowSpan: Int = 2,
    width: Dp = Dp.Unspecified,
    height: Dp = MetroDimens.tileH(2),
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    val track by repo.track.collectAsState()
    val isEnabled = repo.isListenerEnabled()

    TileFrame(
        onClick = {
            if (!isEnabled) {
                repo.openListenerSettings()
            } else {
                repo.openPlayer()
            }
        },
        modifier = modifier,
        width = width,
        height = height,
        onLongPress = onLongPress,
    ) {
        when {
            // Компактный размер 1x1
            colSpan == 1 && rowSpan == 1 -> {
                PlayerTile1x1(
                    repo = repo,
                    track = track,
                    isEnabled = isEnabled,
                    scheme = scheme,
                )
            }

            // Горизонтальная полоса 2x1 или 3x1 (высота 1 ряд)
            colSpan in 2..3 && rowSpan == 1 -> {
                PlayerTile2x1(
                    repo = repo,
                    track = track,
                    isEnabled = isEnabled,
                    scheme = scheme,
                )
            }

            // Широкая полоса во всю ширину 4x1
            colSpan >= 4 && rowSpan == 1 -> {
                PlayerTile4x1(
                    repo = repo,
                    track = track,
                    isEnabled = isEnabled,
                    scheme = scheme,
                )
            }

            // Широкая большая карточка 3x2, 4x2 или больше
            colSpan >= 3 && rowSpan >= 2 -> {
                PlayerTileLarge(
                    repo = repo,
                    track = track,
                    isEnabled = isEnabled,
                    scheme = scheme,
                )
            }

            // Стандартный размер 2x2 (и вертикальные 1x2, 2x3 и т.д.)
            else -> {
                PlayerTile2x2(
                    repo = repo,
                    track = track,
                    isEnabled = isEnabled,
                    scheme = scheme,
                )
            }
        }
    }
}

/** 1x1: Компактный виджет — иконка/обложка, название и статус */
@Composable
private fun PlayerTile1x1(
    repo: PlayerRepository,
    track: TrackInfo?,
    isEnabled: Boolean,
    scheme: MetroScheme,
) {
    if (!isEnabled) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "\uF001",
                color = scheme.accent,
                fontFamily = MetroFonts.icon,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "ДОСТУП",
                color = scheme.textDim,
                fontSize = 9.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
            )
        }
        return
    }

    if (track == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "\uF001",
                color = scheme.accent,
                fontFamily = MetroFonts.icon,
                fontSize = 22.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Музыка",
                color = scheme.textDim,
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Обложка или мини-глиф
            if (track.albumArt != null) {
                Image(
                    bitmap = track.albumArt.asImageBitmap(),
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, scheme.stroke, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uF001",
                        color = scheme.accent,
                        fontFamily = MetroFonts.icon,
                        fontSize = 16.sp,
                    )
                }
            }

            Text(
                text = track.title,
                color = scheme.text,
                fontSize = 10.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            // Кнопка Play/Pause
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(scheme.accent.copy(alpha = 0.25f))
                    .border(1.dp, scheme.accent, CircleShape)
                    .metroClickable(targetScale = 0.85f) { repo.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (track.playing) "\uF04C" else "\uF04B",
                    color = Color.White,
                    fontFamily = MetroFonts.icon,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

/** 2x1: Горизонтальная аккуратная полоса */
@Composable
private fun PlayerTile2x1(
    repo: PlayerRepository,
    track: TrackInfo?,
    isEnabled: Boolean,
    scheme: MetroScheme,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isEnabled) {
                Text(
                    text = "Нужен доступ к уведомлениям",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ВКЛЮЧИТЬ",
                    color = scheme.accent,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.metroClickable(targetScale = 0.92f) { repo.openListenerSettings() },
                )
            } else if (track == null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uF001",
                        color = scheme.accent,
                        fontFamily = MetroFonts.icon,
                        fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ничего не играет",
                        color = scheme.text,
                        fontSize = 13.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Нажмите, чтобы открыть плеер",
                        color = scheme.textDim,
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.text,
                    )
                }
            } else {
                // Обложка трека
                if (track.albumArt != null) {
                    Image(
                        bitmap = track.albumArt.asImageBitmap(),
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.glassHover)
                            .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\uF001",
                            color = scheme.accent,
                            fontFamily = MetroFonts.icon,
                            fontSize = 20.sp,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Название и артист
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = track.title,
                        color = scheme.text,
                        fontSize = 13.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist.ifBlank { track.appLabel },
                        color = scheme.textDim,
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Кнопки управления (Play/Pause и Next)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(scheme.accent.copy(alpha = 0.22f))
                            .border(1.5.dp, scheme.accent, CircleShape)
                            .metroClickable(targetScale = 0.88f) { repo.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (track.playing) "\uF04C" else "\uF04B",
                            color = Color.White,
                            fontFamily = MetroFonts.icon,
                            fontSize = 15.sp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.glassHover)
                            .border(1.dp, scheme.stroke, CircleShape)
                            .metroClickable(targetScale = 0.85f) { repo.next() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\uF051",
                            color = scheme.text,
                            fontFamily = MetroFonts.icon,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Нижняя акцентная полоска прогресса
        if (track != null && track.durationMs > 0L) {
            val progress = (track.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(scheme.glassHover),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress)
                        .fillMaxHeight()
                        .background(scheme.accent),
                )
            }
        }
    }
}

/** 2x2: Главный сбалансированный виджет в стилистике Windows Phone / Metro */
@Composable
private fun PlayerTile2x2(
    repo: PlayerRepository,
    track: TrackInfo?,
    isEnabled: Boolean,
    scheme: MetroScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Шапка: акцентный бейдж "СЕЙЧАС ИГРАЕТ" + название приложения
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 11.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(scheme.accent),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "СЕЙЧАС ИГРАЕТ",
                    color = scheme.accent,
                    fontSize = 10.5.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
            }

            if (track != null && track.appLabel.isNotBlank()) {
                Text(
                    text = track.appLabel,
                    color = scheme.textDim,
                    fontSize = 10.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!isEnabled) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Нужен доступ к уведомлениям",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "ВКЛЮЧИТЬ",
                    color = scheme.accent,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.metroClickable(targetScale = 0.92f) { repo.openListenerSettings() },
                )
            }
        } else if (track == null) {
            // Состояние покоя: стильный акриловый диск
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.strokeStrong, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uF001",
                        color = scheme.accent,
                        fontFamily = MetroFonts.icon,
                        fontSize = 24.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ничего не играет",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Нажмите, чтобы включить",
                    color = scheme.accent,
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            // Средняя часть: Обложка альбома + Информация о треке
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track.albumArt != null) {
                    Image(
                        bitmap = track.albumArt.asImageBitmap(),
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.glassHover)
                            .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\uF001",
                            color = scheme.accent,
                            fontFamily = MetroFonts.icon,
                            fontSize = 26.sp,
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = track.title,
                        color = scheme.text,
                        fontSize = 14.5.sp,
                        fontFamily = MetroFonts.headline,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.artist.ifBlank { track.appLabel },
                        color = scheme.textDim,
                        fontSize = 11.5.sp,
                        fontFamily = MetroFonts.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Тонкая полоска прогресса трека
            if (track.durationMs > 0L) {
                val progress = (track.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(scheme.glassHover),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progress)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(scheme.accent),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(track.positionMs),
                            color = scheme.textDim,
                            fontSize = 9.sp,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = formatDuration(track.durationMs),
                            color = scheme.textDim,
                            fontSize = 9.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                }
            } else {
                // Если стрим без фиксированной длины — тонкая декоративная акцентная полоска
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(scheme.glassHover),
                )
            }

            // Кнопки управления воспроизведением
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Кнопка «Назад»
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, CircleShape)
                        .metroClickable(targetScale = 0.85f) { repo.prev() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uF048",
                        color = scheme.text,
                        fontFamily = MetroFonts.icon,
                        fontSize = 15.sp,
                    )
                }

                // Кнопка «Воспроизведение / Пауза»
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(scheme.accent.copy(alpha = 0.22f))
                        .border(1.5.dp, scheme.accent, CircleShape)
                        .metroClickable(targetScale = 0.88f) { repo.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (track.playing) "\uF04C" else "\uF04B",
                        color = Color.White,
                        fontFamily = MetroFonts.icon,
                        fontSize = 18.sp,
                    )
                }

                // Кнопка «Вперёд»
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, CircleShape)
                        .metroClickable(targetScale = 0.85f) { repo.next() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uF051",
                        color = scheme.text,
                        fontFamily = MetroFonts.icon,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** 4x1: Полноразмерная однострочная полоса */
@Composable
private fun PlayerTile4x1(
    repo: PlayerRepository,
    track: TrackInfo?,
    isEnabled: Boolean,
    scheme: MetroScheme,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isEnabled) {
            Text(
                text = "Нужен доступ к уведомлениям для управления плеером",
                color = scheme.textDim,
                fontSize = 13.sp,
                fontFamily = MetroFonts.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "ВКЛЮЧИТЬ",
                color = scheme.accent,
                fontSize = 13.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.metroClickable(targetScale = 0.92f) { repo.openListenerSettings() },
            )
        } else if (track == null) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\uF001",
                    color = scheme.accent,
                    fontFamily = MetroFonts.icon,
                    fontSize = 22.sp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Музыка не играет",
                    color = scheme.text,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Нажмите в любое место, чтобы открыть плеер",
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                )
            }
        } else {
            if (track.albumArt != null) {
                Image(
                    bitmap = track.albumArt.asImageBitmap(),
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\uF001",
                        color = scheme.accent,
                        fontFamily = MetroFonts.icon,
                        fontSize = 22.sp,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = track.title,
                    color = scheme.text,
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist.ifBlank { track.appLabel },
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, CircleShape)
                        .metroClickable(targetScale = 0.85f) { repo.prev() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "\uF048", color = scheme.text, fontFamily = MetroFonts.icon, fontSize = 15.sp)
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.accent.copy(alpha = 0.22f))
                        .border(1.5.dp, scheme.accent, CircleShape)
                        .metroClickable(targetScale = 0.88f) { repo.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (track.playing) "\uF04C" else "\uF04B",
                        color = Color.White,
                        fontFamily = MetroFonts.icon,
                        fontSize = 17.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, CircleShape)
                        .metroClickable(targetScale = 0.85f) { repo.next() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "\uF051", color = scheme.text, fontFamily = MetroFonts.icon, fontSize = 15.sp)
                }
            }
        }
    }
}

/** 3x2 / 4x2+: Большой мультимедийный виджет */
@Composable
private fun PlayerTileLarge(
    repo: PlayerRepository,
    track: TrackInfo?,
    isEnabled: Boolean,
    scheme: MetroScheme,
) {
    if (!isEnabled || track == null) {
        PlayerTile2x2(repo = repo, track = track, isEnabled = isEnabled, scheme = scheme)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Крупная обложка альбома слева
        if (track.albumArt != null) {
            Image(
                bitmap = track.albumArt.asImageBitmap(),
                contentDescription = track.title,
                modifier = Modifier
                    .size(96.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, scheme.strokeStrong, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.glassHover)
                    .border(1.dp, scheme.strokeStrong, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\uF001",
                    color = scheme.accent,
                    fontFamily = MetroFonts.icon,
                    fontSize = 42.sp,
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Правая колонка с информацией и контролами
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "СЕЙЧАС ИГРАЕТ",
                    color = scheme.accent,
                    fontSize = 11.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )
                if (track.appLabel.isNotBlank()) {
                    Text(
                        text = track.appLabel,
                        color = scheme.textDim,
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.text,
                    )
                }
            }

            Column {
                Text(
                    text = track.title,
                    color = scheme.text,
                    fontSize = 17.sp,
                    fontFamily = MetroFonts.headline,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist.ifBlank { track.appLabel },
                    color = scheme.textDim,
                    fontSize = 13.sp,
                    fontFamily = MetroFonts.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Прогресс
            if (track.durationMs > 0L) {
                val progress = (track.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(scheme.glassHover),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = progress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(scheme.accent),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatDuration(track.positionMs),
                            color = scheme.textDim,
                            fontSize = 10.sp,
                            fontFamily = MetroFonts.text,
                        )
                        Text(
                            text = formatDuration(track.durationMs),
                            color = scheme.textDim,
                            fontSize = 10.sp,
                            fontFamily = MetroFonts.text,
                        )
                    }
                }
            }

            // Кнопки
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, CircleShape)
                        .metroClickable(targetScale = 0.85f) { repo.prev() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "\uF048", color = scheme.text, fontFamily = MetroFonts.icon, fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(scheme.accent.copy(alpha = 0.22f))
                        .border(1.5.dp, scheme.accent, CircleShape)
                        .metroClickable(targetScale = 0.88f) { repo.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (track.playing) "\uF04C" else "\uF04B",
                        color = Color.White,
                        fontFamily = MetroFonts.icon,
                        fontSize = 19.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(scheme.glassHover)
                        .border(1.dp, scheme.stroke, CircleShape)
                        .metroClickable(targetScale = 0.85f) { repo.next() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "\uF051", color = scheme.text, fontFamily = MetroFonts.icon, fontSize = 16.sp)
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
