package dev.metro.launcher.ui.tiles

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.TileFrame

/**
 * Плитка приложения: адаптивный лейаут под 1x1, 2x1, 2x2 (стиль Windows Phone Metro).
 */
@Composable
fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = MetroDimens.tileH(1),
    colSpan: Int = 1,
    rowSpan: Int = 1,
    onLongPress: (() -> Unit)? = null,
) {
    val scheme = LocalMetroScheme.current
    val context = LocalContext.current
    val iconRevision by AppIconLoader.revisions.collectAsState()
    val bitmap by produceState(
        initialValue = AppIconLoader.cachedAppIcon(app.packageName, app),
        app.packageName,
        app.icon,
        iconRevision,
    ) {
        value = AppIconLoader.loadAppIcon(context, app.packageName, app)
    }

    TileFrame(
        onClick = onClick,
        modifier = modifier,
        width = width,
        height = height,
        onLongPress = onLongPress,
    ) {
        when {
            // Широкая плитка 2x1 или 4x1 (горизонтальная)
            colSpan >= 2 && rowSpan == 1 -> {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    bitmap?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(44.dp),
                        )
                    } ?: Box(modifier = Modifier.size(44.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = app.label,
                        color = scheme.text,
                        fontSize = 14.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Крупная плитка 2x2 или больше (Metro стиль: иконка по центру, подпись в нижнем углу)
            colSpan >= 2 && rowSpan >= 2 -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                ) {
                    bitmap?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(54.dp)
                                .align(Alignment.Center),
                        )
                    } ?: Box(
                        modifier = Modifier
                            .size(54.dp)
                            .align(Alignment.Center),
                    )
                    Text(
                        text = app.label,
                        color = scheme.text,
                        fontSize = 12.sp,
                        fontFamily = MetroFonts.text,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
            // Стандартная 1x1
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    bitmap?.let { icon ->
                        Image(
                            bitmap = icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(44.dp),
                        )
                    } ?: Box(modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = app.label,
                        color = scheme.text,
                        fontSize = 11.sp,
                        fontFamily = MetroFonts.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
