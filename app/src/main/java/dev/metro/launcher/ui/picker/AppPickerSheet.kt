package dev.metro.launcher.ui.picker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.metro.launcher.data.AppIconLoader
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.metroClickable

/**
 * Диалог выбора приложения для добавления на главный экран (полный Metro-стиль).
 */
@Composable
fun AppPickerSheet(
    apps: List<AppInfo>,
    onSelectApp: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = LocalMetroScheme.current
    val iconRevision by AppIconLoader.revisions.collectAsState()
    var query by remember { mutableStateOf("") }

    val filteredApps = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        FrostedGlassBox(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010).copy(alpha = 0.94f)),
            tint = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                // Заголовок в стиле Windows Phone Metro
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "ДОБАВИТЬ ЗНАЧОК",
                            color = scheme.text,
                            fontSize = 22.sp,
                            fontFamily = MetroFonts.headline,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 28.dp, height = 2.5.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(scheme.accent),
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = scheme.text,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Поле поиска приложений
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(MetroDimens.radius))
                        .background(scheme.glass)
                        .border(
                            width = 1.dp,
                            color = if (query.isNotEmpty()) scheme.accent else scheme.stroke,
                            shape = RoundedCornerShape(MetroDimens.radius),
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (query.isNotEmpty()) scheme.accent else scheme.textDim,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        textStyle = TextStyle(
                            color = scheme.text,
                            fontSize = 15.sp,
                            fontFamily = MetroFonts.text,
                        ),
                        cursorBrush = SolidColor(scheme.accent),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = "Поиск приложений…",
                                    color = scheme.textDim,
                                    fontSize = 15.sp,
                                    fontFamily = MetroFonts.text,
                                )
                            }
                            innerTextField()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = scheme.textDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Список приложений
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val bitmap by produceState(
                            initialValue = AppIconLoader.cachedAppIcon(app.packageName, app),
                            app.packageName,
                            app.icon,
                            iconRevision,
                        ) {
                            value = AppIconLoader.loadAppIcon(context, app.packageName, app)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(MetroDimens.radius))
                                .background(scheme.glass)
                                .border(
                                    width = 1.dp,
                                    color = scheme.stroke,
                                    shape = RoundedCornerShape(MetroDimens.radius),
                                )
                                .metroClickable(targetScale = 0.97f) {
                                    onSelectApp(app)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(MetroDimens.radiusSmall))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .border(
                                        width = 1.dp,
                                        color = scheme.stroke,
                                        shape = RoundedCornerShape(MetroDimens.radiusSmall),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                bitmap?.let { icon ->
                                    Image(
                                        bitmap = icon,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(38.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    color = scheme.text,
                                    fontSize = 15.sp,
                                    fontFamily = MetroFonts.text,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = app.packageName,
                                    color = scheme.textDim,
                                    fontSize = 12.sp,
                                    fontFamily = MetroFonts.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = scheme.textDim.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
