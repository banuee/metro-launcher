package dev.metro.launcher.ui.picker

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import dev.metro.launcher.data.InternalWidgetType
import dev.metro.launcher.ui.theme.FrostedGlassBox
import dev.metro.launcher.ui.theme.LocalMetroScheme
import dev.metro.launcher.ui.theme.MetroDimens
import dev.metro.launcher.ui.theme.MetroFonts
import dev.metro.launcher.ui.theme.metroClickable

data class AppWithWidgets(
    val packageName: String,
    val appLabel: String,
    val appInfo: AppInfo?,
    val widgets: List<AppWidgetProviderInfo>,
    val widgetLabels: Map<String, String> = emptyMap(),
)

fun calculateWidgetSpans(info: AppWidgetProviderInfo): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetCols = info.targetCellWidth
        val targetRows = info.targetCellHeight
        if (targetCols > 0 && targetRows > 0) {
            return Pair(targetCols.coerceIn(1, 4), targetRows.coerceIn(1, 6))
        }
    }
    val cols = ((info.minWidth + 30) / 70).coerceIn(1, 4)
    val rows = ((info.minHeight + 30) / 70).coerceIn(1, 6)
    return Pair(cols, rows)
}

private fun widgetCountText(count: Int): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod100 in 11..19 -> "виджетов"
        mod10 == 1 -> "виджет"
        mod10 in 2..4 -> "виджета"
        else -> "виджетов"
    }
    return "$count $word"
}

/**
 * Окно выбора виджета:
 * - Сверху: виджеты Metro лаунчера (Часы, Погода, Заметки, Плеер).
 * - Снизу: список приложений с количеством доступных виджетов, при тапе раскрываются доступные варианты.
 */
