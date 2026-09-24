package dev.metro.launcher.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.data.PlayerRepository
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.TileFrame
import dev.metro.launcher.ui.theme.metroClickable

/**
 * Плеер 2x2: текущий трек + prev/play-next. Без доступа к уведомлениям —
 * кнопка открытия настроек. Без активной сессии — заглушка.
 */
@Composable
fun PlayerTile(
    repo: PlayerRepository,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = MetroDimens.tileH(2),
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    val track by repo.track.collectAsState()
    val currentTrack = track

    TileFrame(
        onClick = { },
        modifier = modifier,
        width = width,
        height = height,
        onLongPress = onLongPress,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "СЕЙЧАС ИГРАЕТ",
                color = scheme.accent,
                fontSize = 11.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            if (!repo.isListenerEnabled()) {
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
                    fontSize = 14.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.metroClickable(targetScale = 0.92f) { repo.openListenerSettings() },
                )
            } else if (currentTrack == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = "\uF001",
                        color = scheme.textDim,
                        fontFamily = MetroFonts.icon,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ничего не играет",
                        color = scheme.textDim,
                        fontSize = 13.sp,
                        fontFamily = MetroFonts.text,
                    )
                }
            } else {
                val t = currentTrack
                Text(
                    text = t.title,
                    color = scheme.text,
                    fontSize = 15.sp,
                    fontFamily = MetroFonts.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = t.artist,
                    color = scheme.textDim,
                    fontSize = 12.sp,
                    fontFamily = MetroFonts.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .metroClickable(targetScale = 0.82f) { repo.prev() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\uF048",
                            color = scheme.text,
                            fontFamily = MetroFonts.icon,
                            fontSize = 20.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                            .background(scheme.accent)
                            .metroClickable(targetScale = 0.88f) { repo.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (t.playing) "\uF04C" else "\uF04B",
                            color = Color.White,
                            fontFamily = MetroFonts.icon,
                            fontSize = 22.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .metroClickable(targetScale = 0.82f) { repo.next() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\uF051",
                            color = scheme.text,
                            fontFamily = MetroFonts.icon,
                            fontSize = 20.sp,
                        )
                    }
                }
            }
        }
    }
}