@Composable
fun WidgetPickerSheet(
    apps: List<AppInfo>,
    onSelectInternalWidget: (InternalWidgetType) -> Unit,
    onSelectAppWidget: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = LocalMetroScheme.current
    val iconRevision by AppIconLoader.revisions.collectAsState()
    var query by remember { mutableStateOf("") }
    val expandedApps = remember { mutableStateMapOf<String, Boolean>() }

    val allAppWidgets by produceState(
        initialValue = emptyList<AppWithWidgets>(),
        apps,
        context,
        iconRevision,
    ) {
        value = AppIconLoader.loadWidgetApps(context, apps).map { loaded ->
            AppWithWidgets(
                packageName = loaded.packageName,
                appLabel = loaded.appLabel,
                appInfo = loaded.appInfo,
                widgets = loaded.widgets,
                widgetLabels = loaded.widgetLabels,
            )
        }
    }

    val filteredAppsWithWidgets = remember(query, allAppWidgets) {
        if (query.isBlank()) allAppWidgets else allAppWidgets.filter { item ->
            item.appLabel.contains(query, ignoreCase = true) ||
                item.widgets.any { widgetInfo ->
                    val label = item.widgetLabels[AppIconLoader.widgetId(widgetInfo)]
                        ?: item.appLabel
                    label.contains(query, ignoreCase = true)
                }
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "ДОБАВИТЬ ВИДЖЕТ",
                        color = scheme.text,
                        fontSize = 24.sp,
                        fontFamily = MetroFonts.headline,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = scheme.text,
                        )
                    }
                }

                        Spacer(Modifier.height(10.dp))

                        // Search Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(MetroDimens.radius))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = scheme.textDim,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
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
                                            text = "Поиск виджетов…",
                                            color = scheme.textDim,
                                            fontSize = 15.sp,
                                            fontFamily = MetroFonts.text,
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            // СЕКЦИЯ 1: ВИДЖЕТЫ METRO (показываем, если не отфильтровано)
                            if (query.isBlank() || "metro часы погода заметки плеер".contains(query, ignoreCase = true)) {
                                item(key = "section_metro_header") {
                                    Text(
                                        text = "ВИДЖЕТЫ METRO",
                                        color = scheme.accent,
                                        fontSize = 12.sp,
                                        fontFamily = MetroFonts.text,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    )
                                }

                                item(key = "metro_widgets_grid") {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        MetroWidgetCard(
                                            title = "Часы и календарь",
                                            subtitle = "Сетка 2×2 • 3D переворот",
                                            iconLabel = "13:26",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.CLOCK)
                                                onDismiss()
                                            },
                                        )
                                        MetroWidgetCard(
                                            title = "Погода",
                                            subtitle = "Сетка 2×2 • Прогноз Open-Meteo",
                                            iconLabel = "22°",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.WEATHER)
                                                onDismiss()
                                            },
                                        )
                                        MetroWidgetCard(
                                            title = "Заметки",
                                            subtitle = "Сетка 2×2 • Чеклист задач",
                                            iconLabel = "LIST",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.NOTES)
                                                onDismiss()
                                            },
                                        )
                                        MetroWidgetCard(
                                            title = "Плеер",
                                            subtitle = "Сетка 2×2 • Управление музыкой",
                                            iconLabel = "PLAY",
                                            onClick = {
                                                onSelectInternalWidget(InternalWidgetType.PLAYER)
                                                onDismiss()
                                            },
                                        )
                                    }
                                }

                                item(key = "divider_after_metro") {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }

                            // СЕКЦИЯ 2: ПРИЛОЖЕНИЯ С ВИДЖЕТАМИ
                            item(key = "section_apps_header") {
                                Text(
                                    text = "ПРИЛОЖЕНИЯ С ВИДЖЕТАМИ",
                                    color = scheme.accent,
                                    fontSize = 12.sp,
                                    fontFamily = MetroFonts.text,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                )
                            }

                            items(filteredAppsWithWidgets, key = { it.packageName }) { appItem ->
                                val isExpanded = expandedApps[appItem.packageName] == true
                                val appBitmap by produceState(
                                    initialValue = AppIconLoader.cachedAppIcon(
                                        appItem.packageName,
                                        appItem.appInfo,
                                    ),
                                    appItem.packageName,
                                    appItem.appInfo?.icon,
                                    iconRevision,
                                ) {
                                    value = AppIconLoader.loadAppIcon(
                                        context,
                                        appItem.packageName,
                                        appItem.appInfo,
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(MetroDimens.radius))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .clickable {
                                            expandedApps[appItem.packageName] = !isExpanded
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    val appIcon = appBitmap
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (appIcon != null) {
                                            Image(
                                                bitmap = appIcon,
                                                contentDescription = appItem.appLabel,
                                                modifier = Modifier.size(40.dp),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.1f)),
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = appItem.appLabel,
                                                color = scheme.text,
                                                fontSize = 15.sp,
                                                fontFamily = MetroFonts.text,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = widgetCountText(appItem.widgets.size),
                                                color = scheme.textDim,
                                                fontSize = 12.sp,
                                                fontFamily = MetroFonts.text,
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = scheme.textDim,
                                        )
                                    }

                                    // Раскрытый список виджетов приложения
                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            appItem.widgets.forEach { widgetInfo ->
                                                val (cols, rows) = calculateWidgetSpans(widgetInfo)
                                                val widgetLabel = appItem.widgetLabels[
                                                    AppIconLoader.widgetId(widgetInfo)
                                                ] ?: appItem.appLabel
                                                val previewBitmap by produceState(
                                                    initialValue = AppIconLoader.cachedWidgetPreview(widgetInfo),
                                                    widgetInfo,
                                                    iconRevision,
                                                ) {
                                                    value = AppIconLoader.loadWidgetPreview(context, widgetInfo)
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color.White.copy(alpha = 0.05f))
                                                        .metroClickable {
                                                            onSelectAppWidget(widgetInfo)
                                                            onDismiss()
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    val preview = previewBitmap
                                                    val appIcon = appBitmap
                                                    if (preview != null) {
                                                        Image(
                                                            bitmap = preview,
                                                            contentDescription = widgetLabel,
                                                            modifier = Modifier.size(36.dp),
                                                        )
                                                    } else if (appIcon != null) {
                                                        Image(
                                                            bitmap = appIcon,
                                                            contentDescription = widgetLabel,
                                                            modifier = Modifier.size(36.dp),
                                                        )
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = widgetLabel,
                                                            color = scheme.text,
                                                            fontSize = 14.sp,
                                                            fontFamily = MetroFonts.text,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            text = "$cols × $rows",
                                                            color = scheme.accent,
                                                            fontSize = 11.sp,
                                                            fontFamily = MetroFonts.text,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
    }
}

@Composable
private fun MetroWidgetCard(
    title: String,
    subtitle: String,
    iconLabel: String,
    onClick: () -> Unit,
) {
    val scheme = LocalMetroScheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MetroDimens.radius))
            .background(Color.White.copy(alpha = 0.06f))
            .metroClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = iconLabel,
                color = scheme.accent,
                fontSize = 12.sp,
                fontFamily = MetroFonts.headline,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = scheme.text,
                fontSize = 15.sp,
                fontFamily = MetroFonts.text,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = scheme.textDim,
                fontSize = 12.sp,
                fontFamily = MetroFonts.text,
            )
        }
    }
}
